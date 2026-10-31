#pragma once

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <libusb.h>
#include <memory>
#include <mutex>
#include <queue>
#include <string>
#include <thread>
#include <vector>
#include <jni.h>

#include "AudioCommon.h"
#include "AudioDecoder.h"

struct AtomicFloat {
  std::atomic<float> val{1.0f};
  AtomicFloat(float v = 1.0f) : val(v) {}
  AtomicFloat(const AtomicFloat &other) : val(other.val.load()) {}
  operator float() const { return val.load(); }
  float load() const { return val.load(); }
  void store(float v) { val.store(v); }
  float operator=(float v) { val.store(v); return v; }
  float operator+=(float d) {
    float cur = val.load();
    while (!val.compare_exchange_weak(cur, cur + d)) {}
    return cur + d;
  }
};

struct AltSettingInfo {
  int interface_num;
  int altsetting;
  uint8_t ep_out;
  int max_packet_size;
  int subframe_size;
  uint8_t ep_feedback = 0;
  bool is_async = false;
  int packets_per_sec = 0;
};

struct RefusedTrackInfo {
  std::string filename;
  int bitDepth;
  int sampleRate;
  std::string reason; // "format_incompatible" or "negotiation_failed"
};

enum ControlRequestType {
  REQ_SET_VOLUME = 1,
  REQ_EVENT_TRACK_FINISHED = 10,
  REQ_EVENT_TRACK_TRANSITION = 11,
  REQ_EVENT_USB_STALL_FAULT = 12
};

struct ControlRequest {
  int type; // 1 = SET_CUR Volume, 10 = onTrackFinished, 11 = onTrackTransition
  float volume = 0.0f;
  int sampleRate = 0;
  int interfaceNum = 0;
  int altSetting = 0;
  std::string eventArg;
};

struct AudioEngineState {
  libusb_context *usbContext = nullptr;
  libusb_device_handle *usbHandle = nullptr;
  int usbFd = -1;
  int usbAudioInterface = -1;
  uint8_t epAddress = 0;
  uint8_t epFeedback = 0;
  bool isAsync = false;
  int maxPacketSize = 0;
  std::atomic<int> currentAltSetting{0};

  std::vector<AltSettingInfo> validAlts;

  // Audio Buffer & Streaming Ring-Buffer State
  std::vector<int32_t> pcmBuffer;
  std::atomic<uint64_t> pcmBufferCapacityFrames{0};
  std::atomic<uint64_t> totalFrames{0};
  std::atomic<uint64_t> decodedFrames{0};
  std::atomic<uint64_t> pcmIndex{0};
  std::atomic<uint32_t> channels{0};
  std::atomic<uint32_t> sampleRate{0};
  std::atomic<int> subframeSize{0}; // 2=16-bit, 3=24-bit, 4=32-bit
  std::atomic<int> activeIsoTransfers{0};
  std::atomic<int> usb_frames_per_sec{1000};

  // Next Track Buffer (For Gapless Playback with Ring Buffer handoff)
  std::vector<int32_t> pcmBufferNext;
  std::vector<int32_t> nextPcmBuffer;
  std::atomic<uint64_t> nextPcmBufferCapacityFrames{0};
  std::atomic<uint64_t> nextTotalFrames{0};
  std::atomic<uint64_t> nextDecodedFrames{0};
  std::unique_ptr<AudioDecoder> nextActiveDecoder;
  std::unique_ptr<AudioDecoder> activeDecoder;
  std::atomic<uint32_t> channelsNext{2};
  std::atomic<uint32_t> sampleRateNext{48000};
  std::atomic<bool> hasNextTrack{false};
  std::atomic<bool> needGaplessDecoderHandoff{false};

  // Hardware DAC Warmup & Pre-Roll
  std::atomic<bool> isWarmingUp{false};
  std::atomic<uint32_t> warmupSilenceFrames{0};
  
  std::string currentFilePath;
  std::string nextFilePath;

  // Garbage buffers
  std::vector<int32_t> pcmBufferGarbage;

  jobject callbackObj = nullptr;
  std::atomic<bool> isPlaying{false};
  std::atomic<bool> isFinished{false};
  std::atomic<bool> isSwapping{false};
  std::atomic<bool> isSwappingAck{false};
  std::atomic<int> swappingZeroUrbCount{0};
  uint32_t iso_fixed_rem = 0; // Jitterless Fixed-Point Bresenham remainder accumulator
  std::atomic<float> targetVolume{1.0f};
  std::atomic<float> rawLinearVolume{1.0f};
  
  std::atomic<uint32_t> prepareNextGen{0};
  std::atomic<uint64_t> seekGeneration{0};
  std::atomic<bool> isInitializingTrack{false};
  
  std::atomic<double> pendingSeekSeconds{-1.0};
  std::atomic<bool> isDecoding{false};
  std::atomic<bool> cancelDecoding{false};
  std::thread *decodeThread = nullptr;
  std::condition_variable decoderCv;
  std::mutex decoderMutex;

  // ISO Thread
  std::thread *isoThread = nullptr;
  std::atomic<bool> stopIsoThread{false};
  std::atomic<bool> isoThreadStarted{false};
  std::atomic<bool> isoThreadExited{true};
  std::atomic<bool> stallFaultReported{false};
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

  // Hardware & Universal Hybrid Volume Control State
  std::atomic<bool> isHardwareVolumeSupported{false};
  std::atomic<bool> isHardwareVolumeActive{false};
  // Default false = Universal 64-Bit DSP Mode (100% compatible with all DACs including AD1 & M06Pro 2)
  std::atomic<bool> preferHardwareVolume{false};
  std::atomic<float> appliedHardwareDb{0.0f}; // Actual dB currently absorbed by DAC hardware
  std::atomic<int> featureUnitId{-1};
  std::atomic<int> volumeChannel{0};
  std::atomic<bool> volumeMasterSupported{false};
  std::atomic<bool> volumePerChannelSupported{false};
  std::atomic<int16_t> minVolumeDb{-32768};
  std::atomic<int16_t> maxVolumeDb{0};
  std::atomic<int16_t> resVolumeDb{256};
  AtomicFloat currentVolume{1.0f};
  float currentSoftwareVolume = 1.0f;

  std::atomic<bool> isForceSoftwareVolume{false};
  std::atomic<bool> deviceWedged{false};
  std::atomic<bool> sampleRateUnverified{false};
  std::atomic<uint32_t> lastKnownGoodSampleRate{48000};
  std::atomic<bool> hasValidatedRate{false};
  std::atomic<int> uacVersion{0}; // 1 or 2
  std::atomic<int> clockSourceId{-1};
  std::atomic<int> clockSelectorId{-1};
  std::atomic<int> clockSelectorPin{1};
  std::atomic<bool> clockSourceProgrammable{true};
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

  // Oscilloscope Hardware Timing Telemetry (sub-microsecond precision)
  std::atomic<int64_t> lastInterfaceSettleUs{0};
  std::atomic<int64_t> lastPllRelockUs{0};
  std::atomic<int64_t> lastAntiPopPaddingUs{0};
  std::atomic<int64_t> maxIsoJitterNs{0};
  std::atomic<int64_t> lastIsoPacketTimeNs{0};

  // Bit-Perfect Pure Integer Path Telemetry & Buffer Profile Configuration
  std::atomic<bool> bitPerfectDirectPassActive{false};
  std::atomic<int> isoTransfersCount{12};
  std::atomic<int> isoPacketsCount{16};
};

extern AudioEngineState g_audioState;
extern std::mutex g_stateMutex;
extern std::queue<ControlRequest> g_controlQueue;
extern std::mutex g_controlMutex;
extern std::condition_variable g_controlCv;
extern std::mutex g_isoStartMutex;
extern std::condition_variable g_isoStartCv;
extern std::string g_lastUsbDiagnostic;
extern std::mutex g_usbDiagMutex;

void record_usb_diag(const std::string &msg);
