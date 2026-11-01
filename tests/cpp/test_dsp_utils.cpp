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
