package com.example.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Controller bridge for the official WireGuard Android client (`com.wireguard.android`).
 *
 * WireGuard Android supports remote control via broadcast intents when:
 * 1. The user grants `com.wireguard.android.permission.CONTROL_TUNNELS` (dangerous permission).
 * 2. The user enables "Settings -> Advanced -> Allow remote control apps" inside WireGuard.
 *
 * Actions:
 * - `com.wireguard.android.action.SET_TUNNEL_UP` (extra `tunnel`: Tunnel name)
 * - `com.wireguard.android.action.SET_TUNNEL_DOWN` (extra `tunnel`: Tunnel name)
 */
class WireGuardBridge(private val context: Context) {

    fun isWireGuardInstalled(): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(WIREGUARD_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    fun hasControlPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_CONTROL_TUNNELS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun sendTunnelUpBroadcast(tunnelName: String = "wg0"): Boolean {
        val targetTunnel = tunnelName.trim().ifEmpty { "wg0" }
        val intent = Intent(ACTION_SET_TUNNEL_UP).apply {
            setPackage(WIREGUARD_PACKAGE)
            component = ComponentName(WIREGUARD_PACKAGE, WIREGUARD_RECEIVER)
            putExtra(EXTRA_TUNNEL, targetTunnel)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        return runCatching {
            context.sendBroadcast(intent)
            true
        }.getOrDefault(false)
    }

    fun sendTunnelDownBroadcast(tunnelName: String = "wg0"): Boolean {
        val targetTunnel = tunnelName.trim().ifEmpty { "wg0" }
        val intent = Intent(ACTION_SET_TUNNEL_DOWN).apply {
            setPackage(WIREGUARD_PACKAGE)
            component = ComponentName(WIREGUARD_PACKAGE, WIREGUARD_RECEIVER)
            putExtra(EXTRA_TUNNEL, targetTunnel)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        return runCatching {
            context.sendBroadcast(intent)
            true
        }.getOrDefault(false)
    }

    fun buildOpenWireGuardIntent(): Intent? {
        return context.packageManager.getLaunchIntentForPackage(WIREGUARD_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildPlayStoreWireGuardIntent(): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$WIREGUARD_PACKAGE")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    companion object {
        const val WIREGUARD_PACKAGE = "com.wireguard.android"
        const val WIREGUARD_RECEIVER = "com.wireguard.android.model.TunnelManager\$IntentReceiver"
        const val ACTION_SET_TUNNEL_UP = "com.wireguard.android.action.SET_TUNNEL_UP"
        const val ACTION_SET_TUNNEL_DOWN = "com.wireguard.android.action.SET_TUNNEL_DOWN"
        const val EXTRA_TUNNEL = "tunnel"
        const val PERMISSION_CONTROL_TUNNELS = "com.wireguard.android.permission.CONTROL_TUNNELS"
    }
}
