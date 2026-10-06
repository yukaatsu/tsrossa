# Phase 1: Now Playing & Playback Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the Now Playing screen (TrackView) and core playback controls from the current "AI slop" look (Pacman arcade seekbar, ascii/text-only controls, 1px boxy borders) into a sleek "Cyber-Audiophile Minimalist" interface.

**Architecture:** Create dedicated high-precision Canvas/Vector UI components for the scrubber (`PrecisionScrubber`), tactile transport controls (`CyberTransportControls`), and refined audiophile spec badges (`AudiophileSpecStrip`). Integrate them cleanly into `TrackView.kt` while preserving all state callbacks, audio synchronization, and volume logic.

**Tech Stack:** Jetpack Compose, Compose Canvas, Compose Graphics / Vectors, Fantasque Sans Mono, Kotlin Coroutines.

**Spec:** Redesign discussion and approval for Phase 1 (Now Playing & Controls) in conversation transcript.

## Global Constraints
- Do NOT alter C++ audio engine code or JNI bindings.
- Keep all existing callback signatures for `TrackView` (`onTogglePlay`, `onPlayNext`, `onPlayPrev`, `onSeekTo`, `onVolumeChange`, etc.).
- Maintain zero-latency seeking behavior and accurate audio synchronization.
- Strictly adhere to `LocalAccentColor.current` and the signature `SignatureDeepNavy` / `SignatureSurfaceNavy` palette.

---

### Task 1: Create Custom Audio Glyphs & Tactile Button Components
**Files to create/modify:**
- Create `app/src/main/java/com/yuka/musicplayer/ui/components/AudioGlyphs.kt`
- Modify `app/src/main/java/com/yuka/musicplayer/ui/components/RetroComponents.kt`

- [ ] Define vector Canvas-based icons for Play, Pause, Previous, Next, Shuffle, Repeat, and Volume (`AudioGlyphs.kt`) so they scale crisply and have perfect optical alignment.
- [ ] Create `TactileButton` with spring-scale physics on pointer press (`animateFloatAsState` targeting 0.92f on press).
- [ ] Create `HeroPlayButton` (64.dp circle with luminous glow and smooth morph/crossfade between Play and Pause).
- [ ] Verify build with `./gradlew compileDebugKotlin`.

---

### Task 2: Implement Precision Scrubber (Replace PacmanSeekBar)
**Files to create/modify:**
- Modify `app/src/main/java/com/yuka/musicplayer/ui/views/TrackView.kt`

- [ ] Replace `PacmanSeekBar` with `PrecisionScrubber`:
  - 3dp track groove with subtle depth (`Color(0xFF141A28)`).
  - Played progress line with smooth glow (`dynamicColor.copy(alpha = 0.4f)` outer bloom + core line).
  - Tactile glowing thumb dot (8dp diameter, expanding to 12dp during drag).
  - Timestamp typography: Left current time (`mm:ss`), Right remaining time (`-mm:ss`), aligned cleanly below/above the track bar with `Fantasque Sans`.
- [ ] Ensure smooth drag scrubbing without stuttering or audio artifacts.
- [ ] Verify build with `./gradlew compileDebugKotlin`.

---

### Task 3: Modernize Album Art Container & Audiophile Spec Strip
**Files to create/modify:**
- Modify `app/src/main/java/com/yuka/musicplayer/ui/views/TrackView.kt`

- [ ] Refactor Album Art container:
  - Remove harsh 1.5px bright neon border.
  - Implement smooth rounded corners (`12.dp`) with subtle layered surface background.
  - Add soft ambient bloom / drop shadow that dynamically blends the dominant album color into the background.
- [ ] Replace raw codec text with `AudiophileSpecStrip`:
  - Compact industrial chip row: `[ FLAC ]` • `[ 24-BIT / 96.0 kHz ]` • `[ BIT-PERFECT USB ]`.
  - Subtle hairline borders (`0.5.dp`, alpha 0.2) and muted styling.
- [ ] Verify build with `./gradlew compileDebugKotlin`.

---

### Task 4: Integrate Tactile Controls into TrackView & Volume Bar
**Files to create/modify:**
- Modify `app/src/main/java/com/yuka/musicplayer/ui/views/TrackView.kt`

- [ ] Replace `|<<`, `❚❚`, `>>|` text boxes with `HeroPlayButton` and `AudioGlyphs` prev/next tactile controls.
- [ ] Modernize the secondary row (Shuffle, Queue, Repeat) using minimal pill chips with micro-indicator LEDs instead of bulky text boxes (`🔀 SHUF: ON`).
- [ ] Polish the dedicated Volume bar into a slim tactile slider/stepper.
- [ ] Run full project compile (`./gradlew compileDebugKotlin`) and verify zero regression.
