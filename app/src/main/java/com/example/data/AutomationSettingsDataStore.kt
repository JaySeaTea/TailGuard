package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.automationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tailguard_automation_prefs"
)

class AutomationSettingsDataStore(private val context: Context) {

    private object Keys {
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect_enabled")
        val ACTIVE_PROVIDER = stringPreferencesKey("active_vpn_provider")
        val WIREGUARD_TUNNEL = stringPreferencesKey("wireguard_tunnel_name")
        val SAVED_WIREGUARD_TUNNELS = stringPreferencesKey("saved_wireguard_tunnels")
        val PREEMPTIVE_RSSI = booleanPreferencesKey("preemptive_rssi_enabled")
        val RSSI_THRESHOLD = intPreferencesKey("rssi_threshold_dbm")
        val EXPOSURE_ALERT = booleanPreferencesKey("exposure_alert_enabled")
        val AUTO_DISCONNECT_TRUSTED = booleanPreferencesKey("auto_disconnect_trusted")
        val DARK_THEME = booleanPreferencesKey("dark_theme_enabled")

        // Legacy keys
        val KILL_SWITCH = booleanPreferencesKey("kill_switch_enabled")
        val STRICT_CELLULAR_KS = booleanPreferencesKey("strict_cellular_only_ks")
    }

    val settingsFlow: Flow<AutomationSettings> = context.automationDataStore.data.map { prefs ->
        val providerStr = prefs[Keys.ACTIVE_PROVIDER]
        val rawTunnels = prefs[Keys.SAVED_WIREGUARD_TUNNELS]
        val defaultTunnels = listOf("Home Router", "ProtonVPN", "wg0")
        val parsedTunnels = if (!rawTunnels.isNullOrBlank()) {
            rawTunnels.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            defaultTunnels
        }
        val activeTunnel = prefs[Keys.WIREGUARD_TUNNEL] ?: parsedTunnels.firstOrNull() ?: "Home Router"
        val finalTunnels = if (parsedTunnels.none { it.equals(activeTunnel, ignoreCase = true) }) {
            listOf(activeTunnel) + parsedTunnels
        } else {
            parsedTunnels
        }

        AutomationSettings(
            autoConnectEnabled = prefs[Keys.AUTO_CONNECT] ?: true,
            activeProvider = VpnProvider.fromId(providerStr),
            wireguardTunnelName = activeTunnel,
            savedWireguardTunnels = finalTunnels,
            preemptiveRssiEnabled = prefs[Keys.PREEMPTIVE_RSSI] ?: true,
            rssiThresholdDbm = prefs[Keys.RSSI_THRESHOLD] ?: -77,
            exposureAlertEnabled = prefs[Keys.EXPOSURE_ALERT] ?: true,
            autoDisconnectOnTrusted = prefs[Keys.AUTO_DISCONNECT_TRUSTED] ?: true,
            darkThemeEnabled = prefs[Keys.DARK_THEME] ?: true,
            killSwitchEnabled = prefs[Keys.KILL_SWITCH] ?: false,
            strictCellularOnlyKillSwitch = prefs[Keys.STRICT_CELLULAR_KS] ?: true
        )
    }

    suspend fun getSnapshot(): AutomationSettings = settingsFlow.first()

    suspend fun setAutoConnectEnabled(enabled: Boolean) {
        context.automationDataStore.edit { it[Keys.AUTO_CONNECT] = enabled }
    }

    suspend fun setActiveProvider(provider: VpnProvider) {
        context.automationDataStore.edit { it[Keys.ACTIVE_PROVIDER] = provider.id }
    }

    suspend fun setWireGuardTunnelName(tunnelName: String) {
        val sanitized = tunnelName.trim().ifEmpty { "Home Router" }
        context.automationDataStore.edit { prefs ->
            prefs[Keys.WIREGUARD_TUNNEL] = sanitized
            val existing = (prefs[Keys.SAVED_WIREGUARD_TUNNELS] ?: "")
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()
            if (existing.none { it.equals(sanitized, ignoreCase = true) }) {
                existing.add(0, sanitized)
                prefs[Keys.SAVED_WIREGUARD_TUNNELS] = existing.joinToString("\n")
            }
        }
    }

    suspend fun addWireGuardTunnel(tunnelName: String) {
        val sanitized = tunnelName.trim()
        if (sanitized.isEmpty()) return
        context.automationDataStore.edit { prefs ->
            val existing = (prefs[Keys.SAVED_WIREGUARD_TUNNELS] ?: "")
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()
            if (existing.none { it.equals(sanitized, ignoreCase = true) }) {
                existing.add(0, sanitized)
                prefs[Keys.SAVED_WIREGUARD_TUNNELS] = existing.joinToString("\n")
            }
            prefs[Keys.WIREGUARD_TUNNEL] = sanitized
        }
    }

    suspend fun removeWireGuardTunnel(tunnelName: String) {
        val sanitized = tunnelName.trim()
        context.automationDataStore.edit { prefs ->
            val existing = (prefs[Keys.SAVED_WIREGUARD_TUNNELS] ?: "")
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals(sanitized, ignoreCase = true) }
            prefs[Keys.SAVED_WIREGUARD_TUNNELS] = existing.joinToString("\n")
            val currentActive = prefs[Keys.WIREGUARD_TUNNEL]
            if (currentActive.equals(sanitized, ignoreCase = true)) {
                prefs[Keys.WIREGUARD_TUNNEL] = existing.firstOrNull() ?: "Home Router"
            }
        }
    }

    suspend fun setPreemptiveRssiEnabled(enabled: Boolean) {
        context.automationDataStore.edit { it[Keys.PREEMPTIVE_RSSI] = enabled }
    }

    suspend fun setRssiThresholdDbm(dbm: Int) {
        val coerced = dbm.coerceIn(-95, -55)
        context.automationDataStore.edit { it[Keys.RSSI_THRESHOLD] = coerced }
    }

    suspend fun setExposureAlertEnabled(enabled: Boolean) {
        context.automationDataStore.edit { it[Keys.EXPOSURE_ALERT] = enabled }
    }

    suspend fun setAutoDisconnectOnTrusted(enabled: Boolean) {
        context.automationDataStore.edit { it[Keys.AUTO_DISCONNECT_TRUSTED] = enabled }
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        context.automationDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setKillSwitchEnabled(enabled: Boolean) {
        context.automationDataStore.edit { it[Keys.KILL_SWITCH] = enabled }
    }

    suspend fun setStrictCellularOnlyKillSwitch(enabled: Boolean) {
        context.automationDataStore.edit { it[Keys.STRICT_CELLULAR_KS] = enabled }
    }
}
