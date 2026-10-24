package io.github.notifilter.ui

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.notifilter.AppLabels
import io.github.notifilter.NotificationInfo
import io.github.notifilter.Rule
import io.github.notifilter.Store
import io.github.notifilter.ruleFrom
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
    onOpenNotification: (NotificationInfo) -> Unit,
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

    // Items picked by long-pressing in the current tab: rule ids on Rules, notification keys on Recent
    // and Filtered. Selection mode lasts while anything is selected.
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val ruleIds = rules.map { it.id }.toSet()
    val selectable = when (tab) {
        TAB_RULES -> ruleIds
        TAB_RECENT -> recent.map { it.key }.toSet()
        else -> filtered.map { it.key }.toSet()
    }
    val selection = selected intersect selectable
    val selecting = selection.isNotEmpty()
    val toggleSelected = { id: String -> selected = if (id in selection) selection - id else selection + id }
    var pendingDelete by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(tab) { selected = emptySet() }
    BackHandler(enabled = selecting) { selected = emptySet() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, "Cancel selection") }
                    }
                },
                title = { Text(if (selecting) "${selection.size} selected" else "NotiFilter") },
                colors = if (selecting) {
                    TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    TopAppBarDefaults.topAppBarColors()
                },
                actions = {
                    if (selecting) {
                        if (selection.size < selectable.size) {
                            TextButton(onClick = { selected = selectable }) { Text("Select all") }
                        }
                        return@TopAppBar
                    }
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
                            text = { Text("Delete all rules") },
                            enabled = rules.isNotEmpty(),
                            onClick = { menuOpen = false; pendingDelete = ruleIds },
                        )
                        DropdownMenuItem(
                            text = { Text("Privacy") },
                            onClick = { menuOpen = false; showPrivacy = true },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (selecting && tab == TAB_RULES) {
                BottomAppBar {
                    TextButton(onClick = { Store.setRulesEnabled(selection, true); selected = emptySet() }) { Text("Turn on") }
                    TextButton(onClick = { Store.setRulesEnabled(selection, false); selected = emptySet() }) { Text("Turn off") }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { pendingDelete = selection },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete") }
                }
            } else if (selecting) {
                // Only NotiFilter's own list changes; the label says so, since the notifications
                // themselves may still be in the shade.
                BottomAppBar {
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            if (tab == TAB_RECENT) Store.removeRecent(selection) else Store.removeFiltered(selection)
                            selected = emptySet()
                        },
                    ) { Text("Remove from list") }
                }
            }
        },
        floatingActionButton = {
            if (tab == TAB_RULES && !selecting) {
                FloatingActionButton(onClick = { onEditRule(Rule(), true) }) { Icon(Icons.Default.Add, "Add rule") }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!hasAccess) AccessCard(onOpenAccessSettings)
            // Hidden while selecting, so the master switch isn't mistaken for a bulk action.
            AnimatedVisibility(visible = !selecting) {
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
            }
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == TAB_RULES, onClick = { onTabChange(TAB_RULES) }, text = { Text("Rules") })
                Tab(selected = tab == TAB_RECENT, onClick = { onTabChange(TAB_RECENT) }, text = { Text("Recent") })
                Tab(selected = tab == TAB_FILTERED, onClick = { onTabChange(TAB_FILTERED) }, text = { Text("Filtered") })
            }
            when (tab) {
                TAB_RULES -> RulesList(
                    rules,
                    selection = selection,
                    onEdit = { onEditRule(it, false) },
                    onToggleSelected = toggleSelected,
                )
                TAB_RECENT -> NotificationList(
                    items = recent,
                    header = "Tap a notification to make a rule from it, or long-press to select",
                    empty = if (hasAccess) "Notifications you receive will show up here." else "Grant notification access first.",
                    onClear = Store::clearRecent,
                    onClick = { onEditRule(ruleFrom(it), true) },
                    selection = selection,
                    onToggleSelected = toggleSelected,
                )
                TAB_FILTERED -> NotificationList(
                    items = filtered,
                    header = "${filtered.size} filtered · tap one to see its rule, or long-press to select",
                    empty = "Notifications dismissed by your rules will show up here.",
                    onClear = Store::clearFiltered,
                    onClick = { n -> rules.firstOrNull { it.id == n.ruleId }?.let { onEditRule(it, false) } },
                    selection = selection,
                    onToggleSelected = toggleSelected,
                    onOpen = onOpenNotification,
                )
            }
        }
    }

    pendingDelete?.let { ids ->
        val all = ids.size == rules.size && ids.size > 1
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    when {
                        all -> "Delete all ${ids.size} rules?"
                        ids.size == 1 -> "Delete this rule?"
                        else -> "Delete ${ids.size} rules?"
                    }
                )
            },
            text = { Text(if (all) "This can't be undone. To keep a copy, use Export rules first." else "This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        Store.deleteRules(ids)
                        pendingDelete = null
                        selected = emptySet()
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
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
private fun RulesList(
    rules: List<Rule>,
    selection: Set<String>,
    onEdit: (Rule) -> Unit,
    onToggleSelected: (id: String) -> Unit,
) {
    if (rules.isEmpty()) {
        EmptyText(
            "No rules yet. Tap + to add one, or pick a notification from the Recent tab " +
                "(also reachable from the \"Filter a notification\" Quick Settings tile)."
        )
        return
    }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val selecting = selection.isNotEmpty()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Long-press is hard to discover, so say it's there once there's more than one rule to select.
        // The hint stays in the list while selecting (just reworded): removing and re-adding it would
        // re-insert it above the rule the list is anchored to, leaving it half scrolled off.
        if (rules.size > 1) {
            item(key = "hint") {
                Text(
                    if (selecting) "Tap rules to select or unselect them" else "Long-press a rule to select several",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(rules, key = { it.id }) { rule ->
            val isSelected = rule.id in selection
            Card(
                Modifier
                    .fillMaxWidth()
                    .clip(CardDefaults.shape)
                    .combinedClickable(
                        onClick = { if (selecting) onToggleSelected(rule.id) else onEdit(rule) },
                        onLongClickLabel = "Select",
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleSelected(rule.id)
                        },
                    ),
                colors = if (isSelected) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selecting) {
                        Checkbox(checked = isSelected, onCheckedChange = { onToggleSelected(rule.id) })
                        Spacer(Modifier.width(8.dp))
                    }
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
                    // Hidden while selecting so a stray tap can't flip a rule; use Turn on / Turn off instead.
                    if (!selecting) {
                        // LazyColumn reuses rows (even a rule's own row from before it was turned on or
                        // off off-screen), and the Material 3 switch keeps its thumb animation across
                        // that, so it would visibly slide from the old state. Remembered values are
                        // reset when a row is reused, so keying on one gives reused rows a fresh switch
                        // while a switch the user flips still slides.
                        val switchInstance = remember { Any() }
                        key(switchInstance) {
                            Switch(checked = rule.enabled, onCheckedChange = { Store.saveRule(rule.copy(enabled = it)) })
                        }
                    }
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
    selection: Set<String>,
    onToggleSelected: (key: String) -> Unit,
    onOpen: ((NotificationInfo) -> Unit)? = null,
) {
    if (items.isEmpty()) {
        EmptyText(empty)
        return
    }
    val sorted = remember(items) { items.sortedByDescending { it.postTime } }
    val haptics = LocalHapticFeedback.current
    val selecting = selection.isNotEmpty()
    Column {
        // A fixed minimum height so the header doesn't shrink when Clear hides during selection.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (selecting) "Tap notifications to select or unselect them" else header,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (!selecting) TextButton(onClick = onClear) { Text("Clear") }
        }
        HorizontalDivider()
        LazyColumn {
            items(sorted, key = { it.key }) { n ->
                val isSelected = n.key in selection
                NotificationRow(
                    n,
                    Modifier.combinedClickable(
                        onClick = { if (selecting) onToggleSelected(n.key) else onClick(n) },
                        onLongClickLabel = "Select",
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleSelected(n.key)
                        },
                    ),
                    selected = isSelected,
                    leading = if (selecting) {
                        { Checkbox(checked = isSelected, onCheckedChange = { onToggleSelected(n.key) }) }
                    } else {
                        null
                    },
                    // Hidden while selecting so a stray tap can't open an app.
                    trailing = onOpen?.takeIf { !selecting }?.let { open -> { OpenButton(n, onClick = { open(n) }) } },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun NotificationRow(
    n: NotificationInfo,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = modifier,
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = leading,
        trailingContent = trailing,
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

/**
 * Opens a filtered notification. Shown whenever there's somewhere to go: the notification's own tap
 * action if we still have it, otherwise its app, so users never have to know which one they'll get.
 */
@Composable
private fun OpenButton(n: NotificationInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    val canOpen = remember(n.key, n.postTime) {
        Store.tapAction(n) != null || context.packageManager.getLaunchIntentForPackage(n.packageName) != null
    }
    if (canOpen) TextButton(onClick = onClick) { Text("Open") }
}

@Composable
private fun EmptyText(text: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
