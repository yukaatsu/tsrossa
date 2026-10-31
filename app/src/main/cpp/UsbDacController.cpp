#include "UsbDacController.h"
#include "AudioEngineState.h"
#include "AudioStreamer.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <set>
#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <unistd.h>

static inline void lib_usb_clear_halt_if_needed() {
  if (g_audioState.usbHandle != nullptr) {
    libusb_clear_halt(g_audioState.usbHandle, 0);
  }
}

void control_thread_func() {
  while (!g_audioState.stopControlThread.load()) {
    ControlRequest req;
    {
      std::unique_lock<std::mutex> lock(g_controlMutex);
      g_controlCv.wait(lock, [] {
        return !g_controlQueue.empty() || g_audioState.stopControlThread.load();
      });

      if (g_audioState.stopControlThread.load()) {
        while (!g_controlQueue.empty())
          g_controlQueue.pop();
        break;
      }

      req = g_controlQueue.front();
      g_controlQueue.pop();
    }

    // Asynchronous JNI event dispatcher (dispatched off realtime audio thread)
    if (req.type == REQ_EVENT_TRACK_FINISHED || req.type == REQ_EVENT_TRACK_TRANSITION ||
        req.type == REQ_EVENT_USB_STALL_FAULT) {
      if (g_jvm && g_audioState.callbackObj) {
        JNIEnv *env = nullptr;
        bool needDetach = false;
        if (g_jvm->GetEnv((void **)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
          if (g_jvm->AttachCurrentThread(&env, NULL) == 0) {
            needDetach = true;
          }
        }
        if (env && g_audioState.callbackObj) {
          jclass clazz = env->GetObjectClass(g_audioState.callbackObj);
          if (req.type == REQ_EVENT_TRACK_FINISHED) {
            jmethodID methodId = env->GetMethodID(clazz, "onTrackFinished", "()V");
            if (methodId) {
              LOGI("[JNI] Dispatching onTrackFinished to Kotlin callback");
              env->CallVoidMethod(g_audioState.callbackObj, methodId);
              if (env->ExceptionCheck()) {
                LOGE("[JNI] Exception occurred during onTrackFinished callback!");
                env->ExceptionDescribe();
                env->ExceptionClear();
              }
            } else {
              LOGE("[JNI] Failed to find method onTrackFinished ()V");
            }
          } else if (req.type == REQ_EVENT_TRACK_TRANSITION) {
            jmethodID methodId = env->GetMethodID(clazz, "onTrackTransition", "(Ljava/lang/String;)V");
            if (methodId) {
              LOGI("[JNI] Dispatching onTrackTransition to Kotlin callback: %s", req.eventArg.c_str());
              jstring pathStr = env->NewStringUTF(req.eventArg.c_str());
              env->CallVoidMethod(g_audioState.callbackObj, methodId, pathStr);
              if (env->ExceptionCheck()) {
                LOGE("[JNI] Exception occurred during onTrackTransition callback!");
                env->ExceptionDescribe();
                env->ExceptionClear();
              }
              env->DeleteLocalRef(pathStr);
            } else {
              LOGE("[JNI] Failed to find method onTrackTransition (Ljava/lang/String;)V");
            }
          } else if (req.type == REQ_EVENT_USB_STALL_FAULT) {
            jmethodID methodId = env->GetMethodID(clazz, "onUsbStallFault", "()V");
            if (methodId) {
              LOGI("[JNI] Dispatching onUsbStallFault to Kotlin callback");
              env->CallVoidMethod(g_audioState.callbackObj, methodId);
              if (env->ExceptionCheck()) {
                LOGE("[JNI] Exception occurred during onUsbStallFault callback!");
                env->ExceptionDescribe();
                env->ExceptionClear();
              }
            }
          }
          if (needDetach) {
            g_jvm->DetachCurrentThread();
          }
        }
      }
      continue;
    }

    if (g_audioState.usbHandle == nullptr)
      continue;

    if (req.type == 1) { // SET_CUR Volume
      if (g_audioState.usbHandle != nullptr && g_audioState.isHardwareVolumeSupported.load() &&
          g_audioState.featureUnitId.load() > 0) {
        int16_t target_db = g_audioState.maxVolumeDb.load();
        float desired_db = 0.0f;

        if (!g_audioState.preferHardwareVolume.load()) {
          // In Universal 64-Bit DSP Mode, lock DAC hardware at 0dB (maxVolumeDb)
          target_db = g_audioState.maxVolumeDb.load();
          desired_db = 0.0f;
        } else if (req.volume <= 0.0f) {
          target_db = g_audioState.minVolumeDb.load();
          desired_db = -128.0f;
        } else {
          desired_db = compute_perceptual_db(req.volume);
          int32_t raw_target = (int32_t)g_audioState.maxVolumeDb.load() +
                               (int32_t)std::lround(desired_db * 256.0f);
          int16_t step = (g_audioState.resVolumeDb.load() > 0) ? g_audioState.resVolumeDb.load() : 1;
          if (step > 1) {
            raw_target = ((raw_target - g_audioState.maxVolumeDb.load()) / step) * step +
                         g_audioState.maxVolumeDb.load();
          }
          if (raw_target < g_audioState.minVolumeDb.load())
            raw_target = g_audioState.minVolumeDb.load();
          if (raw_target > g_audioState.maxVolumeDb.load())
            raw_target = g_audioState.maxVolumeDb.load();
          target_db = (int16_t)raw_target;
        }

        uint8_t vol_buf[2] = {(uint8_t)(target_db & 0xFF),
                              (uint8_t)((target_db >> 8) & 0xFF)};
        int r_master = -1;
        int r_ch = -1;
        uint8_t ac_iface = (uint8_t)(g_audioState.acInterfaceNum.load() & 0xFF);
        uint16_t wIndex = (uint16_t)((g_audioState.featureUnitId.load() << 8) | ac_iface);

        // Ensure Feature Unit is explicitly unmuted across Master (0), Left (1), and Right (2)
        uint8_t unmute_val = 0;
        libusb_control_transfer(g_audioState.usbHandle, 0x21, 0x01, (0x01 << 8) | 0x00, wIndex, &unmute_val, 1, 100);
        libusb_control_transfer(g_audioState.usbHandle, 0x21, 0x01, (0x01 << 8) | 0x01, wIndex, &unmute_val, 1, 100);
        libusb_control_transfer(g_audioState.usbHandle, 0x21, 0x01, (0x01 << 8) | 0x02, wIndex, &unmute_val, 1, 100);

        if (g_audioState.volumeMasterSupported.load() || g_audioState.volumeChannel.load() == 0) {
          r_master = libusb_control_transfer(
              g_audioState.usbHandle, 0x21, 0x01, (0x02 << 8) | 0x00,
              wIndex, vol_buf, 2, 200);
          if (r_master < 0) lib_usb_clear_halt_if_needed();
        }
        if (g_audioState.volumePerChannelSupported.load() || g_audioState.volumeChannel.load() != 0 || r_master < 0) {
          int r1 = libusb_control_transfer(
              g_audioState.usbHandle, 0x21, 0x01, (0x02 << 8) | 0x01,
              wIndex, vol_buf, 2, 200);
          if (r1 < 0) lib_usb_clear_halt_if_needed();
          int r2 = libusb_control_transfer(
              g_audioState.usbHandle, 0x21, 0x01, (0x02 << 8) | 0x02,
              wIndex, vol_buf, 2, 200);
          if (r2 < 0) lib_usb_clear_halt_if_needed();
          r_ch = (r1 >= 0 || r2 >= 0) ? 2 : -1;
        }

        if (r_master >= 0 || r_ch >= 0) {
          if (g_audioState.preferHardwareVolume.load()) {
            int16_t actual_target_db = target_db;
            uint8_t cur_buf[2] = {0, 0};
            int ch_to_read = (r_master >= 0) ? 0 : 1;
            uint8_t req_cur = (g_audioState.uacVersion.load() == 1) ? 0x81 : 0x01;
            int r_cur = libusb_control_transfer(
                g_audioState.usbHandle, 0xA1, req_cur, (0x02 << 8) | ch_to_read,
                wIndex, cur_buf, 2, 100);
            if (r_cur == 2) {
              actual_target_db = (int16_t)(cur_buf[0] | (cur_buf[1] << 8));
            } else if (r_cur < 0) {
              lib_usb_clear_halt_if_needed();
            }
            float actual_hw_db = (float)(actual_target_db - g_audioState.maxVolumeDb.load()) / 256.0f;
            g_audioState.appliedHardwareDb.store(actual_hw_db);
            g_audioState.isHardwareVolumeActive.store(true);
          } else {
            g_audioState.appliedHardwareDb.store(0.0f);
            g_audioState.isHardwareVolumeActive.store(false);
          }
        } else {
          LOGW("Hardware volume SET_CUR failed, falling back to 64-bit DSP volume");
          g_audioState.appliedHardwareDb.store(0.0f);
          g_audioState.isHardwareVolumeActive.store(false);
        }
      } else if (g_audioState.usbHandle != nullptr && g_audioState.featureUnitId.load() > 0) {
        // Fallback: Even if hardware volume query failed or was shallow,
        // force Unmute and 0 dB Full Scale on DAC Feature Unit so the internal analog amplifier is not throttled
        uint8_t ac_iface = (uint8_t)(g_audioState.acInterfaceNum.load() & 0xFF);
        uint16_t wIndex = (uint16_t)((g_audioState.featureUnitId.load() << 8) | ac_iface);
        uint8_t unmute_val = 0;
        libusb_control_transfer(g_audioState.usbHandle, 0x21, 0x01, (0x01 << 8) | 0x00, wIndex, &unmute_val, 1, 100);
        libusb_control_transfer(g_audioState.usbHandle, 0x21, 0x01, (0x01 << 8) | 0x01, wIndex, &unmute_val, 1, 100);
        libusb_control_transfer(g_audioState.usbHandle, 0x21, 0x01, (0x01 << 8) | 0x02, wIndex, &unmute_val, 1, 100);
        uint8_t zero_db[2] = {0, 0};
        libusb_control_transfer(g_audioState.usbHandle, 0x21, 0x01, (0x02 << 8) | 0x00, wIndex, zero_db, 2, 100);
        libusb_control_transfer(g_audioState.usbHandle, 0x21, 0x01, (0x02 << 8) | 0x01, wIndex, zero_db, 2, 100);
        libusb_control_transfer(g_audioState.usbHandle, 0x21, 0x01, (0x02 << 8) | 0x02, wIndex, zero_db, 2, 100);
        g_audioState.appliedHardwareDb.store(0.0f);
        g_audioState.isHardwareVolumeActive.store(false);
      } else {
        g_audioState.appliedHardwareDb.store(0.0f);
        g_audioState.isHardwareVolumeActive.store(false);
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

void set_volume(float volume) {
  g_audioState.rawLinearVolume = volume;
  ControlRequest req;
  req.type = 1; // SET_CUR Volume
  req.volume = volume;
  {
    std::lock_guard<std::mutex> lock(g_controlMutex);
    std::queue<ControlRequest> new_queue;
    while (!g_controlQueue.empty()) {
      ControlRequest r = g_controlQueue.front();
      g_controlQueue.pop();
      if (r.type != 1) new_queue.push(r);
    }
    new_queue.push(req);
    g_controlQueue = new_queue;
    g_controlCv.notify_one();
  }
}

void set_hardware_volume_enabled(bool enabled) {
  g_audioState.preferHardwareVolume.store(enabled);
  set_volume(g_audioState.rawLinearVolume);
}

bool is_hardware_volume_enabled() {
  return g_audioState.preferHardwareVolume.load();
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

  int sf = parse_extra(alt->extra, alt->extra_length);
  if (sf != -1)
    return sf;

  for (int k = 0; k < alt->bNumEndpoints; k++) {
    sf = parse_extra(alt->endpoint[k].extra, alt->endpoint[k].extra_length);
    if (sf != -1)
      return sf;
  }

  return 2; // default to 16-bit
}

std::vector<uint32_t> get_uac1_sample_rates(const struct libusb_interface_descriptor *alt) {
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
          if (format_type == 1) {
            int sam_freq_type_offset = pos + 7;
            if (sam_freq_type_offset < pos + len) {
              int bSamFreqType = extra[sam_freq_type_offset];
              int freq_data_offset = pos + 8;
              if (bSamFreqType > 0) {
                for (int f = 0; f < bSamFreqType; f++) {
                  int foff = freq_data_offset + f * 3;
                  if (foff + 2 < pos + len) {
                    uint32_t freq = extra[foff] | (extra[foff + 1] << 8) | (extra[foff + 2] << 16);
                    if (freq > 0) rates.push_back(freq);
                  }
                }
              } else {
                if (freq_data_offset + 5 < pos + len) {
                  uint32_t min_freq = extra[freq_data_offset] |
                                     (extra[freq_data_offset + 1] << 8) |
                                     (extra[freq_data_offset + 2] << 16);
                  uint32_t max_freq = extra[freq_data_offset + 3] |
                                     (extra[freq_data_offset + 4] << 8) |
                                     (extra[freq_data_offset + 5] << 16);
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

int resolve_effective_usb_speed(libusb_device *dev, libusb_device_handle *handle, int uac_version, const std::set<uint32_t> &sample_rates) {
  if (dev == nullptr) {
    return (uac_version == 2) ? LIBUSB_SPEED_HIGH : LIBUSB_SPEED_FULL;
  }

  int dev_speed = libusb_get_device_speed(dev);
  if (dev_speed == LIBUSB_SPEED_HIGH || dev_speed == LIBUSB_SPEED_SUPER || dev_speed == 5 /* SUPER_PLUS */) {
    return dev_speed;
  }
  if (dev_speed == LIBUSB_SPEED_LOW) {
    return LIBUSB_SPEED_LOW;
  }

  // Tier 1: Check UAC version.
  // UAC2 is designed for High-Speed and Super-Speed.
  if (uac_version == 2) {
    return LIBUSB_SPEED_HIGH;
  }

  // Tier 2: Check sample rates.
  // Full-Speed isochronous bandwidth limit is ~1023 bytes/ms (~8 Mbps).
  // High sample rates (>96kHz such as 176.4k, 192k, 352.8k, 384k) are physically impossible on Full-Speed.
  for (uint32_t sr : sample_rates) {
    if (sr > 96000) {
      return LIBUSB_SPEED_HIGH;
    }
  }

  // Tier 3: Inspect USB Device Descriptor (bcdUSB)
  struct libusb_device_descriptor desc;
  if (libusb_get_device_descriptor(dev, &desc) == 0) {
    if (desc.bcdUSB < 0x0200) {
      // Legacy USB 1.0 / 1.1 hardware cannot exceed Full-Speed (12 Mbps)
      return (dev_speed == LIBUSB_SPEED_LOW) ? LIBUSB_SPEED_LOW : LIBUSB_SPEED_FULL;
    }
    // Tier 4: Device Qualifier Descriptor probe (type 0x06 per USB 2.0 spec)
    if (handle != nullptr) {
      uint8_t qbuf[10];
      constexpr uint8_t USB_DT_DEVICE_QUALIFIER = 0x06;
      int qres = libusb_get_descriptor(handle, USB_DT_DEVICE_QUALIFIER, 0, qbuf, sizeof(qbuf));
      if (qres >= 0) {
        return LIBUSB_SPEED_HIGH;
      }
    }
    // If speed was explicitly detected as FULL by a kernel that supports USBDEVFS_GET_SPEED,
    // and it is UAC1 without hi-res rates, keep FULL.
    if (dev_speed == LIBUSB_SPEED_FULL) {
      return LIBUSB_SPEED_FULL;
    }
    // Otherwise, if unknown on USB 2.0+ audio device, assume High-Speed
    return LIBUSB_SPEED_HIGH;
  }

  if (dev_speed == LIBUSB_SPEED_FULL) {
    return LIBUSB_SPEED_FULL;
  }

  // Tier 5: Safe fallback for modern external USB Audio devices
  return LIBUSB_SPEED_HIGH;
}

void close_usb_dac_internal() {
  if (g_audioState.usbHandle != nullptr) {
    stop_and_join_iso_thread("closeUsbDac");
    stop_and_join_decode_thread();

    // Safely stop control thread before releasing USB handle
    g_audioState.stopControlThread.store(true);
    g_controlCv.notify_all();
    if (g_audioState.controlThread != nullptr &&
        g_audioState.controlThread->joinable()) {
      g_audioState.controlThread->join();
      delete g_audioState.controlThread;
      g_audioState.controlThread = nullptr;
    }

    {
      std::lock_guard<std::mutex> lock(g_stateMutex);
      for (int iface : g_audioState.claimedInterfaces) {
        int rr = libusb_release_interface(g_audioState.usbHandle, iface);
        if (rr != 0)
          LOGE("libusb_release_interface error: %s", libusb_error_name(rr));
      }
      g_audioState.claimedInterfaces.clear();
      g_audioState.supportedBitDepths.clear();
      g_audioState.supportedSampleRates.clear();
    }
    libusb_close(g_audioState.usbHandle);
    g_audioState.usbHandle = nullptr;
    g_audioState.usbFd = -1;
    
    // Reset state to force hardware re-initialization on next playAudio
    g_audioState.subframeSize.store(0);
    g_audioState.sampleRate.store(0);
    g_audioState.channels.store(0);
    g_audioState.currentAltSetting.store(0);
    g_audioState.hasValidatedRate.store(false);
    g_audioState.isPlaying.store(false);
    g_audioState.isSwapping.store(false);
    g_audioState.swappingZeroUrbCount.store(0);
    g_audioState.isSwappingAck.store(false);
    g_audioState.lastIsoPacketTimeNs.store(0);
    g_audioState.isWarmingUp.store(false);
    g_audioState.warmupSilenceFrames.store(0);
    g_audioState.isHardwareVolumeActive.store(false);
    g_audioState.isHardwareVolumeSupported.store(false);
    g_audioState.appliedHardwareDb.store(0.0f);
    g_audioState.volumeMasterSupported.store(false);
    g_audioState.volumePerChannelSupported.store(false);
    g_audioState.featureUnitId.store(-1);
    g_audioState.currentFilePath = "";
    g_audioState.nextFilePath = "";
    g_audioState.hasNextTrack.store(false);
    g_audioState.totalFrames.store(0);
    g_audioState.decodedFrames.store(0);
    g_audioState.pendingSeekSeconds.store(-1.0);
    g_audioState.pcmIndex.store(0);

    if (!g_audioState.pcmBuffer.empty()) {
      munlock(g_audioState.pcmBuffer.data(), g_audioState.pcmBuffer.size() * sizeof(int32_t));
      g_audioState.pcmBuffer.clear();
    }
    if (!g_audioState.pcmBufferNext.empty()) {
      munlock(g_audioState.pcmBufferNext.data(), g_audioState.pcmBufferNext.size() * sizeof(int32_t));
      g_audioState.pcmBufferNext.clear();
    }

    if (g_audioState.callbackObj != nullptr && g_jvm != nullptr) {
      JNIEnv *env = nullptr;
      bool needDetach = false;
      if (g_jvm->GetEnv((void **)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, NULL) == 0) {
          needDetach = true;
        }
      }
      if (env != nullptr) {
        env->DeleteGlobalRef(g_audioState.callbackObj);
        g_audioState.callbackObj = nullptr;
        if (needDetach) {
          g_jvm->DetachCurrentThread();
        }
      }
    }
    
    LOGI("[Thread] Cleanup finished successfully in closeUsbDac");
  }
}

bool init_usb_dac_internal(int fd) {
  char dbg[512];
  snprintf(dbg, sizeof(dbg), "=== initUsbDac(FD=%d) START ===", fd);
  record_usb_diag(dbg);

  if (g_audioState.usbHandle != nullptr) {
    if (g_audioState.usbFd == fd) {
      record_usb_diag("initUsbDac: usbHandle != nullptr with identical FD. Preserving existing connection.");
      return true;
    }
    snprintf(dbg, sizeof(dbg), "initUsbDac: Replacing previous connection (old FD=%d -> new FD=%d)", g_audioState.usbFd, fd);
    record_usb_diag(dbg);
    close_usb_dac_internal();
  }

  // Clean up orphan transfers now that the USB device was physically detached and kernel state is wiped
  if (!g_audioState.orphanTransfers.empty()) {
    snprintf(dbg, sizeof(dbg), "Cleaning up %zu orphan transfers from previous deadlocks.", g_audioState.orphanTransfers.size());
    record_usb_diag(dbg);
    for (auto t : g_audioState.orphanTransfers) {
      if (t) {
        free(t->buffer);
        libusb_free_transfer(t);
      }
    }
    g_audioState.orphanTransfers.clear();
  }

  if (g_audioState.usbContext == nullptr) {
    libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, 1);
    const struct libusb_init_option options[] = {
        { LIBUSB_OPTION_NO_DEVICE_DISCOVERY, { .ival = 1 } }
    };
    int init_res = libusb_init_context(&g_audioState.usbContext, options, 1);
    snprintf(dbg, sizeof(dbg), "libusb_init_context(NO_DEVICE_DISCOVERY) -> %d (%s)", init_res, libusb_error_name(init_res));
    record_usb_diag(dbg);
    if (init_res < 0) {
      init_res = libusb_init(&g_audioState.usbContext);
      snprintf(dbg, sizeof(dbg), "Fallback libusb_init() -> %d (%s)", init_res, libusb_error_name(init_res));
      record_usb_diag(dbg);
      if (init_res < 0) {
        record_usb_diag("FATAL: libusb_init_context failed!");
        return false;
      }
    }
  } else {
    record_usb_diag("libusb_init: Reusing existing usbContext.");
  }

  int wrap_res = libusb_wrap_sys_device(g_audioState.usbContext, (intptr_t)fd,
                                        &g_audioState.usbHandle);
  snprintf(dbg, sizeof(dbg), "libusb_wrap_sys_device(FD=%d) -> %d (%s)",
           fd, wrap_res, libusb_error_name(wrap_res));
  record_usb_diag(dbg);
  if (wrap_res < 0 || g_audioState.usbHandle == nullptr) {
    record_usb_diag("FATAL: libusb_wrap_sys_device failed! Check usbfs/SELinux permissions on FD.");
    return false;
  }
  g_audioState.usbFd = fd;

  libusb_device *dev = libusb_get_device(g_audioState.usbHandle);
  int raw_speed = libusb_get_device_speed(dev);
  int dev_speed = raw_speed;
  struct libusb_device_descriptor dev_desc;
  if (libusb_get_device_descriptor(dev, &dev_desc) == 0) {
    if (dev_speed == LIBUSB_SPEED_UNKNOWN && dev_desc.bcdUSB >= 0x0200) {
      dev_speed = LIBUSB_SPEED_HIGH;
    }
  }
  int dev_fps = (dev_speed == LIBUSB_SPEED_HIGH || dev_speed == LIBUSB_SPEED_SUPER || dev_speed == 5) ? 8000 : 1000;
  g_audioState.usb_frames_per_sec.store(dev_fps);
  snprintf(dbg, sizeof(dbg), "Device speed=%d (initial_resolved=%d) -> default usb_frames_per_sec=%d", raw_speed, dev_speed, dev_fps);
  record_usb_diag(dbg);

  struct libusb_config_descriptor *config = nullptr;
  int act_cfg_res = libusb_get_active_config_descriptor(dev, &config);
  snprintf(dbg, sizeof(dbg), "libusb_get_active_config_descriptor() -> %d (%s)",
           act_cfg_res, libusb_error_name(act_cfg_res));
  record_usb_diag(dbg);

  if (act_cfg_res < 0 || config == nullptr) {
    int cfg0_res = libusb_get_config_descriptor(dev, 0, &config);
    snprintf(dbg, sizeof(dbg), "libusb_get_config_descriptor(idx=0) -> %d (%s)",
             cfg0_res, libusb_error_name(cfg0_res));
    record_usb_diag(dbg);
    if (cfg0_res < 0 || config == nullptr) {
      record_usb_diag("FATAL: Failed to get any USB config descriptor!");
      libusb_close(g_audioState.usbHandle);
      g_audioState.usbHandle = nullptr;
      return false;
    }
    int set_cfg = libusb_set_configuration(g_audioState.usbHandle, config->bConfigurationValue);
    snprintf(dbg, sizeof(dbg), "libusb_set_configuration(%d) -> %d (%s)",
             config->bConfigurationValue, set_cfg, libusb_error_name(set_cfg));
    record_usb_diag(dbg);
  }

  snprintf(dbg, sizeof(dbg), "Active Config: bConfigurationValue=%d, bNumInterfaces=%d",
           config->bConfigurationValue, config->bNumInterfaces);
  record_usb_diag(dbg);

  // Parse Audio Control Interface
  g_audioState.uacVersion = 1;
  g_audioState.featureUnitId = -1;
  g_audioState.clockSourceId = -1;
  g_audioState.clockSelectorId = -1;
  g_audioState.clockSelectorPin = 1;
  g_audioState.sourceBitDepthNext.store(0);
  g_audioState.isHardwareVolumeActive.store(false);
  g_audioState.isHardwareVolumeSupported.store(false);
  g_audioState.volumeMasterSupported.store(false);
  g_audioState.volumePerChannelSupported.store(false);
  g_audioState.appliedHardwareDb.store(0.0f);
  g_audioState.deviceWedged.store(false);
  g_audioState.isSwapping.store(false);
  g_audioState.swappingZeroUrbCount.store(0);
  g_audioState.isSwappingAck.store(false);
  g_audioState.lastIsoPacketTimeNs.store(0);
  g_audioState.sampleRateUnverified.store(false);
  g_audioState.volumeChannel = 0;

  struct ClockSourceCandidate {
    int id;
    uint8_t bmControls;
  };
  struct ClockSelectorCandidate {
    int id;
    std::vector<int> pins;
  };
  struct FeatureUnitCandidate {
    int id;
    int sourceId;
    int volumeChannel;
    bool masterSupported = false;
    bool perChannelSupported = false;
  };

  std::vector<ClockSourceCandidate> clockSources;
  std::vector<ClockSelectorCandidate> clockSelectors;
  std::vector<FeatureUnitCandidate> featureUnits;
  int playbackInputTerminalId = -1;
  int playbackInputClockId = -1;
  int playbackOutputSourceId = -1;
  int playbackOutputClockId = -1;

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
          int d_type = alt->extra[pos + 1];
          int subtype = alt->extra[pos + 2];

          if (d_type == 0x24) {
            if (subtype == 0x01) { // HEADER
              if (len >= 6) {
                g_audioState.uacVersion.store(
                    (alt->extra[pos + 3] == 0x00 && alt->extra[pos + 4] == 0x02)
                        ? 2
                        : 1);
              }
            } else if (subtype == 0x02) { // INPUT_TERMINAL
              if (len >= 8) {
                int termId = alt->extra[pos + 3];
                uint16_t termType = alt->extra[pos + 4] | (alt->extra[pos + 5] << 8);
                // 0x0101 = USB Streaming (Host -> DAC Playback Input Terminal)
                if (termType == 0x0101 && playbackInputTerminalId == -1) {
                  playbackInputTerminalId = termId;
                  if (g_audioState.uacVersion.load() == 2 && len >= 9) {
                    playbackInputClockId = alt->extra[pos + 7];
                  }
                }
              }
            } else if (subtype == 0x03) { // OUTPUT_TERMINAL
              if (len >= 9) {
                uint16_t termType = alt->extra[pos + 4] | (alt->extra[pos + 5] << 8);
                int srcId = alt->extra[pos + 7];
                // Exclude 0x0101 (USB Streaming IN = Microphone Capture Output Terminal)
                if (termType != 0x0101 && playbackOutputSourceId == -1) {
                  playbackOutputSourceId = srcId;
                  if (g_audioState.uacVersion.load() == 2 && len >= 10) {
                    playbackOutputClockId = alt->extra[pos + 8];
                  }
                }
              }
            } else if (subtype == 0x06) { // FEATURE_UNIT
              int unitId = alt->extra[pos + 3];
              int srcId = (len >= 5) ? alt->extra[pos + 4] : -1;
              bool masterVol = false;
              bool ch1Vol = false;

              if (g_audioState.uacVersion.load() == 1) {
                int bControlSize = (len >= 6) ? alt->extra[pos + 5] : 1;
                if (bControlSize <= 0) bControlSize = 1;
                int masterPos = pos + 6;
                uint8_t masterControls = (masterPos < pos + len) ? alt->extra[masterPos] : 0;
                int ch1Pos = masterPos + bControlSize;
                uint8_t ch1Controls = (ch1Pos < pos + len) ? alt->extra[ch1Pos] : 0;
                int numChannels = (len > 7 && bControlSize > 0) ? ((len - 7) / bControlSize) : 0;

                masterVol = (masterControls & 0x02) != 0;
                ch1Vol = (numChannels >= 1) && ((ch1Controls & 0x02) != 0);
              } else { // UAC2
                int masterPos = pos + 5;
                uint8_t masterControls = (masterPos < pos + len) ? alt->extra[masterPos] : 0;
                int ch1Pos = masterPos + 4;
                uint8_t ch1Controls = (ch1Pos < pos + len) ? alt->extra[ch1Pos] : 0;
                int numChannels = (len >= 9) ? ((len - 5) / 4 - 1) : 0;

                masterVol = (masterControls & 0x0C) != 0;
                ch1Vol = (numChannels >= 1) && ((ch1Controls & 0x0C) != 0);
              }

              int volCh = masterVol ? 0 : (ch1Vol ? 1 : 0);
              featureUnits.push_back({unitId, srcId, volCh, masterVol, ch1Vol});
            } else if (subtype == 0x0A &&
                       g_audioState.uacVersion.load() == 2) { // CLOCK_SOURCE
              uint8_t clockId = alt->extra[pos + 3];
              uint8_t bmControls =
                  (pos + 6 <= pos + len) ? alt->extra[pos + 5] : 0;
              clockSources.push_back({clockId, bmControls});
            } else if (subtype == 0x0B &&
                       g_audioState.uacVersion.load() == 2) { // CLOCK_SELECTOR
              uint8_t clockSelId = alt->extra[pos + 3];
              uint8_t numPins = (pos + 4 < pos + len) ? alt->extra[pos + 4] : 0;
              std::vector<int> pins;
              for (int p = 0; p < numPins; p++) {
                if (pos + 5 + p < pos + len) {
                  pins.push_back(alt->extra[pos + 5 + p]);
                }
              }
              clockSelectors.push_back({clockSelId, pins});
              LOGI("USBExclusive: Found CLOCK_SELECTOR ID=%d with %d input pins", clockSelId, numPins);
            }
          }
          pos += len;
        }
      }
    }
  }

  // Resolve Playback Feature Unit (avoid selecting Microphone Capture Feature Unit on headset DACs)
  if (!featureUnits.empty()) {
    FeatureUnitCandidate chosenFu = featureUnits[0];
    for (const auto &fu : featureUnits) {
      if ((playbackOutputSourceId != -1 && fu.id == playbackOutputSourceId) ||
          (playbackInputTerminalId != -1 && fu.sourceId == playbackInputTerminalId)) {
        chosenFu = fu;
        break;
      }
    }
    g_audioState.featureUnitId.store(chosenFu.id);
    g_audioState.volumeChannel.store(chosenFu.volumeChannel);
    g_audioState.volumeMasterSupported.store(chosenFu.masterSupported);
    g_audioState.volumePerChannelSupported.store(chosenFu.perChannelSupported);
    g_audioState.isHardwareVolumeSupported.store(chosenFu.masterSupported || chosenFu.perChannelSupported);
  } else {
    g_audioState.featureUnitId.store(-1);
    g_audioState.volumeChannel.store(0);
    g_audioState.volumeMasterSupported.store(false);
    g_audioState.volumePerChannelSupported.store(false);
    g_audioState.isHardwareVolumeSupported.store(false);
  }

  // Resolve Playback Clock Source (avoid selecting Microphone Capture Clock Source on headset DACs)
  if (g_audioState.uacVersion.load() == 2 && !clockSources.empty()) {
    int targetClockEntity = (playbackInputClockId != -1) ? playbackInputClockId : playbackOutputClockId;
    for (const auto &csel : clockSelectors) {
      if (csel.id == targetClockEntity || targetClockEntity == -1) {
        g_audioState.clockSelectorId.store(csel.id);
        g_audioState.clockSelectorPin.store(1);
        if (csel.id == targetClockEntity && !csel.pins.empty()) {
          targetClockEntity = csel.pins[0];
        }
        break;
      }
    }
    ClockSourceCandidate chosenCs = clockSources[0];
    for (const auto &cs : clockSources) {
      if (cs.id == targetClockEntity) {
        chosenCs = cs;
        break;
      }
    }
    g_audioState.clockSourceId.store(chosenCs.id);
    g_audioState.clockSourceProgrammable.store((chosenCs.bmControls & 0x03) != 0);
    snprintf(dbg, sizeof(dbg),
             "UAC2 AudioControl resolved: clockSourceId=%d (total=%zu, inClk=%d, outClk=%d), featureUnitId=%d (total=%zu)",
             chosenCs.id, clockSources.size(), playbackInputClockId, playbackOutputClockId,
             g_audioState.featureUnitId.load(), featureUnits.size());
    record_usb_diag(dbg);
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
    g_controlQueue.pop();
  g_audioState.controlThread = new std::thread(control_thread_func);

  int as_interface = -1;
  int as_altsetting = -1;
  uint8_t ep_out = 0;
  int max_packet_size = 0;

  {
    std::lock_guard<std::mutex> lock(g_stateMutex);
    g_audioState.validAlts.clear();
    g_audioState.supportedBitDepths.clear();
    g_audioState.supportedSampleRates.clear();
  }
  g_audioState.sampleRateQuerySuccess = false;
  {
    std::lock_guard<std::mutex> lock(g_audioState.refusedHistoryMutex);
    g_audioState.refusedTrackHistory.clear();
  }

  std::set<int> bitDepthSet;
  std::set<uint32_t> sampleRateSet;

  for (int i = 0; i < config->bNumInterfaces; i++) {
    for (int j = 0; j < config->interface[i].num_altsetting; j++) {
      const struct libusb_interface_descriptor *alt =
          &config->interface[i].altsetting[j];
      if (alt->bInterfaceClass == 1 && alt->bInterfaceSubClass == 2) {
        int subframe_size = get_subframe_size(alt);

        for (int k = 0; k < alt->bNumEndpoints; k++) {
          if ((alt->endpoint[k].bEndpointAddress & 0x80) == 0 &&
              (alt->endpoint[k].bmAttributes & 0x03) == 1) { // Isochronous OUT
            bitDepthSet.insert(subframe_size * 8);

            if (g_audioState.uacVersion.load() == 1) {
              auto rates = get_uac1_sample_rates(alt);
              for (auto r : rates) sampleRateSet.insert(r);
            }

            int pk_size = libusb_get_max_iso_packet_size(
                dev, alt->endpoint[k].bEndpointAddress);
            if (pk_size < 0)
              pk_size = alt->endpoint[k].wMaxPacketSize;

            uint8_t ep_addr = alt->endpoint[k].bEndpointAddress;
            uint8_t ep_sync = alt->endpoint[k].bSynchAddress;
            bool is_async = ((alt->endpoint[k].bmAttributes & 0x0C) == 0x04);
            uint8_t ep_bInterval = alt->endpoint[k].bInterval;

            int effective_speed = resolve_effective_usb_speed(
                dev, g_audioState.usbHandle, g_audioState.uacVersion.load(), sampleRateSet);
            int alt_fps = 1000;
            if (effective_speed == LIBUSB_SPEED_HIGH || effective_speed == LIBUSB_SPEED_SUPER || effective_speed == 5) {
              int shift = (ep_bInterval >= 1 && ep_bInterval <= 16) ? (ep_bInterval - 1) : 0;
              int uframes_per_pkt = 1 << shift;
              alt_fps = 8000 / uframes_per_pkt;
              if (alt_fps < 125) alt_fps = 8000;
            }

            // Find companion feedback endpoint if explicit or listed in same altsetting
            uint8_t ep_fb = 0;
            if (ep_sync != 0) {
              ep_fb = ep_sync;
            } else {
              for (int fb_idx = 0; fb_idx < alt->bNumEndpoints; fb_idx++) {
                if ((alt->endpoint[fb_idx].bEndpointAddress & 0x80) != 0 &&
                    ((alt->endpoint[fb_idx].bmAttributes & 0x0C) == 0x10 ||
                     (alt->endpoint[fb_idx].bmAttributes & 0x03) == 1)) {
                  ep_fb = alt->endpoint[fb_idx].bEndpointAddress;
                  break;
                }
              }
            }

            g_audioState.validAlts.push_back(
                {alt->bInterfaceNumber, alt->bAlternateSetting,
                 ep_addr, pk_size, subframe_size, ep_fb, is_async, alt_fps});
            snprintf(dbg, sizeof(dbg),
                     "  Alt[iface=%d, alt=%d]: ep=0x%02X maxPkt=%d sf=%d (%d-bit) bInterval=%d fps=%d async=%d",
                     alt->bInterfaceNumber, alt->bAlternateSetting, ep_addr, pk_size,
                     subframe_size, subframe_size * 8, ep_bInterval, alt_fps, is_async ? 1 : 0);
            record_usb_diag(dbg);
          }
        }
      }
    }
  }

  {
    std::lock_guard<std::mutex> lock(g_stateMutex);
    g_audioState.supportedBitDepths.assign(bitDepthSet.begin(), bitDepthSet.end());
  }
  snprintf(dbg, sizeof(dbg), "Valid streaming altsettings found: %zu", g_audioState.validAlts.size());
  record_usb_diag(dbg);

  if (!g_audioState.validAlts.empty()) {
    int source_bytes = g_audioState.sourceBitDepth.load() / 8;
    if (source_bytes == 0)
      source_bytes = 2; // Default 16-bit

    AltSettingInfo best_alt = g_audioState.validAlts[0];
    int best_score = -1;
    for (const auto &a : g_audioState.validAlts) {
      int score = 0;
      if (a.subframe_size >= source_bytes) {
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
        best_alt = a;
      }
    }

    as_interface = best_alt.interface_num;
    as_altsetting = best_alt.altsetting;
    ep_out = best_alt.ep_out;
    max_packet_size = best_alt.max_packet_size;
    if (best_alt.packets_per_sec > 0) {
      g_audioState.usb_frames_per_sec.store(best_alt.packets_per_sec);
    }
    snprintf(dbg, sizeof(dbg), "USB Speed Final Assessment: effective_speed=%d -> active usb_frames_per_sec=%d",
             resolve_effective_usb_speed(dev, g_audioState.usbHandle, g_audioState.uacVersion.load(), sampleRateSet),
             g_audioState.usb_frames_per_sec.load());
    record_usb_diag(dbg);
  }

  if (as_interface == -1 || ep_out == 0) {
    snprintf(dbg, sizeof(dbg), "FATAL: as_interface=%d, ep_out=0x%02X (no valid audio streaming endpoint found!)",
             as_interface, ep_out);
    record_usb_diag(dbg);
    libusb_free_config_descriptor(config);
    libusb_close(g_audioState.usbHandle);
    g_audioState.usbHandle = nullptr;
    return false;
  }

  libusb_set_auto_detach_kernel_driver(g_audioState.usbHandle, 1);
  {
    std::lock_guard<std::mutex> lock(g_stateMutex);
    g_audioState.claimedInterfaces.clear();
  }

  // Claim audio interfaces
  for (int i = 0; i < config->bNumInterfaces; i++) {
    for (int j = 0; j < config->interface[i].num_altsetting; j++) {
      const struct libusb_interface_descriptor *alt =
          &config->interface[i].altsetting[j];
      if (alt->bInterfaceClass == 1) {
        int iface_num = alt->bInterfaceNumber;
        if (std::find(g_audioState.claimedInterfaces.begin(),
                      g_audioState.claimedInterfaces.end(),
                      iface_num) == g_audioState.claimedInterfaces.end()) {
          int detach_res = libusb_detach_kernel_driver(g_audioState.usbHandle, iface_num);
          int claim_res = libusb_claim_interface(g_audioState.usbHandle, iface_num);
          int ioctl_res = -999;
          int ioctl_errno = 0;
          if (claim_res == LIBUSB_ERROR_BUSY) {
            // If kernel driver held interface, release it first so libusb can claim it
            ioctl(fd, USBDEVFS_RELEASEINTERFACE, &iface_num);
            claim_res = libusb_claim_interface(g_audioState.usbHandle, iface_num);
            if (claim_res == LIBUSB_ERROR_BUSY) {
              struct usbdevfs_disconnect_claim dc;
              memset(&dc, 0, sizeof(dc));
              dc.interface = (unsigned int)iface_num;
              dc.flags = 0;
              ioctl_res = ioctl(fd, USBDEVFS_DISCONNECT_CLAIM, &dc);
              if (ioctl_res != 0) ioctl_errno = errno;
              claim_res = libusb_claim_interface(g_audioState.usbHandle, iface_num);
            }
          }

          snprintf(dbg, sizeof(dbg), " Claim Iface %d: detach=%d (%s), claim=%d (%s), ioctl_dc=%d (errno=%d)",
                   iface_num, detach_res, libusb_error_name(detach_res),
                   claim_res, libusb_error_name(claim_res),
                   ioctl_res, ioctl_errno);
          record_usb_diag(dbg);

          if (claim_res == 0) {
            std::lock_guard<std::mutex> lock(g_stateMutex);
            g_audioState.claimedInterfaces.push_back(iface_num);
          }
        }
      }
    }
  }

  // PHASE 3: Query Sample Rates
  if (g_audioState.uacVersion.load() == 2 && g_audioState.clockSourceId != -1) {
    if (g_audioState.clockSelectorId.load() != -1) {
      uint8_t pin = (uint8_t)g_audioState.clockSelectorPin.load();
      libusb_control_transfer(g_audioState.usbHandle, 0x21, 0x01,
                              (0x01 << 8),
                              (g_audioState.clockSelectorId.load() << 8) |
                                  g_audioState.acInterfaceNum.load(),
                              &pin, 1, 500);
    }

    auto query_clock_rates = [&](int clkId, std::set<uint32_t> &outRates) -> bool {
      unsigned char range_buf[256] = {0};
      int r = -1;
      for (int retry = 0; retry < 3; retry++) {
        r = libusb_control_transfer(g_audioState.usbHandle, 0xA1, 0x02,
                                    (0x01 << 8), // SAM_FREQ_CONTROL
                                    (clkId << 8) | g_audioState.acInterfaceNum.load(),
                                    range_buf, sizeof(range_buf), 1000);
        if (r >= 2) break;
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
      }
      if (r >= 2) {
        int num_subranges = range_buf[0] | (range_buf[1] << 8);
        for (int s = 0; s < num_subranges; s++) {
          int offset = 2 + (s * 12);
          if (offset + 11 < r) {
            uint32_t sr_min = range_buf[offset] | (range_buf[offset+1] << 8) |
                             (range_buf[offset+2] << 16) | (range_buf[offset+3] << 24);
            uint32_t sr_max = range_buf[offset+4] | (range_buf[offset+5] << 8) |
                             (range_buf[offset+6] << 16) | (range_buf[offset+7] << 24);
            uint32_t sr_res = range_buf[offset+8] | (range_buf[offset+9] << 8) |
                             (range_buf[offset+10] << 16) | (range_buf[offset+11] << 24);
            if (sr_res == 0 || sr_min == sr_max) {
              if (sr_min > 0) outRates.insert(sr_min);
            } else if (sr_res <= 100) {
              static const uint32_t standard_rates[] = {
                  44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000, 705600, 768000
              };
              for (uint32_t rate : standard_rates) {
                if (rate >= sr_min && rate <= sr_max) {
                  outRates.insert(rate);
                }
              }
              if (sr_min > 0) outRates.insert(sr_min);
              if (sr_max > 0) outRates.insert(sr_max);
            } else {
              for (uint32_t freq = sr_min; freq <= sr_max; freq += sr_res) {
                if (freq > 0) outRates.insert(freq);
              }
              if (sr_max > 0) outRates.insert(sr_max);
            }
          }
        }
        return !outRates.empty();
      }
      return false;
    };

    if (query_clock_rates(g_audioState.clockSourceId.load(), sampleRateSet)) {
      g_audioState.sampleRateQuerySuccess = true;
    } else {
      // Fallback: try other discovered clock sources if primary didn't respond
      for (const auto &cs : clockSources) {
        if (cs.id != g_audioState.clockSourceId.load() && query_clock_rates(cs.id, sampleRateSet)) {
          g_audioState.clockSourceId.store(cs.id);
          g_audioState.clockSourceProgrammable.store((cs.bmControls & 0x03) != 0);
          g_audioState.sampleRateQuerySuccess = true;
          break;
        }
      }
    }
  } else if (g_audioState.uacVersion.load() == 1) {
    if (!sampleRateSet.empty()) {
      g_audioState.sampleRateQuerySuccess = true;
    }
  }

  {
    std::lock_guard<std::mutex> lock(g_stateMutex);
    g_audioState.supportedSampleRates.assign(sampleRateSet.begin(), sampleRateSet.end());
  }
  libusb_free_config_descriptor(config);

  // Final retry on streaming interface if not claimed yet
  if (std::find(g_audioState.claimedInterfaces.begin(),
                g_audioState.claimedInterfaces.end(),
                as_interface) == g_audioState.claimedInterfaces.end()) {
    struct usbdevfs_disconnect_claim dc;
    memset(&dc, 0, sizeof(dc));
    dc.interface = (unsigned int)as_interface;
    dc.flags = 0;
    int ioctl_res = ioctl(fd, USBDEVFS_DISCONNECT_CLAIM, &dc);
    int claim_res = libusb_claim_interface(g_audioState.usbHandle, as_interface);
    if (ioctl_res == 0 || claim_res == 0) {
      std::lock_guard<std::mutex> lock(g_stateMutex);
      g_audioState.claimedInterfaces.push_back(as_interface);
    }
  }

  if (std::find(g_audioState.claimedInterfaces.begin(),
                g_audioState.claimedInterfaces.end(),
                as_interface) == g_audioState.claimedInterfaces.end()) {
    for (int iface : g_audioState.claimedInterfaces) {
      libusb_release_interface(g_audioState.usbHandle, iface);
    }
    g_audioState.claimedInterfaces.clear();
    libusb_close(g_audioState.usbHandle);
    g_audioState.usbHandle = nullptr;
    return false;
  }

  int alt0_res = libusb_set_interface_alt_setting(g_audioState.usbHandle, as_interface, 0);
  snprintf(dbg, sizeof(dbg), "libusb_set_interface_alt_setting(iface=%d, alt=0) -> %d (%s)",
           as_interface, alt0_res, libusb_error_name(alt0_res));
  record_usb_diag(dbg);

  g_audioState.usbAudioInterface = as_interface;
  g_audioState.epAddress = ep_out;
  g_audioState.maxPacketSize = max_packet_size;

  g_audioState.isForceSoftwareVolume.store(false);
  struct libusb_device_descriptor desc;
  if (libusb_get_device_descriptor(dev, &desc) == 0) {
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

  bool acInterfaceClaimed = std::find(g_audioState.claimedInterfaces.begin(),
                                      g_audioState.claimedInterfaces.end(),
                                      g_audioState.acInterfaceNum.load()) !=
                            g_audioState.claimedInterfaces.end();
  if (g_audioState.isHardwareVolumeSupported.load() &&
      g_audioState.featureUnitId.load() > 0 && acInterfaceClaimed) {
    bool rangeValid = false;
    uint8_t ac_iface = (uint8_t)(g_audioState.acInterfaceNum.load() & 0xFF);
    uint16_t wIndex = (uint16_t)((g_audioState.featureUnitId.load() << 8) | ac_iface);
    int ch = g_audioState.volumeChannel.load();

    if (g_audioState.uacVersion.load() == 1) {
      uint8_t min_buf[2] = {0, 0};
      uint8_t max_buf[2] = {0, 0};
      uint8_t res_buf[2] = {0, 1};
      int rMin = libusb_control_transfer(
          g_audioState.usbHandle, 0xA1, 0x82,
          (0x02 << 8) | ch,
          wIndex, min_buf, 2, 1000);
      if (rMin < 0) lib_usb_clear_halt_if_needed();

      int rMax = libusb_control_transfer(
          g_audioState.usbHandle, 0xA1, 0x83,
          (0x02 << 8) | ch,
          wIndex, max_buf, 2, 1000);
      if (rMax < 0) lib_usb_clear_halt_if_needed();

      int rRes = libusb_control_transfer(
          g_audioState.usbHandle, 0xA1, 0x84,
          (0x02 << 8) | ch,
          wIndex, res_buf, 2, 1000);
      if (rRes < 0) lib_usb_clear_halt_if_needed();

      if (rMin == 2 && rMax == 2) {
        g_audioState.minVolumeDb.store((int16_t)((min_buf[1] << 8) | min_buf[0]));
        g_audioState.maxVolumeDb.store((int16_t)((max_buf[1] << 8) | max_buf[0]));
        int16_t res = (rRes == 2) ? (int16_t)((res_buf[1] << 8) | res_buf[0]) : 256;
        if (res <= 0) res = 256;
        g_audioState.resVolumeDb.store(res);
        rangeValid = (g_audioState.minVolumeDb.load() < g_audioState.maxVolumeDb.load()) &&
                     ((g_audioState.maxVolumeDb.load() - g_audioState.minVolumeDb.load()) >= (6 * 256));
      }
    } else {
      uint8_t range_buf[64];
      memset(range_buf, 0, sizeof(range_buf));
      int r = libusb_control_transfer(
          g_audioState.usbHandle, 0xA1, 0x02,
          (0x02 << 8) | ch,
          wIndex, range_buf,
          sizeof(range_buf), 1000);
      if (r < 0) {
        r = libusb_control_transfer(
            g_audioState.usbHandle, 0xA1, 0x82,
            (0x02 << 8) | ch,
            wIndex, range_buf,
            sizeof(range_buf), 1000);
      }
      if (r >= 8) {
        uint16_t num_subranges = range_buf[0] | (range_buf[1] << 8);
        if (num_subranges >= 1) {
          g_audioState.minVolumeDb.store((int16_t)(range_buf[2] | (range_buf[3] << 8)));
          g_audioState.maxVolumeDb.store((int16_t)(range_buf[4] | (range_buf[5] << 8)));
          int16_t res = (int16_t)(range_buf[6] | (range_buf[7] << 8));
          if (res <= 0) res = 256;
          g_audioState.resVolumeDb.store(res);

          for (int i = 1; i < num_subranges && (2 + i * 6 + 5) < r; i++) {
            int16_t next_min = (int16_t)(range_buf[2 + i * 6] | (range_buf[2 + i * 6 + 1] << 8));
            int16_t next_max = (int16_t)(range_buf[2 + i * 6 + 2] | (range_buf[2 + i * 6 + 3] << 8));
            if (next_min < g_audioState.minVolumeDb.load()) g_audioState.minVolumeDb.store(next_min);
            if (next_max > g_audioState.maxVolumeDb.load()) g_audioState.maxVolumeDb.store(next_max);
          }
          rangeValid = (g_audioState.minVolumeDb.load() < g_audioState.maxVolumeDb.load()) &&
                       ((g_audioState.maxVolumeDb.load() - g_audioState.minVolumeDb.load()) >= (6 * 256));
        }
      } else {
        lib_usb_clear_halt_if_needed();
      }
    }

    if (!rangeValid) {
      LOGW("DAC reported invalid/shallow UAC volume range (min=%d, max=%d). Using standard fallback.",
           g_audioState.minVolumeDb.load(), g_audioState.maxVolumeDb.load());
      g_audioState.minVolumeDb.store(-16384); // -64 dB
      g_audioState.maxVolumeDb.store(0);      //   0 dB
      g_audioState.resVolumeDb.store(256);    //   1 dB
    }

    LOGI("Hardware Volume Initialized: min=%.2fdB, max=%.2fdB, res=%.2fdB, preferHw=%d",
         (float)g_audioState.minVolumeDb.load() / 256.0f,
         (float)g_audioState.maxVolumeDb.load() / 256.0f,
         (float)g_audioState.resVolumeDb.load() / 256.0f,
         g_audioState.preferHardwareVolume.load());
    set_volume(g_audioState.rawLinearVolume);
  } else {
    g_audioState.isHardwareVolumeActive.store(false);
    g_audioState.appliedHardwareDb.store(0.0f);
  }

  snprintf(dbg, sizeof(dbg), "=== initUsbDac SUCCESS: VID=%04X, PID=%04X, Product='%s', Manufacturer='%s', UAC=%d, Claimed=%zu ifaces ===",
           g_audioState.dacVid.load(), g_audioState.dacPid.load(),
           g_audioState.dacProductName.c_str(), g_audioState.dacManufacturerName.c_str(),
           g_audioState.uacVersion.load(), g_audioState.claimedInterfaces.size());
  record_usb_diag(dbg);

  return true;
}
