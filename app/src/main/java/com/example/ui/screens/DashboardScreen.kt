package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ActiveNetworkSnapshot
import com.example.data.TransportKind
import com.example.data.VpnPosture
import com.example.data.VpnProvider
import com.example.ui.DashboardUiState
import com.example.ui.components.MeshNodeIndicator
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onRapidToggle: () -> Unit,
    onProviderSelected: (VpnProvider) -> Unit,
    onWireGuardTunnelNameChanged: (String) -> Unit,
    onAddWireGuardTunnel: (String) -> Unit = {},
    onRemoveWireGuardTunnel: (String) -> Unit = {},
    onPreemptiveRssiChanged: (Boolean) -> Unit,
    onExposureAlertChanged: (Boolean) -> Unit,
    onAutoConnectChanged: (Boolean) -> Unit,
    onAutoDisconnectChanged: (Boolean) -> Unit,
    onTrustCurrentNetworkQuickClick: () -> Unit,
    onOpenClient: () -> Unit,
    onOpenInternetPanel: () -> Unit,
    onOpenSystemVpnSettings: () -> Unit,
    onTestBroadcast: () -> Unit,
    onRefreshTelemetry: () -> Unit,
    onRequestWireGuardPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            RapidToggleHeroCard(
                uiState = uiState,
                onRapidToggle = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRapidToggle()
                }
            )
        }

        item {
            LiveTelemetryCard(
                snapshot = uiState.snapshot,
                matchedNetworkLabel = uiState.matchedTrustedNetwork?.label,
                hasLocationPermission = uiState.hasFineLocationPermission,
                onTrustCurrentClick = onTrustCurrentNetworkQuickClick,
                onRefreshClick = onRefreshTelemetry
            )
        }

        item {
            VpnProviderSelectionCard(
                activeProvider = uiState.settings.activeProvider,
                wireguardTunnelName = uiState.settings.wireguardTunnelName,
                savedWireguardTunnels = uiState.settings.savedWireguardTunnels,
                isClientInstalled = uiState.isClientInstalled,
                hasWireGuardPermission = uiState.hasWireGuardPermission,
                onProviderSelected = onProviderSelected,
                onTunnelSelected = onWireGuardTunnelNameChanged,
                onAddTunnel = onAddWireGuardTunnel,
                onRemoveTunnel = onRemoveWireGuardTunnel,
                onOpenWireGuardApp = onOpenClient,
                onRequestWireGuardPermission = onRequestWireGuardPermission
            )
        }

        item {
            TransitionSafeguardsCard(
                uiState = uiState,
                onAutoConnectChanged = onAutoConnectChanged,
                onPreemptiveRssiChanged = onPreemptiveRssiChanged,
                onExposureAlertChanged = onExposureAlertChanged,
                onAutoDisconnectChanged = onAutoDisconnectChanged,
                onOpenInternetPanel = onOpenInternetPanel
            )
        }

        item {
            IntegrationDiagnosticsCard(
                provider = uiState.settings.activeProvider,
                isClientInstalled = uiState.isClientInstalled,
                onOpenClient = onOpenClient,
                onOpenSystemVpnSettings = onOpenSystemVpnSettings,
                onTestBroadcast = onTestBroadcast
            )
        }
    }
}

@Composable
private fun RapidToggleHeroCard(
    uiState: DashboardUiState,
    onRapidToggle: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val providerName = uiState.settings.activeProvider.displayName

    val (accentColor, statusTitle, statusSubtitle, statusBadgeIcon) = if (uiState.isManualOverrideActive) {
        if (uiState.isHandshakePending) {
            Quad(
                scheme.primary,
                stringResource(R.string.hero_status_manual_override_pending_title, providerName),
                stringResource(R.string.hero_status_manual_override_pending_subtitle),
                Icons.Filled.Bolt
            )
        } else {
            Quad(
                scheme.primary,
                stringResource(R.string.hero_status_manual_override_title, providerName),
                stringResource(R.string.hero_status_manual_override_subtitle),
                Icons.Filled.Shield
            )
        }
    } else when (uiState.posture) {
        VpnPosture.VPN_ACTIVE,
        VpnPosture.TAILSCALE_ACTIVE -> Quad(
            scheme.primary,
            stringResource(R.string.posture_vpn_active),
            uiState.snapshot.vpnIp?.let { "$providerName Tunnel Active • $it" }
                ?: stringResource(R.string.posture_vpn_active_desc),
            Icons.Filled.Shield
        )
        VpnPosture.PREEMPTIVE_SHIELD,
        VpnPosture.KILL_SWITCH_LOCKDOWN -> Quad(
            Color(0xFF00E5FF),
            stringResource(R.string.posture_preemptive_shield),
            stringResource(R.string.posture_preemptive_shield_desc),
            Icons.Filled.VpnLock
        )
        VpnPosture.HANDSHAKE_PENDING -> Quad(
            scheme.primary,
            stringResource(R.string.posture_connecting),
            "Dispatched connect broadcast to $providerName...",
            Icons.Filled.Bolt
        )
        VpnPosture.TRUSTED_BYPASS -> Quad(
            scheme.secondary,
            stringResource(R.string.posture_trusted_bypass),
            uiState.matchedTrustedNetwork?.let { "Matched \"${it.label}\" — battery bypass active" }
                ?: stringResource(R.string.posture_trusted_bypass_desc),
            Icons.Filled.GppGood
        )
        VpnPosture.UNPROTECTED -> Quad(
            scheme.error,
            stringResource(R.string.posture_unprotected),
            stringResource(R.string.posture_unprotected_desc),
            Icons.Filled.GppMaybe
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rapid_toggle_hero_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(R.drawable.img_mesh_hero_bg_1793731114076),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .alpha(0.18f),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = statusBadgeIcon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = statusTitle.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = providerName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MeshNodeIndicator(
                        posture = uiState.posture,
                        accessibilityLabel = "VPN Network Mesh Status Ring",
                        modifier = Modifier.size(68.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = statusSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val isTunnelUpOrPending = uiState.snapshot.isVpnTunnelActive || uiState.isHandshakePending || uiState.isManualOverrideActive
                Button(
                    onClick = onRapidToggle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("rapid_toggle_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = if (isTunnelUpOrPending) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    border = if (isTunnelUpOrPending) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else null
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isTunnelUpOrPending) {
                            stringResource(R.string.btn_deactivate_vpn, providerName)
                        } else {
                            stringResource(R.string.btn_activate_vpn, providerName)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTelemetryCard(
    snapshot: ActiveNetworkSnapshot,
    matchedNetworkLabel: String?,
    hasLocationPermission: Boolean,
    onTrustCurrentClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_telemetry_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.section_live_telemetry),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onRefreshClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("refresh_telemetry_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh telemetry",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            TelemetryRow(
                label = stringResource(R.string.telemetry_transport),
                value = snapshot.transportLabel,
                icon = when (snapshot.transportKind) {
                    TransportKind.WIFI -> Icons.Filled.Wifi
                    TransportKind.CELLULAR_5G,
                    TransportKind.CELLULAR_LTE -> Icons.Filled.SignalCellularAlt
                    else -> Icons.Filled.GppMaybe
                },
                accentColor = when (snapshot.transportKind) {
                    TransportKind.WIFI -> MaterialTheme.colorScheme.secondary
                    TransportKind.CELLULAR_5G -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            // Wi-Fi Signal RSSI row
            if (snapshot.transportKind == TransportKind.WIFI && snapshot.wifiRssiDbm != null) {
                val rssi = snapshot.wifiRssiDbm
                val rssiColor = when {
                    rssi >= -65 -> Color(0xFF00E676)
                    rssi >= -77 -> Color(0xFFFFB74D)
                    else -> Color(0xFFFF5252)
                }
                val rssiQuality = when {
                    rssi >= -65 -> "Strong"
                    rssi >= -77 -> "Fair"
                    else -> "Weak (Shield Arms)"
                }
                TelemetryRow(
                    label = stringResource(R.string.telemetry_wifi_rssi),
                    value = "$rssi dBm ($rssiQuality)",
                    icon = Icons.Filled.Wifi,
                    accentColor = rssiColor
                )
            }

            TelemetryRow(
                label = stringResource(R.string.telemetry_subnet),
                value = snapshot.subnetCidr.ifEmpty { stringResource(R.string.telemetry_none) },
                icon = Icons.Filled.NetworkCheck
            )

            val formattedGatewayDns = if (snapshot.gatewayAndDnsSignature.contains(" | ")) {
                snapshot.gatewayAndDnsSignature.replace(" | ", "\n")
            } else {
                snapshot.gatewayAndDnsSignature.ifEmpty { stringResource(R.string.telemetry_none) }
            }
            TelemetryRow(
                label = stringResource(R.string.telemetry_gateway_dns),
                value = formattedGatewayDns,
                icon = Icons.Filled.CheckCircle
            )

            TelemetryRow(
                label = stringResource(R.string.telemetry_vpn_ip),
                value = snapshot.vpnIp ?: "Inactive",
                icon = Icons.Filled.Shield,
                accentColor = if (snapshot.isVpnTunnelActive) MaterialTheme.colorScheme.primary else null
            )

            if (snapshot.transportKind == TransportKind.WIFI) {
                val ssidDisplay = if (hasLocationPermission) {
                    snapshot.ssid ?: "Hidden / Redacted"
                } else {
                    stringResource(R.string.telemetry_redacted_zero_perm)
                }
                TelemetryRow(
                    label = stringResource(R.string.telemetry_ssid),
                    value = ssidDisplay,
                    icon = Icons.Filled.Wifi
                )
            }

            if (matchedNetworkLabel != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GppGood,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Matched Trusted Network: $matchedNetworkLabel",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } else if (snapshot.transportKind == TransportKind.WIFI && snapshot.subnetCidr.isNotEmpty()) {
                FilledTonalButton(
                    onClick = onTrustCurrentClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trust_current_network_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GppGood,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_trust_current_network))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VpnProviderSelectionCard(
    activeProvider: VpnProvider,
    wireguardTunnelName: String,
    savedWireguardTunnels: List<String>,
    isClientInstalled: Boolean,
    hasWireGuardPermission: Boolean,
    onProviderSelected: (VpnProvider) -> Unit,
    onTunnelSelected: (String) -> Unit,
    onAddTunnel: (String) -> Unit,
    onRemoveTunnel: (String) -> Unit,
    onOpenWireGuardApp: () -> Unit,
    onRequestWireGuardPermission: () -> Unit = {}
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newTunnelInput by remember { mutableStateOf("") }
    var tunnelPendingDeletion by remember { mutableStateOf<String?>(null) }

    if (tunnelPendingDeletion != null) {
        val targetName = tunnelPendingDeletion!!
        AlertDialog(
            onDismissRequest = { tunnelPendingDeletion = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.dialog_delete_tunnel_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_delete_tunnel_msg, targetName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveTunnel(targetName)
                        tunnelPendingDeletion = null
                    },
                    modifier = Modifier.testTag("confirm_delete_tunnel_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.btn_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { tunnelPendingDeletion = null }
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                newTunnelInput = ""
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.VpnLock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.dialog_add_tunnel_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.dialog_add_tunnel_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newTunnelInput,
                        onValueChange = { newTunnelInput = it },
                        label = { Text("Tunnel Profile Name") },
                        placeholder = { Text("e.g. Home Router, ProtonVPN") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_new_tunnel_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        text = "Quick Presets:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Home Router", "ProtonVPN", "Proton-US", "wg0").forEach { suggestion ->
                            SuggestionChip(
                                onClick = { newTunnelInput = suggestion },
                                label = { Text(suggestion) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newTunnelInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddTunnel(trimmed)
                            onTunnelSelected(trimmed)
                            showAddDialog = false
                            newTunnelInput = ""
                        }
                    },
                    modifier = Modifier.testTag("dialog_save_tunnel_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Add & Select")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        newTunnelInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vpn_provider_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.section_vpn_provider),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "TailGuard commands external VPN clients hands-off with zero background conflict.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = activeProvider == VpnProvider.TAILSCALE,
                    onClick = { onProviderSelected(VpnProvider.TAILSCALE) },
                    label = { Text("Tailscale") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("provider_chip_tailscale"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                FilterChip(
                    selected = activeProvider == VpnProvider.WIREGUARD,
                    onClick = { onProviderSelected(VpnProvider.WIREGUARD) },
                    label = { Text("WireGuard") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.VpnLock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("provider_chip_wireguard"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            AnimatedVisibility(visible = activeProvider == VpnProvider.WIREGUARD) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // WireGuard Remote Control Permission Banner & Grant Action
                    if (!hasWireGuardPermission) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wireguard_permission_warning_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.GppMaybe,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.card_wireguard_perm_needed_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.card_wireguard_perm_needed_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onRequestWireGuardPermission,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_grant_wireguard_permission"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.btn_grant_wireguard_perm),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wireguard_permission_granted_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.card_wireguard_perm_granted),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Security Architecture explanation notice
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = 2.dp)
                            )
                            Text(
                                text = stringResource(R.string.wireguard_security_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Active Tunnel Profile Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Active Target Tunnel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = wireguardTunnelName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Configured Profiles Chip Group
                    Text(
                        text = stringResource(R.string.wireguard_tunnels_header),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        savedWireguardTunnels.forEach { tunnel ->
                            val isSelected = tunnel.equals(wireguardTunnelName, ignoreCase = true)
                            InputChip(
                                selected = isSelected,
                                onClick = { onTunnelSelected(tunnel) },
                                label = {
                                    Text(
                                        text = tunnel,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                trailingIcon = if (savedWireguardTunnels.size > 1) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove $tunnel",
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { tunnelPendingDeletion = tunnel }
                                        )
                                    }
                                } else null,
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("tunnel_chip_${tunnel.replace(" ", "_")}")
                            )
                        }
                    }

                    // Action Buttons: Add Profile & Open WireGuard
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_add_tunnel_profile"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_add_wireguard_tunnel))
                        }

                        OutlinedButton(
                            onClick = onOpenWireGuardApp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_open_wireguard_link"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_open_wireguard_app))
                        }
                    }

                    // Remote Control Setting Reminder
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.wireguard_remote_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransitionSafeguardsCard(
    uiState: DashboardUiState,
    onAutoConnectChanged: (Boolean) -> Unit,
    onPreemptiveRssiChanged: (Boolean) -> Unit,
    onExposureAlertChanged: (Boolean) -> Unit,
    onAutoDisconnectChanged: (Boolean) -> Unit,
    onOpenInternetPanel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transition_safeguards_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.section_automation_controls),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            SettingToggleRow(
                title = stringResource(R.string.toggle_auto_guard_title),
                subtitle = stringResource(R.string.toggle_auto_guard_subtitle),
                checked = uiState.settings.autoConnectEnabled,
                onCheckedChange = onAutoConnectChanged,
                testTag = "auto_connect_switch"
            )

            SettingToggleRow(
                title = stringResource(R.string.toggle_preemptive_rssi_title),
                subtitle = stringResource(R.string.toggle_preemptive_rssi_subtitle),
                checked = uiState.settings.preemptiveRssiEnabled,
                onCheckedChange = onPreemptiveRssiChanged,
                testTag = "preemptive_rssi_switch"
            )

            SettingToggleRow(
                title = stringResource(R.string.toggle_exposure_alert_title),
                subtitle = stringResource(R.string.toggle_exposure_alert_subtitle),
                checked = uiState.settings.exposureAlertEnabled,
                onCheckedChange = onExposureAlertChanged,
                testTag = "exposure_alert_switch"
            )

            SettingToggleRow(
                title = stringResource(R.string.toggle_auto_disconnect_title),
                subtitle = stringResource(R.string.toggle_auto_disconnect_subtitle),
                checked = uiState.settings.autoDisconnectOnTrusted,
                onCheckedChange = onAutoDisconnectChanged,
                testTag = "auto_disconnect_switch"
            )

            OutlinedButton(
                onClick = onOpenInternetPanel,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_internet_panel_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_open_internet_panel))
            }
        }
    }
}

@Composable
private fun IntegrationDiagnosticsCard(
    provider: VpnProvider,
    isClientInstalled: Boolean,
    onOpenClient: () -> Unit,
    onOpenSystemVpnSettings: () -> Unit,
    onTestBroadcast: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("integration_diagnostics_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.card_vpn_integration_title, provider.displayName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isClientInstalled) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isClientInstalled) Icons.Filled.CheckCircle else Icons.Filled.GppMaybe,
                        contentDescription = null,
                        tint = if (isClientInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isClientInstalled) {
                            stringResource(R.string.card_client_installed, provider.displayName, provider.packageName)
                        } else {
                            stringResource(R.string.card_client_missing, provider.displayName)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onTestBroadcast,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_broadcast_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_test_broadcast))
                }

                FilledTonalButton(
                    onClick = onOpenClient,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_client_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isClientInstalled) {
                            stringResource(R.string.btn_open_client_app, provider.displayName)
                        } else {
                            stringResource(R.string.btn_install_client_app, provider.displayName)
                        }
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenSystemVpnSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("system_vpn_settings_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_system_vpn_settings))
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun TelemetryRow(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Medium,
            color = accentColor ?: MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.5f, fill = false)
        )
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
