package io.github.notifilter

import android.app.StatusBarManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import io.github.notifilter.ui.HomeScreen
import io.github.notifilter.ui.NotiFilterTheme
import io.github.notifilter.ui.RuleEditor
import io.github.notifilter.ui.TAB_RECENT

class MainActivity : ComponentActivity() {
    private var hasAccess by mutableStateOf(false)
    private var tab by mutableIntStateOf(0)
    private var editing by mutableStateOf<Editing?>(null)

    /** Opened from the Quick Settings tile: finish after saving so the user lands back where they were. */
    private var fromTile = false

    private data class Editing(val rule: Rule, val isNew: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            NotiFilterTheme {
                val current = editing
                if (current == null) {
                    HomeScreen(
                        hasAccess = hasAccess,
                        tab = tab,
                        onTabChange = { tab = it },
                        onOpenAccessSettings = ::openAccessSettings,
                        onEditRule = { rule, isNew -> editing = Editing(rule, isNew) },
                        onAddTile = if (Build.VERSION.SDK_INT >= 33) ::requestAddTile else null,
                    )
                } else {
                    BackHandler { editing = null }
                    key(current.rule.id) {
                        RuleEditor(
                            initial = current.rule,
                            isNew = current.isNew,
                            onSave = ::saveRule,
                            onDelete = {
                                Store.deleteRule(current.rule.id)
                                editing = null
                            },
                            onBack = { editing = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        hasAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_PICK_NOTIFICATION, false)) {
            fromTile = true
            editing = null
            tab = TAB_RECENT
        }
    }

    private fun saveRule(rule: Rule) {
        Store.saveRule(rule)
        Toast.makeText(this, "Rule saved", Toast.LENGTH_SHORT).show()
        editing = null
        if (fromTile) {
            fromTile = false
            finish()
        }
    }

    private fun openAccessSettings() {
        val component = ComponentName(this, FilterListenerService::class.java).flattenToString()
        val detail = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
        } else {
            null
        }
        try {
            startActivity(detail ?: Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    @RequiresApi(33)
    private fun requestAddTile() {
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, AddRuleTileService::class.java),
            getString(R.string.tile_add_rule),
            Icon.createWithResource(this, R.drawable.ic_tile_add),
            mainExecutor,
        ) { result ->
            val message = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Tile added"
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Tile is already in Quick Settings"
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> null
                else -> "Couldn't add the tile. Add it by editing your Quick Settings."
            }
            message?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
    }

    companion object {
        const val EXTRA_PICK_NOTIFICATION = "io.github.notifilter.PICK_NOTIFICATION"
    }
}
