package io.github.notifilter

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile that pauses or resumes all filtering. */
class ToggleTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        Store.setEnabled(!Store.enabled.value)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = Store.enabled.value
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (on) "On" else "Paused"
        tile.updateTile()
    }
}
