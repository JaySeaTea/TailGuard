package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.data.VpnPosture
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.LockdownAmber
import com.example.ui.theme.MeshCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SlateTextMuted

/**
 * Custom Canvas 3x3 Mesh Node Matrix inspired by WireGuard & Tailscale dot-grid iconography.
 * Dynamically animates node illumination and WireGuard mesh interconnects based on [VpnPosture].
 */
@Composable
fun MeshNodeIndicator(
    posture: VpnPosture,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = "VPN Mesh Status Matrix"
) {
    val activeColor by animateColorAsState(
        targetValue = when (posture) {
            VpnPosture.VPN_ACTIVE,
            VpnPosture.TAILSCALE_ACTIVE -> MeshCyan
            VpnPosture.PREEMPTIVE_SHIELD -> Color(0xFF00E5FF)
            VpnPosture.KILL_SWITCH_LOCKDOWN -> LockdownAmber
            VpnPosture.HANDSHAKE_PENDING -> MeshCyan
            VpnPosture.TRUSTED_BYPASS -> SignalEmerald
            VpnPosture.UNPROTECTED -> AlertCrimson
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "meshActiveColor"
    )

    val pulseTransition = rememberInfiniteTransition(label = "meshPulse")
    val pulsePhase by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // WireGuard / Tailscale "T" crossbar indices in a 0..8 (3x3) grid:
    // Row 0: [0, 1, 2]
    // Row 1: [3, 4, 5]
    // Row 2: [6, 7, 8]
    val wireguardTNodes = setOf(1, 3, 4, 5, 7)
    val shieldDiamondNodes = setOf(1, 3, 4, 5, 7)
    val lockdownXNodes = setOf(0, 2, 4, 6, 8)
    val trustedRingNodes = setOf(0, 1, 2, 3, 5, 6, 7, 8)

    Canvas(
        modifier = modifier.semantics {
            contentDescription = accessibilityLabel
        }
    ) {
        val w = size.width
        val h = size.height
        val minDim = minOf(w, h)
        val padding = minDim * 0.18f
        val stepX = (w - padding * 2f) / 2f
        val stepY = (h - padding * 2f) / 2f
        val baseRadius = minDim * 0.088f

        val centers = List(9) { idx ->
            val r = idx / 3
            val c = idx % 3
            Offset(padding + c * stepX, padding + r * stepY)
        }

        // Draw subtle mesh link lines when protected or connecting
        val isProtected = posture == VpnPosture.VPN_ACTIVE ||
            posture == VpnPosture.TAILSCALE_ACTIVE ||
            posture == VpnPosture.PREEMPTIVE_SHIELD ||
            posture == VpnPosture.HANDSHAKE_PENDING

        if (isProtected) {
            val lineAlpha = if (posture == VpnPosture.HANDSHAKE_PENDING || posture == VpnPosture.PREEMPTIVE_SHIELD) {
                pulsePhase * 0.48f
            } else {
                0.40f
            }
            val edges = listOf(1 to 4, 3 to 4, 5 to 4, 7 to 4, 1 to 3, 1 to 5)
            for ((a, b) in edges) {
                drawLine(
                    color = activeColor.copy(alpha = lineAlpha),
                    start = centers[a],
                    end = centers[b],
                    strokeWidth = minDim * 0.032f,
                    cap = StrokeCap.Round
                )
            }
        } else if (posture == VpnPosture.KILL_SWITCH_LOCKDOWN) {
            val edges = listOf(0 to 4, 2 to 4, 6 to 4, 8 to 4)
            for ((a, b) in edges) {
                drawLine(
                    color = activeColor.copy(alpha = 0.45f * pulsePhase),
                    start = centers[a],
                    end = centers[b],
                    strokeWidth = minDim * 0.036f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Draw 9 mesh nodes
        centers.forEachIndexed { idx, center ->
            val isHighlighted = when (posture) {
                VpnPosture.VPN_ACTIVE,
                VpnPosture.TAILSCALE_ACTIVE -> idx in wireguardTNodes
                VpnPosture.PREEMPTIVE_SHIELD -> idx in shieldDiamondNodes
                VpnPosture.HANDSHAKE_PENDING -> idx in wireguardTNodes
                VpnPosture.KILL_SWITCH_LOCKDOWN -> idx in lockdownXNodes
                VpnPosture.TRUSTED_BYPASS -> idx in trustedRingNodes
                VpnPosture.UNPROTECTED -> idx == 4
            }

            if (isHighlighted) {
                val dynamicAlpha = when (posture) {
                    VpnPosture.HANDSHAKE_PENDING,
                    VpnPosture.PREEMPTIVE_SHIELD,
                    VpnPosture.KILL_SWITCH_LOCKDOWN -> pulsePhase
                    else -> 1f
                }
                // Soft outer halo
                drawCircle(
                    color = activeColor.copy(alpha = 0.22f * dynamicAlpha),
                    radius = baseRadius * 1.65f,
                    center = center
                )
                drawCircle(
                    color = activeColor.copy(alpha = dynamicAlpha),
                    radius = baseRadius,
                    center = center
                )
            } else {
                drawCircle(
                    color = SlateTextMuted.copy(alpha = 0.28f),
                    radius = baseRadius * 0.85f,
                    center = center
                )
            }
        }
    }
}
