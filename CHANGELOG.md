# Release Notes & Changelog

## Version 1.7.0 (2026-10-31) — The Audiophile Tuning Update 🎧

Hey everyone! Welcome to version **1.7.0**. 

This update is all about sound quality, dynamic punch, and interface polish. If you’ve felt that USB DAC playback sounded a bit tame or lacked that airy, wide soundstage in previous builds, this release was made specifically for you. We overhauled the internal audio math from the ground up and cleaned up the interface.

---

### What’s New & Improved

#### 🔊 Bigger Sound, Zero Distortion (Universal 64-Bit DSP)
* **Tuned Studio Taper Volume Curve**: We completely recalibrated how volume scales. At 50% slider, you now get significantly more presence and body (~14.2% output voltage / -17 dB) rather than sounding hushed. It gives your music full, punchy dynamics across normal listening levels without ever clipping the peaks.
* **Wider, Holographic Soundstage**: We introduced independent dual-channel SplitMix64 random generators for stereo dithering. By eliminating crosstalk noise between the left and right ears, the stereo imaging opens up dramatically—instruments have distinct breathing room, and live recordings feel much more spacious.
* **Locked Stereo Imaging**: Volume ramps now step simultaneously for both left and right channels per stereo frame. No more subtle vocal swaying or imaging drift when you adjust the volume slider during a song.
* **True Micro-Detail Preservation**: Subtle reverb decays, room acoustics, and gentle acoustic fading now round smoothly to the nearest target bit (`std::llrint`), preventing harsh truncation distortion at low listening volumes.

#### ⚡ Full Power to Your External DAC (Hybrid Hardware UAC)
* **Automatic Full-Scale Unity & Unmute**: Many USB dongles and DACs (Cirrus Logic CS43131, ESS Sabre, Conexant CX31993, etc.) boot up in a quiet "soft-mute" state. Tsrossa now forcefully commands the DAC hardware to full 0.0 dB scale and un-mutes all channels on startup and track change. You get the full 2Vrms/4Vrms output right out of the gate.
* **32-Bit USB Container Priority**: The audio streamer now always requests the highest bit-depth container your DAC can handle (up to 32-bit/384kHz). This gives our 64-bit DSP maximum dynamic headroom and keeps 16-bit to 24-bit transitions seamless without relay clicks.
* **Dedicated Mode Switcher**: Easily toggle between **Universal 64-Bit DSP** and **Hardware UAC** directly from the playback volume panel.

#### 🎨 Interface Polish & Housekeeping
* **Cleaner Developer Card**: Restored the GitHub action button to a clean, balanced full-width layout with the official GitHub icon.
* **Farewell to Arrow Clutter**: Removed the Unicode `↗` symbols from the buttons across the Settings and About pages for crisp, aligned typography.
* **Dedicated Support Section**: The donation link (Bagi-Bagi) now has its own dedicated card right below the developer info, keeping things clean and uncluttered.

#### 🛠️ Under-the-Hood Fixes
* Fixed gapless handover edge cases that could cause USB audio worker pauses between long tracks.
* Improved queue shuffle recovery and playlist track indexing.
* Full test coverage ensuring mathematical bit-accuracy between C++ native code and Kotlin UI layers.

---

### Previous Releases

#### Version 1.6.0-BETA
* Initial rollout of direct USB Isochronous streaming engine.
* Support for FLAC, WAV, and MP3 high-resolution decoding.
* Early prototype of the tactile cyberpunk UI.

---

*Thank you for listening and supporting Tsrossa Music Player! If you enjoy this release, please consider starring the repository on GitHub or sharing your feedback.*
