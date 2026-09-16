<p align="center">
  <img src="tsrossa.png" alt="tsrossa Banner" width="100%"/>
</p>

<h1 align="center">tsrossa — Audiophile Music Player</h1>

<p align="center">
  <strong>Bit-Perfect Direct USB DAC (UAC1/UAC2) Music Player with Native C++ Engine for Android</strong>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
  <a href="#"><img src="https://img.shields.io/badge/Status-Release%20Candidate%20(RC)-orange.svg" alt="Status"></a>
  <a href="#"><img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%E2%80%9334)-green.svg" alt="Platform"></a>
  <a href="#"><img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-purple.svg" alt="Kotlin"></a>
  <a href="#"><img src="https://img.shields.io/badge/Audio%20Engine-C%2B%2B17%20%7C%20NDK%20%7C%20libusb-red.svg" alt="C++ Engine"></a>
  <a href="#"><img src="https://img.shields.io/badge/Audio-Bit--Perfect%20UAC2-success.svg" alt="Bit-Perfect"></a>
</p>

---

> [!WARNING]
> ### 🚧 Project Status: Release Candidate (RC)
> **tsrossa** is currently in the **Release Candidate (RC)** development phase. The core native USB streaming engine, UAC2 negotiations, and FLAC playback are fully functional. However, because USB DAC implementations vary widely across manufacturers and Android OEM USB controller behaviors differ, active field testing and feedback are underway.
> 
> Found an issue or incompatible DAC? Please open an issue in our [Issue Tracker](https://github.com) with your DAC model and device log!

---

## 📖 Overview

Standard Android audio routes music through the OS mixer (`AudioFlinger`), which frequently resamples all audio streams to a fixed 48kHz (or 44.1kHz), degrading high-resolution audio files.

**tsrossa** completely bypasses Android AudioFlinger. By utilizing the Android USB Host API and compiling `libusb-1.0` directly into a native C++17 layer, `tsrossa` communicates directly with your external USB Digital-to-Analog Converter (DAC) in asynchronous/adaptive isochronous mode, delivering authentic, unaltered **Bit-Perfect** audio straight to your ears.

---

## ✨ Key Features

### 🎧 Bit-Perfect Audio Engine
- **AudioFlinger Bypass**: Direct kernel-level USB communication bypassing Android's internal resampling and software mixers.
- **Native FLAC Decoding**: Integrated high-speed [`dr_flac`](app/src/main/cpp/dr_flac.h) decoder in C++ minimizing JNI overhead and memory copies.
- **UAC1 & UAC2 Protocol Support**: Full compliance with USB Audio Class 1.0 and 2.0 specifications with automated Alternate Setting negotiation.
- **Audiophile TPDF Dithering**: Inline fast Triangular Probability Density Function (TPDF) dithering for transparent bit-depth alignment without quantization distortion.
- **True Gapless Playback**: Native next-track pre-buffering (`prepareNextTrack`) ensures zero-silence, click-free track transitions.
- **Hybrid Volume Architecture**: Automatic discovery of USB Audio Feature Units for native hardware gain control, with seamless fallback to precision 32-bit software attenuation.

### 📊 Live Diagnostics & DAC Telemetry
- **Live I/O Monitor**: Real-time display of active negotiated sample rate, bit depth, USB endpoint, and buffer health.
- **DAC Capability Enumeration**: Queries and logs supported sample rates and bit depths directly from USB interface descriptors.
- **Error Recovery & Stall Detection**: Self-healing USB isochronous pipeline with automatic stall detection, buffer clear, and graceful DAC detachment handling.

### 🎨 Modern Minimalist Jetpack Compose UI
- **Terminal Audiophile Aesthetic**: Clean, dark-mode focused UI built entirely in Jetpack Compose and Material 3.
- **Dynamic Palette Extraction**: Automatically extracts dynamic color accents from album art using AndroidX Palette and Coil.
- **4 Dedicated Views**:
  - **Library**: Local storage audio scanner with instant FLAC filtering.
  - **Playlist**: Intuitive playlist manager with tactile haptic feedback.
  - **Track (Now Playing)**: High-resolution playback controls, waveform progress bar, and real-time audio spec badge (e.g., `24-bit / 96kHz`).
  - **Settings & Telemetry**: Deep diagnostics dashboard for DAC info, interface claims, and hardware volume toggles.

### 🛡️ Background Stability
- **Foreground Audio Service**: `AudioForegroundService` holds a protected `PARTIAL_WAKE_LOCK` ensuring the native isochronous audio thread is immune to aggressive Android Doze mode and task termination.

---

## 🏛️ Audio Architecture

```
Standard Android Audio (Resampled):
[ Audio File ] ──> [ MediaCodec ] ──> [ AudioFlinger (Resamples to 48kHz) ] ──> [ USB DAC ] (Compromised)

tsrossa Bit-Perfect Architecture (Bypass):
[ FLAC File ] 
      │
      ▼
[ dr_flac (C++17) ] ──> [ TPDF Dither / Buffer Queue ]
                               │
                               ▼
                    [ libusb-1.0 (Native NDK) ]
                               │ (Isochronous Direct Transfer)
                               ▼
                       [ External USB DAC ] (Pure Bit-Perfect Output)
```

---

## 📱 System Requirements

| Component | Requirement |
| :--- | :--- |
| **Operating System** | Android 8.0 (Oreo, API 26) up to Android 14+ (API 34) |
| **USB Support** | USB Host (OTG) enabled device |
| **Hardware** | External USB DAC / Dongle DAC supporting UAC1 or UAC2 |
| **File Format** | FLAC (Free Lossless Audio Codec) — 16/24/32-bit, 44.1kHz up to 384kHz |

---

## 🚀 Getting Started & Building from Source

### Prerequisites
- [Android Studio Iguana | Jellyfish or newer](https://developer.android.com/studio)
- Android SDK Platform 34
- Android NDK (version 25.x or newer recommended)
- CMake 3.22.1+
- Java JDK 17

### Build Steps

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/YOUR_USERNAME/tsrossa.git
   cd tsrossa
   ```

2. **Open in Android Studio**:
   - Open Android Studio and select **Open**.
   - Navigate to the cloned `tsrossa` directory.
   - Wait for Gradle and CMake synchronization to finish.

3. **Build Debug APK via CLI**:
   ```bash
   ./gradlew assembleDebug
   ```
   The resulting APK will be located at:
   `app/build/outputs/apk/debug/app-debug.apk`

---

## 🗺️ Release Candidate (RC) Roadmap

- [x] Native libusb UAC1/UAC2 isochronous streaming engine.
- [x] Native FLAC decoder integration (`dr_flac`).
- [x] Jetpack Compose UI with Dynamic Palette.
- [x] Hardware volume control and software fallback.
- [x] Live DAC diagnostics and error telemetry screen.
- [ ] Direct DSD (Direct Stream Digital) / DoP (DSD over PCM) playback.
- [ ] Additional lossless format decoders (WAV, ALAC, AIFF).
- [ ] 10-band Parametric Equalizer (PEQ) in C++ layer.
- [ ] Auto-reconnect profile presets for popular USB DAC chipsets (ESS Sabre, AKM, Cirrus Logic).

---

## 🤝 Contributing

Contributions are warmly welcomed! Since this project is in the **Release Candidate** stage, testing across various USB DAC dongles and DAC/Amps is especially helpful.

Please read our [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

---

## 📄 License

This project is licensed under the **Apache License 2.0** — see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Crafted with passion for audiophiles and open source enthusiasts.
</p>
