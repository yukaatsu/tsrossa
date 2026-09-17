#include <algorithm>
#include <android/log.h>
#include <atomic>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdio>
#include <cstring>
#include <dlfcn.h>
#include <jni.h>
#include <libusb.h>
#include <mutex>
#include <queue>
#include <set>
#include <string>
#include <sys/mman.h>
#include <sys/types.h>
#include <thread>
#include <unistd.h>
#include <vector>

#define DR_FLAC_IMPLEMENTATION
#include "dr_flac.h"

static JavaVM *g_jvm = nullptr;
std::mutex g_apiMutex;

#define TAG "KewAudioEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

struct ApiMutexLock {
  const char *func;
  int tid;
  std::unique_lock<std::mutex> lock;
  ApiMutexLock(const char *f)
      : func(f), tid(gettid()), lock(g_apiMutex, std::defer_lock) {
    LOGI("[Mutex] TID %d: %s -> WAITING for g_apiMutex", tid, func);
    lock.lock();
    LOGI("[Mutex] TID %d: %s -> ACQUIRED g_apiMutex", tid, func);
  }
  ~ApiMutexLock() {
    LOGI("[Mutex] TID %d: %s -> RELEASING g_apiMutex", tid, func);
    lock.unlock();
  }
};

// Ultra-fast inline PRNG for TPDF Dithering (avoiding heavy stdlib calls)
static uint32_t fast_rand_state = 123456789;
inline float fast_uniform_rand() {
  fast_rand_state = (1103515245 * fast_rand_state + 12345);
  return (float)(fast_rand_state & 0x7FFFFFFF) / (float)0x7FFFFFFF;
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  g_jvm = vm;
  return JNI_VERSION_1_6;
}

static uint64_t get_time_ms() {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return (uint64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

#define TAG "KewAudioEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)


struct AltSettingInfo {
  int interface_num;
  int altsetting;
  uint8_t ep_out;
  int max_packet_size;
  int subframe_size;
};

struct RefusedTrackInfo {
  std::string filename;
  int bitDepth;
  int sampleRate;
  std::string reason; // "format_incompatible" or "negotiation_failed"
};

static std::string json_escape(const std::string& s) {
  std::string result;
  result.reserve(s.size() + 8);
  for (char c : s) {
    switch (c) {
      case '"':  result += "\\\""; break;
      case '\\': result += "\\\\"; break;
      case '\b': result += "\\b";  break;
      case '\f': result += "\\f";  break;
      case '\n': result += "\\n";  break;
      case '\r': result += "\\r";  break;
      case '\t': result += "\\t";  break;
      default:
        if (static_cast<unsigned char>(c) < 0x20) {
          char buf[8];
          snprintf(buf, sizeof(buf), "\\u%04x", (unsigned char)c);
          result += buf;
        } else {
          result += c;
        }
    }
  }
  return result;
}

struct AudioEngineState {
  libusb_context *usbContext = nullptr;
  libusb_device_handle *usbHandle = nullptr;
  int usbAudioInterface = -1;
  uint8_t epAddress = 0;
  int maxPacketSize = 0;

  std::vector<AltSettingInfo> validAlts;

  // RAM Playback Buffer (Raw PCM Int32 for UAC1/UAC2)
  std::vector<int32_t> pcmBuffer;
  std::atomic<size_t> pcmIndex{0};
  std::atomic<uint32_t> channels{0};
  std::atomic<uint32_t> sampleRate{0};
  std::atomic<int> subframeSize{0}; // 2=16-bit, 3=24-bit, 4=32-bit
  std::atomic<int> activeIsoTransfers{0};

  // Gapless pre-loading buffers
  std::vector<int32_t> pcmBufferNext;
  std::atomic<uint32_t> channelsNext{2};
  std::atomic<uint32_t> sampleRateNext{48000};
  std::atomic<bool> hasNextTrack{false};
  
  std::string currentFilePath;
  std::string nextFilePath;

  // Garbage buffers
  std::vector<int32_t> pcmBufferGarbage;

  jobject callbackObj = nullptr;
  std::atomic<bool> isPlaying{false};
  std::atomic<bool> isFinished{false};
  std::atomic<bool> isSwapping{false};
  std::atomic<bool> isSwappingAck{false};
  double phase_accumulator = 0.0;
  std::atomic<float> targetVolume{1.0f};
  float rawLinearVolume = 1.0f;
  

  std::atomic<uint32_t> prepareNextGen{0};
  
  float currentVolume = 1.0f; // Only accessed by isoThread
  libusb_hotplug_callback_handle hotplugHandle = 0;

  std::atomic<size_t> decodedFrames{0};
  std::atomic<bool> isDecoding{false};
  std::atomic<bool> cancelDecoding{false};
  std::thread *decodeThread = nullptr;

  // ISO Thread
  std::thread *isoThread = nullptr;
  std::atomic<bool> stopIsoThread{false};
  std::atomic<bool> isoThreadExited{true};
  std::vector<libusb_transfer *> transfers;
  std::vector<struct libusb_transfer *> orphanTransfers;

  // Control Thread for non-blocking UAC requests
  std::thread *controlThread = nullptr;
  std::atomic<bool> stopControlThread{false};

  // DAC Metadata
  std::string dacProductName;
  std::string dacManufacturerName;
  std::atomic<int> dacVid{0};
  std::atomic<int> dacPid{0};

  // Hardware Volume State
  std::atomic<bool> isHardwareVolumeActive{false};
  std::atomic<bool> isForceSoftwareVolume{false};
  std::atomic<bool> deviceWedged{false};
  std::atomic<bool> sampleRateUnverified{false};
  std::atomic<uint32_t> lastKnownGoodSampleRate{48000};
  std::atomic<bool> hasValidatedRate{false};
  std::atomic<int> uacVersion{0}; // 1 or 2
  std::atomic<int> featureUnitId{-1};
  std::atomic<int> clockSourceId{-1};
  std::atomic<bool> clockSourceProgrammable{true};
  std::atomic<int> minVolumeDb{0}; // in 1/256 dB
  std::atomic<int> maxVolumeDb{0};
  std::atomic<int> volumeChannel{0};
  std::atomic<int> acInterfaceNum{0}; // 0 for master, 1 for L/R parallel
  std::vector<int> claimedInterfaces;
  std::atomic<int> consecutiveErrors{0};
  uint64_t lastErrorTimeMs = 0;

  std::atomic<int> sourceBitDepth{0};
  std::atomic<int> sourceBitDepthNext{0};

  // DAC Capabilities (populated once in initUsbDac)
  std::vector<int> supportedBitDepths;       // e.g. {16, 24}
  std::vector<uint32_t> supportedSampleRates; // discrete list
  bool sampleRateQuerySuccess = false;

  // Refused track history (max 5 entries)
  std::vector<RefusedTrackInfo> refusedTrackHistory;
  std::mutex refusedHistoryMutex;
};

std::mutex g_stateMutex;

struct ControlRequest {
  int type; // 1 = SET_CUR Volume, 2 = SET_CUR Sample Rate, 3 = SET_INTERFACE
  float volume;
  int sampleRate;
  int interfaceNum;
  int altSetting;
};

std::queue<ControlRequest> g_controlQueue;
std::mutex g_controlMutex;
std::condition_variable g_controlCv;

static AudioEngineState g_audioState;



static void control_thread_func() {
  while (!g_audioState.stopControlThread.load()) {
    ControlRequest req;
    {
      std::unique_lock<std::mutex> lock(g_controlMutex);
      g_controlCv.wait(lock, [] {
        return !g_controlQueue.empty() || g_audioState.stopControlThread.load();
      });

      if (g_audioState.stopControlThread.load()) {
        // Drop all remaining requests and exit immediately
        while (!g_controlQueue.empty())
          g_controlQueue.pop();
        break;
      }

      req = g_controlQueue.front();
      g_controlQueue.pop();
    }

    if (g_audioState.usbHandle == nullptr)
      continue;

    if (req.type == 1) { // SET_CUR Volume
      int target_db;
      if (req.volume <= 0.01f) {
        target_db = g_audioState.minVolumeDb;
      } else {
        // req.volume is linear amplitude (0.01 to 1.0). Convert to decibels.
        float db = 20.0f * std::log10(req.volume);
        target_db = g_audioState.maxVolumeDb + (int)(db * 256.0f);
        if (target_db < g_audioState.minVolumeDb)
          target_db = g_audioState.minVolumeDb;
      }
      if (target_db > g_audioState.maxVolumeDb)
        target_db = g_audioState.maxVolumeDb;

      uint16_t vol_data = (uint16_t)target_db;
      unsigned char *raw_buf = (unsigned char *)&vol_data;
      LOGI("USBExclusive: [HEX DUMP] Sending SET_CUR Volume | req.vol=%.3f, "
           "target_db_dec=%d | Raw Byte Array (Little Endian): [%02X %02X]",
           req.volume, target_db, raw_buf[0], raw_buf[1]);

      int r = -1;
      int retries = 3;
      while (retries >= 0) {
        if (g_audioState.uacVersion == 1 || g_audioState.uacVersion == 2) {
          if (g_audioState.volumeChannel == 0) {
            r = libusb_control_transfer(
                g_audioState.usbHandle, 0x21, 0x01, (0x02 << 8) | 0,
                (g_audioState.featureUnitId << 8) | g_audioState.acInterfaceNum,
                raw_buf, 2, 100);
          } else {
            r = libusb_control_transfer(
                g_audioState.usbHandle, 0x21, 0x01, (0x02 << 8) | 1,
                (g_audioState.featureUnitId << 8) | g_audioState.acInterfaceNum,
                raw_buf, 2, 100);
            libusb_control_transfer(
                g_audioState.usbHandle, 0x21, 0x01, (0x02 << 8) | 2,
                (g_audioState.featureUnitId << 8) | g_audioState.acInterfaceNum,
                raw_buf, 2, 100);
          }
        }
        if (r >= 0)
          break;
        retries--;
        if (retries >= 0)
          std::this_thread::sleep_for(std::chrono::milliseconds(10));
      }
      if (r < 0) {
        LOGE(
            "Hardware volume SET_CUR failed! Falling back to software volume.");
        g_audioState.isHardwareVolumeActive.store(false);
        // Fallback: apply the software volume immediately
        float vol = g_audioState.rawLinearVolume;
        float scaled_vol = vol;
        if (vol < 0.001f) {
          scaled_vol = 0.0f;
        } else {
          scaled_vol = (std::exp(vol * 4.0f) - 1.0f) / (std::exp(4.0f) - 1.0f);
        }
        g_audioState.targetVolume.store(scaled_vol);
      }
    } else if (req.type == 2) { // SET_CUR Sample Rate
      unsigned char data[4] = {0};
      if (g_audioState.uacVersion == 1) {
        data[0] = req.sampleRate & 0xFF;
        data[1] = (req.sampleRate >> 8) & 0xFF;
        data[2] = (req.sampleRate >> 16) & 0xFF;
        libusb_control_transfer(g_audioState.usbHandle, 0x22, 0x01, (0x01 << 8),
                                g_audioState.epAddress, data, 3, 100);
      } else if (g_audioState.uacVersion == 2 &&
                 g_audioState.clockSourceId != -1) {
        data[0] = req.sampleRate & 0xFF;
        data[1] = (req.sampleRate >> 8) & 0xFF;
        data[2] = (req.sampleRate >> 16) & 0xFF;
        data[3] = (req.sampleRate >> 24) & 0xFF;
        libusb_control_transfer(g_audioState.usbHandle, 0x21, 0x01, (0x01 << 8),
                                (g_audioState.clockSourceId << 8) |
                                    g_audioState.acInterfaceNum,
                                data, 4, 100);
      }
    } else if (req.type == 3) { // SET_INTERFACE
      libusb_set_interface_alt_setting(g_audioState.usbHandle, req.interfaceNum,
                                       req.altSetting);
    }
  }
}

static void LIBUSB_CALL iso_callback(struct libusb_transfer *transfer) {
  if (g_audioState.stopIsoThread.load() ||
      transfer->status == LIBUSB_TRANSFER_CANCELLED ||
      transfer->status == LIBUSB_TRANSFER_NO_DEVICE) {
    g_audioState.activeIsoTransfers.fetch_sub(1);
    return;
  }

  // PHASE 3: Differentiate recovery vs fatal errors
  if (transfer->status == LIBUSB_TRANSFER_ERROR ||
      transfer->status == LIBUSB_TRANSFER_TIMED_OUT ||
      transfer->status == LIBUSB_TRANSFER_STALL ||
      transfer->status == LIBUSB_TRANSFER_OVERFLOW) {

    uint64_t now = get_time_ms();
    if (now - g_audioState.lastErrorTimeMs > 100) {
      g_audioState.consecutiveErrors.store(
          0); // Reset window if older than 100ms
    }
    g_audioState.lastErrorTimeMs = now;

    int err_count = g_audioState.consecutiveErrors.fetch_add(1) + 1;
    if (err_count > 50) {
      LOGE("USBExclusive: CRITICAL THRESHOLD REACHED (>50 errors in 100ms)! "
           "Aborting transfer and escalating to full re-init. Status: %d",
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

    // Log warning for temporary glitch
    LOGW("USBExclusive: Transient USB error %d (count=%d/50). Recovering...",
         transfer->status, err_count);
    // We do NOT clear_halt for Isochronous endpoints (invalid operation per USB
    // spec). Just let it fall through and resubmit the buffer filled with
    // silence/zeros.
  } else {
    // Success
    g_audioState.consecutiveErrors.store(0);
  }

  int num_packets = transfer->num_iso_packets;
  uint8_t *buffer = transfer->buffer;

  int speed =
      libusb_get_device_speed(libusb_get_device(g_audioState.usbHandle));
  int usb_frames_per_sec =
      (speed == LIBUSB_SPEED_HIGH || speed == LIBUSB_SPEED_SUPER) ? 8000 : 1000;
  double frames_per_packet =
      (double)g_audioState.sampleRate / usb_frames_per_sec;

  if (g_audioState.isSwapping.load()) {
    g_audioState.isSwappingAck.store(true);
    int data_offset = 0;
    for (int i = 0; i < num_packets; i++) {
      g_audioState.phase_accumulator += frames_per_packet;
      int audio_frames_to_send = (int)g_audioState.phase_accumulator;
      g_audioState.phase_accumulator -= audio_frames_to_send;
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
    g_audioState.phase_accumulator += frames_per_packet;
    int audio_frames_to_send = (int)g_audioState.phase_accumulator;
    g_audioState.phase_accumulator -= audio_frames_to_send;

    int bytes_to_send = audio_frames_to_send * g_audioState.channels.load() *
                        g_audioState.subframeSize.load();
    if (bytes_to_send > g_audioState.maxPacketSize) {
      bytes_to_send = g_audioState.maxPacketSize;
    }

    int bytes_filled = 0;

    while (bytes_filled < bytes_to_send && g_audioState.isPlaying.load() &&
           !g_audioState.pcmBuffer.empty()) {
      size_t currentIndex = g_audioState.pcmIndex.load();
      size_t totalFrames = g_audioState.decodedFrames.load();
      size_t src_bytes_per_frame =
          g_audioState.channels.load() *
          sizeof(int32_t); // PCM buffer is 32-bit (left-justified)
      size_t dst_bytes_per_frame =
          g_audioState.channels.load() *
          g_audioState.subframeSize.load(); // USB packet size

      size_t bytes_needed = bytes_to_send - bytes_filled;
      size_t frames_needed =
          bytes_needed / dst_bytes_per_frame; // CRITICAL FIX: Calculate frames
                                              // based on OUTGOING byte size!
      size_t frames_avail =
          (totalFrames > currentIndex) ? (totalFrames - currentIndex) : 0;
      size_t frames_to_read = std::min(frames_needed, frames_avail);

      if (frames_to_read > 0) {
        float target_vol = g_audioState.targetVolume.load();

        float delta = 0.0f;
        if (std::abs(target_vol - g_audioState.currentVolume) > 0.0001f) {
          delta = (target_vol - g_audioState.currentVolume) / 220.0f;
        }

        int32_t *src = (int32_t *)((uint8_t *)g_audioState.pcmBuffer.data() +
                                   (currentIndex * src_bytes_per_frame));
        uint8_t *dst_bytes = (uint8_t *)(buffer + data_offset + bytes_filled);
        size_t num_samples = frames_to_read * g_audioState.channels.load();

        int sf_size = g_audioState.subframeSize.load();

        // TRUE BIT-PERFECT BYPASS (UNITY GAIN / HARDWARE VOLUME ACTIVE)
        // Completely bypasses float casting, multipliers, and dithering when at unity gain.
        if (g_audioState.currentVolume >= 0.999f && delta == 0.0f) {
          if (sf_size == 4) {
            // Direct 1:1 memory copy: decoded 32-bit PCM straight into 32-bit USB subslot
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
          // Software attenuation path with volume ramping and dynamic TPDF dithering
          for (size_t s = 0; s < num_samples; s++) {
            if (delta != 0.0f) {
              g_audioState.currentVolume += delta;
              if ((delta > 0 && g_audioState.currentVolume > target_vol) ||
                  (delta < 0 && g_audioState.currentVolume < target_vol)) {
                g_audioState.currentVolume = target_vol;
                delta = 0.0f;
              }
            }

            // src[s] is a 32-bit left-justified sample.
            float scaled = (float)src[s] * g_audioState.currentVolume;

            if (g_audioState.currentVolume < 0.999f) {
              // Dynamic TPDF Dither amplitude based on target bit depth (sf_size)
              // 16-bit: LSB in 32-bit space is 65536. Half LSB = 32768.0f
              // 24-bit: LSB in 32-bit space is 256. Half LSB = 128.0f
              // 32-bit: LSB in 32-bit space is 1. Half LSB = 0.5f
              float half_lsb = 128.0f;
              if (sf_size == 2)
                half_lsb = 32768.0f;
              else if (sf_size == 4)
                half_lsb = 0.5f;

              float randA = fast_uniform_rand() * half_lsb;
              float randB = fast_uniform_rand() * half_lsb;
              scaled += (randA - randB);
            }

            if (scaled > 2147483647.0f)
              scaled = 2147483647.0f;
            if (scaled < -2147483648.0f)
              scaled = -2147483648.0f;

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
          g_audioState.sourceBitDepth.store(
              g_audioState.sourceBitDepthNext.load());
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
      memset(buffer + data_offset + bytes_filled, 0,
             bytes_to_send - bytes_filled);
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
        g_audioState.activeIsoTransfers.fetch_sub(
            1); // Decrement because it failed to resubmit
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
    g_audioState.activeIsoTransfers.fetch_sub(
        1); // Decrement because we chose not to resubmit
  }
}

int get_subframe_size(const struct libusb_interface_descriptor *alt) {
  auto parse_extra = [](const unsigned char *extra, int extra_length) -> int {
    int pos = 0;
    while (pos + 2 < extra_length) {
      int len = extra[pos];
      if (len < 3 || pos + len > extra_length)
        break;
      int type = extra[pos + 1];
      int subtype = extra[pos + 2];
      if (type == 0x24 && subtype == 0x02) { // FORMAT_TYPE
        if (pos + 3 < extra_length) {
          int format_type = extra[pos + 3];
          if (format_type == 1) { // Type I
            if (len == 6 && pos + 4 < extra_length)
              return extra[pos + 4];
            if (len >= 8 && pos + 5 < extra_length)
              return extra[pos + 5];
          }
        }
      }
      pos += len;
    }
    return -1;
  };

  // Try interface-level extra first
  int sf = parse_extra(alt->extra, alt->extra_length);
  if (sf != -1)
    return sf;

  // Fallback: UAC Spec allows FORMAT_TYPE in class-specific AS endpoint
  // descriptor
  for (int k = 0; k < alt->bNumEndpoints; k++) {
    sf = parse_extra(alt->endpoint[k].extra, alt->endpoint[k].extra_length);
    if (sf != -1)
      return sf;
  }

  return 2; // default to 16-bit
}

// Parse UAC1 FORMAT_TYPE descriptor for discrete sample rates (tSamFreq array)
static std::vector<uint32_t> get_uac1_sample_rates(const struct libusb_interface_descriptor *alt) {
  std::vector<uint32_t> rates;
  auto parse_extra = [&rates](const unsigned char *extra, int extra_length) {
    int pos = 0;
    while (pos + 2 < extra_length) {
      int len = extra[pos];
      if (len < 3 || pos + len > extra_length) break;
      int type = extra[pos + 1];
      int subtype = extra[pos + 2];
      if (type == 0x24 && subtype == 0x02) { // FORMAT_TYPE
        if (pos + 3 < extra_length) {
          int format_type = extra[pos + 3];
          if (format_type == 1) { // Type I
            // UAC1 FORMAT_TYPE I: bSamFreqType is at offset 7
            // If bSamFreqType > 0: discrete frequencies follow at offset 8, each 3 bytes LE
            // If bSamFreqType == 0: continuous range (min 3 bytes, max 3 bytes) at offset 8
            int sam_freq_type_offset = pos + 7;
            if (sam_freq_type_offset < pos + len) {
              int bSamFreqType = extra[sam_freq_type_offset];
              int freq_data_offset = pos + 8;
              if (bSamFreqType > 0) {
                // Discrete frequencies
                for (int f = 0; f < bSamFreqType; f++) {
                  int foff = freq_data_offset + f * 3;
                  if (foff + 2 < pos + len) {
                    uint32_t freq = extra[foff] | (extra[foff + 1] << 8) | (extra[foff + 2] << 16);
                    if (freq > 0) rates.push_back(freq);
                  }
                }
              } else {
                // Continuous range: min (3 bytes) + max (3 bytes)
                if (freq_data_offset + 5 < pos + len) {
                  uint32_t min_freq = extra[freq_data_offset] |
                                     (extra[freq_data_offset + 1] << 8) |
                                     (extra[freq_data_offset + 2] << 16);
                  uint32_t max_freq = extra[freq_data_offset + 3] |
                                     (extra[freq_data_offset + 4] << 8) |
                                     (extra[freq_data_offset + 5] << 16);
                  // Expand with common rates in range
                  static const uint32_t common_rates[] = {
                    8000, 11025, 16000, 22050, 32000, 44100, 48000,
                    88200, 96000, 176400, 192000, 352800, 384000
                  };
                  for (uint32_t cr : common_rates) {
                    if (cr >= min_freq && cr <= max_freq) rates.push_back(cr);
                  }
                }
              }
            }
          }
        }
      }
      pos += len;
    }
  };

  parse_extra(alt->extra, alt->extra_length);
  for (int k = 0; k < alt->bNumEndpoints; k++) {
    parse_extra(alt->endpoint[k].extra, alt->endpoint[k].extra_length);
  }
  return rates;
}

// Cleanup helper for negotiation failure in playAudio (prevents dirty state on return -3)
static void playAudio_cleanup_on_negotiation_failure() {
  if (g_audioState.decodeThread != nullptr) {
    g_audioState.cancelDecoding.store(true);
    if (g_audioState.decodeThread->joinable())
      g_audioState.decodeThread->join();
    delete g_audioState.decodeThread;
    g_audioState.decodeThread = nullptr;
  }
  if (!g_audioState.pcmBuffer.empty()) {
    munlock(g_audioState.pcmBuffer.data(),
            g_audioState.pcmBuffer.size() * sizeof(int32_t));
    g_audioState.pcmBuffer.clear();
  }
  g_audioState.currentFilePath = "";
  g_audioState.isPlaying.store(false);
  g_audioState.isSwapping.store(false);
}

static int LIBUSB_CALL hotplug_callback(libusb_context *ctx,
                                        libusb_device *device,
                                        libusb_hotplug_event event,
                                        void *user_data) {
  if (event == LIBUSB_HOTPLUG_EVENT_DEVICE_LEFT) {
    if (g_audioState.usbHandle != nullptr) {
      libusb_device* current_dev = libusb_get_device(g_audioState.usbHandle);
      if (current_dev != device) {
        LOGW("Ignored stale hotplug DEVICE_LEFT event for old device!");
        return 0;
      }
    }
    LOGE("USB Device Disconnected (Surprise Removal)!");
    g_audioState.stopIsoThread.store(true);
    std::thread([]() {
      if (g_jvm && g_audioState.callbackObj) {
        JNIEnv *env;
        if (g_jvm->AttachCurrentThread(&env, NULL) == 0) {
          jclass clazz = env->GetObjectClass(g_audioState.callbackObj);
          jmethodID methodId =
              env->GetMethodID(clazz, "onDeviceForceDisconnected", "()V");
          if (methodId)
            env->CallVoidMethod(g_audioState.callbackObj, methodId);
          g_jvm->DetachCurrentThread();
        }
      }
    }).detach();
  }
  return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_closeUsbDac(JNIEnv *env,
                                                        jobject thiz) {
  // RUN SYNCHRONOUSLY to ensure libusb_close finishes BEFORE Kotlin closes the OS FD.
  // This guarantees no zombie transfers or UAF.
  ApiMutexLock lock(__func__);
  if (g_audioState.usbHandle != nullptr) {
    g_audioState.stopIsoThread.store(true);

    if (g_audioState.isoThread != nullptr &&
        g_audioState.isoThread->joinable()) {
      LOGI("[Thread] Waiting for isoThread from closeUsbDac...");
      bool exited = false;
      for (int i = 0; i < 200; i++) { // 2 second timeout
        if (g_audioState.isoThreadExited.load()) { exited = true; break; }
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
      }
      if (!exited) {
        LOGE("FATAL: isoThread did not exit! Detaching to prevent ANR.");
        g_audioState.deviceWedged.store(true);
        g_audioState.isoThread->detach();
      } else {
        g_audioState.isoThread->join();
        LOGI("[Thread] Joined isoThread successfully");
      }
      delete g_audioState.isoThread;
      g_audioState.isoThread = nullptr;
    }
    for (int iface : g_audioState.claimedInterfaces) {
      int rr = libusb_release_interface(g_audioState.usbHandle, iface);
      if (rr != 0)
        LOGE("libusb_release_interface error: %s", libusb_error_name(rr));
    }
    g_audioState.claimedInterfaces.clear();
    libusb_close(g_audioState.usbHandle);
    g_audioState.usbHandle = nullptr;
    
    // Reset state to force hardware re-initialization on next playAudio
    g_audioState.subframeSize.store(0);
    g_audioState.sampleRate.store(0);
    g_audioState.channels.store(0);
    g_audioState.hasValidatedRate.store(false);
    
    LOGI("[Thread] Cleanup finished successfully in closeUsbDac");
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_initUsbDac(JNIEnv *env,
                                                       jobject thiz, jint fd) {
  ApiMutexLock lock(__func__);
  if (g_audioState.usbHandle != nullptr) {
    LOGI("initUsbDac: USB DAC is already initialized and active. Preserving existing connection.");
    return JNI_TRUE;
  }

  // Clean up orphan transfers now that the USB device was physically detached and kernel state is wiped
  if (!g_audioState.orphanTransfers.empty()) {
    LOGI("Cleaning up %zu orphan transfers from previous deadlocks.", g_audioState.orphanTransfers.size());
    for (auto t : g_audioState.orphanTransfers) {
      if (t) {
        delete[] t->buffer;
        libusb_free_transfer(t);
      }
    }
    g_audioState.orphanTransfers.clear();
  }

  if (g_audioState.usbContext == nullptr) {
    if (libusb_init(&g_audioState.usbContext) < 0)
      return JNI_FALSE;

    if (libusb_has_capability(LIBUSB_CAP_HAS_HOTPLUG)) {
      libusb_hotplug_register_callback(
          g_audioState.usbContext, LIBUSB_HOTPLUG_EVENT_DEVICE_LEFT,
          LIBUSB_HOTPLUG_NO_FLAGS, LIBUSB_HOTPLUG_MATCH_ANY,
          LIBUSB_HOTPLUG_MATCH_ANY, LIBUSB_HOTPLUG_MATCH_ANY, hotplug_callback,
          nullptr, &g_audioState.hotplugHandle);
    }
  }

  if (libusb_wrap_sys_device(g_audioState.usbContext, (intptr_t)fd,
                             &g_audioState.usbHandle) < 0) {
    return JNI_FALSE;
  }

  libusb_device *dev = libusb_get_device(g_audioState.usbHandle);
  struct libusb_config_descriptor *config;
  if (libusb_get_active_config_descriptor(dev, &config) < 0)
    return JNI_FALSE;

  // --- DUMP DESCRIPTOR LOGGING ---
  LOGI("=== START DESCRIPTOR DUMP ===");
  for (int i = 0; i < config->bNumInterfaces; i++) {
    for (int j = 0; j < config->interface[i].num_altsetting; j++) {
      const struct libusb_interface_descriptor *alt =
          &config->interface[i].altsetting[j];
      LOGI("DUMP: Interface %d, AltSetting %d | Class %d, SubClass %d, "
           "Protocol %d",
           alt->bInterfaceNumber, alt->bAlternateSetting, alt->bInterfaceClass,
           alt->bInterfaceSubClass, alt->bInterfaceProtocol);
    }
  }
  LOGI("=== END DESCRIPTOR DUMP ===");

  // Bagian 2.1 - 2.4: Parse Audio Control Interface
  g_audioState.uacVersion = 1;
  g_audioState.featureUnitId = -1;
  g_audioState.sourceBitDepthNext.store(0);
  g_audioState.isHardwareVolumeActive.store(false);
  g_audioState.deviceWedged.store(false);
  g_audioState.sampleRateUnverified.store(false);
  g_audioState.volumeChannel = 0;

  for (int i = 0; i < config->bNumInterfaces; i++) {
    for (int j = 0; j < config->interface[i].num_altsetting; j++) {
      const struct libusb_interface_descriptor *alt =
          &config->interface[i].altsetting[j];
      if (alt->bInterfaceClass == 1 &&
          alt->bInterfaceSubClass == 1) { // AUDIO CONTROL
        g_audioState.acInterfaceNum = alt->bInterfaceNumber;
        int pos = 0;
        while (pos + 2 < alt->extra_length) {
          int len = alt->extra[pos];
          if (len < 3 || pos + len > alt->extra_length)
            break;
          int subtype = alt->extra[pos + 2];

          if (subtype == 0x01) { // HEADER
            if (len >= 6) {
              g_audioState.uacVersion.store(
                  (alt->extra[pos + 3] == 0x00 && alt->extra[pos + 4] == 0x02)
                      ? 2
                      : 1);
              LOGI("RAW HEADER DUMP: [pos+0]=0x%02X [pos+1]=0x%02X "
                   "[pos+2]=0x%02X [pos+3]=0x%02X [pos+4]=0x%02X "
                   "[pos+5]=0x%02X [pos+6]=0x%02X",
                   alt->extra[pos], alt->extra[pos + 1], alt->extra[pos + 2],
                   alt->extra[pos + 3], alt->extra[pos + 4],
                   alt->extra[pos + 5], alt->extra[pos + 6]);
            }
          } else if (subtype == 0x06) { // FEATURE_UNIT
            g_audioState.featureUnitId.store(alt->extra[pos + 3]);
            int bmaPos =
                (g_audioState.uacVersion.load() == 1) ? pos + 6 : pos + 5;
            if (bmaPos < pos + len) {
              uint8_t masterControls = alt->extra[bmaPos];
              bool hasMasterVol = false;
              if (g_audioState.uacVersion.load() == 1 &&
                  (masterControls & 0x02))
                hasMasterVol = true;
              if (g_audioState.uacVersion.load() == 2 &&
                  (masterControls & 0x0C))
                hasMasterVol = true;

              g_audioState.volumeChannel.store(hasMasterVol ? 0 : 1);
            }
          } else if (subtype == 0x0A &&
                     g_audioState.uacVersion.load() == 2) { // CLOCK_SOURCE
            uint8_t clockId = alt->extra[pos + 3];
            uint8_t bmControls =
                (pos + 7 <= alt->extra_length) ? alt->extra[pos + 5] : 0;
            LOGI("USBExclusive: Found CLOCK_SOURCE ID=%d, bmControls=0x%02X",
                 clockId, bmControls);

            g_audioState.clockSourceId.store(clockId);
            g_audioState.clockSourceProgrammable.store((bmControls & 0x03) ==
                                                       0x03);
          } else if (subtype == 0x0B &&
                     g_audioState.uacVersion.load() == 2) { // CLOCK_SELECTOR
            uint8_t clockSelId = alt->extra[pos + 3];
            uint8_t numPins = alt->extra[pos + 4];
            LOGI("USBExclusive: Found CLOCK_SELECTOR ID=%d with %d input pins",
                 clockSelId, numPins);
            for (int p = 0; p < numPins; p++) {
              LOGI("USBExclusive:   Pin %d -> Clock ID %d", p + 1,
                   alt->extra[pos + 5 + p]);
            }
          } else if (subtype == 0x0B && g_audioState.uacVersion == 1) {
            // UAC1 has no clock selectors, but just in case
          }
          pos += len;
        }
      }
    }
  }

  // Start control thread
  if (g_audioState.controlThread != nullptr &&
      g_audioState.controlThread->joinable()) {
    g_audioState.stopControlThread.store(true);
    g_controlCv.notify_all();
    g_audioState.controlThread->join();
    delete g_audioState.controlThread;
  }
  g_audioState.stopControlThread.store(false);
  while (!g_controlQueue.empty())
    g_controlQueue.pop(); // Clear queue
  g_audioState.controlThread = new std::thread(control_thread_func);

  int as_interface = -1;
  int as_altsetting = -1;
  uint8_t ep_out = 0;
  int max_packet_size = 0;

  g_audioState.validAlts.clear();
  g_audioState.supportedBitDepths.clear();
  g_audioState.supportedSampleRates.clear();
  g_audioState.sampleRateQuerySuccess = false;
  {
    std::lock_guard<std::mutex> lock(g_audioState.refusedHistoryMutex);
    g_audioState.refusedTrackHistory.clear(); // New DAC session
  }

  std::set<int> bitDepthSet;
  std::set<uint32_t> sampleRateSet;

  for (int i = 0; i < config->bNumInterfaces; i++) {
    for (int j = 0; j < config->interface[i].num_altsetting; j++) {
      const struct libusb_interface_descriptor *alt =
          &config->interface[i].altsetting[j];
      if (alt->bInterfaceClass == 1 && alt->bInterfaceSubClass == 2) {
        int subframe_size = get_subframe_size(alt);
        bitDepthSet.insert(subframe_size * 8);

        // For UAC1: extract sample rates from FORMAT_TYPE descriptor
        if (g_audioState.uacVersion.load() == 1) {
          auto rates = get_uac1_sample_rates(alt);
          for (auto r : rates) sampleRateSet.insert(r);
        }

        for (int k = 0; k < alt->bNumEndpoints; k++) {
          if ((alt->endpoint[k].bEndpointAddress & 0x80) == 0 &&
              (alt->endpoint[k].bmAttributes & 0x03) == 1) {
            int pk_size = libusb_get_max_iso_packet_size(
                dev, alt->endpoint[k].bEndpointAddress);
            if (pk_size < 0)
              pk_size = alt->endpoint[k].wMaxPacketSize;
            g_audioState.validAlts.push_back(
                {alt->bInterfaceNumber, alt->bAlternateSetting,
                 alt->endpoint[k].bEndpointAddress, pk_size, subframe_size});
          }
        }
      }
    }
  }

  // Store supported bit depths
  g_audioState.supportedBitDepths.assign(bitDepthSet.begin(), bitDepthSet.end());
  LOGI("DAC Capabilities: Supported bit depths: ");
  for (int bd : g_audioState.supportedBitDepths) {
    LOGI("  %d-Bit", bd);
  }



  if (!g_audioState.validAlts.empty()) {
    int source_bytes = g_audioState.sourceBitDepth.load() / 8;
    if (source_bytes == 0)
      source_bytes = 2; // Default to 16-bit jika belum ada lagu yang ter-load

    AltSettingInfo best_alt = g_audioState.validAlts[0];
    bool found_exact = false;

    for (const auto &a : g_audioState.validAlts) {
      if (a.subframe_size == source_bytes) {
        best_alt = a;
        found_exact = true;
        break;
      }
    }

    LOGI(
        "Selected altsetting: iface=%d alt=%d sf_size=%d (source expected: %d)",
        best_alt.interface_num, best_alt.altsetting, best_alt.subframe_size,
        source_bytes);
    as_interface = best_alt.interface_num;
    as_altsetting = best_alt.altsetting;
    ep_out = best_alt.ep_out;
    max_packet_size = best_alt.max_packet_size;
  }
  if (as_interface == -1 || ep_out == 0) {
    libusb_free_config_descriptor(config);
    return JNI_FALSE;
  }

  libusb_set_auto_detach_kernel_driver(g_audioState.usbHandle, 1);
  g_audioState.claimedInterfaces.clear();

  // PHASE 2: Claim ALL audio interfaces (Class 1) to prevent OS interruption
  // We intentionally SKIP Class 3 (HID) to keep physical buttons working on Android.
  // For each interface, we explicitly try to detach the kernel driver first,
  // then claim. This is needed because auto_detach often fails silently on
  // Android's Audio Control interface (error -6 BUSY).
  for (int i = 0; i < config->bNumInterfaces; i++) {
    for (int j = 0; j < config->interface[i].num_altsetting; j++) {
      const struct libusb_interface_descriptor *alt =
          &config->interface[i].altsetting[j];
      if (alt->bInterfaceClass == 1) {
        int iface_num = alt->bInterfaceNumber;
        if (std::find(g_audioState.claimedInterfaces.begin(),
                      g_audioState.claimedInterfaces.end(),
                      iface_num) == g_audioState.claimedInterfaces.end()) {
          // Step 1: Explicitly try to detach kernel driver
          int detach_res = libusb_detach_kernel_driver(g_audioState.usbHandle, iface_num);
          if (detach_res == 0) {
            LOGI("USBExclusive: Detached kernel driver from Interface %d", iface_num);
          } else if (detach_res == LIBUSB_ERROR_NOT_FOUND) {
            LOGI("USBExclusive: No kernel driver on Interface %d (already free)", iface_num);
          } else {
            LOGW("USBExclusive: Could not detach kernel driver from Interface %d, error %d (%s)",
                 iface_num, detach_res, libusb_error_name(detach_res));
          }

          // Step 2: Claim the interface
          int claim_res =
              libusb_claim_interface(g_audioState.usbHandle, iface_num);
          if (claim_res == 0) {
            std::lock_guard<std::mutex> lock(g_stateMutex);
            g_audioState.claimedInterfaces.push_back(iface_num);
            LOGI("USBExclusive: Successfully claimed Interface %d", iface_num);
          } else {
            LOGW("USBExclusive: Failed to claim Interface %d, error %d (%s). "
                 "Continuing — this interface is not required for basic playback.",
                 iface_num, claim_res, libusb_error_name(claim_res));
          }
        }
      }
    }
  }

  // PHASE 3: Query Sample Rates (MUST be done AFTER claiming Audio Control interface)
  // For UAC2: query sample rate range from clock source via GET_RANGE
  if (g_audioState.uacVersion.load() == 2 && g_audioState.clockSourceId != -1) {
    unsigned char range_buf[256] = {0};
    int r = -1;
    for (int retry = 0; retry < 3; retry++) {
      r = libusb_control_transfer(g_audioState.usbHandle, 0xA1, 0x02,
                                  (0x01 << 8), // SAM_FREQ_CONTROL
                                  (g_audioState.clockSourceId << 8) |
                                      g_audioState.acInterfaceNum,
                                  range_buf, sizeof(range_buf), 1000);
      if (r >= 2) break;
      LOGW("UAC2 RANGE query failed (Code %d). Retrying in 200ms...", r);
      std::this_thread::sleep_for(std::chrono::milliseconds(200));
    }
    if (r >= 2) {
      int num_subranges = range_buf[0] | (range_buf[1] << 8);
      LOGI("DAC Capabilities: UAC2 Clock Source RANGE: %d subranges", num_subranges);
      for (int s = 0; s < num_subranges; s++) {
        int offset = 2 + (s * 12); // Each subrange: dMIN(4) + dMAX(4) + dRES(4)
        if (offset + 11 < r) {
          uint32_t sr_min = range_buf[offset] | (range_buf[offset+1] << 8) |
                           (range_buf[offset+2] << 16) | (range_buf[offset+3] << 24);
          uint32_t sr_max = range_buf[offset+4] | (range_buf[offset+5] << 8) |
                           (range_buf[offset+6] << 16) | (range_buf[offset+7] << 24);
          uint32_t sr_res = range_buf[offset+8] | (range_buf[offset+9] << 8) |
                           (range_buf[offset+10] << 16) | (range_buf[offset+11] << 24);
          LOGI("  Subrange %d: min=%u max=%u res=%u", s, sr_min, sr_max, sr_res);
          if (sr_res == 0 || sr_min == sr_max) {
            sampleRateSet.insert(sr_min);
          } else {
            for (uint32_t freq = sr_min; freq <= sr_max; freq += sr_res) {
              sampleRateSet.insert(freq);
            }
            sampleRateSet.insert(sr_max); // Ensure max is included
          }
        }
      }
      g_audioState.sampleRateQuerySuccess = true;
    } else {
      LOGE("DAC Capabilities: UAC2 Clock Source RANGE query FAILED! Code: %d", r);
    }
  } else if (g_audioState.uacVersion.load() == 1) {
    // UAC1 rates already collected from FORMAT_TYPE parsing above
    if (!sampleRateSet.empty()) {
      g_audioState.sampleRateQuerySuccess = true;
    } else {
      LOGW("DAC Capabilities: UAC1 FORMAT_TYPE had no sample rate data!");
    }
  }

  g_audioState.supportedSampleRates.assign(sampleRateSet.begin(), sampleRateSet.end());
  LOGI("DAC Capabilities: Supported sample rates (%zu total):", g_audioState.supportedSampleRates.size());
  for (uint32_t sr : g_audioState.supportedSampleRates) {
    LOGI("  %u Hz", sr);
  }

  libusb_free_config_descriptor(config); // Move free to AFTER the loop!

  // We only absolutely need the streaming interface to succeed
  if (std::find(g_audioState.claimedInterfaces.begin(),
                g_audioState.claimedInterfaces.end(),
                as_interface) == g_audioState.claimedInterfaces.end()) {
    LOGE("USBExclusive: Failed to claim streaming interface %d! Aborting.",
         as_interface);

    // FIX: Clean up properly to prevent crash in playAudio
    for (int iface : g_audioState.claimedInterfaces) {
      libusb_release_interface(g_audioState.usbHandle, iface);
    }
    g_audioState.claimedInterfaces.clear();

    libusb_close(g_audioState.usbHandle);
    g_audioState.usbHandle = nullptr;
    return JNI_FALSE;
  }

  if (libusb_set_interface_alt_setting(g_audioState.usbHandle, as_interface,
                                       as_altsetting) < 0)
    return JNI_FALSE;

  g_audioState.usbAudioInterface = as_interface;
  g_audioState.epAddress = ep_out;
  g_audioState.maxPacketSize = max_packet_size;

  // Bagian 2.5 - 2.7: Query Volume Range and Startup Handshake
  // FINAL CONCLUSION: Kinera Celest Ruyi firmware crashes when receiving ANY
  // SET_CUR volume command while streaming. We MUST force Software Volume.
  g_audioState.isForceSoftwareVolume.store(false);
  
  struct libusb_device_descriptor desc;
  if (libusb_get_device_descriptor(dev, &desc) == 0) {
    LOGI("USBExclusive: DAC Detected - VID: %04X, PID: %04X", desc.idVendor, desc.idProduct);
    
    {
      std::lock_guard<std::mutex> lock(g_stateMutex);
      g_audioState.dacVid.store(desc.idVendor);
      g_audioState.dacPid.store(desc.idProduct);
      g_audioState.dacProductName = "";
      g_audioState.dacManufacturerName = "";

      if (desc.iProduct > 0) {
        unsigned char string_buf[256];
        int string_res = libusb_get_string_descriptor_ascii(
            g_audioState.usbHandle, desc.iProduct, string_buf, sizeof(string_buf));
        if (string_res > 0) {
          g_audioState.dacProductName = std::string(reinterpret_cast<char*>(string_buf), string_res);
        }
      }

      if (desc.iManufacturer > 0) {
        unsigned char string_buf[256];
        int string_res = libusb_get_string_descriptor_ascii(
            g_audioState.usbHandle, desc.iManufacturer, string_buf, sizeof(string_buf));
        if (string_res > 0) {
          g_audioState.dacManufacturerName = std::string(reinterpret_cast<char*>(string_buf), string_res);
        }
      }
    }

    // Check product string for blacklisted devices
    if (desc.iProduct > 0) {
      unsigned char string_buf[256];
      int string_res = libusb_get_string_descriptor_ascii(
          g_audioState.usbHandle, desc.iProduct, string_buf, sizeof(string_buf));
      if (string_res > 0) {
        std::string productName(reinterpret_cast<char*>(string_buf), string_res);
        LOGI("USBExclusive: DAC Product Name: %s", productName.c_str());
        
        // Convert to lower case for safer matching
        std::string lowerProduct = productName;
        std::transform(lowerProduct.begin(), lowerProduct.end(), lowerProduct.begin(), ::tolower);
        
        // Blacklist check
        if (lowerProduct.find("celest") != std::string::npos || lowerProduct.find("ruyi") != std::string::npos) {
          LOGW("USBExclusive: Blacklisted buggy DAC detected (%s). Forcing Software Volume.", productName.c_str());
          g_audioState.isForceSoftwareVolume.store(true);
        }
      }
    }
  }
  bool acInterfaceClaimed = std::find(g_audioState.claimedInterfaces.begin(),
                                      g_audioState.claimedInterfaces.end(),
                                      g_audioState.acInterfaceNum.load()) !=
                            g_audioState.claimedInterfaces.end();
  if (!g_audioState.isForceSoftwareVolume.load() && g_audioState.featureUnitId != -1 && acInterfaceClaimed) {
    g_audioState.isHardwareVolumeActive.store(true);
    g_audioState.targetVolume.store(1.0f); // Force Bit-Perfect
    if (g_audioState.uacVersion == 1) {
      uint16_t min_vol = 0, max_vol = 0;
      libusb_control_transfer(g_audioState.usbHandle, 0xA1, 0x82,
                              (0x02 << 8) | g_audioState.volumeChannel,
                              (g_audioState.featureUnitId << 8) |
                                  g_audioState.acInterfaceNum,
                              (unsigned char *)&min_vol, 2, 1000);
      libusb_control_transfer(g_audioState.usbHandle, 0xA1, 0x83,
                              (0x02 << 8) | g_audioState.volumeChannel,
                              (g_audioState.featureUnitId << 8) |
                                  g_audioState.acInterfaceNum,
                              (unsigned char *)&max_vol, 2, 1000);
      g_audioState.minVolumeDb = (int16_t)min_vol;
      g_audioState.maxVolumeDb = (int16_t)max_vol;
    } else {
      // UAC2 RANGE request
      unsigned char range_buf[256] = {0};
      int r = libusb_control_transfer(g_audioState.usbHandle, 0xA1, 0x02,
                                      (0x02 << 8) | g_audioState.volumeChannel,
                                      (g_audioState.featureUnitId << 8) |
                                          g_audioState.acInterfaceNum,
                                      range_buf, sizeof(range_buf), 1000);
      if (r >= 2) {
        int num_subranges = range_buf[0] | (range_buf[1] << 8);
        LOGI("USBExclusive: UAC2 Volume RANGE Request successful, "
             "num_subranges=%d",
             num_subranges);
        int min_vol = 32767, max_vol = -32768;
        for (int s = 0; s < num_subranges; s++) {
          int offset = 2 + (s * 6);
          if (offset + 4 <= r) {
            int16_t sub_min = range_buf[offset] | (range_buf[offset + 1] << 8);
            int16_t sub_max =
                range_buf[offset + 2] | (range_buf[offset + 3] << 8);
            LOGI("USBExclusive: Subrange %d: Min=%d, Max=%d", s, sub_min,
                 sub_max);
            if (sub_min < min_vol)
              min_vol = sub_min;
            if (sub_max > max_vol)
              max_vol = sub_max;
          }
        }
        if (min_vol != 32767)
          g_audioState.minVolumeDb = min_vol;
        if (max_vol != -32768)
          g_audioState.maxVolumeDb = max_vol;
      } else {
        LOGE("USBExclusive: UAC2 Volume RANGE Request FAILED! Error code: %d",
             r);
      }

      // Fallback if bounds are identical or invalid (e.g., both 0)
      if (g_audioState.minVolumeDb >= g_audioState.maxVolumeDb) {
        LOGW("USBExclusive: Volume bounds invalid (Min=%d, Max=%d)! Applying "
             "standard UAC2 fallback (-127dB to 0dB)",
             g_audioState.minVolumeDb.load(), g_audioState.maxVolumeDb.load());
        g_audioState.minVolumeDb = -32512; // -127 dB * 256
        g_audioState.maxVolumeDb = 0;
      }
    }

    ControlRequest initVolReq;
    initVolReq.type = 1;
    initVolReq.volume = g_audioState.rawLinearVolume;
    {
      std::lock_guard<std::mutex> lock(g_controlMutex);
      g_controlQueue.push(initVolReq);
      g_controlCv.notify_one();
    }
  }

  LOGI("USB Audio Interface %d Claimed! Endpoint: 0x%x, PacketSize: %d",
       as_interface, ep_out, max_packet_size);
  return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_playAudio(JNIEnv *env, jobject thiz,
                                                      jstring filePath) {
  ApiMutexLock lock(__func__);
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

  // 1. Buka file untuk mengetahui bit depth baru
  const char *path = env->GetStringUTFChars(filePath, 0);
  std::string savedPath = path;
  drflac *pFlac = drflac_open_file(path, nullptr);
  if (!pFlac) {
    env->ReleaseStringUTFChars(filePath, path);
    return -1; // file open error
  }
  
  g_audioState.currentFilePath = savedPath;
  g_audioState.nextFilePath = "";

  uint32_t newSampleRate = pFlac->sampleRate;
  uint32_t newChannels = pFlac->channels;
  uint32_t newSourceBitDepth = pFlac->bitsPerSample;
  uint64_t totalFrames = pFlac->totalPCMFrameCount;
  
  // Increment generation to abort any running prepareNextTrack
  g_audioState.prepareNextGen.fetch_add(1);

  // === FORMAT COMPATIBILITY CHECK ===
  if (g_audioState.usbHandle != nullptr) {
    int target_sf = pFlac->bitsPerSample / 8;
    if (target_sf == 0) target_sf = 2;

    // Check bit depth
    bool bitDepthSupported = false;
    for (int bd : g_audioState.supportedBitDepths) {
      if (bd == (int)pFlac->bitsPerSample) {
        bitDepthSupported = true;
        break;
      }
    }

    // Check sample rate
    bool sampleRateSupported = false;
    if (g_audioState.sampleRateQuerySuccess) {
      for (uint32_t sr : g_audioState.supportedSampleRates) {
        if (sr == pFlac->sampleRate) {
          sampleRateSupported = true;
          break;
        }
      }
    } else {
      // STRICT FALLBACK MODE
      if (!g_audioState.hasValidatedRate.load()) {
        // COLD-START
        if (pFlac->sampleRate == 44100 || pFlac->sampleRate == 48000) {
          sampleRateSupported = true;
          LOGW("Cold-start fallback: Allowing %u Hz since no rate has been validated yet.", pFlac->sampleRate);
        } else {
          sampleRateSupported = false;
          LOGE("Cold-start fallback: Rejecting %u Hz (only 44.1/48kHz safe on unready device).", pFlac->sampleRate);
        }
      } else {
        // STRICT MODE
        if (pFlac->sampleRate == g_audioState.lastKnownGoodSampleRate.load()) {
          sampleRateSupported = true;
        } else {
          sampleRateSupported = false;
          LOGE("Strict fallback: Rejecting %u Hz (last known good is %u Hz).", pFlac->sampleRate, g_audioState.lastKnownGoodSampleRate.load());
        }
      }
    }

    if (!bitDepthSupported || !sampleRateSupported) {
      std::string reason;
      if (!bitDepthSupported && !sampleRateSupported) {
        reason = std::to_string(pFlac->bitsPerSample) + "-Bit/" +
                 std::to_string(pFlac->sampleRate) + "Hz not supported";
      } else if (!bitDepthSupported) {
        reason = std::to_string(pFlac->bitsPerSample) + "-Bit not supported";
      } else {
        reason = std::to_string(pFlac->sampleRate) + "Hz not supported";
      }
      LOGE("FORMAT REFUSED: %s — File: %s", reason.c_str(), savedPath.c_str());

      // Extract just the filename for display
      std::string filename = savedPath;
      size_t lastSlash = filename.find_last_of('/');
      if (lastSlash != std::string::npos) filename = filename.substr(lastSlash + 1);

      // Add to refused history
      {
        std::lock_guard<std::mutex> hlock(g_audioState.refusedHistoryMutex);
        g_audioState.refusedTrackHistory.push_back(
            {filename, (int)pFlac->bitsPerSample, (int)pFlac->sampleRate, "format_incompatible"});
        if (g_audioState.refusedTrackHistory.size() > 5) {
          g_audioState.refusedTrackHistory.erase(g_audioState.refusedTrackHistory.begin());
        }
      }

      // Call JNI callback
      if (g_jvm && g_audioState.callbackObj) {
        jclass clazz = env->GetObjectClass(g_audioState.callbackObj);
        jmethodID methodId = env->GetMethodID(clazz, "onFormatIncompatible",
            "(Ljava/lang/String;IILjava/lang/String;)V");
        if (methodId) {
          jstring jFilename = env->NewStringUTF(filename.c_str());
          jstring jReason = env->NewStringUTF(reason.c_str());
          env->CallVoidMethod(g_audioState.callbackObj, methodId,
              jFilename, (jint)pFlac->bitsPerSample, (jint)pFlac->sampleRate, jReason);
          env->DeleteLocalRef(jFilename);
          env->DeleteLocalRef(jReason);
        }
      }

      drflac_close(pFlac);
      env->ReleaseStringUTFChars(filePath, path);
      g_audioState.currentFilePath = "";
      return -2; // format incompatible
    }
  }

  // 2. Cek apakah altsetting perlu diganti
  int target_sf = pFlac->bitsPerSample / 8;
  if (target_sf == 0)
    target_sf = 2; // Default 16-bit

  bool rate_changed = (g_audioState.sampleRate.load() != newSampleRate);
  bool altsetting_changed = false;
  
  if (g_audioState.usbHandle != nullptr &&
      (target_sf != g_audioState.subframeSize.load() || rate_changed)) {
      
    // Hentikan ISO thread lama secara paksa KARENA kita harus mengganti altsetting ATAU sample rate (clock).
    // Mengubah clock saat stream aktif akan membuat DAC glitch (suara dengungan).
    g_audioState.stopIsoThread.store(true);
    if (g_audioState.isoThread != nullptr &&
        g_audioState.isoThread->joinable()) {
      LOGI("[Thread] Waiting for isoThread from playAudio (stream parameter change)");
      bool exited = false;
      for (int i = 0; i < 200; i++) { // 2 second timeout
        if (g_audioState.isoThreadExited.load()) { exited = true; break; }
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
      }
      if (!exited) {
        LOGE("FATAL: isoThread did not exit! Detaching to prevent ANR.");
        g_audioState.deviceWedged.store(true);
        g_audioState.isoThread->detach();
      } else {
        g_audioState.isoThread->join();
        LOGI("[Thread] Joined isoThread successfully");
      }
      delete g_audioState.isoThread;
      g_audioState.isoThread = nullptr;
    }
    g_audioState.transfers.clear();
    g_audioState.activeIsoTransfers.store(0);

    if (target_sf != g_audioState.subframeSize.load()) {
      LOGI("playAudio: Bit depth changed to %d bytes. Searching for new altsetting...", target_sf);
      if (!g_audioState.validAlts.empty()) {
        AltSettingInfo *exact_alt = nullptr;
        for (auto &a : g_audioState.validAlts) {
          if (a.subframe_size == target_sf) {
            exact_alt = &a;
            break;
          }
        }

        if (!exact_alt) {
          LOGE("playAudio: No exact altsetting for sf=%d despite passing compat check!", target_sf);
          drflac_close(pFlac);
          env->ReleaseStringUTFChars(filePath, path);
          g_audioState.currentFilePath = "";
          return -3;
        }

        LOGI("playAudio: Found altsetting iface=%d alt=%d sf_size=%d",
             exact_alt->interface_num, exact_alt->altsetting, exact_alt->subframe_size);

        // Set Altsetting baru
        int r = libusb_set_interface_alt_setting(
            g_audioState.usbHandle, exact_alt->interface_num, exact_alt->altsetting);
        if (r == 0) {
          g_audioState.usbAudioInterface = exact_alt->interface_num;
          g_audioState.epAddress = exact_alt->ep_out;
          g_audioState.maxPacketSize = exact_alt->max_packet_size;
          g_audioState.subframeSize.store(exact_alt->subframe_size);
          altsetting_changed = true;
          LOGI("playAudio: Successfully switched altsetting!");
        } else {
          LOGE("playAudio: Failed to switch altsetting! libusb error: %s (%d)",
               libusb_error_name(r), r);
          drflac_close(pFlac);
          env->ReleaseStringUTFChars(filePath, path);
          g_audioState.currentFilePath = "";
          return -3; // negotiation failure (Point A — isoThread already dead, clean state)
        }
      }
    }
  }

  // 3. Jika altsetting DAN sample rate TIDAK berubah, dan thread masih jalan, lakukan soft swapping
  if (!altsetting_changed && !rate_changed && g_audioState.isoThread != nullptr &&
      !g_audioState.stopIsoThread.load()) {
    g_audioState.isSwapping.store(true);
    LOGI("playAudio: Waiting for isSwappingAck...");
    auto wait_start = std::chrono::steady_clock::now();
    bool ack_timeout = false;
    while (!g_audioState.isSwappingAck.load() &&
           !g_audioState.stopIsoThread.load()) {
      std::this_thread::sleep_for(std::chrono::milliseconds(1));
      auto now = std::chrono::steady_clock::now();
      if (std::chrono::duration_cast<std::chrono::milliseconds>(now -
                                                                wait_start)
              .count() > 100) {
        ack_timeout = true;
        break;
      }
    }
    if (ack_timeout) {
      LOGE("playAudio: isSwappingAck timed out after 100ms! Forcing ISO thread recovery.");
      g_audioState.stopIsoThread.store(true);
      
      if (g_audioState.isoThread != nullptr && g_audioState.isoThread->joinable()) {
        LOGW("[Thread] Waiting for blocked isoThread from playAudio (isSwappingAck timeout).");
        bool exited = false;
        for (int i = 0; i < 200; i++) { // 2 second timeout
          if (g_audioState.isoThreadExited.load()) { exited = true; break; }
          std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }
        if (!exited) {
          LOGE("FATAL: isoThread did not exit! Detaching to prevent ANR.");
          g_audioState.deviceWedged.store(true);
          g_audioState.isoThread->detach();
        } else {
          g_audioState.isoThread->join();
        }
      }
      delete g_audioState.isoThread;
      g_audioState.isoThread = nullptr;
      
      g_audioState.transfers.clear();
      g_audioState.activeIsoTransfers.store(0);
    } else {
      auto wait_end = std::chrono::steady_clock::now();
      auto wait_duration =
          std::chrono::duration_cast<std::chrono::milliseconds>(wait_end -
                                                                wait_start)
              .count();
      LOGI("playAudio: isSwappingAck received. Waited for %lld ms.",
           (long long)wait_duration);
    }
  }

  // 4. Bersihkan state decode
  if (g_audioState.decodeThread != nullptr) {
    g_audioState.cancelDecoding.store(true);
    if (g_audioState.decodeThread->joinable())
      g_audioState.decodeThread->join();
    delete g_audioState.decodeThread;
    g_audioState.decodeThread = nullptr;
  }

  if (!g_audioState.pcmBuffer.empty()) {
    munlock(g_audioState.pcmBuffer.data(),
            g_audioState.pcmBuffer.size() * sizeof(int32_t));
    g_audioState.pcmBuffer.clear();
  }
  if (!g_audioState.pcmBufferNext.empty()) {
    munlock(g_audioState.pcmBufferNext.data(),
            g_audioState.pcmBufferNext.size() * sizeof(int32_t));
    g_audioState.pcmBufferNext.clear();
  }

  // AMAN SEKARANG: isoThread lama sudah mati atau isSwappingAck = true
  uint32_t oldSampleRate = g_audioState.sampleRate.load();
  g_audioState.sampleRate.store(newSampleRate);
  g_audioState.channels.store(newChannels);
  g_audioState.sourceBitDepth.store(newSourceBitDepth);

  try {
    g_audioState.pcmBuffer.resize(totalFrames * pFlac->channels);
  } catch (const std::bad_alloc &e) {
    LOGE("OOM: Not enough memory for %llu frames!", (unsigned long long)totalFrames);
    drflac_close(pFlac);
    g_audioState.isSwapping.store(false);
    return -1;
  }

  // Partial load strategy: decode first 100ms synchronously, the rest
  // asynchronously
  size_t framesPer100ms = pFlac->sampleRate / 10;
  size_t initialFrames =
      (totalFrames < framesPer100ms) ? totalFrames : framesPer100ms;

  size_t initialRead = drflac_read_pcm_frames_s32(
      pFlac, initialFrames, g_audioState.pcmBuffer.data());

  g_audioState.decodedFrames.store(initialRead);
  g_audioState.pcmIndex.store(0);
  g_audioState.cancelDecoding.store(false);

  if (initialRead < totalFrames) {
    g_audioState.isDecoding.store(true);
    g_audioState.decodeThread =
        new std::thread([pFlac, totalFrames, initialRead]() {
          size_t currentOffset = initialRead;
          size_t chunkSize = pFlac->sampleRate;
          while (currentOffset < totalFrames &&
                 !g_audioState.cancelDecoding.load()) {
            size_t framesToRead = (totalFrames - currentOffset > chunkSize)
                                      ? chunkSize
                                      : (totalFrames - currentOffset);
            size_t read = drflac_read_pcm_frames_s32(
                pFlac, framesToRead,
                g_audioState.pcmBuffer.data() +
                    (currentOffset * g_audioState.channels));
            if (read == 0)
              break;
            currentOffset += read;
            g_audioState.decodedFrames.store(currentOffset);
            std::this_thread::sleep_for(std::chrono::milliseconds(5));
          }
          drflac_close(pFlac);
          g_audioState.isDecoding.store(false);
        });
  } else {
    drflac_close(pFlac);
    g_audioState.isDecoding.store(false);
  }

  env->ReleaseStringUTFChars(filePath, path);
  if (mlock(g_audioState.pcmBuffer.data(),
            g_audioState.pcmBuffer.size() * sizeof(int32_t)) < 0) {
    LOGI("mlock failed or not permitted, continuing without locked memory");
  }

  g_audioState.isSwapping.store(false);

  // === SAMPLE RATE NEGOTIATION ===
  bool stream_was_stopped = (g_audioState.isoThread == nullptr);
  if (g_audioState.usbHandle != nullptr && (rate_changed || stream_was_stopped)) {
    if (g_audioState.uacVersion.load() == 2 &&
        g_audioState.clockSourceProgrammable &&
        g_audioState.clockSourceId != -1) {
      // UAC2: SET_CUR via Clock Source
      uint32_t sr = newSampleRate;
      uint8_t data[4];
      data[0] = sr & 0xFF;
      data[1] = (sr >> 8) & 0xFF;
      data[2] = (sr >> 16) & 0xFF;
      data[3] = (sr >> 24) & 0xFF;

      int r = libusb_control_transfer(g_audioState.usbHandle,
                                      0x21, 0x01, (0x01 << 8),
                                      (g_audioState.clockSourceId << 8) |
                                          g_audioState.acInterfaceNum,
                                      data, 4, 1000);

      if (r < 0) {
        LOGE("Failed to SET_CUR sample rate on Clock Source %d! Code: %d",
             g_audioState.clockSourceId.load(), r);
        g_audioState.sampleRate.store(oldSampleRate);
        playAudio_cleanup_on_negotiation_failure();
        return -3; // negotiation failure (Point B)
      }

      // GET_CUR to verify
      uint8_t verify_data[4] = {0};
      int rv = libusb_control_transfer(
          g_audioState.usbHandle,
          0xA1, 0x81, (0x01 << 8),
          (g_audioState.clockSourceId << 8) | g_audioState.acInterfaceNum,
          verify_data, 4, 1000);

      if (rv >= 4) {
        uint32_t verified_sr = verify_data[0] | (verify_data[1] << 8) |
                               (verify_data[2] << 16) | (verify_data[3] << 24);
        if (verified_sr != sr) {
          LOGE("Sample rate verification failed! Requested: %d, DAC is "
               "actually running at: %d",
               sr, verified_sr);
          g_audioState.sampleRate.store(oldSampleRate);
          playAudio_cleanup_on_negotiation_failure();
          return -3; // negotiation failure (Point C — GET_CUR mismatch)
        } else {
          LOGI("Sample rate successfully set and verified at %d Hz",
               verified_sr);
          g_audioState.lastKnownGoodSampleRate.store(sr);
          g_audioState.hasValidatedRate.store(true);
        }
      } else {
        LOGW("Failed to GET_CUR sample rate for verification! Code: %d (continuing anyway)", rv);
        g_audioState.sampleRateUnverified.store(true);
        LOGI("Assuming SET_CUR sample rate %u Hz success (unverified).", sr);
        g_audioState.lastKnownGoodSampleRate.store(sr);
        // GET_CUR failure is non-fatal: SET_CUR succeeded, we proceed
      }
    } else if (g_audioState.uacVersion.load() == 1) {
      // UAC1: SET_CUR via Endpoint
      uint32_t sr = newSampleRate;
      unsigned char data[3];
      data[0] = sr & 0xFF;
      data[1] = (sr >> 8) & 0xFF;
      data[2] = (sr >> 16) & 0xFF;
      int r = libusb_control_transfer(g_audioState.usbHandle, 0x22, 0x01,
                                      (0x01 << 8), g_audioState.epAddress,
                                      data, 3, 1000);
      if (r < 0) {
        LOGE("UAC1: Failed to SET_CUR sample rate %u on endpoint 0x%02X! Code: %d",
             sr, g_audioState.epAddress, r);
        g_audioState.sampleRate.store(oldSampleRate);
        playAudio_cleanup_on_negotiation_failure();
        return -3;
      } else {
        LOGI("UAC1: SET_CUR sample rate %u Hz on endpoint 0x%02X success", sr, g_audioState.epAddress);
        g_audioState.lastKnownGoodSampleRate.store(sr);
      }
    }
  }

  // Start ISO Thread if not running
  if (g_audioState.isoThread == nullptr && g_audioState.usbHandle != nullptr) {
    LOGI("[Thread] Spawning new isoThread");
    g_audioState.stopIsoThread.store(false);
    g_audioState.isoThreadExited.store(false);
    g_audioState.isoThread = new std::thread([]() {
      int num_transfers = 32;
      int num_packets = 32;
      int packet_size = g_audioState.maxPacketSize;
      g_audioState.activeIsoTransfers.store(0);

      for (int i = 0; i < num_transfers; i++) {
        struct libusb_transfer *transfer = libusb_alloc_transfer(num_packets);
        uint8_t *buffer = new uint8_t[num_packets * packet_size](); // Added () for zero-initialization
        libusb_fill_iso_transfer(transfer, g_audioState.usbHandle,
                                 g_audioState.epAddress, buffer,
                                 num_packets * packet_size, num_packets,
                                 iso_callback, nullptr, 1000);
        libusb_set_iso_packet_lengths(transfer, packet_size);

        g_audioState.activeIsoTransfers.fetch_add(1);
        if (libusb_submit_transfer(transfer) < 0) {
          g_audioState.activeIsoTransfers.fetch_sub(1);
        }
        g_audioState.transfers.push_back(transfer);
      }

      // Pump events while running
      while (!g_audioState.stopIsoThread.load()) {
        struct timeval tv = {0, 50000};
        libusb_handle_events_timeout_completed(g_audioState.usbContext, &tv,
                                               nullptr);
      }

      // Cancel all transfers
      for (auto t : g_audioState.transfers) {
        libusb_cancel_transfer(t);
      }

      // Wait for cancellations with Timeout Guard (max 1 second)
      auto start_time = std::chrono::steady_clock::now();
      bool timeout_hit = false;
      while (g_audioState.activeIsoTransfers.load() > 0) {
        struct timeval tv = {0, 10000};
        libusb_handle_events_timeout_completed(g_audioState.usbContext, &tv,
                                               nullptr);

        auto now = std::chrono::steady_clock::now();
        if (std::chrono::duration_cast<std::chrono::milliseconds>(now -
                                                                  start_time)
                .count() > 1000) {
          LOGE("CRITICAL: Timeout waiting for transfers to cancel! Leaking memory to prevent DMA Use-After-Free.");
          timeout_hit = true;
          break;
        }
      }

      if (timeout_hit) {
        // LEAK TERKENDALI: Pindahkan transfer ke orphan list agar dibersihkan nanti saat initUsbDac (DAC direkonek).
        // Kita tidak bisa free() sekarang karena DMA asinkron OS masih menguncinya.
        LOGE("CRITICAL: Moving %zu wedged transfers to orphan list.", g_audioState.transfers.size());
        g_audioState.deviceWedged.store(true);
        for (auto t : g_audioState.transfers) {
          g_audioState.orphanTransfers.push_back(t);
        }
        g_audioState.transfers.clear();
      } else {
        // Cleanup safely
        for (auto t : g_audioState.transfers) {
          delete[] t->buffer;
          libusb_free_transfer(t);
        }
        g_audioState.transfers.clear();
      }
      g_audioState.isoThreadExited.store(true);
    });
  }

  g_audioState.isPlaying = true;
  return 0; // success
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_pauseAudio(JNIEnv *env,
                                                       jobject thiz) {
  ApiMutexLock lock(__func__);
  g_audioState.isPlaying = false;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_resumeAudio(JNIEnv *env,
                                                        jobject thiz) {
  ApiMutexLock lock(__func__);
  g_audioState.isPlaying = true;
  
  if (g_audioState.isoThread == nullptr && g_audioState.usbHandle != nullptr) {
      LOGW("resumeAudio: isoThread is dead! Falling back to playAudio restart.");
      return JNI_FALSE;
  }
  return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_stopAudio(JNIEnv *env,
                                                      jobject thiz) {
  ApiMutexLock lock(__func__);
  g_audioState.isPlaying = false;
  if (g_audioState.decodeThread != nullptr) {
    g_audioState.cancelDecoding.store(true);
    if (g_audioState.decodeThread->joinable())
      g_audioState.decodeThread->join();
    delete g_audioState.decodeThread;
    g_audioState.decodeThread = nullptr;
  }
  
  if (g_audioState.usbHandle != nullptr) {
    g_audioState.stopIsoThread.store(true);
    if (g_audioState.isoThread != nullptr && g_audioState.isoThread->joinable()) {
      LOGI("[Thread] Waiting for isoThread from stopAudio...");
      bool exited = false;
      for (int i = 0; i < 200; i++) {
        if (g_audioState.isoThreadExited.load()) { exited = true; break; }
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
      }
      if (!exited) {
        LOGE("FATAL: isoThread did not exit! Detaching to prevent ANR.");
        g_audioState.deviceWedged.store(true);
        g_audioState.isoThread->detach();
      } else {
        g_audioState.isoThread->join();
        LOGI("[Thread] Joined isoThread successfully in stopAudio");
      }
      delete g_audioState.isoThread;
      g_audioState.isoThread = nullptr;
    }
    
    // Do NOT close usbHandle or release interfaces here!
    // stopAudio is just to stop the streaming thread cleanly.
    g_audioState.transfers.clear();
    g_audioState.activeIsoTransfers.store(0);
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_setSoftwareVolume(JNIEnv *env,
                                                              jobject thiz,
                                                              jfloat vol) {
  ApiMutexLock lock(__func__);
  g_audioState.rawLinearVolume = vol;

  if (g_audioState.isHardwareVolumeActive.load()) {
    ControlRequest req;
    req.type = 1;
    req.volume = vol;
    {
      std::lock_guard<std::mutex> lock2(g_controlMutex);
      // Coalescing: Remove any pending volume requests to prevent flooding the
      // DAC
      std::queue<ControlRequest> new_queue;
      while (!g_controlQueue.empty()) {
        ControlRequest r = g_controlQueue.front();
        g_controlQueue.pop();
        if (r.type != 1) { // Keep non-volume requests
          new_queue.push(r);
        }
      }
      new_queue.push(req);
      g_controlQueue = new_queue;
      g_controlCv.notify_one();
    }
  }
  // Always calculate software targetVolume
  float scaled_vol = vol;
  if (vol < 0.001f) {
    scaled_vol = 0.0f;
  } else {
    scaled_vol = (std::exp(vol * 4.0f) - 1.0f) / (std::exp(4.0f) - 1.0f);
  }

  if (!g_audioState.isHardwareVolumeActive.load()) {
    g_audioState.targetVolume.store(scaled_vol);
  } else {
    // If HW volume is active, ensure SW multiplier remains 1.0f (Bit-Perfect)
    g_audioState.targetVolume.store(1.0f);
  }
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getPosition(JNIEnv *env,
                                                        jobject thiz) {
  if (g_audioState.sampleRate == 0)
    return 0.0;
  return (double)g_audioState.pcmIndex.load() /
         (double)g_audioState.sampleRate.load();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getSampleRate(JNIEnv *env,
                                                          jobject thiz) {
  return g_audioState.sampleRate.load();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getNegotiatedBitDepth(
    JNIEnv *env, jobject thiz) {
  return g_audioState.subframeSize.load() * 8;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getUacVersion(JNIEnv *env,
                                                          jobject thiz) {
  return g_audioState.uacVersion.load();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getClaimedInterfaces(JNIEnv *env,
                                                                 jobject thiz) {
  std::string interfaces;
  {
    std::lock_guard<std::mutex> lock(g_stateMutex);
    for (size_t i = 0; i < g_audioState.claimedInterfaces.size(); ++i) {
      interfaces += std::to_string(g_audioState.claimedInterfaces[i]);
      if (i < g_audioState.claimedInterfaces.size() - 1) {
        interfaces += ", ";
      }
    }
  }
  return env->NewStringUTF(interfaces.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getSourceSampleRate(JNIEnv *env,
                                                                jobject thiz) {
  return g_audioState.sampleRate
      .load(); // Since active track sample rate is stored here
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getSourceBitDepth(JNIEnv *env,
                                                              jobject thiz) {
  return g_audioState.sourceBitDepth.load();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getRecentErrorCount(JNIEnv *env,
                                                                jobject thiz) {
  return g_audioState.consecutiveErrors.load();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isDacConnected(JNIEnv *env,
                                                           jobject thiz) {
  return g_audioState.usbHandle != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isPlaying(JNIEnv *env,
                                                      jobject thiz) {
  return g_audioState.isPlaying.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isFinished(JNIEnv *env,
                                                       jobject thiz) {
  return g_audioState.isFinished.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_cleanGarbage(JNIEnv *env,
                                                         jobject thiz) {
  if (!g_audioState.pcmBufferGarbage.empty()) {
    munlock(g_audioState.pcmBufferGarbage.data(),
            g_audioState.pcmBufferGarbage.size() * sizeof(int32_t));
    g_audioState.pcmBufferGarbage.clear();
    g_audioState.pcmBufferGarbage.shrink_to_fit();
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_prepareNextTrack(JNIEnv *env,
                                                             jobject thiz,
                                                             jstring filePath) {
  const char *path = env->GetStringUTFChars(filePath, 0);
  drflac *pFlac = drflac_open_file(path, nullptr);
  if (!pFlac) {
    env->ReleaseStringUTFChars(filePath, path);
    return JNI_FALSE;
  }
  uint64_t totalFrames = pFlac->totalPCMFrameCount;
  int channels = pFlac->channels;
  int sampleRate = pFlac->sampleRate;
  int bitsPerSample = pFlac->bitsPerSample;
  
  std::vector<int32_t> tempBuffer;
  try {
    tempBuffer.resize(totalFrames * channels);
    } catch (const std::bad_alloc &e) {
      LOGE("OOM: Not enough memory for next track (%llu frames)!", (unsigned long long)totalFrames);
      drflac_close(pFlac);
    env->ReleaseStringUTFChars(filePath, path);
    return JNI_FALSE;
  }
  
  uint32_t myGen = ++g_audioState.prepareNextGen;
  size_t framesRead = 0;
  size_t chunkSize = sampleRate; // Decode 1 second at a time
  
  while (framesRead < totalFrames) {
    if (g_audioState.prepareNextGen.load() != myGen) {
      LOGW("prepareNextTrack: Aborted decode by newer generation request!");
      drflac_close(pFlac);
      env->ReleaseStringUTFChars(filePath, path);
      return JNI_FALSE;
    }
    
    size_t toRead = totalFrames - framesRead;
    if (toRead > chunkSize) toRead = chunkSize;
    
    size_t read = drflac_read_pcm_frames_s32(pFlac, toRead, tempBuffer.data() + (framesRead * channels));
    if (read == 0) break;
    framesRead += read;
  }
  
  std::string savedPath = path;
  drflac_close(pFlac);
  env->ReleaseStringUTFChars(filePath, path);
  
  if (mlock(tempBuffer.data(), tempBuffer.size() * sizeof(int32_t)) < 0) {
    // Just ignore, not all systems allow this much locked memory
  }

  ApiMutexLock lock(__func__);
  if (g_audioState.prepareNextGen.load() != myGen) {
      LOGW("prepareNextTrack: Aborted swap AT COMMIT PHASE by newer request! (TOCTOU protection)");
      munlock(tempBuffer.data(), tempBuffer.size() * sizeof(int32_t));
      return JNI_FALSE;
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
  g_audioState.nextFilePath = savedPath;
  g_audioState.hasNextTrack = true;
  
  return JNI_TRUE;
}


extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isHardwareVolumeActive(
    JNIEnv *env, jobject thiz) {
  return g_audioState.isHardwareVolumeActive.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isDeviceWedged(JNIEnv *env, jobject thiz) {
  return g_audioState.deviceWedged.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isSampleRateUnverified(JNIEnv *env, jobject thiz) {
  return g_audioState.sampleRateUnverified.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isHardwareVolumeLockedBySystem(JNIEnv *env, jobject thiz) {
  bool acInterfaceClaimed = std::find(g_audioState.claimedInterfaces.begin(),
                                      g_audioState.claimedInterfaces.end(),
                                      g_audioState.acInterfaceNum.load()) !=
                            g_audioState.claimedInterfaces.end();
  return (g_audioState.featureUnitId != -1 && !acInterfaceClaimed) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isForceSoftwareVolume(JNIEnv *env, jobject thiz) {
  return g_audioState.isForceSoftwareVolume.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getSupportedBitDepths(
    JNIEnv *env, jobject thiz) {
  std::string result;
  for (size_t i = 0; i < g_audioState.supportedBitDepths.size(); i++) {
    if (i > 0) result += " / ";
    result += std::to_string(g_audioState.supportedBitDepths[i]);
  }
  return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getSupportedSampleRates(
    JNIEnv *env, jobject thiz) {
  std::string result;
  for (size_t i = 0; i < g_audioState.supportedSampleRates.size(); i++) {
    if (i > 0) result += ",";
    result += std::to_string(g_audioState.supportedSampleRates[i]);
  }
  return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getRefusedTrackHistory(
    JNIEnv *env, jobject thiz) {
  std::lock_guard<std::mutex> lock(g_audioState.refusedHistoryMutex);
  std::string json = "[";
  for (size_t i = 0; i < g_audioState.refusedTrackHistory.size(); i++) {
    const auto& entry = g_audioState.refusedTrackHistory[i];
    if (i > 0) json += ",";
    json += "{\"file\":\"" + json_escape(entry.filename) + "\",";
    json += "\"bits\":" + std::to_string(entry.bitDepth) + ",";
    json += "\"rate\":" + std::to_string(entry.sampleRate) + ",";
    json += "\"reason\":\"" + json_escape(entry.reason) + "\"}";
  }
  json += "]";
  return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getDacInfo(JNIEnv *env, jobject thiz) {
  std::lock_guard<std::mutex> lock(g_stateMutex);
  if (g_audioState.usbHandle == nullptr) {
    return env->NewStringUTF("{}");
  }

  std::string json = "{";
  json += "\"productName\":\"" + json_escape(g_audioState.dacProductName) + "\",";
  json += "\"manufacturerName\":\"" + json_escape(g_audioState.dacManufacturerName) + "\",";
  json += "\"vid\":" + std::to_string(g_audioState.dacVid.load()) + ",";
  json += "\"pid\":" + std::to_string(g_audioState.dacPid.load()) + ",";
  json += "\"isHardwareVolumeActive\":" + std::string(g_audioState.isHardwareVolumeActive.load() ? "true" : "false") + ",";
  json += "\"isForceSoftwareVolume\":" + std::string(g_audioState.isForceSoftwareVolume.load() ? "true" : "false") + ",";
  json += "\"minVolumeDb\":" + std::to_string(g_audioState.minVolumeDb.load()) + ",";
  json += "\"maxVolumeDb\":" + std::to_string(g_audioState.maxVolumeDb.load()) + ",";
  json += "\"uacVersion\":" + std::to_string(g_audioState.uacVersion.load());
  json += "}";

  return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getOutputBitDepth(
    JNIEnv *env, jobject thiz) {
  return g_audioState.subframeSize.load() * 8;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getOutputSampleRate(
    JNIEnv *env, jobject thiz) {
  return g_audioState.sampleRate.load();
}
