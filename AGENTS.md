# AGENTS.md

BatMonitor: an Expo (React Native + TypeScript) app that reads XiaoXiang-compatible BMS batteries over BLE. Targets Android and iOS. The user writes in Spanish; reply in Spanish.

## Product constraints
- Phase 1 is functional parity with the reference XiaoXiang app. Don't add features beyond that until the user asks.
- UI should look modern, in the style of EcoFlow and Silence apps.
- No accounts or login. Users add batteries directly. Don't port the reference app's cloud calls (`cloud.jiabaida.com`, Netty on port 10241, HTTP API).

## Expo rules
- Expo changes often and training data is outdated. Before writing code that touches Expo, EAS, or React Native APIs, check the docs for the major version in `package.json` (`https://docs.expo.dev/versions/v<major>.0.0/`) or `https://docs.expo.dev/llms.txt`.
- Add packages with `npx expo install <pkg>` so versions match the SDK. Don't use plain `npm install` for Expo-managed packages.
- Routes live in `src/app/` (Expo Router). `_layout.tsx` defines navigators. Keep everything else in `src/`. Import with the `@/` alias, which maps to `src/`.
- `ios/` and `android/` are generated and gitignored. Never edit them. Configure native behavior in `app.json` plugins.
- `react-native-ble-plx` has native code, so Expo Go won't work. Run a development build (`npx expo run:android` or `npx expo run:ios`).
- `npx expo run:ios` doesn't regenerate an existing `ios/` folder. After changing `app.json` plugins or adding a native-config dependency, run `npx expo prebuild --platform ios --no-install` first. Otherwise the change never reaches `Info.plist`.
- Xcode 26/27 device builds fail out of the box with "Clang dependency scanning failure: EXConstants.modulemap not found" / "could not build module '_DarwinFoundation1'". The fix is already applied via `plugins/with-xcode27-module-workaround.js` (registered in `app.json`): it sets `SWIFT_ENABLE_EXPLICIT_MODULES=NO` and `CLANG_ENABLE_EXPLICIT_MODULES=NO` on every configuration of both `BatMonitor.xcodeproj` and the Pods project. The settings must live in the .xcodeproj files; xcconfig-only values get overridden. Any build for a device or EAS should keep this plugin; verify with `xcodebuild -workspace ios/BatMonitor.xcworkspace -scheme BatMonitor -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build`.
- Icons come from `phosphor-react-native`, with mostly `duotone` weight for personality. Pass icon components (not names) as props, typed as `IconComponent` from `src/components/icons.ts`. Phosphor has no `Signal` or `BluetoothSearching`. Use `Broadcast` for signal strength.
- The app logo is a bat whose body is a half-charged battery. Sources are `assets/logo/bat.svg` (transparent) and `assets/logo/icon.svg` (full, with background). The raster files in `assets/` (`icon.png`, `android-icon-*.png`, `splash-icon.png`, `favicon.png`) are rendered from those SVGs with `@resvg/resvg-js`. The render script lives outside the repo. Re-render after editing the SVGs, then run `npx expo prebuild --platform ios --no-install` so the iOS icon is regenerated.

## Expo agent tooling
- Official Expo skills are installed in `.agents/skills/` (locked in `skills-lock.json`, restore with `npx skills experimental_install`). For Expo work, start with the `expo-overview` skill, which routes to the specific `expo-*` / `eas-*` skill.
- The Expo MCP server is configured in `opencode.json` as a remote server (`https://mcp.expo.dev/mcp`). It uses OAuth with an Expo account. If it shows as awaiting sign-in, the user signs in through `/mcps`.
- Local MCP capabilities (screenshots, taps, DevTools, router sitemap) need the `expo-mcp` dev dependency and the dev server started with `EXPO_UNSTABLE_MCP_SERVER=1 npx expo start`. They work on macOS simulators only, and the MCP connection must be restarted after each dev-server start or stop.

## Commands
- Before declaring work done, run in this order: `npm run lint`, `npm run typecheck`, `npm test`.
- `npm test` runs Jest with the `jest-expo` preset. Protocol tests are in `src/lib/bms/protocol.test.ts`.
- Bundle check: `npx expo export --platform android` (and `ios`). It catches import and config errors that typecheck misses.
- `npx expo-doctor` passed 20/20 at setup.

## Architecture
- `src/lib/bms/protocol.ts`: framing, checksum, and parsers. Pure TypeScript with no native dependencies, so it's the place to test first.
- `src/lib/bms/connection.ts`: `react-native-ble-plx` wrapper. The `BleManager` is created lazily. Scans filter by service `0000ff00`. `BmsSession` reads basic info (cmd `0x03`) and cell voltages (cmd `0x04`) in sequence, with a 3 s timeout per request.
- `src/lib/bms/useBmsMonitor.ts`: polls every 2 s and reconnects after 4 s on error.
- `src/lib/devices.ts`: saved batteries in AsyncStorage under `batmonitor.batteries.v1`.
- `src/app/`: `index` (battery list), `add-device` (scan modal), `device/[id]` (live dashboard).
- `src/theme.ts` holds colors and radii. Shared components are in `src/components/`.

## BMS protocol
Verified against the reference app's decompiled code (`BMSBaseInfoCMDEntity`, `BMSBatteryVoltageCMDEntity`, `BMSCommandEntity`, `BluetoothUtil`).
- Service `0000ff00-0000-1000-8000-00805f9b34fb`. Read/notify on `0000ff01-...`, write on `0000ff02-...`.
- Request: `DD A5 <cmd> <len> <data> <checksum 2 bytes BE> 77`.
- Response: `DD <cmd> <status> <len> <data> <checksum> 77`. Status `00` means OK. Request checksum is the low 16 bits of `~(cmd + len + sum(data)) + 1`. Response checksum leaves out cmd and status: `~(len + sum(data)) + 1`. Verified on a real capture (`DD 03 00 26 ...` → `F9 2F`). Requests use `A5` for read and `5A` for write.
- Basic info (cmd `0x03`) offsets:
  - Voltage u16 /100 V at 0.
  - Current s16 /100 A at 2. Positive means charging. This is assumed from the common JBD convention and not yet confirmed on hardware.
  - Remaining and nominal capacity u16 /100 Ah at 4 and 6.
  - Cycles u16 at 8.
  - Date bitfield at 10: `(y-2000)<<9 | m<<5 | d`.
  - Balance bytes at 12-15. Cells 0-7 use byte 13, cells 8-15 use byte 12, cells 16-23 use byte 15, cells 24-31 use byte 14.
  - Protection bytes at 16-17. Bits 0-7 come from byte 17, bits 8-12 from byte 16.
  - Firmware nibbles at 18. SOC % at 19. FET bits at 20: bit 0 charge, bit 1 discharge.
  - Cell count at 21. NTC count at 22. NTC temperatures are u16 at 23+2i, in `(raw - 2731) / 10` °C.
- Cell voltages (cmd `0x04`): one u16 per cell in mV.
- Not implemented from the reference app: password-protected BMS, settings and calibration, charge history, OTA. Add these only when asked.

## Reference app and REA
- The only original file is `com.jiabaida.little_elephant_3.2.063.xapk`. It's gitignored and must not be modified.
- Decompile with `/opt/homebrew/bin/jadx -d <out> --no-res <apk>`. It exits 3 on partial errors but still writes sources.
- Extract the xapk into a temp dir, not into the repo: `/private/var/folders/3b/blsm2x8x4sz4ntxqxtkjs1t00000gn/T/opencode/`.
- REA's Android tools (`inspect_android_*`, `search_android_classes`) need `REA_JADX_MCP_JAR` (path to the jadx-headless-mcp 0.7.1 JAR). It's not installed.
- `decode_android_resources` needs `REA_APKTOOL_COMMAND`. apktool is not installed.
- macOS has no `timeout` command.

## Status
- Typecheck, lint, tests, and Android/iOS bundling pass.
- The BLE flow works on a real BMS ("Andy 72x45", 24 cells, iPhone 12 mini Release build). It reads basic info and cell voltages every 2 s. The link drops now and then (iOS reports "was disconnected") and the app reconnects after 4 s. Keep the phone close to the battery.
- Connection notes: wait about 1 s after subscribing to FF01 before the first command, or the first response is lost. Connect timeout is 20 s.
