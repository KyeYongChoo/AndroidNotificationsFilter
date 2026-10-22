package io.github.notifilter.ui

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.notifilter.AppLabels
import io.github.notifilter.NotificationInfo
import io.github.notifilter.Rule
import io.github.notifilter.Store
import io.github.notifilter.escapeRegex
import org.json.JSONException

const val TAB_RULES = 0
const val TAB_RECENT = 1
const val TAB_FILTERED = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    hasAccess: Boolean,
    tab: Int,
    onTabChange: (Int) -> Unit,
    onOpenAccessSettings: () -> Unit,
    onEditRule: (rule: Rule, isNew: Boolean) -> Unit,
    onAddTile: (() -> Unit)?,
    onAddPauseTile: (() -> Unit)?,
) {
    val context = LocalContext.current
    val rules by Store.rules.collectAsState()
    val recent by Store.recent.collectAsState()
    val filtered by Store.filtered.collectAsState()
    val enabled by Store.enabled.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(Store.exportRules().encodeToByteArray()) }
        Toast.makeText(context, "Exported ${rules.size} rules", Toast.LENGTH_SHORT).show()
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val message = try {
            val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
            "Imported ${Store.importRules(json)} rules"
        } catch (_: JSONException) {
            "That file doesn't contain NotiFilter rules"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NotiFilter") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (onAddTile != null) {
                            DropdownMenuItem(
                                text = { Text("Add Quick Settings tile") },
                                onClick = { menuOpen = false; onAddTile() },
                            )
                        }
                        if (onAddPauseTile != null) {
                            DropdownMenuItem(
                                text = { Text("Add pause tile") },
                                onClick = { menuOpen = false; onAddPauseTile() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Export rules") },
                            onClick = { menuOpen = false; exporter.launch("notifilter-rules.json") },
                        )
                        DropdownMenuItem(
                            text = { Text("Import rules") },
                            onClick = {
                                menuOpen = false
                                importer.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Privacy") },
                            onClick = { menuOpen = false; showPrivacy = true },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab == TAB_RULES) {
                FloatingActionButton(onClick = { onEditRule(Rule(), true) }) { Icon(Icons.Default.Add, "Add rule") }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!hasAccess) AccessCard(onOpenAccessSettings)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (enabled) "Filtering is on" else "Filtering is paused",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(checked = enabled, onCheckedChange = Store::setEnabled)
            }
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == TAB_RULES, onClick = { onTabChange(TAB_RULES) }, text = { Text("Rules") })
                Tab(selected = tab == TAB_RECENT, onClick = { onTabChange(TAB_RECENT) }, text = { Text("Recent") })
                Tab(selected = tab == TAB_FILTERED, onClick = { onTabChange(TAB_FILTERED) }, text = { Text("Filtered") })
            }
            when (tab) {
                TAB_RULES -> RulesList(rules, onEdit = { onEditRule(it, false) })
                TAB_RECENT -> NotificationList(
                    items = recent,
                    header = "Tap a notification to make a rule from it",
                    empty = if (hasAccess) "Notifications you receive will show up here." else "Grant notification access first.",
                    onClear = Store::clearRecent,
                    onClick = { onEditRule(ruleFrom(it), true) },
                )
                TAB_FILTERED -> NotificationList(
                    items = filtered,
                    header = "${filtered.size} filtered · tap to see the rule",
                    empty = "Notifications dismissed by your rules will show up here.",
                    onClear = Store::clearFiltered,
                    onClick = { n -> rules.firstOrNull { it.id == n.ruleId }?.let { onEditRule(it, false) } },
                )
            }
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("OK") } },
            title = { Text("Privacy") },
            text = {
                Text(
                    "NotiFilter has no internet permission, so Android itself prevents it from sending " +
                        "anything off your phone. You can confirm this in the Play Store under " +
                        "\"About this app → App permissions\", or in Settings → Apps → NotiFilter → Permissions.\n\n" +
                        "Rules and the recent/filtered lists are stored only in the app's private storage and " +
                        "are excluded from Google backups. The source code is open for anyone to audit."
                )
            },
        )
    }
}

@Composable
private fun AccessCard(onOpenAccessSettings: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Notification access needed", style = MaterialTheme.typography.titleMedium)
            Text(
                "NotiFilter needs to read notifications to dismiss the ones matching your rules. " +
                    "If Android says this is a restricted setting, open App info → ⋮ → " +
                    "\"Allow restricted settings\" first.",
                style = MaterialTheme.typography.bodyMedium,
            )
            // Android's consent dialog for notification access lists every capability it bundles
            // (replying, answering calls, Do Not Disturb), and apps can't change it, so set expectations here.
            Text(
                "Android will warn that NotiFilter could reply to messages, answer calls and change " +
                    "Do Not Disturb. It shows that same warning for every app with notification access. " +
                    "NotiFilter only uses it to dismiss notifications that match your rules, and it has no " +
                    "internet permission, so nothing can leave your phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onOpenAccessSettings) { Text("Grant access") }
        }
    }
}

@Composable
private fun RulesList(rules: List<Rule>, onEdit: (Rule) -> Unit) {
    if (rules.isEmpty()) {
        EmptyText(
            "No rules yet. Tap + to add one, or pick a notification from the Recent tab " +
                "(also reachable from the \"Filter a notification\" Quick Settings tile)."
        )
        return
    }
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rules, key = { it.id }) { rule ->
            Card(onClick = { onEdit(rule) }, Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(rule.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val app = rule.packageName.takeIf { it.isNotBlank() }
                        Text(
                            app?.let { AppLabels.get(context, it) ?: it } ?: "Any app",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (rule.titlePattern.isNotBlank()) PatternLine("Title", rule.titlePattern)
                        if (rule.textPattern.isNotBlank()) PatternLine("Text", rule.textPattern)
                        if (rule.enabled && rule.compileOrNull() == null) {
                            Text("Invalid rule — not applied", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Switch(checked = rule.enabled, onCheckedChange = { Store.saveRule(rule.copy(enabled = it)) })
                }
            }
        }
    }
}

@Composable
private fun PatternLine(label: String, pattern: String) {
    Text(
        "$label: /$pattern/",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun NotificationList(
    items: List<NotificationInfo>,
    header: String,
    empty: String,
    onClear: () -> Unit,
    onClick: (NotificationInfo) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyText(empty)
        return
    }
    val sorted = remember(items) { items.sortedByDescending { it.postTime } }
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(header, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onClear) { Text("Clear") }
        }
        HorizontalDivider()
        LazyColumn {
            items(sorted, key = { it.key }) { n ->
                NotificationRow(n, Modifier.clickable { onClick(n) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun NotificationRow(n: NotificationInfo, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier,
        overlineContent = {
            val time = DateUtils.getRelativeTimeSpanString(n.postTime).toString()
            Text(listOfNotNull(n.appLabel, time, n.ruleName?.let { "rule: $it" }).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        headlineContent = { Text(n.title.ifBlank { "(no title)" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (n.text.isBlank()) null else {
            { Text(n.text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
    )
}

@Composable
private fun EmptyText(text: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Starts a rule that matches this exact notification; the user usually generalises it. */
private fun ruleFrom(n: NotificationInfo) = Rule(
    name = n.title.ifBlank { n.appLabel }.take(60),
    packageName = n.packageName,
    titlePattern = if (n.title.isNotBlank()) "^${escapeRegex(n.title)}$" else "",
    textPattern = if (n.title.isBlank() && n.text.isNotBlank()) escapeRegex(n.text.lineSequence().first()) else "",
)
