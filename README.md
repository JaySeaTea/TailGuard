# TailGuard 🛡️
### Intelligent Android VPN Automation Orchestrator for Tailscale & WireGuard

[![Android 14+](https://img.shields.io/badge/Android-14%2B%20%7C%20API%2034%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Architecture](https://img.shields.io/badge/Architecture-Pure%20Orchestrator-00E5FF)](https://github.com)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**TailGuard** is a battery-friendly, hands-off VPN automation manager for Android. It monitors network transitions (Wi-Fi, 5G, weak RSSI) and automatically orchestrates official external VPN client apps (**Tailscale** and **WireGuard**) via secure broadcast intents.

Unlike conventional VPN wrappers, TailGuard **does not run an internal TUN interface or duplicate background VPN service**. It acts purely as a conductor, letting official, kernel-optimized VPN engines handle packet routing without battery drain, dual-VPN conflicts, or routing overhead.

---

## ⚡ Key Highlights

- **Zero-Permission Subnet Fingerprinting**: Automatically recognizes your Trusted Home/Office networks using DHCP Subnet CIDR (e.g. `192.168.50.0/24`) and Gateway/DNS signatures—**requiring zero Android location permissions**.
- **Pre-Emptive Wi-Fi Roaming Shield**: Detects weakening Wi-Fi signal strength (e.g., RSSI dropping past `-77 dBm` as you walk towards your car or porch) and connects your VPN *before* Wi-Fi drops, closing the unencrypted cellular exposure window.
- **5G Exposure Alerts + 1-Tap Internet Panel**: If cellular data becomes active without an established VPN tunnel, a high-priority heads-up alert appears with a 1-tap shortcut to Android’s floating Internet Panel.
- **Manual Override Mode on Trusted Wi-Fi**: Need your VPN on while at home to access remote servers or homelab subnets? Tapping the Hero card activates the VPN and suppresses auto-disconnect until you turn it off or reconnect.
- **Dual Client Support**:
  - **Tailscale**: Commands `com.tailscale.ipn` via official `CONNECT_VPN` and `DISCONNECT_VPN` broadcast intents.
  - **WireGuard**: Commands `com.wireguard.android` via `SET_TUNNEL_UP` and `SET_TUNNEL_DOWN` intents with built-in runtime permission diagnostics.
- **WireGuard Multi-Tunnel Selector**: Save and switch between multiple named tunnel profiles (e.g., `Home Router`, `ProtonVPN`, `wg0`) with deletion confirmation protection.
- **Transition Audit Logs**: Chronological log of network shifts, RSSI alerts, and dispatched VPN intents with instant CSV export for troubleshooting.
- **Modern Material 3 UI**: Edge-to-edge layout, responsive tablet navigation rail, dynamic dark/light theme, Quick Settings Tile, and 1x1 rapid toggle widget.

---

## 🏗️ Architecture & Philosophy

```
  ┌─────────────────────────────────────────────────────────────┐
  │                        TailGuard                            │
  │   - Passive NetworkCallback (Zero Polling Battery Impact)    │
  │   - Subnet CIDR + Gateway Fingerprint Matching              │
  │   - Pre-Emptive RSSI Threshold Monitor (-77 dBm)            │
  └──────────────┬──────────────────────────────┬───────────────┘
                 │ (Broadcast Intents)          │ (Broadcast Intents)
                 ▼                              ▼
    ┌──────────────────────────┐   ┌───────────────────────────┐
    │     Tailscale Client     │   │      WireGuard Client     │
    │   (com.tailscale.ipn)    │   │  (com.wireguard.android)  │
    │  - CONNECT_VPN           │   │  - SET_TUNNEL_UP          │
    │  - DISCONNECT_VPN        │   │  - SET_TUNNEL_DOWN        │
    └──────────────────────────┘   └───────────────────────────┘
```

1. **No Duplicate TUN Interface**: Android only allows one active `VpnService` at a time. TailGuard stays out of the data path and delegates routing to Tailscale or WireGuard.
2. **Event-Driven, Zero Polling**: Relies on Android's event-driven `ConnectivityManager.NetworkCallback` and LinkProperties change events, consuming virtually 0% idle battery.
3. **Privacy First**: Wi-Fi SSID access requires continuous GPS location permissions on Android 10+. TailGuard's subnet fingerprinting verifies your network cryptographically/structurally without needing location access.

---

## 🚀 Setup & Getting Started

### Prerequisites
- Android 14+ device (tested and optimized for Pixel 9 series and Android 17).
- Either the official **Tailscale** app or **WireGuard** app installed on your phone.

---

### Setting Up WireGuard

1. Open the official **WireGuard** app and import your `.conf` tunnel file (e.g., named `AsusRouter` or `Home Router`).
2. In WireGuard, open **Settings** ➔ **Advanced** ➔ ensure **Allow remote control apps** is **checked**.
3. Open **TailGuard**:
   - In the **VPN Client Provider** card, select **WireGuard**.
   - Tap **"Grant WireGuard Control Permission"** to approve the `com.wireguard.android.permission.CONTROL_TUNNELS` runtime permission.
   - Tap **"Add Tunnel Profile"** and enter the exact tunnel name matching your WireGuard configuration.

---

### Setting Up Tailscale

1. Install the official **Tailscale** app from Google Play or F-Droid and log in to your tailnet.
2. Open **TailGuard**:
   - In the **VPN Client Provider** card, select **Tailscale**.
   - TailGuard will automatically detect the installed package (`com.tailscale.ipn`).

---

### Adding Your Trusted Network (Home / Office)

1. Connect your phone to your trusted Wi-Fi network.
2. Open TailGuard on the **Dashboard** or **Trusted Nets** tab.
3. Tap **"Trust Current Network"**.
4. TailGuard captures your Subnet CIDR (e.g. `192.168.50.0/24`) and router gateway. Tap **Save**.
5. You're done! When you disconnect or walk away from this Wi-Fi, TailGuard automatically engages the VPN. When you return, it safely disconnects to save phone battery.

---

## 🛠️ Building From Source

### Requirements
- Android Studio Koala / Ladybug or newer
- JDK 17+
- Android SDK 35/36

### Build Steps
```bash
# Clone the repository
git clone https://github.com/<your-username>/tailguard.git
cd tailguard

# Compile Debug APK
gradle assembleDebug

# Run Unit & Robolectric Tests
gradle testDebugUnitTest
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 Permissions & Privacy

TailGuard uses the principle of least privilege:

| Permission | Purpose | Optional / Required |
| :--- | :--- | :--- |
| `ACCESS_NETWORK_STATE` | Detect network transitions and link properties | Required |
| `INTERNET` | Validate gateway resolution and reachability | Required |
| `POST_NOTIFICATIONS` | 5G exposure heads-up alerts and persistent monitoring | Optional (Android 13+) |
| `CONTROL_TUNNELS` | Dispatch broadcast intents to WireGuard | Required for WireGuard |
| `ACCESS_FINE_LOCATION` | Optional SSID name matching only if manually enabled | 100% Optional |

---

## 🤝 Contributing & Community

Contributions are welcome! Please feel free to open an Issue for bug reports, device-specific Wi-Fi roaming feedback, or pull requests for new VPN provider bridges.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'feat: add AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the Apache License 2.0. See `LICENSE` for more information.
