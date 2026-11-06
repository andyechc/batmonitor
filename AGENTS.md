# AGENTS.md

BatMonitor: a native app that reads XiaoXiang-compatible BMS batteries over BLE. iOS (SwiftUI) is complete; Android (Kotlin + Jetpack Compose, Material3) lives in `android/` as a strict port of the iOS design — same theme hexes, M PLUS 1p figures, Spanish default + English, bottom floating tabs, sheets, dialogs. The user writes in Spanish; reply in Spanish.

## Layout
- `ios/`: native iOS app. `project.yml` (XcodeGen) generates `BatMonitor.xcodeproj`. After changing `project.yml`, run `xcodegen generate` in `ios/`. The `.xcodeproj` is committed.
  - `BatMonitor/Protocol/BMSProtocol.swift`: pure Swift framing, checksums, command builders, and parsers. No Bluetooth dependency.
  - `BatMonitor/BLE/`: `BLECentral` (CoreBluetooth wrapper), `BatteryMonitor` (poll and reconnect loop), `BatteryScanner` (discovery).
  - `BatMonitor/Models/BatteryStore.swift`: saved batteries in UserDefaults (`batmonitor.batteries.v2`) and last real readings (`batmonitor.snapshots.v1`). Every saved battery is a real BMS; the retired demo entry (UUID `A73B1487-8B90-4A74-9512-273B9F100001`) is dropped and re-saved when the store loads.
  - `BatMonitor/Views/`: Home, Add battery sheet, Battery detail, rename and shared confirmation/info sheets. `Components/` holds theme, panels, cell bars, and the drawn fluid battery.
  - `BatMonitor/Localizable.xcstrings`: all strings, Spanish source plus English. The system language chooses the locale.
  - `BatMonitorTests/`: protocol and store tests, run with `xcodebuild test`. `BatMonitorUITests/` holds screenshot walkthroughs.
- `android/`: native Android app (Kotlin + Jetpack Compose, Material3, minSdk 26). Strict port of the iOS design. Build with `./gradlew :app:assembleDebug` (needs `ANDROID_HOME`; JDK 21 via `JAVA_HOME`). Tests: `./gradlew :app:testDebugUnitTest` (12 protocol + store + update-check). No demo mode anywhere: the retired demo UUID `A73B…0001` is dropped on load, same as iOS.
  - Update check: on every app open, `update/UpdateCheck.kt` GETs `landing/api/version.json` once per process; if `versionCode` exceeds the installed one, a modal shows the changelog with a Descargar button pointing at `uptodownUrl`. All URLs are server-driven from that JSON; the app only hardcodes the JSON URL (TODO in `UpdateCheck.kt` must match the landing deployment).
- `landing/`: static download site (plain HTML/CSS/JS, no build step) + update API at `api/version.json` + `DEPLOY.md`. Flow: Uptodown listing → landing → `apkUrl` of the GitHub release (asset must be named exactly `batmonitor.apk`). Bump `versionCode`/`versionName` in `android/app/build.gradle.kts` and in `version.json` together on every release.
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
- Dark/light theme that follows the system appearance (nothing forces a scheme). Every token in `Components/Theme.swift` is a dynamic color with a light and a dark hex; dark keeps the original design, light uses F2F2F5 canvas, FFFFFF cards, E7E7EC chips and text-safe accents (charge 066B49, discharge 8A5300, danger C21A12). Use `Color(.label)` (not `.primary`) for toolbar and menu item colors, since a `Menu` re-tints `.primary`. Icons are SF Symbols. Navigation and sheets use system styling, which gets Liquid Glass on iOS 26.
- Navigation bars use native ultra-thin material; cards use distinct filled surfaces without outlines. BatMonitor green is the single brand accent, amber marks discharge/warnings, red marks errors/destructive actions, and neutral tones cover idle/secondary data.
- Home uses cached real readings with an explicit last-reading time. There is no demo or simulated battery anywhere in the UI: the empty state only offers "Añadir batería".
- Detail screen has bottom tabs (summary, cells, status, control) and a code-drawn animated battery. The `...` menu holds rename, balance, clear protections and remove for every battery.
- Add-battery uses the shared `BMSLoader`, a native search field matching name/Bluetooth UUID/RSSI, and ascending numeric RSSI order. Battery connection uses one shimmering dashboard skeleton and centered status text, not stacked spinners; the shimmer is disabled by Reduce Motion.
- The battery liquid animation follows current direction while charging or discharging, pauses at 100%, and respects Reduce Motion.
- The rename writes the name to the BMS first and saves it locally only after the BMS acknowledges.

## Status
- Unit tests: 14 passing (12 protocol plus 2 `BatteryStore` migration/decoding). UI tests: 6 passing (`BatMonitorUITests`, uses the `UITEST_FAKE_BATTERY` in-memory hook so no BLE is needed).
- Release build installed on the test iPhone and launches.
- Still to confirm on hardware: switches, balance, clear protections (their responses), and the BMS name write (the scan still showed the old name after a rename).
