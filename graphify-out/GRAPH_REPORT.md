# Graph Report - tsrossa-macos  (2026-11-02)

## Corpus Check
- 71 files · ~157,628 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 2 file(s) not represented in the graph (top: (none) 1, .plist 1)

## Summary
- 1500 nodes · 4279 edges · 69 communities (48 shown, 21 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 123 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- CoreAudio HAL & Bit-Perfect DSP
- FLAC Frame Seeking & NEON
- WAV Stream Callbacks & I/O
- WAV Container & Header Formats
- FLAC Metadata & Cuesheet Parser
- Binary Audio Tag & Art Cache
- FLAC Bitstream & Rice Decoder
- WAV Sample Endianness & Seeking
- WAV Chunk & Metadata Reader
- AppViewModel & Media Integration
- FLAC Ogg & Vorbis Comments
- Swift Protocols & Log Categories
- C++ Concurrency & AudioToolbox
- FLAC 16-Bit Stereo SIMD Decode
- FLAC CPU Feature Detection
- Ring Buffer & Decode Thread
- Master Remediation Roadmap & Plans
- MacAudioDecoder C++ Stream Wrapper
- Swift AudioEngine & Gapless Control
- Telemetry Logger & Event RingBuffer
- Track Filtering & Theme State
- Liquid Glass Optical Modifiers
- Play Queue & Autoplay Radio
- Playlist Modal & Workspace Routing
- Secure GitHub Release Auto-Updater
- WAV Format Tag & Byte-Swap
- User Playlist Persistence & Folders
- Playback Transport & Track Advance
- Library View & Filter Chips
- HSL Color Space & Glassmorphic
- WAV 16-Bit PCM Conversion
- GitHub Release Asset Verification
- Swift Unit Test Harness
- WAV 32-Bit PCM Conversion
- WCAG Contrast & Hue Clustering
- Fuzzy Search & Multi-Field Sort
- Synchronized LRC Lyrics Engine
- macOS App Lifecycle & Delegate
- Workspace Tab Navigation
- Shuffle Deck & Repeat State
- macOS System Frameworks & Crypto
- Reverse Engineering Parity Audits
- Playlist Codable Serialization
- Isolated Playback Scrubber View
- CoreAudio Output Device Enumeration
- Window Resize & Zoom Bug Audit
- macOS App Bundle & Icon Build
- Ambient Artwork Mesh Background
- Dynamic Theme Color Bridge
- Settings Bento Grid Views
- C++ Telemetry JSON Serialization
- Queue & Lyrics Split Panel
- Track Technical Details Sheet
- Synthetic FLAC Tag Unit Tests
- Custom Button Styles & Colors
- NSVisualEffectView SwiftUI Bridge
- SplitMix64 TPDF Dither Processor
- Async Music Folder Scanner
- Now Playing Hero Split View
- AudioEngine System Frameworks
- Diagnostic Log Severity Levels
- Adaptive Foreground Launcher Icon
- Studio Taper Volume Curve
- Integer Family Sample Rate Matcher
- DMG Disk Image Packager
- Automated Test Runner Script
- macOS Clean Uninstaller Script

## God Nodes (most connected - your core abstractions)
1. `CoreAudioSink` - 140 edges
2. `AppViewModel` - 110 edges
3. `AudioEngine` - 43 edges
4. `TrackMetadata` - 40 edges
5. `LiquidGlassTheme` - 38 edges
6. `MacAudioDecoder` - 29 edges
7. `UpdateManager` - 25 edges
8. `UserPlaylist` - 24 edges
9. `drflac_open_with_metadata_private()` - 23 edges
10. `ExtractedAudioMetadata` - 18 edges

## Surprising Connections (you probably didn't know these)
- `AudioProgressModel Isolation & Negative Cache for AlbumArtCache` --rationale_for--> `AudioEngine`  [EXTRACTED]
  AUDIT-ANALISA/07-AUDIT-BUG-WINDOW-RESIZE-FIT-SCREEN-DAN-MENU-LAG.md → macos/Sources/AudioEngine.swift
- `TsrossaColors` --semantically_similar_to--> `Crimson Rose Blossom & Floating Petal Motif`  [INFERRED] [semantically similar]
  macos/Sources/Theme.swift → macos/Resources/ic_launcher.png
- `07 Audit Bug Window Resize, Double-Tap Fit-to-Screen, & Menu Lag` --references--> `AlbumArtCache`  [EXTRACTED]
  AUDIT-ANALISA/07-AUDIT-BUG-WINDOW-RESIZE-FIT-SCREEN-DAN-MENU-LAG.md → macos/Sources/AlbumArtCache.swift
- `AudioProgressModel Isolation & Negative Cache for AlbumArtCache` --rationale_for--> `AlbumArtCache`  [EXTRACTED]
  AUDIT-ANALISA/07-AUDIT-BUG-WINDOW-RESIZE-FIT-SCREEN-DAN-MENU-LAG.md → macos/Sources/AlbumArtCache.swift
- `03 Audit Metadata, Database Library, Sorting, & Search` --references--> `AppViewModel`  [EXTRACTED]
  AUDIT-ANALISA/03-AUDIT-METADATA-DATABASE-LIBRARY-DAN-SEARCH.md → macos/Sources/AppViewModel.swift

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **CoreAudio Bit-Perfect & 64-Bit DSP Pipeline Requirements** — src_nativemain_cpp_coreaudiosink_coreaudiosink, audit_analisa_01_audit_audio_engine_dsp_dan_coreaudio_bit_perfect_signal_path, audit_analisa_01_audit_audio_engine_dsp_dan_coreaudio_splitmix64_tpdf_dither, audit_analisa_01_audit_audio_engine_dsp_dan_coreaudio_volume_ramping, audit_analisa_01_audit_audio_engine_dsp_dan_coreaudio_sample_rate_negotiation [EXTRACTED 1.00]
- **Bounded Ring Buffer Streaming, Gapless Handover & Atomic CAS Seek** — src_nativemain_cpp_coreaudiosink_coreaudiosink, macos_sources_audioengine_audioengine, audit_analisa_02_audit_memori_streaming_gapless_dan_konkurensi_sliding_window_ring_buffer, audit_analisa_02_audit_memori_streaming_gapless_dan_konkurensi_gapless_handover, audit_analisa_02_audit_memori_streaming_gapless_dan_konkurensi_atomic_cas_seek [EXTRACTED 1.00]
- **Root Causes of Window Resize Corruption, Fit-to-Screen Bug & Menu Switch Lag** — macos_sources_mainappview_mainappview, macos_sources_liquidglasscomponents_liquidglasscomponents, macos_sources_audioengine_audioengine, macos_sources_albumartcache_albumartcache, audit_analisa_07_audit_bug_window_resize_fit_screen_dan_menu_lag_zstack_scaledtofill_trap, audit_analisa_07_audit_bug_window_resize_fit_screen_dan_menu_lag_window_zoom_manager, audit_analisa_07_audit_bug_window_resize_fit_screen_dan_menu_lag_observable_progress_isolation [EXTRACTED 1.00]
- **TSRossa macOS 7-Phase Master Remediation & Architecture Plans** — planning_perbaikan_00_master_roadmap_dan_arsitektur_perbaikan_doc, planning_perbaikan_01_plan_build_system_updater_dan_test_harness_doc, planning_perbaikan_02_plan_audio_engine_dsp_dan_coreaudio_doc, planning_perbaikan_03_plan_memori_ringbuffer_gapless_dan_konkurensi_doc, planning_perbaikan_04_plan_metadata_sqlite_sorting_dan_search_doc, planning_perbaikan_05_plan_telemetri_nyata_mediakeys_dan_paritas_fitur_doc, planning_perbaikan_06_plan_ui_ux_themes_swift_hig_dan_liquid_glass_doc, planning_perbaikan_07_plan_perbaikan_bug_resize_fit_screen_dan_menu_lag_doc [EXTRACTED 1.00]
- **Audiophile Native C++ CoreAudio DSP, 64MB Ring Buffer & Gapless Pipeline** — planning_perbaikan_02_plan_audio_engine_dsp_dan_coreaudio_studiotaper, planning_perbaikan_02_plan_audio_engine_dsp_dan_coreaudio_splitmix64dither, planning_perbaikan_02_plan_audio_engine_dsp_dan_coreaudio_sampleprocessor, planning_perbaikan_02_plan_audio_engine_dsp_dan_coreaudio_sampleratematcher, planning_perbaikan_03_plan_memori_ringbuffer_gapless_dan_konkurensi_sliding_window_ringbuffer, planning_perbaikan_03_plan_memori_ringbuffer_gapless_dan_konkurensi_gapless_engine [EXTRACTED 1.00]
- **Bug-Note Remediation Triad (Deterministic App Shell, WindowZoomManager & AudioProgressModel)** — bug_note_doc, planning_perbaikan_07_plan_perbaikan_bug_resize_fit_screen_dan_menu_lag_deterministic_app_shell, planning_perbaikan_07_plan_perbaikan_bug_resize_fit_screen_dan_menu_lag_windowzoommanager, planning_perbaikan_07_plan_perbaikan_bug_resize_fit_screen_dan_menu_lag_audioprogressmodel_and_negative_cache [EXTRACTED 1.00]
- **TSRossa App Icon Visual Composition** — macos_resources_ic_launcher, macos_resources_ic_launcher_thorny_eighth_note_glyph, macos_resources_ic_launcher_crimson_rose_blossom, macos_resources_ic_launcher_obsidian_radial_canvas, macos_resources_ic_launcher_tsrossa_brand_identity [EXTRACTED 1.00]
- **TSRossa Launcher Icon Foreground Branding Elements** — macos_resources_ic_launcher_foreground_tsrossa_foreground_icon, macos_resources_ic_launcher_foreground_musical_note_rose_emblem, macos_resources_ic_launcher_foreground_adaptive_foreground_layer, macos_resources_ic_launcher_tsrossa_app_icon [INFERRED 0.85]

## Communities (69 total, 21 thin omitted)

### Community 0 - "CoreAudio HAL & Bit-Perfect DSP"
Cohesion: 0.03
Nodes (75): CoreAudioSink, m_activeDecoder, m_activeDevice, m_actualHardwareSampleRate, m_audioUnit, m_autoClockEnabled, m_bitsPerSample, m_bufferProfile (+67 more)

### Community 1 - "FLAC Frame Seeking & NEON"
Cohesion: 0.10
Nodes (67): drflac_bool32 drflac_seek_to_pcm_frame(), drflac__decode_flac_frame(), drflac__decode_flac_frame_and_seek_forward_by_pcm_frames(), drflac__get_pcm_frame_range_of_current_flac_frame(), drflac_int32 drflac__calculate_prediction_32(), drflac_int32 drflac__calculate_prediction_64(), drflac__is_current_flac_frame_valid(), drflac_ogg__seek_to_pcm_frame() (+59 more)

### Community 2 - "WAV Stream Callbacks & I/O"
Cohesion: 0.08
Nodes (50): drwav_bool32 drwav_init(), drwav_bool32 drwav_init_ex(), drwav_bool32 drwav_init_file(), drwav_bool32 drwav_init_file_ex(), drwav_bool32 drwav_init_file_ex_w(), drwav_bool32 drwav_init_file__internal_FILE(), drwav_bool32 drwav_init_file_w(), drwav_bool32 drwav_init_file_with_metadata() (+42 more)

### Community 3 - "WAV Container & Header Formats"
Cohesion: 0.07
Nodes (48): drwav_alaw_to_f32(), drwav_bool32 drwav__chunk_matches(), drwav_bool32 drwav_fourcc_equal(), drwav_bool32 drwav_guid_equal(), drwav_bool32 drwav_init__internal(), drwav_bytes_to_f32(), drwav_bytes_to_guid(), drwav_bytes_to_u16_be() (+40 more)

### Community 4 - "FLAC Metadata & Cuesheet Parser"
Cohesion: 0.10
Nodes (41): drflac_bool32 drflac_next_cuesheet_track(), drflac_close(), drflac_fopen(), drflac_free(), drflac__free_default(), drflac__free_from_callbacks(), drflac__init_cpu_caps(), drflac_init_cuesheet_track_iterator() (+33 more)

### Community 5 - "Binary Audio Tag & Art Cache"
Cohesion: 0.07
Nodes (8): AlbumArtCache, AudioTagReader, ExtractedAudioMetadata, TrackTitleParser, MetadataScanner, MusicLibraryDatabase, .artwork, SQLite3

### Community 6 - "FLAC Bitstream & Rice Decoder"
Cohesion: 0.13
Nodes (43): drflac_bool32 drflac__read_rice_parts(), drflac_bool32 drflac__read_rice_parts_x1(), drflac_bool32 drflac__read_uint32(), drflac_bool32 drflac__seek_past_next_set_bit(), drflac_bool32 drflac__seek_rice_parts(), drflac__decode_samples__constant(), drflac__decode_samples__fixed(), drflac__decode_samples__lpc() (+35 more)

### Community 7 - "WAV Sample Endianness & Seeking"
Cohesion: 0.10
Nodes (48): drwav_bool32 drwav_init_write__internal(), drwav_bool32 drwav_seek_to_pcm_frame(), drwav__bswap_samples(), drwav__bswap_samples_s16(), drwav__bswap_samples_s32(), drwav_calculate_padding_size(), drwav__chunk_padding_size_riff(), drwav__chunk_padding_size_w64() (+40 more)

### Community 8 - "WAV Chunk & Metadata Reader"
Cohesion: 0.09
Nodes (38): drwav_allocation_callbacks drwav_copy_allocation_callbacks_or_defaults(), drwav_bool32 drwav__on_seek(), drwav_bool32 drwav__on_seek_memory(), drwav_bool32 drwav__on_seek_memory_write(), drwav_bool32 drwav__on_seek_stdio(), drwav_bool32 drwav_seek_to_first_pcm_frame(), drwav_buffer_reader_ptr(), drwav__malloc_default() (+30 more)

### Community 9 - "AppViewModel & Media Integration"
Cohesion: 0.06
Nodes (22): AppViewModel, .accentColorMode, .activeTab, .bgCanvasMode, .bufferProfile, .distinctArtists, .hasActiveFilters, .hasLyrics (+14 more)

### Community 10 - "FLAC Ogg & Vorbis Comments"
Cohesion: 0.08
Nodes (25): drflac_bool32 drflac_has_sse2(), drflac_bool32 drflac_has_sse41(), drflac__cpuid(), drflac__decode_samples_with_residual__rice__neon_32(), drflac__decode_samples_with_residual__rice__neon_64(), drflac_init_vorbis_comment_iterator(), drflac_next_vorbis_comment(), drflac_oggbs__get_current_segment_index() (+17 more)

### Community 11 - "Swift Protocols & Log Categories"
Cohesion: 0.06
Nodes (37): LogCategory, audio, dsp, hardware, library, system, RepeatMode, all (+29 more)

### Community 12 - "C++ Concurrency & AudioToolbox"
Cohesion: 0.10
Nodes (10): BufferProfileHelper, main(), testPerSampleGainRampAndBitExactPass(), testSampleRateMatcher(), testSplitMix64TpdfDither(), testStudioTaper(), main(), testCompareExchangeSeekSafety() (+2 more)

### Community 13 - "FLAC 16-Bit Stereo SIMD Decode"
Cohesion: 0.12
Nodes (33): drflac_bool32 drflac__use_64_bit_prediction(), drflac__mm_packs_interleaved_epi32(), drflac_read_pcm_frames_s16__decode_independent_stereo(), drflac_read_pcm_frames_s16__decode_independent_stereo__neon(), drflac_read_pcm_frames_s16__decode_independent_stereo__reference(), drflac_read_pcm_frames_s16__decode_independent_stereo__scalar(), drflac_read_pcm_frames_s16__decode_independent_stereo__sse2(), drflac_read_pcm_frames_s16__decode_left_side() (+25 more)

### Community 14 - "FLAC CPU Feature Detection"
Cohesion: 0.09
Nodes (30): drflac_bool32 drflac__has_neon(), drflac_bool32 drflac__is_little_endian(), drflac_bool32 drflac__is_lzcnt_supported(), drflac_bool32 drflac_ogg__is_capture_pattern(), drflac_bool32 drflac__read_and_decode_block_header(), drflac_bool32 drflac__reload_l1_cache_from_l2(), drflac__decode_block_header(), drflac__decode_samples_with_residual__rice__sse41_64() (+22 more)

### Community 16 - "Master Remediation Roadmap & Plans"
Cohesion: 0.07
Nodes (30): User Bug Note: Window Resize Layout Corruption, Double-Tap Fit-to-Screen & Menu Switch Lag, 00 Master Roadmap & Arsitektur Perbaikan Total TSRossa macOS, 01 Plan: Build System Mandiri, Keamanan Updater & Test Harness, Automated C++ & Swift Test Harness (run_tests.sh & TestSuite), VersionComparator (SemVer Pre-Release & macOS Asset Filter), 02 Plan: Native C++ Audio Engine, 64-Bit DSP, TPDF Dither & CoreAudio, SampleProcessor Dual-Path (32-Bit Int Direct Pass & 64-Bit Double DSP with Per-Sample Gain Ramp), SampleRateMatcher & BufferProfileHelper (Integer Family Rate Matching & 64/160/192ms Profiles) (+22 more)

### Community 17 - "MacAudioDecoder C++ Stream Wrapper"
Cohesion: 0.08
Nodes (15): MacAudioDecoder, bitsPerSample, channels, filePath, format, isWavInit, lastOpenError, pFlac (+7 more)

### Community 18 - "Swift AudioEngine & Gapless Control"
Cohesion: 0.14
Nodes (4): AudioEngine, .telemetrySnapshot, HardwareTelemetrySnapshot, OutputDevice

### Community 19 - "Telemetry Logger & Event RingBuffer"
Cohesion: 0.12
Nodes (7): AppLogger, LogEntry, .formattedTimestamp, TelemetryModalView, .body, .filteredLogs, .snap

### Community 20 - "Track Filtering & Theme State"
Cohesion: 0.15
Nodes (4): .filteredTracks, FuzzySearchHelper, ArtworkExtractor, TsrossaBrandLogo

### Community 21 - "Liquid Glass Optical Modifiers"
Cohesion: 0.13
Nodes (13): ConcentricSquircle, LiquidGlassModifier, .shadowRadius, .shadowYOffset, .swiftUIMaterial, LiquidGlassRole, bentoCard, floatingPillBar (+5 more)

### Community 23 - "Playlist Modal & Workspace Routing"
Cohesion: 0.15
Nodes (9): .body, LiquidGlassTheme, .error, CreatePlaylistModalSheet, .body, FloatingTopToolbarView, LiquidTransportBarView, SidebarView (+1 more)

### Community 24 - "Secure GitHub Release Auto-Updater"
Cohesion: 0.15
Nodes (4): UpdateManager, .currentVersion, .repoName, .repoOwner

### Community 25 - "WAV Format Tag & Byte-Swap"
Cohesion: 0.13
Nodes (16): drwav_bool32 drwav__is_compressed_format_tag(), drwav_bool32 drwav__on_tell_memory(), drwav_bool32 drwav__on_tell_stdio(), drwav__bswap_f32(), drwav__bswap_s24(), drwav__bswap_samples_f32(), drwav__bswap_samples_s24(), drwav__bswap_samples_s64() (+8 more)

### Community 26 - "User Playlist Persistence & Folders"
Cohesion: 0.20
Nodes (5): .activePlaylist, .makeFolderAsPlaylist, .selectedPlaylist, UserPlaylist, playlists

### Community 28 - "Library View & Filter Chips"
Cohesion: 0.24
Nodes (4): LibraryView, .body, .formattedTotalDuration, .headerTitle

### Community 29 - "HSL Color Space & Glassmorphic"
Cohesion: 0.22
Nodes (8): GlassmorphicSurface, HSL, hslToColor(), rgbToHsl(), TsrossaBrandIcon, .body, TsrossaScaleButtonStyle, View

### Community 30 - "WAV 16-Bit PCM Conversion"
Cohesion: 0.18
Nodes (16): drwav_alaw_to_s16(), drwav_f32_to_s16(), drwav_f64_to_s16(), drwav__ieee_to_s16(), drwav_mulaw_to_s16(), drwav__pcm_to_s16(), drwav_s24_to_s16(), drwav_s32_to_s16() (+8 more)

### Community 32 - "Swift Unit Test Harness"
Cohesion: 0.24
Nodes (4): TestFailure, .description, TestSuite, runUpdateVersionTests()

### Community 33 - "WAV 32-Bit PCM Conversion"
Cohesion: 0.21
Nodes (15): drwav_alaw_to_s32(), drwav_f32_to_s32(), drwav_f64_to_s32(), drwav__ieee_to_s32(), drwav_mulaw_to_s32(), drwav__pcm_to_s32(), drwav_s16_to_s32(), drwav_s24_to_s32() (+7 more)

### Community 34 - "WCAG Contrast & Hue Clustering"
Cohesion: 0.24
Nodes (5): ColorContrastHelper, RGBColor, .hexString, .nsColor, runColorAndThemeTests()

### Community 35 - "Fuzzy Search & Multi-Field Sort"
Cohesion: 0.14
Nodes (14): SortField, album, artist, dateAdded, dateModified, duration, fileSize, .id (+6 more)

### Community 36 - "Synchronized LRC Lyrics Engine"
Cohesion: 0.22
Nodes (4): AVFoundation, LyricLine, .isSynced, LyricsManager

### Community 38 - "Workspace Tab Navigation"
Cohesion: 0.21
Nodes (6): AppTab, library, nowPlaying, playlist, settings, .body

### Community 39 - "Shuffle Deck & Repeat State"
Cohesion: 0.32
Nodes (4): PlaybackQueueState, .currentTrack, QueueHelper, runSortAndFuzzySearchTests()

### Community 40 - "macOS System Frameworks & Crypto"
Cohesion: 0.27
Nodes (3): AppKit, CommonCrypto, Foundation

### Community 41 - "Reverse Engineering Parity Audits"
Cohesion: 0.26
Nodes (11): 00 Ringkasan Eksekutif & Matriks Paritas Porting, Matriks Paritas Fitur Android vs macOS, 01 Audit Audio Engine, DSP, & CoreAudio HAL, 02 Audit Memori, Streaming, Gapless Playback, & Konkurensi, 03 Audit Metadata, Database Library, Sorting, & Search, 04 Audit UI/UX, Telemetri Palsu, & Integrasi macOS, 05 Audit Build System, Keamanan Updater, & Testing, 06 Audit UI/UX Themes, Swift/SwiftUI, Apple HIG, & Liquid Glass (+3 more)

### Community 42 - "Playlist Codable Serialization"
Cohesion: 0.18
Nodes (10): CodingKeys, filterKeyword, id, isAutoFolder, isPinned, name, trackPaths, CodingKeys (+2 more)

### Community 43 - "Isolated Playback Scrubber View"
Cohesion: 0.25
Nodes (3): PlaybackScrubberView, .body, ScrubberState

### Community 44 - "CoreAudio Output Device Enumeration"
Cohesion: 0.18
Nodes (8): DeviceInfo, id, isDefault, isUsb, name, nominalSampleRate, supportedRates, uid

### Community 45 - "Window Resize & Zoom Bug Audit"
Cohesion: 0.22
Nodes (5): 07 Audit Bug Window Resize, Double-Tap Fit-to-Screen, & Menu Lag, LiquidGlassComponents (Glass Modifiers & Ambient Background), MainAppView, .currentCanvasMode, .isEffectiveDark

### Community 46 - "macOS App Bundle & Icon Build"
Cohesion: 0.27
Nodes (8): build_mac.sh script, install_mac.sh script, ic_launcher.png (TSRossa Launcher Icon), Crimson Rose Blossom & Floating Petal Motif, macOS AppIcon.icns & Dock Application Icon Asset, Dark Obsidian-Violet Circular Icon Canvas, Stylized White Thorny Eighth Note Glyph, TSRossa Brand Visual Identity (Music & Rose Emblem)

### Community 48 - "Dynamic Theme Color Bridge"
Cohesion: 0.29
Nodes (5): Color, .activeTheme, InteractiveCircleButton, .body, TsrossaCircleButtonStyle

### Community 51 - "Queue & Lyrics Split Panel"
Cohesion: 0.29
Nodes (4): QueueLyricsView, .body, .lyricsView, .queueList

### Community 57 - "SplitMix64 TPDF Dither Processor"
Cohesion: 0.43
Nodes (3): SampleProcessor, SplitMix64Dither, m_seed

### Community 60 - "AudioEngine System Frameworks"
Cohesion: 0.40
Nodes (4): Combine, CoreAudio, IOKit.pwr_mgt, MediaPlayer

### Community 61 - "Diagnostic Log Severity Levels"
Cohesion: 0.40
Nodes (5): LogLevel, error, info, notice, warning

### Community 62 - "Adaptive Foreground Launcher Icon"
Cohesion: 0.67
Nodes (4): Transparent Foreground Icon Layer, Stylized Musical Note and Crimson Rose Emblem, TSRossa Foreground Adaptive Launcher Icon Asset, TSRossa Application Launcher Icon

## Knowledge Gaps
- **181 isolated node(s):** `CommonCrypto`, `audio`, `dsp`, `hardware`, `library` (+176 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 360 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **21 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CoreAudioSink` connect `CoreAudio HAL & Bit-Perfect DSP` to `Reverse Engineering Parity Audits`, `C++ Concurrency & AudioToolbox`, `CoreAudio Output Device Enumeration`, `Ring Buffer & Decode Thread`, `MacAudioDecoder C++ Stream Wrapper`, `C++ Telemetry JSON Serialization`, `CoreAudio Real-Time Render Callback`, `SplitMix64 TPDF Dither Processor`?**
  _High betweenness centrality (0.547) - this node is a cross-community bridge._
- **Are the 9 inferred relationships involving `AppViewModel` (e.g. with `.body` and `.formattedTotalDuration`) actually correct?**
  _`AppViewModel` has 9 INFERRED edges - model-reasoned connections that need verification._
- **What connects `CommonCrypto`, `audio`, `dsp` to the rest of the system?**
  _181 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `CoreAudio HAL & Bit-Perfect DSP` be split into smaller, more focused modules?**
  _Cohesion score 0.025656565656565655 - nodes in this community are weakly interconnected._
- **Why does `02 Audit Memori, Streaming, Gapless Playback, & Konkurensi` connect `Reverse Engineering Parity Audits` to `CoreAudio HAL & Bit-Perfect DSP`, `Swift AudioEngine & Gapless Control`?**
  _High betweenness centrality (0.390) - this node is a cross-community bridge._
- **Should `FLAC Frame Seeking & NEON` be split into smaller, more focused modules?**
  _Cohesion score 0.0958904109589041 - nodes in this community are weakly interconnected._
- **Why does `AudioEngine` connect `Swift AudioEngine & Gapless Control` to `Binary Audio Tag & Art Cache`, `Reverse Engineering Parity Audits`, `Isolated Playback Scrubber View`, `Play Queue & Autoplay Radio`, `Playback Transport & Track Advance`, `AudioEngine System Frameworks`?**
  _High betweenness centrality (0.337) - this node is a cross-community bridge._