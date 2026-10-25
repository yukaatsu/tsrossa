# Graph Report - tsrossa-ujicoba  (2026-10-25)

## Corpus Check
- 107 files · ~175,561 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 30 file(s) not represented in the graph (top: .xml 20, (none) 4, .properties 3)

## Summary
- 1918 nodes · 6467 edges · 75 communities (48 shown, 27 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 241 edges (avg confidence: 0.85)
- Token cost: 1,200 input · 800 output

## Community Hubs (Navigation)
- FLAC SIMD & Architecture Features
- Audio Glyphs & Icons
- C++ USB DAC & Engine State
- Audio Glyphs & Icons
- Audio Modals & Bottom Sheets
- FLAC SIMD & Architecture Features
- WAV File Initialization
- FLAC SIMD & Architecture Features
- Audio Modals & Bottom Sheets
- Audio Modals & Bottom Sheets
- FLAC Bitstream & Seeking
- WAV File Initialization
- WAV File Initialization
- USB Audio Timing & Architecture
- C++ USB DAC & Engine State
- USB Audio Timing & Architecture
- Compose Animation Controls
- Audio Metadata Extraction
- Playlist Management UI
- Compose UI Module 19
- WAV Memory & Seek Operations
- Compose Animation Controls
- Compose UI Module 22
- USB Audio Timing & Architecture
- Compose UI Module 24
- Compose UI Module 25
- Playlist Management UI
- Audio Subsystem 27
- C++ USB DAC & Engine State
- Audio Metadata Extraction
- FLAC File & Track Management
- WAV Memory & Seek Operations
- Compose UI Module 32
- WAV Format Conversion
- Audio Modals & Bottom Sheets
- Audio Subsystem 35
- Audio Subsystem 36
- Audio Subsystem 38
- Audio Modals & Bottom Sheets
- Hardware Probe & Telemetry
- Playlist Management UI
- C++ USB DAC & Engine State
- Audio Subsystem 43
- FLAC SIMD & Architecture Features
- Audio Subsystem 45
- Compose UI Module 46
- Audio Subsystem 47
- Compose UI Module 48
- C++ USB DAC & Engine State
- Audio Subsystem 50
- Audio Subsystem 51
- Audio Subsystem 52
- USB Audio Timing & Architecture
- WAV File Initialization
- C++ USB DAC & Engine State
- WAV File Initialization
- Audio Metadata Extraction
- Playlist Management UI
- C++ Audio Utilities & Timing
- Audio Subsystem 60
- Audio Subsystem 61
- Audio Subsystem 62
- Audio Engine JNI Controller
- C++ Audio Utilities & Timing
- Hardware Probe & Telemetry
- C++ USB DAC & Engine State
- C++ USB DAC & Engine State
- Audio Subsystem 70
- Audio Subsystem 72

## God Nodes (most connected - your core abstractions)
1. `AudioEngineState` - 102 edges
2. `m3OnSurface()` - 67 edges
3. `m3OnSurfaceVariant()` - 61 edges
4. `AudioEngine` - 57 edges
5. `KewApp()` - 55 edges
6. `EvaIconCanvas()` - 45 edges
7. `TactileBox()` - 43 edges
8. `LibraryView()` - 35 edges
9. `PlaylistView()` - 35 edges
10. `CyberBottomSheet()` - 33 edges

## Surprising Connections (you probably didn't know these)
- `Hardware Oscilloscope & Telemetry Bridge` --conceptually_related_to--> `Bresenham Isochronous Payload Accumulator`  [INFERRED]
  tools/README.md → planning.md
- `play_audio_internal()` --calls--> `get_file_basename()`  [INFERRED]
  app/src/main/cpp/AudioStreamer.cpp → app/src/main/cpp/AudioCommon.h
- `init_usb_dac_internal()` --calls--> `record_usb_diag()`  [INFERRED]
  app/src/main/cpp/UsbDacController.cpp → app/src/main/cpp/AudioEngineState.cpp
- `close_usb_dac_internal()` --calls--> `stop_and_join_decode_thread()`  [INFERRED]
  app/src/main/cpp/UsbDacController.cpp → app/src/main/cpp/AudioStreamer.cpp
- `close_usb_dac_internal()` --calls--> `stop_and_join_iso_thread()`  [INFERRED]
  app/src/main/cpp/UsbDacController.cpp → app/src/main/cpp/AudioStreamer.cpp

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **USB Audio Timing Remediation Pipeline** — audit_report_uac2_timing_hardware_failure, audit_report_usbdevfs_get_speed_incompatibility, planning_uac2_speed_resolution_plan, planning_multi_tier_speed_heuristic, planning_bresenham_payload_accumulator [INFERRED 0.85]

## Communities (75 total, 27 thin omitted)

### Community 0 - "FLAC SIMD & Architecture Features"
Cohesion: 0.08
Nodes (95): drflac_bool32 drflac__has_neon(), drflac_bool32 drflac__is_little_endian(), drflac_bool32 drflac__is_lzcnt_supported(), drflac_bool32 drflac_ogg__is_capture_pattern(), drflac_bool32 drflac__reload_l1_cache_from_l2(), drflac_bool32 drflac__use_64_bit_prediction(), drflac_int32 drflac__calculate_prediction_32(), drflac_int32 drflac__calculate_prediction_64() (+87 more)

### Community 1 - "Audio Glyphs & Icons"
Cohesion: 0.07
Nodes (63): EvaIconCanvas(), GlyphAdd(), GlyphAlbum(), GlyphArrowBack(), GlyphBitPerfect(), GlyphBolt(), GlyphCheck(), GlyphChevronDown() (+55 more)

### Community 2 - "C++ USB DAC & Engine State"
Cohesion: 0.02
Nodes (91): AudioEngineState, acInterfaceNum, activeIsoTransfers, bitPerfectDirectPassActive, callbackObj, cancelDecoding, channels, channelsNext (+83 more)

### Community 3 - "Audio Glyphs & Icons"
Cohesion: 0.04
Nodes (7): detectMqa(), DetailedTrackMetadata, formatDuration(), formatFileSize(), MetadataDetailRow(), TrackDetailsModalSheet(), WallpaperBlurHelper

### Community 4 - "Audio Modals & Bottom Sheets"
Cohesion: 0.05
Nodes (6): NestedScrollConnection, glassMorphicContainer(), modalBackdropBlur(), ModalBlurContainer(), RegisterModalBlur(), AppBackgroundContainer()

### Community 5 - "FLAC SIMD & Architecture Features"
Cohesion: 0.10
Nodes (58): drflac_bool32 drflac__read_rice_parts(), drflac_bool32 drflac__read_rice_parts_x1(), drflac_bool32 drflac__read_uint32(), drflac_bool32 drflac__seek_past_next_set_bit(), drflac_bool32 drflac__seek_rice_parts(), drflac_bool32 drflac_seek_to_pcm_frame(), drflac__decode_flac_frame(), drflac__decode_flac_frame_and_seek_forward_by_pcm_frames() (+50 more)

### Community 6 - "WAV File Initialization"
Cohesion: 0.09
Nodes (56): drwav_bool32 drwav_init_ex(), drwav_bool32 drwav_init_file(), drwav_bool32 drwav_init_file_ex(), drwav_bool32 drwav_init_file_ex_w(), drwav_bool32 drwav_init_file__internal_FILE(), drwav_bool32 drwav_init_file_w(), drwav_bool32 drwav_init_file_with_metadata(), drwav_bool32 drwav_init_file_with_metadata_w() (+48 more)

### Community 7 - "FLAC SIMD & Architecture Features"
Cohesion: 0.05
Nodes (36): drflac_bool32 drflac_has_sse2(), drflac_bool32 drflac_has_sse41(), drflac_bool32 drflac__read_and_decode_block_header(), drflac__cpuid(), drflac__decode_block_header(), drflac__decode_samples_with_residual__rice__sse41_64(), drflac__free_default(), drflac__init_cpu_caps() (+28 more)

### Community 8 - "Audio Modals & Bottom Sheets"
Cohesion: 0.07
Nodes (31): CyberBottomSheet(), ModalCloseButton(), AudioSpecsAndLogsModalSheet(), SpecRow(), ConfirmDeleteDialog(), CreatePlaylistDialog(), FilterSelectionModalSheet(), FolderPickerDialog() (+23 more)

### Community 10 - "FLAC Bitstream & Seeking"
Cohesion: 0.10
Nodes (41): drflac_bool32 drflac_next_cuesheet_track(), drflac_close(), drflac_fopen(), drflac_free(), drflac__free_from_callbacks(), drflac_init_cuesheet_track_iterator(), drflac__init_from_info(), drflac__init_private() (+33 more)

### Community 11 - "WAV File Initialization"
Cohesion: 0.08
Nodes (48): drwav_allocation_callbacks drwav_copy_allocation_callbacks_or_defaults(), drwav_bool32 drwav__metadata_validate_smpl_chunk(), drwav_bool32 drwav__on_seek(), drwav_bool32 drwav__on_seek_memory(), drwav_bool32 drwav__on_seek_memory_write(), drwav_bool32 drwav__on_seek_stdio(), drwav_bool32 drwav_seek_to_first_pcm_frame(), drwav_buffer_reader_ptr() (+40 more)

### Community 12 - "WAV File Initialization"
Cohesion: 0.09
Nodes (47): drwav_alaw_to_f32(), drwav_bool32 drwav__chunk_matches(), drwav_bool32 drwav_fourcc_equal(), drwav_bool32 drwav_guid_equal(), drwav_bool32 drwav_init__internal(), drwav__bswap_s24(), drwav__bswap_samples_s24(), drwav_bytes_to_f32() (+39 more)

### Community 14 - "C++ USB DAC & Engine State"
Cohesion: 0.09
Nodes (7): fast_uniform_rand(), get_file_basename(), get_time_ms(), LIBUSB_CALL iso_callback(), libusb_transfer, libusb_interface_descriptor, resolve_effective_usb_speed()

### Community 15 - "USB Audio Timing & Architecture"
Cohesion: 0.15
Nodes (35): json_escape(), clear_next_track_internal(), get_position_internal(), is_warming_up_internal(), pause_audio_internal(), resume_audio_internal(), Java_com_yuka_musicplayer_audio_AudioEngine_clearNextTrack(), Java_com_yuka_musicplayer_audio_AudioEngine_getClaimedInterfaces() (+27 more)

### Community 16 - "Compose Animation Controls"
Cohesion: 0.13
Nodes (22): RetroActionItem(), RetroButton(), RetroCircularButton(), SelectPlaylistDialog(), WarmupHudDialog(), AnimatedM3Palette, blendWithWhite(), computeRawM3SurfaceBase() (+14 more)

### Community 17 - "Audio Metadata Extraction"
Cohesion: 0.13
Nodes (6): AudioFileTags, AudioTagReader, LyricLine, LyricsHelper, ParsedLyrics, LyricsHelperTest

### Community 18 - "Playlist Management UI"
Cohesion: 0.18
Nodes (5): KewApp(), rememberIsKeyboardOpen(), Playlist, PlaylistStorageHelper, PlaylistStorageHelperTest

### Community 20 - "WAV Memory & Seek Operations"
Cohesion: 0.12
Nodes (28): drwav_alaw_to_s32(), drwav_bool32 drwav_seek_to_pcm_frame(), drwav__bswap_samples(), drwav__bswap_samples_s32(), drwav_calculate_padding_size(), drwav__chunk_padding_size_riff(), drwav__chunk_padding_size_w64(), drwav_f32_to_s32() (+20 more)

### Community 21 - "Compose Animation Controls"
Cohesion: 0.11
Nodes (9): ViewState, LIBRARY, PLAYLIST, SETTINGS, TRACK, LibraryFastScroller(), AppHeader(), BottomNavigationBar() (+1 more)

### Community 22 - "Compose UI Module 22"
Cohesion: 0.16
Nodes (4): ScannedMusicItem, IncrementalScanResult, MusicScannerHelper, MusicScannerHelperTest

### Community 23 - "USB Audio Timing & Architecture"
Cohesion: 0.18
Nodes (4): sortScannedItemsByField(), DacHardwareTimingTest, DacTimingMetrics, LibrarySortTest

### Community 24 - "Compose UI Module 24"
Cohesion: 0.17
Nodes (3): ColorContrastHelper, SwatchData, ColorContrastHelperTest

### Community 25 - "Compose UI Module 25"
Cohesion: 0.12
Nodes (3): ThemeTest, AlbumArtThumbnailCacheTest, SamsungAudioEffectHelperTest

### Community 27 - "Audio Subsystem 27"
Cohesion: 0.17
Nodes (3): AlbumArtThumbnailCache, BitmapLruCache, SimpleLruCache

### Community 28 - "C++ USB DAC & Engine State"
Cohesion: 0.12
Nodes (16): record_usb_diag(), cancel_warmup_internal(), clean_garbage_internal(), play_audio_internal(), playAudio_cleanup_on_negotiation_failure(), prepare_next_track_internal(), start_iso_streaming_thread(), stop_and_join_decode_thread() (+8 more)

### Community 30 - "FLAC File & Track Management"
Cohesion: 0.10
Nodes (14): AudioCodec, FLAC, UNKNOWN, WAV, AudioDecoder, bitsPerSample, channels, codec (+6 more)

### Community 31 - "WAV Memory & Seek Operations"
Cohesion: 0.13
Nodes (18): drwav_bool32 drwav__is_compressed_format_tag(), drwav_bool32 drwav__on_tell_memory(), drwav_bool32 drwav__on_tell_stdio(), drwav__bswap_f32(), drwav__bswap_samples_f32(), drwav__bswap_samples_s64(), drwav_int16 drwav__bswap_s16(), drwav_int32 drwav__bswap_s32() (+10 more)

### Community 33 - "WAV Format Conversion"
Cohesion: 0.16
Nodes (19): drwav_alaw_to_s16(), drwav__bswap_samples_s16(), drwav_f32_to_s16(), drwav_f64_to_s16(), drwav__ieee_to_s16(), drwav_mulaw_to_s16(), drwav__pcm_to_s16(), drwav_s24_to_s16() (+11 more)

### Community 34 - "Audio Modals & Bottom Sheets"
Cohesion: 0.13
Nodes (5): PlaybackSource, LIBRARY, PLAYLIST, sortLibraryFiles(), AppModalActionHandler

### Community 35 - "Audio Subsystem 35"
Cohesion: 0.17
Nodes (10): Checking, Downloading, DownloadState, Error, Idle, ReadyToInstall, ReleaseInfo, UpdateAvailable (+2 more)

### Community 36 - "Audio Subsystem 36"
Cohesion: 0.17
Nodes (7): AppPermissionState, rememberAppPermissionState(), checkDndPermission(), checkNotificationPermission(), checkStoragePermission(), discoverAvailableMusicRoots(), StorageVolumeRoot

### Community 40 - "Hardware Probe & Telemetry"
Cohesion: 0.22
Nodes (6): check_system_instruments(), main(), query_adb_battery(), render_ascii_waveform(), simulate_dac_transient(), simulate_vbus_droop()

### Community 42 - "C++ USB DAC & Engine State"
Cohesion: 0.15
Nodes (12): ControlRequest, altSetting, eventArg, interfaceNum, sampleRate, type, volume, RefusedTrackInfo (+4 more)

### Community 43 - "Audio Subsystem 43"
Cohesion: 0.15
Nodes (13): SortField, ALBUM, ALBUM_ARTIST, ARTIST, COMPOSER, DATE_ADDED, DATE_MODIFIED, DURATION (+5 more)

### Community 44 - "FLAC SIMD & Architecture Features"
Cohesion: 0.26
Nodes (9): drflac__decode_samples_with_residual__rice__neon_32(), drflac__decode_samples_with_residual__rice__neon_64(), drflac__valignrq_s32_1(), drflac__valignrq_u32_1(), drflac__vhaddq_s32(), drflac__vhaddq_s64(), drflac__vnotq_s32(), drflac__vnotq_u32() (+1 more)

### Community 46 - "Compose UI Module 46"
Cohesion: 0.27
Nodes (6): AppLogsSection(), generateDiagnosticReport(), LogItem(), LogMultilineItem(), LogSectionHeader(), SystemLogsPanel()

### Community 47 - "Audio Subsystem 47"
Cohesion: 0.21
Nodes (8): AltSettingInfo, LibrarySortMode, DATE_ASC, DATE_DESC, NAME_ASC, NAME_DESC, sortScannedMusicItems(), TrackInfo

### Community 49 - "C++ USB DAC & Engine State"
Cohesion: 0.18
Nodes (9): calculate_scaled_volume(), Java_com_yuka_musicplayer_audio_AudioEngine_closeUsbDac(), Java_com_yuka_musicplayer_audio_AudioEngine_initUsbDac(), Java_com_yuka_musicplayer_audio_AudioEngine_setSoftwareVolume(), close_usb_dac_internal(), control_thread_func(), get_subframe_size(), get_uac1_sample_rates() (+1 more)

### Community 51 - "Audio Subsystem 51"
Cohesion: 0.24
Nodes (7): RepeatMode, ALL, OFF, ONE, NextTrackResult, PrevTrackResult, QueueHelper

### Community 53 - "USB Audio Timing & Architecture"
Cohesion: 0.22
Nodes (11): DAC Hardware FIFO Overrun & PLL Desync, Audit Report Stub, 8x Playback Rate Overrun Mechanism, USB Audio Class 2.0 Timing & Hardware Failure, Kernel USBDEVFS_GET_SPEED Incompatibility, Bresenham Isochronous Payload Accumulator, Multi-Tier USB Bus Speed Heuristic, Planning Document Stub (+3 more)

### Community 54 - "WAV File Initialization"
Cohesion: 0.27
Nodes (9): drwav__free_from_callbacks(), drwav_int16* drwav__read_pcm_frames_and_close_s16(drwav* pWav, unsigned int* channels, unsigned int* sampleRate, drwav_uint64* totalFrameCount)(), drwav_int32* drwav__read_pcm_frames_and_close_s32(drwav* pWav, unsigned int* channels, unsigned int* sampleRate, drwav_uint64* totalFrameCount)(), drwav__malloc_from_callbacks(), drwav_open_file_and_read_pcm_frames_f32(), drwav_open_memory_and_read_pcm_frames_f32(), drwav__read_pcm_frames_and_close_f32(), drwav_result drwav_fopen() (+1 more)

### Community 55 - "C++ USB DAC & Engine State"
Cohesion: 0.22
Nodes (9): AltSettingInfo, altsetting, ep_feedback, ep_out, interface_num, is_async, max_packet_size, packets_per_sec (+1 more)

### Community 56 - "WAV File Initialization"
Cohesion: 0.33
Nodes (6): drwav_bool32 drwav_init(), drwav_bool32 drwav_preinit(), drwav_bool32 drwav__seek_forward(), drwav_bool32 drwav__seek_from_start(), drwav__on_read(), drwav_open_and_read_pcm_frames_f32()

### Community 58 - "Playlist Management UI"
Cohesion: 0.25
Nodes (8): SettingsSubPage, ABOUT, APPEARANCE, PLAYBACK_AUDIO, PLAYLIST, ROOT, SETUP, UPDATES

### Community 64 - "C++ Audio Utilities & Timing"
Cohesion: 0.40
Nodes (4): ApiMutexLock, func, lock, tid

### Community 65 - "Hardware Probe & Telemetry"
Cohesion: 0.60
Nodes (3): RefusedTrackEntry, DacTelemetryState, rememberDacTelemetry()

### Community 66 - "C++ USB DAC & Engine State"
Cohesion: 0.40
Nodes (5): ControlRequestType, REQ_EVENT_TRACK_FINISHED, REQ_EVENT_TRACK_TRANSITION, REQ_EVENT_USB_STALL_FAULT, REQ_SET_VOLUME

### Community 70 - "Audio Subsystem 70"
Cohesion: 0.67
Nodes (3): SortOrder, ASCENDING, DESCENDING

## Knowledge Gaps
- **167 isolated node(s):** `func`, `tid`, `UNKNOWN`, `FLAC`, `WAV` (+162 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 422 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **27 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `drflac_wfopen()` connect `FLAC Bitstream & Seeking` to `Audio Glyphs & Icons`, `FLAC SIMD & Architecture Features`?**
  _High betweenness centrality (0.192) - this node is a cross-community bridge._
- **What connects `func`, `tid`, `UNKNOWN` to the rest of the system?**
  _167 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `FLAC SIMD & Architecture Features` be split into smaller, more focused modules?**
  _Cohesion score 0.08337825696316262 - nodes in this community are weakly interconnected._
- **Why does `drflac_fopen()` connect `FLAC Bitstream & Seeking` to `Audio Glyphs & Icons`, `FLAC SIMD & Architecture Features`?**
  _High betweenness centrality (0.187) - this node is a cross-community bridge._
- **Should `Audio Glyphs & Icons` be split into smaller, more focused modules?**
  _Cohesion score 0.07281553398058252 - nodes in this community are weakly interconnected._
- **Why does `AudioEngineState` connect `C++ USB DAC & Engine State` to `C++ USB DAC & Engine State`, `C++ USB DAC & Engine State`, `C++ USB DAC & Engine State`?**
  _High betweenness centrality (0.108) - this node is a cross-community bridge._
- **Should `C++ USB DAC & Engine State` be split into smaller, more focused modules?**
  _Cohesion score 0.021052631578947368 - nodes in this community are weakly interconnected._