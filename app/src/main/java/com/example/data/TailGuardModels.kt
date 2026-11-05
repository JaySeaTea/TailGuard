package com.example.data

enum class VpnProvider(
    val id: String,
    val displayName: String,
    val packageName: String
) {
    TAILSCALE("tailscale", "Tailscale", "com.tailscale.ipn"),
    WIREGUARD("wireguard", "WireGuard", "com.wireguard.android");

    companion object {
        fun fromId(id: String?): VpnProvider {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: TAILSCALE
        }
    }
}

enum class MatchMode {
    FINGERPRINT,
    SSID,
    BOTH
}

enum class TransitionEventType {
    TRUSTED_CONNECTED,
    UNTRUSTED_DETECTED,
    VPN_CONNECT_SENT,
    VPN_DISCONNECT_SENT,
    VPN_CONNECTED,
    VPN_DISCONNECTED,
    PREEMPTIVE_CONNECT_TRIGGERED,
    EXPOSURE_ALERT_POSTED,
    MANUAL_TOGGLE,

    // Legacy event types retained for database schema backward-compatibility
    KILL_SWITCH_ENGAGED,
    KILL_SWITCH_RELEASED,
    TAILSCALE_CONNECT_SENT,
    TAILSCALE_DISCONNECT_SENT,
    TAILSCALE_CONNECTED,
    TAILSCALE_DISCONNECTED
}

enum class VpnPosture {
    VPN_ACTIVE,
    PREEMPTIVE_SHIELD,
    HANDSHAKE_PENDING,
    TRUSTED_BYPASS,
    UNPROTECTED,

    // Legacy postures retained for UI/Widget backward-compatibility
    TAILSCALE_ACTIVE,
    KILL_SWITCH_LOCKDOWN
}

enum class TransportKind {
    WIFI,
    CELLULAR_5G,
    CELLULAR_LTE,
    ETHERNET,
    NONE
}

enum class LogFilter {
    ALL,
    VPN_INTENTS,
    NETWORK_SHIFTS,
    ALERTS
}

data class ActiveNetworkSnapshot(
    val transportKind: TransportKind = TransportKind.NONE,
    val transportLabel: String = "Offline",
    val subnetCidr: String = "",
    val gatewayIp: String = "",
    val dnsServers: List<String> = emptyList(),
    val gatewayAndDnsSignature: String = "",
    val ssid: String? = null,
    val wifiRssiDbm: Int? = null,
    val isValidated: Boolean = true,
    val isVpnTransportPresent: Boolean = false,
    val isVpnTunnelActive: Boolean = false,
    val vpnIp: String? = null,
    val isKillSwitchSinkholeActive: Boolean = false
) {
    // Backwards-compatibility accessors
    val isTailscaleTunnelActive: Boolean
        get() = isVpnTunnelActive

    val tailscaleIp: String?
        get() = vpnIp
}

data class AutomationSettings(
    val autoConnectEnabled: Boolean = true,
    val activeProvider: VpnProvider = VpnProvider.TAILSCALE,
    val wireguardTunnelName: String = "Home Router",
    val savedWireguardTunnels: List<String> = listOf("Home Router", "ProtonVPN", "wg0"),
    val preemptiveRssiEnabled: Boolean = true,
    val rssiThresholdDbm: Int = -77,
    val exposureAlertEnabled: Boolean = true,
    val autoDisconnectOnTrusted: Boolean = true,
    val darkThemeEnabled: Boolean = true,

    // Legacy properties for backward compatibility
    val killSwitchEnabled: Boolean = false,
    val strictCellularOnlyKillSwitch: Boolean = true
)
