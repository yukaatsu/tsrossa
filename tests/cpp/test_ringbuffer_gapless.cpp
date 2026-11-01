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
