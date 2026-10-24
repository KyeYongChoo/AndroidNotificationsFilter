package io.github.notifilter

import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
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
import io.github.notifilter.ui.TileHint
import io.github.notifilter.ui.TileHintDialog

class MainActivity : ComponentActivity() {
    private var hasAccess by mutableStateOf(false)
    private var tab by mutableIntStateOf(0)
    private var editing by mutableStateOf<Editing?>(null)
    private var tileHint by mutableStateOf<TileHint?>(null)

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
                        onAddTile = if (Build.VERSION.SDK_INT >= 33) {
                            {
                                requestAddTile(
                                    AddRuleTileService::class.java, R.string.tile_add_rule, R.drawable.ic_tile_add,
                                    "When an unwanted notification arrives, tap this tile and pick the notification " +
                                        "to make a rule that hides it.",
                                )
                            }
                        } else {
                            null
                        },
                        onAddPauseTile = if (Build.VERSION.SDK_INT >= 33) {
                            {
                                requestAddTile(
                                    ToggleTileService::class.java, R.string.tile_toggle, R.drawable.ic_tile_filter,
                                    "Tap this tile to pause or resume filtering. While it's off, no notifications are hidden.",
                                )
                            }
                        } else {
                            null
                        },
                        onOpenNotification = ::openFiltered,
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
                tileHint?.let { TileHintDialog(it, onDismiss = { tileHint = null }) }
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

    /**
     * Opens a filtered notification the way tapping it in the shade would have. Android only lends us
     * that action while the app is running, so older entries open the notification's app instead.
     */
    private fun openFiltered(n: NotificationInfo) {
        Store.tapAction(n)?.let { action ->
            try {
                action.send(this, 0, null, null, null, null, tapActionOptions())
                return
            } catch (_: PendingIntent.CanceledException) {
                // The app withdrew it, e.g. the message was deleted. Opening the app is the next best thing.
            }
        }
        val launch = packageManager.getLaunchIntentForPackage(n.packageName)
        if (launch != null) {
            startActivity(launch)
        } else {
            Toast.makeText(this, "Couldn't open ${n.appLabel}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Android 14+ only lets another app's PendingIntent start an activity if the sender vouches for it.
     * We're on screen because the user just tapped Open, so we do.
     */
    private fun tapActionOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < 34) return null
        val mode = if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
        } else {
            @Suppress("DEPRECATION")
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        return ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(mode).toBundle()
    }

    @RequiresApi(33)
    private fun requestAddTile(service: Class<out TileService>, @StringRes label: Int, @DrawableRes icon: Int, usage: String) {
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, service),
            getString(label),
            Icon.createWithResource(this, icon),
            mainExecutor,
        ) { result ->
            // Android puts a requested tile at the end of Quick Settings and apps can't move it,
            // so explain where to find it rather than leaving the user to hunt for it.
            when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED,
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> tileHint = TileHint(
                    label = getString(label),
                    usage = usage,
                    icon = icon,
                    alreadyAdded = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED,
                )
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> Unit
                else -> Toast.makeText(
                    this, "Couldn't add the tile. Add it by editing your Quick Settings.", Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    companion object {
        const val EXTRA_PICK_NOTIFICATION = "io.github.notifilter.PICK_NOTIFICATION"
    }
}
