package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LogFilter
import com.example.data.TransitionEventType
import com.example.data.TransitionLogEntity
import com.example.ui.DashboardUiState
import com.example.ui.theme.JetBrainsMonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransitionLogsScreen(
    uiState: DashboardUiState,
    onSelectFilter: (LogFilter) -> Unit,
    onExportLogs: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("transition_logs_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LogsHeaderAndFilterCard(
                totalCount = uiState.allLogs.size,
                selectedFilter = uiState.selectedLogFilter,
                onSelectFilter = onSelectFilter,
                onExportLogs = onExportLogs,
                onClearLogs = onClearLogs
            )
        }

        if (uiState.filteredLogs.isEmpty()) {
            item {
                EmptyLogsCard()
            }
        } else {
            items(
                items = uiState.filteredLogs,
                key = { it.id }
            ) { entry ->
                TransitionLogRowCard(entry = entry)
            }
        }
    }
}

@Composable
private fun LogsHeaderAndFilterCard(
    totalCount: Int,
    selectedFilter: LogFilter,
    onSelectFilter: (LogFilter) -> Unit,
    onExportLogs: () -> Unit,
    onClearLogs: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.logs_header_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.logs_header_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = scheme.primary.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "$totalCount Events",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Scrollable filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == LogFilter.ALL,
                    onClick = { onSelectFilter(LogFilter.ALL) },
                    label = { Text(stringResource(R.string.filter_all)) },
                    modifier = Modifier.testTag("filter_chip_all")
                )
                FilterChip(
                    selected = selectedFilter == LogFilter.VPN_INTENTS,
                    onClick = { onSelectFilter(LogFilter.VPN_INTENTS) },
                    label = { Text(stringResource(R.string.filter_vpn)) },
                    modifier = Modifier.testTag("filter_chip_vpn")
                )
                FilterChip(
                    selected = selectedFilter == LogFilter.NETWORK_SHIFTS,
                    onClick = { onSelectFilter(LogFilter.NETWORK_SHIFTS) },
                    label = { Text(stringResource(R.string.filter_network)) },
                    modifier = Modifier.testTag("filter_chip_network")
                )
                FilterChip(
                    selected = selectedFilter == LogFilter.ALERTS,
                    onClick = { onSelectFilter(LogFilter.ALERTS) },
                    label = { Text(stringResource(R.string.filter_alerts)) },
                    modifier = Modifier.testTag("filter_chip_alerts")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onExportLogs,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("export_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.btn_export_logs))
                }

                OutlinedButton(
                    onClick = onClearLogs,
                    modifier = Modifier
                        .weight(0.8f)
                        .height(48.dp)
                        .testTag("clear_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.btn_clear_logs))
                }
            }
        }
    }
}

@Composable
private fun EmptyLogsCard() {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_logs_card"),
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
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = stringResource(R.string.empty_logs_title),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.empty_logs_body),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TransitionLogRowCard(entry: TransitionLogEntity) {
    val scheme = MaterialTheme.colorScheme
    val formatter = remember { SimpleDateFormat("MMM dd • HH:mm:ss.SSS", Locale.US) }
    val formattedTs = remember(entry.timestamp) { formatter.format(Date(entry.timestamp)) }

    val (icon, badgeColor, badgeLabel) = eventStyle(entry.eventType, scheme.primary, scheme.secondary, scheme.tertiary, scheme.error)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_entry_${entry.id}"),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = badgeColor.copy(alpha = 0.16f),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formattedTs,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = scheme.onSurfaceVariant
                    )
                }

                Text(
                    text = entry.vpnActionSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface
                )

                Text(
                    text = entry.networkSummary,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFamily,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun eventStyle(
    type: TransitionEventType,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    error: Color
): Triple<ImageVector, Color, String> {
    return when (type) {
        TransitionEventType.TRUSTED_CONNECTED ->
            Triple(Icons.Filled.GppGood, secondary, "TRUSTED LAN")
        TransitionEventType.UNTRUSTED_DETECTED ->
            Triple(Icons.Filled.SwapHoriz, error, "UNTRUSTED SHIFT")
        TransitionEventType.PREEMPTIVE_CONNECT_TRIGGERED ->
            Triple(Icons.Filled.VpnLock, Color(0xFF00E5FF), "PRE-EMPTIVE SHIELD")
        TransitionEventType.EXPOSURE_ALERT_POSTED ->
            Triple(Icons.Filled.NotificationsActive, Color(0xFFFFB74D), "5G EXPOSURE ALERT")
        TransitionEventType.VPN_CONNECT_SENT,
        TransitionEventType.VPN_DISCONNECT_SENT,
        TransitionEventType.TAILSCALE_CONNECT_SENT,
        TransitionEventType.TAILSCALE_DISCONNECT_SENT ->
            Triple(Icons.Filled.Bolt, primary, "VPN INTENT")
        TransitionEventType.VPN_CONNECTED,
        TransitionEventType.TAILSCALE_CONNECTED ->
            Triple(Icons.Filled.Shield, primary, "TUNNEL VERIFIED")
        TransitionEventType.VPN_DISCONNECTED,
        TransitionEventType.TAILSCALE_DISCONNECTED ->
            Triple(Icons.Filled.SwapHoriz, error, "TUNNEL CLOSED")
        TransitionEventType.KILL_SWITCH_ENGAGED ->
            Triple(Icons.Filled.Lock, tertiary, "5G KILL SWITCH")
        TransitionEventType.KILL_SWITCH_RELEASED ->
            Triple(Icons.Filled.LockOpen, secondary, "LOCKDOWN RELEASED")
        TransitionEventType.MANUAL_TOGGLE ->
            Triple(Icons.Filled.Bolt, primary, "RAPID TOGGLE")
    }
}
