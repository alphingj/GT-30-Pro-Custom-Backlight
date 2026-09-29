# GT30 Pro Custom Backlight

Custom rear-LED ("Mechanical Light Waves") controller for the **Infinix GT 30 Pro (X6873)**.
A normal app cannot touch this LED (SELinux denies sysfs writes even to `shell`,
and in-app Settings writes throw). This app drives the vendor HAL through
**Shizuku**, no root required.

## Requirements

- Infinix GT 30 Pro (X6873). Other Transsion models use different scene tables.
- [Shizuku](https://shizuku.rikka.app/) installed and running (wireless ADB),
  with permission granted to this app.
- To build: JDK 21 and the Gradle wrapper in `android/` (downloads its own
  distribution on first run). Android SDK with platform 35 + build-tools 34.

## Build & install

```sh
cd android
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`assembleRelease` also builds (unsigned APK, R8 minified).

## Usage

1. Start Shizuku on the phone, open the app, tap the Shizuku button to grant
   permission. Status shows `Connected` when ready.
2. **LED ON** — bright white alternate-side blink (firmware scene 64).
   **LED OFF** — scene 0.
3. Effect dropdown — user-verified firmware scenes (game, notification,
   charging, camera shutter, music, party, …). See [docs/SCENES.md](docs/SCENES.md).
4. Custom Color — RGB sliders + Apply. Drives scene 1, the firmware's dim
   custom-glow scene (subtle by design; see limitations).
5. Scene test row — send any raw scene byte 0–255 for experimenting.

## How it works

`ShizukuBridge` (single owner of Shizuku state) executes
`/system/bin/service call vendor.hardware.tranled.ITranLed/default …`
inside a Shizuku remote process (shell uid) via the server's `newProcess`
transaction, using the generated `moe.shizuku.server.*` AIDL stubs from
`dev.rikka.shizuku:aidl:13.1.5`, then parses `Result: Parcel(…)`.

Frame (8 ints): `[0, 1, 0, scene, variant, R, G, B]`.
Scene bytes are **decimal firmware scenes** (see below), NOT the hex-looking
values from older maps. Details: [docs/PROTOCOL.md](docs/PROTOCOL.md).

## Scene table (verified on-device)

| Scene | Look |
|------:|------|
| 0 | off |
| 1 | dim custom glow (uses RGB bytes; subtle by design) |
| 3 | flip-to-flash |
| 4 | green breathe + double-blink, then off (notification) |
| 5 | rotating red trail (~15x) |
| 9 | dim white |
| 10 | music mode, runs until OFF |
| 11 | music mode, single run |
| 20 | charging mode (1 round + 2 fast green blinks) |
| 22 | breathing green (game launch) |
| 51/52/53 | camera shutter timer, ~3s / ~5s / ~10s, red |
| 54 | alternating red trailer |
| 61 | XArena game-start flash |
| 62/63 | red fast blink ~5x, then full bottom-to-top sweep |
| 64/65 | bright white alternate-side blink |
| 80 | blue dripping fill + purple base, 2x, then 2x full breathing blink |
| 81/82 | dripping unlimited (no final blink) |
| 83 | the 80-blink, infinite until OFF |
| 91/92/93 | party breathe / meteor / rhythm |

Full notes: [docs/SCENES.md](docs/SCENES.md). Scenes 2 and 12 do nothing.
`probe/` holds the original ADB shell probe harness.

## Project layout

```text
android/            Gradle project (app module, wrapper 8.9, AGP 8.7.3)
  app/src/main/java/com/gj/gt30light/
    MainActivity.java / LedViewModel.java   UI + state
    ShizukuBridge.java                      Shizuku state machine + HAL calls
    ShizukuShell.java                       newProcess exec + Parcel parser
    LedProtocol.java                        frame builder + scene table
probe/              ADB shell probes (pre-Shizuku research)
docs/               PROTOCOL.md (RE notes), SCENES.md (visual table)
```

## Known limitations

- No solid-bright scene exists in firmware; the brightest white (64/65) blinks.
- Custom RGB only affects scene 1 (dim glow). Preset scenes use built-in colors.
- The PDLC backdrop layer (`tran_pdlc_cmd`) is not driven by this app.
- Shizuku **user services** do not start on this MediaTek device
  ([Shizuku #1198](https://github.com/RikkaApps/Shizuku/issues/1198)); the app
  deliberately uses `newProcess` instead.
