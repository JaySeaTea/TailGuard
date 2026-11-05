package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trusted_networks")
data class TrustedNetworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val matchMode: MatchMode = MatchMode.FINGERPRINT,
    val subnetCidr: String = "",
    val gatewayAndDnsSignature: String = "",
    val ssid: String? = null,
    val autoDisconnectVpn: Boolean = true,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "connection_transition_logs")
data class TransitionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: TransitionEventType,
    val networkSummary: String,
    val vpnActionSummary: String
)
