# TailGuard — Manual Trusted Network Bypass, Deletion Safeguards & WireGuard Remote Fix

A targeted functional update addressing three critical user requirements:
1. **Manual VPN Activation (Trusted Network Bypass)**: Enabling forced VPN connection while on Trusted Wi-Fi without TailGuard immediately disconnecting it, with auto-resumption of normal rules upon network reconnect.
2. **Tunnel Deletion Safeguard**: A confirmation pop-up dialog before removing defined WireGuard tunnel profiles.
3. **WireGuard Connection Resolution**: Resolving why tunnels (such as "AsusRouter") failed to activate by introducing runtime grant handling for WireGuard's `dangerous` `com.wireguard.android.permission.CONTROL_TUNNELS` permission and resilient broadcast dispatch.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions incorporate your confirmed answers from Phase 1:

- **Confirmed — Manual Activation Behavior**:
  - Tapping "Activate Tailscale / WireGuard VPN" on the Dashboard hero card while on a Trusted Network engages **Manual Override Mode**.
  - While in Manual Override Mode, TailGuard suspends automatic disconnect on Trusted Networks, allowing you to use apps requiring the VPN.
  - Per your selection: **Manual override automatically resets on the next network reconnect**, smoothly returning to hands-off auto-disconnect without requiring manual reconfiguration.
- **Confirmed — WireGuard Control Permission Prompt & Live Diagnostics**:
  - TailGuard will include a one-tap runtime permission prompt for `com.wireguard.android.permission.CONTROL_TUNNELS` and a live status check on the WireGuard card so you can immediately verify that Android is permitting intent communication to the WireGuard app.

---

## 1. Overview & Core Concept

- **The Problem**:
  1. On Trusted Wi-Fi, TailGuard's automated rule immediately dispatches `DISCONNECT_VPN` whenever a tunnel is active, preventing you from deliberately running Tailscale or WireGuard for specific apps while at home.
  2. Tapping the "x" on an inactive tunnel profile deletes it immediately with no undo or confirmation.
  3. WireGuard defines its broadcast receiver permission (`com.wireguard.android.permission.CONTROL_TUNNELS`) with `android:protectionLevel="dangerous"`. On Android 17 (Pixel 9 Pro XL), broadcasts sent without an active runtime grant are dropped by Android's `ActivityManagerService` before reaching WireGuard's `TunnelManager$IntentReceiver`.
- **The Solution**:
  1. Track `isManualOverrideActive` in `VpnAutomationController`. When engaged via the Hero button, suppress `needsTrustedDisconnect`. Reset `isManualOverrideActive` when switching networks.
  2. Guard tunnel profile removal with a Material 3 `AlertDialog` confirming deletion.
  3. Implement runtime permission requesting for `com.wireguard.android.permission.CONTROL_TUNNELS` with an interactive setup card in Dashboard and resilient dual-mode intent broadcast in `WireGuardBridge`.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Manual VPN Activation on Trusted Wi-Fi**:
   - You are connected to "Junction - Main Network" (Trusted Wi-Fi).
   - Tapping **"Activate WireGuard VPN"** (or Tailscale) dispatches the connect broadcast and sets **Manual Override Active**.
   - The hero status card displays:
     - Title: **WireGuard Active (Manual Override)**
     - Subtitle: **Trusted Network auto-disconnect paused • Tap to resume auto-guard**
     - Button: **Deactivate WireGuard VPN** (with an outlined accent).
   - Tapping Deactivate returns to normal Trusted Network posture. If you leave home and reconnect later, TailGuard automatically restores standard auto-disconnect.

2. **WireGuard Tunnel Deletion Protection**:
   - Tapping the "x" on any saved WireGuard tunnel chip pops an `AlertDialog`:
     - Title: **Delete Tunnel Profile**
     - Message: *"Are you sure you want to delete profile '<name>'? You can re-add it at any time."*
     - Actions: **Cancel** and **Delete** (tonal red).

3. **WireGuard Permission & Live Diagnostics**:
   - In the **VPN Client Provider** card when WireGuard is selected:
     - If the Android runtime permission is missing, an amber surface banner appears:
       - *"⚠️ Android Remote Control Permission Required: WireGuard requires authorization for external apps to activate tunnels."*
       - Includes a prominent button: **Grant WireGuard Control Permission**.
     - Tapping the button launches Android's native runtime permission dialog (`ActivityResultContracts.RequestPermission()`).
     - Once granted, a green indicator verifies: **Remote Control Permission Active**.
     - Reminds you of the one-time toggle in WireGuard: **Settings → Advanced → Allow remote control apps**.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Scoped Manual Override Lifespan**
  - *Chosen Approach*: Override persists for the current session and clears when the network link reconnects or shifts.
  - *Why*: Perfectly balances deliberate on-demand VPN use at home with hands-off automation, eliminating the risk of forgetting that auto-guard was disabled.
- **Decision 2: Runtime Permission Handling for WireGuard**
  - *Chosen Approach*: Explicitly request `com.wireguard.android.permission.CONTROL_TUNNELS` using Jetpack `ActivityResultContracts.RequestPermission()`.
  - *Why*: WireGuard's manifest explicitly marks this permission as `dangerous`. Without a granted runtime token, Pixel 9 Pro XL / Android 17 blocks `sendBroadcast` calls silently.
- **Decision 3: Dual-Mode Intent Dispatch in `WireGuardBridge`**
  - *Chosen Approach*: In `WireGuardBridge`, set both `setPackage("com.wireguard.android")` and query `PackageManager` for active receivers matching `com.wireguard.android.action.SET_TUNNEL_UP`, falling back to `TunnelManager$IntentReceiver`.
  - *Why*: Guarantees intent delivery across both standard installations and vendor variants.

---

## 4. Technical Architecture & Component Mapping

```
┌────────────────────────────────────────────────────────────────────────┐
│                        USER & SYSTEM INTERFACES                        │
│  ┌──────────────────────┐  ┌─────────────────────┐  ┌───────────────┐  │
│  │ Dashboard Hero Card  │  │ Tunnel Profile Chip │  │ WireGuard Card│  │
│  │ (Manual Override)    │  │ (Confirm Delete)    │  │ (Perm Prompt) │  │
│  └──────────┬───────────┘  └──────────┬──────────┘  └───────┬───────┘  │
└─────────────┼─────────────────────────┼─────────────────────┼──────────┘
              ▼                         ▼                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      VpnAutomationController                           │
│  • Holds `isManualOverrideActive: Boolean`                             │
│  • Suppresses `needsTrustedDisconnect` while manual override is true   │
│  • Resets `isManualOverrideActive` on `physicalPolicyChanged`          │
│  • Evaluates posture: `VPN_ACTIVE` with override indicator             │
└─────────────┬──────────────────────────────────────────────────────────┘
              │
              ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         WireGuardBridge                                │
│  • Checks `hasWireGuardControlPermission()`                            │
│  • Dispatches explicit broadcast with `setPackage("com.wireguard...")` │
│  • Logs permission denial or dispatch failure with actionable tips     │
└────────────────────────────────────────────────────────────────────────┘
```

### Component Updates
1. **`VpnAutomationController.kt`**:
   - Add `isManualOverrideActive` state.
   - In `rapidToggleVpn()`: when toggled ON while `matched != null`, set `isManualOverrideActive = true`.
   - In `evaluateInternal()`: bypass `needsTrustedDisconnect` when `isManualOverrideActive == true`.
   - On physical network shift (`physicalPolicyChanged`): reset `isManualOverrideActive = false`.
   - In `computePosture()`: reflect manual override state in `OrchestratorState`.
2. **`WireGuardBridge.kt`**:
   - Add `hasControlPermission(): Boolean`.
   - Strengthen broadcast dispatch with explicit package, action, and extras.
3. **`DashboardScreen.kt`**:
   - Add delete confirmation `AlertDialog` for tunnel profile chips.
   - Add WireGuard runtime permission launcher and diagnostic banner with 1-tap grant button.
   - Update Hero card copy to reflect Manual Override when active on Trusted Wi-Fi.
4. **`ExampleUnitTest.kt`**:
   - Add unit tests validating manual override suppression and reset behavior.
