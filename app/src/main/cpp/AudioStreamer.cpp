#include "AudioStreamer.h"
#include "AudioEngineState.h"
#include "AudioDecoder.h"
#include "UsbDacController.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <linux/usbdevice_fs.h>
#include <pthread.h>
#include <sched.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <unistd.h>

void LIBUSB_CALL iso_callback(struct libusb_transfer *transfer);

bool start_iso_streaming_thread(const char *thread_name) {
  if (g_audioState.isoThread != nullptr || g_audioState.usbHandle == nullptr) {
    return false;
  }
  if (g_audioState.usb_frames_per_sec.load() <= 0) {
    libusb_device *dev = libusb_get_device(g_audioState.usbHandle);
    if (dev) {
      int dev_speed = libusb_get_device_speed(dev);
      if (dev_speed == LIBUSB_SPEED_UNKNOWN && g_audioState.uacVersion.load() == 2) {
        dev_speed = LIBUSB_SPEED_HIGH;
      }
      int dev_fps = (dev_speed == LIBUSB_SPEED_HIGH || dev_speed == LIBUSB_SPEED_SUPER || dev_speed == 5) ? 8000 : 1000;
      g_audioState.usb_frames_per_sec.store(dev_fps);
    } else {
      g_audioState.usb_frames_per_sec.store((g_audioState.uacVersion.load() == 2) ? 8000 : 1000);
    }
  }
  g_audioState.stopIsoThread.store(false);
  g_audioState.isoThreadStarted.store(false);
  g_audioState.isoThreadExited.store(false);
  g_audioState.stallFaultReported.store(false);
  g_audioState.isSwapping.store(false);
  g_audioState.swappingZeroUrbCount.store(0);
  g_audioState.isSwappingAck.store(false);
  g_audioState.lastIsoPacketTimeNs.store(0);

  std::string name_copy = thread_name ? thread_name : "TsrossaIso";
  g_audioState.isoThread = new std::thread([name_copy]() {
    pthread_setname_np(pthread_self(), name_copy.c_str());

    // 1. Minimum timer slack (1ns) prevents kernel from coalescing wakeups when Android demotes process to background
    prctl(PR_SET_TIMERSLACK, 1, 0, 0, 0);

    // 2. Maximum urgent nice priority (-20) ensures immediate preemption of UI/compositor threads under CFS
    setpriority(PRIO_PROCESS, 0, -20);
    struct sched_param param;
    param.sched_priority = sched_get_priority_max(SCHED_FIFO);
    if (param.sched_priority > 2) param.sched_priority = 2; // Real-time audio priority
    pthread_setschedparam(pthread_self(), SCHED_FIFO, &param);

    // 3. CPU Affinity: Include ALL online cores (or avoid restrictive mask) so cpuset transitions never strand the thread
    long num_cpus = sysconf(_SC_NPROCESSORS_ONLN);
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    for (int c = 0; c < num_cpus; c++) CPU_SET(c, &cpuset);
    sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);

    // Auto-scale queue based on bus speed and endpoint bInterval:
    // Synopsys DWC3 xHCI hardware Frame Index (FRINDEX) boundary is 2048 microframes (256ms).
    // Queueing > 2048 microframes causes FRINDEX wrap-around / -EXDEV and burst overruns.
    int num_transfers = g_audioState.isoTransfersCount.load();
    int num_packets = g_audioState.isoPacketsCount.load();
    int fps = g_audioState.usb_frames_per_sec.load();
    if (fps >= 8000) {
      if (num_transfers <= 0 || num_transfers > 32) num_transfers = 24;
      if (num_packets <= 0 || num_packets > 64) num_packets = 64; // 64 microframes = 8ms per URB; 24 * 8ms = 192ms total runway
    } else if (fps >= 4000) {
      if (num_transfers <= 0 || num_transfers > 24) num_transfers = 20;
      if (num_packets <= 0 || num_packets > 32) num_packets = 32; // 32 * 250us = 8ms per URB
    } else if (fps >= 2000) {
      if (num_transfers <= 0 || num_transfers > 24) num_transfers = 16;
      if (num_packets <= 0 || num_packets > 16) num_packets = 16; // 16 * 500us = 8ms per URB
    } else {
      if (num_transfers <= 0 || num_transfers > 24) num_transfers = 16;
      if (num_packets <= 0 || num_packets > 16) num_packets = 16;
    }
    int packet_size = g_audioState.maxPacketSize;
    if (packet_size <= 0) packet_size = 1024;
    g_audioState.activeIsoTransfers.store(0);

    uint32_t init_sr = g_audioState.sampleRate.load();
    if (init_sr == 0) init_sr = 44100;
    uint32_t init_fps = (uint32_t)g_audioState.usb_frames_per_sec.load();
    if (init_fps == 0) init_fps = 8000;
    uint32_t init_base_frames = init_sr / init_fps;
    uint32_t init_remainder = init_sr % init_fps;
    int init_channels = g_audioState.channels.load();
    if (init_channels <= 0) init_channels = 2;
    int init_sf = g_audioState.subframeSize.load();
    if (init_sf <= 0) init_sf = 2;

    uint32_t init_rem_acc = 0;
    int first_sub_err = 0;

    for (int i = 0; i < num_transfers; i++) {
      struct libusb_transfer *transfer = libusb_alloc_transfer(num_packets);
      uint8_t *buffer = nullptr;
      if (posix_memalign((void **)&buffer, 64, num_packets * packet_size) != 0 || buffer == nullptr) {
        buffer = (uint8_t *)calloc(num_packets, packet_size);
      } else {
        memset(buffer, 0, num_packets * packet_size);
      }
      if (buffer) {
        mlock(buffer, num_packets * packet_size);
      }
      libusb_fill_iso_transfer(transfer, g_audioState.usbHandle,
                               g_audioState.epAddress, buffer,
                               num_packets * packet_size, num_packets,
                               iso_callback, nullptr, 1000);

      for (int p = 0; p < num_packets; p++) {
        int audio_frames = (int)init_base_frames;
        init_rem_acc += init_remainder;
        if (init_rem_acc >= init_fps) {
          audio_frames++;
          init_rem_acc -= init_fps;
        }
        int bytes_to_send = audio_frames * init_channels * init_sf;
        if (bytes_to_send > packet_size) bytes_to_send = packet_size;

        transfer->iso_packet_desc[p].length = bytes_to_send;
      }

      g_audioState.transfers.push_back(transfer);
      g_audioState.activeIsoTransfers.fetch_add(1);
      int sub_res = libusb_submit_transfer(transfer);
      if (sub_res < 0) {
        g_audioState.activeIsoTransfers.fetch_sub(1);
        if (first_sub_err == 0) {
          first_sub_err = sub_res;
          char errBuf[256];
          snprintf(errBuf, sizeof(errBuf),
                   "ERROR: libusb_submit_transfer[0] failed: %d (%s), ep=0x%02X, pkts=%d, pkt_size=%d",
                   sub_res, libusb_error_name(sub_res), g_audioState.epAddress, num_packets, packet_size);
          record_usb_diag(errBuf);
        }
      }
    }
    g_audioState.iso_fixed_rem = init_rem_acc;

    if (g_audioState.activeIsoTransfers.load() == 0) {
      for (auto *t : g_audioState.transfers) {
        if (t->buffer) {
          munlock(t->buffer, t->length);
          free(t->buffer);
        }
        libusb_free_transfer(t);
      }
      g_audioState.transfers.clear();
      g_audioState.isoThreadExited.store(true);
      {
        std::lock_guard<std::mutex> lk(g_isoStartMutex);
        g_isoStartCv.notify_all();
      }
      return;
    }

    g_audioState.isoThreadStarted.store(true);
    {
      std::lock_guard<std::mutex> lk(g_isoStartMutex);
      g_isoStartCv.notify_all();
    }

    uint32_t loop_count = 0;
    while (!g_audioState.stopIsoThread.load()) {
      struct timeval tv = {0, 1000}; // 1ms polling avoids CFS scheduler wakeup jitter under compositor load
      auto poll_start = std::chrono::steady_clock::now();
      libusb_handle_events_timeout_completed(g_audioState.usbContext, &tv, nullptr);
      auto poll_dur_us = std::chrono::duration_cast<std::chrono::microseconds>(
          std::chrono::steady_clock::now() - poll_start).count();
      if (poll_dur_us > 25000) { // Delayed >25ms
        __android_log_print(ANDROID_LOG_WARN, "TSROSSA_DIAG",
                            "STALL in libusb_handle_events: %lld us on CPU %d (activeUrbs=%d)",
                            (long long)poll_dur_us, sched_getcpu(), g_audioState.activeIsoTransfers.load());
      }
    }

    for (auto *t : g_audioState.transfers) {
      libusb_cancel_transfer(t);
    }

    auto start_time = std::chrono::steady_clock::now();
    bool timeout_hit = false;
    while (g_audioState.activeIsoTransfers.load() > 0) {
      struct timeval tv = {0, 10000};
      libusb_handle_events_timeout_completed(g_audioState.usbContext, &tv, nullptr);
      auto now = std::chrono::steady_clock::now();
      if (std::chrono::duration_cast<std::chrono::milliseconds>(now - start_time).count() > 1000) {
        LOGE("CRITICAL: Timeout waiting for transfers to cancel! Leaking memory to prevent DMA UAF.");
        timeout_hit = true;
        break;
      }
    }

    if (timeout_hit) {
      g_audioState.deviceWedged.store(true);
      for (auto *t : g_audioState.transfers) {
        g_audioState.orphanTransfers.push_back(t);
      }
      g_audioState.transfers.clear();
    } else {
      for (auto *t : g_audioState.transfers) {
        if (t->buffer) {
          munlock(t->buffer, t->length);
          free(t->buffer);
        }
        libusb_free_transfer(t);
      }
      g_audioState.transfers.clear();
    }

    // Safely report USB stall fault on exit without synchronous JNI / AttachCurrentThread in audio thread
    if (g_audioState.stallFaultReported.load()) {
      g_audioState.stallFaultReported.store(false);
      std::lock_guard<std::mutex> lock(g_controlMutex);
      ControlRequest req;
      req.type = REQ_EVENT_USB_STALL_FAULT;
      g_controlQueue.push(req);
      g_controlCv.notify_one();
    }

    g_audioState.isoThreadExited.store(true);
    {
      std::lock_guard<std::mutex> lk(g_isoStartMutex);
      g_isoStartCv.notify_all();
    }
  });

  // Synchronous barrier: wait for initial USB transfers to queue without busy-polling
  {
    std::unique_lock<std::mutex> lk(g_isoStartMutex);
    g_isoStartCv.wait_for(lk, std::chrono::milliseconds(100), [] {
      return g_audioState.isoThreadStarted.load() || g_audioState.isoThreadExited.load();
    });
  }

  return true;
}

void stop_and_join_decode_thread() {
  if (g_audioState.decodeThread != nullptr) {
    g_audioState.cancelDecoding.store(true);
    g_audioState.decoderCv.notify_all();
    if (g_audioState.decodeThread->joinable())
      g_audioState.decodeThread->join();
    delete g_audioState.decodeThread;
    g_audioState.decodeThread = nullptr;
  }
  g_audioState.isDecoding.store(false);
}

void decoder_thread_func() {
  pthread_setname_np(pthread_self(), "TsrossaDecode");
  prctl(PR_SET_TIMERSLACK, 1, 0, 0, 0);
  setpriority(PRIO_PROCESS, 0, 0); // Normal priority to never starve real-time USB thread

  // Initial 250ms yield so USB streaming and DAC warmup have uninterrupted priority,
  // unless user already sought past the initial buffer
  for (int w = 0; w < 25; w++) {
    if (g_audioState.cancelDecoding.load() ||
        g_audioState.pcmIndex.load(std::memory_order_relaxed) > 48000) {
      break;
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
  }

  constexpr uint64_t CHUNK_FRAMES = 16384ULL;

  while (!g_audioState.cancelDecoding.load(std::memory_order_relaxed)) {
    // 1. Handle gapless decoder handover outside real-time USB callback
    if (g_audioState.needGaplessDecoderHandoff.load(std::memory_order_acquire)) {
      std::lock_guard<std::mutex> lock(g_audioState.decoderMutex);
      if (g_audioState.needGaplessDecoderHandoff.load(std::memory_order_acquire)) {
        if (g_audioState.activeDecoder != nullptr) {
          g_audioState.activeDecoder->close();
        }
        g_audioState.activeDecoder = std::move(g_audioState.nextActiveDecoder);
        g_audioState.needGaplessDecoderHandoff.store(false, std::memory_order_release);
      }
      continue; // MUST restart loop with fresh Track 2 activeDecoder and fresh frame counters!
    }

    uint64_t total = 0;
    uint64_t current = 0;
    uint64_t capacity = 0;
    uint32_t channels = 2;
    uint64_t mySeekGen = 0;
    bool needWait = false;
    {
      std::lock_guard<std::mutex> lock(g_audioState.decoderMutex);
      if (g_audioState.needGaplessDecoderHandoff.load(std::memory_order_acquire)) {
        continue;
      }
      mySeekGen = g_audioState.seekGeneration.load(std::memory_order_acquire);
      total = g_audioState.totalFrames.load(std::memory_order_acquire);
      current = g_audioState.decodedFrames.load(std::memory_order_acquire);
      capacity = g_audioState.pcmBufferCapacityFrames.load(std::memory_order_acquire);
      channels = g_audioState.channels.load(std::memory_order_acquire);
      if (channels == 0) channels = 2;

      if (current >= total || g_audioState.activeDecoder == nullptr ||
          g_audioState.isInitializingTrack.load(std::memory_order_acquire)) {
        needWait = true;
      }
    }
    if (needWait) {
      g_audioState.isDecoding.store(false, std::memory_order_release);
      std::unique_lock<std::mutex> lock(g_audioState.decoderMutex);
      if (g_audioState.cancelDecoding.load()) break;
      g_audioState.decoderCv.wait_for(lock, std::chrono::milliseconds(50), [] {
        return g_audioState.cancelDecoding.load() ||
               g_audioState.needGaplessDecoderHandoff.load(std::memory_order_acquire) ||
               (!g_audioState.isInitializingTrack.load(std::memory_order_acquire) &&
                g_audioState.activeDecoder != nullptr &&
                g_audioState.decodedFrames.load(std::memory_order_acquire) <
                g_audioState.totalFrames.load(std::memory_order_acquire));
      });
      continue;
    }

    uint64_t readIdx = g_audioState.pcmIndex.load(std::memory_order_acquire);

    // Ring buffer backpressure: if ring buffer is filled ahead of read pointer up to capacity
    if (capacity > 0 && current >= readIdx &&
        (current - readIdx) + CHUNK_FRAMES + RING_BUFFER_SAFETY_FRAMES > capacity) {
      g_audioState.isDecoding.store(false, std::memory_order_release);
      std::unique_lock<std::mutex> lock(g_audioState.decoderMutex);
      g_audioState.decoderCv.wait_for(lock, std::chrono::milliseconds(20));
      continue;
    }

    g_audioState.isDecoding.store(true, std::memory_order_release);

    uint64_t remaining = (total > current) ? (total - current) : 0;
    uint64_t framesToDecode = std::min<uint64_t>(CHUNK_FRAMES, remaining);
    if (framesToDecode == 0) {
      g_audioState.isDecoding.store(false, std::memory_order_release);
      continue;
    }

    uint64_t writeSlot = (capacity > 0) ? (current % capacity) : current;
    uint64_t contiguousSlots = (capacity > 0) ? (capacity - writeSlot) : framesToDecode;
    if (framesToDecode > contiguousSlots) {
      framesToDecode = contiguousSlots;
    }

    if (g_audioState.pcmBuffer.empty()) {
      g_audioState.isDecoding.store(false, std::memory_order_release);
      std::this_thread::sleep_for(std::chrono::milliseconds(5));
      continue;
    }

    uint64_t newDecoded = current;
    bool seekChangedOrAborted = false;
    {
      std::lock_guard<std::mutex> lock(g_audioState.decoderMutex);
      // Verify state has not been altered by a concurrent seek or track transition
      if (g_audioState.seekGeneration.load(std::memory_order_acquire) != mySeekGen ||
          g_audioState.needGaplessDecoderHandoff.load(std::memory_order_acquire) ||
          g_audioState.isInitializingTrack.load(std::memory_order_acquire) ||
          g_audioState.decodedFrames.load(std::memory_order_acquire) != current ||
          g_audioState.activeDecoder == nullptr ||
          g_audioState.pcmBuffer.empty()) {
        seekChangedOrAborted = true;
      } else {
        int32_t *writePtr = g_audioState.pcmBuffer.data() + (writeSlot * channels);
        size_t read = g_audioState.activeDecoder->read_pcm_frames_s32(framesToDecode, writePtr);
        if (read == 0) {
          LOGI("[TsrossaDecode] Decoder reached EOF at frame %llu / %llu",
               (unsigned long long)current, (unsigned long long)total);
          uint64_t curPcmIdx = g_audioState.pcmIndex.load(std::memory_order_acquire);
          if (current >= curPcmIdx && current > 0) {
            g_audioState.totalFrames.store(current, std::memory_order_release);
            g_audioState.decodedFrames.store(current, std::memory_order_release);
          } else {
            g_audioState.decodedFrames.store(total, std::memory_order_release);
          }
          g_audioState.isDecoding.store(false, std::memory_order_release);
          seekChangedOrAborted = true;
        } else {
          newDecoded = current + read;
          g_audioState.decodedFrames.store(newDecoded, std::memory_order_release);
          if (newDecoded >= total) {
            g_audioState.isDecoding.store(false, std::memory_order_release);
          }
        }
      }
    }

    if (seekChangedOrAborted) {
      continue;
    }

    // Pace decode slightly if comfortably ahead (> 3 seconds)
    uint32_t sr = g_audioState.sampleRate.load(std::memory_order_relaxed);
    uint64_t latestReadIdx = g_audioState.pcmIndex.load(std::memory_order_relaxed);
    if (sr > 0 && newDecoded > latestReadIdx + (uint64_t)sr * 3) {
      std::this_thread::sleep_for(std::chrono::milliseconds(2));
    }
  }

  g_audioState.isDecoding.store(false, std::memory_order_release);
  LOGI("[TsrossaDecode] Exiting background decode thread.");
}

void stop_and_join_iso_thread(const char* caller_reason) {
  g_audioState.stopIsoThread.store(true);
  std::thread *t = g_audioState.isoThread;
  if (t != nullptr) {
    g_audioState.isoThread = nullptr;
    if (t->joinable()) {
      LOGI("[Thread] Waiting for isoThread from %s...", caller_reason);
      bool exited = false;
      for (int i = 0; i < 200; i++) { // 2s timeout
        if (g_audioState.isoThreadExited.load()) { exited = true; break; }
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
      }
      if (!exited) {
        LOGE("FATAL: isoThread did not exit! Detaching to prevent ANR.");
        g_audioState.deviceWedged.store(true);
        t->detach();
      } else {
        t->join();
        LOGI("[Thread] Joined isoThread successfully in %s", caller_reason);
      }
    }
    delete t;
  }
  g_audioState.transfers.clear();
  g_audioState.activeIsoTransfers.store(0);
}

void playAudio_cleanup_on_negotiation_failure() {
  stop_and_join_decode_thread();
  {
    std::lock_guard<std::mutex> lock(g_audioState.decoderMutex);
    if (g_audioState.activeDecoder != nullptr) {
      g_audioState.activeDecoder->close();
      g_audioState.activeDecoder.reset();
    }
  }
  if (!g_audioState.pcmBuffer.empty()) {
    g_audioState.pcmBuffer.clear();
  }
  g_audioState.currentFilePath = "";
  g_audioState.isPlaying.store(false);
  g_audioState.isSwapping.store(false);
}

void LIBUSB_CALL iso_callback(struct libusb_transfer *transfer) {
  if (g_audioState.stopIsoThread.load() ||
      g_audioState.usbHandle == nullptr ||
      transfer->status == LIBUSB_TRANSFER_CANCELLED ||
      transfer->status == LIBUSB_TRANSFER_NO_DEVICE) {
    g_audioState.activeIsoTransfers.fetch_sub(1);
    return;
  }

  // Handle transient vs critical errors
  if (transfer->status == LIBUSB_TRANSFER_ERROR ||
      transfer->status == LIBUSB_TRANSFER_TIMED_OUT ||
      transfer->status == LIBUSB_TRANSFER_STALL ||
      transfer->status == LIBUSB_TRANSFER_OVERFLOW) {

    if (g_audioState.stopIsoThread.load() || g_audioState.usbHandle == nullptr) {
      g_audioState.activeIsoTransfers.fetch_sub(1);
      return;
    }

    uint64_t now = get_time_ms();
    if (now - g_audioState.lastErrorTimeMs > 100) {
      g_audioState.consecutiveErrors.store(0);
    }
    g_audioState.lastErrorTimeMs = now;

    int err_count = g_audioState.consecutiveErrors.fetch_add(1) + 1;
    if (err_count > 50) {
      LOGE("USBExclusive: CRITICAL THRESHOLD REACHED (>50 errors in 100ms)! Aborting transfer. Status: %d",
           transfer->status);
      g_audioState.activeIsoTransfers.fetch_sub(1);
      g_audioState.stopIsoThread.store(true);
      g_audioState.stallFaultReported.store(true);
      return;
    }

    LOGW("USBExclusive: Transient USB error %d (count=%d/50). Recovering...",
         transfer->status, err_count);
  } else {
    g_audioState.consecutiveErrors.store(0);
  }

  int num_packets = transfer->num_iso_packets;
  uint8_t *buffer = transfer->buffer;

  uint32_t sr = g_audioState.sampleRate.load();
  uint32_t usb_frames_per_sec = (uint32_t)g_audioState.usb_frames_per_sec.load();
  if (usb_frames_per_sec == 0) usb_frames_per_sec = (g_audioState.uacVersion.load() == 2) ? 8000 : 1000;
  uint32_t base_frames = sr / usb_frames_per_sec;
  uint32_t remainder_rate = sr % usb_frames_per_sec;

  // Jitter tracking across transfers
  auto now_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(
      std::chrono::steady_clock::now().time_since_epoch()).count();
  int64_t last_ns = g_audioState.lastIsoPacketTimeNs.exchange(now_ns);
  if (last_ns > 0) {
    int64_t delta_ns = now_ns - last_ns;
    int64_t expected_ns = (1000000000LL / usb_frames_per_sec) * num_packets;
    int64_t jitter = std::abs(delta_ns - expected_ns);
    int64_t current_max = g_audioState.maxIsoJitterNs.load();
    if (jitter > current_max) {
      g_audioState.maxIsoJitterNs.store(jitter);
    }
    if (delta_ns > 20000000LL) { // Callback gap > 20ms
      __android_log_print(ANDROID_LOG_WARN, "TSROSSA_DIAG",
                          "CALLBACK STALL: gap=%lld us (expected %lld us) on CPU %d, activeUrbs=%d",
                          (long long)(delta_ns / 1000), (long long)(expected_ns / 1000),
                          sched_getcpu(), g_audioState.activeIsoTransfers.load());
    }
  }

  // Check packet statuses with rate-limiting to prevent logcat flooding
  int bad_packets = 0;
  int first_bad_status = 0;
  for (int p = 0; p < num_packets; p++) {
    int st = transfer->iso_packet_desc[p].status;
    if (st != LIBUSB_TRANSFER_COMPLETED) {
      bad_packets++;
      if (first_bad_status == 0) first_bad_status = st;
    }
  }
  if (bad_packets > 0) {
    static int64_t last_packet_err_log_ns = 0;
    if (now_ns - last_packet_err_log_ns > 200000000LL) { // max once every 200ms
      last_packet_err_log_ns = now_ns;
      __android_log_print(ANDROID_LOG_WARN, "TSROSSA_DIAG",
                          "PACKET ERROR: badPackets=%d/%d, firstStatus=%d (activeUrbs=%d)",
                          bad_packets, num_packets, first_bad_status, g_audioState.activeIsoTransfers.load());
    }
  }

  // Soft swapping check (drains all in-flight URBs with zeroes to reach true 0V DC)
  if (g_audioState.isSwapping.load()) {
    int zero_urbs = g_audioState.swappingZeroUrbCount.fetch_add(1);
    if (zero_urbs >= 6) {
      g_audioState.isSwappingAck.store(true);
    }
    int data_offset = 0;
    for (int i = 0; i < num_packets; i++) {
      int audio_frames_to_send = (int)base_frames;
      g_audioState.iso_fixed_rem += remainder_rate;
      if (g_audioState.iso_fixed_rem >= usb_frames_per_sec) {
        audio_frames_to_send++;
        g_audioState.iso_fixed_rem -= usb_frames_per_sec;
      }

      int bytes_to_send = audio_frames_to_send * g_audioState.channels.load() *
                          g_audioState.subframeSize.load();
      if (bytes_to_send > g_audioState.maxPacketSize)
        bytes_to_send = g_audioState.maxPacketSize;

      memset(buffer + data_offset, 0, bytes_to_send);
      transfer->iso_packet_desc[i].length = bytes_to_send;
      data_offset += bytes_to_send;
    }
    if (!g_audioState.stopIsoThread.load()) {
      libusb_submit_transfer(transfer);
    }
    return;
  }

  g_audioState.isSwappingAck.store(false);
  int data_offset = 0;

  for (int i = 0; i < num_packets; i++) {
    int audio_frames_to_send = (int)base_frames;
    g_audioState.iso_fixed_rem += remainder_rate;
    if (g_audioState.iso_fixed_rem >= usb_frames_per_sec) {
      audio_frames_to_send++;
      g_audioState.iso_fixed_rem -= usb_frames_per_sec;
    }

    int bytes_to_send = audio_frames_to_send * g_audioState.channels.load() *
                        g_audioState.subframeSize.load();
    if (bytes_to_send > g_audioState.maxPacketSize) {
      bytes_to_send = g_audioState.maxPacketSize;
    }
    int bytes_filled = 0;

    // HARDWARE DAC WARMUP / PRE-ROLL ZERO-PADDING
    uint32_t silenceRemaining = g_audioState.warmupSilenceFrames.load();
    if (silenceRemaining > 0 && (g_audioState.isPlaying.load() || g_audioState.isWarmingUp.load())) {
      int dst_bytes_per_frame = g_audioState.channels.load() * g_audioState.subframeSize.load();
      if (dst_bytes_per_frame > 0) {
        int bytes_needed = bytes_to_send - bytes_filled;
        int frames_needed = bytes_needed / dst_bytes_per_frame;
        int frames_to_zero = std::min((int)silenceRemaining, frames_needed);
        if (frames_to_zero > 0) {
          int zero_bytes = frames_to_zero * dst_bytes_per_frame;
          memset(buffer + data_offset + bytes_filled, 0, zero_bytes);
          bytes_filled += zero_bytes;
          if (g_audioState.warmupSilenceFrames.fetch_sub(frames_to_zero) <= (uint32_t)frames_to_zero) {
            // Keep currentVolume at its starting point (0.0f) so smooth micro-ramp fades in audio
          }
        }
      }
    }

    while (bytes_filled < bytes_to_send && g_audioState.isPlaying.load() &&
           !g_audioState.pcmBuffer.empty()) {
      size_t currentIndex = g_audioState.pcmIndex.load(std::memory_order_relaxed);
      size_t totalDecoded = g_audioState.decodedFrames.load(std::memory_order_acquire);
      size_t src_bytes_per_frame =
          g_audioState.channels.load() * sizeof(int32_t);
      size_t dst_bytes_per_frame =
          g_audioState.channels.load() * g_audioState.subframeSize.load();

      size_t bytes_needed = bytes_to_send - bytes_filled;
      size_t frames_needed = bytes_needed / dst_bytes_per_frame;
      size_t frames_avail =
          (totalDecoded > currentIndex) ? (totalDecoded - currentIndex) : 0;
      size_t frames_to_read = std::min(frames_needed, frames_avail);

      if (frames_to_read == 0 && (g_audioState.isDecoding.load() ||
                                  totalDecoded < g_audioState.totalFrames.load())) {
        // Decoder is lagging behind playback: fill current packet with silence to prevent squeaks/clicks
        memset(buffer + data_offset + bytes_filled, 0, bytes_needed);
        bytes_filled += bytes_needed;
        break;
      }
      if (frames_to_read == 0) {
        break;
      }

      uint64_t cap = g_audioState.pcmBufferCapacityFrames.load(std::memory_order_relaxed);
      uint64_t bufFrame = (cap > 0) ? (currentIndex % cap) : currentIndex;
      size_t contiguous = (cap > 0) ? (size_t)(cap - bufFrame) : frames_to_read;
      if (frames_to_read > contiguous) {
        frames_to_read = contiguous;
      }

      if (frames_to_read > 0) {
        size_t channels = g_audioState.channels.load();
        size_t num_samples = frames_to_read * channels;
        int sf_size = g_audioState.subframeSize.load();

        float raw_vol = g_audioState.rawLinearVolume.load();
        float target_vol = 1.0f;
        if (raw_vol <= 0.0f) {
          target_vol = 0.0f;
        } else if (raw_vol < 1.0f) {
          float desired_db = compute_perceptual_db(raw_vol);
          float hw_db = g_audioState.isHardwareVolumeActive.load()
                            ? g_audioState.appliedHardwareDb.load()
                            : 0.0f;
          float residual_db = desired_db - hw_db;
          target_vol = (residual_db < -0.05f) ? db_to_linear_gain(residual_db) : 1.0f;
        }

        float delta = 0.0f;
        if (std::abs(target_vol - g_audioState.currentVolume) > 0.0001f && frames_to_read > 0) {
          size_t ramp_frames = std::max((size_t)512, frames_to_read);
          delta = (target_vol - g_audioState.currentVolume) / (float)ramp_frames;
        } else {
          g_audioState.currentVolume = target_vol;
        }

        int32_t *src = g_audioState.pcmBuffer.data() + (bufFrame * channels);
        uint8_t *dst_bytes = (uint8_t *)(buffer + data_offset + bytes_filled);

        // BIT-PERFECT BYPASS
        if (g_audioState.currentVolume >= 0.9999f && target_vol >= 0.9999f && delta == 0.0f) {
          g_audioState.bitPerfectDirectPassActive.store(true);
          if (sf_size == 4) {
            // 32-bit subslot container (UAC standard Type I PCM):
            // In dr_flac and dr_wav, samples are always scaled to full 32-bit signed range (left-aligned).
            // Direct 32-bit integer transfer preserves bit-perfect audio for all bit depths (16-bit, 24-bit, 32-bit).
            memcpy(dst_bytes, src, num_samples * sizeof(int32_t));
          } else if (sf_size == 2) {
            for (size_t s = 0; s < num_samples; s++) {
              int64_t val = (static_cast<int64_t>(src[s]) + 0x8000LL) >> 16;
              if (val > 32767LL) val = 32767LL;
              if (val < -32768LL) val = -32768LL;
              int32_t val16 = static_cast<int32_t>(val);
              dst_bytes[s * 2] = static_cast<uint8_t>(val16 & 0xFF);
              dst_bytes[s * 2 + 1] = static_cast<uint8_t>((val16 >> 8) & 0xFF);
            }
          } else if (sf_size == 3) {
            for (size_t s = 0; s < num_samples; s++) {
              int64_t val = (static_cast<int64_t>(src[s]) + 0x80LL) >> 8;
              if (val > 8388607LL) val = 8388607LL;
              if (val < -8388608LL) val = -8388608LL;
              int32_t val24 = static_cast<int32_t>(val);
              dst_bytes[s * 3] = static_cast<uint8_t>(val24 & 0xFF);
              dst_bytes[s * 3 + 1] = static_cast<uint8_t>((val24 >> 8) & 0xFF);
              dst_bytes[s * 3 + 2] = static_cast<uint8_t>((val24 >> 16) & 0xFF);
            }
          }
        } else {
          g_audioState.bitPerfectDirectPassActive.store(false);
          double frame_vol = static_cast<double>(g_audioState.currentVolume);

          // Frame-locked volume scaling with decorrelated dual-channel TPDF dither and mid-tread rounding
          for (size_t s = 0; s < num_samples; s++) {
            uint32_t ch = static_cast<uint32_t>(s % channels);
            if (ch == 0) {
              if (delta != 0.0f) {
                g_audioState.currentVolume += delta;
                if ((delta > 0.0f && g_audioState.currentVolume > target_vol) ||
                    (delta < 0.0f && g_audioState.currentVolume < target_vol)) {
                  g_audioState.currentVolume = target_vol;
                  delta = 0.0f;
                }
              }
              frame_vol = static_cast<double>(g_audioState.currentVolume);
              if (frame_vol > 1.0) frame_vol = 1.0;
              if (frame_vol < 0.0) frame_vol = 0.0;
            }

            double scaled32 = static_cast<double>(src[s]) * frame_vol;
            bool apply_dither = (frame_vol < 0.9999 && frame_vol > 0.00001);

            if (sf_size == 4) {
              // 32-bit container: 1 LSB = 1.0 on int32 scale
              if (apply_dither) {
                scaled32 += fast_tpdf_dither(ch);
              }
              int64_t q32 = std::llrint(scaled32);
              if (q32 > 2147483647LL) q32 = 2147483647LL;
              if (q32 < -2147483648LL) q32 = -2147483648LL;
              int32_t val32 = static_cast<int32_t>(q32);
              dst_bytes[s * 4]     = static_cast<uint8_t>(val32 & 0xFF);
              dst_bytes[s * 4 + 1] = static_cast<uint8_t>((val32 >> 8) & 0xFF);
              dst_bytes[s * 4 + 2] = static_cast<uint8_t>((val32 >> 16) & 0xFF);
              dst_bytes[s * 4 + 3] = static_cast<uint8_t>((val32 >> 24) & 0xFF);
            } else if (sf_size == 3) {
              // 24-bit container: scale to true 24-bit domain (divide by 256.0) and round with std::llrint
              double scaled24 = scaled32 * (1.0 / 256.0);
              if (apply_dither) {
                scaled24 += fast_tpdf_dither(ch);
              }
              int64_t q24 = std::llrint(scaled24);
              if (q24 > 8388607LL) q24 = 8388607LL;
              if (q24 < -8388608LL) q24 = -8388608LL;
              int32_t val24 = static_cast<int32_t>(q24);
              dst_bytes[s * 3]     = static_cast<uint8_t>(val24 & 0xFF);
              dst_bytes[s * 3 + 1] = static_cast<uint8_t>((val24 >> 8) & 0xFF);
              dst_bytes[s * 3 + 2] = static_cast<uint8_t>((val24 >> 16) & 0xFF);
            } else if (sf_size == 2) {
              // 16-bit container: scale to true 16-bit domain (divide by 65536.0) and round with std::llrint
              double scaled16 = scaled32 * (1.0 / 65536.0);
              if (apply_dither) {
                scaled16 += fast_tpdf_dither(ch);
              }
              int64_t q16 = std::llrint(scaled16);
              if (q16 > 32767LL) q16 = 32767LL;
              if (q16 < -32768LL) q16 = -32768LL;
              int32_t val16 = static_cast<int32_t>(q16);
              dst_bytes[s * 2]     = static_cast<uint8_t>(val16 & 0xFF);
              dst_bytes[s * 2 + 1] = static_cast<uint8_t>((val16 >> 8) & 0xFF);
            }
          }
        }

        uint64_t expectedPcmIdx = static_cast<uint64_t>(currentIndex);
        g_audioState.pcmIndex.compare_exchange_strong(
            expectedPcmIdx, expectedPcmIdx + frames_to_read,
            std::memory_order_release, std::memory_order_relaxed);
        bytes_filled += frames_to_read * channels * sf_size;
      }

      // Check for End-of-Track (or buffering)
      size_t nowIndex = g_audioState.pcmIndex.load(std::memory_order_relaxed);
      size_t trackTotal = g_audioState.totalFrames.load(std::memory_order_acquire);
      size_t nowDecoded = g_audioState.decodedFrames.load(std::memory_order_acquire);

      if (nowIndex >= trackTotal) {
        LOGI("[AudioStreamer] Track reached end of stream (nowIndex=%zu, trackTotal=%zu, hasNext=%d)",
             nowIndex, trackTotal, g_audioState.hasNextTrack.load() ? 1 : 0);

        // Track is fully played to the end: Check Gapless handover
        if (g_audioState.hasNextTrack.load()) {
          bool format_changed = (g_audioState.sampleRate.load() !=
                                 g_audioState.sampleRateNext.load()) ||
                                (g_audioState.channels.load() !=
                                 g_audioState.channelsNext.load()) ||
                                (g_audioState.sourceBitDepth.load() !=
                                 g_audioState.sourceBitDepthNext.load());

          if (format_changed) {
            LOGW("Gapless aborted due to format change. Notifying track finished for normal advance.");
            g_audioState.hasNextTrack.store(false);
            g_audioState.isFinished.store(true);
            g_audioState.isPlaying.store(false);

            {
              std::lock_guard<std::mutex> lock(g_controlMutex);
              ControlRequest evReq;
              evReq.type = REQ_EVENT_TRACK_FINISHED;
              g_controlQueue.push(evReq);
              g_controlCv.notify_one();
            }
            break;
          }

          LOGI("[AudioStreamer] Performing seamless gapless transition to: %s", g_audioState.nextFilePath.c_str());
          {
            std::unique_lock<std::mutex> lock(g_stateMutex, std::try_to_lock);
            if (lock.owns_lock()) {
              g_audioState.pcmBufferGarbage = std::move(g_audioState.pcmBuffer);
            } else {
              g_audioState.pcmBuffer.clear();
            }
          }
          g_audioState.pcmBuffer = std::move(g_audioState.pcmBufferNext);
          g_audioState.pcmBufferCapacityFrames.store(g_audioState.nextPcmBufferCapacityFrames.load());
          g_audioState.sampleRate.store(g_audioState.sampleRateNext.load());
          g_audioState.channels.store(g_audioState.channelsNext.load());
          g_audioState.sourceBitDepth.store(g_audioState.sourceBitDepthNext.load());
          g_audioState.hasNextTrack.store(false);

          uint64_t nextTotal = g_audioState.nextTotalFrames.load();
          uint64_t nextDecoded = g_audioState.nextDecodedFrames.load();

          // Signal decoder thread FIRST before exposing nextTotal/nextDecoded so TsrossaDecode
          // never attempts to decode Track 2's remaining frames using Track 1's EOF activeDecoder!
          g_audioState.seekGeneration.fetch_add(1, std::memory_order_acq_rel);
          g_audioState.needGaplessDecoderHandoff.store(true, std::memory_order_release);

          g_audioState.totalFrames.store(nextTotal, std::memory_order_release);
          g_audioState.decodedFrames.store(nextDecoded, std::memory_order_release);
          g_audioState.pendingSeekSeconds.store(-1.0);
          g_audioState.pcmIndex.store(0, std::memory_order_release);
          g_audioState.currentFilePath = g_audioState.nextFilePath;
          g_audioState.nextFilePath = "";

          g_audioState.decoderCv.notify_one();

          {
            std::lock_guard<std::mutex> lock(g_controlMutex);
            ControlRequest evReq;
            evReq.type = REQ_EVENT_TRACK_TRANSITION;
            evReq.eventArg = g_audioState.currentFilePath;
            g_controlQueue.push(evReq);
            g_controlCv.notify_one();
          }
        } else {
          // No next track prepared: mark finished and dispatch REQ_EVENT_TRACK_FINISHED
          LOGI("[AudioStreamer] No next track pre-buffered. Emitting REQ_EVENT_TRACK_FINISHED.");
          g_audioState.isFinished.store(true);
          g_audioState.isPlaying.store(false);

          {
            std::lock_guard<std::mutex> lock(g_controlMutex);
            ControlRequest evReq;
            evReq.type = REQ_EVENT_TRACK_FINISHED;
            g_controlQueue.push(evReq);
            g_controlCv.notify_one();
          }
          break;
        }
      } else if (nowIndex >= nowDecoded) {
        // Buffer underrun: decoder still working, fill remaining packet with silence
        size_t remainingBytes = bytes_to_send - bytes_filled;
        if (remainingBytes > 0) {
          memset(buffer + data_offset + bytes_filled, 0, remainingBytes);
          bytes_filled += remainingBytes;
        }
        break;
      }
    }

    if (bytes_filled < bytes_to_send) {
      memset(buffer + data_offset + bytes_filled, 0, bytes_to_send - bytes_filled);
    }

    transfer->iso_packet_desc[i].length = bytes_to_send;
    data_offset += bytes_to_send;
  }

  if (!g_audioState.stopIsoThread.load()) {
    int r = libusb_submit_transfer(transfer);
    if (r != 0) {
      LOGE("libusb_submit_transfer failed: %d", r);
      if (r == LIBUSB_ERROR_PIPE) {
        libusb_clear_halt(g_audioState.usbHandle, g_audioState.epAddress);
        r = libusb_submit_transfer(transfer);
      }
      if (r != 0) {
        g_audioState.activeIsoTransfers.fetch_sub(1);
        g_audioState.stopIsoThread.store(true);
        g_audioState.stallFaultReported.store(true);
      }
    }
  } else {
    g_audioState.activeIsoTransfers.fetch_sub(1);
  }
}

struct InitializingTrackGuard {
  InitializingTrackGuard() {
    g_audioState.isInitializingTrack.store(true, std::memory_order_release);
  }
  ~InitializingTrackGuard() {
    g_audioState.isInitializingTrack.store(false, std::memory_order_release);
  }
};

static void apply_pending_seek_before_start(uint32_t sr, uint64_t totalFrames, uint64_t capacityFrames) {
  double pendingSeek = g_audioState.pendingSeekSeconds.exchange(-1.0);
  if (pendingSeek < 0.0 || sr == 0 || totalFrames == 0) {
    return;
  }
  uint64_t seekFrame = static_cast<uint64_t>(pendingSeek * static_cast<double>(sr));
  uint64_t safetyTail = std::min<uint64_t>(sr / 4, (totalFrames > 1) ? (totalFrames - 1) : 0);
  uint64_t maxSafeFrame = (totalFrames > safetyTail && safetyTail > 0)
                              ? (totalFrames - safetyTail)
                              : (totalFrames - 1);
  if (seekFrame > maxSafeFrame) {
    seekFrame = maxSafeFrame;
  }

  std::lock_guard<std::mutex> dlock(g_audioState.decoderMutex);
  g_audioState.seekGeneration.fetch_add(1, std::memory_order_acq_rel);
  uint64_t curDecoded = g_audioState.decodedFrames.load(std::memory_order_acquire);

  // Fast-path: if target frame is already decoded inside contiguous linear start of buffer
  if (seekFrame < curDecoded && seekFrame < capacityFrames &&
      (totalFrames <= capacityFrames || curDecoded <= capacityFrames)) {
    g_audioState.pcmIndex.store(seekFrame, std::memory_order_release);
  } else {
    if (g_audioState.activeDecoder != nullptr) {
      g_audioState.activeDecoder->seek(seekFrame);
    }
    g_audioState.decodedFrames.store(seekFrame, std::memory_order_release);
    g_audioState.pcmIndex.store(seekFrame, std::memory_order_release);
  }
}

int play_audio_internal(const std::string &savedPath, jobject thiz, JNIEnv *env) {
  if (g_audioState.deviceWedged.load()) {
    LOGE("Refusing to start playAudio because device is wedged from previous timeout. Reconnect required.");
    return -4;
  }
  InitializingTrackGuard initGuard;
  g_audioState.seekGeneration.fetch_add(1, std::memory_order_acq_rel);
  g_audioState.sampleRateUnverified.store(false);
  g_audioState.isFinished = false;
  g_audioState.pendingSeekSeconds.store(-1.0);

  if (g_audioState.callbackObj != nullptr)
    env->DeleteGlobalRef(g_audioState.callbackObj);
  g_audioState.callbackObj = env->NewGlobalRef(thiz);

  // === OPTIMIZATION 1: INSTANT GAPLESS HANDOVER ON MANUAL NEXT ===
  bool reusedPrebuffer = false;
  uint32_t newSampleRate = 0;
  uint32_t newChannels = 0;
  uint32_t newSourceBitDepth = 0;
  uint64_t totalFrames = 0;
  size_t initialRead = 0;

  if (g_audioState.hasNextTrack.load() &&
      g_audioState.nextFilePath == savedPath &&
      !g_audioState.pcmBufferNext.empty()) {
    bool format_match = (g_audioState.sampleRate.load() == g_audioState.sampleRateNext.load()) &&
                        (g_audioState.channels.load() == g_audioState.channelsNext.load()) &&
                        (g_audioState.sourceBitDepth.load() == g_audioState.sourceBitDepthNext.load());
    if (format_match && g_audioState.isoThread != nullptr && !g_audioState.stopIsoThread.load() &&
        g_audioState.activeIsoTransfers.load() > 0) {
      LOGI("playAudio: INSTANT GAPLESS HANDOVER for manual Next -> %s", savedPath.c_str());

      // Handshake with iso_callback: instruct it to send silence while buffer is being moved
      g_audioState.swappingZeroUrbCount.store(0);
      g_audioState.isSwappingAck.store(false);
      g_audioState.isSwapping.store(true);
      auto wait_start = std::chrono::steady_clock::now();
      bool ack_timeout = false;
      while (!g_audioState.isSwappingAck.load() && !g_audioState.stopIsoThread.load()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
        auto now = std::chrono::steady_clock::now();
        if (std::chrono::duration_cast<std::chrono::milliseconds>(now - wait_start).count() > 60) {
          ack_timeout = true;
          break;
        }
      }

      if (ack_timeout) {
        LOGW("playAudio: isSwappingAck timeout during gapless handover, safely restarting isoThread");
        stop_and_join_iso_thread("gapless handover timeout");
      }

      stop_and_join_decode_thread();
      {
        std::lock_guard<std::mutex> lock(g_stateMutex);
        g_audioState.pcmBufferGarbage = std::move(g_audioState.pcmBuffer);
      }
      g_audioState.pcmBuffer = std::move(g_audioState.pcmBufferNext);
      g_audioState.pcmBufferCapacityFrames.store(g_audioState.nextPcmBufferCapacityFrames.load());
      g_audioState.sampleRate.store(g_audioState.sampleRateNext.load());
      g_audioState.channels.store(g_audioState.channelsNext.load());
      g_audioState.sourceBitDepth.store(g_audioState.sourceBitDepthNext.load());
      g_audioState.hasNextTrack.store(false);
      size_t handoverFrames = g_audioState.pcmBuffer.size() / g_audioState.channels.load();
      uint64_t handoverTotal = g_audioState.nextTotalFrames.load();
      uint64_t handoverDecoded = g_audioState.nextDecodedFrames.load();
      if (handoverTotal == 0) handoverTotal = handoverFrames;
      if (handoverDecoded == 0) handoverDecoded = handoverFrames;
      g_audioState.decodedFrames.store(handoverDecoded);
      g_audioState.totalFrames.store(handoverTotal);
      g_audioState.pcmIndex.store(0);
      g_audioState.currentFilePath = savedPath;
      g_audioState.nextFilePath = "";
      {
        std::lock_guard<std::mutex> dlock(g_audioState.decoderMutex);
        g_audioState.activeDecoder = std::move(g_audioState.nextActiveDecoder);
        g_audioState.needGaplessDecoderHandoff.store(false, std::memory_order_release);
      }
      apply_pending_seek_before_start(g_audioState.sampleRate.load(), handoverTotal, g_audioState.pcmBufferCapacityFrames.load());
      uint64_t finalDecoded = g_audioState.decodedFrames.load(std::memory_order_acquire);
      bool needMoreHandover = (finalDecoded < handoverTotal);
      g_audioState.isDecoding.store(needMoreHandover, std::memory_order_release);
      if (g_audioState.decodeThread == nullptr) {
        g_audioState.cancelDecoding.store(false);
        g_audioState.decodeThread = new std::thread(decoder_thread_func);
      } else {
        g_audioState.decoderCv.notify_one();
      }
      g_audioState.isSwapping.store(false);
      g_audioState.isPlaying.store(true);
      if (ack_timeout) {
        start_iso_streaming_thread("TsrossaIso");
      }
      return 0; // ZERO DELAY INSTANT START!
    } else {
      LOGI("playAudio: Reusing pre-buffered pcmBufferNext across format change for %s", savedPath.c_str());
      stop_and_join_decode_thread();
      if (g_audioState.isoThread != nullptr) {
        stop_and_join_iso_thread("reusing next buffer across format change");
      }
      {
        std::lock_guard<std::mutex> lock(g_stateMutex);
        g_audioState.pcmBufferGarbage = std::move(g_audioState.pcmBuffer);
      }
      g_audioState.pcmBuffer = std::move(g_audioState.pcmBufferNext);
      g_audioState.pcmBufferCapacityFrames.store(g_audioState.nextPcmBufferCapacityFrames.load());
      newSampleRate = g_audioState.sampleRateNext.load();
      newChannels = g_audioState.channelsNext.load();
      newSourceBitDepth = g_audioState.sourceBitDepthNext.load();
      totalFrames = g_audioState.nextTotalFrames.load();
      initialRead = g_audioState.nextDecodedFrames.load();
      if (totalFrames == 0) totalFrames = g_audioState.pcmBuffer.size() / (newChannels > 0 ? newChannels : 2);
      if (initialRead == 0) initialRead = totalFrames;
      g_audioState.hasNextTrack.store(false);
      g_audioState.decodedFrames.store(initialRead);
      g_audioState.totalFrames.store(totalFrames);
      g_audioState.pcmIndex.store(0);
      {
        std::lock_guard<std::mutex> dlock(g_audioState.decoderMutex);
        g_audioState.activeDecoder = std::move(g_audioState.nextActiveDecoder);
        g_audioState.needGaplessDecoderHandoff.store(false, std::memory_order_release);
      }
      reusedPrebuffer = true;
    }
  }

  if (!reusedPrebuffer) {
    // Open audio file
    auto dec = std::make_unique<AudioDecoder>();
    if (!dec->open(savedPath.c_str())) {
      char errDiag[512];
      snprintf(errDiag, sizeof(errDiag), "playAudio ERROR: %s -> %s",
               dec->lastOpenError.c_str(), savedPath.c_str());
      record_usb_diag(errDiag);
      LOGE("%s", errDiag);
      return -1;
    }

    newSampleRate = dec->sampleRate;
    newChannels = dec->channels;
    newSourceBitDepth = dec->bitsPerSample;
    totalFrames = dec->totalPCMFrameCount;
    stop_and_join_decode_thread();
    {
      std::lock_guard<std::mutex> dlock(g_audioState.decoderMutex);
      g_audioState.activeDecoder = std::move(dec);
    }
  }

  g_audioState.currentFilePath = savedPath;
  g_audioState.nextFilePath = "";
  g_audioState.prepareNextGen.fetch_add(1);

  char dbg[512];
  snprintf(dbg, sizeof(dbg), "=== playAudio START: rate=%u, bitDepth=%u (sf=%d, frames=%llu, reused=%d) ===",
           newSampleRate, newSourceBitDepth, (int)(newSourceBitDepth / 8), (unsigned long long)totalFrames, reusedPrebuffer ? 1 : 0);
  record_usb_diag(dbg);

  // Format Compatibility Check
  if (g_audioState.usbHandle != nullptr) {
    int target_sf = newSourceBitDepth / 8;
    if (target_sf == 0) target_sf = 2;

    bool bitDepthSupported = false;
    for (int bd : g_audioState.supportedBitDepths) {
      if (bd >= (int)newSourceBitDepth) {
        bitDepthSupported = true;
        break;
      }
    }

    bool sampleRateSupported = false;
    if (g_audioState.sampleRateQuerySuccess) {
      for (uint32_t sr : g_audioState.supportedSampleRates) {
        if (sr == newSampleRate) {
          sampleRateSupported = true;
          break;
        }
      }
    } else {
      if (!g_audioState.hasValidatedRate.load()) {
        if (newSampleRate == 44100 || newSampleRate == 48000) {
          sampleRateSupported = true;
        }
      } else {
        if (newSampleRate == g_audioState.lastKnownGoodSampleRate.load()) {
          sampleRateSupported = true;
        }
      }
    }

    if (!bitDepthSupported || !sampleRateSupported) {
      std::string reason;
      if (!bitDepthSupported && !sampleRateSupported) {
        reason = std::to_string(newSourceBitDepth) + "-Bit/" + std::to_string(newSampleRate) + "Hz not supported";
      } else if (!bitDepthSupported) {
        reason = std::to_string(newSourceBitDepth) + "-Bit not supported";
      } else {
        reason = std::to_string(newSampleRate) + "Hz not supported";
      }
      LOGE("FORMAT REFUSED: %s — File: %s", reason.c_str(), savedPath.c_str());

      std::string filename = get_file_basename(savedPath);

      {
        std::lock_guard<std::mutex> hlock(g_audioState.refusedHistoryMutex);
        g_audioState.refusedTrackHistory.push_back(
            {filename, (int)newSourceBitDepth, (int)newSampleRate, "format_incompatible"});
        if (g_audioState.refusedTrackHistory.size() > 5) {
          g_audioState.refusedTrackHistory.erase(g_audioState.refusedTrackHistory.begin());
        }
      }

      if (g_jvm && g_audioState.callbackObj) {
        jclass clazz = env->GetObjectClass(g_audioState.callbackObj);
        jmethodID methodId = env->GetMethodID(clazz, "onFormatIncompatible",
            "(Ljava/lang/String;IILjava/lang/String;)V");
        if (methodId) {
          jstring jFilename = env->NewStringUTF(filename.c_str());
          jstring jReason = env->NewStringUTF(reason.c_str());
          env->CallVoidMethod(g_audioState.callbackObj, methodId,
              jFilename, (jint)newSourceBitDepth, (jint)newSampleRate, jReason);
          env->DeleteLocalRef(jFilename);
          env->DeleteLocalRef(jReason);
        }
      }

      {
        std::lock_guard<std::mutex> dlock(g_audioState.decoderMutex);
        if (g_audioState.activeDecoder != nullptr) {
          g_audioState.activeDecoder->close();
          g_audioState.activeDecoder.reset();
        }
      }
      g_audioState.currentFilePath = "";
      return -2;
    }
  }

  // === STEP 1: ALLOCATE & PRELOAD AUDIO BUFFER IN RAM BEFORE HARDWARE USB RECONFIG ===
  if (!reusedPrebuffer) {
    if (g_audioState.isoThread != nullptr) {
      g_audioState.swappingZeroUrbCount.store(0);
      g_audioState.isSwappingAck.store(false);
      g_audioState.isSwapping.store(true);
    }

    // Decode first 5.0 seconds into fast staging buffer
    size_t initialFrames = (size_t)newSampleRate * 5;
    if (initialFrames > totalFrames) initialFrames = totalFrames;

    std::vector<int32_t> stagingBuffer;
    try {
      stagingBuffer.resize(initialFrames * newChannels);
    } catch (const std::bad_alloc &e) {
      LOGE("OOM: Not enough memory for %zu staging frames!", initialFrames);
      {
        std::lock_guard<std::mutex> dlock(g_audioState.decoderMutex);
        if (g_audioState.activeDecoder != nullptr) {
          g_audioState.activeDecoder->close();
          g_audioState.activeDecoder.reset();
        }
      }
      g_audioState.isSwapping.store(false);
      return -1;
    }

    {
      std::lock_guard<std::mutex> dlock(g_audioState.decoderMutex);
      if (g_audioState.activeDecoder != nullptr) {
        initialRead = g_audioState.activeDecoder->read_pcm_frames_s32(initialFrames, stagingBuffer.data());
      }
    }

    if (g_audioState.isoThread != nullptr) {
      auto swap_start = std::chrono::steady_clock::now();
      while (!g_audioState.isSwappingAck.load() && !g_audioState.stopIsoThread.load()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
        if (std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - swap_start).count() > 120) {
          break;
        }
      }
    }

    // Allocate sliding-window ring buffer in RAM
    clean_garbage_internal();

    uint64_t maxRingFrames = MAX_RING_BUFFER_BYTES / ((uint64_t)newChannels * sizeof(int32_t));
    uint64_t capacityFrames = std::min(totalFrames, maxRingFrames);

    try {
      g_audioState.pcmBuffer.resize(capacityFrames * newChannels);
    } catch (const std::bad_alloc &e) {
      LOGE("OOM: Not enough memory for %llu frames!", (unsigned long long)capacityFrames);
      {
        std::lock_guard<std::mutex> dlock(g_audioState.decoderMutex);
        if (g_audioState.activeDecoder != nullptr) {
          g_audioState.activeDecoder->close();
          g_audioState.activeDecoder.reset();
        }
      }
      g_audioState.isSwapping.store(false);
      return -1;
    }
    g_audioState.pcmBufferCapacityFrames.store(capacityFrames);

    if (initialRead > 0 && !stagingBuffer.empty()) {
      size_t copyFrames = std::min((uint64_t)initialRead, capacityFrames);
      memcpy(g_audioState.pcmBuffer.data(), stagingBuffer.data(), copyFrames * newChannels * sizeof(int32_t));
    }
    stagingBuffer.clear();
    stagingBuffer.shrink_to_fit();

    if (initialRead < initialFrames) {
      totalFrames = initialRead;
    }
    g_audioState.decodedFrames.store(initialRead, std::memory_order_release);
    g_audioState.totalFrames.store(totalFrames, std::memory_order_release); // Full track frame count preserved!
    g_audioState.pcmIndex.store(0, std::memory_order_release);
  } else {
    // Reused prebuffer: clear any old garbage
    clean_garbage_internal();
  }

  // Purge any stale finished events from previous track
  {
    std::lock_guard<std::mutex> lock(g_controlMutex);
    std::queue<ControlRequest> filteredQueue;
    while (!g_controlQueue.empty()) {
      auto ev = g_controlQueue.front();
      g_controlQueue.pop();
      if (ev.type != REQ_EVENT_TRACK_FINISHED) {
        filteredQueue.push(ev);
      }
    }
    g_controlQueue = std::move(filteredQueue);
  }

  // Store new track format state
  int target_sf = newSourceBitDepth / 8;
  if (target_sf == 0) target_sf = 2;

  // Find optimal DAC container altsetting:
  // Always negotiate the highest subframe container supported by DAC (32-bit > 24-bit > 16-bit)
  // to give 64-bit DSP maximum dynamic range and prevent micro-detail truncation
  bool has_selected_alt = false;
  AltSettingInfo selected_alt{};
  if (!g_audioState.validAlts.empty()) {
    int best_score = -1;
    for (const auto &a : g_audioState.validAlts) {
      int score = 0;
      if (a.subframe_size >= target_sf) {
        if (a.subframe_size == 4) score = 600;
        else if (a.subframe_size == 3) score = 550;
        else if (a.subframe_size == 2) score = 400;
        else score = 300;
      } else {
        if (a.subframe_size == 3) score = 250;
        else if (a.subframe_size == 2) score = 200;
        else score = 50;
      }
      if (score > best_score) {
        best_score = score;
        selected_alt = a;
        has_selected_alt = true;
      }
    }
  }

  uint32_t oldSampleRate = g_audioState.sampleRate.load();
  int oldSubframeSize = g_audioState.subframeSize.load();
  int oldAltSetting = g_audioState.currentAltSetting.load();
  bool rate_changed = (oldSampleRate != newSampleRate);
  bool alt_changed = has_selected_alt && (selected_alt.subframe_size != oldSubframeSize || selected_alt.altsetting != oldAltSetting);
  bool altsetting_changed = false;
  bool stream_was_stopped = (g_audioState.isoThread == nullptr || g_audioState.activeIsoTransfers.load() == 0);

  bool needs_reconfig = alt_changed ||
                        rate_changed ||
                        stream_was_stopped ||
                        g_audioState.isWarmingUp.load();

  if (needs_reconfig) {
    if (g_audioState.isWarmingUp.load()) {
      g_audioState.isWarmingUp.store(false);
      g_audioState.warmupSilenceFrames.store(0);
    }
    stop_and_join_iso_thread("format or rate reconfiguration");
  }

  g_audioState.sampleRate.store(newSampleRate);
  g_audioState.channels.store(newChannels);
  g_audioState.sourceBitDepth.store(newSourceBitDepth);

  // === STEP 2: HARDWARE USB INTERFACE & CLOCK NEGOTIATION (STRICT ALSA UAC2 ORDER) ===
  if (g_audioState.usbHandle != nullptr && needs_reconfig) {
    if (has_selected_alt) {
      int iface_num = selected_alt.interface_num;
      if (iface_num < 0) iface_num = g_audioState.usbAudioInterface;

      // 1. Ensure both AUDIO_CONTROL (acInterfaceNum) and AUDIO_STREAMING (iface_num) are claimed
      auto ensure_claimed = [](int ifnum) {
        if (ifnum < 0) return;
        int cr = libusb_claim_interface(g_audioState.usbHandle, ifnum);
        if (cr != 0 && g_audioState.usbFd >= 0) {
          struct usbdevfs_disconnect_claim dc;
          memset(&dc, 0, sizeof(dc));
          dc.interface = (unsigned int)ifnum;
          dc.flags = 0;
          if (ioctl(g_audioState.usbFd, USBDEVFS_DISCONNECT_CLAIM, &dc) < 0) {
            ioctl(g_audioState.usbFd, USBDEVFS_DISCONNECT, &ifnum);
            ioctl(g_audioState.usbFd, USBDEVFS_CLAIMINTERFACE, &ifnum);
          }
          libusb_claim_interface(g_audioState.usbHandle, ifnum);
        }
      };
      ensure_claimed(g_audioState.acInterfaceNum);
      ensure_claimed(iface_num);

      // 2. Put DAC in hardware mute (altsetting 0) first
      if (iface_num >= 0 && g_audioState.currentAltSetting.load() != 0) {
        libusb_set_interface_alt_setting(g_audioState.usbHandle, iface_num, 0);
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
        g_audioState.currentAltSetting.store(0);
      }

      // 2b. If DAC has a UAC2 Clock Selector on the Playback path, select the Playback clock pin
      if (g_audioState.uacVersion.load() == 2 && g_audioState.clockSelectorId.load() != -1) {
        uint8_t pin = (uint8_t)g_audioState.clockSelectorPin.load();
        if (pin == 0) pin = 1;
        libusb_control_transfer(
            g_audioState.usbHandle,
            0x21, 0x01, (0x01 << 8),
            (g_audioState.clockSelectorId.load() << 8) | g_audioState.acInterfaceNum,
            &pin, 1, 500);
      }

      // 3. Negotiate Hardware Sample Rate (SET_CUR 0x21, 0x01) WHILE STILL IN ALTSETTING 0 (DAC MUTED)
      if (g_audioState.uacVersion.load() == 2 &&
          g_audioState.clockSourceId != -1) {
        uint32_t sr = newSampleRate;
        uint8_t data[4];
        data[0] = sr & 0xFF;
        data[1] = (sr >> 8) & 0xFF;
        data[2] = (sr >> 16) & 0xFF;
        data[3] = (sr >> 24) & 0xFF;

        int sr_res = -1;
        for (int retry = 0; retry < 3; retry++) {
          sr_res = libusb_control_transfer(g_audioState.usbHandle,
                                           0x21, 0x01, (0x01 << 8),
                                           (g_audioState.clockSourceId << 8) |
                                               g_audioState.acInterfaceNum,
                                           data, 4, 1000);
          if (sr_res >= 0) break;
          std::this_thread::sleep_for(std::chrono::milliseconds(15));
        }

        uint8_t verify_data[4] = {0};
        int rv = -1;
        for (int retry = 0; retry < 5; retry++) {
          rv = libusb_control_transfer(
              g_audioState.usbHandle,
              0xA1, 0x01, (0x01 << 8),
              (g_audioState.clockSourceId << 8) | g_audioState.acInterfaceNum,
              verify_data, 4, 1000);
          if (rv >= 4) break;
          std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }
        uint32_t verified_sr = 0;
        if (rv >= 4) {
          verified_sr = verify_data[0] | (verify_data[1] << 8) |
                        (verify_data[2] << 16) | (verify_data[3] << 24);
          g_audioState.lastKnownGoodSampleRate.store(newSampleRate);
          g_audioState.hasValidatedRate.store(true);
        } else {
          g_audioState.sampleRateUnverified.store(true);
          g_audioState.lastKnownGoodSampleRate.store(newSampleRate);
        }
        snprintf(dbg, sizeof(dbg),
                 "UAC2 Clock Reconfig (Muted): iface=%d alt=%d, SET_CUR=%d, GET_CUR=%d (verified_sr=%u)",
                 selected_alt.interface_num, selected_alt.altsetting, sr_res, rv, verified_sr);
        record_usb_diag(dbg);
      } else if (g_audioState.uacVersion.load() == 1) {
        uint32_t sr = newSampleRate;
        unsigned char data[3];
        data[0] = sr & 0xFF;
        data[1] = (sr >> 8) & 0xFF;
        data[2] = (sr >> 16) & 0xFF;
        libusb_control_transfer(g_audioState.usbHandle, 0x22, 0x01,
                                (0x01 << 8), selected_alt.ep_out,
                                data, 3, 1000);
        g_audioState.lastKnownGoodSampleRate.store(sr);
      }

      // 4. Essential hardware PLL settling time while still in altsetting 0
      auto pll_start = std::chrono::steady_clock::now();
      std::this_thread::sleep_for(std::chrono::milliseconds(40));
      auto pll_end = std::chrono::steady_clock::now();
      g_audioState.lastPllRelockUs.store(
          std::chrono::duration_cast<std::chrono::microseconds>(pll_end - pll_start).count());

      // 5. Activate streaming altsetting as the FINAL hardware step
      LOGI("playAudio: Activating streaming altsetting %d (bit depth %d bytes)...",
           selected_alt.altsetting, selected_alt.subframe_size);
      auto settle_start = std::chrono::steady_clock::now();
      int r = libusb_set_interface_alt_setting(
           g_audioState.usbHandle, selected_alt.interface_num, selected_alt.altsetting);
      int ioctl_alt_res = -999;
      if (r != 0 && g_audioState.usbFd >= 0) {
        struct usbdevfs_setinterface setintf;
        memset(&setintf, 0, sizeof(setintf));
        setintf.interface = (unsigned int)selected_alt.interface_num;
        setintf.altsetting = (unsigned int)selected_alt.altsetting;
        ioctl_alt_res = ioctl(g_audioState.usbFd, USBDEVFS_SETINTERFACE, &setintf);
      }
      snprintf(dbg, sizeof(dbg),
               "Activate Altsetting: iface=%d alt=%d ep=0x%02X maxPkt=%d pktsPerSec=%d -> libusb=%d (%s), ioctl=%d",
               selected_alt.interface_num, selected_alt.altsetting, selected_alt.ep_out,
               selected_alt.max_packet_size, selected_alt.packets_per_sec,
               r, libusb_error_name(r), ioctl_alt_res);
      record_usb_diag(dbg);
      std::this_thread::sleep_for(std::chrono::milliseconds(5));
      auto settle_end = std::chrono::steady_clock::now();
      g_audioState.lastInterfaceSettleUs.store(
          std::chrono::duration_cast<std::chrono::microseconds>(settle_end - settle_start).count());

      g_audioState.usbAudioInterface = selected_alt.interface_num;
      g_audioState.epAddress = selected_alt.ep_out;
      g_audioState.epFeedback = selected_alt.ep_feedback;
      g_audioState.isAsync = selected_alt.is_async;
      g_audioState.maxPacketSize = selected_alt.max_packet_size;
      if (selected_alt.packets_per_sec > 0) {
        g_audioState.usb_frames_per_sec.store(selected_alt.packets_per_sec);
      } else if (g_audioState.uacVersion.load() == 2) {
        g_audioState.usb_frames_per_sec.store(8000);
      }
      g_audioState.subframeSize.store(selected_alt.subframe_size);
      g_audioState.currentAltSetting.store(selected_alt.altsetting);
      g_audioState.iso_fixed_rem = 0;
      altsetting_changed = true;
      set_volume(g_audioState.rawLinearVolume);
    }
  }

  // === STEP 3: PRE-ROLL PRIMING & IMMEDIATE STREAM LAUNCH ===
  g_audioState.isSwapping.store(false);
  g_audioState.swappingZeroUrbCount.store(0);
  g_audioState.isSwappingAck.store(false);

  apply_pending_seek_before_start(newSampleRate, totalFrames, g_audioState.pcmBufferCapacityFrames.load());

  if (needs_reconfig) {
    double silence_duration = (rate_changed || altsetting_changed) ? 0.20 : (stream_was_stopped ? 0.08 : 0.015);
    uint32_t silence_frames = static_cast<uint32_t>(newSampleRate * silence_duration);
    g_audioState.warmupSilenceFrames.store(silence_frames);
    g_audioState.lastAntiPopPaddingUs.store(static_cast<int64_t>(silence_duration * 1000000.0));
    g_audioState.currentVolume = 0.0f;
    g_audioState.isPlaying.store(true);
    // Immediate stream launch within < 1ms of altsetting activation
    start_iso_streaming_thread("TsrossaIso");
  } else {
    // Seamless swap without restarting ISO thread: micro-ramp volume
    g_audioState.warmupSilenceFrames.store(static_cast<uint32_t>(newSampleRate * 0.015));
    g_audioState.currentVolume = 0.0f;
    g_audioState.isPlaying.store(true);
    if (g_audioState.isoThread == nullptr) {
      start_iso_streaming_thread("TsrossaIso");
    }
  }

  if (g_audioState.usbHandle != nullptr && g_audioState.activeIsoTransfers.load() == 0) {
    snprintf(dbg, sizeof(dbg),
             "=== playAudio ERROR: 0 active Isochronous transfers! USB stream failed to start (iface=%d alt=%d ep=0x%02X) ===",
             g_audioState.usbAudioInterface, g_audioState.currentAltSetting.load(), g_audioState.epAddress);
    record_usb_diag(dbg);
    stop_and_join_iso_thread("zero active transfers");
    {
      std::lock_guard<std::mutex> dlock(g_audioState.decoderMutex);
      if (g_audioState.activeDecoder != nullptr) {
        g_audioState.activeDecoder->close();
        g_audioState.activeDecoder.reset();
      }
    }
    playAudio_cleanup_on_negotiation_failure();
    return -3;
  }

  // === STEP 4: ENSURE BACKGROUND DECODE THREAD IS RUNNING ===
  bool needMoreDecode = (g_audioState.decodedFrames.load(std::memory_order_relaxed) <
                         g_audioState.totalFrames.load(std::memory_order_relaxed));
  g_audioState.cancelDecoding.store(false);
  g_audioState.isDecoding.store(needMoreDecode, std::memory_order_release);
  if (g_audioState.decodeThread == nullptr) {
    g_audioState.decodeThread = new std::thread(decoder_thread_func);
  } else {
    g_audioState.decoderCv.notify_one();
  }

  snprintf(dbg, sizeof(dbg), "=== playAudio SUCCESS: isPlaying=true, activeIsoTransfers=%d ===",
           g_audioState.activeIsoTransfers.load());
  record_usb_diag(dbg);
  return 0;
}

void pause_audio_internal() {
  g_audioState.isPlaying.store(false);
}

bool resume_audio_internal() {
  g_audioState.isSwapping.store(false);
  g_audioState.isPlaying.store(true);
  if ((g_audioState.isoThread == nullptr || g_audioState.isoThreadExited.load()) && g_audioState.usbHandle != nullptr) {
    LOGW("resumeAudio: isoThread is dead! Falling back to playAudio restart.");
    g_audioState.isPlaying.store(false);
    return false;
  }
  // After DAC reconnect warmup, pcmBuffer is cleared and decodedFrames reset to 0.
  // isoThread (TsrossaIsoWarm) may still be alive but there's no PCM data to play.
  // Return false so Kotlin falls back to playTrack() which re-decodes the file.
  if (g_audioState.pcmBuffer.empty() || g_audioState.decodedFrames.load() == 0) {
    LOGW("resumeAudio: No PCM data (buffer empty or decodedFrames=0). Falling back to playAudio restart.");
    g_audioState.isPlaying.store(false);
    return false;
  }
  return true;
}

void stop_audio_internal() {
  g_audioState.isPlaying = false;
  g_audioState.isSwapping.store(false);
  g_audioState.needGaplessDecoderHandoff.store(false);
  stop_and_join_decode_thread();
  {
    std::lock_guard<std::mutex> lock(g_audioState.decoderMutex);
    if (g_audioState.activeDecoder != nullptr) {
      g_audioState.activeDecoder->close();
      g_audioState.activeDecoder.reset();
    }
  }
  stop_and_join_iso_thread("stopAudio");
  if (g_audioState.usbHandle != nullptr && g_audioState.usbAudioInterface >= 0) {
    libusb_set_interface_alt_setting(g_audioState.usbHandle, g_audioState.usbAudioInterface, 0);
    g_audioState.currentAltSetting.store(0);
  }
}

bool prepare_next_track_internal(const std::string &path) {
  uint32_t myGen = ++g_audioState.prepareNextGen;

  // Wait briefly until initial primary track buffering settles (max ~1 second)
  int waitTicks = 0;
  while (g_audioState.isDecoding.load(std::memory_order_acquire) && waitTicks < 20) {
    if (!g_audioState.isPlaying.load() || g_audioState.cancelDecoding.load()) {
      return false;
    }
    if (g_audioState.prepareNextGen.load() != myGen) {
      return false;
    }
    uint64_t decoded = g_audioState.decodedFrames.load(std::memory_order_relaxed);
    uint64_t readIdx = g_audioState.pcmIndex.load(std::memory_order_relaxed);
    uint32_t sr = g_audioState.sampleRate.load(std::memory_order_relaxed);
    if (sr > 0 && decoded > readIdx && (decoded - readIdx) >= static_cast<uint64_t>(sr) * 5) {
      break; // Ring buffer already has >= 5 seconds of audio ahead; safe to pre-buffer next track
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(50));
    waitTicks++;
  }

  auto nextDecoder = std::make_unique<AudioDecoder>();
  if (!nextDecoder->open(path.c_str())) {
    return false;
  }
  uint64_t totalFrames = nextDecoder->totalPCMFrameCount;
  int channels = nextDecoder->channels;
  int sampleRate = nextDecoder->sampleRate;
  int bitsPerSample = nextDecoder->bitsPerSample;
  if (channels <= 0) channels = 2;
  
  // Partial pre-buffering up to MAX_NEXT_RING_BUFFER_BYTES (32 MB) without rejecting tracks > 32 MB
  uint64_t maxNextFrames = MAX_NEXT_RING_BUFFER_BYTES / ((uint64_t)channels * sizeof(int32_t));
  uint64_t targetCapacityFrames = std::min(totalFrames, maxNextFrames);

  std::vector<int32_t> tempBuffer;
  try {
    tempBuffer.resize(targetCapacityFrames * channels);
  } catch (const std::bad_alloc &e) {
    LOGE("OOM: Not enough memory for next track pre-buffer (%llu frames)!", (unsigned long long)targetCapacityFrames);
    nextDecoder->close();
    return false;
  }
  
  size_t framesRead = 0;
  size_t chunkSize = sampleRate; // Decode 1 second at a time
  
  while (framesRead < targetCapacityFrames) {
    if (g_audioState.prepareNextGen.load() != myGen) {
      LOGW("prepareNextTrack: Aborted decode by newer generation request!");
      nextDecoder->close();
      return false;
    }
    
    size_t toRead = targetCapacityFrames - framesRead;
    if (toRead > chunkSize) toRead = chunkSize;
    
    size_t read = nextDecoder->read_pcm_frames_s32(toRead, tempBuffer.data() + (framesRead * channels));
    if (read == 0) break;
    framesRead += read;
    std::this_thread::sleep_for(std::chrono::milliseconds(2));
  }

  if (g_audioState.prepareNextGen.load() != myGen) {
    LOGW("prepareNextTrack: Aborted swap BEFORE COMMIT PHASE by newer request!");
    if (nextDecoder != nullptr) nextDecoder->close();
    return false;
  }

  std::unique_lock<std::mutex> lock(g_apiMutex, std::defer_lock);
  bool acquired = false;
  for (int retry = 0; retry < 50; retry++) {
    if (g_audioState.prepareNextGen.load() != myGen) {
      if (nextDecoder != nullptr) nextDecoder->close();
      return false;
    }
    if (lock.try_lock()) {
      acquired = true;
      break;
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
  }

  if (!acquired) {
    if (nextDecoder != nullptr) nextDecoder->close();
    return false;
  }

  if (g_audioState.prepareNextGen.load() != myGen) {
    if (nextDecoder != nullptr) nextDecoder->close();
    return false;
  }

  if (!g_audioState.pcmBufferNext.empty()) {
    g_audioState.pcmBufferNext.clear();
  }
  
  g_audioState.sampleRateNext.store(sampleRate);
  g_audioState.channelsNext.store(channels);
  g_audioState.sourceBitDepthNext.store(bitsPerSample);
  g_audioState.nextTotalFrames.store(totalFrames);
  g_audioState.nextDecodedFrames.store(framesRead);
  g_audioState.nextPcmBufferCapacityFrames.store(targetCapacityFrames);
  {
    std::lock_guard<std::mutex> dlock(g_audioState.decoderMutex);
    g_audioState.nextActiveDecoder = std::move(nextDecoder);
  }
  g_audioState.pcmBufferNext = std::move(tempBuffer);
  g_audioState.nextFilePath = path;
  g_audioState.hasNextTrack = true;
  
  return true;
}

bool clear_next_track_internal() {
  g_audioState.prepareNextGen++;
  ApiMutexLock lock(__func__);
  g_audioState.hasNextTrack.store(false);
  g_audioState.nextFilePath = "";
  if (!g_audioState.pcmBufferNext.empty()) {
    g_audioState.pcmBufferNext.clear();
  }
  g_audioState.sampleRateNext.store(0);
  g_audioState.channelsNext.store(0);
  g_audioState.sourceBitDepthNext.store(0);
  g_audioState.nextTotalFrames.store(0);
  g_audioState.nextDecodedFrames.store(0);
  g_audioState.nextPcmBufferCapacityFrames.store(0);
  {
    std::lock_guard<std::mutex> dlock(g_audioState.decoderMutex);
    if (g_audioState.needGaplessDecoderHandoff.exchange(false, std::memory_order_acq_rel)) {
      if (g_audioState.activeDecoder != nullptr) {
        g_audioState.activeDecoder->close();
      }
      g_audioState.activeDecoder = std::move(g_audioState.nextActiveDecoder);
    } else if (g_audioState.nextActiveDecoder != nullptr) {
      g_audioState.nextActiveDecoder->close();
      g_audioState.nextActiveDecoder.reset();
    }
  }
  return true;
}

void clean_garbage_internal() {
  std::vector<int32_t> localGarbage;
  {
    std::lock_guard<std::mutex> lock(g_stateMutex);
    localGarbage.swap(g_audioState.pcmBufferGarbage);
  }
  if (!localGarbage.empty()) {
    localGarbage.clear();
    localGarbage.shrink_to_fit();
  }
}

bool seek_to_internal(double targetSeconds) {
  if (targetSeconds < 0.0) targetSeconds = 0.0;
  uint32_t sr = g_audioState.sampleRate.load(std::memory_order_acquire);
  uint64_t total = g_audioState.totalFrames.load(std::memory_order_acquire);

  if (g_audioState.isInitializingTrack.load(std::memory_order_acquire) || sr == 0 || total == 0) {
    g_audioState.pendingSeekSeconds.store(targetSeconds, std::memory_order_release);
    return true;
  }

  uint64_t targetFrame = static_cast<uint64_t>(targetSeconds * static_cast<double>(sr));
  uint64_t safetyTail = std::min<uint64_t>(sr / 4, (total > 1) ? (total - 1) : 0);
  uint64_t maxSafeFrame = (total > safetyTail && safetyTail > 0) ? (total - safetyTail) : (total - 1);
  if (targetFrame > maxSafeFrame) {
    targetFrame = maxSafeFrame;
  }

  {
    std::lock_guard<std::mutex> lock(g_audioState.decoderMutex);
    // If a gapless decoder handoff is pending, complete it immediately under the lock
    if (g_audioState.needGaplessDecoderHandoff.exchange(false, std::memory_order_acq_rel)) {
      if (g_audioState.activeDecoder != nullptr) {
        g_audioState.activeDecoder->close();
      }
      g_audioState.activeDecoder = std::move(g_audioState.nextActiveDecoder);
    }

    g_audioState.seekGeneration.fetch_add(1, std::memory_order_acq_rel);

    uint64_t capacity = g_audioState.pcmBufferCapacityFrames.load(std::memory_order_acquire);
    uint64_t decoded = g_audioState.decodedFrames.load(std::memory_order_acquire);

    // Fast-Path 1: Entire track fits in RAM and is already 100% decoded
    if (capacity > 0 && total <= capacity && decoded >= total) {
      g_audioState.pcmIndex.store(targetFrame, std::memory_order_release);
    } else if (g_audioState.activeDecoder != nullptr) {
      // Streaming / Partial buffer seek: seek decoder and synchronize counters atomically under lock
      g_audioState.activeDecoder->seek(targetFrame);
      g_audioState.decodedFrames.store(targetFrame, std::memory_order_release);
      g_audioState.pcmIndex.store(targetFrame, std::memory_order_release);
      g_audioState.isDecoding.store(true, std::memory_order_release);
    } else if (targetFrame < decoded && capacity > 0 && decoded <= capacity) {
      // Fallback if activeDecoder is null but targetFrame is within decoded linear buffer
      g_audioState.pcmIndex.store(targetFrame, std::memory_order_release);
    }
  }

  g_audioState.decoderCv.notify_one();
  return true;
}

double get_position_internal() {
  if (g_audioState.sampleRate == 0) return 0.0;
  return (double)g_audioState.pcmIndex.load() / (double)g_audioState.sampleRate.load();
}

bool trigger_warmup_internal(int durationMs) {
  if (g_audioState.usbHandle == nullptr) {
    LOGW("trigger_warmup: No USB DAC connected.");
    return false;
  }

  // If already warming up, toggle it OFF (cancel)
  if (g_audioState.isWarmingUp.load()) {
    LOGI("trigger_warmup: Warmup currently active. Toggling OFF (cancelling).");
    cancel_warmup_internal();
    return false;
  }

  // If playback is actively streaming audio, do not interrupt active stream with manual warmup
  if (g_audioState.isPlaying.load()) {
    LOGI("trigger_warmup: Playback active. Skipping warmup trigger.");
    return true;
  }

  // Stop any existing paused ISO stream before reconfiguring interface altsetting
  if (g_audioState.isoThread != nullptr) {
    stop_and_join_iso_thread("warmup start");
  }

  uint32_t sr = g_audioState.sampleRate.load();
  if (sr == 0) {
    sr = g_audioState.lastKnownGoodSampleRate.load();
    if (sr == 0) sr = 48000;
    g_audioState.sampleRate.store(sr);
  }

  if (g_audioState.channels.load() == 0) {
    g_audioState.channels.store(2);
  }

  if (!g_audioState.validAlts.empty()) {
    const auto &alt = g_audioState.validAlts[0];
    g_audioState.subframeSize.store(alt.subframe_size);
    g_audioState.maxPacketSize = alt.max_packet_size;
    g_audioState.epAddress = alt.ep_out;
    g_audioState.usbAudioInterface = alt.interface_num;

    libusb_claim_interface(g_audioState.usbHandle, alt.interface_num);

    // 1. Put DAC in hardware mute (altsetting 0) first
    libusb_set_interface_alt_setting(g_audioState.usbHandle, alt.interface_num, 0);
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
    g_audioState.currentAltSetting.store(0);

    // 2. Negotiate hardware sample rate while in altsetting 0
    if (g_audioState.uacVersion.load() == 2 &&
        g_audioState.clockSourceProgrammable &&
        g_audioState.clockSourceId != -1) {
      uint8_t data[4];
      data[0] = sr & 0xFF;
      data[1] = (sr >> 8) & 0xFF;
      data[2] = (sr >> 16) & 0xFF;
      data[3] = (sr >> 24) & 0xFF;
      libusb_control_transfer(g_audioState.usbHandle,
                              0x21, 0x01, (0x01 << 8),
                              (g_audioState.clockSourceId << 8) | g_audioState.acInterfaceNum,
                              data, 4, 1000);
    } else if (g_audioState.uacVersion.load() == 1) {
      unsigned char data[3];
      data[0] = sr & 0xFF;
      data[1] = (sr >> 8) & 0xFF;
      data[2] = (sr >> 16) & 0xFF;
      libusb_control_transfer(g_audioState.usbHandle, 0x22, 0x01,
                              (0x01 << 8), g_audioState.epAddress,
                              data, 3, 1000);
    }

    // 3. PLL settling time while still in altsetting 0
    std::this_thread::sleep_for(std::chrono::milliseconds(25));

    // 4. Activate streaming altsetting
    int r = libusb_set_interface_alt_setting(g_audioState.usbHandle, alt.interface_num, alt.altsetting);
    if (r != 0 && g_audioState.usbFd >= 0) {
      struct usbdevfs_setinterface setintf;
      memset(&setintf, 0, sizeof(setintf));
      setintf.interface = (unsigned int)alt.interface_num;
      setintf.altsetting = (unsigned int)alt.altsetting;
      ioctl(g_audioState.usbFd, USBDEVFS_SETINTERFACE, &setintf);
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(5));
    g_audioState.currentAltSetting.store(alt.altsetting);
  } else if (g_audioState.subframeSize.load() == 0) {
    g_audioState.subframeSize.store(4);
  }

  int dur = (durationMs > 0) ? durationMs : 1500;
  uint32_t silenceFrames = static_cast<uint32_t>((static_cast<uint64_t>(sr) * dur) / 1000);
  g_audioState.warmupSilenceFrames.store(silenceFrames);
  g_audioState.isWarmingUp.store(true);

  // If no track is currently loaded, clear stale buffers to stream pure silence.
  if (g_audioState.currentFilePath.empty() && g_audioState.pcmBuffer.empty()) {
    g_audioState.pcmIndex.store(0);
    g_audioState.decodedFrames.store(0);
    g_audioState.hasNextTrack.store(false);
  }

  // Spawn isochronous thread to stream pure silence during warmup
  start_iso_streaming_thread("TsrossaIsoWarm");

  // Launch background watchdog for warmup completion
  std::thread([dur]() {
    std::this_thread::sleep_for(std::chrono::milliseconds(dur + 100));
    ApiMutexLock lock("warmup_watchdog");
    if (g_audioState.isWarmingUp.load()) {
      g_audioState.isWarmingUp.store(false);
      // If user did not start playing music during warmup, cleanly shut down isochronous thread and reset to altsetting 0
      if (!g_audioState.isPlaying.load()) {
        stop_and_join_iso_thread("warmup complete");
        if (g_audioState.usbHandle != nullptr && g_audioState.usbAudioInterface >= 0) {
          libusb_set_interface_alt_setting(g_audioState.usbHandle, g_audioState.usbAudioInterface, 0);
          g_audioState.currentAltSetting.store(0);
        }
      }
      LOGI("DAC Warmup completed.");
    }
  }).detach();

  return true;
}

bool cancel_warmup_internal() {
  if (!g_audioState.isWarmingUp.load()) return false;
  g_audioState.isWarmingUp.store(false);
  g_audioState.warmupSilenceFrames.store(0);
  if (!g_audioState.isPlaying.load()) {
    stop_and_join_iso_thread("warmup cancelled");
    if (g_audioState.usbHandle != nullptr && g_audioState.usbAudioInterface >= 0) {
      libusb_set_interface_alt_setting(g_audioState.usbHandle, g_audioState.usbAudioInterface, 0);
      g_audioState.currentAltSetting.store(0);
    }
  }
  LOGI("DAC Warmup cancelled.");
  return true;
}

bool is_warming_up_internal() {
  return g_audioState.isWarmingUp.load();
}
