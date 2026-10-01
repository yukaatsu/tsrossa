# Contributing to tsrossa

Want to help out with tsrossa? That's awesome.

We're trying to build a solid, bit-perfect music player that actually talks directly to USB DACs on Android without the OS audio stack messing with the stream. Since Android devices and USB DACs behave wildly differently depending on the OEM kernel and DAC firmware, real-world testing and code contributions help a ton.

---

## What We Need Most Right Now

1. **Hardware testing & DAC compatibility:**
   - Plug in your dongles or desktop DACs.
   - Let us know if playback works, if volume hardware control works, or if it breaks.
   - Mention your phone model, Android version, and DAC name.

2. **Audio pipeline & stability:**
   - Bug reports for audio pops, stuttering, sample rate sync issues, or app crashes.
   - Improvements to the C++ audio engine (`libusb`, threading, buffer management).

3. **UI/UX polish:**
   - Jetpack Compose tweaks, responsiveness, or library browsing improvements.

---

## Local Setup

Grab the repo and build it locally:

```bash
git clone https://github.com/yukaatsu/tsrossa.git
cd tsrossa
./gradlew assembleDebug
```

Requirements:
- Android Studio / Android SDK (compileSdk 34)
- Android NDK & CMake
- JDK 17

---

## Reporting Issues

If something sounds wrong or crashes, please open an issue and mention:
- **Phone:** e.g. Samsung Galaxy S23 (OneUI 6 / Android 14)
- **DAC:** e.g. Moondrop Dawn Pro, Fiio KA3, Snowsky Melody
- **Track info:** Format, sample rate, bit depth (e.g. FLAC 24-bit / 96 kHz)
- **What happened:** No sound, stuttering, noise, crash, etc.
- **Logs (if you have ADB handy):**
  ```bash
  adb logcat -s tsrossa:V native-lib:V UsbExclusive:V
  ```

---

## Submitting Changes

1. Fork the repo and make your changes on a separate branch.
2. Keep PRs focused. If you're fixing two unrelated things, use two PRs.
3. Keep commit messages straightforward and descriptive.
4. Make sure it actually builds before opening a PR:
   ```bash
   ./gradlew assembleDebug
   ```
5. Open your pull request against `main`.

---

## Notes on Code

- **C++:** We use C++17. The USB streaming thread runs in real-time priority, so keep callbacks fast and avoid heap allocations inside time-critical audio loops.
- **Kotlin:** Standard Jetpack Compose. Keep UI state predictable and don't block the main thread.
