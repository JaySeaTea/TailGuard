package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ActiveNetworkSnapshot
import com.example.data.MatchMode
import com.example.data.TrustedNetworkEntity
import com.example.ui.DashboardUiState
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun TrustedNetworksScreen(
    uiState: DashboardUiState,
    openEditorInitially: Boolean = false,
    onEditorConsumed: () -> Unit = {},
    onSaveNetwork: (
        existingId: Long?,
        label: String,
        matchMode: MatchMode,
        subnetCidr: String,
        gatewaySig: String,
        ssid: String?,
        autoDisconnectVpn: Boolean
    ) -> Unit,
    onToggleEnabled: (TrustedNetworkEntity, Boolean) -> Unit,
    onDeleteNetwork: (TrustedNetworkEntity) -> Unit,
    onRequestLocationPermissionForSsid: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingEntity by remember { mutableStateOf<TrustedNetworkEntity?>(null) }
    var showEditorDialog by rememberSaveable { mutableStateOf(openEditorInitially) }

    if (openEditorInitially && !showEditorDialog) {
        showEditorDialog = true
        onEditorConsumed()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("trusted_networks_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TrustedNetworksHeaderCard(
                snapshot = uiState.snapshot,
                onTrustCurrentClick = {
                    editingEntity = null
                    showEditorDialog = true
                },
                onAddManualRuleClick = {
                    editingEntity = TrustedNetworkEntity(
                        id = 0L,
                        label = "",
                        matchMode = MatchMode.FINGERPRINT,
                        subnetCidr = uiState.snapshot.subnetCidr.ifEmpty { "192.168.1.0/24" },
                        gatewayAndDnsSignature = uiState.snapshot.gatewayAndDnsSignature,
                        ssid = uiState.snapshot.ssid
                    )
                    showEditorDialog = true
                }
            )
        }

        if (uiState.trustedNetworks.isEmpty()) {
            item {
                EmptyTrustedNetworksState()
            }
        } else {
            items(
                items = uiState.trustedNetworks,
                key = { it.id }
            ) { network ->
                val isCurrentlyMatched = uiState.matchedTrustedNetwork?.id == network.id
                TrustedNetworkItemCard(
                    network = network,
                    isCurrentlyMatched = isCurrentlyMatched,
                    onToggleEnabled = { enabled -> onToggleEnabled(network, enabled) },
                    onEditClick = {
                        editingEntity = network
                        showEditorDialog = true
                    },
                    onDeleteClick = { onDeleteNetwork(network) }
                )
            }
        }
    }

    if (showEditorDialog) {
        TrustedNetworkEditorDialog(
            initial = editingEntity,
            liveSnapshot = uiState.snapshot,
            hasLocationPermission = uiState.hasFineLocationPermission,
            onRequestLocationPermission = onRequestLocationPermissionForSsid,
            onDismiss = {
                showEditorDialog = false
                editingEntity = null
                onEditorConsumed()
            },
            onConfirmSave = { existingId, label, mode, cidr, sig, ssid, autoDisconnect ->
                onSaveNetwork(existingId, label, mode, cidr, sig, ssid, autoDisconnect)
                showEditorDialog = false
                editingEntity = null
                onEditorConsumed()
            }
        )
    }
}

@Composable
private fun TrustedNetworksHeaderCard(
    snapshot: ActiveNetworkSnapshot,
    onTrustCurrentClick: () -> Unit,
    onAddManualRuleClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.trusted_header_title),
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface
            )
            Text(
                text = stringResource(R.string.trusted_header_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )

            if (snapshot.subnetCidr.isNotEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = scheme.background.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Detected Link",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                        Text(
                            text = buildString {
                                append(snapshot.transportLabel)
                                append(" • ")
                                append(snapshot.subnetCidr)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFamily,
                            color = scheme.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTrustCurrentClick,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("trust_current_network_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.btn_trust_current_network))
                }

                OutlinedButton(
                    onClick = onAddManualRuleClick,
                    modifier = Modifier
                        .weight(0.9f)
                        .height(48.dp)
                        .testTag("add_custom_network_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.btn_add_custom_network))
                }
            }
        }
    }
}

@Composable
private fun EmptyTrustedNetworksState() {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_trusted_networks_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = scheme.secondary,
                modifier = Modifier.size(42.dp)
            )
            Text(
                text = stringResource(R.string.empty_trusted_title),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.empty_trusted_body),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TrustedNetworkItemCard(
    network: TrustedNetworkEntity,
    isCurrentlyMatched: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val borderColor = if (isCurrentlyMatched) {
        scheme.secondary
    } else {
        scheme.outline.copy(alpha = 0.45f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trusted_network_card_${network.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        border = BorderStroke(if (isCurrentlyMatched) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (network.matchMode == MatchMode.SSID) {
                            Icons.Filled.Wifi
                        } else {
                            Icons.Filled.Lan
                        },
                        contentDescription = null,
                        tint = if (isCurrentlyMatched) scheme.secondary else scheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = network.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurface
                            )
                            if (isCurrentlyMatched) {
                                Surface(
                                    shape = CircleShape,
                                    color = scheme.secondaryContainer
                                ) {
                                    Text(
                                        text = stringResource(R.string.badge_currently_matched),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = when (network.matchMode) {
                                MatchMode.FINGERPRINT -> stringResource(R.string.match_mode_fingerprint)
                                MatchMode.SSID -> stringResource(R.string.match_mode_ssid)
                                MatchMode.BOTH -> stringResource(R.string.match_mode_both)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = network.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("switch_trusted_net_${network.id}")
                )
            }

            if (network.subnetCidr.isNotEmpty()) {
                Text(
                    text = buildString {
                        append("CIDR: ${network.subnetCidr}")
                        if (network.gatewayAndDnsSignature.isNotEmpty()) {
                            append(" • ${network.gatewayAndDnsSignature}")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFamily,
                    color = scheme.onSurface
                )
            }

            if (!network.ssid.isNullOrEmpty()) {
                Text(
                    text = "SSID: \"${network.ssid}\"",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFamily,
                    color = scheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (network.autoDisconnectVpn) {
                        "• Auto-disconnects Tailscale on arrival"
                    } else {
                        "• Leaves current VPN state unchanged"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )

                Row {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.testTag("edit_trusted_net_${network.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit ${network.label}",
                            tint = scheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.testTag("delete_trusted_net_${network.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.btn_delete_network),
                            tint = scheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrustedNetworkEditorDialog(
    initial: TrustedNetworkEntity?,
    liveSnapshot: ActiveNetworkSnapshot,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmSave: (
        existingId: Long?,
        label: String,
        matchMode: MatchMode,
        subnetCidr: String,
        gatewaySig: String,
        ssid: String?,
        autoDisconnectVpn: Boolean
    ) -> Unit
) {
    var label by rememberSaveable {
        mutableStateOf(
            initial?.label?.takeIf { it.isNotEmpty() }
                ?: liveSnapshot.ssid
                ?: "Home Trusted Wi-Fi"
        )
    }
    var matchMode by rememberSaveable {
        mutableStateOf(initial?.matchMode ?: MatchMode.FINGERPRINT)
    }
    var subnetCidr by rememberSaveable {
        mutableStateOf(
            initial?.subnetCidr?.takeIf { it.isNotEmpty() }
                ?: liveSnapshot.subnetCidr.ifEmpty { "192.168.1.0/24" }
        )
    }
    var gatewaySig by rememberSaveable {
        mutableStateOf(
            initial?.gatewayAndDnsSignature ?: liveSnapshot.gatewayAndDnsSignature
        )
    }
    var ssid by rememberSaveable {
        mutableStateOf(initial?.ssid ?: liveSnapshot.ssid.orEmpty())
    }
    var autoDisconnect by rememberSaveable {
        mutableStateOf(initial?.autoDisconnectVpn ?: true)
    }

    // Auto-fill live SSID if permission was just granted
    if (ssid.isEmpty() && !liveSnapshot.ssid.isNullOrEmpty()) {
        ssid = liveSnapshot.ssid
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_add_network_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.field_network_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_network_label")
                )

                Text(
                    text = "Identification Mode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = matchMode == MatchMode.FINGERPRINT,
                        onClick = { matchMode = MatchMode.FINGERPRINT },
                        label = { Text("Subnet (0 Perm)", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.testTag("chip_mode_fingerprint")
                    )
                    FilterChip(
                        selected = matchMode == MatchMode.SSID,
                        onClick = { matchMode = MatchMode.SSID },
                        label = { Text("SSID", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.testTag("chip_mode_ssid")
                    )
                    FilterChip(
                        selected = matchMode == MatchMode.BOTH,
                        onClick = { matchMode = MatchMode.BOTH },
                        label = { Text("Both", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.testTag("chip_mode_both")
                    )
                }

                if (matchMode != MatchMode.SSID) {
                    OutlinedTextField(
                        value = subnetCidr,
                        onValueChange = { subnetCidr = it },
                        label = { Text(stringResource(R.string.field_subnet_cidr)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_subnet_cidr")
                    )

                    OutlinedTextField(
                        value = gatewaySig,
                        onValueChange = { gatewaySig = it },
                        label = { Text(stringResource(R.string.field_gateway_signature)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_gateway_signature")
                    )
                }

                if (matchMode == MatchMode.SSID || matchMode == MatchMode.BOTH) {
                    OutlinedTextField(
                        value = ssid,
                        onValueChange = { ssid = it },
                        label = { Text(stringResource(R.string.field_wifi_ssid)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_wifi_ssid")
                    )
                    if (!hasLocationPermission) {
                        Text(
                            text = "Android hides Wi-Fi SSIDs until Location permission is granted. Grant Location below or use Subnet (0 Perm) mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        OutlinedButton(
                            onClick = onRequestLocationPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("grant_location_for_ssid_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GpsFixed,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_grant_location_for_ssid),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.label_auto_disconnect_chip),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoDisconnect,
                        onCheckedChange = { autoDisconnect = it },
                        modifier = Modifier.testTag("switch_dialog_auto_disconnect")
                    )
                }
            }
        },
        confirmButton = {
            val isValid = when (matchMode) {
                MatchMode.FINGERPRINT -> subnetCidr.isNotBlank()
                MatchMode.SSID -> ssid.isNotBlank() && hasLocationPermission
                MatchMode.BOTH -> subnetCidr.isNotBlank() && ssid.isNotBlank() && hasLocationPermission
            }
            Button(
                onClick = {
                    onConfirmSave(
                        initial?.id?.takeIf { it != 0L },
                        label,
                        matchMode,
                        subnetCidr,
                        gatewaySig,
                        ssid.ifBlank { null },
                        autoDisconnect
                    )
                },
                enabled = isValid,
                modifier = Modifier.testTag("confirm_save_network_button")
            ) {
                Text(stringResource(R.string.btn_save_network))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_save_network_button")
            ) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
