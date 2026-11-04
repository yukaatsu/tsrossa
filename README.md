# tsrossa for Android

Bit-perfect, direct-to-DAC audiophile music player for Android 8.0+ (API 26 to API 35+).

> 💡 **Looking for the macOS version?** Switch to the [`macos` branch](https://github.com/yukaatsu/tsrossa/tree/macos).  
> To clone only the macOS app:  
> `git clone -b macos --single-branch https://github.com/yukaatsu/tsrossa.git tsrossa-mac`

---

tsrossa bypasses Android's native audio subsystem (`AudioTrack`, `AudioFlinger`, and vendor audio HAL layers) by streaming linear PCM audio data directly to external USB DACs using asynchronous USB Isochronous transfers through Linux `usbfs` kernel endpoints.

---

## Key Features

- **Direct Kernel USB Audio**: Complete bypass of Android's mandatory 48 kHz resampler (SRC), audio effects, and software mixer.
- **Bit-Perfect Pass-Through**: Supports FLAC and WAV formats from 16-bit / 44.1 kHz up to 32-bit / 384 kHz.
- **Hardware Clock Switching**: Direct configuration of physical DAC crystal oscillators via USB Audio Class (UAC1 / UAC2) `CS_SAMPLING_FREQ_CONTROL`.
- **POSIX `mlock()` Memory Pinning**: Pre-decodes audio into RAM and locks memory pages to prevent system page-outs and buffer stutter.
- **Hardware Feature Unit Volume**: Direct DAC volume control via USB requests with 0 dBFS True Unity Gain at 100%.
- **Material 3 UI**: Built with Jetpack Compose featuring dynamic album art color palettes, 11-criteria sorting, fast scroller, and synchronized `.lrc` lyrics.
- **Hardware Telemetry**: Real-time DAC status monitoring, endpoint audits, and one-tap diagnostic log export.
- **DAP Mode (Screen Pinning)**: Native lock task support turning your Android device into a dedicated Digital Audio Player.

---

## Architecture & Directory Layout

```text
tsrossa/ (branch: android)
├── app/
│   ├── build.gradle.kts            # Application Gradle configuration (compileSdk 34)
│   ├── proguard-rules.pro          # ProGuard rules for release builds
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # Permissions, USB intent filters, foreground service
│       │   ├── cpp/                # Native C++17 Audio Engine
│       │   │   ├── CMakeLists.txt         # CMake build configuration
│       │   │   ├── AudioCommon.h          # Shared audio structures & ring buffers
│       │   │   ├── AudioDecoder.h         # Decoder interfaces
│       │   │   ├── AudioEngineState.cpp/h # Engine state management
│       │   │   ├── AudioStreamer.cpp/h    # Isochronous USB transfer loop
│       │   │   ├── UsbDacController.cpp/h # UAC descriptor parsing & clock selector
│       │   │   ├── dr_flac.h & dr_wav.h   # Zero-dependency FLAC & WAV decoders
│       │   │   └── native-lib.cpp         # JNI bridge between Kotlin and C++
│       │   ├── java/com/yuka/musicplayer/
│       │   │   ├── MainActivity.kt        # Compose activity entry point
│       │   │   ├── audio/                 # Foreground service, JNI interface, USB manager
│       │   │   ├── metadata/              # Audio tag & embedded artwork extraction
│       │   │   ├── model/                 # Track, playlist, and scan models
│       │   │   ├── player/queue/          # Dynamic queue & playback order helpers
│       │   │   ├── ui/                    # Compose UI components, sheets, views, theme
│       │   │   │   ├── components/        # Fast scroller, bottom sheets, navigation
│       │   │   │   ├── diagnostics/       # Hardware telemetry & system logs UI
│       │   │   │   ├── dialogs/           # Modal dialogs (sorting, playlists, warmup)
│       │   │   │   ├── theme/             # Material 3 colors, typography, blur effects
│       │   │   │   └── views/             # Library, Now Playing, Playlist, Settings views
│       │   │   ├── update/                # In-app GitHub release update checker
│       │   │   └── util/                  # Cache, lyrics parser, permissions, helpers
│       │   └── res/                       # App icons, vectors, and XML resources
│       └── test/                          # Unit tests (JUnit 4)
├── gradle/wrapper/                 # Gradle Wrapper distribution
├── build.gradle.kts                # Root Android build configuration
├── gradle.properties               # JVM memory & AndroidX settings
├── gradlew & gradlew.bat           # Gradle wrapper scripts
├── local.properties                # Android SDK directory config (git-ignored)
└── settings.gradle.kts             # Gradle subproject include settings
```

---

## Development Prerequisites

- **Java Development Kit**: JDK 17 (recommended: OpenJDK 17)
- **Android SDK**: `compileSdk = 34`, `minSdk = 26`, `targetSdk = 34`
- **Android NDK**: NDK version 25+ (e.g. `25.1.8937393` or higher)
- **CMake**: Version 3.22.1+

Make sure your `local.properties` specifies the path to your Android SDK:
```properties
sdk.dir=/path/to/your/Android/sdk
```

---

## Building

```bash
# 1. Clean Build
./gradlew clean

# 2. Build Debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# 3. Build Release APK
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# 4. Run Unit Tests
./gradlew test
```

---

## Testing & USB Telemetry

To monitor real-time USB DAC negotiations, Isochronous transfer timing, and native engine logs via ADB:

```bash
adb logcat -s tsrossa:V native-lib:V UsbExclusive:V
```
