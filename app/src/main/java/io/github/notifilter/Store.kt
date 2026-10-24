package io.github.notifilter

import android.app.PendingIntent
import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * All app state. Everything is kept in small JSON files in the app's private no-backup directory;
 * nothing is ever sent anywhere.
 */
object Store {
    private const val TAG = "Store"
    private const val MAX_RECENT = 100
    private const val MAX_FILTERED = 200

    private const val RULES_FILE = "rules.json"
    private const val SETTINGS_FILE = "settings.json"
    private const val RECENT_FILE = "recent.json"
    private const val FILTERED_FILE = "filtered.json"

    private lateinit var dir: File
    private val io = Executors.newSingleThreadExecutor()

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rules: StateFlow<List<Rule>> = _rules

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled

    /** Notifications seen recently that were not filtered, newest first. */
    private val _recent = MutableStateFlow<List<NotificationInfo>>(emptyList())
    val recent: StateFlow<List<NotificationInfo>> = _recent

    /** Notifications dismissed by a rule, newest first. */
    private val _filtered = MutableStateFlow<List<NotificationInfo>>(emptyList())
    val filtered: StateFlow<List<NotificationInfo>> = _filtered

    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected

    /**
     * What tapping each filtered notification would have opened, so the Filtered tab can open it.
     * Android only lends these to us in memory, so they're gone once the app's process ends and older
     * entries fall back to opening the app.
     */
    private val tapActions = object : LinkedHashMap<String, PendingIntent>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PendingIntent>) = size > MAX_FILTERED
    }

    @Volatile
    private var compiled: List<CompiledRule> = emptyList()

    fun init(context: Context) {
        dir = context.noBackupFilesDir
        _rules.value = read(RULES_FILE) { rulesFromJson(it) } ?: emptyList()
        _enabled.value = read(SETTINGS_FILE) { JSONObject(it).optBoolean("enabled", true) } ?: true
        _recent.value = read(RECENT_FILE) { notificationsFromJson(JSONArray(it)) } ?: emptyList()
        _filtered.value = read(FILTERED_FILE) { notificationsFromJson(JSONArray(it)) } ?: emptyList()
        compiled = compileAll(_rules.value)
    }

    fun match(n: NotificationInfo): Rule? = compiled.firstOrNull { it.matches(n) }?.rule

    fun saveRule(rule: Rule) {
        _rules.update { list ->
            if (list.any { it.id == rule.id }) list.map { if (it.id == rule.id) rule else it } else list + rule
        }
        rulesChanged()
    }

    fun deleteRule(id: String) = deleteRules(setOf(id))

    fun deleteRules(ids: Set<String>) {
        _rules.update { list -> list.filterNot { it.id in ids } }
        rulesChanged()
    }

    fun setRulesEnabled(ids: Set<String>, enabled: Boolean) {
        _rules.update { list -> list.map { if (it.id in ids) it.copy(enabled = enabled) else it } }
        rulesChanged()
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        write(SETTINGS_FILE, JSONObject().put("enabled", enabled).toString())
        if (enabled) FilterListenerService.reapply()
    }

    fun setListenerConnected(connected: Boolean) {
        _listenerConnected.value = connected
    }

    fun recordSeen(n: NotificationInfo) {
        _recent.update { list -> (listOf(n) + list.filterNot { it.key == n.key }).take(MAX_RECENT) }
        write(RECENT_FILE, notificationsToJson(_recent.value).toString())
    }

    fun recordFiltered(n: NotificationInfo, tapAction: PendingIntent?) {
        synchronized(tapActions) {
            tapActions.remove(n.key)
            if (tapAction != null) tapActions[n.key] = tapAction
        }
        // An app updating the same notification (e.g. a progress bar) replaces its entry.
        _filtered.update { list -> (listOf(n) + list.filterNot { it.key == n.key }).take(MAX_FILTERED) }
        _recent.update { list -> list.filterNot { it.key == n.key } }
        write(FILTERED_FILE, notificationsToJson(_filtered.value).toString())
        write(RECENT_FILE, notificationsToJson(_recent.value).toString())
    }

    fun clearRecent() {
        _recent.value = emptyList()
        write(RECENT_FILE, "[]")
    }

    fun clearFiltered() {
        _filtered.value = emptyList()
        synchronized(tapActions) { tapActions.clear() }
        write(FILTERED_FILE, "[]")
    }

    /** The action tapping [n] would have run, if it was filtered since the app last started. */
    fun tapAction(n: NotificationInfo): PendingIntent? = synchronized(tapActions) { tapActions[n.key] }

    fun exportRules(): String =
        JSONObject().put("version", 1).put("rules", JSONArray(_rules.value.map { it.toJson() })).toString(2)

    /** Adds the rules in [json], replacing existing rules with the same id. Returns the count. */
    fun importRules(json: String): Int {
        val imported = rulesFromJson(json)
        val ids = imported.map { it.id }.toSet()
        _rules.update { list -> list.filterNot { it.id in ids } + imported }
        rulesChanged()
        return imported.size
    }

    private fun rulesChanged() {
        compiled = compileAll(_rules.value)
        write(RULES_FILE, exportRules())
        FilterListenerService.reapply()
    }

    private fun <T> read(name: String, parse: (String) -> T): T? {
        val file = AtomicFile(File(dir, name))
        if (!file.baseFile.exists()) return null
        return try {
            parse(file.readFully().decodeToString())
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't read $name", e)
            null
        } catch (e: JSONException) {
            Log.w(TAG, "Couldn't parse $name", e)
            null
        }
    }

    private fun write(name: String, contents: String) {
        io.execute {
            val file = AtomicFile(File(dir, name))
            val out = try {
                file.startWrite()
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't write $name", e)
                return@execute
            }
            try {
                out.write(contents.encodeToByteArray())
                file.finishWrite(out)
            } catch (e: IOException) {
                file.failWrite(out)
                Log.w(TAG, "Couldn't write $name", e)
            }
        }
    }
}

private fun Rule.toJson() = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("enabled", enabled)
    .put("packageName", packageName)
    .put("titlePattern", titlePattern)
    .put("textPattern", textPattern)
    .put("ignoreCase", ignoreCase)

/** Accepts either `{"rules": [...]}` (our export format) or a bare array of rules. */
private fun rulesFromJson(json: String): List<Rule> {
    val array = json.trim().let { if (it.startsWith("[")) JSONArray(it) else JSONObject(it).getJSONArray("rules") }
    return (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        Rule(
            id = o.optString("id").ifBlank { Rule().id },
            name = o.optString("name"),
            enabled = o.optBoolean("enabled", true),
            packageName = o.optString("packageName"),
            titlePattern = o.optString("titlePattern"),
            textPattern = o.optString("textPattern"),
            ignoreCase = o.optBoolean("ignoreCase", true),
        )
    }
}

private fun notificationsToJson(list: List<NotificationInfo>) = JSONArray(list.map { n ->
    JSONObject()
        .put("key", n.key)
        .put("packageName", n.packageName)
        .put("appLabel", n.appLabel)
        .put("title", n.title)
        .put("text", n.text)
        .put("postTime", n.postTime)
        .putOpt("ruleId", n.ruleId)
        .putOpt("ruleName", n.ruleName)
})

private fun notificationsFromJson(array: JSONArray) = (0 until array.length()).map { i ->
    val o = array.getJSONObject(i)
    NotificationInfo(
        key = o.getString("key"),
        packageName = o.getString("packageName"),
        appLabel = o.optString("appLabel"),
        title = o.optString("title"),
        text = o.optString("text"),
        postTime = o.optLong("postTime"),
        ruleId = o.optString("ruleId").ifBlank { null },
        ruleName = o.optString("ruleName").ifBlank { null },
    )
}
