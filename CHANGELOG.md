# Changelog

### Version 1.8.1-BETA
* Routed notifications, incoming calls, microphone input, and audio from other apps to the phone's built-in speaker and mic whenever tsrossa holds the USB DAC (both while playing and while paused/standby).
* Removed the old 8 kHz `AudioFlinger` keep-alive `AudioTrack` so tsrossa never wakes Android's software mixer or resampler, and stopped holding `AudioFocus` on USB DAC so music keeps playing without getting paused by phone speaker sounds.
* Fixed a brief full-volume spike when switching from Hardware UAC mode to 64-Bit DSP mode during playback by waiting for in-flight full-scale USB packets to drain before raising DAC hardware gain.
* Added automatic USB isochronous auto-suspend after roughly 3 seconds of continuous pause to reduce DAC heat and battery drain while idle.
* Tuned the 10-band Parametric EQ to true 1-octave Constant-Q (`1.41`) with pure RBJ Cookbook bandwidth, added +0.2 dB inter-sample headroom compensation, raised the transparent soft-clipping threshold to -0.05 dBFS, and added unbiased mid-tread rounding when down-converting to 16-bit or 24-bit containers.
* Fixed high-speed USB multi-transaction packet size calculation and enforced whole-frame packet alignment to prevent stereo channel or byte misalignment, along with a USB DAC reconnect helper.
* Added a dedicated Folders tab in the Library with fast in-memory folder indexing and a new Sort Folders sheet.
* Reworked library scanning with a multi-layer fallback (direct filesystem scan, active MediaScanner re-indexing for stale `.nomedia` entries, MediaStore query, and SAF DocumentTree support), plus `.nomedia` warnings and clearer Android 11+ All Files Access guidance.
* Made library scrolling smoother by moving fast-scroller thumb positioning to the layout phase and avoiding extra recompositions when dismissing search focus.
* Redesigned the Parametric EQ sheet with a cleaner layout, easier preset management, and a notice explaining how PEQ syncs with 64-Bit DSP volume mode.
* Added a Modal Backdrop Blur toggle under Theme & Display settings.
* Expanded the System Diagnostics panel with storage and scanner details, and filtered noisy Android framework messages out of the app log viewer.
* Updated the Discord community modal and improved supporter wall entry merging.

### Version 1.7.0-BETA
* Added Universal 64-Bit Audiophile DSP with calibrated studio taper curve for more powerful, dynamic volume output.
* Implemented decorrelated dual-channel SplitMix64 TPDF dither for a wider soundstage and holographic instrument separation.
* Implemented frame-locked stereo volume ramp to prevent vocal center-image drift during volume changes.
* Added mid-tread quantization rounding (`std::llrint`) to preserve micro-details and acoustic reverb tails without bit truncation.
* Added Hybrid Hardware UAC with automatic full-scale unity gain (0.0 dB) and hardware unmute on DAC initialization.
* Prioritized high-resolution 32-bit USB subframe container negotiation for maximum dynamic headroom.
* Added dedicated segmented toggle button to switch seamlessly between 64-Bit DSP and Hardware UAC volume modes.
* Redesigned Developer Card with a clean, full-width GitHub Repository button and official brand icon.
* Removed arrow symbols (↗) from Developer and Donation cards for clean and aligned typography.
* Separated developer support into a dedicated card featuring the official Bagi-Bagi vector icon.
* Fixed gapless playback handover and worker thread lifecycle during USB streaming.
* Improved queue shuffle recovery and playlist track index resolution.

### Version 1.6.0-BETA
* Initial rollout of direct USB Isochronous streaming engine.
* Support for FLAC, WAV, and MP3 high-resolution decoding.
* Early implementation of gapless playback buffer.
* Early prototype of the tactile cyberpunk UI.
