<div align="center">

# 📶 Signal Monitor

**Live cellular & WiFi signal dashboard for Android**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-31%2B-brightgreen.svg)](https://android-arsenal.com/api?level=31)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF.svg)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](https://android.com)

Track your signal strength in real time — cellular and WiFi — with a live scrolling graph.

</div>

---

## Features

| | |
|---|---|
| 📱 **Cellular** | RSRP, RSRQ, SINR for LTE/5G · Network type · Band |
| 📡 **WiFi** | RSSI · Channel & band · Link speed (Rx/Tx) · Standard (Wi-Fi 4/5/6/7) · BSSID |
| 📈 **Live graph** | Color-coded scrolling chart (120-point window) — red → yellow → green |
| 💾 **CSV export** | Export timestamped readings with full metadata |
| 🔄 **Multi-SIM** | Per-SIM signal tracking |
| ⚡ **Sampling rate** | Configurable interval: 500 ms to 5 s |

## Signal color coding

| Color | Cellular (dBm) | WiFi (dBm) |
|-------|---------------|------------|
| 🔴 Poor | < −110 | < −80 |
| 🟡 Fair | −110 to −90 | −80 to −67 |
| 🟢 Good | > −90 | > −67 |

## Install

### Build from source

```bash
git clone https://github.com/lpapa1977/SignalMonitor.git
cd SignalMonitor
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Permissions required

- `READ_PHONE_STATE` — cellular signal and SIM info
- `ACCESS_FINE_LOCATION` — required by Android to read cell tower details

## No external dependencies

Pure Android SDK + Kotlin stdlib. No third-party libraries.

## Support

[![Donate via PayPal](https://img.shields.io/badge/Donate-PayPal-0070ba?style=for-the-badge&logo=paypal&logoColor=white)](https://paypal.me/lpapa1977)

## License

[MIT](LICENSE) © Leonardo Papa
