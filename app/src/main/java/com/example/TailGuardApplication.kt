package com.example

import android.app.Application
import com.example.data.AutomationSettingsDataStore
import com.example.data.TailGuardDatabase
import com.example.data.TailGuardRepository
import com.example.service.NetworkFingerprintInspector
import com.example.service.NetworkMonitorService
import com.example.service.TailscaleBridge
import com.example.service.VpnAutomationController
import com.example.service.WireGuardBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TailGuardContainer(context: Application) {
    val database: TailGuardDatabase = TailGuardDatabase.getInstance(context)
    val settingsStore: AutomationSettingsDataStore = AutomationSettingsDataStore(context)
    val repository: TailGuardRepository = TailGuardRepository(
        trustedNetworkDao = database.trustedNetworkDao(),
        transitionLogDao = database.transitionLogDao(),
        settingsStore = settingsStore
    )
    val inspector: NetworkFingerprintInspector = NetworkFingerprintInspector(context)
    val tailscaleBridge: TailscaleBridge = TailscaleBridge(context)
    val wireguardBridge: WireGuardBridge = WireGuardBridge(context)
    val controller: VpnAutomationController = VpnAutomationController(
        appContext = context,
        repository = repository,
        inspector = inspector,
        tailscaleBridge = tailscaleBridge,
        wireguardBridge = wireguardBridge
    )
}

class TailGuardApplication : Application() {
    lateinit var container: TailGuardContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = TailGuardContainer(this)
        container.controller.refreshLiveState(triggerAutomation = false)

        appScope.launch {
            val settings = container.repository.getSettingsSnapshot()
            if (settings.autoConnectEnabled) {
                NetworkMonitorService.startPassiveMonitor(this@TailGuardApplication)
            }
        }
    }
}
