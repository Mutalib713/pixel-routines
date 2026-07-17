package com.mosman.routines

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: pause / resume all routines from the notification shade. */
class RoutinesTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        Store.setPaused(this, !Store.isPaused(this))
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val paused = Store.isPaused(this)
        val active = Store.load(this).count { it.enabled }
        tile.state = if (paused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = "Routines"
        tile.subtitle = if (paused) "Paused" else "$active on"
        tile.updateTile()
    }
}
