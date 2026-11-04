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
        constexpr double kDitherLsbScale = 1.0; // 1 LSB for 32-bit Integer domain (matching Android AudioStreamer.cpp)
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
                if (applyDither && g > 0.0 && g < 0.99999) {
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
