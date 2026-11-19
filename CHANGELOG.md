# Release Notes & Changelog

## Version 1.8.1-BETA (2026-11-20) — The Coexistence & Daily Driver Update 🎧

Hey everyone! Welcome to version **1.8.1-BETA**.

While previous updates focused heavily on raw audio math and pushing bit-perfect playback to the limit, this release is all about making tsrossa a joy to use as your everyday music player. We solved the long-standing friction where connecting an external DAC hijacked all audio on your phone, squashed volume jumps when switching audio modes, and introduced dedicated folder browsing.

---

### What’s New in v1.8.1-BETA

#### 📱 Seamless Phone Speaker Coexistence (Play & Standby)
* **Calls, Notifications & Other Apps on Phone Speaker**: When your USB DAC is connected, Android notifications, phone calls, WhatsApp voice notes, and YouTube videos are automatically routed to your phone's built-in speaker and microphone. Your music stays locked to your DAC, and your phone behaves like a normal phone again.
* **Continuous Standby & Play Coverage**: This smart routing stays active the second you plug in your DAC—whether you're actively playing music or paused in standby.
* **Zero OS Resampling Interference**: We eliminated background keep-alive tracks and non-essential `AudioFocus` locks. Tsrossa never forces Android’s 48 kHz mixer or software resampler into action, keeping your bit-perfect stream 100% pure.

#### 🛡️ Anti-Blast Volume Switching & Thermal Auto-Suspend
* **Safe Mode Switching (Hardware UAC ↔ 64-Bit DSP)**: Switching from Hardware UAC to Universal 64-Bit DSP while music is playing now waits for in-flight USB packets to finish draining before raising hardware gain. No more sudden 100% volume blasts when toggling modes.
* **Idle USB Auto-Suspend**: Pausing music for more than ~3 seconds now cleanly suspends isochronous USB transfers. Your dongles and portable DACs will run significantly cooler, saving your phone's battery when you step away.

#### 🎚️ Refined 10-Band Parametric EQ & Mastering Headroom
* **True Constant-Q Filters (Q = 1.41)**: Upgraded peaking biquad filters to true 1-octave Constant-Q with Robert Bristow-Johnson Cookbook bandwidth. Every band now shapes the sound surgically without choking upper-treble air or masking adjacent frequencies.
* **Pristine Dynamic Ceiling**: Raised the soft-knee limiting threshold to `-0.05 dBFS` with `+0.2 dB` inter-sample headroom compensation. You get maximum punch, expansive dynamics, and zero audible clipping or compression artifacts.
* **Unbiased Mid-Tread Quantization**: Smooth down-conversion when playing high-res files into 16-bit or 24-bit DAC containers, keeping low-level acoustic decays and reverb tails natural.

#### 📁 Dedicated Folders Tab & Resilient Music Scanner
* **Instant In-Memory Folder Browser**: Added a dedicated **Folders** tab in your Library with pure-RAM indexing. Browse by directory, drill down through subfolders, and sort by name, track count, or date without lag.
* **Smarter Scanner with Stale `.nomedia` Detection**: If music was missing because of leftover `.nomedia` files or Android 11+ scoped storage quirks, the new multi-layer scanner actively triggers re-indexing and displays a helpful banner to guide you.
* **Buttery Smooth Fast-Scroller**: Moved fast-scroller thumb calculations to the layout phase, eliminating micro-stutters and unnecessary recompositions when gliding through thousands of songs.

#### 🎨 Interface Polish & Housekeeping
* **Modal Backdrop Blur Toggle**: Added an option in *Settings → Theme & Display* to turn background screen blur on or off when opening bottom sheets and dialogs.
* **Cleaner Parametric EQ Interface**: Reorganized the PEQ sheet with larger touch targets, quick preset switching, and a clear explanation of how PEQ pairs with 64-Bit DSP mode.
* **Noise-Filtered Diagnostic Logs**: Filtered noisy Android framework logs from the System Logs panel so hardware telemetry and USB handshake details are easy to read.

---

## Version 1.8.0-BETA (2026-11-14) — Parametric EQ & Community Integration

#### 🎛️ 10-Band Parametric Equalizer
* **Full PEQ Engine**: Introduced a studio-grade 10-band Parametric EQ with Low Shelf, High Shelf, and Peaking biquad filters.
* **Preset Export & Import**: Save custom sound profiles and export/import them as `.json` files to share across devices or with the community.
* **Mastering Limiter Integration**: Integrated soft-knee lookahead ceiling to prevent digital clipping when applying positive gain boosts.

#### 💬 Discord & Community Hub
* **Direct Bug Reporting**: Added a dedicated community modal connecting you to our Discord server for instant feedback, troubleshooting, and feature requests.
* **Preset Sharing & Beta Testing**: Join fellow audiophiles to exchange PEQ curves and help test upcoming experimental builds.
* **Supporter Wall**: Added developer support recognition via Bagi-Bagi.

---

## Version 1.7.0-BETA (2026-10-31) — The Audiophile Tuning Update

* **Universal 64-Bit Audiophile DSP**: Calibrated studio taper volume curve delivering powerful, dynamic volume output.
* **Decorrelated Dual-Channel SplitMix64 Dither**: Independent stereo TPDF dither for an expansive soundstage and clear instrument separation.
* **Frame-Locked Stereo Volume Ramp**: Synchronized stereo volume steps preventing vocal center-image drift.
* **Mid-Tread Quantization Rounding**: `std::llrint` rounding preserving micro-details and acoustic reverb tails.
* **Hybrid Hardware UAC**: Automatic full-scale unity gain (0.0 dB) and hardware unmute on DAC initialization.
* **32-Bit USB Container Priority**: Automatic negotiation prioritizing highest available subframe container.
* **Dedicated Mode Switcher**: Quick toggle button between 64-Bit DSP and Hardware UAC volume modes.
* **UI Refinements**: Clean developer and supporter cards with official brand iconography.

---

## Version 1.6.0-BETA
* Initial rollout of direct USB Isochronous streaming engine.
* Support for FLAC, WAV, and MP3 high-resolution decoding.
* Early implementation of gapless playback buffer.
* Early prototype of the tactile cyberpunk UI.
