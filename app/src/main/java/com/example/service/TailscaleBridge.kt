package com.example.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

class TailscaleBridge(private val context: Context) {

    fun isTailscaleInstalled(): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(TAILSCALE_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    /**
     * Dispatches the official Tailscale explicit broadcast intent (`com.tailscale.ipn.CONNECT_VPN`)
     * targeting `com.tailscale.ipn.IPNReceiver`.
     */
    fun sendConnectBroadcast(): Boolean {
        val intent = Intent(ACTION_CONNECT_VPN).apply {
            component = ComponentName(TAILSCALE_PACKAGE, TAILSCALE_RECEIVER)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        return runCatching {
            context.sendBroadcast(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * Dispatches the official Tailscale explicit broadcast intent (`com.tailscale.ipn.DISCONNECT_VPN`)
     * targeting `com.tailscale.ipn.IPNReceiver`.
     */
    fun sendDisconnectBroadcast(): Boolean {
        val intent = Intent(ACTION_DISCONNECT_VPN).apply {
            component = ComponentName(TAILSCALE_PACKAGE, TAILSCALE_RECEIVER)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        return runCatching {
            context.sendBroadcast(intent)
            true
        }.getOrDefault(false)
    }

    fun buildOpenTailscaleIntent(): Intent? {
        return context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildPlayStoreTailscaleIntent(): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$TAILSCALE_PACKAGE")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildSystemVpnSettingsIntent(): Intent {
        return Intent(Settings.ACTION_VPN_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    companion object {
        const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
        const val TAILSCALE_RECEIVER = "com.tailscale.ipn.IPNReceiver"
        const val ACTION_CONNECT_VPN = "com.tailscale.ipn.CONNECT_VPN"
        const val ACTION_DISCONNECT_VPN = "com.tailscale.ipn.DISCONNECT_VPN"
    }
}
