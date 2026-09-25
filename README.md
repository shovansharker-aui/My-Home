# My Home

A personal smart-home shell for Android, built from **modules** — small self-contained apps that live inside one main app, the way Google Home and Xiaomi Home host their devices. Everything runs locally: no cloud, no account.

The first module is the **Mijia 4K Camera** (Xiaomi `YDXJ01FM`), a full replacement for the official app that Xiaomi stopped updating years ago.

## Modules

| Module | What it does |
|--------|--------------|
| Mijia 4K Camera | Live view with 10x digital zoom, shutter/record, all nine shooting modes with icon-based parameter chips, album with multi-select download/delete, camera settings, battery and free-space readouts, landscape layout |

### Adding a module

A module implements [`HomeModule`](app/src/main/kotlin/com/myhome/app/home/HomeModule.kt): an id, a title and one-line description for its tile, an icon, an entry route, and a `register` function that adds its own navigation graph. List it in `ModuleRegistry` and it appears on the home screen. See [`MijiaCameraModule`](app/src/main/kotlin/com/myhome/app/modules/mijia/MijiaCameraModule.kt) for a complete example.

## How the camera module talks to the camera

Join the camera's hotspot (SSID `MiCam_<serial suffix>`). It answers at `192.168.42.1` on:

| Port | Purpose |
|------|---------|
| 80   | HTTP file server (Cherokee): directory listings and downloads under `/DCIM/100MEDIA/` |
| 554  | RTSP live preview (`rtsp://192.168.42.1/live`); has no video track unless the camera is idle in viewfinder mode |
| 7878 | JSON control socket: shutter, record, mode, settings, battery, storage, file operations |

Confirmed against the real camera:

- Every reply carries `rval`; non-zero means the camera refused.
- Setting option lists come from `msg_id 9` with the setting name in `param`, and include a `permission` of `settable` or `readonly` for the current mode.
- Battery is `msg_id 13` → `{"param":"adapter"|"battery","level":"87"}`.
- The SD card is mounted at `/tmp/SD0`; file delete is `msg_id 1281` with that prefix, and deleting a photo also removes its RAW sibling. `1282`/`1283`/`1284` are ls/cd/pwd on the camera's own filesystem.
- `msg_id 1794` restarts the camera's Wi-Fi. Sending a start-viewfinder while it is recording makes the control port stop answering.
- `.THM` files are low-resolution proxy videos, not thumbnails.

## Protocol background

This camera uses an Ambarella A12S SoC, the same firmware lineage as several other white-label action cams (SJCAM SJ8 Pro, Thieye T5e, etc.) whose control protocol the community has documented:

- Hardware/hotspot details: [Theliel/Xiaomi-Mijia-4K](https://github.com/Theliel/Xiaomi-Mijia-4K)
- Ambarella JSON control-socket protocol (port 7878): [rigacci.org SJCAM SJ8 Pro API notes](https://www.rigacci.org/wiki/doku.php/doc/appunti/hardware/sjcam-8pro-ambarella-wifi-api), [RigacciOrg/ambarella-api-pytools](https://github.com/RigacciOrg/ambarella-api-pytools)

## Building

```
./gradlew assembleDebug
```

Or just push to `main` / open a PR — GitHub Actions builds a debug APK on every push (see [Actions](../../actions)) and uploads it as a workflow artifact. Tag a commit `vX.Y.Z` to also cut a GitHub Release with an installable APK.

## Disclaimer

Unofficial, community project. Not affiliated with Xiaomi.
