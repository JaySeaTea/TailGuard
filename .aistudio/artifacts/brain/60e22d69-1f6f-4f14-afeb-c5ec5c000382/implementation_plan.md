# TailGuard UX Simplification & Progressive Disclosure Plan

## Objective
Streamline and declutter the TailGuard Android interface to reduce visual noise and vertical scrolling while preserving 100% of existing network telemetry (Gateway, DNS, Subnet, RSSI, VPN IP) and granular automation controls.

---

## Proposed Changes

### 1. Hero Card: Embedded Network Glance
- **Current State**: Tall hero card showing status and large button, but network details are separated in a subsequent large card.
- **Revamp**:
  - Keep the responsive **MeshNodeIndicator** and high-contrast status badge (`TRUSTED NETWORK ACTIVE`, `MANUAL OVERRIDE`, etc.).
  - Embed a compact **Live Status Capsule** directly below the subtitle:
    - Chip 1: Active Transport & Signal (`Wi-Fi • -52 dBm`)
    - Chip 2: LAN Subnet (`192.168.50.0/24`)
    - Chip 3: VPN Tunnel state (`Tailscale • 100.84.204.99` or `Inactive`)
  - Retain the rapid one-tap Action Button (`Activate / Deactivate VPN`).

### 2. Network Telemetry: Compact Glance + Expandable Inspector
- **Current State**: 7 large, full-width rows taking up the middle 40% of the screen.
- **Revamp**:
  - **Collapsed State (Default)**:
    - A clean, modern **Network Glance Card** presenting a 2x2 scannable summary:
      - Link: Transport + SSID / Subnet
      - Gateway & DNS: Shortened gateway IP (`192.168.50.210`)
      - Signal: Dynamic colored meter with dBm
      - Matched Trusted Network badge
    - An intuitive **"Inspect Full Telemetry"** toggle with an animated expand/collapse chevron.
  - **Expanded State**:
    - Smoothly reveals all granular diagnostics:
      - Full Gateway & DNS signature string with right-aligned overflow handling
      - IPv4/IPv6 Subnet CIDR
      - Verified VPN Tunnel IP
      - Zero-permission SSID telemetry indicator
      - Quick manual refresh telemetry button with haptic feedback
      - "Trust Current Network" CTA when on an untrusted Wi-Fi LAN

### 3. VPN Provider Card: Clean Segmented Switcher
- Keep the simple segmented selector between Tailscale and WireGuard.
- When WireGuard is active, display:
  - Active Target Tunnel chip with 1-tap switcher.
  - One-tap Add Profile and Open WireGuard buttons.
  - Remote control permission status badge (`Remote Control Permission Active` or warning card if not granted).
  - Deletion protection dialog intact.

### 4. Advanced Controls: Collapsible Section
- **Current State**: Two separate, large cards (`TransitionSafeguardsCard` and `IntegrationDiagnosticsCard`) perpetually expanded, occupying excessive vertical space.
- **Revamp**:
  - Merge into a single **"Advanced Controls & Safeguards"** collapsible card on the Dashboard:
    - Header shows a subtle badge: e.g., *"3 Active Safeguards • Tailscale Verified"*.
    - Collapsed by default (or remembers user's toggle state).
    - Expanding reveals:
      - **Transition Safeguards**: Pre-emptive RSSI Shield toggle & dBm slider, 5G Exposure Alert toggle, Auto-disconnect on Trusted toggle, and the 1-tap floating Internet Panel button.
      - **Integration Diagnostics**: Client package detection indicator, Android System Always-On VPN shortcut, and Test Broadcast action.

---

## Verification Plan

### Automated Verification
1. **Compilation**: Run `compile_applet` to ensure syntax, imports, and Compose bindings build cleanly.
2. **Unit & Robolectric Tests**: Run `gradle :app:testDebugUnitTest` to verify:
   - All 12 existing test cases pass without regressions.
   - Compose test tags for the new glance card, expansion toggles, and hero button remain accessible for automated testing.

### Manual UX & Telemetry Verification
1. Verify the glance card shows instant high-level status without requiring scrolling.
2. Tap "Inspect Full Telemetry" to verify granular Gateway, DNS, Subnet, and RSSI expand smoothly.
3. Tap "Advanced Controls & Safeguards" to verify toggles and test broadcast triggers operate identically.
4. Verify the Hero toggle button still supports manual override on Trusted Wi-Fi.
