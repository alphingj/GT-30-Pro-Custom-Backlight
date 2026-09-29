# GT30 Pro LED protocol notes

Reverse-engineered from `tranlightservice.jar`
(`com.transsion.display.tranlight`, from `/system_ext/framework` on X6873,
Android 16) plus live `service call` probes. All scene bytes below are
**decimal**.

## HAL service

- Service: `vendor.hardware.tranled.ITranLed/default` (AIDL v1).
- Binary `/vendor/bin/hw/vendor.hardware.tranled-service` runs as `system`,
  not pullable over ADB on production builds.
- Transaction 1 = `setLightingEffect(int[8]) -> boolean`. No interface token
  required (adding one fails). Request data is the raw 8 ints prefixed with
  their length (`i32 8 i32 v0 …` — the stub reads them via `createIntArray`).
- Transaction 2 = `getLightingEffect(int[8]) -> (bool, int[8])`. Returns
  firmware state in node layout. `set` returns true for any scene byte, so a
  `true` result does **not** mean the scene is implemented — only eyes do.

## Frame layout (raw/direct shape, `[0]`-type 0)

```text
[0, 1, 0, scene, variant, R, G, B]
```

- `[0]=0, [1]=1, [2]=0` — header (matches the `tran_led_cmd` sysfs format).
- `[3]` — firmware scene byte (decimal, see table).
- `[4]` — variant/sub-scene, always 0–3 in every system preset and live
  state. It is **not** a 0–255 brightness slider; out-of-range values fall
  back to dim output. Single-variant scenes use 0.
- `[5..7]` — color bytes. Only scene 1 renders them (dim custom glow; the
  system charging frame is `[0,1,0,1,0,184,168,185]` = default charge color
  `0xB8A8B9`). Preset scenes use built-in colors, bytes are 0.

Other shapes exist for other models/families and must not be mixed in:
`[1,1,effect,…,255,…]` (Camon rich effects), `[2,1,…]` (Note/Awinic),
`[3,hwId,effect,loop,bright,…]` (`TranLedHardwareManager` commands, where
`[4]` is overwritten with stepless brightness 0–255 — a different layer).

## Controller-ID → firmware remap (TranLedGt30Pro.setFlash)

The system never sends controller IDs raw; `setFlash` remaps first
(inputs decimal): `1→01, 2→02, 3→03, 15→04, 26→21, 27→22, 73→05, 76→03`,
`29→20, 30→51, 31→52, 32→53, 33→54, 34→61, 35..37→62/00..02,
38..40→63/.., 41..44→64/.., 45..47→65/.., 48→80, 49→81, 50→82, 51→83,
52→91, 53→92, 54→93, 55→11, 56→10, 57→12` (volume-gated), `0→00`,
`74/75` = PDLC electric-color branch (inactive on `nvcolor=X833`).
Anything else is a no-op on the system path. Older maps that sent e.g.
hex `0x22` (= 34, a controller ID for game-exit, not firmware 22) hit
unknown firmware scenes → dim fallback. This app sends post-remap firmware
bytes directly, bypassing `setFlash`.

## Useful constants (SceneConstants)

`ENTER_GAME=33, EXIT_GAME=34, NOTIFICATION_WAKE_UP=1, PUBG_FLASH_800=4,
PUBG_FLASH_801=55, LED_CAMERA_BEGIN_RECORDING=101,
SCENE_STEPLESS_BRIGHTNESS_{MIN,MAX} = 0/255`,
preview IDs 1000+ (charging 1011-1014, notification 1021, music 1081, …),
notification styles 3001+, `tran_led_brightness_setting` (null = default 0).

## App control path (why this shape)

- Sysfs writes (`tran_led_cmd`, `tran_pdlc_cmd`) are SELinux-denied even for
  `shell` (`sysfs_tran_led_file`), despite 0666 DAC perms. Nodes are
  world-readable: `tran_led_cmd` reads back a small status int.
- In-app `ServiceManager.getService` returns null for the tranled HAL, and
  in-app `Settings.Global` writes throw `SecurityException`.
- Shizuku user services crash on start on this MediaTek device
  (starter NPE in `LoadedApk.makeApplicationInner`, Shizuku #1198, server
  13.6.0 = latest). So the app uses the stable `newProcess` transaction
  (code 7) with generated `IShizukuService`/`IRemoteProcess` stubs and runs
  `/system/bin/service call …`, parsing `Result: Parcel(…)`. The remote
  process runs as `shell` — the same identity the manual probes used.
