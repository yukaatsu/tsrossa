# tsrossa for macOS

Bit-perfect audiophile music player for macOS (macOS 12.0 Monterey through macOS 15+ Sequoia) on Apple Silicon (arm64) and Intel (x86_64).

tsrossa bypasses the macOS system CoreAudio software mixer, volume scaling, and alert sound multiplexing by communicating directly with hardware output devices in **Exclusive Hog Mode** (`kAudioDevicePropertyHogMode`) and dynamically switching physical DAC hardware clocks to match native file sampling frequencies.

---

## Key Features

- **Exclusive Hog Mode**: Takes direct ownership of the DAC hardware device (`kAudioDevicePropertyHogMode`), locking out other applications and eliminating system mixer interference.
- **Dynamic Clock Rate Switching**: Dynamically negotiates the physical DAC clock rate (44.1 kHz, 48 kHz, 88.2 kHz, 96 kHz, 176.4 kHz, 192 kHz, up to 384 kHz) without software resampling.
- **Native C++17 Audio Engine**: Standalone CoreAudio HAL driver with POSIX `mlock()` memory pinning for jitter-free, zero-copy PCM playback.
- **Hardware Master Scalar & Unity Gain**: Preserves genuine 0 dBFS dynamic range when set to 100%.
- **SwiftUI Desktop Interface**:
  - Split widescreen layout with Now Playing (Cmd + 1), Audiophile Library (Cmd + 2), and Settings (Cmd + 4).
  - Synchronized `.lrc` and embedded lyrics viewer with Theater Mode (Cmd + F) and click-to-seek support.
  - Typeahead fuzzy search and format/sampling rate filter chips.
  - Real-time signal path inspector showing source bitrate/kHz vs. active hardware DAC clock frequency.
  - Automatic hotplug handling for USB DACs, Thunderbolt interfaces, and 3.5mm headphone jacks.
- **No Heavy IDE Required**: Builds directly via standard `clang++` and `swiftc` toolchains without requiring full Xcode project overhead.
- **DMG Installer Packaging**: Includes automated disk image packaging with custom drag-and-drop Applications symlink.

---

## Directory Structure

```text
mac/
├── macos/
│   ├── Info.plist                     # Application bundle metadata and permissions
│   └── Sources/                       # Native SwiftUI Application
│       ├── main.swift                 # Entry point (`@main NSApplication`)
│       ├── MainAppView.swift          # Main window layout, sidebar, player bar
│       ├── AppViewModel.swift         # Reactive application state & queue controller
│       ├── AudioEngine.swift          # Swift bridge to native CoreAudio HAL
│       ├── LyricsManager.swift        # Synchronized LRC and lyrics parser
│       ├── MetadataScanner.swift      # Multi-threaded lossless track scanner
│       ├── TelemetryModalView.swift   # Real-time hardware telemetry inspector
│       ├── Theme.swift                # Audiophile color schemes and tokens
│       └── UpdateManager.swift        # In-app update checker via GitHub Releases
├── src/nativeMain/cpp/                # Native C++ CoreAudio HAL Engine
│   ├── CoreAudioSink.cpp / .h         # Hardware HAL driver & Exclusive Hog Mode
│   ├── TsrossaAudioBridge.cpp / .h    # C / Objective-C bridge for Swift runtime
│   ├── MacAudioCommon.h               # Shared audio formats and ring buffer structs
│   ├── MacAudioDecoder.h              # Decoder abstractions
│   └── dr_impl.c                      # Single-compilation-unit dr_flac & dr_wav
├── build/                             # Compiled binaries, dylibs, and app bundle (git-ignored)
├── build_mac.sh                       # Shell script compiling dylib and bundling tsrossa.app
├── package_dmg.sh                     # Shell script creating distributable DMG installer
├── .gitignore                         # Subproject-specific ignore rules
└── README.md                          # Dedicated macOS documentation
```

---

## Prerequisites

- **Operating System**: macOS 12.0 (Monterey) or newer (tested up to macOS 15+ Sequoia).
- **Architecture**: Apple Silicon (`arm64`) or Intel (`x86_64`).
- **Toolchain**: Xcode Command Line Tools. Install if not already available:
  ```bash
  xcode-select --install
  ```

---

## Building from Source

All commands are executed from the `mac/` directory:

### 1. Build Native Engine and macOS App Bundle
```bash
chmod +x build_mac.sh package_dmg.sh
./build_mac.sh
```

This compiles:
1. `libtsrossa-mac.dylib` via `clang++` with `-O2 -std=c++17` linking CoreAudio, AudioToolbox, and CoreFoundation.
2. The SwiftUI frontend using `swiftc` with module caching.
3. Assembles the standalone `build/tsrossa.app` bundle containing executable binaries, dynamic libraries, and application icon resources.

### 2. Package as a Disk Image (.dmg)
```bash
./package_dmg.sh
```
Output: `build/tsrossa-1.6.0.dmg`

### 3. Deploy to `/Applications`
```bash
rm -rf /Applications/tsrossa.app
cp -R build/tsrossa.app /Applications/tsrossa.app
xattr -cr /Applications/tsrossa.app
codesign --force --deep -s - /Applications/tsrossa.app
open /Applications/tsrossa.app
```

---

## Keyboard Shortcuts

| Shortcut | Action |
| :--- | :--- |
| `Space` | Play / Pause |
| `Cmd + 1` | Switch to Now Playing Screen |
| `Cmd + 2` | Switch to Music Library |
| `Cmd + 4` | Open Settings & CoreAudio Configuration |
| `Cmd + F` | Toggle Theater Mode (Immersive Lyrics & Album Art) |
| `Cmd + U` | Check for App Updates |
| `Cmd + [` / `Cmd + ]` | Previous / Next Track |
| `Cmd + Up` / `Cmd + Down` | Volume Up / Down (Scalar Control) |

---

## Troubleshooting

### LaunchServices Error -54 ("The application cannot be opened")
macOS quarantine attributes flag downloaded or locally built unsigned binaries. Strip the quarantine attribute and ad-hoc sign the bundle:
```bash
xattr -cr /Applications/tsrossa.app
codesign --force --deep -s - /Applications/tsrossa.app
```

### Exclusive Hog Mode Denied
If another application (such as Apple Music, Tidal, or a DAW like Logic Pro) currently has exclusive ownership of your DAC, close that application or toggle Hog Mode in tsrossa settings (`Cmd + 4`).
