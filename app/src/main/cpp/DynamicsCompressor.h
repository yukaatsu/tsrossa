#ifndef TSROSSA_DYNAMICS_COMPRESSOR_H
#define TSROSSA_DYNAMICS_COMPRESSOR_H

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <mutex>

static constexpr int kMaxCompChannels = 8;

struct DynamicsCompressorConfig {
    double downThresholdDb = -16.0;  // [-40.0 .. 0.0] dBFS
    double downRatio = 2.2;          // [1.0 .. 20.0] (1.0 = downward OFF)
    double upThresholdDb = -34.0;    // [-54.0 .. -6.0] dBFS (invariant <= downThresholdDb)
    double upRatio = 1.8;            // [1.0 .. 10.0] (1.0 = upward OFF)
    double maxUpwardBoostDb = 5.0;   // [0.0 .. 12.0] dB
    double floorThresholdDb = -60.0; // [-80.0 .. -30.0] dBFS (invariant <= upThresholdDb - 6.0)
    double kneeDb = 6.0;             // [0.0 .. 12.0] dB
    double attackMs = 8.0;           // [0.5 .. 100.0] ms
    double releaseMs = 160.0;        // [20.0 .. 1000.0] ms
    double makeupGainDb = 1.0;       // [-6.0 .. +12.0] dB
    double sidechainHpfHz = 90.0;    // [0.0 (OFF) or 20.0 .. 250.0] Hz
    bool useRmsDetector = true;      // false = Stereo-Linked Peak, true = 10ms RMS
};

struct CompressorCoeffBank {
    double downThresholdDb = -16.0;
    double downRatio = 1.0;
    double upThresholdDb = -34.0;
    double upRatio = 1.0;
    double maxUpwardBoostDb = 5.0;
    double floorThresholdDb = -60.0;
    double gateTransitionDb = 6.0;
    double kneeDb = 6.0;
    double attackCoeff = 0.997;
    double releaseCoeff = 0.9998;
    double fastUpwardRetractCoeff = 0.982; // ~1.2 ms fast retract when upward boost hits loud transient
    double rmsCoeff = 0.9993;              // 30 ms RMS window (eliminates 50-100 Hz 2x-f ripple)
    int peakHoldFrames = 576;              // ~12 ms peak-hold for clean Peak-mode envelope
    double peakReleaseCoeff = 0.99958;     // ~50 ms smooth Peak-mode pre-envelope decay
    // 2nd-Order Butterworth Biquad Sidechain HPF (-12 dB/oct, Q = 0.70710678)
    double scHpfB0 = 1.0;
    double scHpfB1 = 0.0;
    double scHpfB2 = 0.0;
    double scHpfA1 = 0.0;
    double scHpfA2 = 0.0;
    bool scHpfEnabled = false;
    double makeupGainDb = 0.0;
    double makeupSmoothCoeff = 0.9986;
    uint32_t sampleRate = 48000;
    bool useRmsDetector = true;
    bool hasActiveProcessing = false;
};

/**
 * Pure C++17 Stereo-Linked Upward/Downward Dynamics Compressor (zero OS or USB driver dependencies).
 * - Processes 64-bit double PCM frames scaled in 32-bit full-scale domain (FS = 2147483648.0).
 * - Zero heap allocations, zero locks, zero virtual dispatch on real-time audio thread.
 * - Guaranteed 0.000000 dB Interaural Level Difference (ILD) shift via single linked gain scalar.
 */
class DynamicsCompressor {
public:
    static constexpr double kFullScalePcm32 = 2147483648.0;
    static constexpr double kMinFloorLin = 6.30957344480193e-8; // 10^(-144/20) -> -144.0 dBFS floor
    static constexpr double kLn10Over20 = 0.1151292546497022842; // ln(10) / 20.0
    static constexpr double kBypassRampDurationSec = 0.009; // 9 ms finite-time linear dB bypass ramp
    static constexpr double kBypassSilenceEpsDb = 0.01;     // < 0.01 dB residual -> clean stop & reset

    DynamicsCompressor() {
        reset(48000);
    }

    void reset(uint32_t /*sampleRate*/) {
        std::memset(scZ1, 0, sizeof(scZ1));
        std::memset(scZ2, 0, sizeof(scZ2));
        rmsPower = 0.0;
        peakEnv = 0.0;
        peakHoldRemaining = 0;
        smoothedGainDb = 0.0;
        smoothedMakeupDb = 0.0;
        lastAppliedGainDb = 0.0f;
        bypassRamping = false;
        bypassFramesRemaining = 0;
        bypassGainStepDb = 0.0;
        bypassMakeupStepDb = 0.0;
    }

    /**
     * Evaluates C1-continuous static gain delta (dB) for a given detector level (dBFS).
     * Negative return = downward gain reduction; Positive return = upward boost; 0.0 = unity.
     */
    static inline double computeStaticGainDb(double inDb, const CompressorCoeffBank &bank) {
        double downGainDb = 0.0;
        if (bank.downRatio > 1.001) {
            const double td = bank.downThresholdDb;
            const double w = bank.kneeDb;
            const double halfW = 0.5 * w;
            const double sd = 1.0 - (1.0 / bank.downRatio);
            if (inDb <= td - halfW) {
                downGainDb = 0.0;
            } else if (inDb >= td + halfW || w <= 0.01) {
                downGainDb = -(inDb - td) * sd;
            } else {
                const double u = inDb - td + halfW;
                downGainDb = -(sd * u * u) / (2.0 * w);
            }
        }

        double upGainDb = 0.0;
        if (bank.upRatio > 1.001 && bank.maxUpwardBoostDb > 0.01) {
            const double tu = bank.upThresholdDb;
            const double w = bank.kneeDb;
            const double halfW = 0.5 * w;
            const double su = 1.0 - (1.0 / bank.upRatio);

            double upRawDb = 0.0;
            if (inDb >= tu + halfW) {
                upRawDb = 0.0;
            } else if (inDb <= tu - halfW || w <= 0.01) {
                upRawDb = (tu - inDb) * su;
            } else {
                const double v = tu + halfW - inDb;
                upRawDb = (su * v * v) / (2.0 * w);
            }

            const double upCapped = std::min(upRawDb, bank.maxUpwardBoostDb);
            const double tf = bank.floorThresholdDb;
            const double wg = std::max(1.0, bank.gateTransitionDb);
            double gateFactor = 0.0;
            if (inDb <= tf) {
                gateFactor = 0.0;
            } else if (inDb >= tf + wg) {
                gateFactor = 1.0;
            } else {
                const double t = (inDb - tf) / wg;
                gateFactor = t * t * (3.0 - 2.0 * t); // Hermite C1 smoothstep
            }
            upGainDb = upCapped * gateFactor;
        }

        return downGainDb + upGainDb;
    }

    inline void processFrame(double *samples, uint32_t chCount, const CompressorCoeffBank &bank) {
        const uint32_t safeCh = (chCount > static_cast<uint32_t>(kMaxCompChannels))
                                    ? static_cast<uint32_t>(kMaxCompChannels)
                                    : chCount;
        if (safeCh == 0U) return;

        // 1. Optional 2nd-Order Butterworth Sidechain HPF (-12 dB/oct, TDF-II) + Stereo-Linked Detector
        double rawPeakLin = 0.0;
        double scPeakLin = 0.0;
        double sumSq = 0.0;
        const bool useHpf = bank.scHpfEnabled;

        for (uint32_t c = 0; c < safeCh; ++c) {
            const double x = samples[c];
            const double absRaw = std::abs(x);
            if (absRaw > rawPeakLin) rawPeakLin = absRaw;

            double sc = x;
            if (useHpf) {
                const double y = bank.scHpfB0 * x + scZ1[c];
                scZ1[c] = bank.scHpfB1 * x - bank.scHpfA1 * y + scZ2[c];
                scZ2[c] = bank.scHpfB2 * x - bank.scHpfA2 * y;
                sc = y;
            }
            if (bank.useRmsDetector) {
                sumSq += sc * sc;
            } else {
                const double a = std::abs(sc);
                if (a > scPeakLin) scPeakLin = a;
            }
        }

        double detLin = 0.0;
        if (bank.useRmsDetector) {
            const double instPow = sumSq / static_cast<double>(safeCh);
            rmsPower = bank.rmsCoeff * rmsPower + (1.0 - bank.rmsCoeff) * instPow;
            detLin = (rmsPower > 0.0) ? std::sqrt(rmsPower) : 0.0;
        } else {
            // Peak-Hold (12 ms) + Smooth Decay (50 ms) eliminates rectified half-cycle AM-IMD!
            if (scPeakLin >= peakEnv) {
                peakEnv = scPeakLin;
                peakHoldRemaining = bank.peakHoldFrames;
            } else if (peakHoldRemaining > 0) {
                peakHoldRemaining--;
            } else {
                peakEnv = bank.peakReleaseCoeff * peakEnv + (1.0 - bank.peakReleaseCoeff) * scPeakLin;
            }
            detLin = peakEnv;
        }

        const double normLin = std::max(detLin / kFullScalePcm32, kMinFloorLin);
        const double inDb = 20.0 * std::log10(normLin);

        // 2. Static Dual-Region Transfer Curve
        const double targetDb = computeStaticGainDb(inDb, bank);

        // 3. Asymmetric Ballistics + Fast Protective Retract for Upward Boost on Sudden Loud Transients
        if (smoothedGainDb > 0.0) {
            const double rawPeakDb = 20.0 * std::log10(std::max(rawPeakLin / kFullScalePcm32, kMinFloorLin));
            if (targetDb < 0.0 || (rawPeakDb + smoothedGainDb) > -1.5) {
                const double safeTarget = std::min(targetDb, std::min(0.0, -1.5 - rawPeakDb));
                smoothedGainDb = safeTarget + (smoothedGainDb - safeTarget) * bank.fastUpwardRetractCoeff;
            } else {
                const double balCoeff = (targetDb < smoothedGainDb) ? bank.attackCoeff : bank.releaseCoeff;
                smoothedGainDb = targetDb + (smoothedGainDb - targetDb) * balCoeff;
            }
        } else {
            const double balCoeff = (targetDb < smoothedGainDb) ? bank.attackCoeff : bank.releaseCoeff;
            smoothedGainDb = targetDb + (smoothedGainDb - targetDb) * balCoeff;
        }
        smoothedMakeupDb = bank.makeupGainDb + (smoothedMakeupDb - bank.makeupGainDb) * bank.makeupSmoothCoeff;

        // 4. Single Identical Scalar Multiplier Applied Across ALL Channels (0.000000 dB ILD shift)
        const double totalGainDb = smoothedGainDb + smoothedMakeupDb;
        const double totalGainLin = std::exp(totalGainDb * kLn10Over20);
        for (uint32_t c = 0; c < safeCh; ++c) {
            samples[c] *= totalGainLin;
        }

        lastAppliedGainDb = static_cast<float>(smoothedGainDb);
    }

    inline float getLastAppliedGainDb() const {
        return lastAppliedGainDb;
    }

    inline bool isBypassRamping() const {
        return bypassRamping;
    }

    inline void cancelBypassRamp() {
        bypassRamping = false;
        bypassFramesRemaining = 0;
        bypassGainStepDb = 0.0;
        bypassMakeupStepDb = 0.0;
    }

    inline void beginBypassRamp(uint32_t sampleRate) {
        if (std::abs(smoothedGainDb) < kBypassSilenceEpsDb &&
            std::abs(smoothedMakeupDb) < kBypassSilenceEpsDb) {
            reset(sampleRate);
            return;
        }
        const double sr = static_cast<double>(sampleRate > 0U ? sampleRate : 48000U);
        const int rampFrames = std::max(64, static_cast<int>(std::llround(sr * kBypassRampDurationSec)));
        bypassFramesRemaining = rampFrames;
        bypassGainStepDb = -smoothedGainDb / static_cast<double>(rampFrames);
        bypassMakeupStepDb = -smoothedMakeupDb / static_cast<double>(rampFrames);
        bypassRamping = true;
    }

    inline void processBypassRampFrame(double *samples, uint32_t chCount) {
        if (!bypassRamping) return;
        const uint32_t safeCh = (chCount > static_cast<uint32_t>(kMaxCompChannels))
                                    ? static_cast<uint32_t>(kMaxCompChannels)
                                    : chCount;

        smoothedGainDb += bypassGainStepDb;
        smoothedMakeupDb += bypassMakeupStepDb;
        bypassFramesRemaining--;

        const double totalGainDb = smoothedGainDb + smoothedMakeupDb;
        if (bypassFramesRemaining <= 0 ||
            (std::abs(smoothedGainDb) < kBypassSilenceEpsDb &&
             std::abs(smoothedMakeupDb) < kBypassSilenceEpsDb &&
             std::abs(totalGainDb) < kBypassSilenceEpsDb)) {
            reset(0U);
            return;
        }

        const double totalGainLin = std::exp(totalGainDb * kLn10Over20);
        for (uint32_t c = 0; c < safeCh; ++c) {
            samples[c] *= totalGainLin;
        }
        lastAppliedGainDb = static_cast<float>(smoothedGainDb);
    }

private:
    double scZ1[kMaxCompChannels]{};
    double scZ2[kMaxCompChannels]{};
    double rmsPower = 0.0;
    double peakEnv = 0.0;
    int peakHoldRemaining = 0;
    double smoothedGainDb = 0.0;
    double smoothedMakeupDb = 0.0;
    float lastAppliedGainDb = 0.0f;
    bool bypassRamping = false;
    int bypassFramesRemaining = 0;
    double bypassGainStepDb = 0.0;
    double bypassMakeupStepDb = 0.0;
};

struct DynamicsCompressorState {
    static constexpr uint32_t kNumBanks = 4;

    std::atomic<bool> enabled{false};
    std::atomic<bool> stateResetPending{true};

    DynamicsCompressorConfig config;
    CompressorCoeffBank banks[kNumBanks];
    std::atomic<uint32_t> activeBankIndex{0};
    std::mutex coeffWriteMutex;

    DynamicsCompressorState() {
        updateCoefficients(48000U);
    }

    void requestStateReset() {
        stateResetPending.store(true, std::memory_order_release);
    }

    bool consumeStateResetIfPending() {
        return stateResetPending.exchange(false, std::memory_order_acq_rel);
    }

    CompressorCoeffBank snapshotActiveBank() const {
        const uint32_t idx = activeBankIndex.load(std::memory_order_acquire) & (kNumBanks - 1U);
        return banks[idx];
    }

    void setFullConfig(
        bool isEnabled,
        float downThresholdDb,
        float downRatio,
        float upThresholdDb,
        float upRatio,
        float maxUpwardBoostDb,
        float floorThresholdDb,
        float kneeDb,
        float attackMs,
        float releaseMs,
        float makeupGainDb,
        float sidechainHpfHz,
        bool useRmsDetector,
        uint32_t currentSampleRate
    ) {
        const bool wasEnabled = enabled.load(std::memory_order_relaxed);
        {
            std::lock_guard<std::mutex> lock(coeffWriteMutex);
            config.downThresholdDb = static_cast<double>(downThresholdDb);
            config.downRatio = static_cast<double>(downRatio);
            config.upThresholdDb = static_cast<double>(upThresholdDb);
            config.upRatio = static_cast<double>(upRatio);
            config.maxUpwardBoostDb = static_cast<double>(maxUpwardBoostDb);
            config.floorThresholdDb = static_cast<double>(floorThresholdDb);
            config.kneeDb = static_cast<double>(kneeDb);
            config.attackMs = static_cast<double>(attackMs);
            config.releaseMs = static_cast<double>(releaseMs);
            config.makeupGainDb = static_cast<double>(makeupGainDb);
            config.sidechainHpfHz = static_cast<double>(sidechainHpfHz);
            config.useRmsDetector = useRmsDetector;
            const uint32_t sr = (currentSampleRate > 0U) ? currentSampleRate : 48000U;
            updateCoefficientsLocked(sr);
        }
        if (isEnabled && !wasEnabled) {
            requestStateReset();
        }
        enabled.store(isEnabled, std::memory_order_release);
    }

    void updateCoefficients(uint32_t targetSampleRate) {
        if (targetSampleRate == 0U) return;
        std::lock_guard<std::mutex> lock(coeffWriteMutex);
        updateCoefficientsLocked(targetSampleRate);
    }

private:
    void updateCoefficientsLocked(uint32_t targetSampleRate) {
        if (targetSampleRate == 0U) return;
        const uint32_t nextIdx = (activeBankIndex.load(std::memory_order_relaxed) + 1U) & (kNumBanks - 1U);
        CompressorCoeffBank &bank = banks[nextIdx];

        const double sr = static_cast<double>(targetSampleRate);
        const double downThresh = std::max(-40.0, std::min(0.0, config.downThresholdDb));
        const double downRat = std::max(1.0, std::min(20.0, config.downRatio));
        const double upThresh = std::max(-54.0, std::min(downThresh, std::min(-6.0, config.upThresholdDb)));
        const double upRat = std::max(1.0, std::min(10.0, config.upRatio));
        const double maxUp = std::max(0.0, std::min(12.0, config.maxUpwardBoostDb));
        const double floorThresh = std::max(-80.0, std::min(upThresh - 6.0, std::min(-30.0, config.floorThresholdDb)));
        const double knee = std::max(0.0, std::min(12.0, config.kneeDb));
        const double attMs = std::max(0.5, std::min(100.0, config.attackMs));
        const double relMs = std::max(20.0, std::min(1000.0, config.releaseMs));
        const double makeupDb = std::max(-6.0, std::min(12.0, config.makeupGainDb));
        const double hpfHz = (config.sidechainHpfHz <= 1.0) ? 0.0 : std::max(20.0, std::min(250.0, config.sidechainHpfHz));

        bank.downThresholdDb = downThresh;
        bank.downRatio = downRat;
        bank.upThresholdDb = upThresh;
        bank.upRatio = upRat;
        bank.maxUpwardBoostDb = maxUp;
        bank.floorThresholdDb = floorThresh;
        bank.gateTransitionDb = 6.0;
        bank.kneeDb = knee;
        bank.attackCoeff = std::exp(-1.0 / (sr * attMs * 0.001));
        bank.releaseCoeff = std::exp(-1.0 / (sr * relMs * 0.001));
        bank.fastUpwardRetractCoeff = std::exp(-1.0 / (sr * 0.0012)); // 1.2 ms protective retract
        bank.rmsCoeff = std::exp(-1.0 / (sr * 0.030));               // 30 ms RMS integration
        bank.peakHoldFrames = std::max(1, static_cast<int>(std::llround(sr * 0.012))); // 12 ms hold
        bank.peakReleaseCoeff = std::exp(-1.0 / (sr * 0.050));       // 50 ms smooth peak decay

        if (hpfHz > 0.0) {
            const double safeHpf = std::min(hpfHz, sr * 0.40);
            const double w0 = 2.0 * 3.14159265358979323846 * safeHpf / sr;
            const double cosW0 = std::cos(w0);
            const double sinW0 = std::sin(w0);
            const double alpha = sinW0 / (2.0 * 0.7071067811865475); // Q = 1/sqrt(2) Butterworth
            const double a0 = 1.0 + alpha;
            const double invA0 = 1.0 / a0;
            bank.scHpfB0 = ((1.0 + cosW0) * 0.5) * invA0;
            bank.scHpfB1 = -(1.0 + cosW0) * invA0;
            bank.scHpfB2 = ((1.0 + cosW0) * 0.5) * invA0;
            bank.scHpfA1 = (-2.0 * cosW0) * invA0;
            bank.scHpfA2 = (1.0 - alpha) * invA0;
            bank.scHpfEnabled = true;
        } else {
            bank.scHpfB0 = 1.0;
            bank.scHpfB1 = 0.0;
            bank.scHpfB2 = 0.0;
            bank.scHpfA1 = 0.0;
            bank.scHpfA2 = 0.0;
            bank.scHpfEnabled = false;
        }
        bank.makeupGainDb = makeupDb;
        bank.makeupSmoothCoeff = std::exp(-1.0 / (sr * 0.015));
        bank.sampleRate = targetSampleRate;
        bank.useRmsDetector = config.useRmsDetector;
        bank.hasActiveProcessing =
            (downRat > 1.001) || (upRat > 1.001 && maxUp > 0.01) || (std::abs(makeupDb) > 0.01);

        activeBankIndex.store(nextIdx, std::memory_order_release);
    }
};

#endif // TSROSSA_DYNAMICS_COMPRESSOR_H
