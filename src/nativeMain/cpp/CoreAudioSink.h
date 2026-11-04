#pragma once

#include <CoreAudio/CoreAudio.h>
#include <AudioToolbox/AudioToolbox.h>
#include <CoreFoundation/CoreFoundation.h>

#include <atomic>
#include <cmath>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include <thread>
#include <condition_variable>

#include "MacAudioCommon.h"
#include "MacAudioDecoder.h"
#include "DspUtils.h"

struct DeviceInfo {
  AudioDeviceID id;
  std::string uid;
  std::string name;
  bool isUsb;
  bool isDefault;
  Float64 nominalSampleRate;
  std::vector<Float64> supportedRates;
};

class CoreAudioSink {
public:
  static constexpr size_t MAX_RING_BUFFER_SAMPLES = 16 * 1024 * 1024; // 64 MB (16M x int32_t)
  static constexpr size_t MAX_NEXT_BUFFER_SAMPLES = 8 * 1024 * 1024;  // 32 MB pre-buffer for Gapless

  static CoreAudioSink &getInstance() {
    static CoreAudioSink instance;
    return instance;
  }

  CoreAudioSink();
  ~CoreAudioSink();

  bool init();
  void shutdown();

  std::vector<DeviceInfo> getAudioDevices();
  bool selectDevice(AudioDeviceID deviceId);
  AudioDeviceID getActiveDevice() const { return m_activeDevice; }

  bool playTrack(const std::string &filePath);
  void pause();
  void resume();
  void stop();
  bool seek(double seconds);

  // Volume & DSP
  void setVolumeDb(int db);
  void setSliderVolume(double sliderValue, bool useHardwareVolume);
  void setBufferProfile(int profileIndex);
  int getBufferProfile() const { return m_bufferProfile.load(std::memory_order_relaxed); }
  void triggerWarmup(uint32_t durationMs);

  void setHogModeEnabled(bool enabled);
  void setAutoClockEnabled(bool enabled);
  void setRamLockEnabled(bool enabled);
  void setDitherEnabled(bool enabled);

  // Gapless
  bool prepareNextTrack(const std::string &filePath);
  void clearNextTrack();
  bool isTrackFinished() const { return m_trackFinished.load(std::memory_order_acquire); }
  bool consumeGaplessTransition();

  bool isPlaying() const { return m_isPlaying.load(std::memory_order_acquire); }
  double getCurrentTimeSeconds() const;
  double getTotalDurationSeconds() const;
  uint32_t getSampleRate() const { return m_sampleRate.load(std::memory_order_acquire); }
  uint32_t getBitDepth() const { return m_bitsPerSample.load(std::memory_order_acquire); }
  uint32_t getChannels() const { return m_channels.load(std::memory_order_acquire); }
  double getActualHardwareSampleRate() const { return m_actualHardwareSampleRate.load(std::memory_order_acquire); }
  std::string getHogModeStatusString() const;
  bool isStreamIntegerMode() const { return m_streamIsInteger.load(std::memory_order_acquire); }

  std::string getTelemetryJson();

private:
  static OSStatus renderCallback(
      void *inRefCon,
      AudioUnitRenderActionFlags *ioActionFlags,
      const AudioTimeStamp *inTimeStamp,
      UInt32 inBusNumber,
      UInt32 inNumberFrames,
      AudioBufferList *ioData);

  OSStatus handleRender(
      AudioUnitRenderActionFlags *ioActionFlags,
      const AudioTimeStamp *inTimeStamp,
      UInt32 inNumberFrames,
      AudioBufferList *ioData);

  bool setupAudioUnit(AudioDeviceID deviceId, Float64 sampleRate, UInt32 channels, UInt32 bitDepth);
  void teardownAudioUnit();

  bool setDeviceSampleRate(AudioDeviceID deviceId, Float64 targetRate);
  bool lockHardwarePhysicalStreamFormat(AudioDeviceID deviceId, Float64 targetRate, UInt32 channels, UInt32 preferredBitDepth);
  bool setHogMode(AudioDeviceID deviceId, bool enable);
  bool applyHardwareVolumeScalar(AudioDeviceID deviceId, double sliderValue, double& outResidualLinearGain);
  void promoteCurrentThreadToRealtime();

  void stopDecodeThread();
  void decodeThreadLoop(uint64_t generation);
  void applyRamLockState(bool shouldLock);

  AudioDeviceID m_activeDevice = kAudioObjectUnknown;
  AudioComponentInstance m_audioUnit = nullptr;
  pid_t m_hoggerPid = -1;

  std::atomic<bool> m_isPlaying{false};
  std::atomic<bool> m_isPaused{false};
  std::atomic<uint64_t> m_currentFrameIndex{0};
  std::atomic<uint64_t> m_decodedFrames{0};
  std::atomic<uint64_t> m_seekGeneration{0};

  // DSP & Gain
  std::atomic<double> m_targetGain{1.0};
  double m_currentGain{1.0};
  TsrossaDsp::SplitMix64Dither m_dither;
  std::atomic<bool> m_streamIsInteger{false};
  std::atomic<double> m_actualHardwareSampleRate{0.0};
  std::atomic<int> m_bufferProfile{1}; // 0=64ms, 1=160ms, 2=192ms
  std::atomic<uint32_t> m_warmupFramesRemaining{0};
  std::atomic<bool> m_ditherEnabled{true};

  std::atomic<bool> m_hogModeEnabled{true};
  std::atomic<bool> m_autoClockEnabled{true};
  std::atomic<bool> m_ramLockEnabled{true};

  std::atomic<uint64_t> m_totalFrames{0};
  std::atomic<uint32_t> m_sampleRate{0};
  std::atomic<uint32_t> m_channels{0};
  std::atomic<uint32_t> m_bitsPerSample{0};

  // 64 MB Sliding-Window Ring Buffer
  std::vector<int32_t> m_pcmBuffer;
  std::vector<int32_t> m_renderScratchBuffer;
  std::atomic<bool> m_machPrioritySet{false};
  bool m_isMemoryLocked = false;
  std::string m_currentFilePath;

  // Gapless Next Track State (64 MB pre-buffer for O(1) swap)
  std::vector<int32_t> m_pcmBufferNext;
  std::unique_ptr<MacAudioDecoder> m_activeDecoder;
  std::unique_ptr<MacAudioDecoder> m_nextDecoder;
  std::atomic<bool> m_nextTrackReady{false};
  std::atomic<uint32_t> m_nextSampleRate{0};
  std::atomic<uint32_t> m_nextChannels{0};
  std::atomic<uint32_t> m_nextBitsPerSample{0};
  std::atomic<uint64_t> m_nextTotalFrames{0};
  std::atomic<uint64_t> m_nextDecodedFrames{0};
  std::string m_nextFilePath;

  std::atomic<bool> m_trackFinished{false};
  std::atomic<bool> m_gaplessTransitioned{false};
  std::atomic<bool> m_needGaplessDecoderHandoff{false};
  std::atomic<uint64_t> m_prepareNextGen{0};
  std::mutex m_nextPrepMutex;

  // Background Streaming Thread
  std::thread m_decodeThread;
  std::atomic<bool> m_stopDecodeThread{false};
  std::mutex m_decoderMutex;
  std::condition_variable m_decodeCv;

  std::recursive_mutex m_engineMutex;
};
