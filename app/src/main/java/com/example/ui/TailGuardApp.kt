package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.WifiLock
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.WifiLock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.MeshNodeIndicator
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.TransitionLogsScreen
import com.example.ui.screens.TrustedNetworksScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TailGuardApp(
    viewModel: MainViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var openTrustedEditorDirectly by rememberSaveable { mutableStateOf(false) }

    // BackHandler on secondary tabs returns cleanly to Dashboard
    BackHandler(enabled = uiState.selectedTab != TailGuardTab.DASHBOARD) {
        viewModel.selectTab(TailGuardTab.DASHBOARD)
    }

    // Show feedback banners in Snackbar
    LaunchedEffect(uiState.bannerMessage) {
        val msg = uiState.bannerMessage
        if (!msg.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    // Optional runtime Fine Location permission launcher (ONLY invoked when user clicks SSID button)
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshTelemetry()
    }

    // Optional Android 13+ notification permission launcher when enabling passive monitor
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.setAutoConnectEnabled(true)
    }

    val handleAutoConnectToggle: (Boolean) -> Unit = { wantEnabled ->
        if (wantEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setAutoConnectEnabled(wantEnabled)
        }
    }

    val handleRequestSsidLocation: () -> Unit = {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    // WireGuard Control Tunnels runtime permission launcher
    val wireguardPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshTelemetry()
    }

    val handleRequestWireGuardPermission: () -> Unit = {
        wireguardPermissionLauncher.launch("com.wireguard.android.permission.CONTROL_TUNNELS")
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MeshNodeIndicator(
                                posture = uiState.posture,
                                accessibilityLabel = "TailGuard Header Mesh Icon",
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleDarkTheme() },
                            modifier = Modifier.testTag("toggle_theme_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.settings.darkThemeEnabled) {
                                    Icons.Filled.LightMode
                                } else {
                                    Icons.Filled.DarkMode
                                },
                                contentDescription = "Toggle color theme"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.testTag("bottom_navigation_bar")
                    ) {
                        NavigationBarItem(
                            selected = uiState.selectedTab == TailGuardTab.DASHBOARD,
                            onClick = { viewModel.selectTab(TailGuardTab.DASHBOARD) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.selectedTab == TailGuardTab.DASHBOARD) {
                                        Icons.Filled.Dashboard
                                    } else {
                                        Icons.Outlined.Dashboard
                                    },
                                    contentDescription = stringResource(R.string.nav_dashboard)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_dashboard)) },
                            modifier = Modifier.testTag("nav_tab_dashboard")
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == TailGuardTab.TRUSTED_NETWORKS,
                            onClick = { viewModel.selectTab(TailGuardTab.TRUSTED_NETWORKS) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.selectedTab == TailGuardTab.TRUSTED_NETWORKS) {
                                        Icons.Filled.WifiLock
                                    } else {
                                        Icons.Outlined.WifiLock
                                    },
                                    contentDescription = stringResource(R.string.nav_trusted_networks)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_trusted_networks)) },
                            modifier = Modifier.testTag("nav_tab_trusted")
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == TailGuardTab.TRANSITION_LOGS,
                            onClick = { viewModel.selectTab(TailGuardTab.TRANSITION_LOGS) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.selectedTab == TailGuardTab.TRANSITION_LOGS) {
                                        Icons.Filled.History
                                    } else {
                                        Icons.Outlined.History
                                    },
                                    contentDescription = stringResource(R.string.nav_transition_logs)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_transition_logs)) },
                            modifier = Modifier.testTag("nav_tab_logs")
                        )
                    }
                }
            }
        ) { innerPadding ->
            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("side_navigation_rail")
                    ) {
                        NavigationRailItem(
                            selected = uiState.selectedTab == TailGuardTab.DASHBOARD,
                            onClick = { viewModel.selectTab(TailGuardTab.DASHBOARD) },
                            icon = { Icon(Icons.Filled.Dashboard, contentDescription = stringResource(R.string.nav_dashboard)) },
                            label = { Text(stringResource(R.string.nav_dashboard)) },
                            modifier = Modifier.testTag("rail_tab_dashboard")
                        )
                        NavigationRailItem(
                            selected = uiState.selectedTab == TailGuardTab.TRUSTED_NETWORKS,
                            onClick = { viewModel.selectTab(TailGuardTab.TRUSTED_NETWORKS) },
                            icon = { Icon(Icons.Filled.WifiLock, contentDescription = stringResource(R.string.nav_trusted_networks)) },
                            label = { Text(stringResource(R.string.nav_trusted_networks)) },
                            modifier = Modifier.testTag("rail_tab_trusted")
                        )
                        NavigationRailItem(
                            selected = uiState.selectedTab == TailGuardTab.TRANSITION_LOGS,
                            onClick = { viewModel.selectTab(TailGuardTab.TRANSITION_LOGS) },
                            icon = { Icon(Icons.Filled.History, contentDescription = stringResource(R.string.nav_transition_logs)) },
                            label = { Text(stringResource(R.string.nav_transition_logs)) },
                            modifier = Modifier.testTag("rail_tab_logs")
                        )
                    }

                    // Canonical two-pane supporting layout on wide/tablet displays
                    Box(modifier = Modifier.weight(1.15f)) {
                        if (uiState.selectedTab == TailGuardTab.TRUSTED_NETWORKS) {
                            TrustedNetworksScreen(
                                uiState = uiState,
                                openEditorInitially = openTrustedEditorDirectly,
                                onEditorConsumed = { openTrustedEditorDirectly = false },
                                onSaveNetwork = viewModel::saveTrustedNetwork,
                                onToggleEnabled = viewModel::toggleTrustedNetworkEnabled,
                                onDeleteNetwork = viewModel::deleteTrustedNetwork,
                                onRequestLocationPermissionForSsid = handleRequestSsidLocation
                            )
                        } else {
                            DashboardScreen(
                                uiState = uiState,
                                onRapidToggle = viewModel::onRapidToggleClicked,
                                onProviderSelected = viewModel::setActiveProvider,
                                onWireGuardTunnelNameChanged = viewModel::setWireGuardTunnelName,
                                onAddWireGuardTunnel = viewModel::addWireGuardTunnel,
                                onRemoveWireGuardTunnel = viewModel::removeWireGuardTunnel,
                                onPreemptiveRssiChanged = viewModel::setPreemptiveRssiEnabled,
                                onExposureAlertChanged = viewModel::setExposureAlertEnabled,
                                onAutoConnectChanged = handleAutoConnectToggle,
                                onAutoDisconnectChanged = viewModel::setAutoDisconnectOnTrusted,
                                onTrustCurrentNetworkQuickClick = {
                                    openTrustedEditorDirectly = true
                                    viewModel.selectTab(TailGuardTab.TRUSTED_NETWORKS)
                                },
                                onOpenClient = { viewModel.openActiveClientOrPlayStore(context) },
                                onOpenInternetPanel = { viewModel.openInternetPanel(context) },
                                onOpenSystemVpnSettings = { viewModel.openSystemVpnSettings(context) },
                                onTestBroadcast = viewModel::onTestBroadcastClicked,
                                onRefreshTelemetry = viewModel::refreshTelemetry,
                                onRequestWireGuardPermission = handleRequestWireGuardPermission
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(0.95f)) {
                        TransitionLogsScreen(
                            uiState = uiState,
                            onSelectFilter = viewModel::selectLogFilter,
                            onExportLogs = { viewModel.shareLogs(context) },
                            onClearLogs = viewModel::clearLogs
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentModifier = Modifier.widthIn(max = 600.dp)
                    when (uiState.selectedTab) {
                        TailGuardTab.DASHBOARD -> {
                            DashboardScreen(
                                uiState = uiState,
                                onRapidToggle = viewModel::onRapidToggleClicked,
                                onProviderSelected = viewModel::setActiveProvider,
                                onWireGuardTunnelNameChanged = viewModel::setWireGuardTunnelName,
                                onAddWireGuardTunnel = viewModel::addWireGuardTunnel,
                                onRemoveWireGuardTunnel = viewModel::removeWireGuardTunnel,
                                onPreemptiveRssiChanged = viewModel::setPreemptiveRssiEnabled,
                                onExposureAlertChanged = viewModel::setExposureAlertEnabled,
                                onAutoConnectChanged = handleAutoConnectToggle,
                                onAutoDisconnectChanged = viewModel::setAutoDisconnectOnTrusted,
                                onTrustCurrentNetworkQuickClick = {
                                    openTrustedEditorDirectly = true
                                    viewModel.selectTab(TailGuardTab.TRUSTED_NETWORKS)
                                },
                                onOpenClient = { viewModel.openActiveClientOrPlayStore(context) },
                                onOpenInternetPanel = { viewModel.openInternetPanel(context) },
                                onOpenSystemVpnSettings = { viewModel.openSystemVpnSettings(context) },
                                onTestBroadcast = viewModel::onTestBroadcastClicked,
                                onRefreshTelemetry = viewModel::refreshTelemetry,
                                onRequestWireGuardPermission = handleRequestWireGuardPermission,
                                modifier = contentModifier
                            )
                        }
                        TailGuardTab.TRUSTED_NETWORKS -> {
                            TrustedNetworksScreen(
                                uiState = uiState,
                                openEditorInitially = openTrustedEditorDirectly,
                                onEditorConsumed = { openTrustedEditorDirectly = false },
                                onSaveNetwork = viewModel::saveTrustedNetwork,
                                onToggleEnabled = viewModel::toggleTrustedNetworkEnabled,
                                onDeleteNetwork = viewModel::deleteTrustedNetwork,
                                onRequestLocationPermissionForSsid = handleRequestSsidLocation,
                                modifier = contentModifier
                            )
                        }
                        TailGuardTab.TRANSITION_LOGS -> {
                            TransitionLogsScreen(
                                uiState = uiState,
                                onSelectFilter = viewModel::selectLogFilter,
                                onExportLogs = { viewModel.shareLogs(context) },
                                onClearLogs = viewModel::clearLogs,
                                modifier = contentModifier
                            )
                        }
                    }
                }
            }
        }
    }
}
