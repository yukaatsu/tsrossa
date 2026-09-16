# Contributing to tsrossa

Thank you for your interest in contributing to **tsrossa**! We are building an open-source, bit-perfect audiophile music player for Android, and community contributions are essential to making it stable and feature-rich.

---

## 🚧 Release Candidate (RC) Notice

Because the project is currently in the **Release Candidate** phase, testing on diverse hardware is our top priority. The most valuable contributions right now are:
1. **DAC Hardware Compatibility Reports**: Testing your specific USB DAC or dongle and reporting your experience (success or failure).
2. **Buffer and Latency Feedback**: Reporting any audio dropouts, pops, or click issues on specific Android OEM devices (Samsung, Xiaomi, Pixel, Sony, etc.).
3. **Bug Fixes**: Refinements in C++ NDK code, libusb lifecycle management, and Compose UI stability.

---

## 🛠️ Development Setup

1. Fork the repository on GitHub.
2. Clone your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/tsrossa.git
   cd tsrossa
   ```
3. Ensure you have the Android SDK (API 34), NDK, and CMake installed via the SDK Manager in Android Studio.
4. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 🐛 Reporting Bugs & Incompatibilities

When filing an issue, please include:
- **Phone Model & OS Version**: e.g., Google Pixel 7 (Android 14) or Samsung Galaxy S23 (OneUI 6).
- **USB DAC Model & Chipset**: e.g., Moondrop Dawn Pro (Dual CS43131), Fiio KA3 (ES9038Q2M), or Apple Dongle.
- **Audio File Details**: Format, sample rate, and bit depth (e.g., FLAC 24-bit / 96kHz).
- **Observed Behavior**: Description of what happened (silence, distortion, app crash, stall).
- **Logcat Output**: If possible, filter by tag `KewAudioEngine` or `UsbAudioController`:
  ```bash
  adb logcat -s KewAudioEngine:V UsbAudioController:V
  ```

---

## 🚀 Submitting a Pull Request (PR)

1. Create a descriptive branch for your feature or bug fix:
   ```bash
   git checkout -b fix/dac-stall-recovery
   ```
2. Write clean, readable code following standard Kotlin and C++ conventions.
3. Commit your changes with clear commit messages following Conventional Commits (e.g., `feat: ...`, `fix: ...`, `docs: ...`).
4. Push to your fork:
   ```bash
   git push origin fix/dac-stall-recovery
   ```
5. Open a Pull Request against the `main` branch.

---

## 📜 Code Style Guidelines

- **Kotlin**: Follow official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) and Android Compose best practices.
- **C++**: Follow C++17 modern idioms. Keep memory management RAII-compliant and thread-safe when dealing with JNI and libusb isochronous transfers.
