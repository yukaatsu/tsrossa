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

    if (g_audioState.usbHandle == nullptr)
      continue;

    if (req.type == 1) { // SET_CUR Volume
      int target_db;
      if (req.volume <= 0.01f) {
        target_db = g_audioState.minVolumeDb;
      } else {
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
        LOGE("Hardware volume SET_CUR failed! Falling back to software volume.");
        g_audioState.isHardwareVolumeActive.store(false);
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

int LIBUSB_CALL hotplug_callback(libusb_context *ctx,
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

void close_usb_dac_internal() {
  if (g_audioState.usbHandle != nullptr) {
    stop_and_join_iso_thread("closeUsbDac");
    stop_and_join_decode_thread();

    for (int iface : g_audioState.claimedInterfaces) {
      int rr = libusb_release_interface(g_audioState.usbHandle, iface);
      if (rr != 0)
        LOGE("libusb_release_interface error: %s", libusb_error_name(rr));
    }
    g_audioState.claimedInterfaces.clear();
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
    g_audioState.isWarmingUp.store(false);
    g_audioState.warmupSilenceFrames.store(0);
    g_audioState.currentFilePath = "";
    g_audioState.nextFilePath = "";
    g_audioState.hasNextTrack.store(false);
    g_audioState.decodedFrames.store(0);
    g_audioState.pcmIndex.store(0);

    if (!g_audioState.pcmBuffer.empty()) {
      munlock(g_audioState.pcmBuffer.data(), g_audioState.pcmBuffer.size() * sizeof(int32_t));
      g_audioState.pcmBuffer.clear();
    }
    if (!g_audioState.pcmBufferNext.empty()) {
      munlock(g_audioState.pcmBufferNext.data(), g_audioState.pcmBufferNext.size() * sizeof(int32_t));
      g_audioState.pcmBufferNext.clear();
    }
    
    LOGI("[Thread] Cleanup finished successfully in closeUsbDac");
  }
}

bool init_usb_dac_internal(int fd) {
  char dbg[512];
  snprintf(dbg, sizeof(dbg), "=== initUsbDac(FD=%d) START ===", fd);
  record_usb_diag(dbg);

  if (g_audioState.usbHandle != nullptr) {
    record_usb_diag("initUsbDac: usbHandle != nullptr. Preserving existing connection.");
    return true;
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
  int dev_speed = libusb_get_device_speed(dev);
  int dev_fps = (dev_speed == LIBUSB_SPEED_HIGH || dev_speed == LIBUSB_SPEED_SUPER) ? 8000 : 1000;
  g_audioState.usb_frames_per_sec.store(dev_fps);
  snprintf(dbg, sizeof(dbg), "Device speed=%d -> usb_frames_per_sec=%d", dev_speed, dev_fps);
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
            g_audioState.clockSourceId.store(clockId);
            g_audioState.clockSourceProgrammable.store((bmControls & 0x03) == 0x03);
          } else if (subtype == 0x0B &&
                     g_audioState.uacVersion.load() == 2) { // CLOCK_SELECTOR
            uint8_t clockSelId = alt->extra[pos + 3];
            uint8_t numPins = alt->extra[pos + 4];
            LOGI("USBExclusive: Found CLOCK_SELECTOR ID=%d with %d input pins", clockSelId, numPins);
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
    g_controlQueue.pop();
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
        bitDepthSet.insert(subframe_size * 8);

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

  g_audioState.supportedBitDepths.assign(bitDepthSet.begin(), bitDepthSet.end());
  snprintf(dbg, sizeof(dbg), "Valid streaming altsettings found: %zu", g_audioState.validAlts.size());
  record_usb_diag(dbg);

  if (!g_audioState.validAlts.empty()) {
    int source_bytes = g_audioState.sourceBitDepth.load() / 8;
    if (source_bytes == 0)
      source_bytes = 2; // Default 16-bit

    AltSettingInfo best_alt = g_audioState.validAlts[0];
    for (const auto &a : g_audioState.validAlts) {
      if (a.subframe_size == source_bytes) {
        best_alt = a;
        break;
      }
    }

    as_interface = best_alt.interface_num;
    as_altsetting = best_alt.altsetting;
    ep_out = best_alt.ep_out;
    max_packet_size = best_alt.max_packet_size;
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
  g_audioState.claimedInterfaces.clear();

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
            struct usbdevfs_disconnect_claim dc;
            memset(&dc, 0, sizeof(dc));
            dc.interface = (unsigned int)iface_num;
            dc.flags = 0;
            ioctl_res = ioctl(fd, USBDEVFS_DISCONNECT_CLAIM, &dc);
            if (ioctl_res != 0) ioctl_errno = errno;
            if (ioctl_res == 0) {
              claim_res = 0;
            } else {
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
    unsigned char range_buf[256] = {0};
    int r = -1;
    for (int retry = 0; retry < 3; retry++) {
      r = libusb_control_transfer(g_audioState.usbHandle, 0xA1, 0x02,
                                  (0x01 << 8), // SAM_FREQ_CONTROL
                                  (g_audioState.clockSourceId << 8) |
                                      g_audioState.acInterfaceNum,
                                  range_buf, sizeof(range_buf), 1000);
      if (r >= 2) break;
      std::this_thread::sleep_for(std::chrono::milliseconds(200));
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
            sampleRateSet.insert(sr_min);
          } else {
            for (uint32_t freq = sr_min; freq <= sr_max; freq += sr_res) {
              sampleRateSet.insert(freq);
            }
            sampleRateSet.insert(sr_max);
          }
        }
      }
      g_audioState.sampleRateQuerySuccess = true;
    }
  } else if (g_audioState.uacVersion.load() == 1) {
    if (!sampleRateSet.empty()) {
      g_audioState.sampleRateQuerySuccess = true;
    }
  }

  g_audioState.supportedSampleRates.assign(sampleRateSet.begin(), sampleRateSet.end());
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
        std::string lowerProduct = g_audioState.dacProductName;
        std::transform(lowerProduct.begin(), lowerProduct.end(), lowerProduct.begin(), ::tolower);
        if (lowerProduct.find("celest") != std::string::npos || lowerProduct.find("ruyi") != std::string::npos) {
          g_audioState.isForceSoftwareVolume.store(true);
        }
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
  if (!g_audioState.isForceSoftwareVolume.load() && g_audioState.featureUnitId != -1 && acInterfaceClaimed) {
    g_audioState.isHardwareVolumeActive.store(true);
    g_audioState.targetVolume.store(1.0f); // Bit-Perfect
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
      unsigned char range_buf[256] = {0};
      int r = libusb_control_transfer(g_audioState.usbHandle, 0xA1, 0x02,
                                      (0x02 << 8) | g_audioState.volumeChannel,
                                      (g_audioState.featureUnitId << 8) |
                                          g_audioState.acInterfaceNum,
                                      range_buf, sizeof(range_buf), 1000);
      if (r >= 2) {
        int num_subranges = range_buf[0] | (range_buf[1] << 8);
        int min_vol = 32767, max_vol = -32768;
        for (int s = 0; s < num_subranges; s++) {
          int offset = 2 + (s * 6);
          if (offset + 4 <= r) {
            int16_t sub_min = range_buf[offset] | (range_buf[offset + 1] << 8);
            int16_t sub_max = range_buf[offset + 2] | (range_buf[offset + 3] << 8);
            if (sub_min < min_vol) min_vol = sub_min;
            if (sub_max > max_vol) max_vol = sub_max;
          }
        }
        if (min_vol != 32767) g_audioState.minVolumeDb = min_vol;
        if (max_vol != -32768) g_audioState.maxVolumeDb = max_vol;
      }

      if (g_audioState.minVolumeDb >= g_audioState.maxVolumeDb) {
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

  snprintf(dbg, sizeof(dbg), "=== initUsbDac SUCCESS: VID=%04X, PID=%04X, Product='%s', Manufacturer='%s', UAC=%d, Claimed=%zu ifaces ===",
           g_audioState.dacVid.load(), g_audioState.dacPid.load(),
           g_audioState.dacProductName.c_str(), g_audioState.dacManufacturerName.c_str(),
           g_audioState.uacVersion.load(), g_audioState.claimedInterfaces.size());
  record_usb_diag(dbg);

  return true;
}
