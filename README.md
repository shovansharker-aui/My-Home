# Mijia4K

An unofficial, modern companion app for the Xiaomi Mijia 4K action camera (`YDXJ01FM`), built because Xiaomi stopped updating the official app years ago. Talks directly to the camera over its own WiFi hotspot — no cloud, no Mi account.

## Status

Early bring-up. The camera's shutter/record/mode control protocol is implemented from public reverse-engineering research (see [Protocol notes](#protocol-notes)) but not yet verified against real hardware. The file-listing/download protocol is genuinely unknown — see the **Diagnostics** screen below.

## Planned features

- Live preview + shutter/record/mode control
- Browse and download photos/videos from the SD card to your phone
- Battery/storage status
- A Diagnostics screen to probe the camera's local network and help finish reverse-engineering the file-access protocol

## How it works

Connect your phone to the camera's WiFi hotspot (SSID `MiCam_<serial suffix>`, default password `1234567890`). The camera answers at `192.168.42.1` on:

| Port | Purpose |
|------|---------|
| 80   | HTTP file server (Cherokee) — exact file paths TBD, see Diagnostics |
| 554  | RTSP live preview (`rtsp://192.168.42.1/live`) |
| 7878 | JSON control socket (shutter, record, mode, battery, storage) |

## Protocol notes

This camera uses an Ambarella A12S SoC, the same firmware lineage as several other "Ambarella action cam" white-label devices (SJCAM SJ8 Pro, Thieye T5e, etc.) whose WiFi control protocol has been documented by the community:

- Hardware/hotspot details: [Theliel/Xiaomi-Mijia-4K](https://github.com/Theliel/Xiaomi-Mijia-4K)
- Ambarella JSON control-socket protocol (port 7878): [rigacci.org SJCAM SJ8 Pro API notes](https://www.rigacci.org/wiki/doku.php/doc/appunti/hardware/sjcam-8pro-ambarella-wifi-api), [RigacciOrg/ambarella-api-pytools](https://github.com/RigacciOrg/ambarella-api-pytools)

The control-socket protocol is implemented in [`AmbaSocketClient`](app/src/main/kotlin/com/mijia4k/app/net/AmbaSocketClient.kt). The file-access HTTP paths are **not documented anywhere publicly** for this specific camera, so [`NetworkDiagnostics`](app/src/main/kotlin/com/mijia4k/app/net/NetworkDiagnostics.kt) + the in-app Diagnostics screen exist to find them empirically.

## Building

```
./gradlew assembleDebug
```

Or just push to `main` / open a PR — GitHub Actions builds a debug APK on every push (see [Actions](../../actions)) and uploads it as a workflow artifact. Tag a commit `vX.Y.Z` to also cut a GitHub Release with a signed installable APK.

## Disclaimer

Unofficial, community project. Not affiliated with Xiaomi.
