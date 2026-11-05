package com.example.tile

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.R
import com.example.TailGuardApplication
import com.example.data.VpnPosture
import com.example.service.OrchestratorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Quick Settings Tile (`TileService`) in the pull-down notification shade
 * for rapid one-button toggling of Tailscale VPN and 5G Kill Switch posture.
 */
class TailGuardTileService : TileService() {

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val controller by lazy {
        (application as TailGuardApplication).container.controller
    }

    override fun onStartListening() {
        super.onStartListening()
        controller.refreshLiveState(triggerAutomation = false)
        renderTile(controller.state.value)
        tileScope.launch {
            controller.state.collect { state ->
                renderTile(state)
            }
        }
    }

    override fun onClick() {
        super.onClick()
        tileScope.launch {
            controller.rapidToggleVpn(sourceLabel = "Quick Settings Tile")
            renderTile(controller.state.value)
        }
    }

    override fun onStopListening() {
        tileScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
        super.onStopListening()
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    private fun renderTile(state: OrchestratorState) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label)

        when (state.posture) {
            VpnPosture.VPN_ACTIVE,
            VpnPosture.TAILSCALE_ACTIVE -> {
                tile.state = Tile.STATE_ACTIVE
                tile.icon = Icon.createWithResource(this, R.drawable.ic_tailguard_mesh)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.tile_subtitle_on)
                }
            }
            VpnPosture.PREEMPTIVE_SHIELD,
            VpnPosture.KILL_SWITCH_LOCKDOWN -> {
                tile.state = Tile.STATE_ACTIVE
                tile.icon = Icon.createWithResource(this, R.drawable.ic_shield_lockdown)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.tile_subtitle_lockdown)
                }
            }
            VpnPosture.HANDSHAKE_PENDING -> {
                tile.state = Tile.STATE_ACTIVE
                tile.icon = Icon.createWithResource(this, R.drawable.ic_tailguard_mesh)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.posture_connecting)
                }
            }
            VpnPosture.TRUSTED_BYPASS -> {
                tile.state = Tile.STATE_INACTIVE
                tile.icon = Icon.createWithResource(this, R.drawable.ic_tailguard_mesh)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.tile_subtitle_trusted)
                }
            }
            VpnPosture.UNPROTECTED -> {
                tile.state = Tile.STATE_INACTIVE
                tile.icon = Icon.createWithResource(this, R.drawable.ic_tailguard_mesh)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.tile_subtitle_off)
                }
            }
        }
        runCatching { tile.updateTile() }
    }

    companion object {
        fun requestSync(context: Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, TailGuardTileService::class.java)
                )
            }
        }
    }
}
