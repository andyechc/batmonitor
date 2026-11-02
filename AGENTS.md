# AGENTS.md

BatMonitor: a native app that reads XiaoXiang-compatible BMS batteries over BLE. Current scope is iOS only (SwiftUI). Android comes later, in `android/`, and is not started. The user writes in Spanish; reply in Spanish.

## Layout
- `ios/`: native iOS app. `project.yml` (XcodeGen) generates `BatMonitor.xcodeproj`. After changing `project.yml`, run `xcodegen generate` in `ios/`. The `.xcodeproj` is committed.
  - `BatMonitor/Protocol/BMSProtocol.swift`: pure Swift framing, checksums, command builders, and parsers. No Bluetooth dependency.
  - `BatMonitor/BLE/`: `BLECentral` (CoreBluetooth wrapper), `BatteryMonitor` (poll and reconnect loop), `BatteryScanner` (discovery).
  - `BatMonitor/Models/BatteryStore.swift`: saved batteries in UserDefaults (`batmonitor.batteries.v2`) and last real readings (`batmonitor.snapshots.v1`).
  - `BatMonitor/Models/DemoBatteryState.swift`: clearly labeled, BLE-free UI fixture for charging, discharging, idle, connecting, disconnected, and protection states. Select it from Home, then `...` on its detail screen.
  - `BatMonitor/Views/`: Home, Add battery sheet, Battery detail, rename and shared confirmation/info sheets. `Components/` holds theme, panels, cell bars, and the drawn fluid battery.
  - `BatMonitor/Localizable.xcstrings`: all strings, Spanish source plus English. The system language chooses the locale.
  - `BatMonitorTests/`: protocol tests, run with `xcodebuild test`.
- `legacy/expo/`: the previous React Native app, kept for reference only. It is not built or maintained. Its `ios/` and `node_modules/` are local leftovers and ignored.
- `com.jiabaida.little_elephant_3.2.063.xapk`: reference app. Gitignored, do not modify.

## Commands
- Tests: `cd ios && xcodebuild test -project BatMonitor.xcodeproj -scheme BatMonitor -destination 'platform=iOS Simulator,name=iPhone 17 Pro' CODE_SIGNING_ALLOWED=NO`
- Device build: `cd ios && xcodebuild -project BatMonitor.xcodeproj -scheme BatMonitor -configuration Release -destination 'id=<UDID>' build`. Install with `xcrun devicectl device install app --device <UDID> <BatMonitor.app>`, then `xcrun devicectl device process launch --device <UDID> com.andyechc.batmonitor`.
- Test device: iPhone 12 mini, UDID `00008101-001859600A43003A`.
- Signing: team `3PUM2552T9` is set in `ios/project.yml`. Bundle id `com.andyechc.batmonitor`.

## Protocol
Verified against the reference app's decompiled code and a real battery ("Andy 72x45", 24 cells).
- Service `0000ff00-0000-1000-8000-00805f9b34fb`. Notify on `0000ff01-...`, write on `0000ff02-...`.
- Read request: `DD A5 <cmd> 00 <checksum 2 bytes BE> 77`. Write request: `DD 5A <cmd> <len> <data> <checksum> 77`.
- Request checksum: low 16 bits of `~(cmd + len + sum(data)) + 1`.
- Response: `DD <cmd> <status> <len> <data> <checksum> 77`. Status `00` means OK. The response checksum covers only `len + sum(data)`, not cmd or status. Verified on a real capture (`DD 03 00 26 ...` gives `F9 2F`).
- Basic info (cmd `0x03`): voltage u16 /100 V at 0; current s16 /100 A at 2 (positive while charging, assumed); remaining and nominal capacity u16 /100 Ah at 4 and 6; cycles u16 at 8; date bitfield at 10 (`(y-2000)<<9 | m<<5 | d`); balance bytes at 12-15 (cells 0-7 use byte 13, 8-15 use 12, 16-23 use 15, 24-31 use 14); protections at 16-17 (bits 0-7 from byte 17, 8-12 from byte 16); firmware nibbles at 18; SOC % at 19; FET bits at 20 (bit 0 charge, bit 1 discharge); cell count at 21; NTC count at 22; NTC temperatures u16 at 23+2i, `(raw - 2731) / 10` °C.
- Cell voltages (cmd `0x04`): u16 per cell in mV.
- Switches (cmd `0xE1`): one byte, bit set to 1 turns that path off. Bit 0 charge, bit 1 discharge. Not yet confirmed on hardware.
- Control (cmd `0x0A`): `07 00` auto balance, `04 00` clear protections. Not yet confirmed on hardware.
- BMS Bluetooth name (cmd `0x07`): write `FF AA 07 <len> <utf8 name> <checksum 1 byte>`, where checksum is `(sum(name bytes) + len + 7) & 0xFF`. Ack is `FF AA 07 <status>`, and `00` means stored. Format taken from the reference app. NOT confirmed: the scan still showed the old name after a rename on hardware, so the write or the advertised name is still unverified.
- Not implemented on purpose: reset capacity, calibrations, password-protected BMS, history, OTA.

## Connection notes (from hardware testing)
- Wait for the notify subscription to finish before the first command. The first response is lost otherwise. The Swift code waits for the real subscription callback and then 1 s more.
- Connect timeout is 20 s. A stalled connect is cancelled and retried.
- iOS reports "was disconnected" now and then. The monitor reconnects after 4 s. Keep the phone close to the battery.
- Wait for a settled Bluetooth state (not `unknown` or `resetting`) before scanning or connecting.

## Design
- Dark theme, colors in `Components/Theme.swift`. Icons are SF Symbols. Navigation and sheets use system styling, which gets Liquid Glass on iOS 26.
- Navigation bars use native ultra-thin material; cards use distinct filled surfaces without outlines. BatMonitor green is the single brand accent, amber marks discharge/warnings, red marks errors/destructive actions, and neutral tones cover idle/secondary data.
- Home uses cached real readings with an explicit last-reading time. The demo battery is labeled as simulated and never starts BLE or writes to a real BMS.
- Detail screen has bottom tabs (summary, cells, status, control), a code-drawn animated battery, and a test-state selector for demo only.
- Add-battery uses the shared `BMSLoader`, a native search field matching name/Bluetooth UUID/RSSI, and ascending numeric RSSI order. Battery connection uses one dashboard skeleton and centered status text, not stacked spinners.
- The battery liquid animation follows current direction while charging or discharging, pauses at 100%, and respects Reduce Motion.
- The rename writes the name to the BMS first and saves it locally only after the BMS acknowledges.

## Status
- Protocol and demo fixture tests: 17 passing.
- Release build installed on the test iPhone and launches.
- Still to confirm on hardware: switches, balance, clear protections (their responses), and the BMS name write (the scan still showed the old name after a rename).
