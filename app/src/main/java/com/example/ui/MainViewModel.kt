package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.TailGuardApplication
import com.example.data.ActiveNetworkSnapshot
import com.example.data.AutomationSettings
import com.example.data.LogFilter
import com.example.data.MatchMode
import com.example.data.TailGuardRepository
import com.example.data.TransitionEventType
import com.example.data.TransitionLogEntity
import com.example.data.TrustedNetworkEntity
import com.example.data.VpnPosture
import com.example.data.VpnProvider
import com.example.service.NetworkFingerprintInspector
import com.example.service.NetworkMonitorService
import com.example.service.TailscaleBridge
import com.example.service.VpnAutomationController
import com.example.service.WireGuardBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TailGuardTab(val routeId: String) {
    DASHBOARD("dashboard"),
    TRUSTED_NETWORKS("trusted_networks"),
    TRANSITION_LOGS("transition_logs")
}

data class DashboardUiState(
    val selectedTab: TailGuardTab = TailGuardTab.DASHBOARD,
    val snapshot: ActiveNetworkSnapshot = ActiveNetworkSnapshot(),
    val matchedTrustedNetwork: TrustedNetworkEntity? = null,
    val posture: VpnPosture = VpnPosture.UNPROTECTED,
    val isHandshakePending: Boolean = false,
    val isPreemptiveTriggerActive: Boolean = false,
    val isManualOverrideActive: Boolean = false,
    val activeProvider: VpnProvider = VpnProvider.TAILSCALE,
    val isClientInstalled: Boolean = false,
    val hasWireGuardPermission: Boolean = true,
    val settings: AutomationSettings = AutomationSettings(),
    val trustedNetworks: List<TrustedNetworkEntity> = emptyList(),
    val allLogs: List<TransitionLogEntity> = emptyList(),
    val filteredLogs: List<TransitionLogEntity> = emptyList(),
    val selectedLogFilter: LogFilter = LogFilter.ALL,
    val hasFineLocationPermission: Boolean = false,
    val bannerMessage: String? = null
)

class MainViewModel(
    private val appContext: Context,
    private val repository: TailGuardRepository,
    private val controller: VpnAutomationController,
    private val inspector: NetworkFingerprintInspector,
    private val tailscaleBridge: TailscaleBridge,
    private val wireguardBridge: WireGuardBridge
) : ViewModel() {

    private val selectedTabFlow = MutableStateFlow(TailGuardTab.DASHBOARD)
    private val selectedLogFilterFlow = MutableStateFlow(LogFilter.ALL)
    private val localBannerFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        selectedTabFlow,
        controller.state,
        repository.settings,
        repository.trustedNetworks,
        combine(repository.recentLogs, selectedLogFilterFlow, localBannerFlow) { logs, filter, banner ->
            Triple(logs, filter, banner)
        }
    ) { tab, orch, settings, networks, (logs, filter, localBanner) ->
        val filtered = filterLogs(logs, filter)
        val clientInstalled = controller.isClientInstalled(settings.activeProvider)
        DashboardUiState(
            selectedTab = tab,
            snapshot = orch.snapshot,
            matchedTrustedNetwork = orch.matchedTrustedNetwork,
            posture = orch.posture,
            isHandshakePending = orch.isHandshakePending,
            isPreemptiveTriggerActive = orch.isPreemptiveTriggerActive,
            isManualOverrideActive = orch.isManualOverrideActive,
            activeProvider = settings.activeProvider,
            isClientInstalled = clientInstalled,
            hasWireGuardPermission = orch.hasWireGuardPermission,
            settings = settings,
            trustedNetworks = networks,
            allLogs = logs,
            filteredLogs = filtered,
            selectedLogFilter = filter,
            hasFineLocationPermission = inspector.hasFineLocationPermission(),
            bannerMessage = localBanner ?: orch.lastActionBanner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DashboardUiState()
    )

    fun selectTab(tab: TailGuardTab) {
        selectedTabFlow.value = tab
    }

    fun selectLogFilter(filter: LogFilter) {
        selectedLogFilterFlow.value = filter
    }

    fun refreshTelemetry() {
        controller.refreshLiveState(triggerAutomation = false)
    }

    fun onRapidToggleClicked() {
        viewModelScope.launch {
            controller.rapidToggleVpn(sourceLabel = "Dashboard Hero")
        }
    }

    fun onTestBroadcastClicked() {
        viewModelScope.launch {
            controller.testProviderBroadcast()
        }
    }

    fun setActiveProvider(provider: VpnProvider) {
        viewModelScope.launch {
            repository.setActiveProvider(provider)
            controller.evaluateRulesNow()
            postBanner("Selected VPN Client: ${provider.displayName}")
        }
    }

    fun setWireGuardTunnelName(tunnelName: String) {
        viewModelScope.launch {
            val sanitized = tunnelName.trim().ifEmpty { "Home Router" }
            repository.setWireGuardTunnelName(sanitized)
            postBanner("Active WireGuard tunnel set to: $sanitized")
        }
    }

    fun addWireGuardTunnel(tunnelName: String) {
        viewModelScope.launch {
            val sanitized = tunnelName.trim()
            if (sanitized.isNotEmpty()) {
                repository.addWireGuardTunnel(sanitized)
                postBanner("Added WireGuard tunnel: $sanitized")
            }
        }
    }

    fun removeWireGuardTunnel(tunnelName: String) {
        viewModelScope.launch {
            repository.removeWireGuardTunnel(tunnelName)
            postBanner("Removed WireGuard tunnel: $tunnelName")
        }
    }

    fun setPreemptiveRssiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setPreemptiveRssiEnabled(enabled)
            controller.evaluateRulesNow()
            postBanner(if (enabled) "Pre-Emptive Wi-Fi Shield enabled" else "Pre-Emptive Shield paused")
        }
    }

    fun setRssiThresholdDbm(dbm: Int) {
        viewModelScope.launch {
            repository.setRssiThresholdDbm(dbm)
            postBanner("Pre-emptive trigger threshold set to $dbm dBm")
        }
    }

    fun setExposureAlertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setExposureAlertEnabled(enabled)
            postBanner(if (enabled) "5G Exposure Alert armed" else "Exposure Alert disabled")
        }
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoConnect(enabled)
            if (enabled) {
                NetworkMonitorService.startPassiveMonitor(appContext)
                controller.evaluateRulesNow()
                postBanner("Passive Auto-Guard enabled")
            } else {
                NetworkMonitorService.stopPassiveMonitor(appContext)
                postBanner("Auto-Guard paused")
            }
        }
    }

    fun setAutoDisconnectOnTrusted(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoDisconnectOnTrusted(enabled)
            controller.evaluateRulesNow()
        }
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            val current = uiState.value.settings.darkThemeEnabled
            repository.setDarkTheme(!current)
        }
    }

    fun saveTrustedNetwork(
        existingId: Long?,
        label: String,
        matchMode: MatchMode,
        subnetCidr: String,
        gatewayAndDnsSignature: String,
        ssid: String?,
        autoDisconnectVpn: Boolean
    ) {
        viewModelScope.launch {
            val normalizedLabel = label.trim().ifEmpty { "Trusted Wi-Fi" }
            val entity = TrustedNetworkEntity(
                id = existingId ?: 0L,
                label = normalizedLabel,
                matchMode = matchMode,
                subnetCidr = subnetCidr.trim(),
                gatewayAndDnsSignature = gatewayAndDnsSignature.trim(),
                ssid = TailGuardRepository.normalizeSsid(ssid),
                autoDisconnectVpn = autoDisconnectVpn,
                isEnabled = true
            )
            repository.saveTrustedNetwork(entity)
            controller.evaluateRulesNow()
            postBanner("Saved trusted network \"$normalizedLabel\"")
        }
    }

    fun toggleTrustedNetworkEnabled(network: TrustedNetworkEntity, enabled: Boolean) {
        viewModelScope.launch {
            repository.updateTrustedNetwork(network.copy(isEnabled = enabled))
            controller.evaluateRulesNow()
        }
    }

    fun deleteTrustedNetwork(network: TrustedNetworkEntity) {
        viewModelScope.launch {
            repository.deleteTrustedNetwork(network.id)
            controller.evaluateRulesNow()
            postBanner("Removed \"${network.label}\"")
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            postBanner("Transition log cleared")
        }
    }

    fun shareLogs(context: Context) {
        viewModelScope.launch {
            val csvText = repository.buildExportText()
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "TailGuard Connection Transition Log")
                putExtra(Intent.EXTRA_TEXT, csvText)
            }
            val chooser = Intent.createChooser(sendIntent, "Export Transition Log").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(chooser) }
        }
    }

    fun openActiveClientOrPlayStore(context: Context) {
        val provider = uiState.value.settings.activeProvider
        val launchIntent = when (provider) {
            VpnProvider.TAILSCALE -> tailscaleBridge.buildOpenTailscaleIntent()
                ?: tailscaleBridge.buildPlayStoreTailscaleIntent()
            VpnProvider.WIREGUARD -> wireguardBridge.buildOpenWireGuardIntent()
                ?: wireguardBridge.buildPlayStoreWireGuardIntent()
        }
        runCatching { context.startActivity(launchIntent) }
    }

    fun openInternetPanel(context: Context) {
        val panelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(panelIntent) }
    }

    fun openSystemVpnSettings(context: Context) {
        runCatching { context.startActivity(tailscaleBridge.buildSystemVpnSettingsIntent()) }
    }

    fun dismissBanner() {
        localBannerFlow.value = null
        controller.clearBanner()
    }

    private fun postBanner(message: String) {
        localBannerFlow.value = message
    }

    companion object {
        fun filterLogs(
            logs: List<TransitionLogEntity>,
            filter: LogFilter
        ): List<TransitionLogEntity> {
            return when (filter) {
                LogFilter.ALL -> logs
                LogFilter.VPN_INTENTS -> logs.filter {
                    it.eventType == TransitionEventType.VPN_CONNECT_SENT ||
                        it.eventType == TransitionEventType.VPN_DISCONNECT_SENT ||
                        it.eventType == TransitionEventType.VPN_CONNECTED ||
                        it.eventType == TransitionEventType.VPN_DISCONNECTED ||
                        it.eventType == TransitionEventType.MANUAL_TOGGLE ||
                        it.eventType == TransitionEventType.TAILSCALE_CONNECT_SENT ||
                        it.eventType == TransitionEventType.TAILSCALE_DISCONNECT_SENT ||
                        it.eventType == TransitionEventType.TAILSCALE_CONNECTED ||
                        it.eventType == TransitionEventType.TAILSCALE_DISCONNECTED
                }
                LogFilter.NETWORK_SHIFTS -> logs.filter {
                    it.eventType == TransitionEventType.TRUSTED_CONNECTED ||
                        it.eventType == TransitionEventType.UNTRUSTED_DETECTED
                }
                LogFilter.ALERTS -> logs.filter {
                    it.eventType == TransitionEventType.PREEMPTIVE_CONNECT_TRIGGERED ||
                        it.eventType == TransitionEventType.EXPOSURE_ALERT_POSTED ||
                        it.eventType == TransitionEventType.KILL_SWITCH_ENGAGED ||
                        it.eventType == TransitionEventType.KILL_SWITCH_RELEASED
                }
            }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TailGuardApplication)
                val c = app.container
                MainViewModel(
                    appContext = app.applicationContext,
                    repository = c.repository,
                    controller = c.controller,
                    inspector = c.inspector,
                    tailscaleBridge = c.tailscaleBridge,
                    wireguardBridge = c.wireguardBridge
                )
            }
        }
    }
}
