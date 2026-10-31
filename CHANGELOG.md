# Changelog

### Version 1.7.0
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
