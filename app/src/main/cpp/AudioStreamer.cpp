#include "AudioStreamer.h"
#include "AudioEngineState.h"
#include "AudioDecoder.h"

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
#include <sys/resource.h>
#include <unistd.h>

void stop_and_join_decode_thread() {
  if (g_audioState.decodeThread != nullptr) {
    g_audioState.cancelDecoding.store(true);
    if (g_audioState.decodeThread->joinable())
      g_audioState.decodeThread->join();
    delete g_audioState.decodeThread;
    g_audioState.decodeThread = nullptr;
  }
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
  if (!g_audioState.pcmBuffer.empty()) {
    munlock(g_audioState.pcmBuffer.data(),
            g_audioState.pcmBuffer.size() * sizeof(int32_t));
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
      std::thread([]() {
        if (g_jvm && g_audioState.callbackObj) {
          JNIEnv *env;
          if (g_jvm->AttachCurrentThread(&env, NULL) == 0) {
            jclass clazz = env->GetObjectClass(g_audioState.callbackObj);
            jmethodID methodId =
                env->GetMethodID(clazz, "onUsbStallFault", "()V");
            if (methodId)
              env->CallVoidMethod(g_audioState.callbackObj, methodId);
            g_jvm->DetachCurrentThread();
          }
        }
      }).detach();
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
  if (usb_frames_per_sec == 0) usb_frames_per_sec = 1000;
  uint32_t base_frames = sr / usb_frames_per_sec;
  uint32_t remainder_rate = sr % usb_frames_per_sec;

  // Soft swapping check
  if (g_audioState.isSwapping.load()) {
    g_audioState.isSwappingAck.store(true);
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
    if (silenceRemaining > 0 && g_audioState.isPlaying.load()) {
      int dst_bytes_per_frame = g_audioState.channels.load() * g_audioState.subframeSize.load();
      if (dst_bytes_per_frame > 0) {
        int bytes_needed = bytes_to_send - bytes_filled;
        int frames_needed = bytes_needed / dst_bytes_per_frame;
        int frames_to_zero = std::min((int)silenceRemaining, frames_needed);
        if (frames_to_zero > 0) {
          int zero_bytes = frames_to_zero * dst_bytes_per_frame;
          memset(buffer + data_offset + bytes_filled, 0, zero_bytes);
          bytes_filled += zero_bytes;
          g_audioState.warmupSilenceFrames.fetch_sub(frames_to_zero);
        }
      }
    }

    while (bytes_filled < bytes_to_send && g_audioState.isPlaying.load() &&
           !g_audioState.pcmBuffer.empty()) {
      size_t currentIndex = g_audioState.pcmIndex.load();
      size_t totalFrames = g_audioState.decodedFrames.load();
      size_t src_bytes_per_frame =
          g_audioState.channels.load() * sizeof(int32_t);
      size_t dst_bytes_per_frame =
          g_audioState.channels.load() * g_audioState.subframeSize.load();

      size_t bytes_needed = bytes_to_send - bytes_filled;
      size_t frames_needed = bytes_needed / dst_bytes_per_frame;
      size_t frames_avail =
          (totalFrames > currentIndex) ? (totalFrames - currentIndex) : 0;
      size_t frames_to_read = std::min(frames_needed, frames_avail);

      if (frames_to_read > 0) {
        size_t num_samples = frames_to_read * g_audioState.channels.load();
        int sf_size = g_audioState.subframeSize.load();
        float target_vol = g_audioState.targetVolume.load();
        float delta = 0.0f;
        if (std::abs(target_vol - g_audioState.currentVolume) > 0.0001f) {
          delta = (target_vol - g_audioState.currentVolume) / (float)std::max((size_t)2048, num_samples);
        }

        int32_t *src = (int32_t *)((uint8_t *)g_audioState.pcmBuffer.data() +
                                   (currentIndex * src_bytes_per_frame));
        uint8_t *dst_bytes = (uint8_t *)(buffer + data_offset + bytes_filled);

        // BIT-PERFECT BYPASS
        if (g_audioState.currentVolume >= 0.999f && delta == 0.0f) {
          int src_bits = g_audioState.sourceBitDepth.load();
          if (sf_size == 4) {
            // 32-bit subslot container (UAC standard Type I PCM):
            // In dr_flac and dr_wav, samples are always scaled to full 32-bit signed range (left-aligned).
            // Direct 32-bit integer transfer preserves bit-perfect audio for all bit depths (16-bit, 24-bit, 32-bit).
            memcpy(dst_bytes, src, num_samples * sizeof(int32_t));
          } else if (sf_size == 2) {
            for (size_t s = 0; s < num_samples; s++) {
              int32_t val16 = src[s] >> 16;
              dst_bytes[s * 2] = (uint8_t)(val16 & 0xFF);
              dst_bytes[s * 2 + 1] = (uint8_t)((val16 >> 8) & 0xFF);
            }
          } else if (sf_size == 3) {
            for (size_t s = 0; s < num_samples; s++) {
              int32_t val24 = src[s] >> 8;
              dst_bytes[s * 3] = (uint8_t)(val24 & 0xFF);
              dst_bytes[s * 3 + 1] = (uint8_t)((val24 >> 8) & 0xFF);
              dst_bytes[s * 3 + 2] = (uint8_t)((val24 >> 16) & 0xFF);
            }
          }
        } else {
          // Volume scaling with TPDF dither
          for (size_t s = 0; s < num_samples; s++) {
            if (delta != 0.0f) {
              g_audioState.currentVolume += delta;
              if ((delta > 0 && g_audioState.currentVolume > target_vol) ||
                  (delta < 0 && g_audioState.currentVolume < target_vol)) {
                g_audioState.currentVolume = target_vol;
                delta = 0.0f;
              }
            }

            float scaled = (float)src[s] * g_audioState.currentVolume;
            if (g_audioState.currentVolume < 0.999f) {
              float half_lsb = 128.0f;
              if (sf_size == 2) half_lsb = 32768.0f;
              else if (sf_size == 4) half_lsb = 0.5f;

              float randA = fast_uniform_rand() * half_lsb;
              float randB = fast_uniform_rand() * half_lsb;
              scaled += (randA - randB);
            }

            if (scaled > 2147483647.0f) scaled = 2147483647.0f;
            if (scaled < -2147483648.0f) scaled = -2147483648.0f;

            int32_t val32 = (int32_t)scaled;
            if (sf_size == 2) {
              int32_t val16 = val32 >> 16;
              dst_bytes[s * 2] = val16 & 0xFF;
              dst_bytes[s * 2 + 1] = (val16 >> 8) & 0xFF;
            } else if (sf_size == 3) {
              int32_t val24 = val32 >> 8;
              dst_bytes[s * 3] = val24 & 0xFF;
              dst_bytes[s * 3 + 1] = (val24 >> 8) & 0xFF;
              dst_bytes[s * 3 + 2] = (val24 >> 16) & 0xFF;
            } else if (sf_size == 4) {
              dst_bytes[s * 4] = val32 & 0xFF;
              dst_bytes[s * 4 + 1] = (val32 >> 8) & 0xFF;
              dst_bytes[s * 4 + 2] = (val32 >> 16) & 0xFF;
              dst_bytes[s * 4 + 3] = (val32 >> 24) & 0xFF;
            }
          }
        }

        g_audioState.pcmIndex.store(currentIndex + frames_to_read);
        bytes_filled += frames_to_read * g_audioState.channels.load() * sf_size;
      } else {
        // Track finished or buffering: Check Gapless
        if (g_audioState.hasNextTrack.load()) {
          bool format_changed = (g_audioState.sampleRate.load() !=
                                 g_audioState.sampleRateNext.load()) ||
                                (g_audioState.channels.load() !=
                                 g_audioState.channelsNext.load()) ||
                                (g_audioState.sourceBitDepth.load() !=
                                 g_audioState.sourceBitDepthNext.load());

          if (format_changed) {
            LOGW("Gapless aborted due to format change. Falling back to playAudio()");
            g_audioState.hasNextTrack.store(false);
            continue;
          }

          g_audioState.pcmBufferGarbage = std::move(g_audioState.pcmBuffer);
          g_audioState.pcmBuffer = std::move(g_audioState.pcmBufferNext);
          g_audioState.sampleRate.store(g_audioState.sampleRateNext.load());
          g_audioState.channels.store(g_audioState.channelsNext.load());
          g_audioState.sourceBitDepth.store(g_audioState.sourceBitDepthNext.load());
          g_audioState.hasNextTrack.store(false);
          g_audioState.decodedFrames.store(g_audioState.pcmBuffer.size() /
                                           g_audioState.channels.load());
          g_audioState.pcmIndex.store(0);
          g_audioState.currentFilePath = g_audioState.nextFilePath;
          g_audioState.nextFilePath = "";

          if (g_jvm && g_audioState.callbackObj) {
            JNIEnv *env;
            if (g_jvm->AttachCurrentThread(&env, NULL) == 0) {
              jclass clazz = env->GetObjectClass(g_audioState.callbackObj);
              jmethodID methodId =
                  env->GetMethodID(clazz, "onTrackTransition", "(Ljava/lang/String;)V");
              if (methodId) {
                jstring pathStr = env->NewStringUTF(g_audioState.currentFilePath.c_str());
                env->CallVoidMethod(g_audioState.callbackObj, methodId, pathStr);
                env->DeleteLocalRef(pathStr);
              }
              g_jvm->DetachCurrentThread();
            }
          }
        } else {
          // If still decoding the current track in background, just wait/send silence rather than finish!
          if (g_audioState.isDecoding.load()) {
            break;
          }
          g_audioState.isFinished.store(true);
          g_audioState.isPlaying.store(false);

          if (g_jvm && g_audioState.callbackObj) {
            JNIEnv *env;
            if (g_jvm->AttachCurrentThread(&env, NULL) == 0) {
              jclass clazz = env->GetObjectClass(g_audioState.callbackObj);
              jmethodID methodId =
                  env->GetMethodID(clazz, "onTrackFinished", "()V");
              if (methodId)
                env->CallVoidMethod(g_audioState.callbackObj, methodId);
              g_jvm->DetachCurrentThread();
            }
          }
          break;
        }
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
        std::thread([]() {
          if (g_jvm && g_audioState.callbackObj) {
            JNIEnv *env;
            if (g_jvm->AttachCurrentThread(&env, NULL) == 0) {
              jclass clazz = env->GetObjectClass(g_audioState.callbackObj);
              jmethodID methodId =
                  env->GetMethodID(clazz, "onUsbStallFault", "()V");
              if (methodId)
                env->CallVoidMethod(g_audioState.callbackObj, methodId);
              g_jvm->DetachCurrentThread();
            }
          }
        }).detach();
      }
    }
  } else {
    g_audioState.activeIsoTransfers.fetch_sub(1);
  }
}

int play_audio_internal(const std::string &savedPath, jobject thiz, JNIEnv *env) {
  if (g_audioState.deviceWedged.load()) {
    LOGE("Refusing to start playAudio because device is wedged from previous timeout. Reconnect required.");
    return -4;
  }
  g_audioState.sampleRateUnverified.store(false);
  g_audioState.isPlaying = false;
  g_audioState.isFinished = false;

  if (g_audioState.callbackObj != nullptr)
    env->DeleteGlobalRef(g_audioState.callbackObj);
  g_audioState.callbackObj = env->NewGlobalRef(thiz);

  // === OPTIMIZATION 1: INSTANT GAPLESS HANDOVER ON MANUAL NEXT ===
  if (g_audioState.hasNextTrack.load() &&
      g_audioState.nextFilePath == savedPath &&
      !g_audioState.pcmBufferNext.empty()) {
    bool format_match = (g_audioState.sampleRate.load() == g_audioState.sampleRateNext.load()) &&
                        (g_audioState.channels.load() == g_audioState.channelsNext.load()) &&
                        (g_audioState.sourceBitDepth.load() == g_audioState.sourceBitDepthNext.load());
    if (format_match && g_audioState.isoThread != nullptr && !g_audioState.stopIsoThread.load()) {
      LOGI("playAudio: INSTANT GAPLESS HANDOVER for manual Next -> %s", savedPath.c_str());
      stop_and_join_decode_thread();
      g_audioState.pcmBufferGarbage = std::move(g_audioState.pcmBuffer);
      g_audioState.pcmBuffer = std::move(g_audioState.pcmBufferNext);
      g_audioState.sampleRate.store(g_audioState.sampleRateNext.load());
      g_audioState.channels.store(g_audioState.channelsNext.load());
      g_audioState.sourceBitDepth.store(g_audioState.sourceBitDepthNext.load());
      g_audioState.hasNextTrack.store(false);
      g_audioState.decodedFrames.store(g_audioState.pcmBuffer.size() / g_audioState.channels.load());
      g_audioState.pcmIndex.store(0);
      g_audioState.currentFilePath = savedPath;
      g_audioState.nextFilePath = "";
      g_audioState.isPlaying.store(true);
      return 0; // ZERO DELAY INSTANT START!
    }
  }

  // Open audio file
  auto decoder = std::make_shared<AudioDecoder>();
  if (!decoder->open(savedPath.c_str())) {
    char errDiag[512];
    snprintf(errDiag, sizeof(errDiag), "playAudio ERROR: %s -> %s",
             decoder->lastOpenError.c_str(), savedPath.c_str());
    record_usb_diag(errDiag);
    LOGE("%s", errDiag);
    return -1;
  }

  g_audioState.currentFilePath = savedPath;
  g_audioState.nextFilePath = "";

  uint32_t newSampleRate = decoder->sampleRate;
  uint32_t newChannels = decoder->channels;
  uint32_t newSourceBitDepth = decoder->bitsPerSample;
  uint64_t totalFrames = decoder->totalPCMFrameCount;

  g_audioState.prepareNextGen.fetch_add(1);

  char dbg[512];
  snprintf(dbg, sizeof(dbg), "=== playAudio START: rate=%u, bitDepth=%u (sf=%d, frames=%llu) ===",
           newSampleRate, newSourceBitDepth, (int)(newSourceBitDepth / 8), (unsigned long long)totalFrames);
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

      std::string filename = savedPath;
      size_t lastSlash = filename.find_last_of('/');
      if (lastSlash != std::string::npos) filename = filename.substr(lastSlash + 1);

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

      decoder->close();
      g_audioState.currentFilePath = "";
      return -2;
    }
  }

  // Check Altsetting
  int target_sf = newSourceBitDepth / 8;
  if (target_sf == 0) target_sf = 2;

  bool rate_changed = (g_audioState.sampleRate.load() != newSampleRate);
  bool altsetting_changed = false;
  bool stream_was_stopped = (g_audioState.isoThread == nullptr);

  bool needs_reconfig = (target_sf != g_audioState.subframeSize.load()) ||
                        rate_changed ||
                        stream_was_stopped ||
                        g_audioState.isWarmingUp.load();

  if (g_audioState.usbHandle != nullptr && needs_reconfig) {
    if (g_audioState.isWarmingUp.load()) {
      g_audioState.isWarmingUp.store(false);
      g_audioState.warmupSilenceFrames.store(0);
    }
    stop_and_join_iso_thread("stream parameter change or startup");

    AltSettingInfo *selected_alt = nullptr;
    if (!g_audioState.validAlts.empty()) {
      for (auto &a : g_audioState.validAlts) {
        if (a.subframe_size == target_sf) {
          selected_alt = &a;
          break;
        }
      }
      if (!selected_alt) {
        for (auto &a : g_audioState.validAlts) {
          if (a.subframe_size >= target_sf) {
            selected_alt = &a;
            break;
          }
        }
      }
      if (!selected_alt) selected_alt = &g_audioState.validAlts[0];
    }

    if (selected_alt && (g_audioState.currentAltSetting.load() != selected_alt->altsetting ||
                         target_sf != g_audioState.subframeSize.load() ||
                         stream_was_stopped)) {
      LOGI("playAudio: Activating altsetting %d (bit depth %d bytes)...",
           selected_alt->altsetting, selected_alt->subframe_size);

      libusb_claim_interface(g_audioState.usbHandle, selected_alt->interface_num);
      int r = libusb_set_interface_alt_setting(
          g_audioState.usbHandle, selected_alt->interface_num, selected_alt->altsetting);
      if (r != 0 && g_audioState.usbFd >= 0) {
        struct usbdevfs_setinterface setintf;
        memset(&setintf, 0, sizeof(setintf));
        setintf.interface = (unsigned int)selected_alt->interface_num;
        setintf.altsetting = (unsigned int)selected_alt->altsetting;
        ioctl(g_audioState.usbFd, USBDEVFS_SETINTERFACE, &setintf);
      }

      g_audioState.usbAudioInterface = selected_alt->interface_num;
      g_audioState.epAddress = selected_alt->ep_out;
      g_audioState.maxPacketSize = selected_alt->max_packet_size;
      g_audioState.subframeSize.store(selected_alt->subframe_size);
      g_audioState.currentAltSetting.store(selected_alt->altsetting);
      altsetting_changed = true;
    }

    // Negotiate Hardware Sample Rate immediately after configuring active streaming interface
    if (rate_changed || stream_was_stopped || !g_audioState.hasValidatedRate.load()) {
      if (g_audioState.uacVersion.load() == 2 &&
          g_audioState.clockSourceProgrammable &&
          g_audioState.clockSourceId != -1) {
        uint32_t sr = newSampleRate;
        uint8_t data[4];
        data[0] = sr & 0xFF;
        data[1] = (sr >> 8) & 0xFF;
        data[2] = (sr >> 16) & 0xFF;
        data[3] = (sr >> 24) & 0xFF;

        int r = -1;
        for (int retry = 0; retry < 3; retry++) {
          r = libusb_control_transfer(g_audioState.usbHandle,
                                      0x21, 0x01, (0x01 << 8),
                                      (g_audioState.clockSourceId << 8) |
                                          g_audioState.acInterfaceNum,
                                      data, 4, 1000);
          if (r >= 0) break;
          std::this_thread::sleep_for(std::chrono::milliseconds(20));
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

        if (rv >= 4) {
          g_audioState.lastKnownGoodSampleRate.store(sr);
          g_audioState.hasValidatedRate.store(true);
        } else {
          g_audioState.sampleRateUnverified.store(true);
          g_audioState.lastKnownGoodSampleRate.store(sr);
        }
      } else if (g_audioState.uacVersion.load() == 1) {
        uint32_t sr = newSampleRate;
        unsigned char data[3];
        data[0] = sr & 0xFF;
        data[1] = (sr >> 8) & 0xFF;
        data[2] = (sr >> 16) & 0xFF;
        libusb_control_transfer(g_audioState.usbHandle, 0x22, 0x01,
                                (0x01 << 8), g_audioState.epAddress,
                                data, 3, 1000);
        g_audioState.lastKnownGoodSampleRate.store(sr);
      }

      // Essential hardware PLL lock-in settling time (gives DAC crystal oscillator time to stabilize)
      std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }
  }

  // Soft swapping check if altsetting and sample rate didn't change
  if (!altsetting_changed && !rate_changed && g_audioState.isoThread != nullptr &&
      !g_audioState.stopIsoThread.load()) {
    g_audioState.isSwapping.store(true);
    auto wait_start = std::chrono::steady_clock::now();
    bool ack_timeout = false;
    while (!g_audioState.isSwappingAck.load() && !g_audioState.stopIsoThread.load()) {
      std::this_thread::sleep_for(std::chrono::milliseconds(1));
      auto now = std::chrono::steady_clock::now();
      if (std::chrono::duration_cast<std::chrono::milliseconds>(now - wait_start).count() > 100) {
        ack_timeout = true;
        break;
      }
    }
    if (ack_timeout) {
      stop_and_join_iso_thread("isSwappingAck timeout");
    }
  }

  stop_and_join_decode_thread();

  if (!g_audioState.pcmBuffer.empty()) {
    munlock(g_audioState.pcmBuffer.data(), g_audioState.pcmBuffer.size() * sizeof(int32_t));
    g_audioState.pcmBuffer.clear();
  }
  if (!g_audioState.pcmBufferNext.empty()) {
    munlock(g_audioState.pcmBufferNext.data(), g_audioState.pcmBufferNext.size() * sizeof(int32_t));
    g_audioState.pcmBufferNext.clear();
  }

  g_audioState.sampleRate.store(newSampleRate);
  g_audioState.channels.store(newChannels);
  g_audioState.sourceBitDepth.store(newSourceBitDepth);

  try {
    g_audioState.pcmBuffer.resize(totalFrames * newChannels);
  } catch (const std::bad_alloc &e) {
    LOGE("OOM: Not enough memory for %llu frames!", (unsigned long long)totalFrames);
    decoder->close();
    g_audioState.isSwapping.store(false);
    return -1;
  }

  // === OPTIMIZATION 2: SAFE 10-SECOND INSTANT PRELOAD ===
  // Synchronously decode the first 10 seconds so the stream never starves
  size_t initialFrames = (size_t)newSampleRate * 10;
  if (initialFrames > totalFrames) initialFrames = totalFrames;

  size_t initialRead = decoder->read_pcm_frames_s32(initialFrames, g_audioState.pcmBuffer.data());
  g_audioState.decodedFrames.store(initialRead);
  g_audioState.pcmIndex.store(0);

  // Strict Physical RAM Lock for decoded initial frames to eliminate MMU page-fault jitter
  mlock(g_audioState.pcmBuffer.data(), initialRead * newChannels * sizeof(int32_t));

  // If there are remaining frames, spawn background decodeThread to fill RAM buffer without UI lag
  if (initialRead < totalFrames) {
    g_audioState.cancelDecoding.store(false);
    g_audioState.isDecoding.store(true);
    g_audioState.decodeThread = new std::thread([decoder, totalFrames, newChannels, initialRead]() {
      pthread_setname_np(pthread_self(), "TsrossaDecode");
      setpriority(PRIO_PROCESS, 0, -10); // Elevated priority to prevent starvation
      size_t framesRead = initialRead;
      size_t chunkSize = 131072; // 128k frames per chunk for faster throughput
      while (framesRead < totalFrames && !g_audioState.cancelDecoding.load()) {
        size_t toRead = totalFrames - framesRead;
        if (toRead > chunkSize) toRead = chunkSize;
        size_t read = decoder->read_pcm_frames_s32(
            toRead, g_audioState.pcmBuffer.data() + (framesRead * newChannels));
        if (read == 0) break;
        framesRead += read;
        g_audioState.decodedFrames.store(framesRead);
      }
      decoder->close();
      g_audioState.isDecoding.store(false);
      // Lock completed buffer in RAM
      mlock(g_audioState.pcmBuffer.data(), framesRead * newChannels * sizeof(int32_t));
      LOGI("Background Pure-RAM preload complete: %zu frames loaded and locked in physical RAM.", framesRead);
    });
  } else {
    decoder->close();
    g_audioState.isDecoding.store(false);
  }

  g_audioState.isSwapping.store(false);

  // Auto Warmup Pre-roll (180ms silence) on cold start or sample rate change
  if (rate_changed || altsetting_changed || stream_was_stopped) {
    uint32_t silence_frames = static_cast<uint32_t>(newSampleRate * 0.18); // 180ms
    g_audioState.warmupSilenceFrames.store(silence_frames);
    g_audioState.currentVolume = 0.0f; // Smooth micro fade-in attack to eliminate DC pop
    LOGI("Warmup: Priming DAC with %u silence frames (180ms) for rate=%u", silence_frames, newSampleRate);
  }

  // Start ISO Thread if not running
  if (g_audioState.isoThread == nullptr && g_audioState.usbHandle != nullptr) {
    libusb_device *dev = libusb_get_device(g_audioState.usbHandle);
    if (dev) {
      int dev_speed = libusb_get_device_speed(dev);
      int dev_fps = (dev_speed == LIBUSB_SPEED_HIGH || dev_speed == LIBUSB_SPEED_SUPER) ? 8000 : 1000;
      g_audioState.usb_frames_per_sec.store(dev_fps);
    }
    g_audioState.phase_accumulator = 0.0;
    g_audioState.isPlaying.store(true);
    g_audioState.stopIsoThread.store(false);
    g_audioState.isoThreadExited.store(false);
    g_audioState.isoThread = new std::thread([]() {
      pthread_setname_np(pthread_self(), "TsrossaIso");
      setpriority(PRIO_PROCESS, 0, -19); // Urgent Audio
      struct sched_param param;
      param.sched_priority = sched_get_priority_max(SCHED_FIFO);
      pthread_setschedparam(pthread_self(), SCHED_FIFO, &param);

      long nprocs = sysconf(_SC_NPROCESSORS_ONLN);
      if (nprocs > 4) {
        cpu_set_t cpuset;
        CPU_ZERO(&cpuset);
        int target_core = (int)(nprocs - 2);
        CPU_SET(target_core, &cpuset);
        sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
      }

      int num_transfers = 32;
      int num_packets = 32;
      int packet_size = g_audioState.maxPacketSize;
      g_audioState.activeIsoTransfers.store(0);

      for (int i = 0; i < num_transfers; i++) {
        struct libusb_transfer *transfer = libusb_alloc_transfer(num_packets);
        uint8_t *buffer = nullptr;
        if (posix_memalign((void **)&buffer, 64, num_packets * packet_size) != 0 || buffer == nullptr) {
          buffer = (uint8_t *)calloc(num_packets, packet_size);
        } else {
          memset(buffer, 0, num_packets * packet_size);
        }
        libusb_fill_iso_transfer(transfer, g_audioState.usbHandle,
                                 g_audioState.epAddress, buffer,
                                 num_packets * packet_size, num_packets,
                                 iso_callback, nullptr, 1000);
        libusb_set_iso_packet_lengths(transfer, packet_size);

        g_audioState.activeIsoTransfers.fetch_add(1);
        int sub_res = libusb_submit_transfer(transfer);
        if (sub_res < 0) {
          g_audioState.activeIsoTransfers.fetch_sub(1);
        }
        g_audioState.transfers.push_back(transfer);
      }

      while (!g_audioState.stopIsoThread.load()) {
        struct timeval tv = {0, 50000};
        libusb_handle_events_timeout_completed(g_audioState.usbContext, &tv, nullptr);
      }

      for (auto t : g_audioState.transfers) {
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
        for (auto t : g_audioState.transfers) {
          g_audioState.orphanTransfers.push_back(t);
        }
        g_audioState.transfers.clear();
      } else {
        for (auto t : g_audioState.transfers) {
          free(t->buffer);
          libusb_free_transfer(t);
        }
        g_audioState.transfers.clear();
      }
      g_audioState.isoThreadExited.store(true);
    });
  }

  g_audioState.isPlaying = true;
  snprintf(dbg, sizeof(dbg), "=== playAudio SUCCESS: isPlaying=true, activeIsoTransfers=%d ===",
           g_audioState.activeIsoTransfers.load());
  record_usb_diag(dbg);
  return 0;
}

void pause_audio_internal() {
  g_audioState.isPlaying.store(false);
}

bool resume_audio_internal() {
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
  stop_and_join_decode_thread();
  stop_and_join_iso_thread("stopAudio");
}

bool prepare_next_track_internal(const std::string &path) {
  auto nextDecoder = std::make_unique<AudioDecoder>();
  if (!nextDecoder->open(path.c_str())) {
    return false;
  }
  uint64_t totalFrames = nextDecoder->totalPCMFrameCount;
  int channels = nextDecoder->channels;
  int sampleRate = nextDecoder->sampleRate;
  int bitsPerSample = nextDecoder->bitsPerSample;
  
  std::vector<int32_t> tempBuffer;
  try {
    tempBuffer.resize(totalFrames * channels);
  } catch (const std::bad_alloc &e) {
    LOGE("OOM: Not enough memory for next track (%llu frames)!", (unsigned long long)totalFrames);
    nextDecoder->close();
    return false;
  }
  
  uint32_t myGen = ++g_audioState.prepareNextGen;
  size_t framesRead = 0;
  size_t chunkSize = sampleRate; // Decode 1 second at a time
  
  while (framesRead < totalFrames) {
    if (g_audioState.prepareNextGen.load() != myGen) {
      LOGW("prepareNextTrack: Aborted decode by newer generation request!");
      nextDecoder->close();
      return false;
    }
    
    size_t toRead = totalFrames - framesRead;
    if (toRead > chunkSize) toRead = chunkSize;
    
    size_t read = nextDecoder->read_pcm_frames_s32(toRead, tempBuffer.data() + (framesRead * channels));
    if (read == 0) break;
    framesRead += read;
  }
  
  nextDecoder->close();

  if (g_audioState.prepareNextGen.load() != myGen) {
    LOGW("prepareNextTrack: Aborted swap BEFORE COMMIT PHASE by newer request!");
    return false;
  }

  std::unique_lock<std::mutex> lock(g_apiMutex, std::defer_lock);
  bool acquired = false;
  for (int retry = 0; retry < 50; retry++) {
    if (g_audioState.prepareNextGen.load() != myGen) {
      return false;
    }
    if (lock.try_lock()) {
      acquired = true;
      break;
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
  }

  if (!acquired) {
    return false;
  }

  if (g_audioState.prepareNextGen.load() != myGen) {
    return false;
  }

  if (!g_audioState.pcmBufferNext.empty()) {
    munlock(g_audioState.pcmBufferNext.data(),
            g_audioState.pcmBufferNext.size() * sizeof(int32_t));
    g_audioState.pcmBufferNext.clear();
  }
  
  g_audioState.sampleRateNext.store(sampleRate);
  g_audioState.channelsNext.store(channels);
  g_audioState.sourceBitDepthNext.store(bitsPerSample);
  g_audioState.pcmBufferNext = std::move(tempBuffer);
  mlock(g_audioState.pcmBufferNext.data(), g_audioState.pcmBufferNext.size() * sizeof(int32_t));
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
    munlock(g_audioState.pcmBufferNext.data(),
            g_audioState.pcmBufferNext.size() * sizeof(int32_t));
    g_audioState.pcmBufferNext.clear();
    g_audioState.pcmBufferNext.shrink_to_fit();
  }
  g_audioState.sampleRateNext.store(0);
  g_audioState.channelsNext.store(0);
  g_audioState.sourceBitDepthNext.store(0);
  return true;
}

void clean_garbage_internal() {
  if (!g_audioState.pcmBufferGarbage.empty()) {
    munlock(g_audioState.pcmBufferGarbage.data(),
            g_audioState.pcmBufferGarbage.size() * sizeof(int32_t));
    g_audioState.pcmBufferGarbage.clear();
    g_audioState.pcmBufferGarbage.shrink_to_fit();
  }
}

bool seek_to_internal(double targetSeconds) {
  uint32_t sr = g_audioState.sampleRate.load();
  if (sr == 0) return false;

  if (targetSeconds < 0.0) targetSeconds = 0.0;
  size_t targetFrame = static_cast<size_t>(targetSeconds * (double)sr);
  size_t decoded = g_audioState.decodedFrames.load();

  if (targetFrame >= decoded) {
    targetFrame = (decoded > 0) ? (decoded - 1) : 0;
  }

  g_audioState.pcmIndex.store(targetFrame);
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
    g_audioState.currentAltSetting.store(alt.altsetting);
    g_audioState.maxPacketSize = alt.max_packet_size;
    g_audioState.epAddress = alt.ep_out;
    g_audioState.usbAudioInterface = alt.interface_num;

    libusb_claim_interface(g_audioState.usbHandle, alt.interface_num);
    libusb_set_interface_alt_setting(g_audioState.usbHandle, alt.interface_num, alt.altsetting);
    if (g_audioState.usbFd >= 0) {
      struct usbdevfs_setinterface setintf;
      memset(&setintf, 0, sizeof(setintf));
      setintf.interface = (unsigned int)alt.interface_num;
      setintf.altsetting = (unsigned int)alt.altsetting;
      ioctl(g_audioState.usbFd, USBDEVFS_SETINTERFACE, &setintf);
    }
  } else if (g_audioState.subframeSize.load() == 0) {
    g_audioState.subframeSize.store(4);
  }

  int dur = (durationMs > 0) ? durationMs : 1500;
  uint32_t silenceFrames = static_cast<uint32_t>((static_cast<uint64_t>(sr) * dur) / 1000);
  g_audioState.warmupSilenceFrames.store(silenceFrames);
  g_audioState.isWarmingUp.store(true);

  // Clear stale PCM data to prevent iso_callback from reading old track audio
  // after warmup silence frames are exhausted within a packet
  g_audioState.pcmBuffer.clear();
  g_audioState.pcmIndex.store(0);
  g_audioState.decodedFrames.store(0);
  g_audioState.hasNextTrack.store(false);

  // If isoThread is not running, spawn it to stream pure silence during warmup
  if (g_audioState.isoThread == nullptr && g_audioState.usbHandle != nullptr) {
    libusb_device *dev = libusb_get_device(g_audioState.usbHandle);
    if (dev) {
      int dev_speed = libusb_get_device_speed(dev);
      int dev_fps = (dev_speed == LIBUSB_SPEED_HIGH || dev_speed == LIBUSB_SPEED_SUPER) ? 8000 : 1000;
      g_audioState.usb_frames_per_sec.store(dev_fps);
    }
    g_audioState.phase_accumulator = 0.0;
    g_audioState.isPlaying.store(true);
    g_audioState.stopIsoThread.store(false);
    g_audioState.isoThreadExited.store(false);

    g_audioState.isoThread = new std::thread([]() {
      pthread_setname_np(pthread_self(), "TsrossaIsoWarm");
      setpriority(PRIO_PROCESS, 0, -19);
      struct sched_param param;
      param.sched_priority = sched_get_priority_max(SCHED_FIFO);
      pthread_setschedparam(pthread_self(), SCHED_FIFO, &param);

      int num_transfers = 32;
      int num_packets = 32;
      int packet_size = g_audioState.maxPacketSize;
      if (packet_size <= 0) packet_size = 1024;
      g_audioState.activeIsoTransfers.store(0);

      for (int i = 0; i < num_transfers; i++) {
        struct libusb_transfer *transfer = libusb_alloc_transfer(num_packets);
        uint8_t *buffer = nullptr;
        if (posix_memalign((void **)&buffer, 64, num_packets * packet_size) != 0 || buffer == nullptr) {
          buffer = (uint8_t *)calloc(num_packets, packet_size);
        } else {
          memset(buffer, 0, num_packets * packet_size);
        }
        libusb_fill_iso_transfer(transfer, g_audioState.usbHandle,
                                 g_audioState.epAddress, buffer,
                                 num_packets * packet_size, num_packets,
                                 iso_callback, nullptr, 1000);
        libusb_set_iso_packet_lengths(transfer, packet_size);
        g_audioState.transfers.push_back(transfer);
        g_audioState.activeIsoTransfers.fetch_add(1);
        libusb_submit_transfer(transfer);
      }

      while (!g_audioState.stopIsoThread.load()) {
        struct timeval tv = {0, 10000};
        libusb_handle_events_timeout_completed(g_audioState.usbContext, &tv, nullptr);
      }

      for (auto *tr : g_audioState.transfers) {
        libusb_cancel_transfer(tr);
      }

      while (g_audioState.activeIsoTransfers.load() > 0) {
        struct timeval tv = {0, 10000};
        libusb_handle_events_timeout_completed(g_audioState.usbContext, &tv, nullptr);
      }

      for (auto *tr : g_audioState.transfers) {
        if (tr->buffer) free(tr->buffer);
        libusb_free_transfer(tr);
      }
      g_audioState.transfers.clear();
      g_audioState.isoThreadExited.store(true);
    });
  }

  // Launch background watchdog for warmup completion
  std::thread([dur]() {
    std::this_thread::sleep_for(std::chrono::milliseconds(dur + 100));
    g_audioState.isWarmingUp.store(false);
    LOGI("DAC Warmup completed.");
  }).detach();

  return true;
}

bool is_warming_up_internal() {
  return g_audioState.isWarmingUp.load();
}
