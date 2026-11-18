package com.example

import com.example.data.ActiveNetworkSnapshot
import com.example.data.LogFilter
import com.example.data.MatchMode
import com.example.data.TailGuardRepository
import com.example.data.TransitionEventType
import com.example.data.TransitionLogEntity
import com.example.data.TransportKind
import com.example.data.TrustedNetworkEntity
import com.example.data.VpnPosture
import com.example.data.VpnProvider
import com.example.service.NetworkFingerprintInspector
import com.example.service.TailscaleBridge
import com.example.service.VpnAutomationController
import com.example.service.WireGuardBridge
import com.example.ui.MainViewModel
import java.net.Inet4Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `computeIpv4NetworkCidr masks host bits accurately`() {
        val addr = InetAddress.getByName("192.168.50.137") as Inet4Address
        val cidr = NetworkFingerprintInspector.computeIpv4NetworkCidr(addr, 24)
        assertEquals("192.168.50.0/24", cidr)
    }

    @Test
    fun `trusted network fingerprint matching survives Pi-hole and Exit Node DNS changes`() {
        // Rule saved while Pi-hole / IPv6 DNS was present
        val homeRule = TrustedNetworkEntity(
            id = 1L,
            label = "Home Fiber",
            matchMode = MatchMode.FINGERPRINT,
            subnetCidr = "192.168.50.0/24",
            gatewayAndDnsSignature = "gw:192.168.50.1 | dns:100.100.100.100,192.168.50.2",
            ssid = null
        )
        // Active Wi-Fi snapshot when VPN disconnects and router DHCP DNS is active instead
        val wifiSnap = ActiveNetworkSnapshot(
            transportKind = TransportKind.WIFI,
            transportLabel = "Wi-Fi",
            subnetCidr = "192.168.50.0/24",
            gatewayIp = "192.168.50.1",
            gatewayAndDnsSignature = "gw:192.168.50.1 | dns:192.168.50.1",
            ssid = null
        )
        val matched = TailGuardRepository.findMatchingTrustedNetwork(wifiSnap, listOf(homeRule))
        assertNotNull(matched)
        assertEquals("Home Fiber", matched?.label)

        // Different router gateway on another 192.168.50.0/24 network should NOT match
        val spoofedGatewaySnap = wifiSnap.copy(
            gatewayIp = "192.168.50.254",
            gatewayAndDnsSignature = "gw:192.168.50.254 | dns:192.168.50.254"
        )
        assertNull(TailGuardRepository.findMatchingTrustedNetwork(spoofedGatewaySnap, listOf(homeRule)))

        // Cellular 5G must NEVER match a Wi-Fi Trusted Network
        val cellularSnap = wifiSnap.copy(transportKind = TransportKind.CELLULAR_5G)
        assertNull(TailGuardRepository.findMatchingTrustedNetwork(cellularSnap, listOf(homeRule)))

        // Low-quality / unvalidated Wi-Fi (e.g. rejected by Android) must NEVER match or drop VPN
        val unvalidatedWifiSnap = wifiSnap.copy(isValidated = false)
        assertNull(TailGuardRepository.findMatchingTrustedNetwork(unvalidatedWifiSnap, listOf(homeRule)))
    }

    @Test
    fun `checkVpnInetAddress detects Tailscale CGNAT and WireGuard private IPs`() {
        val tailscaleNodeIp = InetAddress.getByName("100.84.204.99")
        assertEquals("100.84.204.99", NetworkFingerprintInspector.checkVpnInetAddress(tailscaleNodeIp))

        val wireguardPrivateIp = InetAddress.getByName("10.0.0.2")
        assertEquals("10.0.0.2", NetworkFingerprintInspector.checkVpnInetAddress(wireguardPrivateIp))

        val magicDns = InetAddress.getByName("100.100.100.100")
        assertNull(NetworkFingerprintInspector.checkVpnInetAddress(magicDns))
    }

    @Test
    fun `wireguard intent actions and package constants comply with WireGuard Android specification`() {
        assertEquals("com.wireguard.android", WireGuardBridge.WIREGUARD_PACKAGE)
        assertEquals(
            "com.wireguard.android.model.TunnelManager\$IntentReceiver",
            WireGuardBridge.WIREGUARD_RECEIVER
        )
        assertEquals("com.wireguard.android.action.SET_TUNNEL_UP", WireGuardBridge.ACTION_SET_TUNNEL_UP)
        assertEquals("com.wireguard.android.action.SET_TUNNEL_DOWN", WireGuardBridge.ACTION_SET_TUNNEL_DOWN)
        assertEquals("tunnel", WireGuardBridge.EXTRA_TUNNEL)
    }

    @Test
    fun `tailscale intent actions and package constants match IPNReceiver specification`() {
        assertEquals("com.tailscale.ipn", TailscaleBridge.TAILSCALE_PACKAGE)
        assertEquals("com.tailscale.ipn.IPNReceiver", TailscaleBridge.TAILSCALE_RECEIVER)
        assertEquals("com.tailscale.ipn.CONNECT_VPN", TailscaleBridge.ACTION_CONNECT_VPN)
        assertEquals("com.tailscale.ipn.DISCONNECT_VPN", TailscaleBridge.ACTION_DISCONNECT_VPN)
    }

    @Test
    fun `vpn posture transitions prioritize verified tunnel then pre-emptive shield`() {
        val base = ActiveNetworkSnapshot(transportKind = TransportKind.CELLULAR_5G)

        // 1. Untrusted connection without tunnel
        assertEquals(
            VpnPosture.UNPROTECTED,
            VpnAutomationController.computePosture(
                base,
                matched = null,
                isHandshakePending = false,
                isPreemptiveActive = false
            )
        )

        // 2. Pre-Emptive Shield active on weak Wi-Fi
        val wifiBase = ActiveNetworkSnapshot(
            transportKind = TransportKind.WIFI,
            wifiRssiDbm = -81
        )
        val homeRule = TrustedNetworkEntity(id = 1L, label = "Home Wi-Fi")
        assertEquals(
            VpnPosture.PREEMPTIVE_SHIELD,
            VpnAutomationController.computePosture(
                wifiBase,
                matched = homeRule,
                isHandshakePending = true,
                isPreemptiveActive = true
            )
        )

        // 3. Handshake pending on 5G
        assertEquals(
            VpnPosture.HANDSHAKE_PENDING,
            VpnAutomationController.computePosture(
                base,
                matched = null,
                isHandshakePending = true,
                isPreemptiveActive = false
            )
        )

        // 4. VPN Active on 5G
        assertEquals(
            VpnPosture.VPN_ACTIVE,
            VpnAutomationController.computePosture(
                base.copy(isVpnTunnelActive = true, vpnIp = "100.84.204.99"),
                matched = null,
                isHandshakePending = false,
                isPreemptiveActive = false
            )
        )

        // 5. Manual Override on Trusted Wi-Fi (connecting)
        assertEquals(
            VpnPosture.HANDSHAKE_PENDING,
            VpnAutomationController.computePosture(
                wifiBase,
                matched = homeRule,
                isHandshakePending = false,
                isPreemptiveActive = false,
                isManualOverride = true
            )
        )

        // 6. Manual Override on Trusted Wi-Fi (tunnel active)
        assertEquals(
            VpnPosture.VPN_ACTIVE,
            VpnAutomationController.computePosture(
                wifiBase.copy(isVpnTunnelActive = true, vpnIp = "10.0.0.2"),
                matched = homeRule,
                isHandshakePending = false,
                isPreemptiveActive = false,
                isManualOverride = true
            )
        )
    }

    @Test
    fun `wireguard control tunnels permission constant is declared accurately`() {
        assertEquals(
            "com.wireguard.android.permission.CONTROL_TUNNELS",
            WireGuardBridge.PERMISSION_CONTROL_TUNNELS
        )
    }

    @Test
    fun `log filter correctly isolates VPN intents alerts and network shifts`() {
        val sampleLogs = listOf(
            TransitionLogEntity(1, 100L, TransitionEventType.PREEMPTIVE_CONNECT_TRIGGERED, "Wi-Fi", "Shield armed"),
            TransitionLogEntity(2, 200L, TransitionEventType.VPN_CONNECT_SENT, "5G", "Sent connect"),
            TransitionLogEntity(3, 300L, TransitionEventType.TRUSTED_CONNECTED, "Wi-Fi", "Trusted matched"),
            TransitionLogEntity(4, 400L, TransitionEventType.EXPOSURE_ALERT_POSTED, "5G", "Alert posted")
        )
        assertEquals(2, MainViewModel.filterLogs(sampleLogs, LogFilter.ALERTS).size)
        assertEquals(1, MainViewModel.filterLogs(sampleLogs, LogFilter.VPN_INTENTS).size)
        assertEquals(1, MainViewModel.filterLogs(sampleLogs, LogFilter.NETWORK_SHIFTS).size)
        assertEquals(4, MainViewModel.filterLogs(sampleLogs, LogFilter.ALL).size)
    }

    @Test
    fun `vpn provider enum parses correctly`() {
        assertEquals(VpnProvider.TAILSCALE, VpnProvider.fromId("tailscale"))
        assertEquals(VpnProvider.WIREGUARD, VpnProvider.fromId("wireguard"))
        assertEquals(VpnProvider.TAILSCALE, VpnProvider.fromId(null))
        assertEquals(VpnProvider.TAILSCALE, VpnProvider.fromId("unknown"))
    }

    @Test
    fun `automation settings includes saved WireGuard tunnel profiles and default active tunnel`() {
        val settings = com.example.data.AutomationSettings()
        assertEquals("Home Router", settings.wireguardTunnelName)
        assertTrue(settings.savedWireguardTunnels.contains("Home Router"))
        assertTrue(settings.savedWireguardTunnels.contains("ProtonVPN"))
        assertTrue(settings.savedWireguardTunnels.contains("wg0"))
    }

    @Test
    fun `connection flap detector latches on 5 cycles within 10 seconds and ignores slower transitions`() {
        val detector = com.example.service.ConnectionFlapDetector(maxCycles = 5, windowMs = 10_000L)

        // Simulate 5 ~1.5s connect/disconnect cycles (~7.5s total) matching real TailGuard log cadence
        var t = 1_000L
        for (cycle in 1..4) {
            org.junit.Assert.assertFalse("Connect cycle $cycle should not latch early", detector.recordAction(com.example.service.ConnectionFlapDetector.FlapAction.CONNECT, t))
            t += 900L
            org.junit.Assert.assertFalse("Disconnect cycle $cycle should not latch early", detector.recordAction(com.example.service.ConnectionFlapDetector.FlapAction.DISCONNECT, t))
            t += 600L
        }

        // Cycle 5 Connect at +6.0s, Disconnect at +6.9s triggers latch!
        org.junit.Assert.assertFalse(detector.recordAction(com.example.service.ConnectionFlapDetector.FlapAction.CONNECT, t))
        t += 900L
        assertTrue(
            "5th cycle within 10s must trigger unstable network latch",
            detector.recordAction(com.example.service.ConnectionFlapDetector.FlapAction.DISCONNECT, t)
        )
        assertEquals(5, detector.currentCycles(t))

        // After reset, slow cycles spaced 3s apart (>10s window across 5 cycles) should NOT latch
        detector.reset()
        var slowT = 100_000L
        for (cycle in 1..6) {
            org.junit.Assert.assertFalse(detector.recordAction(com.example.service.ConnectionFlapDetector.FlapAction.CONNECT, slowT))
            slowT += 1_500L
            org.junit.Assert.assertFalse(
                "Slow transitions exceeding 10s rolling window must not latch (cycle $cycle)",
                detector.recordAction(com.example.service.ConnectionFlapDetector.FlapAction.DISCONNECT, slowT)
            )
            slowT += 1_500L
        }
    }

    @Test
    fun `unstable latch posture takes precedence and latch events appear in Alerts filter`() {
        val snap = ActiveNetworkSnapshot(
            transportKind = TransportKind.WIFI,
            isVpnTunnelActive = true,
            vpnIp = "100.84.204.99"
        )
        assertEquals(
            VpnPosture.UNSTABLE_LATCHED,
            VpnAutomationController.computePosture(
                snap = snap,
                matched = null,
                isHandshakePending = false,
                isUnstableLatched = true
            )
        )

        val latchLogs = listOf(
            TransitionLogEntity(1, 100L, TransitionEventType.UNSTABLE_NETWORK_LATCHED, "Wi-Fi", "Latched ON"),
            TransitionLogEntity(2, 200L, TransitionEventType.UNSTABLE_LATCH_RELEASED, "Wi-Fi", "Resumed")
        )
        assertEquals(2, MainViewModel.filterLogs(latchLogs, LogFilter.ALERTS).size)
    }
}
