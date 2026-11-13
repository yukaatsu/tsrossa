#ifndef TSROSSA_PARAMETRIC_EQ_H
#define TSROSSA_PARAMETRIC_EQ_H

#include <cmath>
#include <cstdint>
#include <atomic>
#include <algorithm>
#include <cstring>

static constexpr int kNumPeqBands = 10;
static constexpr int kMaxPeqChannels = 8;
static constexpr double kPi = 3.14159265358979323846;

enum PeqFilterType : int {
    PEQ_FILTER_PEAKING = 0,
    PEQ_FILTER_LOW_SHELF = 1,
    PEQ_FILTER_HIGH_SHELF = 2
};

struct PeqBandParams {
    double freqHz = 1000.0;
    double gainDb = 0.0;
    double q = 0.75;
    int filterType = PEQ_FILTER_PEAKING;
    bool enabled = true;
};

struct BiquadCoeffs {
    double b0 = 1.0;
    double b1 = 0.0;
    double b2 = 0.0;
    double a1 = 0.0;
    double a2 = 0.0;
    bool isIdentity = true;
};

struct BiquadState {
    double z1 = 0.0;
    double z2 = 0.0;

    void reset() {
        z1 = 0.0;
        z2 = 0.0;
    }
};

struct PeqCoeffBank {
    BiquadCoeffs bands[kNumPeqBands];
    double preampLinear = 1.0;
    uint32_t sampleRate = 0;
    bool hasActiveFilter = false;
};

class ParametricEqEngine {
public:
    static void computeBiquad(
        int filterType,
        double freqHz,
        double gainDb,
        double q,
        double sampleRate,
        BiquadCoeffs &out
    ) {
        if (sampleRate <= 0.0 || freqHz <= 10.0 || freqHz >= 0.49 * sampleRate || std::abs(gainDb) < 0.005) {
            out.b0 = 1.0;
            out.b1 = 0.0;
            out.b2 = 0.0;
            out.a1 = 0.0;
            out.a2 = 0.0;
            out.isIdentity = true;
            return;
        }

        const double maxFreq = sampleRate * 0.45;
        const double safeFreq = std::max(10.0, std::min(freqHz, maxFreq));
        const double safeQ = std::max(0.1, std::min(q, 18.0));

        const double w0 = 2.0 * kPi * (safeFreq / sampleRate);
        const double cos_w0 = std::cos(w0);
        const double sin_w0 = std::sin(w0);
        const double A = std::pow(10.0, gainDb / 40.0);

        // Proportional Nyquist bandwidth stabilization for upper-frequency peaking filters (> 0.18 * fs):
        // Prevents upper-treble bands (4 kHz, 8 kHz) from bloating and piling up near Nyquist.
        double alpha = sin_w0 / (2.0 * safeQ);
        if (filterType == PEQ_FILTER_PEAKING && safeFreq > sampleRate * 0.18) {
            const double cos_half_w0 = std::cos(w0 * 0.5);
            if (cos_half_w0 > 0.05) {
                alpha = (sin_w0 / (2.0 * safeQ)) * std::pow(cos_half_w0, 0.35);
            }
        }

        double b0 = 1.0, b1 = 0.0, b2 = 0.0, a0 = 1.0, a1 = 0.0, a2 = 0.0;

        switch (filterType) {
            case PEQ_FILTER_LOW_SHELF: {
                const double two_sqrt_A_alpha = 2.0 * std::sqrt(A) * alpha;
                b0 = A * ((A + 1.0) - (A - 1.0) * cos_w0 + two_sqrt_A_alpha);
                b1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cos_w0);
                b2 = A * ((A + 1.0) - (A - 1.0) * cos_w0 - two_sqrt_A_alpha);
                a0 = (A + 1.0) + (A - 1.0) * cos_w0 + two_sqrt_A_alpha;
                a1 = -2.0 * ((A - 1.0) + (A + 1.0) * cos_w0);
                a2 = (A + 1.0) + (A - 1.0) * cos_w0 - two_sqrt_A_alpha;
                break;
            }
            case PEQ_FILTER_HIGH_SHELF: {
                const double two_sqrt_A_alpha = 2.0 * std::sqrt(A) * alpha;
                b0 = A * ((A + 1.0) + (A - 1.0) * cos_w0 + two_sqrt_A_alpha);
                b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cos_w0);
                b2 = A * ((A + 1.0) + (A - 1.0) * cos_w0 - two_sqrt_A_alpha);
                a0 = (A + 1.0) - (A - 1.0) * cos_w0 + two_sqrt_A_alpha;
                a1 = 2.0 * ((A - 1.0) - (A + 1.0) * cos_w0);
                a2 = (A + 1.0) - (A - 1.0) * cos_w0 - two_sqrt_A_alpha;
                break;
            }
            case PEQ_FILTER_PEAKING:
            default: {
                b0 = 1.0 + alpha * A;
                b1 = -2.0 * cos_w0;
                b2 = 1.0 - alpha * A;
                a0 = 1.0 + alpha / A;
                a1 = -2.0 * cos_w0;
                a2 = 1.0 - alpha / A;
                break;
            }
        }

        if (std::abs(a0) > 1e-12) {
            const double inv_a0 = 1.0 / a0;
            out.b0 = b0 * inv_a0;
            out.b1 = b1 * inv_a0;
            out.b2 = b2 * inv_a0;
            out.a1 = a1 * inv_a0;
            out.a2 = a2 * inv_a0;
            out.isIdentity = false;
        } else {
            out.b0 = 1.0;
            out.b1 = 0.0;
            out.b2 = 0.0;
            out.a1 = 0.0;
            out.a2 = 0.0;
            out.isIdentity = true;
        }
    }

    // Linear clamp utility for integer range safety without artificial harmonic compression
    static inline double clampPcm32(double x) {
        if (x > 2147483647.0) return 2147483647.0;
        if (x < -2147483648.0) return -2147483648.0;
        return x;
    }

    /**
     * Mastering-Grade Linear-Core Soft-Knee Ceiling for dense multi-instrument passages:
     * - 100.0000% bit-linear (y = x, zero harmonic distortion) up to -0.8 dBFS (91.2% of full scale).
     * - Smooth C1/C2 continuous hyperbolic tangent knee in the top 8.8% (between -0.8 dBFS and 0.0 dBFS)
     *   so overlapping drum, cymbal, guitar, and vocal transients preserve their micro-dynamic depth
     *   hierarchy instead of slamming flat into a single brickwall ceiling ("menumpuk di ujung").
     */
    static inline double applyMasteringCeilingPcm32(double x) {
        constexpr double kFullScalePos = 2147483647.0;
        constexpr double kFullScaleNeg = 2147483648.0;
        constexpr double kLinearThreshold = 1958505086.0; // 0.9120108 * 2147483647.0 (-0.80 dBFS)
        constexpr double kKneeSpanPos = kFullScalePos - kLinearThreshold; // 188978561.0
        constexpr double kInvKneeSpanPos = 1.0 / kKneeSpanPos;

        if (x > kLinearThreshold) {
            const double excess = (x - kLinearThreshold) * kInvKneeSpanPos;
            return kLinearThreshold + kKneeSpanPos * std::tanh(excess);
        } else if (x < -kLinearThreshold) {
            constexpr double kKneeSpanNeg = kFullScaleNeg - kLinearThreshold;
            constexpr double kInvKneeSpanNeg = 1.0 / kKneeSpanNeg;
            const double excess = (-x - kLinearThreshold) * kInvKneeSpanNeg;
            return -(kLinearThreshold + kKneeSpanNeg * std::tanh(excess));
        }
        return x;
    }
};

struct ParametricEqState {
    std::atomic<bool> enabled{false};
    std::atomic<float> preampDb{0.0f};

    // User-configured parameters
    PeqBandParams bandParams[kNumPeqBands];

    // Double-buffered lock-free coefficient bank for real-time USB callback thread
    PeqCoeffBank banks[2];
    std::atomic<uint32_t> activeBankIndex{0};

    // Filter state memory (one per channel per band, TDF-II)
    BiquadState filterStates[kMaxPeqChannels][kNumPeqBands];

    ParametricEqState() {
        // Standard ISO Octave Bands (Harman / Audiophile Reference layout)
        const double defaultFreqs[kNumPeqBands] = {
            31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0
        };
        for (int i = 0; i < kNumPeqBands; ++i) {
            bandParams[i].freqHz = defaultFreqs[i];
            bandParams[i].gainDb = 0.0;
            // Proportional 1-octave Q factor: 0.707 for shelves, 0.85 for punchy bass, 1.05 for clean mid/high instrument separation
            bandParams[i].q = (i == 0 || i == 9) ? 0.707 : (i == 1 || i == 2) ? 0.85 : 1.05;
            bandParams[i].filterType = (i == 0) ? PEQ_FILTER_LOW_SHELF :
                                       (i == 9) ? PEQ_FILTER_HIGH_SHELF : PEQ_FILTER_PEAKING;
            bandParams[i].enabled = true;
        }
        resetFilterStates();
    }

    void resetFilterStates() {
        for (int ch = 0; ch < kMaxPeqChannels; ++ch) {
            for (int b = 0; b < kNumPeqBands; ++b) {
                filterStates[ch][b].reset();
            }
        }
    }

    void setFullConfig(
        bool isEnabled,
        float newPreampDb,
        const float *freqs,
        const float *gains,
        const float *qs,
        const int *types,
        int count,
        uint32_t currentSampleRate
    ) {
        preampDb.store(newPreampDb, std::memory_order_relaxed);
        int n = std::min(count, kNumPeqBands);
        for (int i = 0; i < n; ++i) {
            bandParams[i].freqHz = static_cast<double>(freqs[i]);
            bandParams[i].gainDb = static_cast<double>(gains[i]);
            bandParams[i].q = static_cast<double>(qs[i]);
            bandParams[i].filterType = types[i];
            bandParams[i].enabled = true;
        }
        uint32_t sr = (currentSampleRate > 0) ? currentSampleRate : 48000U;
        updateCoefficients(sr);
        enabled.store(isEnabled, std::memory_order_release);
    }

    void updateCoefficients(uint32_t targetSampleRate) {
        if (targetSampleRate == 0) return;
        const uint32_t inactiveIdx = 1 - activeBankIndex.load(std::memory_order_relaxed);
        PeqCoeffBank &bank = banks[inactiveIdx];

        bank.sampleRate = targetSampleRate;
        const float preDb = preampDb.load(std::memory_order_relaxed);
        bank.preampLinear = std::pow(10.0, preDb / 20.0);
        bool hasFilter = (std::abs(preDb) > 0.01f);

        for (int i = 0; i < kNumPeqBands; ++i) {
            const auto &p = bandParams[i];
            if (p.enabled && std::abs(p.gainDb) >= 0.005) {
                ParametricEqEngine::computeBiquad(
                    p.filterType,
                    p.freqHz,
                    p.gainDb,
                    p.q,
                    static_cast<double>(targetSampleRate),
                    bank.bands[i]
                );
                if (!bank.bands[i].isIdentity) {
                    hasFilter = true;
                }
            } else {
                bank.bands[i].b0 = 1.0;
                bank.bands[i].b1 = 0.0;
                bank.bands[i].b2 = 0.0;
                bank.bands[i].a1 = 0.0;
                bank.bands[i].a2 = 0.0;
                bank.bands[i].isIdentity = true;
            }
        }

        bank.hasActiveFilter = hasFilter;
        activeBankIndex.store(inactiveIdx, std::memory_order_release);
    }
};

#endif // TSROSSA_PARAMETRIC_EQ_H
