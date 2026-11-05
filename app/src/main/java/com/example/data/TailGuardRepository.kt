package com.example.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow

class TailGuardRepository(
    private val trustedNetworkDao: TrustedNetworkDao,
    private val transitionLogDao: TransitionLogDao,
    private val settingsStore: AutomationSettingsDataStore
) {
    val trustedNetworks: Flow<List<TrustedNetworkEntity>> = trustedNetworkDao.observeAll()
    val recentLogs: Flow<List<TransitionLogEntity>> = transitionLogDao.observeRecentLogs()
    val settings: Flow<AutomationSettings> = settingsStore.settingsFlow

    suspend fun getSettingsSnapshot(): AutomationSettings = settingsStore.getSnapshot()

    suspend fun getEnabledTrustedNetworks(): List<TrustedNetworkEntity> =
        trustedNetworkDao.getEnabledNetworks()

    suspend fun saveTrustedNetwork(network: TrustedNetworkEntity): Long =
        trustedNetworkDao.upsert(network)

    suspend fun updateTrustedNetwork(network: TrustedNetworkEntity) =
        trustedNetworkDao.update(network)

    suspend fun deleteTrustedNetwork(id: Long) =
        trustedNetworkDao.deleteById(id)

    suspend fun recordTransition(
        eventType: TransitionEventType,
        networkSummary: String,
        vpnActionSummary: String
    ) {
        transitionLogDao.insert(
            TransitionLogEntity(
                timestamp = System.currentTimeMillis(),
                eventType = eventType,
                networkSummary = networkSummary,
                vpnActionSummary = vpnActionSummary
            )
        )
        transitionLogDao.pruneOldEntries()
    }

    suspend fun clearLogs() {
        transitionLogDao.clearAll()
    }

    suspend fun buildExportText(): String {
        val entries = transitionLogDao.getAllForExport()
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return buildString {
            appendLine("# TailGuard Connection Transition Audit Log")
            appendLine("# Generated: ${formatter.format(Date())}")
            appendLine("timestamp,event_type,network,action_summary")
            for (entry in entries) {
                val ts = formatter.format(Date(entry.timestamp))
                val net = entry.networkSummary.replace("\"", "'")
                val act = entry.vpnActionSummary.replace("\"", "'")
                appendLine("\"$ts\",\"${entry.eventType.name}\",\"$net\",\"$act\"")
            }
        }
    }

    suspend fun setAutoConnect(enabled: Boolean) = settingsStore.setAutoConnectEnabled(enabled)
    suspend fun setActiveProvider(provider: VpnProvider) = settingsStore.setActiveProvider(provider)
    suspend fun setWireGuardTunnelName(tunnelName: String) = settingsStore.setWireGuardTunnelName(tunnelName)
    suspend fun addWireGuardTunnel(tunnelName: String) = settingsStore.addWireGuardTunnel(tunnelName)
    suspend fun removeWireGuardTunnel(tunnelName: String) = settingsStore.removeWireGuardTunnel(tunnelName)
    suspend fun setPreemptiveRssiEnabled(enabled: Boolean) = settingsStore.setPreemptiveRssiEnabled(enabled)
    suspend fun setRssiThresholdDbm(dbm: Int) = settingsStore.setRssiThresholdDbm(dbm)
    suspend fun setExposureAlertEnabled(enabled: Boolean) = settingsStore.setExposureAlertEnabled(enabled)
    suspend fun setKillSwitch(enabled: Boolean) = settingsStore.setKillSwitchEnabled(enabled)
    suspend fun setStrictCellularOnlyKillSwitch(enabled: Boolean) =
        settingsStore.setStrictCellularOnlyKillSwitch(enabled)
    suspend fun setAutoDisconnectOnTrusted(enabled: Boolean) =
        settingsStore.setAutoDisconnectOnTrusted(enabled)
    suspend fun setDarkTheme(enabled: Boolean) = settingsStore.setDarkThemeEnabled(enabled)

    companion object {
        /**
         * Matches active Wi-Fi / Ethernet links against user-configured Trusted Networks.
         *
         * Resilient to Tailscale MagicDNS / Pi-hole Exit Node DNS overrides and Android 17 IPv6
         * Router Advertisement DNS churn:
         * - Compares normalized `subnetCidr` (`192.168.50.0/24`)
         * - Compares Gateway IP (`gw:192.168.50.1`) when both rule and active link report a gateway,
         *   without failing if DNS server lists shift when Tailscale connects or disconnects!
         */
        fun findMatchingTrustedNetwork(
            snapshot: ActiveNetworkSnapshot,
            candidates: List<TrustedNetworkEntity>
        ): TrustedNetworkEntity? {
            if (snapshot.transportKind != TransportKind.WIFI && snapshot.transportKind != TransportKind.ETHERNET) {
                return null
            }
            // Critical: Do NOT treat an unvalidated or rejected Wi-Fi link as a trusted network!
            if (snapshot.transportKind == TransportKind.WIFI && !snapshot.isValidated) {
                return null
            }
            val activeSsid = normalizeSsid(snapshot.ssid)
            val activeCidr = snapshot.subnetCidr.trim()
            val activeGateway = snapshot.gatewayIp.trim()
                .ifEmpty { extractGatewayToken(snapshot.gatewayAndDnsSignature) }

            return candidates.firstOrNull { rule ->
                if (!rule.isEnabled) return@firstOrNull false
                val ruleSsid = normalizeSsid(rule.ssid)
                val ruleCidr = rule.subnetCidr.trim()
                val ruleGateway = extractGatewayToken(rule.gatewayAndDnsSignature)

                val fingerprintMatches = if (ruleCidr.isNotEmpty()) {
                    val cidrMatch = activeCidr.equals(ruleCidr, ignoreCase = true)
                    val gwMatch = ruleGateway.isEmpty() ||
                        activeGateway.isEmpty() ||
                        activeGateway.equals(ruleGateway, ignoreCase = true)
                    cidrMatch && gwMatch
                } else {
                    false
                }

                val ssidMatches = !ruleSsid.isNullOrEmpty() &&
                    !activeSsid.isNullOrEmpty() &&
                    activeSsid.equals(ruleSsid, ignoreCase = true)

                when (rule.matchMode) {
                    MatchMode.FINGERPRINT -> fingerprintMatches
                    MatchMode.SSID -> ssidMatches
                    MatchMode.BOTH -> fingerprintMatches && ssidMatches
                }
            }
        }

        /**
         * Extracts `192.168.50.1` from `"gw:192.168.50.1 | dns:..."` or raw gateway strings,
         * ignoring volatile `dns:...` tokens that change when Pi-hole / MagicDNS / IPv6 RA activates.
         */
        fun extractGatewayToken(signature: String): String {
            val trimmed = signature.trim()
            if (trimmed.isEmpty()) return ""
            val segments = trimmed.split("|").map { it.trim() }
            for (seg in segments) {
                if (seg.startsWith("gw:", ignoreCase = true)) {
                    return seg.substringAfter(":").trim()
                }
            }
            // If user typed a plain IP without "gw:" prefix and without "dns:"
            if (!trimmed.startsWith("dns:", ignoreCase = true) && !trimmed.contains(" ")) {
                return trimmed
            }
            return ""
        }

        fun normalizeSsid(raw: String?): String? {
            val cleaned = raw?.trim()?.removePrefix("\"")?.removeSuffix("\"")?.trim()
            if (cleaned.isNullOrEmpty() || cleaned.equals("<unknown ssid>", ignoreCase = true)) {
                return null
            }
            return cleaned
        }
    }
}
