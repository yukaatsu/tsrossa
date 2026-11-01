# Plan 02: Native C++ Audio Engine, 64-Bit DSP, TPDF Dither & CoreAudio

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mengangkat kualitas pemrosesan sinyal audio C++ di macOS agar mencapai standar *audiophile bit-perfect* yang setara dan melampaui `AudioStreamer.cpp` di Android: jalur ganda **32-Bit Signed Integer Direct Pass** + **64-Bit `double` DSP**, **SplitMix64 TPDF Dither** per kanal, pembulatan `std::llrint()`, interpolasi gain per-sample (*de-clicking ramp*), kurva volume **Studio Taper**, kontrol **Hardware Volume CoreAudio + Residual Gain**, validasi `supportedRates` dengan fallback kelipatan integer, pelaporan **Hog Mode** jujur, ukuran buffer 64/160/192 ms, dan **DAC Hardware Warm-up**.

**Architecture:** Mengekstrak seluruh matematika DSP murni ke dalam header `src/nativeMain/cpp/DspUtils.h` agar dapat diuji secara deterministik lewat unit test C++ (`tests/cpp/test_dsp_utils.cpp`) tanpa membutuhkan perangkat keras DAC terpasang. Selanjutnya mengintegrasikan `DspUtils.h` ke dalam `CoreAudioSink.h`, `CoreAudioSink.cpp`, dan `TsrossaAudioBridge.h`/`.cpp`.

**Tech Stack:** C++17, `CoreAudio`, `AudioToolbox`, `AudioUnit`, `<cmath>`, `<atomic>`, `<cstdint>`.

---

## File Structure

- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/DspUtils.h` — Header C++ murni berisi:
  - `StudioTaper::sliderToLinearGain(double slider)` & `sliderToDecibels(double slider)` (`dB(v) = -48.0 * (1.0 - v) * sqrt(1.0 - v)`)
  - `StudioTaper::computeResidualLinearGain(double targetDb, double hardwareAssignedDb)`
  - `SplitMix64Dither`: Generator PRNG SplitMix64 2 kanal independen dengan distribusi segitiga `[-1.0, +1.0]` (`fast_tpdf_dither(ch)`)
  - `SampleProcessor::processFrameDoubleToInt32(...)` & `processFrameDoubleToFloat32(...)` dengan interpolasi `stepGain` per frame dan `std::llrint()`
  - `SampleRateMatcher::selectBestSupportedRate(uint32_t targetRate, const std::vector<double>& supportedRates)`
  - `BufferProfileHelper::computeTargetBufferFrames(int profileIndex, double sampleRate)`
- **Create:** `/Users/yuka/Documents/Projects/tsrossa-macos/tests/cpp/test_dsp_utils.cpp` — Pengujian unit C++ untuk memverifikasi akurasi matematika DSP, TPDF dither, ramp gain, dan pemilihan sample rate.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.h` — Menambahkan state `double m_currentGain`, `double m_targetGain`, `SplitMix64Dither m_dither`, `bool m_streamIsInteger`, `double m_actualHardwareSampleRate`, `int m_bufferProfile`, `std::atomic<uint32_t> m_warmupFramesRemaining`, serta metode Hardware Volume CoreAudio.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/CoreAudioSink.cpp` — Mengimplementasikan negosiasi format integer/float pada `setupAudioUnit()`, pemrosesan `handleRender()` 64-bit `double` + TPDF dither / direct integer `memcpy`, validasi `setDeviceSampleRate()` terhadap `supportedRates` + pembacaan balik nominal sample rate, konfigurasi `kAudioDevicePropertyBufferFrameSize`, kontrol `setHardwareVolume()`, dan `triggerWarmup()`.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/src/nativeMain/cpp/TsrossaAudioBridge.h` & `TsrossaAudioBridge.cpp` — Mengekspos API C untuk profil buffer, hardware volume, DAC warmup, dan status Hog Mode/sample rate aktual.
- **Modify:** `/Users/yuka/Documents/Projects/tsrossa-macos/macos/AudioEngine.swift` — Menghubungkan slider volume Studio Taper, toggle DAC Warm-up, dan profil ukuran buffer ke C Bridge.

---

### Task 1: Buat Unit Test C++ (`test_dsp_utils.cpp`) dan Header Matematika DSP (`DspUtils.h`)

**Files:**
- Create: `tests/cpp/test_dsp_utils.cpp`
- Create: `src/nativeMain/cpp/DspUtils.h`

- [ ] **Step 1: Tulis unit test C++ `tests/cpp/test_dsp_utils.cpp` untuk menguji Studio Taper, TPDF Dither, Gain Ramping, dan Rate Matcher**

```cpp
#include <cassert>
#include <cmath>
#include <cstdio>
#include <vector>
#include "DspUtils.h"

static void testStudioTaper() {
    printf("  [TEST] StudioTaper curve...\n");
    assert(TsrossaDsp::StudioTaper::sliderToLinearGain(0.0) == 0.0);
    assert(std::abs(TsrossaDsp::StudioTaper::sliderToLinearGain(1.0) - 1.0) < 1e-12);

    // Pada slider = 0.75: 1 - v = 0.25, sqrt(0.25) = 0.5 -> dB = -48 * 0.25 * 0.5 = -6.0 dB (~0.501187)
    double db75 = TsrossaDsp::StudioTaper::sliderToDecibels(0.75);
    assert(std::abs(db75 - (-6.0)) < 1e-9);
    double lin75 = TsrossaDsp::StudioTaper::sliderToLinearGain(0.75);
    assert(std::abs(lin75 - std::pow(10.0, -6.0 / 20.0)) < 1e-9);

    // Residual gain: jika target -10 dB dan hardware menerapkan -8.5 dB, residual harus -1.5 dB
    double resLin = TsrossaDsp::StudioTaper::computeResidualLinearGain(-10.0, -8.5);
    assert(std::abs(resLin - std::pow(10.0, -1.5 / 20.0)) < 1e-9);
}

static void testSplitMix64TpdfDither() {
    printf("  [TEST] SplitMix64 TPDF Dither bounds & decorrelation...\n");
    TsrossaDsp::SplitMix64Dither dither;
    double minVal = 0.0, maxVal = 0.0, sumL = 0.0, sumR = 0.0;
    int diffCount = 0;

    for (int i = 0; i < 10000; ++i) {
        double dL = dither.nextTpdf(0);
        double dR = dither.nextTpdf(1);
        assert(dL >= -1.0 && dL <= 1.0);
        assert(dR >= -1.0 && dR <= 1.0);
        if (dL < minVal) minVal = dL;
        if (dL > maxVal) maxVal = dL;
        sumL += dL;
        sumR += dR;
        if (std::abs(dL - dR) > 1e-9) diffCount++;
    }
    // Rata-rata distribusi segitiga harus mendekati 0.0 dan kanal L/R harus terdekorulasi
    assert(std::abs(sumL / 10000.0) < 0.05);
    assert(std::abs(sumR / 10000.0) < 0.05);
    assert(minVal < -0.7 && maxVal > 0.7);
    assert(diffCount > 9900);
}

static void testPerSampleGainRampAndBitExactPass() {
    printf("  [TEST] 64-bit per-sample linear gain ramp & bit-exact unity pass...\n");
    TsrossaDsp::SplitMix64Dither dither;
    const int32_t src[8] = {
        100000000, -100000000,
        100000000, -100000000,
        100000000, -100000000,
        100000000, -100000000
    };
    int32_t dstInt[8] = {0};

    // 1. Unity gain (1.0 -> 1.0) harus 100% bit-exact tanpa modifikasi bit
    double currentGain = 1.0;
    TsrossaDsp::SampleProcessor::processInt32Buffer(
        src, dstInt, 4, 2, currentGain, 1.0, true, dither
    );
    for (int i = 0; i < 8; ++i) {
        assert(dstInt[i] == src[i]);
    }

    // 2. Ramp dari 0.0 ke 1.0 selama 4 frame harus naik secara monotonik tanpa loncatan kasar
    currentGain = 0.0;
    TsrossaDsp::SampleProcessor::processInt32Buffer(
        src, dstInt, 4, 2, currentGain, 1.0, false, dither
    );
    assert(std::abs(currentGain - 1.0) < 1e-12);
    assert(dstInt[0] < dstInt[2]);
    assert(dstInt[2] < dstInt[4]);
    assert(dstInt[4] < dstInt[6]);
}

static void testSampleRateMatcher() {
    printf("  [TEST] SampleRateMatcher integer multiple & closest fallback...\n");
    std::vector<double> dacRates = {44100.0, 48000.0, 88200.0, 96000.0};

    // Exact match
    assert(TsrossaDsp::SampleRateMatcher::selectBestSupportedRate(96000, dacRates) == 96000.0);
    // 176.4 kHz pada DAC maks 96 kHz -> harus memilih 88.2 kHz (kelipatan integer 44.1k family)
    assert(TsrossaDsp::SampleRateMatcher::selectBestSupportedRate(176400, dacRates) == 88200.0);
    // 192 kHz pada DAC maks 96 kHz -> harus memilih 96.0 kHz (kelipatan integer 48k family)
    assert(TsrossaDsp::SampleRateMatcher::selectBestSupportedRate(192000, dacRates) == 96000.0);
}

int main() {
    printf("==> Running C++ DSP Utils Unit Tests...\n");
    testStudioTaper();
    testSplitMix64TpdfDither();
    testPerSampleGainRampAndBitExactPass();
    testSampleRateMatcher();
    printf("RESULT: ALL C++ DSP TESTS PASSED\n");
    return 0;
}
```

- [ ] **Step 2: Jalankan `./run_tests.sh` untuk memverifikasi kegagalan karena `DspUtils.h` belum ada**

```bash
./run_tests.sh
```

Expected: Gagal kompilasi `fatal error: 'DspUtils.h' file not found`.

- [ ] **Step 3: Implementasikan `src/nativeMain/cpp/DspUtils.h`**

```cpp
#pragma once

#include <cstdint>
#include <cmath>
#include <cstring>
#include <vector>
#include <algorithm>

namespace TsrossaDsp {

struct StudioTaper {
    static inline double sliderToDecibels(double slider) {
        if (slider <= 0.0001) return -120.0;
        if (slider >= 0.9999) return 0.0;
        const double inv = 1.0 - slider;
        return -48.0 * inv * std::sqrt(inv);
    }

    static inline double sliderToLinearGain(double slider) {
        if (slider <= 0.0001) return 0.0;
        if (slider >= 0.9999) return 1.0;
        const double db = sliderToDecibels(slider);
        return std::pow(10.0, db / 20.0);
    }

    static inline double computeResidualLinearGain(double targetDb, double hardwareAssignedDb) {
        const double residualDb = std::clamp(targetDb - hardwareAssignedDb, -60.0, 0.0);
        if (std::abs(residualDb) < 0.01) return 1.0;
        return std::pow(10.0, residualDb / 20.0);
    }
};

class SplitMix64Dither {
public:
    SplitMix64Dither() {
        m_seed[0] = 0x123456789ABCDEF0ULL;
        m_seed[1] = 0xFEDCBA9876543210ULL;
    }

    inline double nextTpdf(int channel) {
        uint64_t& s = m_seed[channel & 1];
        s += 0x9E3779B97F4A7C15ULL;
        uint64_t z1 = s;
        z1 = (z1 ^ (z1 >> 30)) * 0xBF58476D1CE4E5B9ULL;
        z1 = (z1 ^ (z1 >> 27)) * 0x94D049BB133111EBULL;
        z1 = z1 ^ (z1 >> 31);

        s += 0x9E3779B97F4A7C15ULL;
        uint64_t z2 = s;
        z2 = (z2 ^ (z2 >> 30)) * 0xBF58476D1CE4E5B9ULL;
        z2 = (z2 ^ (z2 >> 27)) * 0x94D049BB133111EBULL;
        z2 = z2 ^ (z2 >> 31);

        constexpr double kNorm = 1.0 / 9007199254740992.0; // 2^53
        const double r1 = static_cast<double>(z1 >> 11) * kNorm;
        const double r2 = static_cast<double>(z2 >> 11) * kNorm;
        return r1 - r2; // [-1.0, +1.0] TPDF
    }

private:
    uint64_t m_seed[2];
};

struct SampleProcessor {
    static inline void processInt32Buffer(
        const int32_t* src,
        int32_t* dst,
        uint32_t frames,
        uint32_t channels,
        double& currentGain,
        double targetGain,
        bool applyDither,
        SplitMix64Dither& dither
    ) {
        if (frames == 0 || channels == 0) return;

        const bool isRamping = std::abs(targetGain - currentGain) > 1e-7;
        // True Bit-Perfect Integer Direct Pass saat unity gain & tidak sedang ramping
        if (!isRamping && targetGain >= 0.99999) {
            currentGain = 1.0;
            if (src != dst) {
                std::memcpy(dst, src, static_cast<size_t>(frames) * channels * sizeof(int32_t));
            }
            return;
        }

        const double stepGain = isRamping ? (targetGain - currentGain) / static_cast<double>(frames) : 0.0;
        // Skala dither di LSB ke-24 (256.0 pada representasi 32-bit signed int Q31)
        constexpr double kDitherLsbScale = 256.0;
        constexpr double kMaxInt32 = 2147483647.0;
        constexpr double kMinInt32 = -2147483648.0;

        for (uint32_t f = 0; f < frames; ++f) {
            if (isRamping) {
                currentGain += stepGain;
            } else {
                currentGain = targetGain;
            }
            const double g = currentGain;
            for (uint32_t ch = 0; ch < channels; ++ch) {
                const size_t idx = static_cast<size_t>(f) * channels + ch;
                double sample = static_cast<double>(src[idx]) * g;
                if (applyDither && g > 0.0) {
                    sample += dither.nextTpdf(static_cast<int>(ch)) * kDitherLsbScale;
                }
                sample = std::clamp(sample, kMinInt32, kMaxInt32);
                dst[idx] = static_cast<int32_t>(std::llrint(sample));
            }
        }
        currentGain = targetGain;
    }

    static inline void processFloat32Buffer(
        const int32_t* src,
        float* dst,
        uint32_t frames,
        uint32_t channels,
        double& currentGain,
        double targetGain,
        bool applyDither,
        SplitMix64Dither& dither
    ) {
        if (frames == 0 || channels == 0) return;

        const bool isRamping = std::abs(targetGain - currentGain) > 1e-7;
        const double stepGain = isRamping ? (targetGain - currentGain) / static_cast<double>(frames) : 0.0;
        constexpr double kInvInt32 = 1.0 / 2147483648.0;
        constexpr double kFloat24Lsb = 1.0 / 8388608.0; // 2^-23

        for (uint32_t f = 0; f < frames; ++f) {
            if (isRamping) {
                currentGain += stepGain;
            } else {
                currentGain = targetGain;
            }
            const double g = currentGain;
            for (uint32_t ch = 0; ch < channels; ++ch) {
                const size_t idx = static_cast<size_t>(f) * channels + ch;
                double normalized = (static_cast<double>(src[idx]) * kInvInt32) * g;
                if (applyDither && g > 0.0 && g < 0.99999) {
                    normalized += dither.nextTpdf(static_cast<int>(ch)) * kFloat24Lsb;
                }
                normalized = std::clamp(normalized, -1.0, 1.0);
                dst[idx] = static_cast<float>(normalized);
            }
        }
        currentGain = targetGain;
    }
};

struct SampleRateMatcher {
    static inline double selectBestSupportedRate(uint32_t targetRate, const std::vector<double>& supportedRates) {
        if (supportedRates.empty()) return static_cast<double>(targetRate);

        const double target = static_cast<double>(targetRate);
        // 1. Cek kecocokan persis
        for (double r : supportedRates) {
            if (std::abs(r - target) < 0.5) return r;
        }

        // 2. Cari rate tertinggi yang sekeluarga (kelipatan integer, misal 176.4k -> 88.2k -> 44.1k)
        double bestFamilyRate = 0.0;
        for (double r : supportedRates) {
            const uint32_t rInt = static_cast<uint32_t>(std::llround(r));
            if (rInt == 0) continue;
            if ((targetRate % rInt == 0 || rInt % targetRate == 0) && r > bestFamilyRate) {
                bestFamilyRate = r;
            }
        }
        if (bestFamilyRate > 0.0) return bestFamilyRate;

        // 3. Fallback ke rate terdekat yang didukung DAC
        double closest = supportedRates.front();
        double minDiff = std::abs(closest - target);
        for (double r : supportedRates) {
            const double diff = std::abs(r - target);
            if (diff < minDiff) {
                minDiff = diff;
                closest = r;
            }
        }
        return closest;
    }
};

struct BufferProfileHelper {
    static inline uint32_t computeTargetBufferFrames(int profileIndex, double sampleRate) {
        const double sr = sampleRate > 0.0 ? sampleRate : 48000.0;
        double durationSec = 0.160; // Default: Balanced (160ms)
        if (profileIndex == 0) durationSec = 0.064;      // Low Latency (64ms)
        else if (profileIndex == 2) durationSec = 0.192; // Jitter Safe (192ms)
        uint32_t frames = static_cast<uint32_t>(std::llround(sr * durationSec));
        return std::clamp(frames, 128u, 8192u);
    }
};

} // namespace TsrossaDsp
```

- [ ] **Step 4: Jalankan `./run_tests.sh` untuk memverifikasi seluruh unit test C++ DSP lulus**

```bash
./run_tests.sh
```

Expected: `RESULT: ALL C++ DSP TESTS PASSED` dan `RESULT: ALL 4 SWIFT TESTS PASSED`.

---

### Task 2: Integrasikan Dual-Path (32-Bit Integer Direct + 64-Bit `double` DSP & TPDF Dither) ke `CoreAudioSink`

**Files:**
- Modify: `src/nativeMain/cpp/CoreAudioSink.h`
- Modify: `src/nativeMain/cpp/CoreAudioSink.cpp`

- [ ] **Step 1: Perbarui `src/nativeMain/cpp/CoreAudioSink.h` untuk menyertakan `DspUtils.h` dan state DSP presisi ganda**

Tambahkan `#include "DspUtils.h"` di bagian atas `CoreAudioSink.h` dan tambahkan anggota berikut pada `class CoreAudioSink`:

```cpp
    // DSP & Volume Control
    void setSliderVolume(double sliderValue, bool useHardwareVolume);
    void setBufferProfile(int profileIndex);
    int getBufferProfile() const { return m_bufferProfile.load(); }
    void triggerWarmup(uint32_t durationMs);
    double getActualHardwareSampleRate() const { return m_actualHardwareSampleRate.load(); }
    std::string getHogModeStatusString() const;
    bool isStreamIntegerMode() const { return m_streamIsInteger.load(); }
```

Serta pada bagian `private:` di `CoreAudioSink.h`:

```cpp
    std::atomic<double> m_targetGain{1.0};
    double m_currentGain{1.0};
    TsrossaDsp::SplitMix64Dither m_dither;
    std::atomic<bool> m_streamIsInteger{false};
    std::atomic<double> m_actualHardwareSampleRate{0.0};
    std::atomic<int> m_bufferProfile{1}; // 0=64ms, 1=160ms, 2=192ms
    std::atomic<uint32_t> m_warmupFramesRemaining{0};

    bool applyHardwareVolumeScalar(AudioDeviceID deviceId, double sliderValue, double& outResidualLinearGain);
```

- [ ] **Step 2: Perbarui `setupAudioUnit()` dan `handleRender()` di `src/nativeMain/cpp/CoreAudioSink.cpp`**

Di `setupAudioUnit(uint32_t sampleRate, uint32_t channels)` pada `CoreAudioSink.cpp`:
1. Saat perangkat eksternal dalam mode Exclusive/Hog dan `m_targetGain.load() >= 0.99999`, coba atur `AudioStreamBasicDescription` dengan `kAudioFormatFlagIsSignedInteger | kAudioFormatFlagIsPacked` (`mBitsPerChannel = 32`, `int32_t`). Jika `AudioUnitSetProperty(kAudioUnitProperty_StreamFormat)` sukses (`status == noErr`), set `m_streamIsInteger.store(true)`; jika ditolak oleh driver (misal speaker internal macOS), fallback ke `kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked` (`32-bit float`) dan set `m_streamIsInteger.store(false)`.
2. Di `handleRender()`, proses warmup frames terlebih dahulu (jika `m_warmupFramesRemaining > 0`, isi dengan 0 atau TPDF dither sub-LSB untuk membangunkan clock PLL DAC), lalu panggil:
   - `TsrossaDsp::SampleProcessor::processInt32Buffer(...)` jika `m_streamIsInteger.load() == true`
   - `TsrossaDsp::SampleProcessor::processFloat32Buffer(...)` jika `m_streamIsInteger.load() == false`

```cpp
    // Di dalam handleRender() setelah menyalin sample mentah int32_t ke tempSrc:
    const double targetGain = m_targetGain.load(std::memory_order_relaxed);
    const bool ditherEnabled = m_ditherEnabled.load(std::memory_order_relaxed);

    if (m_streamIsInteger.load(std::memory_order_relaxed)) {
        int32_t* outInt = static_cast<int32_t*>(ioData->mBuffers[0].mData);
        TsrossaDsp::SampleProcessor::processInt32Buffer(
            tempSrc, outInt, framesToCopy, channels,
            m_currentGain, targetGain, ditherEnabled, m_dither
        );
        if (framesToCopy < inNumberFrames) {
            std::memset(outInt + (framesToCopy * channels), 0,
                        (inNumberFrames - framesToCopy) * channels * sizeof(int32_t));
        }
    } else {
        float* outFloat = static_cast<float*>(ioData->mBuffers[0].mData);
        TsrossaDsp::SampleProcessor::processFloat32Buffer(
            tempSrc, outFloat, framesToCopy, channels,
            m_currentGain, targetGain, ditherEnabled, m_dither
        );
        if (framesToCopy < inNumberFrames) {
            std::memset(outFloat + (framesToCopy * channels), 0,
                        (inNumberFrames - framesToCopy) * channels * sizeof(float));
        }
    }
```

---

### Task 3: Implementasikan Validasi `supportedRates`, Pembacaan Balik Sample Rate Aktual, Hog Mode Jujur, Buffer Profile, dan Hardware Volume CoreAudio

**Files:**
- Modify: `src/nativeMain/cpp/CoreAudioSink.cpp`
- Modify: `src/nativeMain/cpp/TsrossaAudioBridge.h`
- Modify: `src/nativeMain/cpp/TsrossaAudioBridge.cpp`
- Modify: `macos/AudioEngine.swift`

- [ ] **Step 1: Perbarui `setDeviceSampleRate()` di `src/nativeMain/cpp/CoreAudioSink.cpp` agar memvalidasi `supportedRates` dan membaca balik rate perangkat keras**

```cpp
bool CoreAudioSink::setDeviceSampleRate(AudioDeviceID deviceId, uint32_t sampleRate) {
    if (deviceId == kAudioObjectUnknown) return false;

    // Ambil daftar sample rate yang benar-benar didukung oleh perangkat ini
    std::vector<double> supportedRates;
    auto devices = enumerateOutputDevices();
    for (const auto& dev : devices) {
        if (dev.deviceId == deviceId) {
            supportedRates = dev.supportedRates;
            break;
        }
    }

    const double targetNominalRate = TsrossaDsp::SampleRateMatcher::selectBestSupportedRate(sampleRate, supportedRates);

    AudioObjectPropertyAddress rateAddr = {
        kAudioDevicePropertyNominalSampleRate,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };

    Float64 currentRate = 0.0;
    UInt32 size = sizeof(Float64);
    AudioObjectGetPropertyData(deviceId, &rateAddr, 0, nullptr, &size, &currentRate);

    if (std::abs(currentRate - targetNominalRate) > 0.5) {
        Float64 desiredRate = targetNominalRate;
        OSStatus status = AudioObjectSetPropertyData(deviceId, &rateAddr, 0, nullptr, sizeof(Float64), &desiredRate);
        if (status == noErr) {
            // Tunggu hingga PLL perangkat keras mengunci sample rate baru (maks 300ms)
            for (int waitIter = 0; waitIter < 30; ++waitIter) {
                usleep(10000);
                Float64 checkRate = 0.0;
                UInt32 checkSize = sizeof(Float64);
                if (AudioObjectGetPropertyData(deviceId, &rateAddr, 0, nullptr, &checkSize, &checkRate) == noErr) {
                    if (std::abs(checkRate - targetNominalRate) < 0.5) {
                        currentRate = checkRate;
                        break;
                    }
                }
            }
        }
    }

    // Baca balik nilai aktual dari perangkat keras
    Float64 verifiedRate = 0.0;
    UInt32 verifiedSize = sizeof(Float64);
    if (AudioObjectGetPropertyData(deviceId, &rateAddr, 0, nullptr, &verifiedSize, &verifiedRate) == noErr && verifiedRate > 0.0) {
        m_actualHardwareSampleRate.store(verifiedRate);
    } else {
        m_actualHardwareSampleRate.store(targetNominalRate);
    }

    // Terapkan ukuran buffer sesuai profil (64ms / 160ms / 192ms)
    UInt32 targetBufferFrames = TsrossaDsp::BufferProfileHelper::computeTargetBufferFrames(
        m_bufferProfile.load(), m_actualHardwareSampleRate.load()
    );
    AudioObjectPropertyAddress bufAddr = {
        kAudioDevicePropertyBufferFrameSize,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    AudioObjectSetPropertyData(deviceId, &bufAddr, 0, nullptr, sizeof(UInt32), &targetBufferFrames);

    return std::abs(m_actualHardwareSampleRate.load() - static_cast<double>(sampleRate)) < 0.5;
}
```

- [ ] **Step 2: Implementasikan `getHogModeStatusString()`, `setSliderVolume()`, dan `triggerWarmup()` di `CoreAudioSink.cpp`**

```cpp
std::string CoreAudioSink::getHogModeStatusString() const {
    if (m_hoggerPid == getpid()) {
        return "EXCLUSIVE_HOG";
    }
    if (!m_hogModeEnabled) {
        return "SHARED_DISABLED";
    }
    return "SHARED_BUILTIN_OR_FALLBACK";
}

bool CoreAudioSink::applyHardwareVolumeScalar(AudioDeviceID deviceId, double sliderValue, double& outResidualLinearGain) {
    outResidualLinearGain = TsrossaDsp::StudioTaper::sliderToLinearGain(sliderValue);
    if (deviceId == kAudioObjectUnknown) return false;

    AudioObjectPropertyAddress volAddr = {
        kAudioHardwareServiceDeviceProperty_VirtualMainVolume,
        kAudioDevicePropertyScopeOutput,
        kAudioObjectPropertyElementMain
    };
    if (!AudioObjectHasProperty(deviceId, &volAddr)) {
        return false;
    }
    Boolean settable = false;
    if (AudioObjectIsPropertySettable(deviceId, &volAddr, &settable) != noErr || !settable) {
        return false;
    }

    // Gunakan kurva Studio Taper dalam skala scalar CoreAudio [0.0, 1.0]
    Float32 scalar = static_cast<Float32>(std::clamp(sliderValue, 0.0, 1.0));
    if (AudioObjectSetPropertyData(deviceId, &volAddr, 0, nullptr, sizeof(Float32), &scalar) == noErr) {
        outResidualLinearGain = 1.0; // Perangkat keras menangani atenuasi utama
        return true;
    }
    return false;
}

void CoreAudioSink::setSliderVolume(double sliderValue, bool useHardwareVolume) {
    const double clamped = std::clamp(sliderValue, 0.0, 1.0);
    if (clamped >= 0.9999) {
        m_targetGain.store(1.0, std::memory_order_relaxed);
        return;
    }
    double residualGain = TsrossaDsp::StudioTaper::sliderToLinearGain(clamped);
    if (useHardwareVolume && m_selectedDeviceId != 0) {
        applyHardwareVolumeScalar(m_selectedDeviceId, clamped, residualGain);
    }
    m_targetGain.store(residualGain, std::memory_order_relaxed);
}

void CoreAudioSink::setBufferProfile(int profileIndex) {
    m_bufferProfile.store(std::clamp(profileIndex, 0, 2));
}

void CoreAudioSink::triggerWarmup(uint32_t durationMs) {
    const uint32_t sr = m_sampleRate > 0 ? m_sampleRate : 48000;
    const uint32_t frames = (sr * std::min(durationMs, 2000u)) / 1000u;
    m_warmupFramesRemaining.store(frames, std::memory_order_relaxed);
}
```

- [ ] **Step 3: Ekspos fungsi C baru di `TsrossaAudioBridge.h` dan `TsrossaAudioBridge.cpp` serta hubungkan di `AudioEngine.swift`**

Tambahkan deklarasi berikut di `src/nativeMain/cpp/TsrossaAudioBridge.h`:

```c
void tsrossa_set_slider_volume(double sliderValue, bool useHardwareVolume);
void tsrossa_set_buffer_profile(int profileIndex);
void tsrossa_trigger_warmup(uint32_t durationMs);
double tsrossa_get_actual_hardware_sample_rate(void);
```

Implementasikan wrapper-nya di `src/nativeMain/cpp/TsrossaAudioBridge.cpp` yang meneruskan ke `g_sink`, lalu perbarui `AudioEngine.swift` pada `setVolume(_ val: Double)` agar memanggil `tsrossa_set_slider_volume(clamped, self.hardwareVolumeEnabled)` dan saat inisialisasi DAC memanggil `tsrossa_trigger_warmup(350)` apabila `dacWarmupEnabled` bernilai `true`.

- [ ] **Step 4: Jalankan `./run_tests.sh` dan `./build_mac.sh` untuk memverifikasi kompilasi dan test**

```bash
./run_tests.sh && ./build_mac.sh
```

Expected: Seluruh test C++ & Swift lulus dan aplikasi ter-build dengan bersih.

- [ ] **Step 5: Commit hasil Plan 02**

```bash
git add src/nativeMain/cpp/DspUtils.h src/nativeMain/cpp/CoreAudioSink.h src/nativeMain/cpp/CoreAudioSink.cpp src/nativeMain/cpp/TsrossaAudioBridge.h src/nativeMain/cpp/TsrossaAudioBridge.cpp macos/AudioEngine.swift tests/cpp/test_dsp_utils.cpp
git commit -m "feat(audio): add 64-bit double DSP, SplitMix64 TPDF dither, studio taper, and honest CoreAudio rate/hog lock"
```

---

## Review Focus

1. **Dual-Path Bit-Perfect Integer vs 64-Bit Float Fallback:** Saat DAC eksternal mendukung stream integer (`kAudioFormatFlagIsSignedInteger`) dan volume berada pada `1.0` (100%), sampel `int32_t` dilewatkan langsung dengan `std::memcpy` tanpa menyentuh floating-point sama sekali. Saat pengguna menurunkan volume atau menggunakan speaker internal Mac (yang mewajibkan 32-bit float di CoreAudio), mesin memproses sinyal di domain `double` 64-bit dengan interpolasi gain per-sample dan TPDF dither SplitMix64.
2. **Validasi `supportedRates` & Rate Family Matching:** Mencegah kegagalan diam-diam ketika memutar file 176.4 kHz / 352.8 kHz pada DAC yang hanya mendukung hingga 96 kHz dengan memilih kelipatan integer tertinggi dari keluarga clock yang sama (misal `88.2 kHz` untuk keluarga `44.1 kHz`).
