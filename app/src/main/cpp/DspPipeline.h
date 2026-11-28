#ifndef TSROSSA_DSP_PIPELINE_H
#define TSROSSA_DSP_PIPELINE_H

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>

#include "ParametricEq.h"
#include "DynamicsCompressor.h"

/**
 * Pure C++17 Multi-Channel Decorrelated TPDF Dither Generator (zero OS/Android dependencies).
 * Provides 8 independent SplitMix64 Weyl-sequence PRNG streams (IACC = 0.0 across all channels).
 */
class MultiChannelTpdfDither {
public:
    static constexpr uint32_t kMaxCh = static_cast<uint32_t>(kMaxPeqChannels);

    inline double nextTpdf(uint32_t ch) {
        const uint32_t idx = ch % kMaxCh;
        return nextUniform(idx) - nextUniform(idx);
    }

private:
    static constexpr uint64_t kWeyl[kMaxCh] = {
        0x9E3779B97F4A7C15ULL,
        0xD1B54A32D192ED03ULL,
        0x94D049BB133111EBULL,
        0xBF58476D1CE4E5B9ULL,
        0xDB4F0B9175AE2165ULL,
        0xA0761D6478BD642FULL,
        0xE7037ED1A0B428DBULL,
        0x8EBC6AF09C88C6E3ULL
    };

    uint64_t states[kMaxCh] = {
        0x9E3779B97F4A7C15ULL,
        0xBF58476D1CE4E5B9ULL,
        0x243F6A8885A308D3ULL,
        0x13198A2E03707344ULL,
        0xA4093822299F31D0ULL,
        0x082EFA98EC4E6C89ULL,
        0x452821E638D01377ULL,
        0xBE5466CF34E90C6CULL
    };

    inline double nextUniform(uint32_t idx) {
        uint64_t z = (states[idx] += kWeyl[idx]);
        z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
        z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
        z = z ^ (z >> 31);
        return static_cast<double>(z >> 11) * (1.0 / 9007199254740992.0);
    }
};

struct DspProcessParams {
    const int32_t *src = nullptr;
    uint8_t *dstBytes = nullptr;
    size_t frames = 0;
    uint32_t channels = 2;
    uint32_t sampleRate = 48000;
    int subframeSize = 4;       // 2, 3, or 4 bytes
    int bitResolution = 32;     // 16, 24, or 32 bits (bBitResolution)
    int sourceBitDepth = 24;
    double currentVolume = 1.0;
    double targetVolume = 1.0;
    bool peqEnabled = false;
    bool compEnabled = false;
    bool forceStateReset = false;     // Hard track-boundary reset (resets PEQ, Compressor, Limiter, wetMix immediately)
    bool peqForceStateReset = false;  // Per-module live PEQ reset (resets PEQ biquads ONLY; preserves Compressor & 8ms crossfade!)
    bool compForceStateReset = false;
};

struct DspProcessResult {
    double finalVolume = 1.0;
    bool bitPerfectDirectPass = false;
    float compressorGainDb = 0.0f;
    float limiterGainReductionDb = 0.0f; // <= 0.0f (most-negative gain reduction in this block)
    bool peqEffective = false;
    bool compEffective = false;
};

/**
 * Pure C++17 Static Value-Composed Audio DSP Engine (shared-ready: Android & future macOS CoreAudio).
 * Handles:
 *  - Bit-Perfect Integer Direct Pass (0 floating-point conversions, 0 delay, 0 dither)
 *  - Frame-Major 64-bit Float Processing (Preamp -> 10-Band TDF-II PEQ -> DynamicsCompressor -> Volume -> Peak Limiter)
 *  - Anti-click live toggle handling: pre-fill lookahead + 8 ms linear wet/dry crossfade
 *  - Sustained 64-frame digital-silence dither gate + 1.0x multi-channel TPDF dither & LE packing
 */
class DspPipeline {
public:
    static constexpr double kCrossfadeDurationSec = 0.008; // 8 ms smooth linear toggle crossfade
    static constexpr uint32_t kSilenceGateFrames = 64U;    // ~1.45 ms @ 44.1 kHz before gating dither off

    void resetRuntimeState(uint32_t sampleRate) {
        const uint32_t sr = sampleRate > 0 ? sampleRate : 48000U;
        limiter.reset(sr);
        compressor.reset(sr);
        consecutiveSilentFrames = 0;
        wetMix = 0.0;
        limiterPrefillRemaining = 0;
        prevAnyModuleActive = false;
        prevCompEffective = false;
    }

    double getWetMixForTest() const { return wetMix; }
    int getLimiterPrefillRemainingForTest() const { return limiterPrefillRemaining; }
    bool isCompressorBypassRampingForTest() const { return compressor.isBypassRamping(); }
    int getLimiterLookaheadFrames() const { return limiter.getLookaheadFrames(); }

    void processBlock(
        const DspProcessParams &params,
        const PeqCoeffBank &peqBank,
        BiquadState (&peqFilterStates)[kMaxPeqChannels][kNumPeqBands],
        const CompressorCoeffBank &compBank,
        DspProcessResult &outResult
    ) {
        const uint32_t activeSr = (params.sampleRate > 0U) ? params.sampleRate : 48000U;
        const size_t numSamples = params.frames * static_cast<size_t>(params.channels);
        const bool peqEffective = params.peqEnabled && peqBank.hasActiveFilter;
        const bool compEffective = params.compEnabled && compBank.hasActiveProcessing;
        const bool anyModuleActive = peqEffective || compEffective;

        if (params.forceStateReset) {
            for (int ch = 0; ch < kMaxPeqChannels; ++ch) {
                for (int b = 0; b < kNumPeqBands; ++b) {
                    peqFilterStates[ch][b].reset();
                }
            }
            compressor.reset(activeSr);
            limiter.reset(activeSr);
            consecutiveSilentFrames = 0;
            limiterPrefillRemaining = 0;
            wetMix = anyModuleActive ? 1.0 : 0.0;
            prevAnyModuleActive = anyModuleActive;
            prevCompEffective = compEffective;
        } else {
            limiter.ensureSampleRate(activeSr);
            if (params.peqForceStateReset) {
                for (int ch = 0; ch < kMaxPeqChannels; ++ch) {
                    for (int b = 0; b < kNumPeqBands; ++b) {
                        peqFilterStates[ch][b].reset();
                    }
                }
            }

            // Per-module smooth bypass state machine for DynamicsCompressor:
            // - Mid-ramp re-enable (OFF -> ON): cancel ramp WITHOUT hard-resetting smoothedGainDb (0 click!)
            // - Cold enable (OFF -> ON while idle): reset stale sidechain HPF/RMS cleanly
            // - Live disable (ON -> OFF): begin 9 ms linear dB ramp to 0.0 dB even if PEQ stays ON
            const bool resumingCompMidRamp = compEffective && compressor.isBypassRamping();
            if (compEffective) {
                if (resumingCompMidRamp) {
                    compressor.cancelBypassRamp();
                } else if (params.compForceStateReset) {
                    compressor.reset(activeSr);
                }
            } else {
                if (prevCompEffective) {
                    compressor.beginBypassRamp(activeSr);
                } else if (params.compForceStateReset && !compressor.isBypassRamping()) {
                    compressor.reset(activeSr);
                }
            }

            if (anyModuleActive && !prevAnyModuleActive) {
                // Live OFF -> ON toggle from full bypass: pre-fill lookahead cleanly then fade in over 8 ms
                // (If wetMix > 0 because user re-enabled within the 8 ms ramp-down window, reverse cleanly.)
                if (wetMix <= 0.0001) {
                    for (int ch = 0; ch < kMaxPeqChannels; ++ch) {
                        for (int b = 0; b < kNumPeqBands; ++b) {
                            peqFilterStates[ch][b].reset();
                        }
                    }
                    if (!resumingCompMidRamp) {
                        compressor.reset(activeSr);
                    }
                    limiter.reset(activeSr);
                    limiterPrefillRemaining = limiter.getLookaheadFrames();
                    wetMix = 0.0;
                }
            }
            prevAnyModuleActive = anyModuleActive;
            prevCompEffective = compEffective;
        }

        const bool compRampActive = !compEffective && compressor.isBypassRamping();
        const bool crossfadeActive = (!anyModuleActive && (wetMix > 0.0 || limiterPrefillRemaining > 0));

        // 15 ms true linear click-free volume ramp step per frame
        double curVol = params.currentVolume;
        const double tgtVol = params.targetVolume;
        const double maxStepPerFrame = 1.0 / std::max(64.0, static_cast<double>(activeSr) * 0.015);
        double volDelta = 0.0;
        if (std::abs(tgtVol - curVol) > 0.00005) {
            volDelta = (tgtVol > curVol) ? maxStepPerFrame : -maxStepPerFrame;
        } else {
            curVol = tgtVol;
        }

        // BIT-PERFECT INTEGER DIRECT PASS:
        // Active only when no DSP module is engaged, crossfade & bypass ramp have finished, and volume is unity (1.0).
        if (!anyModuleActive && !crossfadeActive && !compRampActive && curVol >= 0.9999 && tgtVol >= 0.9999 && volDelta == 0.0) {
            outResult.finalVolume = 1.0;
            outResult.bitPerfectDirectPass = true;
            outResult.compressorGainDb = 0.0f;
            outResult.limiterGainReductionDb = 0.0f;
            outResult.peqEffective = false;
            outResult.compEffective = false;
            packBitPerfectInteger(params, numSamples);
            return;
        }

        outResult.bitPerfectDirectPass = false;
        outResult.compressorGainDb = 0.0f;
        outResult.limiterGainReductionDb = 0.0f;
        outResult.peqEffective = peqEffective;
        outResult.compEffective = compEffective;

        const uint32_t chCount = std::min<uint32_t>(params.channels, static_cast<uint32_t>(kMaxPeqChannels));
        const bool need24QuantIn32 = (params.subframeSize == 4 && params.bitResolution == 24);
        const bool limiterEnabled = anyModuleActive || crossfadeActive || compRampActive;
        const double crossfadeStep = 1.0 / std::max(64.0, static_cast<double>(activeSr) * kCrossfadeDurationSec);
        double minLimiterGain = 1.0;

        // FRAME-MAJOR 64-Bit Floating-Point DSP Loop (zero `% channels`, zero per-frame atomic CAS, stereo-linked!)
        for (size_t f = 0; f < params.frames; ++f) {
            if (volDelta != 0.0) {
                curVol += volDelta;
                if ((volDelta > 0.0 && curVol >= tgtVol) ||
                    (volDelta < 0.0 && curVol <= tgtVol)) {
                    curVol = tgtVol;
                    volDelta = 0.0;
                }
            }
            const double frameVol = std::max(0.0, std::min(1.0, curVol));
            const size_t baseS = f * static_cast<size_t>(params.channels);

            double dryScaled[kMaxPeqChannels];
            double wetSamples[kMaxPeqChannels];
            bool frameIsSilent = true;

            // Stage 1: Master Preamp + 10-Band TDF-II Biquad Cascade
            for (uint32_t ch = 0; ch < chCount; ++ch) {
                const int32_t rawIn = params.src[baseS + ch];
                if (rawIn != 0) frameIsSilent = false;

                const double dryRaw = static_cast<double>(rawIn);
                const double dryVolScaled = dryRaw * frameVol;
                dryScaled[ch] = dryVolScaled;
                if (std::abs(dryVolScaled) >= 1.0) frameIsSilent = false;

                double x = dryRaw;
                if (peqEffective) {
                    x *= peqBank.preampLinear;
                    for (int b = 0; b < kNumPeqBands; ++b) {
                        const BiquadCoeffs &c = peqBank.bands[b];
                        if (!c.isIdentity) {
                            BiquadState &st = peqFilterStates[ch][b];
                            const double y = c.b0 * x + st.z1;
                            st.z1 = c.b1 * x - c.a1 * y + st.z2;
                            st.z2 = c.b2 * x - c.a2 * y;
                            x = y;
                        }
                    }
                }
                wetSamples[ch] = x;
            }

            // Stage 1b: Stereo-Linked Dynamics Compressor (or smooth 9 ms per-module bypass ramp) BEFORE software volume
            if (compEffective) {
                compressor.processFrame(wetSamples, chCount, compBank);
            } else if (compressor.isBypassRamping()) {
                compressor.processBypassRampFrame(wetSamples, chCount);
            }

            // Stage 1c: Frame-Locked Software Volume Scaling (post-PEQ, post-Compressor, pre-Limiter)
            for (uint32_t ch = 0; ch < chCount; ++ch) {
                const double wetVolScaled = wetSamples[ch] * frameVol;
                if (std::abs(wetVolScaled) >= 1.0) frameIsSilent = false;
                wetSamples[ch] = wetVolScaled;
            }

            // Stage 2: Stereo-Linked Lookahead Peak Limiter (-0.10 dBFS linear core, 0.000 dB ILD shift)
            if (limiterEnabled) {
                limiter.processFrame(wetSamples, chCount);
                const double lg = limiter.getCurrentGain();
                if (lg < minLimiterGain) {
                    minLimiterGain = lg;
                }
                for (uint32_t ch = 0; ch < chCount; ++ch) {
                    if (std::abs(wetSamples[ch]) >= 1.0) frameIsSilent = false;
                }
            }

            // Stage 3: Anti-Click Wet/Dry Linear Crossfade (pre-fills lookahead before ramping wetMix 0 -> 1)
            double frameWetMix = 1.0;
            if (anyModuleActive) {
                if (limiterPrefillRemaining > 0) {
                    limiterPrefillRemaining--;
                    frameWetMix = 0.0;
                    wetMix = 0.0;
                } else if (wetMix < 1.0) {
                    wetMix = std::min(1.0, wetMix + crossfadeStep);
                    frameWetMix = wetMix;
                } else {
                    frameWetMix = 1.0;
                }
            } else if (wetMix > 0.0 || limiterPrefillRemaining > 0) {
                limiterPrefillRemaining = 0;
                wetMix = std::max(0.0, wetMix - crossfadeStep);
                frameWetMix = wetMix;
            } else {
                frameWetMix = 0.0;
            }

            double finalSamples[kMaxPeqChannels];
            if (frameWetMix >= 1.0) {
                for (uint32_t ch = 0; ch < chCount; ++ch) {
                    finalSamples[ch] = wetSamples[ch];
                }
            } else if (frameWetMix <= 0.0) {
                for (uint32_t ch = 0; ch < chCount; ++ch) {
                    finalSamples[ch] = dryScaled[ch];
                }
            } else {
                const double dryMix = 1.0 - frameWetMix;
                for (uint32_t ch = 0; ch < chCount; ++ch) {
                    finalSamples[ch] = dryScaled[ch] * dryMix + wetSamples[ch] * frameWetMix;
                }
            }

            // Stage 4: Sustained Digital-Silence Dither Gate (prevents zero-crossing dither pumping!)
            if (frameIsSilent) {
                if (consecutiveSilentFrames < kSilenceGateFrames) {
                    consecutiveSilentFrames++;
                }
            } else {
                consecutiveSilentFrames = 0;
            }
            const bool applyDither =
                (consecutiveSilentFrames < kSilenceGateFrames) &&
                (anyModuleActive || compRampActive || (frameWetMix > 0.0) || (frameVol < 0.9999 && frameVol > 0.00001));

            // Stage 5: Exact 1.0x Multi-Channel Decorrelated TPDF Dither & LE Quantization
            packDitheredFrame(params, baseS, chCount, need24QuantIn32, applyDither, finalSamples);
        }

        outResult.finalVolume = curVol;
        outResult.compressorGainDb = (compEffective || compressor.isBypassRamping())
            ? compressor.getLastAppliedGainDb()
            : 0.0f;
        outResult.limiterGainReductionDb = (minLimiterGain < 0.9999)
            ? static_cast<float>(20.0 * std::log10(minLimiterGain))
            : 0.0f;
        outResult.peqEffective = peqEffective;
        outResult.compEffective = compEffective;
    }

private:
    DynamicsCompressor compressor;
    StereoLinkedPeakLimiter limiter;
    MultiChannelTpdfDither dither;
    uint32_t consecutiveSilentFrames = 0;
    double wetMix = 0.0;
    int limiterPrefillRemaining = 0;
    bool prevAnyModuleActive = false;
    bool prevCompEffective = false;

    static inline void packBitPerfectInteger(const DspProcessParams &params, size_t numSamples) {
        const int32_t *src = params.src;
        uint8_t *dstBytes = params.dstBytes;
        const int sfSize = params.subframeSize;
        const int bitRes = params.bitResolution;
        const int srcBits = params.sourceBitDepth;

        if (sfSize == 4) {
            if (bitRes == 24 && srcBits > 24) {
                for (size_t s = 0; s < numSamples; ++s) {
                    int64_t val = (static_cast<int64_t>(src[s]) + 0x80LL) >> 8;
                    if (val > 8388607LL) val = 8388607LL;
                    if (val < -8388608LL) val = -8388608LL;
                    const int32_t val32 = static_cast<int32_t>(static_cast<uint32_t>(static_cast<int32_t>(val)) << 8);
                    dstBytes[s * 4]     = static_cast<uint8_t>(val32 & 0xFF);
                    dstBytes[s * 4 + 1] = static_cast<uint8_t>((val32 >> 8) & 0xFF);
                    dstBytes[s * 4 + 2] = static_cast<uint8_t>((val32 >> 16) & 0xFF);
                    dstBytes[s * 4 + 3] = static_cast<uint8_t>((val32 >> 24) & 0xFF);
                }
            } else {
                std::memcpy(dstBytes, src, numSamples * sizeof(int32_t));
            }
        } else if (sfSize == 2) {
            if (srcBits > 16) {
                for (size_t s = 0; s < numSamples; ++s) {
                    int64_t val = (static_cast<int64_t>(src[s]) + 0x8000LL) >> 16;
                    if (val > 32767LL) val = 32767LL;
                    if (val < -32768LL) val = -32768LL;
                    const int32_t val16 = static_cast<int32_t>(val);
                    dstBytes[s * 2]     = static_cast<uint8_t>(val16 & 0xFF);
                    dstBytes[s * 2 + 1] = static_cast<uint8_t>((val16 >> 8) & 0xFF);
                }
            } else {
                for (size_t s = 0; s < numSamples; ++s) {
                    const int32_t val16 = src[s] >> 16;
                    dstBytes[s * 2]     = static_cast<uint8_t>(val16 & 0xFF);
                    dstBytes[s * 2 + 1] = static_cast<uint8_t>((val16 >> 8) & 0xFF);
                }
            }
        } else if (sfSize == 3) {
            if (srcBits > 24) {
                for (size_t s = 0; s < numSamples; ++s) {
                    int64_t val = (static_cast<int64_t>(src[s]) + 0x80LL) >> 8;
                    if (val > 8388607LL) val = 8388607LL;
                    if (val < -8388608LL) val = -8388608LL;
                    const int32_t val24 = static_cast<int32_t>(val);
                    dstBytes[s * 3]     = static_cast<uint8_t>(val24 & 0xFF);
                    dstBytes[s * 3 + 1] = static_cast<uint8_t>((val24 >> 8) & 0xFF);
                    dstBytes[s * 3 + 2] = static_cast<uint8_t>((val24 >> 16) & 0xFF);
                }
            } else {
                for (size_t s = 0; s < numSamples; ++s) {
                    const int32_t val24 = src[s] >> 8;
                    dstBytes[s * 3]     = static_cast<uint8_t>(val24 & 0xFF);
                    dstBytes[s * 3 + 1] = static_cast<uint8_t>((val24 >> 8) & 0xFF);
                    dstBytes[s * 3 + 2] = static_cast<uint8_t>((val24 >> 16) & 0xFF);
                }
            }
        }
    }

    inline void packDitheredFrame(
        const DspProcessParams &params,
        size_t baseS,
        uint32_t chCount,
        bool need24QuantIn32,
        bool applyDither,
        const double *finalSamples
    ) {
        uint8_t *dstBytes = params.dstBytes;
        const int sfSize = params.subframeSize;

        for (uint32_t ch = 0; ch < chCount; ++ch) {
            const size_t s = baseS + ch;
            double scaled32 = finalSamples[ch];

            if (sfSize == 4) {
                if (need24QuantIn32) {
                    double scaled24 = scaled32 * (1.0 / 256.0);
                    if (applyDither) {
                        scaled24 += dither.nextTpdf(ch);
                    }
                    int64_t q24 = std::llrint(scaled24);
                    if (q24 > 8388607LL) q24 = 8388607LL;
                    if (q24 < -8388608LL) q24 = -8388608LL;
                    const int32_t val32 = static_cast<int32_t>(static_cast<uint32_t>(static_cast<int32_t>(q24)) << 8);
                    dstBytes[s * 4]     = static_cast<uint8_t>(val32 & 0xFF);
                    dstBytes[s * 4 + 1] = static_cast<uint8_t>((val32 >> 8) & 0xFF);
                    dstBytes[s * 4 + 2] = static_cast<uint8_t>((val32 >> 16) & 0xFF);
                    dstBytes[s * 4 + 3] = static_cast<uint8_t>((val32 >> 24) & 0xFF);
                } else {
                    if (applyDither) {
                        scaled32 += dither.nextTpdf(ch) * 256.0;
                    }
                    int64_t q32 = std::llrint(scaled32);
                    if (q32 > 2147483647LL) q32 = 2147483647LL;
                    if (q32 < -2147483648LL) q32 = -2147483648LL;
                    const int32_t val32 = static_cast<int32_t>(q32);
                    dstBytes[s * 4]     = static_cast<uint8_t>(val32 & 0xFF);
                    dstBytes[s * 4 + 1] = static_cast<uint8_t>((val32 >> 8) & 0xFF);
                    dstBytes[s * 4 + 2] = static_cast<uint8_t>((val32 >> 16) & 0xFF);
                    dstBytes[s * 4 + 3] = static_cast<uint8_t>((val32 >> 24) & 0xFF);
                }
            } else if (sfSize == 3) {
                double scaled24 = scaled32 * (1.0 / 256.0);
                if (applyDither) {
                    scaled24 += dither.nextTpdf(ch);
                }
                int64_t q24 = std::llrint(scaled24);
                if (q24 > 8388607LL) q24 = 8388607LL;
                if (q24 < -8388608LL) q24 = -8388608LL;
                const int32_t val24 = static_cast<int32_t>(q24);
                dstBytes[s * 3]     = static_cast<uint8_t>(val24 & 0xFF);
                dstBytes[s * 3 + 1] = static_cast<uint8_t>((val24 >> 8) & 0xFF);
                dstBytes[s * 3 + 2] = static_cast<uint8_t>((val24 >> 16) & 0xFF);
            } else if (sfSize == 2) {
                double scaled16 = scaled32 * (1.0 / 65536.0);
                if (applyDither) {
                    scaled16 += dither.nextTpdf(ch);
                }
                int64_t q16 = std::llrint(scaled16);
                if (q16 > 32767LL) q16 = 32767LL;
                if (q16 < -32768LL) q16 = -32768LL;
                const int32_t val16 = static_cast<int32_t>(q16);
                dstBytes[s * 2]     = static_cast<uint8_t>(val16 & 0xFF);
                dstBytes[s * 2 + 1] = static_cast<uint8_t>((val16 >> 8) & 0xFF);
            }
        }
    }
};

#endif // TSROSSA_DSP_PIPELINE_H
