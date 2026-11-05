package com.example.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.ActiveNetworkSnapshot
import com.example.data.TailGuardRepository
import com.example.data.TransportKind
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

class NetworkFingerprintInspector(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Caches the last observed VPN tunnel IP across an unbroken `TRANSPORT_VPN` session.
     * On Android 12–17 with an Exit Node, Android redacts foreign VPN `linkAddresses` ~500ms
     * after tunnel establishment while `TRANSPORT_VPN` remains `true`.
     */
    @Volatile
    private var lastObservedSessionVpnIp: String? = null

    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    fun captureSnapshot(): ActiveNetworkSnapshot {
        val allNetworks = runCatching { connectivityManager.allNetworks }.getOrDefault(emptyArray())

        var explicitVpnIp: String? = null
        var anyVpnTransportUp = false
        var primaryPhysicalNetwork: Network? = null
        var primaryCaps: NetworkCapabilities? = null
        var primaryProps: LinkProperties? = null

        for (net in allNetworks) {
            val caps = runCatching { connectivityManager.getNetworkCapabilities(net) }.getOrNull() ?: continue
            val props = runCatching { connectivityManager.getLinkProperties(net) }.getOrNull()

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                anyVpnTransportUp = true
                if (props != null) {
                    val candidateIp = findVpnIp(props)
                    if (candidateIp != null) {
                        explicitVpnIp = candidateIp
                    }
                }
            } else if (
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            ) {
                val candidateIsValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val currentIsValidated = primaryCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

                val currentIsWifi = primaryCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                    primaryCaps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                val candidateIsWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

                // Never let an unvalidated/rejected Wi-Fi override a validated Cellular network!
                val shouldReplace = when {
                    primaryPhysicalNetwork == null -> true
                    candidateIsValidated && !currentIsValidated -> true
                    !candidateIsValidated && currentIsValidated -> false
                    !currentIsWifi && candidateIsWifi -> true
                    else -> false
                }

                if (shouldReplace) {
                    primaryPhysicalNetwork = net
                    primaryCaps = caps
                    primaryProps = props
                }
            }
        }

        // Also check activeNetwork: if it's not a VPN, the OS itself has selected it as the primary internet route!
        val activeNet = runCatching { connectivityManager.activeNetwork }.getOrNull()
        val activeCaps = activeNet?.let { runCatching { connectivityManager.getNetworkCapabilities(it) }.getOrNull() }
        val activeProps = activeNet?.let { runCatching { connectivityManager.getLinkProperties(it) }.getOrNull() }

        if (activeCaps != null) {
            if (activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                anyVpnTransportUp = true
                if (activeProps != null && explicitVpnIp == null) {
                    explicitVpnIp = findVpnIp(activeProps)
                }
            } else {
                primaryPhysicalNetwork = activeNet
                primaryCaps = activeCaps
                primaryProps = activeProps
            }
        }

        // Optional kernel tun*/wg* inspection for unredacted tunnel IP
        if (explicitVpnIp == null) {
            explicitVpnIp = findVpnIpFromNetworkInterfaces()
            if (explicitVpnIp != null) {
                anyVpnTransportUp = true
            }
        }

        val resolvedVpnIp = if (anyVpnTransportUp) {
            if (explicitVpnIp != null) {
                lastObservedSessionVpnIp = explicitVpnIp
                explicitVpnIp
            } else {
                lastObservedSessionVpnIp ?: "Active (Exit Node)"
            }
        } else {
            lastObservedSessionVpnIp = null
            null
        }

        if (primaryCaps == null) {
            return ActiveNetworkSnapshot(
                transportKind = TransportKind.NONE,
                transportLabel = "Offline",
                isValidated = false,
                isVpnTransportPresent = anyVpnTransportUp,
                isVpnTunnelActive = anyVpnTransportUp,
                vpnIp = resolvedVpnIp,
                isKillSwitchSinkholeActive = false
            )
        }

        val transportKind = resolveTransportKind(primaryCaps)
        val isPhysicalValidated = primaryCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val transportLabel = when (transportKind) {
            TransportKind.WIFI -> if (isPhysicalValidated) "Wi-Fi" else "Wi-Fi (Unvalidated)"
            TransportKind.ETHERNET -> "Ethernet LAN"
            TransportKind.CELLULAR_5G -> "5G / Cellular"
            TransportKind.CELLULAR_LTE -> "Cellular Data"
            TransportKind.NONE -> "Offline"
        }

        val subnetCidr = primaryProps?.let { extractPrimarySubnetCidr(it) }.orEmpty()
        val gatewayIp = primaryProps?.let { extractDefaultGateway(it) }.orEmpty()

        val dnsList = primaryProps?.dnsServers
            ?.mapNotNull { addr ->
                val host = addr.hostAddress?.substringBefore('%') ?: return@mapNotNull null
                if (host == "100.100.100.100" || host == "10.255.255.1" || host.startsWith("fd00:dead:beef")) {
                    null
                } else if (addr is Inet4Address) {
                    host
                } else {
                    null
                }
            }
            ?.distinct()
            ?.sorted()
            .orEmpty()

        val signature = buildString {
            if (gatewayIp.isNotEmpty()) append("gw:$gatewayIp")
            if (dnsList.isNotEmpty()) {
                if (isNotEmpty()) append(" | ")
                append("dns:${dnsList.take(2).joinToString(",")}")
            }
        }

        val resolvedSsid = if (transportKind == TransportKind.WIFI && hasFineLocationPermission()) {
            readCurrentWifiSsid(primaryCaps)
        } else {
            null
        }

        val wifiRssiDbm = if (transportKind == TransportKind.WIFI) {
            readWifiRssiDbm(primaryCaps)
        } else {
            null
        }

        return ActiveNetworkSnapshot(
            transportKind = transportKind,
            transportLabel = transportLabel,
            subnetCidr = subnetCidr,
            gatewayIp = gatewayIp,
            dnsServers = dnsList,
            gatewayAndDnsSignature = signature,
            ssid = resolvedSsid,
            wifiRssiDbm = wifiRssiDbm,
            isValidated = isPhysicalValidated,
            isVpnTransportPresent = anyVpnTransportUp,
            isVpnTunnelActive = anyVpnTransportUp,
            vpnIp = resolvedVpnIp,
            isKillSwitchSinkholeActive = false
        )
    }

    private fun resolveTransportKind(caps: NetworkCapabilities): TransportKind {
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TransportKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TransportKind.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val downBw = caps.linkDownstreamBandwidthKbps
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) || downBw >= 50_000) {
                    TransportKind.CELLULAR_5G
                } else {
                    TransportKind.CELLULAR_LTE
                }
            }
            else -> TransportKind.NONE
        }
    }

    @Suppress("DEPRECATION")
    private fun readCurrentWifiSsid(caps: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val transportInfo = caps.transportInfo
            if (transportInfo is WifiInfo) {
                val fromCaps = TailGuardRepository.normalizeSsid(transportInfo.ssid)
                if (fromCaps != null) return fromCaps
            }
        }
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return TailGuardRepository.normalizeSsid(wifiManager?.connectionInfo?.ssid)
    }

    @Suppress("DEPRECATION")
    private fun readWifiRssiDbm(caps: NetworkCapabilities): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val dbm = caps.signalStrength
            if (dbm in -125..-10) {
                return dbm
            }
            val transportInfo = caps.transportInfo
            if (transportInfo is WifiInfo) {
                val rssi = transportInfo.rssi
                if (rssi in -125..-10) {
                    return rssi
                }
            }
        }
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val rssi = wifiManager?.connectionInfo?.rssi
        return if (rssi != null && rssi in -125..-10) rssi else null
    }

    companion object {
        fun findVpnIp(props: LinkProperties): String? {
            for (linkAddr in props.linkAddresses) {
                val ip = checkVpnInetAddress(linkAddr.address)
                if (ip != null) return ip
            }
            return null
        }

        fun findVpnIpFromNetworkInterfaces(): String? {
            return runCatching {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
                for (nif in Collections.list(interfaces)) {
                    if (!nif.isUp || nif.isLoopback) continue
                    val name = nif.name.lowercase()
                    val isTunnelInterface = name.startsWith("tun") ||
                        name.startsWith("wg") ||
                        name.contains("tailscale")
                    if (!isTunnelInterface) continue

                    for (addr in Collections.list(nif.inetAddresses)) {
                        val match = checkVpnInetAddress(addr)
                        if (match != null) return@runCatching match
                    }
                }
                null
            }.getOrNull()
        }

        fun checkVpnInetAddress(addr: InetAddress): String? {
            val host = addr.hostAddress?.substringBefore('%') ?: return null
            if (addr is Inet4Address) {
                val bytes = addr.address
                val first = bytes[0].toInt() and 0xFF
                val second = bytes[1].toInt() and 0xFF
                // Tailscale CGNAT 100.64.0.0/10
                if (first == 100 && second in 64..127 && host != "100.100.100.100") {
                    return host
                }
                // Private IPv4 on tunnel interface (WireGuard wg0 or custom mesh)
                if ((first == 10) || (first == 172 && second in 16..31) || (first == 192 && second == 168)) {
                    return host
                }
            } else if (host.lowercase().startsWith("fd7a:115c:a1e0:")) {
                // Tailscale IPv6
                return host
            }
            return null
        }

        fun extractPrimarySubnetCidr(props: LinkProperties): String {
            val ipv4Link = props.linkAddresses.firstOrNull {
                it.address is Inet4Address && !it.address.isLoopbackAddress
            }
            if (ipv4Link != null) {
                return computeIpv4NetworkCidr(ipv4Link.address as Inet4Address, ipv4Link.prefixLength)
            }
            val ipv6Link = props.linkAddresses.firstOrNull {
                !it.address.isLoopbackAddress && !it.address.isLinkLocalAddress
            }
            if (ipv6Link != null) {
                val cleanHost = ipv6Link.address.hostAddress?.substringBefore('%').orEmpty()
                return "$cleanHost/${ipv6Link.prefixLength}"
            }
            return ""
        }

        fun computeIpv4NetworkCidr(address: Inet4Address, prefixLength: Int): String {
            val safePrefix = prefixLength.coerceIn(0, 32)
            val rawBytes = address.address
            val ipInt = ((rawBytes[0].toInt() and 0xFF) shl 24) or
                ((rawBytes[1].toInt() and 0xFF) shl 16) or
                ((rawBytes[2].toInt() and 0xFF) shl 8) or
                (rawBytes[3].toInt() and 0xFF)
            val mask = if (safePrefix == 0) 0 else (-1 shl (32 - safePrefix))
            val netInt = ipInt and mask
            val b0 = (netInt ushr 24) and 0xFF
            val b1 = (netInt ushr 16) and 0xFF
            val b2 = (netInt ushr 8) and 0xFF
            val b3 = netInt and 0xFF
            return "$b0.$b1.$b2.$b3/$safePrefix"
        }

        private fun extractDefaultGateway(props: LinkProperties): String {
            for (route in props.routes) {
                val gw: InetAddress? = route.gateway
                if (route.isDefaultRoute && gw != null && !gw.isAnyLocalAddress) {
                    val text = gw.hostAddress?.substringBefore('%').orEmpty()
                    if (gw is Inet4Address) return text
                }
            }
            return props.routes.firstOrNull {
                it.gateway is Inet4Address && !it.gateway!!.isAnyLocalAddress
            }?.gateway?.hostAddress?.substringBefore('%').orEmpty()
        }
    }
}
