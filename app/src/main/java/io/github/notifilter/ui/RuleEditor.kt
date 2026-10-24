package io.github.notifilter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.notifilter.AppLabels
import io.github.notifilter.NotificationInfo
import io.github.notifilter.Rule
import io.github.notifilter.Store
import io.github.notifilter.patternError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val PREVIEW_LIMIT = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditor(
    initial: Rule,
    isNew: Boolean,
    onSave: (Rule) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var packageName by rememberSaveable { mutableStateOf(initial.packageName) }
    var titlePattern by rememberSaveable { mutableStateOf(initial.titlePattern) }
    var textPattern by rememberSaveable { mutableStateOf(initial.textPattern) }
    var ignoreCase by rememberSaveable { mutableStateOf(initial.ignoreCase) }
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    var pickingApp by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val candidate = initial.copy(
        name = name.trim(),
        packageName = packageName.trim(),
        titlePattern = titlePattern,
        textPattern = textPattern,
        ignoreCase = ignoreCase,
        enabled = enabled,
    )
    val titleError = remember(titlePattern, ignoreCase) { patternError(titlePattern, ignoreCase) }
    val textError = remember(textPattern, ignoreCase) { patternError(textPattern, ignoreCase) }
    val canSave = !candidate.isEmpty && titleError == null && textError == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New rule" else "Edit rule") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete rule") }
                    }
                    TextButton(onClick = { onSave(candidate) }, enabled = canSave) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it },
                label = { Text("App") },
                placeholder = { Text("Any app") },
                singleLine = true,
                supportingText = {
                    val pkg = packageName.trim()
                    Text(if (pkg.isEmpty()) "Blank matches every app" else AppLabels.get(context, pkg) ?: "Package name")
                },
                trailingIcon = {
                    IconButton(onClick = { pickingApp = true }) { Icon(Icons.Default.ArrowDropDown, "Choose app") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            PatternField("Title matches", titlePattern, titleError) { titlePattern = it }
            PatternField("Text matches", textPattern, textError) { textPattern = it }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = ignoreCase, onCheckedChange = { ignoreCase = it })
                Text("Ignore case", Modifier.clickable { ignoreCase = !ignoreCase })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rule enabled", Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Text(
                "A rule hides a notification when every filled-in box matches. Empty boxes are ignored.\n\n" +
                    "Title and Text only need to appear somewhere in the notification, so \"sale\" also " +
                    "catches \"Big sale today\". Capital letters don't matter while Ignore case is ticked.\n\n" +
                    "Some symbols have special meanings: ^ at the start means \"starts with\", $ at the end " +
                    "means \"ends with\", .* means \"anything\", and \\d+ means \"any number\". A \\ in front " +
                    "of a symbol, like \\. or \\?, means the symbol itself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (candidate.isEmpty) {
                Text("Fill in at least one of app, title or text.", color = MaterialTheme.colorScheme.error)
            }

            HorizontalDivider()
            MatchPreview(candidate.takeIf { canSave })
        }
    }

    if (pickingApp) {
        AppPickerDialog(
            onPick = { packageName = it; pickingApp = false },
            onDismiss = { pickingApp = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this rule?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PatternField(label: String, value: String, error: String?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text("Anything") },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        textStyle = TextStyle(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Shows which of the recently seen notifications this rule would have dismissed. */
@Composable
private fun MatchPreview(rule: Rule?) {
    val recent by Store.recent.collectAsState()
    val filtered by Store.filtered.collectAsState()
    var matches by remember { mutableStateOf<List<NotificationInfo>?>(null) }
    val sample = remember(recent, filtered) { (recent + filtered).distinctBy { it.key }.sortedByDescending { it.postTime } }

    LaunchedEffect(rule, sample) {
        matches = null
        val compiled = rule?.compileOrNull() ?: return@LaunchedEffect
        delay(300) // debounce typing
        matches = withContext(Dispatchers.Default) { sample.filter(compiled::matches) }
    }

    Text("Preview", style = MaterialTheme.typography.titleMedium)
    val result = matches
    when {
        rule == null -> Text("Fix the rule above to see what it matches.", style = MaterialTheme.typography.bodyMedium)
        result == null -> CircularProgressIndicator()
        else -> {
            Text(
                "Matches ${result.size} of the last ${sample.size} notifications",
                style = MaterialTheme.typography.bodyMedium,
            )
            result.take(PREVIEW_LIMIT).forEach { NotificationRow(it) }
            if (result.size > PREVIEW_LIMIT) Text("…and ${result.size - PREVIEW_LIMIT} more")
        }
    }
}

@Composable
private fun AppPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val recent by Store.recent.collectAsState()
    val filtered by Store.filtered.collectAsState()
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val seen = (recent + filtered).map { it.packageName }
        apps = withContext(Dispatchers.IO) { AppLabels.installedApps(context, seen) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Choose app") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val list = apps
                if (list == null) {
                    CircularProgressIndicator(Modifier.padding(16.dp))
                } else {
                    val shown = list.filter { (label, pkg) ->
                        query.isBlank() || label.contains(query, ignoreCase = true) || pkg.contains(query, ignoreCase = true)
                    }
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        item {
                            ListItem(headlineContent = { Text("Any app") }, modifier = Modifier.clickable { onPick("") })
                        }
                        items(shown, key = { it.second }) { (label, pkg) ->
                            ListItem(
                                headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(pkg, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.clickable { onPick(pkg) },
                            )
                        }
                    }
                }
            }
        },
    )
}
