# Changelog

All notable changes to **Tsrossa Music Player** are documented in this file.

---

## [1.7.0] - 2026-10-31

### 🎵 Universal 64-Bit Audiophile DSP
* **Calibrated Studio Taper Volume Curve**: Implemented an audiophile-grade perceptual volume curve `dB(v) = -48.0 * (1 - v) * sqrt(1 - v)`. Volume at 50% now delivers powerful ~14.2% voltage output (-16.97 dB) for authoritative dynamics without clipping or harshness.
* **Decorrelated Dual-Channel SplitMix64 TPDF Dither**: Replaced legacy single LCG PRNG with dual independent 64-bit SplitMix64 state generators for Left and Right channels. Interaural Cross-Correlation (`IACC = 0.0`) provides an expansive 3D soundstage and holographic instrument separation.
* **Frame-Locked Stereo Volume Ramp**: Synchronized volume transitions strictly per stereo frame (L and R ramp at the exact same instant), eliminating vocal center-image drift during volume adjustments.
* **Mid-Tread Rounding Quantization**: Integrated `std::llrint()` nearest-integer rounding for 32-bit, 24-bit, and 16-bit subframe output, preserving delicate micro-details, harmonic decay, and acoustic reverb tails without truncation distortion.

### 🔌 Hybrid Hardware UAC (USB Audio Class 1.0 & 2.0)
* **High-Resolution Subframe Container Negotiation**: Automatic negotiation prioritizes the highest bit-depth container supported by the external DAC (32-bit > 24-bit > 16-bit), providing up to 192 dB dynamic processing headroom.
* **Auto Full-Scale Unity Gain & Unmute**: Sends hardware control transfers to unmute and force `0.0 dB` Unity Gain (2Vrms/4Vrms Full-Scale) on DAC initialization and altsetting switch, resolving low-volume or cold-boot soft-mute issues on Cirrus Logic CS43131, ESS Sabre, Conexant CX31993, and other audiophile dongles.
* **Hardened Feature Unit Control**: Robust parsing of UAC 1.0 & UAC 2.0 descriptors across master and discrete stereo channels.
* **Segmented Audio Mode Selector**: Dedicated UI toggle allowing instantaneous seamless switching between **UNIVERSAL 64-BIT DSP** and **HARDWARE UAC** modes.

### 🎨 UI / UX Refinements
* **Developer Card Redesign**: Restored GitHub action button to full-width with the official GitHub brand glyph (`GlyphGitHub`).
* **Typography Polish**: Removed Unicode arrow decorators (`↗`) from Developer and Donation cards for clean, balanced text baselines across all Android OEM font engines.
* **Dedicated Donation Section**: Isolated developer support link into a distinct card featuring the custom Bagi-Bagi vector glyph (`GlyphBagiBagi`).

### 🛠️ Engine & Playback Stability
* **Gapless Decoder Handover**: Fixed decoder thread lifecycle and eliminated race conditions/deadlocks during gapless track transitions over USB Isochronous streaming.
* **Queue & Playlist Resolution**: Hardened shuffle recovery, playlist synchronization, and track index resolution in queue management.
* **Comprehensive Test Suite**: Added JUnit test assertions verifying volume curve mathematical parity, boundary safety, and bit-exact calculations.

---

## [1.6.0-BETA] - 2026-10-24
* Initial implementation of direct USB Isochronous audio streaming engine.
* Support for high-resolution FLAC, WAV, and MP3 decoding.
* Early implementation of gapless playback buffer.
* Material 3 / Modern Cyberpunk tactile user interface.
