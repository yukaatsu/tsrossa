#pragma once

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <libusb.h>
#include <mutex>
#include <queue>
#include <string>
#include <thread>
#include <vector>
#include <jni.h>

#include "AudioCommon.h"

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

struct ControlRequest {
  int type; // 1 = SET_CUR Volume, 2 = SET_CUR Sample Rate, 3 = SET_INTERFACE
  float volume;
  int sampleRate;
  int interfaceNum;
  int altSetting;
};

struct AudioEngineState {
  libusb_context *usbContext = nullptr;
  libusb_device_handle *usbHandle = nullptr;
  int usbFd = -1;
  int usbAudioInterface = -1;
  uint8_t epAddress = 0;
  int maxPacketSize = 0;
  std::atomic<int> currentAltSetting{0};

  std::vector<AltSettingInfo> validAlts;

  // RAM Playback Buffer (Raw PCM Int32 for UAC1/UAC2)
  std::vector<int32_t> pcmBuffer;
  std::atomic<size_t> pcmIndex{0};
  std::atomic<uint32_t> channels{0};
  std::atomic<uint32_t> sampleRate{0};
  std::atomic<int> subframeSize{0}; // 2=16-bit, 3=24-bit, 4=32-bit
  std::atomic<int> activeIsoTransfers{0};
  std::atomic<int> usb_frames_per_sec{1000};

  // Gapless pre-loading buffers
  std::vector<int32_t> pcmBufferNext;
  std::atomic<uint32_t> channelsNext{2};
  std::atomic<uint32_t> sampleRateNext{48000};
  std::atomic<bool> hasNextTrack{false};

  // Hardware DAC Warmup & Pre-Roll
  std::atomic<bool> isWarmingUp{false};
  std::atomic<uint32_t> warmupSilenceFrames{0};
  std::atomic<uint32_t> microFadeFrames{0};
  std::atomic<uint32_t> microFadeTotal{0};
  
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
  uint32_t iso_fixed_rem = 0; // Jitterless Fixed-Point Bresenham remainder accumulator
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
  std::vector<int> supportedBitDepths;       // e.g. {16, 24, 32}
  std::vector<uint32_t> supportedSampleRates; // discrete list
  bool sampleRateQuerySuccess = false;

  // Refused track history (max 5 entries)
  std::vector<RefusedTrackInfo> refusedTrackHistory;
  std::mutex refusedHistoryMutex;
};

extern AudioEngineState g_audioState;
extern std::mutex g_stateMutex;
extern std::queue<ControlRequest> g_controlQueue;
extern std::mutex g_controlMutex;
extern std::condition_variable g_controlCv;
extern std::string g_lastUsbDiagnostic;
extern std::mutex g_usbDiagMutex;

void record_usb_diag(const std::string &msg);
