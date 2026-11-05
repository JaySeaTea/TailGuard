package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.R
import com.example.TailGuardApplication
import com.example.data.VpnPosture
import com.example.service.OrchestratorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 1x1 Home Screen Rapid Toggle Widget (`AppWidgetProvider`).
 * Tapping anywhere on the widget toggles Tailscale VPN (or releases an active 5G Kill Switch lockdown)
 * with zero periodic background wakeup drain (`updatePeriodMillis = 0`).
 */
class TailGuardAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val app = context.applicationContext as? TailGuardApplication
        val state = app?.container?.controller?.state?.value ?: OrchestratorState()
        for (widgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(widgetId, buildRemoteViews(context, state))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_RAPID_TOGGLE) {
            val pendingResult = goAsync()
            val app = context.applicationContext as? TailGuardApplication
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    app?.container?.controller?.rapidToggleVpn(sourceLabel = "1x1 Home Widget")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_RAPID_TOGGLE = "com.example.action.WIDGET_RAPID_TOGGLE"

        fun updateAllWidgets(context: Context, state: OrchestratorState) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, TailGuardAppWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isEmpty()) return
                val views = buildRemoteViews(context, state)
                for (id in ids) {
                    manager.updateAppWidget(id, views)
                }
            }
        }

        private fun buildRemoteViews(context: Context, state: OrchestratorState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tailguard_toggle)

            val toggleIntent = Intent(context, TailGuardAppWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_RAPID_TOGGLE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                501,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            val (statusLabel, accentColor, iconRes) = when (state.posture) {
                VpnPosture.VPN_ACTIVE,
                VpnPosture.TAILSCALE_ACTIVE -> Triple(
                    context.getString(R.string.widget_status_on),
                    Color.parseColor("#38BDF8"),
                    R.drawable.ic_tailguard_mesh
                )
                VpnPosture.PREEMPTIVE_SHIELD,
                VpnPosture.KILL_SWITCH_LOCKDOWN -> Triple(
                    context.getString(R.string.widget_status_lock),
                    Color.parseColor("#00E5FF"),
                    R.drawable.ic_shield_lockdown
                )
                VpnPosture.HANDSHAKE_PENDING -> Triple(
                    "SYNC...",
                    Color.parseColor("#7DD3FC"),
                    R.drawable.ic_tailguard_mesh
                )
                VpnPosture.TRUSTED_BYPASS -> Triple(
                    context.getString(R.string.widget_status_safe),
                    Color.parseColor("#10B981"),
                    R.drawable.ic_tailguard_mesh
                )
                VpnPosture.UNPROTECTED -> Triple(
                    context.getString(R.string.widget_status_off),
                    Color.parseColor("#64748B"),
                    R.drawable.ic_tailguard_mesh
                )
            }

            views.setImageViewResource(R.id.widget_mesh_icon, iconRes)
            views.setInt(R.id.widget_mesh_icon, "setColorFilter", accentColor)
            views.setInt(R.id.widget_status_dot, "setColorFilter", accentColor)
            views.setTextViewText(R.id.widget_status_text, statusLabel)
            return views
        }
    }
}
