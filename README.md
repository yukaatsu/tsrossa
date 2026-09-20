<p align="center">
  <img src="tsrossa.png" alt="tsrossa Banner" width="100%"/>
</p>

<h1 align="center">tsrossa — Audiophile Music Player</h1>

<p align="center">
  <strong>Direct Kernel-Level USB DAC (UAC1/UAC2) Bit-Perfect Music Player for Android with Native C++ Engine</strong>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
  <a href="release/app-release.apk"><img src="https://img.shields.io/badge/Release-v1.2.0--beta2-orange.svg" alt="Release"></a>
  <a href="#"><img src="https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg" alt="Platform"></a>
  <a href="#"><img src="https://img.shields.io/badge/Formats-FLAC%20%7C%20WAV-blueviolet.svg" alt="Supported Formats"></a>
  <a href="#"><img src="https://img.shields.io/badge/Audio-Bit--Perfect%20UAC2-success.svg" alt="Bit-Perfect"></a>
  <a href="https://bagibagi.co/Yukaatsu"><img src="https://img.shields.io/badge/Buy%20me%20a%20coffee-Donate-FFDD00.svg?logo=buy-me-a-coffee&logoColor=black" alt="Buy me a coffee"></a>
</p>

---

## ⚡ Overview

**tsrossa** is a lightweight, open-source Bit-Perfect audiophile music player for Android. It bypasses the standard Android OS mixer (`AudioFlinger`) and communicates directly with external USB DACs at the kernel level using a custom native C++17 `libusb-1.0` asynchronous isochronous engine.

Playback is streamed with zero resampling, zero DSP alterations, and exact clock synchronization for pristine audio fidelity.

---

## 📸 Interface Preview

<table align="center">
  <tr>
    <td align="center" width="33%">
      <img src="screenshots/01_now_playing.png" alt="Now Playing" width="100%"/>
      <br/><b>Now Playing</b><br/>
      <sub>Pac-Man seek bar, dynamic album art palette, hardware volume dial</sub>
    </td>
    <td align="center" width="33%">
      <img src="screenshots/02_library.png" alt="Library Browser" width="100%"/>
      <br/><b>Library Browser</b><br/>
      <sub>Fast file navigation with sleek terminal search and codec tags</sub>
    </td>
    <td align="center" width="33%">
      <img src="screenshots/03_system_logs.png" alt="Live Diagnostics" width="100%"/>
      <br/><b>Live Diagnostics</b><br/>
      <sub>Real-time DAC telemetry, clock locking, and 1-tap report export</sub>
    </td>
  </tr>
</table>

---

## 🚀 Key Features

- **100% AudioFlinger Bypass**: Direct USB Host access prevents Android's forced 48kHz resampling and system audio degradation.
- **Lossless & Uncompressed Playback**: Native decoding of **FLAC** (`.flac`) and **WAV** (`.wav`, `.wave`) from 16-bit/44.1kHz up to 32-bit/384kHz (including IEEE 32-bit Float).
- **Hardware Volume Control**: Native logarithmic gain adjustment via USB Feature Units (indicated by a Gold Status Dot for true unity gain).
- **Retro-Cyberpunk HUD**: Terminal monospaced interface with an interactive Pac-Man animated seek bar, dark AMOLED theme, and album art accent lighting.
- **True Gapless Playback**: Native pre-buffering delivers seamless track handovers without silence or clicks.
- **In-App Updater & Hotplug Safety**: Built-in GitHub release updater and graceful USB disconnection handling to prevent crashes.

---

## 💡 Obtaining FLAC Music (SpotiFLAC)

Looking for lossless FLAC tracks to test bit-perfect playback with your DAC setup? You can obtain high-quality audio files using **SpotiFLAC**:

- 🌐 **Official Website**: [spotiflac.com](https://spotiflac.com)
- 📱 **SpotiFLAC Mobile GitHub**: [spotiflacapp/SpotiFLAC-Mobile](https://github.com/spotiflacapp/SpotiFLAC-Mobile)

---

## 📥 Installation

1. Download the latest signed APK:
   - 📦 **Direct Download**: [`release/app-release.apk`](release/app-release.apk)
   - 🚀 **GitHub Releases**: [tsrossa Releases](https://github.com/yukaatsu/tsrossa/releases)
2. Install the APK on your Android device (Android 8.0+).
3. Connect your USB DAC via OTG cable and tap **OK** when prompted for USB permissions.

---

## 📱 Compatibility

- **Android Versions**: Android 8.0 (Oreo, API 26) to Android 14+ (API 34).
- **USB DAC Support**: Standard USB Audio Class 1.0 (UAC1) and 2.0 (UAC2) devices.
- **Tested DACs**: ESS Sabre, AKM, Cirrus Logic (CS43131/CS43198), Realtek, Conexant, Savitech, and more.

---

## 🛠️ Building From Source

```bash
git clone https://github.com/yukaatsu/tsrossa.git
cd tsrossa
./gradlew assembleRelease
```
Compiled APK: `app/build/outputs/apk/release/app-release.apk`

---

## ☕ Support

If you enjoy **tsrossa**, you can support its ongoing development:
👉 **[Buy me a coffee on BagiBagi](https://bagibagi.co/Yukaatsu)**

---

## 📄 License

Licensed under the [Apache License 2.0](LICENSE).
