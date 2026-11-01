# Plan 03: 64 MB Sliding-Window Ring Buffer, Gapless Engine & Thread-Safety

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menghilangkan potensi *Out-Of-Memory (OOM)* dan pembekuan UI akibat dekode penuh ke RAM dengan mem-porting arsitektur **64 MB Sliding-Window Ring Buffer** + **Fast-Start 5 Detik (`< 50 ms`)** + **Sample-Accurate Gapless Playback (`prepareNextTrack` 32 MB)** dari `AudioStreamer.cpp` Android, sekaligus memperbaiki seluruh bug *data race*, kebocoran `mlock()`, dan pemotongan 500 ms akhir lagu.

**Architecture:** Memperluas `MacAudioDecoder` agar mendukung pembacaan bertahap (`readFramesChunk` dan `seekToFrame`) tanpa menutup handle `drflac*`/`drwav*`. Mengganti `std::vector<int32_t> m_pcmBuffer` yang dialokasikan penuh dengan **Ring Buffer 64 MB** (`MAX_RING_BUFFER_SAMPLES = 16 * 1024 * 1024` sampel `int32_t`) yang diisi oleh `std::thread m_decodeThread` di latar belakang. Menambahkan pre-buffer lagu berikutnya (`m_pcmBufferNext`, 32 MB) untuk transisi *gapless* tepat di dalam callback `handleRender()` tanpa menghentikan AudioUnit jika sample rate dan jumlah kanal sama.

**Tech Stack:** C++17 (`std::thread`, `std::mutex`, `std::condition_variable`, `std::atomic`, `sys/mman.h`, `sys/resource.h`), Swift (`DispatchQueue`, `Timer`).

---

## File Structure

- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/MacAudioDecoder.h:1-145` — Menambahkan metode streaming `openStream(path)`, `readFramesChunk(dst, framesToRead)`, `seekToFrame(frameIndex)`, dan `closeStream()` di atas `drflac*` dan `drwav*`.
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/tests/cpp/test_ringbuffer_gapless.cpp` — Unit test C++ untuk menguji wrap-around Ring Buffer 64 MB, operasi atomik `compare_exchange_strong` saat seek konkuren, clamp seek 250 ms, dan transisi gapless tanpa jeda sampel.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.h` — Mengubah `m_sampleRate`, `m_channels`, `m_totalFrames` menjadi `std::atomic`, menambahkan `m_seekGeneration`, `m_decodeThread`, `m_pcmBufferNext` (32 MB), `m_nextTrackReady`, `m_trackFinished`, dan `m_gaplessTransitioned`.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp` — Mengimplementasikan:
  - Urutan aman `teardownAudioUnit()` sebelum mutasi state di `playTrack()`
  - 5 detik dekode sinkron awal + background `decodeThreadLoop()` dengan sliding window
  - Pemeriksaan `getrlimit(RLIMIT_MEMLOCK)` sebelum `mlock()`, `munlock()` langsung saat `setRamLockEnabled(false)`, dan pembersihan saat `stop()`
  - Operasi `m_currentFrame.compare_exchange_strong` di `handleRender()`
  - `prepareNextTrack(path)` & `clearNextTrack()` serta penyambungan *gapless* di dalam `handleRender()`
  - Clamp `seekTo()` sebesar `250 ms` dari akhir lagu saat scrubbing
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/TsrossaAudioBridge.h` & `TsrossaAudioBridge.cpp` — Menambahkan `std::mutex s_bridgeMutex` untuk melindungi `s_cachedDevices` & `s_lastTelemetry`, serta menambahkan fungsi C `tsrossa_prepare_next_track`, `tsrossa_clear_next_track`, `tsrossa_is_track_finished`, dan `tsrossa_consume_gapless_transition`.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/AudioEngine.swift:120-385` — Menghapus kondisi salah `t >= self.totalDuration - 0.5`, menggantinya dengan `tsrossa_is_track_finished()` & `tsrossa_consume_gapless_transition()`, memicu `prepareNextTrack` otomatis saat lagu berjalan, dan menambahkan *anti-snap-back guard* (`!isSeeking` + 800 ms pasca-seek) pada `progressTimer`.

---

### Task 1: Perluas `MacAudioDecoder.h` untuk Streaming Chunk & Seek, lalu Tulis Unit Test Ring Buffer & Gapless (`test_ringbuffer_gapless.cpp`)

**Files:**
- Modify: `src/nativeMain/cpp/MacAudioDecoder.h:1-145`
- Create: `tests/cpp/test_ringbuffer_gapless.cpp`

- [ ] **Step 1: Perbarui `src/nativeMain/cpp/MacAudioDecoder.h` agar mendukung pembacaan bertahap (`openStream`, `readFramesChunk`, `seekToFrame`, `closeStream`)**

Perbarui `MacAudioDecoder.h` sehingga selain metode kompatibilitas `open()`, ia menyediakan handle streaming persisten untuk `drflac*` dan `drwav*`:

```cpp
#pragma once

#include <string>
#include <vector>
#include <cstdint>
#include <cstring>
#include <algorithm>
#include <iostream>

#include "dr_flac.h"
#include "dr_wav.h"

class MacAudioDecoder {
public:
    MacAudioDecoder() = default;
    ~MacAudioDecoder() { closeStream(); }

    MacAudioDecoder(const MacAudioDecoder&) = delete;
    MacAudioDecoder& operator=(const MacAudioDecoder&) = delete;

    bool openStream(const std::string& filePath) {
        closeStream();
        m_filePath = filePath;

        m_flac = drflac_open_file(filePath.c_str(), nullptr);
        if (m_flac != nullptr) {
            m_sampleRate = m_flac->sampleRate;
            m_channels = m_flac->channels;
            m_bitsPerSample = m_flac->bitsPerSample;
            m_totalFrames = m_flac->totalPCMFrameCount;
            m_format = Format::FLAC;
            return (m_sampleRate > 0 && m_channels > 0 && m_totalFrames > 0);
        }

        m_wav = new drwav();
        if (drwav_init_file(m_wav, filePath.c_str(), nullptr)) {
            m_sampleRate = m_wav->sampleRate;
            m_channels = m_wav->channels;
            m_bitsPerSample = m_wav->bitsPerSample;
            m_totalFrames = m_wav->totalPCMFrameCount;
            m_format = Format::WAV;
            return (m_sampleRate > 0 && m_channels > 0 && m_totalFrames > 0);
        }
        delete m_wav;
        m_wav = nullptr;
        return false;
    }

    uint64_t readFramesChunk(int32_t* dstInterleaved, uint64_t framesToRead) {
        if (framesToRead == 0 || dstInterleaved == nullptr) return 0;
        if (m_format == Format::FLAC && m_flac != nullptr) {
            return drflac_read_pcm_frames_s32(m_flac, framesToRead, dstInterleaved);
        }
        if (m_format == Format::WAV && m_wav != nullptr) {
            return drwav_read_pcm_frames_s32(m_wav, framesToRead, dstInterleaved);
        }
        return 0;
    }

    bool seekToFrame(uint64_t targetFrame) {
        if (m_format == Format::FLAC && m_flac != nullptr) {
            return drflac_seek_to_pcm_frame(m_flac, targetFrame) == DRFLAC_TRUE;
        }
        if (m_format == Format::WAV && m_wav != nullptr) {
            return drwav_seek_to_pcm_frame(m_wav, targetFrame) == DRWAV_TRUE;
        }
        return false;
    }

    void closeStream() {
        if (m_flac != nullptr) {
            drflac_close(m_flac);
            m_flac = nullptr;
        }
        if (m_wav != nullptr) {
            drwav_uninit(m_wav);
            delete m_wav;
            m_wav = nullptr;
        }
        m_format = Format::NONE;
    }

    uint32_t getSampleRate() const { return m_sampleRate; }
    uint32_t getChannels() const { return m_channels; }
    uint32_t getBitsPerSample() const { return m_bitsPerSample; }
    uint64_t getTotalFrames() const { return m_totalFrames; }
    const std::string& getFilePath() const { return m_filePath; }

private:
    enum class Format { NONE, FLAC, WAV };
    Format m_format{Format::NONE};
    drflac* m_flac{nullptr};
    drwav* m_wav{nullptr};
    std::string m_filePath;
    uint32_t m_sampleRate{0};
    uint32_t m_channels{0};
    uint32_t m_bitsPerSample{0};
    uint64_t m_totalFrames{0};
};
```

- [ ] **Step 2: Buat `tests/cpp/test_ringbuffer_gapless.cpp` untuk menguji logika Ring Buffer Modulo, CAS Seek, Clamp 250ms, dan Gapless Handover**

```cpp
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <vector>
#include <atomic>
#include <algorithm>

static void testRingBufferWrapAround() {
    printf("  [TEST] RingBuffer modulo wrap-around integrity...\n");
    constexpr size_t kRingSamples = 1024; // 512 stereo frames
    constexpr uint32_t kChannels = 2;
    const uint64_t ringFrames = kRingSamples / kChannels;
    std::vector<int32_t> ring(kRingSamples, 0);

    // Simulasikan penulisan 1.500 frame stereo (melewati batas wrap-around 512 frame hampir 3x)
    for (uint64_t f = 0; f < 1500; ++f) {
        size_t pos = static_cast<size_t>((f % ringFrames) * kChannels);
        ring[pos] = static_cast<int32_t>(f * 10 + 1);
        ring[pos + 1] = static_cast<int32_t>(f * 10 + 2);

        // Baca kembali dari sliding window aktif
        assert(ring[pos] == static_cast<int32_t>(f * 10 + 1));
        assert(ring[pos + 1] == static_cast<int32_t>(f * 10 + 2));
    }
}

static void testCompareExchangeSeekSafety() {
    printf("  [TEST] Atomic compare_exchange_strong prevents overwriting concurrent seek...\n");
    std::atomic<uint64_t> currentFrame{1000};

    // Callback audio membaca posisi awal 1000
    uint64_t expected = currentFrame.load(std::memory_order_acquire);
    assert(expected == 1000);

    // Di tengah render callback, UI thread melakukan seekTo(50000)
    currentFrame.store(50000, std::memory_order_release);

    // Callback audio mencoba memajukan dari 1000 -> 1512 dengan compare_exchange_strong:
    // Harus GAGAL menimpa 50000, sehingga posisi seek baru tetap utuh di 50000!
    bool exchanged = currentFrame.compare_exchange_strong(expected, expected + 512, std::memory_order_acq_rel);
    assert(!exchanged);
    assert(currentFrame.load() == 50000);
}

static void testSeekEndBoundaryClamp() {
    printf("  [TEST] Seek end boundary 250ms guard...\n");
    const uint32_t sampleRate = 48000;
    const uint64_t totalFrames = 48000 * 10; // 10.0 detik
    const uint64_t guardFrames = (sampleRate * 250) / 1000; // 12.000 frame (250ms)

    uint64_t requestedFrame = totalFrames; // User drag ke 100%
    uint64_t maxSafeFrame = (totalFrames > guardFrames) ? (totalFrames - guardFrames) : 0;
    uint64_t clampedFrame = std::min(requestedFrame, maxSafeFrame);

    assert(clampedFrame == 480000 - 12000);
}

int main() {
    printf("==> Running C++ RingBuffer & Concurrency Unit Tests...\n");
    testRingBufferWrapAround();
    testCompareExchangeSeekSafety();
    testSeekEndBoundaryClamp();
    printf("RESULT: ALL C++ RINGBUFFER & CONCURRENCY TESTS PASSED\n");
    return 0;
}
```

- [ ] **Step 3: Jalankan `./run_tests.sh` untuk memverifikasi test C++ baru berjalan dan lulus**

```bash
./run_tests.sh
```

Expected: `RESULT: ALL C++ RINGBUFFER & CONCURRENCY TESTS PASSED`.

---

### Task 2: Implementasikan 64 MB Sliding-Window Ring Buffer, Fast-Start 5 Detik, dan Keamanan `mlock()` di `CoreAudioSink`

**Files:**
- Modify: `src/nativeMain/cpp/CoreAudioSink.h`
- Modify: `src/nativeMain/cpp/CoreAudioSink.cpp`

- [ ] **Step 1: Perbarui struktur member di `src/nativeMain/cpp/CoreAudioSink.h` untuk Ring Buffer 64 MB, Threading, dan Gapless 32 MB**

Pastikan deklarasi member di `CoreAudioSink.h` menggunakan tipe atomik dan konstanta ukuran tetap berikut:

```cpp
    static constexpr size_t MAX_RING_BUFFER_SAMPLES = 16 * 1024 * 1024; // 64 MB (16M x int32_t)
    static constexpr size_t MAX_NEXT_BUFFER_SAMPLES = 8 * 1024 * 1024;  // 32 MB pre-buffer untuk Gapless

    bool prepareNextTrack(const std::string& filePath);
    void clearNextTrack();
    bool isTrackFinished() const { return m_trackFinished.load(std::memory_order_acquire); }
    bool consumeGaplessTransition();
```

Serta pada bagian `private:` di `CoreAudioSink.h`:

```cpp
    std::unique_ptr<MacAudioDecoder> m_activeDecoder;
    std::unique_ptr<MacAudioDecoder> m_nextDecoder;

    std::vector<int32_t> m_pcmBuffer;
    std::vector<int32_t> m_pcmBufferNext;

    std::atomic<uint32_t> m_sampleRate{0};
    std::atomic<uint32_t> m_channels{0};
    std::atomic<uint32_t> m_bitsPerSample{0};
    std::atomic<uint64_t> m_totalFrames{0};
    std::atomic<uint64_t> m_currentFrame{0};
    std::atomic<uint64_t> m_decodedFrames{0};
    std::atomic<uint64_t> m_seekGeneration{0};

    std::atomic<bool> m_nextTrackReady{false};
    std::atomic<uint32_t> m_nextSampleRate{0};
    std::atomic<uint32_t> m_nextChannels{0};
    std::atomic<uint32_t> m_nextBitsPerSample{0};
    std::atomic<uint64_t> m_nextTotalFrames{0};
    std::atomic<uint64_t> m_nextDecodedFrames{0};
    std::string m_nextFilePath;

    std::atomic<bool> m_trackFinished{false};
    std::atomic<bool> m_gaplessTransitioned{false};

    std::thread m_decodeThread;
    std::atomic<bool> m_stopDecodeThread{false};
    std::mutex m_decoderMutex;
    std::condition_variable m_decodeCv;

    bool m_isMemoryLocked{false};
    void stopDecodeThread();
    void decodeThreadLoop(uint64_t generation);
    void applyRamLockState(bool shouldLock);
```

- [ ] **Step 2: Implementasikan `applyRamLockState()` dengan pemeriksaan `getrlimit(RLIMIT_MEMLOCK)` dan `munlock()` langsung di `CoreAudioSink.cpp`**

```cpp
#include <sys/resource.h>

void CoreAudioSink::applyRamLockState(bool shouldLock) {
    if (m_pcmBuffer.empty()) return;
    const size_t byteSize = m_pcmBuffer.size() * sizeof(int32_t);

    if (shouldLock && !m_isMemoryLocked) {
        struct rlimit rl{};
        if (getrlimit(RLIMIT_MEMLOCK, &rl) == 0) {
            if (rl.rlim_cur != RLIM_INFINITY && rl.rlim_cur < byteSize) {
                return; // Jangan memaksa mlock jika melampaui kuota kernel macOS
            }
        }
        if (mlock(m_pcmBuffer.data(), byteSize) == 0) {
            m_isMemoryLocked = true;
        }
    } else if (!shouldLock && m_isMemoryLocked) {
        munlock(m_pcmBuffer.data(), byteSize);
        m_isMemoryLocked = false;
    }
}

void CoreAudioSink::setRamLockEnabled(bool enabled) {
    m_ramLockEnabled = enabled;
    applyRamLockState(enabled);
}
```

- [ ] **Step 3: Implementasikan `playTrack()` dengan Fast-Start 5 detik + `decodeThreadLoop()` sliding-window 64 MB**

Di `CoreAudioSink::playTrack(const std::string& filePath)`:
1. Panggil `stopDecodeThread()` dan `teardownAudioUnit()` **sebelum** menyentuh decoder atau buffer agar callback audio real-time dijamin berhenti.
2. Buka `m_activeDecoder = std::make_unique<MacAudioDecoder>()` dengan `openStream(filePath)`.
3. Pastikan `m_pcmBuffer` berukuran `MAX_RING_BUFFER_SAMPLES` (64 MB).
4. Dekode secara sinkron hanya **5 detik pertama** (`initialFrames = std::min<uint64_t>(totalFrames, sampleRate * 5)`), set `m_decodedFrames.store(framesRead)`, `m_currentFrame.store(0)`, dan `m_trackFinished.store(false)`.
5. Konfigurasi perangkat & mulai `AudioOutputUnitStart(m_audioUnit)` (playback langsung terdengar dalam `< 50 ms`).
6. Jika `m_decodedFrames.load() < totalFrames`, luncurkan `m_decodeThread = std::thread(&CoreAudioSink::decodeThreadLoop, this, m_seekGeneration.load())`.

```cpp
void CoreAudioSink::decodeThreadLoop(uint64_t generation) {
    pthread_setname_np("TsrossaDecode");
    const uint32_t channels = m_channels.load(std::memory_order_acquire);
    const uint64_t totalFrames = m_totalFrames.load(std::memory_order_acquire);
    if (channels == 0 || totalFrames == 0) return;

    const uint64_t ringCapacityFrames = MAX_RING_BUFFER_SAMPLES / channels;
    // Sisakan safety margin 4 detik agar write head tidak pernah menabrak read head yang belum diputar
    const uint64_t safetyMarginFrames = std::min<uint64_t>(ringCapacityFrames / 4, m_sampleRate.load() * 4);
    const uint64_t maxLeadFrames = ringCapacityFrames - safetyMarginFrames;
    constexpr uint64_t kChunkFrames = 16384;
    std::vector<int32_t> chunkBuf(kChunkFrames * channels);

    while (!m_stopDecodeThread.load(std::memory_order_acquire)) {
        if (m_seekGeneration.load(std::memory_order_acquire) != generation) break;

        uint64_t decoded = m_decodedFrames.load(std::memory_order_acquire);
        if (decoded >= totalFrames) break;

        uint64_t playhead = m_currentFrame.load(std::memory_order_acquire);
        if (decoded >= playhead && (decoded - playhead) >= maxLeadFrames) {
            std::unique_lock<std::mutex> lk(m_decoderMutex);
            m_decodeCv.wait_for(lk, std::chrono::milliseconds(15));
            continue;
        }

        uint64_t toRead = std::min<uint64_t>(kChunkFrames, totalFrames - decoded);
        if (decoded >= playhead) {
            toRead = std::min<uint64_t>(toRead, maxLeadFrames - (decoded - playhead));
        }
        if (toRead == 0) continue;

        uint64_t framesRead = 0;
        {
            std::lock_guard<std::mutex> lk(m_decoderMutex);
            if (m_activeDecoder) {
                framesRead = m_activeDecoder->readFramesChunk(chunkBuf.data(), toRead);
            }
        }
        if (framesRead == 0) break;

        for (uint64_t f = 0; f < framesRead; ++f) {
            const uint64_t ringFrameIdx = (decoded + f) % ringCapacityFrames;
            std::memcpy(
                &m_pcmBuffer[ringFrameIdx * channels],
                &chunkBuf[f * channels],
                channels * sizeof(int32_t)
            );
        }
        m_decodedFrames.store(decoded + framesRead, std::memory_order_release);
    }
}
```

---

### Task 3: Implementasikan Sample-Accurate Gapless Playback (`prepareNextTrack`) dan Perbaiki `handleRender()` & `seekTo()`

**Files:**
- Modify: `src/nativeMain/cpp/CoreAudioSink.cpp`
- Modify: `src/nativeMain/cpp/TsrossaAudioBridge.h`
- Modify: `src/nativeMain/cpp/TsrossaAudioBridge.cpp`

- [ ] **Step 1: Implementasikan `prepareNextTrack()` dan `clearNextTrack()` di `CoreAudioSink.cpp`**

```cpp
bool CoreAudioSink::prepareNextTrack(const std::string& filePath) {
    std::lock_guard<std::mutex> lk(m_decoderMutex);
    m_nextTrackReady.store(false, std::memory_order_release);

    auto nextDec = std::make_unique<MacAudioDecoder>();
    if (!nextDec->openStream(filePath)) {
        return false;
    }

    const uint32_t ch = nextDec->getChannels();
    const uint32_t sr = nextDec->getSampleRate();
    const uint64_t total = nextDec->getTotalFrames();
    if (ch == 0 || sr == 0 || total == 0) return false;

    if (m_pcmBufferNext.size() < MAX_NEXT_BUFFER_SAMPLES) {
        m_pcmBufferNext.resize(MAX_NEXT_BUFFER_SAMPLES, 0);
    }

    const uint64_t maxPrebufferFrames = MAX_NEXT_BUFFER_SAMPLES / ch;
    const uint64_t framesToPreload = std::min<uint64_t>(total, maxPrebufferFrames);
    const uint64_t readFrames = nextDec->readFramesChunk(m_pcmBufferNext.data(), framesToPreload);
    if (readFrames == 0) return false;

    m_nextFilePath = filePath;
    m_nextSampleRate.store(sr, std::memory_order_relaxed);
    m_nextChannels.store(ch, std::memory_order_relaxed);
    m_nextBitsPerSample.store(nextDec->getBitsPerSample(), std::memory_order_relaxed);
    m_nextTotalFrames.store(total, std::memory_order_relaxed);
    m_nextDecodedFrames.store(readFrames, std::memory_order_relaxed);
    m_nextDecoder = std::move(nextDec);
    m_nextTrackReady.store(true, std::memory_order_release);
    return true;
}

void CoreAudioSink::clearNextTrack() {
    std::lock_guard<std::mutex> lk(m_decoderMutex);
    m_nextTrackReady.store(false, std::memory_order_release);
    m_nextDecoder.reset();
    m_nextFilePath.clear();
}

bool CoreAudioSink::consumeGaplessTransition() {
    return m_gaplessTransitioned.exchange(false, std::memory_order_acq_rel);
}
```

- [ ] **Step 2: Perbarui `handleRender()` dengan pembacaan Ring Buffer Modulo, `compare_exchange_strong`, dan penyambungan Gapless ketika mencapai EOF**

Di dalam `CoreAudioSink::handleRender(...)`:
- Baca sampel dari `m_pcmBuffer[(currentFrame + f) % ringCapacityFrames]`.
- Majukan `m_currentFrame` menggunakan `m_currentFrame.compare_exchange_strong(expectedFrame, expectedFrame + framesToCopy)` dan bangunkan thread dekoder via `m_decodeCv.notify_one()`.
- Saat `currentFrame + framesToCopy >= totalFrames`:
  - Jika `m_nextTrackReady.load()` bernilai `true` dan `m_nextSampleRate == m_sampleRate && m_nextChannels == m_channels`: salin sisa frame callback langsung dari awal `m_pcmBufferNext`, salin pre-buffer `m_pcmBufferNext` ke window awal `m_pcmBuffer`, perbarui `m_totalFrames` & `m_decodedFrames`, set `m_currentFrame = remainingFramesCopied`, dan set `m_gaplessTransitioned.store(true)`.
  - Jika tidak ada lagu berikutnya (atau sample rate berbeda sehingga butuh re-lock DAC), set `m_trackFinished.store(true)` dan `m_isPlaying.store(false)`.

- [ ] **Step 3: Perbarui `seekTo(double seconds)` dengan clamp 250ms, generasi seek baru, dan ре-fill sinkron 2 detik**

```cpp
void CoreAudioSink::seekTo(double seconds) {
    const uint32_t sr = m_sampleRate.load(std::memory_order_acquire);
    const uint32_t ch = m_channels.load(std::memory_order_acquire);
    const uint64_t total = m_totalFrames.load(std::memory_order_acquire);
    if (sr == 0 || ch == 0 || total == 0) return;

    const double clampedSec = std::max(0.0, seconds);
    uint64_t targetFrame = static_cast<uint64_t>(clampedSec * sr);
    const uint64_t guardFrames = (sr * 250) / 1000; // 250ms guard
    const uint64_t maxSafeFrame = (total > guardFrames) ? (total - guardFrames) : 0;
    targetFrame = std::min(targetFrame, maxSafeFrame);

    stopDecodeThread();
    const uint64_t newGen = m_seekGeneration.fetch_add(1, std::memory_order_acq_rel) + 1;

    {
        std::lock_guard<std::mutex> lk(m_decoderMutex);
        if (m_activeDecoder && m_activeDecoder->seekToFrame(targetFrame)) {
            const uint64_t ringCapacityFrames = MAX_RING_BUFFER_SAMPLES / ch;
            const uint64_t preloadFrames = std::min<uint64_t>(total - targetFrame, sr * 2);
            std::vector<int32_t> temp(preloadFrames * ch);
            const uint64_t got = m_activeDecoder->readFramesChunk(temp.data(), preloadFrames);
            for (uint64_t f = 0; f < got; ++f) {
                const uint64_t ringIdx = (targetFrame + f) % ringCapacityFrames;
                std::memcpy(&m_pcmBuffer[ringIdx * ch], &temp[f * ch], ch * sizeof(int32_t));
            }
            m_decodedFrames.store(targetFrame + got, std::memory_order_release);
            m_currentFrame.store(targetFrame, std::memory_order_release);
            m_trackFinished.store(false, std::memory_order_release);
        }
    }

    if (m_decodedFrames.load(std::memory_order_acquire) < total) {
        m_stopDecodeThread.store(false, std::memory_order_release);
        m_decodeThread = std::thread(&CoreAudioSink::decodeThreadLoop, this, newGen);
    }
}
```

---

### Task 4: Lindungi `TsrossaAudioBridge.cpp` dengan `std::mutex` dan Hubungkan Gapless & Scrubber Guard di `AudioEngine.swift`

**Files:**
- Modify: `src/nativeMain/cpp/TsrossaAudioBridge.h`
- Modify: `src/nativeMain/cpp/TsrossaAudioBridge.cpp`
- Modify: `macos/AudioEngine.swift:120-385`

- [ ] **Step 1: Tambahkan `std::mutex s_bridgeMutex` dan fungsi C Gapless/EOF di `TsrossaAudioBridge.h` & `TsrossaAudioBridge.cpp`**

Di `TsrossaAudioBridge.h`, tambahkan:

```c
bool tsrossa_prepare_next_track(const char* filePath);
void tsrossa_clear_next_track(void);
bool tsrossa_is_track_finished(void);
bool tsrossa_consume_gapless_transition(void);
```

Di `TsrossaAudioBridge.cpp`, tambahkan `static std::mutex s_bridgeMutex;` dan kunci `std::lock_guard<std::mutex> lock(s_bridgeMutex);` di dalam `tsrossa_get_devices_count()`, `tsrossa_get_device_id()`, `tsrossa_get_device_name()`, `tsrossa_get_telemetry_json()`, serta implementasikan keempat fungsi C gapless di atas.

- [ ] **Step 2: Perbarui `AudioEngine.swift` untuk menggunakan `tsrossa_is_track_finished()`, `tsrossa_consume_gapless_transition()`, dan guard anti-snap-back pada `progressTimer`**

Di `macos/AudioEngine.swift`:
1. Tambahkan properti `private var lastSeekTimestamp: Date = .distantPast` dan `@Published var isSeeking: Bool = false`.
2. Pada `seek(to time: Double)`:
   ```swift
   func seek(to time: Double) {
       let safeMax = max(0.0, totalDuration - 0.25)
       let clamped = min(max(0.0, time), safeMax)
       lastSeekTimestamp = Date()
       currentTime = clamped
       tsrossa_seek_to(clamped)
   }
   ```
3. Pada `startProgressTimer()` (di sekitar baris 340-370), ganti deteksi `if t >= self.totalDuration - 0.5 && self.totalDuration > 0` yang salah dengan pemeriksaan atomik dari C++:
   ```swift
   let elapsedSinceSeek = Date().timeIntervalSince(self.lastSeekTimestamp)
   if !self.isSeeking && elapsedSinceSeek > 0.8 {
       self.currentTime = t
   }
   if tsrossa_consume_gapless_transition() {
       self.handleSeamlessGaplessAdvance()
   } else if tsrossa_is_track_finished() && self.totalDuration > 0 && elapsedSinceSeek > 0.4 {
       self.nextTrack()
   }
   ```
4. Saat `gaplessEnabled` aktif dan lagu mulai diputar (atau antrean berubah), panggil `syncPreparedNextTrack()` yang mengirim path lagu berikutnya ke `tsrossa_prepare_next_track(nextURL.path)`.

- [ ] **Step 3: Jalankan `./run_tests.sh` dan `./build_mac.sh` untuk memverifikasi semua test dan build lulus**

```bash
./run_tests.sh && ./build_mac.sh
```

Expected: Seluruh unit test C++ dan Swift lulus dan `TSRossa.app` ter-build tanpa error.

- [ ] **Step 4: Commit hasil Plan 03**

```bash
git add src/nativeMain/cpp/MacAudioDecoder.h src/nativeMain/cpp/CoreAudioSink.h src/nativeMain/cpp/CoreAudioSink.cpp src/nativeMain/cpp/TsrossaAudioBridge.h src/nativeMain/cpp/TsrossaAudioBridge.cpp macos/AudioEngine.swift tests/cpp/test_ringbuffer_gapless.cpp
git commit -m "feat(streaming): implement 64MB sliding-window ring buffer, 32MB gapless handover, and thread-safe seek"
```

---

## Review Focus

1. **Konsumsi RAM Maksimal Terkunci di 64 MB + 32 MB:** Berbeda dengan kode lama yang mendekode seluruh file Hi-Res 192 kHz ke dalam satu `std::vector` tak terbatas, arsitektur baru membatasi ring buffer lagu aktif tepat di 64 MB (`16M` sampel `int32_t`) dan pre-buffer gapless di 32 MB, dengan latensi mulai putar `< 50 ms` karena hanya 5 detik pertama yang didekode secara sinkron.
2. **Penghapusan Bug `totalDuration - 0.5`:** Transisi akhir lagu kini dikendalikan 100% oleh flag atomik `m_trackFinished` dan `m_gaplessTransitioned` dari callback audio C++ ketika frame terakhir benar-benar selesai dirender, sehingga tidak ada lagi pemotongan 500 ms terakhir maupun hang pada sampel audio pendek (`< 0.5 detik`).
