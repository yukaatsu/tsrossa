#include <cassert>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <vector>

#include "DspPipeline.h"

namespace {

constexpr uint32_t kSr = 48000U;
constexpr uint32_t kCh = 2U;

void generateSineBlock(
    std::vector<int32_t> &buf,
    size_t frames,
    uint64_t &sampleOffset,
    double freqHz,
    double ampLinearFs
) {
    buf.resize(frames * kCh);
    constexpr double kTwoPi = 6.28318530717958647692;
    const double peakInt = ampLinearFs * 2147483647.0;
    for (size_t f = 0; f < frames; ++f) {
        const double phase = kTwoPi * freqHz * static_cast<double>(sampleOffset + f) / static_cast<double>(kSr);
        const int32_t v = static_cast<int32_t>(std::llround(std::sin(phase) * peakInt));
        buf[f * 2] = v;
        buf[f * 2 + 1] = v;
    }
    sampleOffset += frames;
}

CompressorCoeffBank makeActiveCompBank(DynamicsCompressorState &compState, bool enabled) {
    compState.setFullConfig(
        enabled,
        -20.0f, // downThresholdDb
        4.0f,   // downRatio
        -36.0f, // upThresholdDb
        2.0f,   // upRatio
        6.0f,   // maxUpwardBoostDb
        -65.0f, // floorThresholdDb
        4.0f,   // kneeDb
        5.0f,   // attackMs
        120.0f, // releaseMs
        3.0f,   // makeupGainDb
        90.0f,  // sidechainHpfHz
        true,   // useRmsDetector
        kSr
    );
    return compState.snapshotActiveBank();
}

PeqCoeffBank makeActivePeqBank(ParametricEqState &peqState, bool enabled) {
    float freqs[kNumPeqBands] = {31.f, 62.f, 125.f, 250.f, 500.f, 1000.f, 2000.f, 4000.f, 8000.f, 16000.f};
    float gains[kNumPeqBands] = {0.f, 0.f, 0.f, 0.f, 0.f, 3.0f, 0.f, 0.f, 0.f, 0.f};
    float qs[kNumPeqBands]    = {0.707f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 0.707f};
    int types[kNumPeqBands]   = {1, 0, 0, 0, 0, 0, 0, 0, 0, 2};
    peqState.setFullConfig(enabled, 0.0f, freqs, gains, qs, types, kNumPeqBands, kSr);
    return peqState.snapshotActiveBank();
}

void test_comp_bypass_ramp_smooth_while_peq_stays_on() {
    DspPipeline pipeline;
    pipeline.resetRuntimeState(kSr);

    ParametricEqState peqState;
    DynamicsCompressorState compState;
    PeqCoeffBank peqBank = makeActivePeqBank(peqState, true);
    CompressorCoeffBank compBank = makeActiveCompBank(compState, true);

    const size_t blockFrames = 48; // 1 ms blocks
    std::vector<int32_t> src;
    std::vector<uint8_t> dst(blockFrames * kCh * 4);
    uint64_t offset = 0;

    DspProcessParams params{};
    params.frames = blockFrames;
    params.channels = kCh;
    params.sampleRate = kSr;
    params.subframeSize = 4;
    params.bitResolution = 32;
    params.sourceBitDepth = 24;
    params.currentVolume = 1.0;
    params.targetVolume = 1.0;
    params.peqEnabled = true;
    params.compEnabled = true;

    DspProcessResult res{};
    for (int i = 0; i < 45; ++i) { // 45 ms warm-up on -6 dBFS sine
        generateSineBlock(src, blockFrames, offset, 1000.0, 0.5);
        params.src = src.data();
        params.dstBytes = dst.data();
        params.forceStateReset = peqState.consumeStateResetIfPending();
        params.compForceStateReset = compState.consumeStateResetIfPending();
        params.currentVolume = res.finalVolume;
        pipeline.processBlock(params, peqBank, peqState.filterStates, compBank, res);
    }

    assert(!res.bitPerfectDirectPass);
    assert(res.compressorGainDb < -3.0f);

    // Disable Compressor live while PEQ remains ON (via setFullConfig(false, ...))
    compBank = makeActiveCompBank(compState, false);
    params.compEnabled = false;
    params.peqEnabled = true;

    float prevGainDb = res.compressorGainDb;
    bool rampObserved = false;

    for (int i = 0; i < 20; ++i) { // 20 ms
        generateSineBlock(src, blockFrames, offset, 1000.0, 0.5);
        params.src = src.data();
        params.dstBytes = dst.data();
        params.forceStateReset = false;
        params.compForceStateReset = compState.consumeStateResetIfPending();
        params.currentVolume = res.finalVolume;
        pipeline.processBlock(params, peqBank, peqState.filterStates, compBank, res);

        if (pipeline.isCompressorBypassRampingForTest()) {
            rampObserved = true;
        }
        // Each 1 ms block (~9 ms total ramp of ~9 dB) should change smoothly (~<= 1.2 dB/ms)
        const float stepDiff = std::abs(res.compressorGainDb - prevGainDb);
        assert(stepDiff < 1.5f);
        assert(res.compressorGainDb >= prevGainDb - 0.001f); // rising smoothly from negative GR toward 0
        assert(!res.bitPerfectDirectPass);
        prevGainDb = res.compressorGainDb;
    }

    assert(rampObserved);
    assert(!pipeline.isCompressorBypassRampingForTest());
    assert(std::abs(res.compressorGainDb) < 1e-5f);
    std::cout << "[PASS] test_comp_bypass_ramp_smooth_while_peq_stays_on\n";
}

void test_comp_reenable_mid_bypass_ramp_no_discontinuity() {
    DspPipeline pipeline;
    pipeline.resetRuntimeState(kSr);

    ParametricEqState peqState;
    DynamicsCompressorState compState;
    PeqCoeffBank peqBank = makeActivePeqBank(peqState, true);
    CompressorCoeffBank compBank = makeActiveCompBank(compState, true);

    const size_t blockFrames = 48; // 1 ms
    std::vector<int32_t> src;
    std::vector<uint8_t> dst(blockFrames * kCh * 4);
    uint64_t offset = 0;

    DspProcessParams params{};
    params.frames = blockFrames;
    params.channels = kCh;
    params.sampleRate = kSr;
    params.subframeSize = 4;
    params.bitResolution = 32;
    params.sourceBitDepth = 24;
    params.currentVolume = 1.0;
    params.targetVolume = 1.0;
    params.peqEnabled = true;
    params.compEnabled = true;

    DspProcessResult res{};
    for (int i = 0; i < 45; ++i) {
        generateSineBlock(src, blockFrames, offset, 1000.0, 0.5);
        params.src = src.data();
        params.dstBytes = dst.data();
        params.forceStateReset = peqState.consumeStateResetIfPending();
        params.compForceStateReset = compState.consumeStateResetIfPending();
        params.currentVolume = res.finalVolume;
        pipeline.processBlock(params, peqBank, peqState.filterStates, compBank, res);
    }

    // Turn Compressor OFF for 4 ms via setFullConfig(false, ...) (~mid-way through 9 ms ramp)
    compBank = makeActiveCompBank(compState, false);
    params.compEnabled = false;
    for (int i = 0; i < 4; ++i) {
        generateSineBlock(src, blockFrames, offset, 1000.0, 0.5);
        params.src = src.data();
        params.dstBytes = dst.data();
        params.forceStateReset = false;
        params.compForceStateReset = compState.consumeStateResetIfPending();
        params.currentVolume = res.finalVolume;
        pipeline.processBlock(params, peqBank, peqState.filterStates, compBank, res);
    }

    assert(pipeline.isCompressorBypassRampingForTest());
    const float gainBeforeReenable = res.compressorGainDb;
    assert(gainBeforeReenable < -1.0f);

    // Re-enable Compressor mid-ramp WITH compForceStateReset=true (simulating setFullConfig)
    compBank = makeActiveCompBank(compState, true);
    params.compEnabled = true;
    params.compForceStateReset = compState.consumeStateResetIfPending();
    assert(params.compForceStateReset);

    // Process 1 frame to inspect exact continuity on re-enable sample
    generateSineBlock(src, 1, offset, 1000.0, 0.5);
    params.frames = 1;
    params.src = src.data();
    params.dstBytes = dst.data();
    params.currentVolume = res.finalVolume;
    pipeline.processBlock(params, peqBank, peqState.filterStates, compBank, res);

    assert(!pipeline.isCompressorBypassRampingForTest());
    assert(std::abs(res.compressorGainDb - gainBeforeReenable) < 0.25f);
    std::cout << "[PASS] test_comp_reenable_mid_bypass_ramp_no_discontinuity\n";
}

void test_both_modules_off_returns_to_bit_perfect_direct_pass() {
    DspPipeline pipeline;
    pipeline.resetRuntimeState(kSr);

    ParametricEqState peqState;
    DynamicsCompressorState compState;
    PeqCoeffBank peqBank = makeActivePeqBank(peqState, true);
    CompressorCoeffBank compBank = makeActiveCompBank(compState, true);

    const size_t blockFrames = 96; // 2 ms
    std::vector<int32_t> src;
    std::vector<uint8_t> dst(blockFrames * kCh * 4);
    uint64_t offset = 0;

    DspProcessParams params{};
    params.frames = blockFrames;
    params.channels = kCh;
    params.sampleRate = kSr;
    params.subframeSize = 4;
    params.bitResolution = 32;
    params.sourceBitDepth = 24;
    params.currentVolume = 1.0;
    params.targetVolume = 1.0;
    params.peqEnabled = true;
    params.compEnabled = true;

    DspProcessResult res{};
    for (int i = 0; i < 20; ++i) {
        generateSineBlock(src, blockFrames, offset, 1000.0, 0.5);
        params.src = src.data();
        params.dstBytes = dst.data();
        params.forceStateReset = peqState.consumeStateResetIfPending();
        params.compForceStateReset = compState.consumeStateResetIfPending();
        params.currentVolume = res.finalVolume;
        pipeline.processBlock(params, peqBank, peqState.filterStates, compBank, res);
    }

    // Disable BOTH PEQ and Compressor via real config update path
    peqState.enabled.store(false, std::memory_order_release);
    compBank = makeActiveCompBank(compState, false);
    params.peqEnabled = false;
    params.compEnabled = false;
    for (int i = 0; i < 12; ++i) { // 24 ms (> max(8ms crossfade, 9ms bypass ramp))
        generateSineBlock(src, blockFrames, offset, 1000.0, 0.5);
        params.src = src.data();
        params.dstBytes = dst.data();
        params.forceStateReset = false;
        params.compForceStateReset = compState.consumeStateResetIfPending();
        params.currentVolume = res.finalVolume;
        pipeline.processBlock(params, peqBank, peqState.filterStates, compBank, res);
    }

    // Verify full return to Bit-Perfect Integer Direct Pass with bit-for-bit identity
    generateSineBlock(src, blockFrames, offset, 1000.0, 0.5);
    params.src = src.data();
    params.dstBytes = dst.data();
    params.currentVolume = res.finalVolume;
    pipeline.processBlock(params, peqBank, peqState.filterStates, compBank, res);

    assert(res.bitPerfectDirectPass);
    assert(res.compressorGainDb == 0.0f);
    assert(!pipeline.isCompressorBypassRampingForTest());
    assert(std::memcmp(dst.data(), src.data(), src.size() * sizeof(int32_t)) == 0);
    std::cout << "[PASS] test_both_modules_off_returns_to_bit_perfect_direct_pass\n";
}

void test_gate_transition_6db_aligned_with_floor_margin() {
    DynamicsCompressorState compState;
    compState.setFullConfig(
        true,
        -16.0f, // downThresholdDb
        1.0f,   // downRatio OFF
        -34.0f, // upThresholdDb
        2.0f,   // upRatio
        6.0f,   // maxUpwardBoostDb
        -40.0f, // floorThresholdDb (exact 6 dB margin below upThresholdDb)
        0.0f,   // hard knee for exact analytical check
        8.0f,
        160.0f,
        0.0f,
        90.0f,
        true,
        kSr
    );
    const CompressorCoeffBank bank = compState.snapshotActiveBank();
    assert(std::abs(bank.gateTransitionDb - 6.0) < 1e-9);

    const double atFloor = DynamicsCompressor::computeStaticGainDb(-40.0, bank);
    const double midGate = DynamicsCompressor::computeStaticGainDb(-37.0, bank);
    const double nearUp = DynamicsCompressor::computeStaticGainDb(-34.001, bank);

    assert(std::abs(atFloor - 0.0) < 1e-9);
    // At -37 dBFS: raw upward boost = (-34 - (-37)) * 0.5 = 1.5 dB; smoothstep(0.5) = 0.5 -> +0.75 dB
    assert(std::abs(midGate - 0.75) < 1e-9);
    // Just below upThreshold (-34.001 dBFS): gateFactor == 1.0 -> boost = 0.001 * 0.5 = 0.0005 dB
    assert(std::abs(nearUp - 0.0005) < 1e-9);
    std::cout << "[PASS] test_gate_transition_6db_aligned_with_floor_margin\n";
}

double dftToneMagDb(const std::vector<double> &signal, double fs, double freqHz) {
    const size_t N = signal.size();
    constexpr double kTwoPi = 6.28318530717958647692;
    double re = 0.0;
    double im = 0.0;
    double winSum = 0.0;
    for (size_t n = 0; n < N; ++n) {
        const double w = 0.5 * (1.0 - std::cos(kTwoPi * static_cast<double>(n) / static_cast<double>(N - 1)));
        winSum += w;
        const double ang = kTwoPi * freqHz * static_cast<double>(n) / fs;
        re += signal[n] * w * std::cos(ang);
        im -= signal[n] * w * std::sin(ang);
    }
    const double mag = 2.0 * std::hypot(re, im) / winSum;
    return 20.0 * std::log10(std::max(mag, 1e-15));
}

void test_compressor_rms_and_peak_low_imd_sidebands() {
    constexpr uint32_t kSr96 = 96000U;
    constexpr double kFs = 96000.0;
    constexpr double kFullScale = 2147483648.0;
    constexpr double kTwoPi = 6.28318530717958647692;
    constexpr size_t kTotalFrames = 96000; // 1.0 s

    std::vector<int32_t> src(kTotalFrames * kCh);
    for (size_t n = 0; n < kTotalFrames; ++n) {
        const double t = static_cast<double>(n) / kFs;
        const double s = 0.63 * std::sin(kTwoPi * 60.0 * t)
                       + 0.31 * std::sin(kTwoPi * 1000.0 * t)
                       + 0.05 * std::sin(kTwoPi * 7000.0 * t);
        const int32_t q = static_cast<int32_t>(std::llround(s * kFullScale));
        src[n * 2] = q;
        src[n * 2 + 1] = q;
    }

    auto measureSidebandDbc = [&](bool useRms, float attackMs, float releaseMs) -> double {
        DspPipeline pipeline;
        pipeline.resetRuntimeState(kSr96);
        ParametricEqState peqState;
        DynamicsCompressorState compState;
        compState.setFullConfig(
            true,
            -16.0f, // downThresholdDb
            2.8f,   // downRatio
            -36.0f, // upThresholdDb
            2.2f,   // upRatio
            6.0f,   // maxUpwardBoostDb
            -65.0f, // floorThresholdDb
            6.0f,   // kneeDb
            attackMs,
            releaseMs,
            0.0f,   // makeupGainDb
            90.0f,  // sidechainHpfHz (2nd-order Butterworth)
            useRms,
            kSr96
        );

        std::vector<uint8_t> dst(kTotalFrames * kCh * 4);
        DspProcessResult res{};
        const PeqCoeffBank peqBank = peqState.snapshotActiveBank();
        const CompressorCoeffBank compBank = compState.snapshotActiveBank();

        for (size_t pos = 0; pos < kTotalFrames; pos += 12) {
            DspProcessParams p{};
            p.src = src.data() + pos * kCh;
            p.dstBytes = dst.data() + pos * kCh * 4;
            p.frames = std::min<size_t>(12, kTotalFrames - pos);
            p.channels = kCh;
            p.sampleRate = kSr96;
            p.subframeSize = 4;
            p.bitResolution = 32;
            p.sourceBitDepth = 24;
            p.currentVolume = 1.0;
            p.targetVolume = 1.0;
            p.peqEnabled = false;
            p.compEnabled = true;
            p.forceStateReset = (pos == 0);
            p.compForceStateReset = (pos == 0);
            pipeline.processBlock(p, peqBank, peqState.filterStates, compBank, res);
        }

        std::vector<double> settledL(48000);
        for (size_t i = 0; i < 48000; ++i) {
            int32_t s32 = 0;
            std::memcpy(&s32, dst.data() + ((48000 + i) * kCh) * 4, sizeof(int32_t));
            settledL[i] = static_cast<double>(s32) / kFullScale;
        }
        const double carrierDb = dftToneMagDb(settledL, kFs, 1000.0);
        const double sbLowDb = dftToneMagDb(settledL, kFs, 880.0);
        const double sbHighDb = dftToneMagDb(settledL, kFs, 1120.0);
        return std::max(sbLowDb, sbHighDb) - carrierDb;
    };

    const double rmsImdDbc = measureSidebandDbc(true, 15.0f, 250.0f);
    const double peakImdDbc = measureSidebandDbc(false, 5.0f, 100.0f);
    assert(rmsImdDbc < -85.0);
    assert(peakImdDbc < -82.0);
    std::cout << "[PASS] test_compressor_rms_and_peak_low_imd_sidebands (RMS="
              << rmsImdDbc << " dBc, Peak=" << peakImdDbc << " dBc)\n";
}

void test_compressor_verse_to_chorus_fast_upward_retract() {
    constexpr uint32_t kSr96 = 96000U;
    constexpr double kFs = 96000.0;
    constexpr double kFullScale = 2147483648.0;
    constexpr double kTwoPi = 6.28318530717958647692;
    constexpr size_t kHalfSec = 48000;
    constexpr size_t kTotalFrames = 96000;

    std::vector<int32_t> src(kTotalFrames * kCh);
    for (size_t n = 0; n < kTotalFrames; ++n) {
        const double t = static_cast<double>(n) / kFs;
        const double amp = (n < kHalfSec) ? 0.01 : 0.988; // -40 dBFS verse -> -0.1 dBFS chorus
        const int32_t q = static_cast<int32_t>(std::llround(amp * std::sin(kTwoPi * 500.0 * t) * kFullScale));
        src[n * 2] = q;
        src[n * 2 + 1] = q;
    }

    auto measureChorusPosGainMs = [&](float downThresh, float downRat, float upThresh, float upRat,
                                      float maxUp, float floorThresh, float attMs, float relMs) -> double {
        DspPipeline pipeline;
        pipeline.resetRuntimeState(kSr96);
        ParametricEqState peqState;
        DynamicsCompressorState compState;
        compState.setFullConfig(
            true, downThresh, downRat, upThresh, upRat, maxUp, floorThresh,
            6.0f, attMs, relMs, 0.0f, 90.0f, true, kSr96
        );
        const PeqCoeffBank peqBank = peqState.snapshotActiveBank();
        const CompressorCoeffBank compBank = compState.snapshotActiveBank();

        std::vector<uint8_t> dst(12 * kCh * 4);
        DspProcessResult res{};
        size_t posGainFrames = 0;

        for (size_t pos = 0; pos < kTotalFrames; pos += 12) {
            const size_t chunk = std::min<size_t>(12, kTotalFrames - pos);
            DspProcessParams p{};
            p.src = src.data() + pos * kCh;
            p.dstBytes = dst.data();
            p.frames = chunk;
            p.channels = kCh;
            p.sampleRate = kSr96;
            p.subframeSize = 4;
            p.bitResolution = 32;
            p.sourceBitDepth = 24;
            p.currentVolume = 1.0;
            p.targetVolume = 1.0;
            p.peqEnabled = false;
            p.compEnabled = true;
            p.forceStateReset = (pos == 0);
            p.compForceStateReset = (pos == 0);
            pipeline.processBlock(p, peqBank, peqState.filterStates, compBank, res);
            if (pos >= kHalfSec && res.compressorGainDb > 0.05f) {
                posGainFrames += chunk;
            }
        }
        return static_cast<double>(posGainFrames) * 1000.0 / kFs;
    };

    const double lowVolWindowMs = measureChorusPosGainMs(-14.0f, 2.0f, -38.0f, 2.0f, 5.5f, -66.0f, 18.0f, 240.0f);
    const double nightWindowMs = measureChorusPosGainMs(-20.0f, 4.0f, -34.0f, 2.5f, 7.0f, -64.0f, 8.0f, 260.0f);
    assert(lowVolWindowMs < 3.5);
    assert(nightWindowMs < 3.5);
    std::cout << "[PASS] test_compressor_verse_to_chorus_fast_upward_retract (LowVol="
              << lowVolWindowMs << " ms, Night=" << nightWindowMs << " ms)\n";
}

void test_live_peq_toggle_uses_crossfade_zero_silence_hole_and_preserves_comp() {
    // Subtest A: Live PEQ OFF -> ON with Compressor OFF uses 8ms crossfade & lookahead prefill (0 silence hole!)
    {
        DspPipeline pipeline;
        pipeline.resetRuntimeState(96000U);
        ParametricEqState peqState;
        DynamicsCompressorState compState;

        float freqs[kNumPeqBands] = {31.f, 62.f, 125.f, 250.f, 500.f, 1000.f, 2000.f, 4000.f, 8000.f, 16000.f};
        float gains[kNumPeqBands] = {2.f, 2.f, 0.f, -2.f, 0.f, 1.f, 1.f, 1.f, 0.f, 2.f};
        float qs[kNumPeqBands]    = {0.707f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 0.707f};
        int types[kNumPeqBands]   = {1, 0, 0, 0, 0, 0, 0, 0, 0, 2};
        peqState.setFullConfig(false, -3.0f, freqs, gains, qs, types, kNumPeqBands, 96000U);

        constexpr size_t kBlock = 128;
        std::vector<int32_t> src(kBlock * kCh);
        for (size_t i = 0; i < kBlock; ++i) {
            const double val = 0.5 * std::sin(6.28318530717958647692 * 1000.0 * i / 96000.0) * 2147483647.0;
            const int32_t q = static_cast<int32_t>(std::llround(val));
            src[i * 2] = q;
            src[i * 2 + 1] = q;
        }
        std::vector<uint8_t> dst(kBlock * kCh * 4);
        DspProcessResult res{};

        // Prime initial steady state with PEQ OFF
        DspProcessParams p1{};
        p1.src = src.data();
        p1.dstBytes = dst.data();
        p1.frames = kBlock;
        p1.channels = kCh;
        p1.sampleRate = 96000U;
        p1.subframeSize = 4;
        p1.bitResolution = 32;
        p1.sourceBitDepth = 24;
        p1.currentVolume = 1.0;
        p1.targetVolume = 1.0;
        p1.peqEnabled = false;
        p1.compEnabled = false;
        p1.forceStateReset = true;
        pipeline.processBlock(p1, peqState.snapshotActiveBank(), peqState.filterStates, compState.snapshotActiveBank(), res);
        assert(res.bitPerfectDirectPass);

        // Toggle PEQ ON live using peqForceStateReset (NOT forceStateReset!)
        peqState.setFullConfig(true, -3.0f, freqs, gains, qs, types, kNumPeqBands, 96000U);
        DspProcessParams p2 = p1;
        p2.peqEnabled = true;
        p2.forceStateReset = false;
        p2.peqForceStateReset = peqState.consumeStateResetIfPending();
        assert(p2.peqForceStateReset);
        pipeline.processBlock(p2, peqState.snapshotActiveBank(), peqState.filterStates, compState.snapshotActiveBank(), res);

        int nearZeroFrames = 0;
        int32_t peakAbs = 0;
        for (size_t i = 0; i < kBlock; ++i) {
            int32_t l32 = 0;
            std::memcpy(&l32, dst.data() + i * kCh * 4, sizeof(int32_t));
            const int32_t a = std::abs(l32);
            if (a > peakAbs) peakAbs = a;
            if (a < 1000) nearZeroFrames++;
        }
        // Only natural 1 kHz sine zero-crossings (frames 0, 48, 96 @ 96 kHz) can be near zero — zero silence hole!
        assert(nearZeroFrames <= 4);
        assert(peakAbs > 900000000);
    }

    // Subtest B: Live PEQ OFF -> ON while Compressor is active preserves Compressor gain state!
    {
        DspPipeline pipeline;
        pipeline.resetRuntimeState(kSr);
        ParametricEqState peqState;
        DynamicsCompressorState compState;
        CompressorCoeffBank compBank = makeActiveCompBank(compState, true);

        float freqs[kNumPeqBands] = {31.f, 62.f, 125.f, 250.f, 500.f, 1000.f, 2000.f, 4000.f, 8000.f, 16000.f};
        float gains[kNumPeqBands] = {0.f, 0.f, 0.f, 0.f, 0.f, 3.f, 0.f, 0.f, 0.f, 0.f};
        float qs[kNumPeqBands]    = {0.707f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 0.707f};
        int types[kNumPeqBands]   = {1, 0, 0, 0, 0, 0, 0, 0, 0, 2};
        peqState.setFullConfig(false, 0.0f, freqs, gains, qs, types, kNumPeqBands, kSr);

        const size_t blockFrames = 48;
        std::vector<int32_t> src;
        std::vector<uint8_t> dst(blockFrames * kCh * 4);
        uint64_t offset = 0;

        DspProcessParams params{};
        params.frames = blockFrames;
        params.channels = kCh;
        params.sampleRate = kSr;
        params.subframeSize = 4;
        params.bitResolution = 32;
        params.sourceBitDepth = 24;
        params.currentVolume = 1.0;
        params.targetVolume = 1.0;
        params.peqEnabled = false;
        params.compEnabled = true;

        DspProcessResult res{};
        for (int i = 0; i < 60; ++i) { // Engage compressor well past 30 ms RMS window
            generateSineBlock(src, blockFrames, offset, 1000.0, 0.5);
            params.src = src.data();
            params.dstBytes = dst.data();
            params.forceStateReset = (i == 0);
            params.compForceStateReset = compState.consumeStateResetIfPending();
            params.currentVolume = res.finalVolume;
            pipeline.processBlock(params, peqState.snapshotActiveBank(), peqState.filterStates, compBank, res);
        }
        const float compGainBefore = res.compressorGainDb;
        assert(compGainBefore < -2.0f);

        // Toggle PEQ ON live while Compressor continues uninterrupted
        peqState.setFullConfig(true, 0.0f, freqs, gains, qs, types, kNumPeqBands, kSr);
        generateSineBlock(src, 1, offset, 1000.0, 0.5);
        params.frames = 1;
        params.src = src.data();
        params.dstBytes = dst.data();
        params.peqEnabled = true;
        params.forceStateReset = false;
        params.peqForceStateReset = peqState.consumeStateResetIfPending();
        params.currentVolume = res.finalVolume;
        pipeline.processBlock(params, peqState.snapshotActiveBank(), peqState.filterStates, compBank, res);

        assert(std::abs(res.compressorGainDb - compGainBefore) < 0.25f);
    }

    std::cout << "[PASS] test_live_peq_toggle_uses_crossfade_zero_silence_hole_and_preserves_comp\n";
}

void test_bit_perfect_direct_pass_preserved_at_96khz_24bit() {
    DspPipeline pipeline;
    pipeline.resetRuntimeState(96000U);
    ParametricEqState peqState;
    DynamicsCompressorState compState;

    constexpr size_t kFrames = 64;
    std::vector<int32_t> src(kFrames * kCh);
    for (size_t f = 0; f < kFrames; ++f) {
        src[f * 2] = static_cast<int32_t>(((static_cast<int64_t>(f) * 131071LL) - 4194304LL) << 8);
        src[f * 2 + 1] = -src[f * 2];
    }
    std::vector<uint8_t> dst(kFrames * kCh * 3, 0xAA);

    DspProcessParams params{};
    params.src = src.data();
    params.dstBytes = dst.data();
    params.frames = kFrames;
    params.channels = kCh;
    params.sampleRate = 96000U;
    params.subframeSize = 3;
    params.bitResolution = 24;
    params.sourceBitDepth = 24;
    params.currentVolume = 1.0;
    params.targetVolume = 1.0;
    params.peqEnabled = false;
    params.compEnabled = false;
    params.forceStateReset = true;

    DspProcessResult res{};
    pipeline.processBlock(params, peqState.snapshotActiveBank(), peqState.filterStates, compState.snapshotActiveBank(), res);
    assert(res.bitPerfectDirectPass);
    assert(res.finalVolume == 1.0);
    assert(res.compressorGainDb == 0.0f);

    for (size_t s = 0; s < kFrames * kCh; ++s) {
        const int32_t v24 = src[s] >> 8;
        assert(dst[s * 3] == static_cast<uint8_t>(v24 & 0xFF));
        assert(dst[s * 3 + 1] == static_cast<uint8_t>((v24 >> 8) & 0xFF));
        assert(dst[s * 3 + 2] == static_cast<uint8_t>((v24 >> 16) & 0xFF));
    }
    std::cout << "[PASS] test_bit_perfect_direct_pass_preserved_at_96khz_24bit\n";
}

void test_limiter_gain_reduction_db_reported_accurately_and_zero_below_ceiling() {
    // Case A: Moderate -6 dBFS signal with PEQ (+3 dB -> -3 dBFS < -0.10 dBFS ceiling): limiter GR == 0.0f
    {
        DspPipeline pipeline;
        pipeline.resetRuntimeState(kSr);
        ParametricEqState peqState;
        DynamicsCompressorState compState;
        PeqCoeffBank peqBank = makeActivePeqBank(peqState, true);
        CompressorCoeffBank compBank = compState.snapshotActiveBank();

        const size_t blockFrames = 256;
        std::vector<int32_t> src;
        std::vector<uint8_t> dst(blockFrames * kCh * 4);
        uint64_t offset = 0;

        DspProcessParams params{};
        params.frames = blockFrames;
        params.channels = kCh;
        params.sampleRate = kSr;
        params.subframeSize = 4;
        params.bitResolution = 32;
        params.sourceBitDepth = 24;
        params.currentVolume = 1.0;
        params.targetVolume = 1.0;
        params.peqEnabled = true;
        params.compEnabled = false;

        DspProcessResult res{};
        for (int i = 0; i < 6; ++i) {
            generateSineBlock(src, blockFrames, offset, 1000.0, 0.5);
            params.src = src.data();
            params.dstBytes = dst.data();
            params.forceStateReset = (i == 0);
            pipeline.processBlock(params, peqBank, peqState.filterStates, compBank, res);
        }
        assert(res.peqEffective);
        assert(!res.compEffective);
        assert(!res.bitPerfectDirectPass);
        assert(res.limiterGainReductionDb == 0.0f);
    }

    // Case B: Overdrive full-scale 0 dBFS sine with +6 dB PEQ boost (0 dB preamp) -> limiter reduces peak below -2.0 dB
    {
        DspPipeline pipeline;
        pipeline.resetRuntimeState(kSr);
        ParametricEqState peqState;
        DynamicsCompressorState compState;
        float freqs[kNumPeqBands] = {31.f, 62.f, 125.f, 250.f, 500.f, 1000.f, 2000.f, 4000.f, 8000.f, 16000.f};
        float gains[kNumPeqBands] = {0.f, 0.f, 0.f, 0.f, 0.f, 6.0f, 0.f, 0.f, 0.f, 0.f};
        float qs[kNumPeqBands]    = {0.707f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 0.707f};
        int types[kNumPeqBands]   = {1, 0, 0, 0, 0, 0, 0, 0, 0, 2};
        peqState.setFullConfig(true, 0.0f, freqs, gains, qs, types, kNumPeqBands, kSr);
        PeqCoeffBank peqBank = peqState.snapshotActiveBank();
        CompressorCoeffBank compBank = compState.snapshotActiveBank();

        const size_t blockFrames = 256;
        std::vector<int32_t> src;
        std::vector<uint8_t> dst(blockFrames * kCh * 4);
        uint64_t offset = 0;

        DspProcessParams params{};
        params.frames = blockFrames;
        params.channels = kCh;
        params.sampleRate = kSr;
        params.subframeSize = 4;
        params.bitResolution = 32;
        params.sourceBitDepth = 24;
        params.currentVolume = 1.0;
        params.targetVolume = 1.0;
        params.peqEnabled = true;
        params.compEnabled = false;

        DspProcessResult res{};
        float minGrSeen = 0.0f;
        for (int i = 0; i < 6; ++i) {
            generateSineBlock(src, blockFrames, offset, 1000.0, 0.99);
            params.src = src.data();
            params.dstBytes = dst.data();
            params.forceStateReset = (i == 0);
            pipeline.processBlock(params, peqBank, peqState.filterStates, compBank, res);
            minGrSeen = std::min(minGrSeen, res.limiterGainReductionDb);
        }
        assert(minGrSeen < -2.0f);
    }

    std::cout << "[PASS] test_limiter_gain_reduction_db_reported_accurately_and_zero_below_ceiling\n";
}

void test_peq_and_comp_effective_flags_honor_true_bypass_semantics() {
    DspPipeline pipeline;
    pipeline.resetRuntimeState(kSr);
    ParametricEqState peqState;
    DynamicsCompressorState compState;

    const size_t blockFrames = 64;
    std::vector<int32_t> src;
    uint64_t offset = 0;
    generateSineBlock(src, blockFrames, offset, 1000.0, 0.5);
    std::vector<uint8_t> dst(blockFrames * kCh * 4);

    // 1. PEQ enabled with flat 0.0 dB bands and 0.0 dB preamp -> True Bypass (peqEffective == false, Bit-Perfect == true)
    float freqs[kNumPeqBands] = {31.f, 62.f, 125.f, 250.f, 500.f, 1000.f, 2000.f, 4000.f, 8000.f, 16000.f};
    float flatGains[kNumPeqBands] = {0.f};
    float qs[kNumPeqBands]    = {0.707f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 1.41f, 0.707f};
    int types[kNumPeqBands]   = {1, 0, 0, 0, 0, 0, 0, 0, 0, 2};
    peqState.setFullConfig(true, 0.0f, freqs, flatGains, qs, types, kNumPeqBands, kSr);

    // Compressor enabled with neutral 1.0:1 ratios & 0.0 dB makeup -> True Bypass (compEffective == false)
    compState.setFullConfig(true, -16.f, 1.0f, -34.f, 1.0f, 0.f, -60.f, 6.f, 10.f, 200.f, 0.f, 90.f, true, kSr);

    DspProcessParams params{};
    params.src = src.data();
    params.dstBytes = dst.data();
    params.frames = blockFrames;
    params.channels = kCh;
    params.sampleRate = kSr;
    params.subframeSize = 4;
    params.bitResolution = 32;
    params.sourceBitDepth = 24;
    params.currentVolume = 1.0;
    params.targetVolume = 1.0;
    params.peqEnabled = true;
    params.compEnabled = true;
    params.forceStateReset = true;

    DspProcessResult res{};
    pipeline.processBlock(params, peqState.snapshotActiveBank(), peqState.filterStates, compState.snapshotActiveBank(), res);
    assert(!res.peqEffective);
    assert(!res.compEffective);
    assert(res.bitPerfectDirectPass);

    // 2. Raise 1 PEQ band +3 dB -> peqEffective == true, bitPerfectDirectPass == false
    PeqCoeffBank activePeq = makeActivePeqBank(peqState, true);
    params.forceStateReset = false;
    params.peqForceStateReset = true;
    pipeline.processBlock(params, activePeq, peqState.filterStates, compState.snapshotActiveBank(), res);
    assert(res.peqEffective);
    assert(!res.compEffective);
    assert(!res.bitPerfectDirectPass);

    std::cout << "[PASS] test_peq_and_comp_effective_flags_honor_true_bypass_semantics\n";
}

} // namespace

int main() {
    test_comp_bypass_ramp_smooth_while_peq_stays_on();
    test_comp_reenable_mid_bypass_ramp_no_discontinuity();
    test_both_modules_off_returns_to_bit_perfect_direct_pass();
    test_gate_transition_6db_aligned_with_floor_margin();
    test_compressor_rms_and_peak_low_imd_sidebands();
    test_compressor_verse_to_chorus_fast_upward_retract();
    test_live_peq_toggle_uses_crossfade_zero_silence_hole_and_preserves_comp();
    test_bit_perfect_direct_pass_preserved_at_96khz_24bit();
    test_limiter_gain_reduction_db_reported_accurately_and_zero_below_ceiling();
    test_peq_and_comp_effective_flags_honor_true_bypass_semantics();
    std::cout << "ALL_CPP_HOST_TESTS_PASSED (10/10)\n";
    return 0;
}
