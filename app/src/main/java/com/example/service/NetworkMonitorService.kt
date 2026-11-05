package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.TailGuardApplication
import com.example.data.VpnPosture
import com.example.data.VpnProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Ultra-low-battery, passive NetworkCallback listener.
 *
 * Uses zero wake-locks, zero timers, and zero background polling. The process sleeps at 0% CPU
 * until the Android OS ConnectivityService dispatches a transport, RSSI, or link-property change.
 */
class NetworkMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connectivityManager: ConnectivityManager? = null
    private var isCallbackRegistered = false

    private val controller by lazy {
        (application as TailGuardApplication).container.controller
    }

    private val passiveNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            controller.refreshLiveState(triggerAutomation = true)
        }

        override fun onLost(network: Network) {
            controller.refreshLiveState(triggerAutomation = true)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            controller.refreshLiveState(triggerAutomation = true)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            controller.refreshLiveState(triggerAutomation = true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        ensureNotificationChannels(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        registerPassiveCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MONITOR -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_NOTIFICATION_TOGGLE_VPN -> {
                serviceScope.launch {
                    controller.rapidToggleVpn(sourceLabel = "Status Notification")
                }
            }
            else -> {
                promoteToLowPriorityForeground(controller.state.value)
                controller.refreshLiveState(triggerAutomation = true)
            }
        }
        return START_STICKY
    }

    private fun registerPassiveCallback() {
        if (isCallbackRegistered) return
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        runCatching {
            cm.registerNetworkCallback(request, passiveNetworkCallback)
            isCallbackRegistered = true
        }
    }

    private fun promoteToLowPriorityForeground(state: OrchestratorState) {
        val notification = buildStatusNotification(this, state)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        if (isCallbackRegistered) {
            runCatching { connectivityManager?.unregisterNetworkCallback(passiveNetworkCallback) }
            isCallbackRegistered = false
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "tailguard_passive_monitor"
        const val ALERT_CHANNEL_ID = "tailguard_exposure_alert"
        const val NOTIFICATION_ID = 4102
        const val ALERT_NOTIFICATION_ID = 4103

        const val ACTION_START_MONITOR = "com.example.action.START_PASSIVE_MONITOR"
        const val ACTION_STOP_MONITOR = "com.example.action.STOP_PASSIVE_MONITOR"
        const val ACTION_NOTIFICATION_TOGGLE_VPN = "com.example.action.NOTIF_TOGGLE_VPN"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun startPassiveMonitor(context: Context) {
            val intent = Intent(context, NetworkMonitorService::class.java).apply {
                action = ACTION_START_MONITOR
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stopPassiveMonitor(context: Context) {
            if (!isRunning) return
            val intent = Intent(context, NetworkMonitorService::class.java).apply {
                action = ACTION_STOP_MONITOR
            }
            runCatching { context.startService(intent) }
        }

        fun refreshNotificationIfRunning(context: Context, state: OrchestratorState) {
            if (!isRunning) return
            ensureNotificationChannels(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            runCatching {
                nm.notify(NOTIFICATION_ID, buildStatusNotification(context, state))
            }
        }

        fun postExposureAlert(context: Context, provider: VpnProvider) {
            ensureNotificationChannels(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Native floating Internet Panel intent (API 29+)
            val panelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            } else {
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            }.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val pendingPanel = PendingIntent.getActivity(
                context,
                201,
                panelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val openAppIntent = PendingIntent.getActivity(
                context,
                202,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alertNotif = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield_lockdown)
                .setContentTitle(context.getString(R.string.notif_exposure_alert_title))
                .setContentText(context.getString(R.string.notif_exposure_alert_body, provider.displayName))
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.notif_exposure_alert_body_long, provider.displayName)
                ))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent)
                .addAction(
                    R.drawable.ic_tailguard_mesh,
                    context.getString(R.string.notif_action_open_internet_panel),
                    pendingPanel
                )
                .build()

            runCatching {
                nm.notify(ALERT_NOTIFICATION_ID, alertNotif)
            }
        }

        fun cancelExposureAlert(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            runCatching { nm.cancel(ALERT_NOTIFICATION_ID) }
        }

        private fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

                val monitorChannel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_monitor_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notif_channel_monitor_desc)
                    setShowBadge(false)
                }

                val alertChannel = NotificationChannel(
                    ALERT_CHANNEL_ID,
                    context.getString(R.string.notif_channel_alert_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_alert_desc)
                    enableVibration(true)
                    setShowBadge(true)
                }

                nm.createNotificationChannel(monitorChannel)
                nm.createNotificationChannel(alertChannel)
            }
        }

        private fun buildStatusNotification(context: Context, state: OrchestratorState): Notification {
            val openAppIntent = PendingIntent.getActivity(
                context,
                100,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val togglePendingIntent = PendingIntent.getService(
                context,
                101,
                Intent(context, NetworkMonitorService::class.java).apply {
                    action = ACTION_NOTIFICATION_TOGGLE_VPN
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val providerName = state.activeProvider.displayName

            val title = when (state.posture) {
                VpnPosture.VPN_ACTIVE,
                VpnPosture.TAILSCALE_ACTIVE -> context.getString(R.string.notif_monitor_title_protected_fmt, providerName)
                VpnPosture.PREEMPTIVE_SHIELD -> context.getString(R.string.notif_monitor_title_preemptive_fmt, providerName)
                VpnPosture.TRUSTED_BYPASS -> context.getString(R.string.notif_monitor_title_trusted)
                VpnPosture.HANDSHAKE_PENDING -> context.getString(R.string.notif_monitor_title_connecting_fmt, providerName)
                VpnPosture.KILL_SWITCH_LOCKDOWN,
                VpnPosture.UNPROTECTED -> context.getString(R.string.notif_monitor_title_standby)
            }

            val body = VpnAutomationController.describeNetwork(state.snapshot, state.matchedTrustedNetwork)
            val iconRes = R.drawable.ic_tailguard_mesh

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(openAppIntent)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(
                    R.drawable.ic_tailguard_mesh,
                    context.getString(R.string.notif_action_toggle_vpn),
                    togglePendingIntent
                )
                .build()
        }
    }
}
