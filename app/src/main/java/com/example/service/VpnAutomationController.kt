package com.example.service

import android.content.Context
import com.example.data.ActiveNetworkSnapshot
import com.example.data.AutomationSettings
import com.example.data.TailGuardRepository
import com.example.data.TransitionEventType
import com.example.data.TransportKind
import com.example.data.TrustedNetworkEntity
import com.example.data.VpnPosture
import com.example.data.VpnProvider
import com.example.tile.TailGuardTileService
import com.example.widget.TailGuardAppWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class OrchestratorState(
    val snapshot: ActiveNetworkSnapshot = ActiveNetworkSnapshot(),
    val matchedTrustedNetwork: TrustedNetworkEntity? = null,
    val posture: VpnPosture = VpnPosture.UNPROTECTED,
    val activeProvider: VpnProvider = VpnProvider.TAILSCALE,
    val isClientInstalled: Boolean = false,
    val isHandshakePending: Boolean = false,
    val isPreemptiveTriggerActive: Boolean = false,
    val isManualOverrideActive: Boolean = false,
    val hasWireGuardPermission: Boolean = true,
    val lastActionBanner: String? = null
) {
    val isTailscaleInstalled: Boolean
        get() = isClientInstalled
}

class VpnAutomationController(
    private val appContext: Context,
    private val repository: TailGuardRepository,
    private val inspector: NetworkFingerprintInspector,
    private val tailscaleBridge: TailscaleBridge,
    private val wireguardBridge: WireGuardBridge
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val evaluationMutex = Mutex()

    private var handoffWorkflowJob: Job? = null
    private var debounceEvaluationJob: Job? = null
    private var preemptiveDebounceJob: Job? = null

    @Volatile
    private var isHandshakePendingFlag: Boolean = false

    @Volatile
    private var isPreemptiveActiveFlag: Boolean = false

    @Volatile
    private var isManualOverrideActive: Boolean = false

    private var lastEvaluatedPolicyKey: String? = null
    private var wasPreviouslyUntrusted: Boolean = false
    private var lastVpnActive: Boolean? = null
    private var lastAutoBroadcastEpochMs: Long = 0L
    private var manualBypassActive: Boolean = false

    private val _state = MutableStateFlow(OrchestratorState())
    val state: StateFlow<OrchestratorState> = _state.asStateFlow()

    fun isClientInstalled(provider: VpnProvider): Boolean {
        return when (provider) {
            VpnProvider.TAILSCALE -> tailscaleBridge.isTailscaleInstalled()
            VpnProvider.WIREGUARD -> wireguardBridge.isWireGuardInstalled()
        }
    }

    fun refreshLiveState(triggerAutomation: Boolean = false) {
        if (!triggerAutomation) {
            refreshUiSnapshotOnly()
            return
        }
        debounceEvaluationJob?.cancel()
        debounceEvaluationJob = scope.launch {
            // Coalesce burst callbacks during Wi-Fi / 5G transitions
            delay(400L)
            evaluateInternal(triggerAutomation = true, forceEvaluate = false)
        }
    }

    fun evaluateRulesNow() {
        debounceEvaluationJob?.cancel()
        scope.launch {
            manualBypassActive = false
            evaluateInternal(triggerAutomation = true, forceEvaluate = true)
        }
    }

    private fun refreshUiSnapshotOnly() {
        scope.launch {
            evaluateInternal(triggerAutomation = false, forceEvaluate = false)
        }
    }

    suspend fun rapidToggleVpn(sourceLabel: String = "Dashboard") {
        debounceEvaluationJob?.cancel()
        cancelHandoffWorkflowSafely()

        val settings = repository.getSettingsSnapshot()
        val current = _state.value
        val netDesc = describeNetwork(current.snapshot, current.matchedTrustedNetwork)
        val provider = settings.activeProvider
        val clientInstalled = isClientInstalled(provider)

        if (current.snapshot.isVpnTunnelActive || isHandshakePendingFlag || isManualOverrideActive) {
            manualBypassActive = true
            isManualOverrideActive = false
            isHandshakePendingFlag = false
            isPreemptiveActiveFlag = false
            NetworkMonitorService.cancelExposureAlert(appContext)

            val sent = sendProviderDisconnect(provider, settings.wireguardTunnelName)
            repository.recordTransition(
                eventType = TransitionEventType.VPN_DISCONNECT_SENT,
                networkSummary = netDesc,
                vpnActionSummary = if (sent) {
                    "Rapid toggle ($sourceLabel): Dispatched disconnect to ${provider.displayName}"
                } else {
                    "Rapid toggle ($sourceLabel): Failed to dispatch disconnect to ${provider.displayName}"
                }
            )
            publishCurrentState(banner = "Sent disconnect broadcast to ${provider.displayName}")
            scheduleVerificationChecks()
        } else {
            manualBypassActive = false
            if (current.matchedTrustedNetwork != null) {
                isManualOverrideActive = true
            }
            if (provider == VpnProvider.WIREGUARD && !wireguardBridge.hasControlPermission()) {
                repository.recordTransition(
                    eventType = TransitionEventType.VPN_CONNECT_SENT,
                    networkSummary = netDesc,
                    vpnActionSummary = "WireGuard connect warning: 'CONTROL_TUNNELS' runtime permission is not yet granted"
                )
                publishCurrentState(banner = "⚠️ WireGuard control permission needed! Grant permission on Dashboard.")
            }
            val sent = sendProviderConnect(provider, settings.wireguardTunnelName)
            repository.recordTransition(
                eventType = TransitionEventType.VPN_CONNECT_SENT,
                networkSummary = netDesc,
                vpnActionSummary = if (clientInstalled && sent) {
                    if (isManualOverrideActive) {
                        "Rapid toggle ($sourceLabel): Manual VPN override on Trusted Wi-Fi -> Dispatched connect to ${provider.displayName}"
                    } else {
                        "Rapid toggle ($sourceLabel): Dispatched connect to ${provider.displayName}"
                    }
                } else {
                    "Rapid toggle ($sourceLabel): Sent connect broadcast (${provider.displayName} client not installed)"
                }
            )
            launchUntrustedHandoffWorkflow(
                provider = provider,
                tunnelName = settings.wireguardTunnelName,
                initialBanner = if (clientInstalled) {
                    if (isManualOverrideActive) {
                        "Manual VPN Override Active — connecting to ${provider.displayName}..."
                    } else {
                        "Dispatched connect to ${provider.displayName} — waiting for tunnel..."
                    }
                } else {
                    "Dispatched connect broadcast (${provider.displayName} app not detected)"
                }
            )
        }
    }

    suspend fun testProviderBroadcast() {
        cancelHandoffWorkflowSafely()
        val settings = repository.getSettingsSnapshot()
        val provider = settings.activeProvider
        val current = _state.value
        val installed = isClientInstalled(provider)
        val permOk = if (provider == VpnProvider.WIREGUARD) wireguardBridge.hasControlPermission() else true

        val sent = sendProviderConnect(provider, settings.wireguardTunnelName)
        val actionSummary = when {
            !installed -> "Diagnostic connect broadcast dispatched (${provider.displayName} package not installed)"
            !permOk -> "Diagnostic connect dispatched, but Android permission 'CONTROL_TUNNELS' is NOT granted yet"
            sent -> "Diagnostic connect broadcast delivered to ${provider.displayName}"
            else -> "Failed to send diagnostic broadcast to ${provider.displayName}"
        }
        repository.recordTransition(
            eventType = TransitionEventType.VPN_CONNECT_SENT,
            networkSummary = describeNetwork(current.snapshot, current.matchedTrustedNetwork),
            vpnActionSummary = actionSummary
        )
        launchUntrustedHandoffWorkflow(
            provider = provider,
            tunnelName = settings.wireguardTunnelName,
            initialBanner = when {
                !installed -> "Broadcast sent! Install ${provider.displayName} on device to complete tunnel."
                !permOk -> "⚠️ Sent, but WireGuard control permission is missing! Tap 'Grant Permission' below."
                else -> "Diagnostic connect sent to ${provider.displayName}!"
            }
        )
    }

    fun clearBanner() {
        _state.value = _state.value.copy(lastActionBanner = null)
    }

    private fun sendProviderConnect(provider: VpnProvider, wireguardTunnelName: String): Boolean {
        return when (provider) {
            VpnProvider.TAILSCALE -> tailscaleBridge.sendConnectBroadcast()
            VpnProvider.WIREGUARD -> wireguardBridge.sendTunnelUpBroadcast(wireguardTunnelName)
        }
    }

    private fun sendProviderDisconnect(provider: VpnProvider, wireguardTunnelName: String): Boolean {
        return when (provider) {
            VpnProvider.TAILSCALE -> tailscaleBridge.sendDisconnectBroadcast()
            VpnProvider.WIREGUARD -> wireguardBridge.sendTunnelDownBroadcast(wireguardTunnelName)
        }
    }

    private suspend fun cancelHandoffWorkflowSafely() {
        val currentJob = currentCoroutineContext()[Job]
        val target = handoffWorkflowJob
        if (target != null && target != currentJob && target.isActive) {
            target.cancel()
        }
    }

    private suspend fun evaluateInternal(triggerAutomation: Boolean, forceEvaluate: Boolean) {
        evaluationMutex.withLock {
            val settings = repository.getSettingsSnapshot()
            val trustedList = repository.getEnabledTrustedNetworks()
            var snap = inspector.captureSnapshot()

            // Wait once if Wi-Fi just associated and DHCP hasn't assigned an IPv4 CIDR yet
            if (triggerAutomation && snap.transportKind == TransportKind.WIFI && snap.subnetCidr.isEmpty()) {
                delay(500L)
                snap = inspector.captureSnapshot()
            }

            val matched = TailGuardRepository.findMatchingTrustedNetwork(snap, trustedList)
            val clientInstalled = isClientInstalled(settings.activeProvider)

            // Track verified external VPN tunnel up/down transitions
            val prevVpn = lastVpnActive
            if (prevVpn != null && prevVpn != snap.isVpnTunnelActive) {
                if (snap.isVpnTunnelActive) {
                    isHandshakePendingFlag = false
                    // Maintain Pre-Emptive Shield active flag while Wi-Fi signal is still weak
                    if (snap.transportKind != TransportKind.WIFI || snap.wifiRssiDbm == null || snap.wifiRssiDbm >= settings.rssiThresholdDbm + 6) {
                        isPreemptiveActiveFlag = false
                    }
                    NetworkMonitorService.cancelExposureAlert(appContext)
                    cancelHandoffWorkflowSafely()

                    repository.recordTransition(
                        eventType = TransitionEventType.VPN_CONNECTED,
                        networkSummary = describeNetwork(snap, matched),
                        vpnActionSummary = "${settings.activeProvider.displayName} tunnel verified (${snap.vpnIp ?: "Active"})"
                    )
                } else {
                    repository.recordTransition(
                        eventType = TransitionEventType.VPN_DISCONNECTED,
                        networkSummary = describeNetwork(snap, matched),
                        vpnActionSummary = "${settings.activeProvider.displayName} VPN tunnel disconnected"
                    )
                }
            }
            lastVpnActive = snap.isVpnTunnelActive

            val policyKey = "${snap.transportKind}|${snap.subnetCidr}|${matched?.id ?: "untrusted"}"
            val physicalPolicyChanged = policyKey != lastEvaluatedPolicyKey
            if (physicalPolicyChanged) {
                manualBypassActive = false
                isManualOverrideActive = false
            }

            // Check Pre-Emptive Wi-Fi RSSI Trigger (when connected to a Trusted Wi-Fi Network)
            if (matched != null && snap.transportKind == TransportKind.WIFI) {
                checkPreemptiveRssiTrigger(snap, matched, settings)
            } else {
                preemptiveDebounceJob?.cancel()
            }

            val workflowRunning = handoffWorkflowJob?.isActive == true

            if (triggerAutomation && settings.autoConnectEnabled) {
                val cooldownElapsed = (System.currentTimeMillis() - lastAutoBroadcastEpochMs) > 8_000L

                val isWeakWifiShielding = isPreemptiveActiveFlag ||
                    (snap.transportKind == TransportKind.WIFI && snap.wifiRssiDbm != null && snap.wifiRssiDbm < (settings.rssiThresholdDbm + 6))

                val needsUntrustedEnforcement = matched == null &&
                    snap.transportKind != TransportKind.NONE &&
                    !snap.isVpnTunnelActive &&
                    !workflowRunning &&
                    !manualBypassActive &&
                    cooldownElapsed

                // Do NOT disconnect VPN on Trusted Network while manual override or Pre-Emptive Shield is active!
                val needsTrustedDisconnect = matched != null &&
                    !isManualOverrideActive &&
                    settings.autoDisconnectOnTrusted &&
                    matched.autoDisconnectVpn &&
                    !isWeakWifiShielding &&
                    (snap.isVpnTunnelActive || wasPreviouslyUntrusted || physicalPolicyChanged)

                if (physicalPolicyChanged || forceEvaluate || needsUntrustedEnforcement || needsTrustedDisconnect) {
                    lastEvaluatedPolicyKey = policyKey
                    executeTransitionPolicy(
                        snap = snap,
                        matched = matched,
                        settings = settings,
                        clientInstalled = clientInstalled
                    )
                }
            }

            val latestSnap = inspector.captureSnapshot()
            val pendingNow = isHandshakePendingFlag && !latestSnap.isVpnTunnelActive
            val posture = computePosture(
                snap = latestSnap,
                matched = matched,
                isHandshakePending = pendingNow,
                isPreemptiveActive = isPreemptiveActiveFlag,
                isManualOverride = isManualOverrideActive
            )
            _state.value = OrchestratorState(
                snapshot = latestSnap,
                matchedTrustedNetwork = matched,
                posture = posture,
                activeProvider = settings.activeProvider,
                isClientInstalled = clientInstalled,
                isHandshakePending = pendingNow,
                isPreemptiveTriggerActive = isPreemptiveActiveFlag,
                isManualOverrideActive = isManualOverrideActive,
                hasWireGuardPermission = wireguardBridge.hasControlPermission(),
                lastActionBanner = _state.value.lastActionBanner
            )
            syncExternalSurfaces()
        }
    }

    private suspend fun checkPreemptiveRssiTrigger(
        snap: ActiveNetworkSnapshot,
        matched: TrustedNetworkEntity,
        settings: AutomationSettings
    ) {
        if (!settings.preemptiveRssiEnabled) {
            preemptiveDebounceJob?.cancel()
            isPreemptiveActiveFlag = false
            return
        }

        val rssi = snap.wifiRssiDbm ?: return
        if (rssi < settings.rssiThresholdDbm) {
            // Signal is weak: if tunnel is already up, keep shield flag active so auto-disconnect never drops it
            if (snap.isVpnTunnelActive) {
                isPreemptiveActiveFlag = true
                preemptiveDebounceJob?.cancel()
                return
            }

            // If not connected and not currently firing, debounce 1.5s then fire pre-emptive connect
            if (!isPreemptiveActiveFlag && !isHandshakePendingFlag) {
                if (preemptiveDebounceJob?.isActive != true) {
                    preemptiveDebounceJob = scope.launch {
                        delay(1500L)
                        val verifySnap = inspector.captureSnapshot()
                        val verifyRssi = verifySnap.wifiRssiDbm
                        if (verifyRssi != null && verifyRssi < settings.rssiThresholdDbm && !verifySnap.isVpnTunnelActive) {
                            isPreemptiveActiveFlag = true
                            isHandshakePendingFlag = true
                            sendProviderConnect(settings.activeProvider, settings.wireguardTunnelName)
                            repository.recordTransition(
                                eventType = TransitionEventType.PREEMPTIVE_CONNECT_TRIGGERED,
                                networkSummary = describeNetwork(verifySnap, matched),
                                vpnActionSummary = "Weak Wi-Fi RSSI ($verifyRssi dBm < ${settings.rssiThresholdDbm} dBm) — pre-emptively connected ${settings.activeProvider.displayName} to eliminate 5G gap"
                            )
                            publishCurrentState(
                                banner = "Weak Wi-Fi signal ($verifyRssi dBm) — pre-emptively connecting ${settings.activeProvider.displayName}"
                            )
                        }
                    }
                }
            }
        } else if (rssi >= (settings.rssiThresholdDbm + 6)) {
            // Signal has solidly recovered above threshold with hysteresis (e.g. >= -71 dBm when threshold is -77 dBm)
            preemptiveDebounceJob?.cancel()
            if (isPreemptiveActiveFlag) {
                isPreemptiveActiveFlag = false
                if (settings.autoDisconnectOnTrusted && matched.autoDisconnectVpn && snap.isVpnTunnelActive) {
                    sendProviderDisconnect(settings.activeProvider, settings.wireguardTunnelName)
                    repository.recordTransition(
                        eventType = TransitionEventType.TRUSTED_CONNECTED,
                        networkSummary = describeNetwork(snap, matched),
                        vpnActionSummary = "Wi-Fi signal recovered ($rssi dBm) — stood down Pre-Emptive Shield and disconnected ${settings.activeProvider.displayName}"
                    )
                    scheduleVerificationChecks()
                }
            }
        }
    }

    private suspend fun executeTransitionPolicy(
        snap: ActiveNetworkSnapshot,
        matched: TrustedNetworkEntity?,
        settings: AutomationSettings,
        clientInstalled: Boolean
    ) {
        val netDesc = describeNetwork(snap, matched)

        // Case 1: Returned to an approved Trusted Network
        if (matched != null) {
            if (isManualOverrideActive) {
                // User deliberately forced VPN on Trusted Network — preserve active tunnel!
                return
            }

            val isWeakWifiShielding = isPreemptiveActiveFlag ||
                (snap.transportKind == TransportKind.WIFI && snap.wifiRssiDbm != null && snap.wifiRssiDbm < (settings.rssiThresholdDbm + 6))

            if (isWeakWifiShielding) {
                // Hold the pre-emptive shield; do NOT send disconnect while signal is weak!
                return
            }

            isHandshakePendingFlag = false
            isPreemptiveActiveFlag = false
            NetworkMonitorService.cancelExposureAlert(appContext)
            cancelHandoffWorkflowSafely()

            val shouldSendDisconnect = settings.autoDisconnectOnTrusted &&
                matched.autoDisconnectVpn &&
                (snap.isVpnTunnelActive || wasPreviouslyUntrusted)
            wasPreviouslyUntrusted = false

            if (shouldSendDisconnect) {
                lastAutoBroadcastEpochMs = System.currentTimeMillis()
                sendProviderDisconnect(settings.activeProvider, settings.wireguardTunnelName)
                repository.recordTransition(
                    eventType = TransitionEventType.TRUSTED_CONNECTED,
                    networkSummary = netDesc,
                    vpnActionSummary = "Joined Trusted '${matched.label}' — sent disconnect to ${settings.activeProvider.displayName}"
                )
                scheduleVerificationChecks()
            } else {
                repository.recordTransition(
                    eventType = TransitionEventType.TRUSTED_CONNECTED,
                    networkSummary = netDesc,
                    vpnActionSummary = "Verified Trusted Network '${matched.label}' — ${settings.activeProvider.displayName} auto-connect paused"
                )
            }
            return
        }

        // Case 2: Offline (mid radio switch)
        if (snap.transportKind == TransportKind.NONE) {
            wasPreviouslyUntrusted = true
            return
        }

        // Case 3: Untrusted Network (5G / Cellular or Foreign Wi-Fi)
        wasPreviouslyUntrusted = true
        if (snap.isVpnTunnelActive) {
            isHandshakePendingFlag = false
            isPreemptiveActiveFlag = false
            NetworkMonitorService.cancelExposureAlert(appContext)
            return
        }

        lastAutoBroadcastEpochMs = System.currentTimeMillis()
        launchUntrustedHandoffWorkflow(
            provider = settings.activeProvider,
            tunnelName = settings.wireguardTunnelName,
            initialBanner = null
        )
    }

    /**
     * Hands-off transition watchdog coroutine:
     * 1. Dispatches connect broadcast to active VPN provider.
     * 2. Watches for tunnel verification for up to 4.5 seconds.
     * 3. If tunnel is not verified after 4.5s on an untrusted cellular network, posts high-priority
     *    Heads-Up Exposure Alert with 1-tap shortcut to native Android floating Internet Panel.
     */
    private fun launchUntrustedHandoffWorkflow(
        provider: VpnProvider,
        tunnelName: String,
        initialBanner: String?
    ) {
        handoffWorkflowJob?.cancel()
        handoffWorkflowJob = scope.launch {
            val clientInstalled = isClientInstalled(provider)
            val startSnap = inspector.captureSnapshot()
            val netDesc = describeNetwork(startSnap, null)

            isHandshakePendingFlag = true
            publishCurrentState(banner = initialBanner)

            sendProviderConnect(provider, tunnelName)
            repository.recordTransition(
                eventType = TransitionEventType.UNTRUSTED_DETECTED,
                networkSummary = netDesc,
                vpnActionSummary = buildString {
                    if (isPreemptiveActiveFlag) append("Pre-Emptive Wi-Fi Shield -> ")
                    append("Sent connect to ${provider.displayName}")
                    if (!clientInstalled) append(" (${provider.displayName} app not detected)")
                }
            )

            // Watch for up to 4.5 seconds (9 x 500ms)
            for (tick in 1..9) {
                delay(500L)
                val checkSnap = inspector.captureSnapshot()
                val matchedNow = TailGuardRepository.findMatchingTrustedNetwork(
                    checkSnap,
                    repository.getEnabledTrustedNetworks()
                )

                if (matchedNow != null || manualBypassActive) {
                    isHandshakePendingFlag = false
                    isPreemptiveActiveFlag = false
                    NetworkMonitorService.cancelExposureAlert(appContext)
                    publishCurrentState(banner = null)
                    return@launch
                }

                if (checkSnap.isVpnTunnelActive) {
                    isHandshakePendingFlag = false
                    isPreemptiveActiveFlag = false
                    lastVpnActive = true
                    NetworkMonitorService.cancelExposureAlert(appContext)
                    repository.recordTransition(
                        eventType = TransitionEventType.VPN_CONNECTED,
                        networkSummary = describeNetwork(checkSnap, null),
                        vpnActionSummary = "${provider.displayName} tunnel verified & stable (${checkSnap.vpnIp ?: "Active"})"
                    )
                    publishCurrentState(banner = "${provider.displayName} tunnel verified!")
                    return@launch
                }
            }

            // Handshake exceeded 4.5s on untrusted/5G network
            isHandshakePendingFlag = false
            val finalSnap = inspector.captureSnapshot()
            val finalMatched = TailGuardRepository.findMatchingTrustedNetwork(
                finalSnap,
                repository.getEnabledTrustedNetworks()
            )

            if (!finalSnap.isVpnTunnelActive && finalMatched == null && !manualBypassActive) {
                val settings = repository.getSettingsSnapshot()
                if (settings.exposureAlertEnabled) {
                    NetworkMonitorService.postExposureAlert(appContext, provider)
                    repository.recordTransition(
                        eventType = TransitionEventType.EXPOSURE_ALERT_POSTED,
                        networkSummary = describeNetwork(finalSnap, null),
                        vpnActionSummary = "Handshake timeout (4.5s) on 5G — posted exposure alert with 1-tap Internet Panel shortcut"
                    )
                    publishCurrentState(
                        banner = "VPN handshake timed out — check ${provider.displayName} session or toggle network"
                    )
                    return@launch
                }
            }

            publishCurrentState(
                banner = if (!finalSnap.isVpnTunnelActive) {
                    "${provider.displayName} is taking longer to connect — open app to refresh session"
                } else {
                    null
                }
            )
        }
    }

    private fun scheduleVerificationChecks() {
        scope.launch {
            repeat(5) {
                delay(750L)
                evaluateInternal(triggerAutomation = false, forceEvaluate = false)
            }
        }
    }

    private suspend fun publishCurrentState(banner: String?) {
        val settings = repository.getSettingsSnapshot()
        val trustedList = repository.getEnabledTrustedNetworks()
        val snap = inspector.captureSnapshot()
        val matched = TailGuardRepository.findMatchingTrustedNetwork(snap, trustedList)
        val pendingNow = isHandshakePendingFlag && !snap.isVpnTunnelActive
        val posture = computePosture(
            snap = snap,
            matched = matched,
            isHandshakePending = pendingNow,
            isPreemptiveActive = isPreemptiveActiveFlag,
            isManualOverride = isManualOverrideActive
        )
        val clientInstalled = isClientInstalled(settings.activeProvider)
        val hasWireGuardPerm = wireguardBridge.hasControlPermission()

        _state.value = OrchestratorState(
            snapshot = snap,
            matchedTrustedNetwork = matched,
            posture = posture,
            activeProvider = settings.activeProvider,
            isClientInstalled = clientInstalled,
            isHandshakePending = pendingNow,
            isPreemptiveTriggerActive = isPreemptiveActiveFlag,
            isManualOverrideActive = isManualOverrideActive,
            hasWireGuardPermission = hasWireGuardPerm,
            lastActionBanner = banner ?: _state.value.lastActionBanner
        )
        syncExternalSurfaces()
    }

    private fun syncExternalSurfaces() {
        TailGuardTileService.requestSync(appContext)
        TailGuardAppWidgetProvider.updateAllWidgets(appContext, _state.value)
        NetworkMonitorService.refreshNotificationIfRunning(appContext, _state.value)
    }

    companion object {
        fun computePosture(
            snap: ActiveNetworkSnapshot,
            matched: TrustedNetworkEntity?,
            isHandshakePending: Boolean,
            isPreemptiveActive: Boolean = false,
            isManualOverride: Boolean = false
        ): VpnPosture {
            return when {
                snap.isVpnTunnelActive -> VpnPosture.VPN_ACTIVE
                isPreemptiveActive -> VpnPosture.PREEMPTIVE_SHIELD
                isHandshakePending -> VpnPosture.HANDSHAKE_PENDING
                isManualOverride -> VpnPosture.HANDSHAKE_PENDING
                matched != null -> VpnPosture.TRUSTED_BYPASS
                else -> VpnPosture.UNPROTECTED
            }
        }

        fun describeNetwork(
            snap: ActiveNetworkSnapshot,
            matched: TrustedNetworkEntity?
        ): String {
            if (snap.transportKind == TransportKind.NONE) return "Offline / Disconnected"
            val parts = mutableListOf(snap.transportLabel)
            if (matched != null) {
                parts.add(matched.label)
            } else if (!snap.ssid.isNullOrEmpty()) {
                parts.add(snap.ssid)
            }
            if (snap.subnetCidr.isNotEmpty()) {
                parts.add(snap.subnetCidr)
            }
            if (snap.wifiRssiDbm != null) {
                parts.add("${snap.wifiRssiDbm} dBm")
            }
            return parts.joinToString(" • ")
        }
    }
}
