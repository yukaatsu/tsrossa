#include "CoreAudioSink.h"

#include <sys/mman.h>
#include <sys/resource.h>
#include <unistd.h>
#include <sstream>
#include <algorithm>
#include <chrono>

CoreAudioSink::CoreAudioSink() = default;

CoreAudioSink::~CoreAudioSink() {
  shutdown();
}

bool CoreAudioSink::init() {
  std::lock_guard<std::recursive_mutex> lock(m_engineMutex);
  LOGI("CoreAudioSink: Initializing CoreAudio subsystem...");

  CFRunLoopRef runLoop = CFRunLoopGetMain();
  AudioObjectPropertyAddress runLoopAddr = {
      kAudioHardwarePropertyRunLoop,
      kAudioObjectPropertyScopeGlobal,
      kAudioObjectPropertyElementMain
  };
  AudioObjectSetPropertyData(
      kAudioObjectSystemObject, &runLoopAddr, 0, nullptr, sizeof(CFRunLoopRef), &runLoop);

  if (m_activeDevice == kAudioObjectUnknown) {
    AudioObjectPropertyAddress addr = {
        kAudioHardwarePropertyDefaultOutputDevice,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    UInt32 size = sizeof(AudioDeviceID);
    OSStatus status = AudioObjectGetPropertyData(
        kAudioObjectSystemObject, &addr, 0, nullptr, &size, &m_activeDevice);
    if (status != noErr || m_activeDevice == kAudioObjectUnknown) {
      auto devices = getAudioDevices();
      for (const auto &dev : devices) {
        if (dev.isDefault) {
          m_activeDevice = dev.id;
          break;
        }
      }
      if (m_activeDevice == kAudioObjectUnknown && !devices.empty()) {
        m_activeDevice = devices.front().id;
      }
    }
  }
  return true;
}

void CoreAudioSink::shutdown() {
  std::lock_guard<std::recursive_mutex> lock(m_engineMutex);
  stop();
  teardownAudioUnit();
  if (m_activeDevice != kAudioObjectUnknown) {
    setHogMode(m_activeDevice, false);
  }
}

std::vector<DeviceInfo> CoreAudioSink::getAudioDevices() {
  std::vector<DeviceInfo> result;

  AudioObjectPropertyAddress addr = {
      kAudioHardwarePropertyDevices,
      kAudioObjectPropertyScopeGlobal,
      kAudioObjectPropertyElementMain
  };
  UInt32 dataSize = 0;
  OSStatus status = AudioObjectGetPropertyDataSize(kAudioObjectSystemObject, &addr, 0, nullptr, &dataSize);
  if (status != noErr || dataSize == 0) {
    return result;
  }

  UInt32 deviceCount = dataSize / sizeof(AudioDeviceID);
  std::vector<AudioDeviceID> deviceIds(deviceCount);
  status = AudioObjectGetPropertyData(kAudioObjectSystemObject, &addr, 0, nullptr, &dataSize, deviceIds.data());
  if (status != noErr) {
    return result;
  }

  AudioDeviceID defaultOutputId = kAudioObjectUnknown;
  AudioObjectPropertyAddress defaultAddr = {
      kAudioHardwarePropertyDefaultOutputDevice,
      kAudioObjectPropertyScopeGlobal,
      kAudioObjectPropertyElementMain
  };
  UInt32 defaultSize = sizeof(AudioDeviceID);
  AudioObjectGetPropertyData(kAudioObjectSystemObject, &defaultAddr, 0, nullptr, &defaultSize, &defaultOutputId);

  for (AudioDeviceID devId : deviceIds) {
    AudioObjectPropertyAddress streamsAddr = {
        kAudioDevicePropertyStreams,
        kAudioDevicePropertyScopeOutput,
        kAudioObjectPropertyElementMain
    };
    UInt32 streamSize = 0;
    AudioObjectGetPropertyDataSize(devId, &streamsAddr, 0, nullptr, &streamSize);
    if (streamSize == 0) continue;

    DeviceInfo info{};
    info.id = devId;
    info.isDefault = (devId == defaultOutputId);

    CFStringRef nameStr = nullptr;
    UInt32 cfSize = sizeof(CFStringRef);
    AudioObjectPropertyAddress nameAddr = {
        kAudioObjectPropertyName,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    if (AudioObjectGetPropertyData(devId, &nameAddr, 0, nullptr, &cfSize, &nameStr) == noErr && nameStr) {
      char buf[256];
      if (CFStringGetCString(nameStr, buf, sizeof(buf), kCFStringEncodingUTF8)) {
        info.name = buf;
      }
      CFRelease(nameStr);
    }

    CFStringRef uidStr = nullptr;
    cfSize = sizeof(CFStringRef);
    AudioObjectPropertyAddress uidAddr = {
        kAudioDevicePropertyDeviceUID,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    if (AudioObjectGetPropertyData(devId, &uidAddr, 0, nullptr, &cfSize, &uidStr) == noErr && uidStr) {
      char buf[256];
      if (CFStringGetCString(uidStr, buf, sizeof(buf), kCFStringEncodingUTF8)) {
        info.uid = buf;
      }
      CFRelease(uidStr);
    }

    UInt32 transport = 0;
    UInt32 transSize = sizeof(UInt32);
    AudioObjectPropertyAddress transAddr = {
        kAudioDevicePropertyTransportType,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    if (AudioObjectGetPropertyData(devId, &transAddr, 0, nullptr, &transSize, &transport) == noErr) {
      info.isUsb = (transport == kAudioDeviceTransportTypeUSB);
    }

    Float64 nominalRate = 0.0;
    UInt32 rateSize = sizeof(Float64);
    AudioObjectPropertyAddress rateAddr = {
        kAudioDevicePropertyNominalSampleRate,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    if (AudioObjectGetPropertyData(devId, &rateAddr, 0, nullptr, &rateSize, &nominalRate) == noErr) {
      info.nominalSampleRate = nominalRate;
    }

    AudioObjectPropertyAddress rangesAddr = {
        kAudioDevicePropertyAvailableNominalSampleRates,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    UInt32 rangesSize = 0;
    if (AudioObjectGetPropertyDataSize(devId, &rangesAddr, 0, nullptr, &rangesSize) == noErr && rangesSize > 0) {
      UInt32 rangeCount = rangesSize / sizeof(AudioValueRange);
      std::vector<AudioValueRange> ranges(rangeCount);
      if (AudioObjectGetPropertyData(devId, &rangesAddr, 0, nullptr, &rangesSize, ranges.data()) == noErr) {
        for (const auto &r : ranges) {
          if (r.mMinimum == r.mMaximum) {
            info.supportedRates.push_back(r.mMinimum);
          } else {
            const double commonRates[] = {44100.0, 48000.0, 88200.0, 96000.0, 176400.0, 192000.0, 352800.0, 384000.0, 705600.0, 768000.0};
            for (double cr : commonRates) {
              if (cr >= r.mMinimum && cr <= r.mMaximum) {
                info.supportedRates.push_back(cr);
              }
            }
          }
        }
      }
    }
    std::sort(info.supportedRates.begin(), info.supportedRates.end());
    info.supportedRates.erase(std::unique(info.supportedRates.begin(), info.supportedRates.end()), info.supportedRates.end());

    result.push_back(info);
  }
  return result;
}

bool CoreAudioSink::selectDevice(AudioDeviceID deviceId) {
  std::lock_guard<std::recursive_mutex> lock(m_engineMutex);
  if (m_activeDevice != deviceId) {
    if (m_activeDevice != kAudioObjectUnknown) {
      setHogMode(m_activeDevice, false);
    }
    m_activeDevice = deviceId;
    if (m_isPlaying.load()) {
      Float64 sr = static_cast<Float64>(m_sampleRate.load());
      setupAudioUnit(m_activeDevice, sr, m_channels.load(), m_bitsPerSample.load());
      AudioOutputUnitStart(m_audioUnit);
    }
  }
  return true;
}

void CoreAudioSink::setVolumeDb(int db) {
  double linear = std::pow(10.0, static_cast<double>(db) / 20.0);
  m_targetGain.store(std::clamp(linear, 0.0, 1.0), std::memory_order_relaxed);
}

bool CoreAudioSink::applyHardwareVolumeScalar(AudioDeviceID deviceId, double sliderValue, double& outResidualLinearGain) {
  outResidualLinearGain = TsrossaDsp::StudioTaper::sliderToLinearGain(sliderValue);
  if (deviceId == kAudioObjectUnknown) return false;

  AudioObjectPropertyAddress volAddr = {
      kAudioHardwareServiceDeviceProperty_VirtualMainVolume,
      kAudioDevicePropertyScopeOutput,
      kAudioObjectPropertyElementMain
  };
  if (!AudioObjectHasProperty(deviceId, &volAddr)) return false;

  Boolean settable = false;
  if (AudioObjectIsPropertySettable(deviceId, &volAddr, &settable) != noErr || !settable) return false;

  Float32 scalar = static_cast<Float32>(std::clamp(sliderValue, 0.0, 1.0));
  if (AudioObjectSetPropertyData(deviceId, &volAddr, 0, nullptr, sizeof(Float32), &scalar) == noErr) {
    outResidualLinearGain = 1.0;
    return true;
  }
  return false;
}

void CoreAudioSink::setSliderVolume(double sliderValue, bool useHardwareVolume) {
  const double clamped = std::clamp(sliderValue, 0.0, 1.0);
  if (clamped >= 0.9999) {
    m_targetGain.store(1.0, std::memory_order_relaxed);
    return;
  }
  double residualGain = TsrossaDsp::StudioTaper::sliderToLinearGain(clamped);
  if (useHardwareVolume && m_activeDevice != kAudioObjectUnknown) {
    applyHardwareVolumeScalar(m_activeDevice, clamped, residualGain);
  }
  m_targetGain.store(residualGain, std::memory_order_relaxed);
}

void CoreAudioSink::setBufferProfile(int profileIndex) {
  m_bufferProfile.store(std::clamp(profileIndex, 0, 2), std::memory_order_relaxed);
  if (m_activeDevice != kAudioObjectUnknown) {
    UInt32 targetFrames = TsrossaDsp::BufferProfileHelper::computeTargetBufferFrames(
        m_bufferProfile.load(), m_actualHardwareSampleRate.load()
    );
    AudioObjectPropertyAddress bufAddr = {
        kAudioDevicePropertyBufferFrameSize,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    AudioObjectSetPropertyData(m_activeDevice, &bufAddr, 0, nullptr, sizeof(UInt32), &targetFrames);
  }
}

void CoreAudioSink::triggerWarmup(uint32_t durationMs) {
  const uint32_t sr = m_sampleRate.load() > 0 ? m_sampleRate.load() : 48000;
  const uint32_t frames = (sr * std::min(durationMs, 2000u)) / 1000u;
  m_warmupFramesRemaining.store(frames, std::memory_order_relaxed);
}

void CoreAudioSink::setHogModeEnabled(bool enabled) {
  m_hogModeEnabled.store(enabled);
  if (m_activeDevice != kAudioObjectUnknown) {
    setHogMode(m_activeDevice, enabled);
  }
}

void CoreAudioSink::setAutoClockEnabled(bool enabled) {
  m_autoClockEnabled.store(enabled);
}

void CoreAudioSink::setDitherEnabled(bool enabled) {
  m_ditherEnabled.store(enabled);
}

std::string CoreAudioSink::getHogModeStatusString() const {
  if (m_hoggerPid == getpid()) return "EXCLUSIVE_HOG";
  if (!m_hogModeEnabled.load()) return "SHARED_DISABLED";
  return "SHARED_BUILTIN_OR_FALLBACK";
}

bool CoreAudioSink::setHogMode(AudioDeviceID deviceId, bool enable) {
  if (deviceId == kAudioObjectUnknown) return false;

  UInt32 transport = 0;
  AudioObjectPropertyAddress transAddr = {
      kAudioDevicePropertyTransportType,
      kAudioObjectPropertyScopeGlobal,
      kAudioObjectPropertyElementMain
  };
  UInt32 transSize = sizeof(UInt32);
  AudioObjectGetPropertyData(deviceId, &transAddr, 0, nullptr, &transSize, &transport);

  if (transport == kAudioDeviceTransportTypeBuiltIn) {
    m_hoggerPid = -1;
    return true;
  }

  AudioObjectPropertyAddress addr = {
      kAudioDevicePropertyHogMode,
      kAudioObjectPropertyScopeGlobal,
      kAudioObjectPropertyElementMain
  };

  pid_t currentHogPid = -1;
  UInt32 pidSize = sizeof(pid_t);
  AudioObjectGetPropertyData(deviceId, &addr, 0, nullptr, &pidSize, &currentHogPid);

  if (enable && currentHogPid == getpid()) {
    m_hoggerPid = getpid();
    return true;
  }
  if (!enable && currentHogPid != getpid()) {
    m_hoggerPid = -1;
    return true;
  }

  pid_t hogPid = enable ? getpid() : -1;
  OSStatus status = AudioObjectSetPropertyData(deviceId, &addr, 0, nullptr, sizeof(pid_t), &hogPid);
  if (status == noErr) {
    m_hoggerPid = hogPid;
    return true;
  }
  m_hoggerPid = -1;
  return false;
}

bool CoreAudioSink::setDeviceSampleRate(AudioDeviceID deviceId, Float64 targetRate) {
  if (deviceId == kAudioObjectUnknown) return false;

  std::vector<double> supportedRates;
  auto devices = getAudioDevices();
  for (const auto& dev : devices) {
    if (dev.id == deviceId) {
      supportedRates = dev.supportedRates;
      break;
    }
  }

  const double targetNominalRate = TsrossaDsp::SampleRateMatcher::selectBestSupportedRate(
      static_cast<uint32_t>(std::llround(targetRate)), supportedRates
  );

  AudioObjectPropertyAddress rateAddr = {
      kAudioDevicePropertyNominalSampleRate,
      kAudioObjectPropertyScopeGlobal,
      kAudioObjectPropertyElementMain
  };

  Float64 currentRate = 0.0;
  UInt32 size = sizeof(Float64);
  AudioObjectGetPropertyData(deviceId, &rateAddr, 0, nullptr, &size, &currentRate);

  if (std::abs(currentRate - targetNominalRate) > 0.5) {
    Float64 desiredRate = targetNominalRate;
    OSStatus status = AudioObjectSetPropertyData(deviceId, &rateAddr, 0, nullptr, sizeof(Float64), &desiredRate);
    if (status == noErr) {
      for (int waitIter = 0; waitIter < 20; ++waitIter) {
        usleep(10000);
        Float64 checkRate = 0.0;
        UInt32 checkSize = sizeof(Float64);
        if (AudioObjectGetPropertyData(deviceId, &rateAddr, 0, nullptr, &checkSize, &checkRate) == noErr) {
          if (std::abs(checkRate - targetNominalRate) < 0.5) {
            currentRate = checkRate;
            break;
          }
        }
      }
    }
  }

  Float64 verifiedRate = 0.0;
  UInt32 verifiedSize = sizeof(Float64);
  if (AudioObjectGetPropertyData(deviceId, &rateAddr, 0, nullptr, &verifiedSize, &verifiedRate) == noErr && verifiedRate > 0.0) {
    m_actualHardwareSampleRate.store(verifiedRate);
  } else {
    m_actualHardwareSampleRate.store(targetNominalRate);
  }

  UInt32 targetBufferFrames = TsrossaDsp::BufferProfileHelper::computeTargetBufferFrames(
      m_bufferProfile.load(), m_actualHardwareSampleRate.load()
  );
  AudioObjectPropertyAddress bufAddr = {
      kAudioDevicePropertyBufferFrameSize,
      kAudioObjectPropertyScopeGlobal,
      kAudioObjectPropertyElementMain
  };
  AudioObjectSetPropertyData(deviceId, &bufAddr, 0, nullptr, sizeof(UInt32), &targetBufferFrames);

  return std::abs(m_actualHardwareSampleRate.load() - targetRate) < 0.5;
}

void CoreAudioSink::applyRamLockState(bool shouldLock) {
  if (m_pcmBuffer.empty()) return;
  const size_t byteSize = m_pcmBuffer.size() * sizeof(int32_t);

  if (shouldLock && !m_isMemoryLocked) {
    struct rlimit rl{};
    if (getrlimit(RLIMIT_MEMLOCK, &rl) == 0) {
      if (rl.rlim_cur != RLIM_INFINITY && rl.rlim_cur < byteSize) {
        return;
      }
    }
    if (mlock(m_pcmBuffer.data(), byteSize) == 0) {
      m_isMemoryLocked = true;
    }
  } else if (!shouldLock && m_isMemoryLocked) {
    munlock(m_pcmBuffer.data(), byteSize);
    m_isMemoryLocked = false;
  }
}

void CoreAudioSink::setRamLockEnabled(bool enabled) {
  m_ramLockEnabled.store(enabled);
  applyRamLockState(enabled);
}

void CoreAudioSink::stopDecodeThread() {
  m_stopDecodeThread.store(true, std::memory_order_release);
  m_decodeCv.notify_all();
  if (m_decodeThread.joinable()) {
    m_decodeThread.join();
  }
}

void CoreAudioSink::decodeThreadLoop(uint64_t generation) {
  pthread_setname_np("TsrossaDecode");
  const uint32_t channels = m_channels.load(std::memory_order_acquire);
  const uint64_t totalFrames = m_totalFrames.load(std::memory_order_acquire);
  if (channels == 0 || totalFrames == 0) return;

  const uint64_t ringCapacityFrames = MAX_RING_BUFFER_SAMPLES / channels;
  const uint64_t safetyMarginFrames = std::min<uint64_t>(ringCapacityFrames / 4, m_sampleRate.load() * 4);
  const uint64_t maxLeadFrames = ringCapacityFrames - safetyMarginFrames;
  constexpr uint64_t kChunkFrames = 16384;
  std::vector<int32_t> chunkBuf(kChunkFrames * channels);

  while (!m_stopDecodeThread.load(std::memory_order_acquire)) {
    if (m_seekGeneration.load(std::memory_order_acquire) != generation) break;

    uint64_t decoded = m_decodedFrames.load(std::memory_order_acquire);
    if (decoded >= totalFrames) break;

    uint64_t playhead = m_currentFrameIndex.load(std::memory_order_acquire);
    if (decoded >= playhead && (decoded - playhead) >= maxLeadFrames) {
      std::unique_lock<std::mutex> lk(m_decoderMutex);
      m_decodeCv.wait_for(lk, std::chrono::milliseconds(15));
      continue;
    }

    uint64_t toRead = std::min<uint64_t>(kChunkFrames, totalFrames - decoded);
    if (decoded >= playhead) {
      toRead = std::min<uint64_t>(toRead, maxLeadFrames - (decoded - playhead));
    }
    if (toRead == 0) {
      std::unique_lock<std::mutex> lk(m_decoderMutex);
      m_decodeCv.wait_for(lk, std::chrono::milliseconds(10));
      continue;
    }

    uint64_t framesRead = 0;
    {
      std::lock_guard<std::mutex> lk(m_decoderMutex);
      if (m_activeDecoder) {
        framesRead = m_activeDecoder->readFramesChunk(chunkBuf.data(), toRead);
      }
    }
    if (framesRead == 0) break;

    for (uint64_t f = 0; f < framesRead; ++f) {
      const uint64_t ringFrameIdx = (decoded + f) % ringCapacityFrames;
      std::memcpy(
          &m_pcmBuffer[ringFrameIdx * channels],
          &chunkBuf[f * channels],
          channels * sizeof(int32_t)
      );
    }
    m_decodedFrames.store(decoded + framesRead, std::memory_order_release);
  }
}

bool CoreAudioSink::setupAudioUnit(AudioDeviceID deviceId, Float64 sampleRate, UInt32 channels, UInt32 bitDepth) {
  teardownAudioUnit();

  AudioComponentDescription desc;
  memset(&desc, 0, sizeof(desc));
  desc.componentType = kAudioUnitType_Output;
  desc.componentSubType = kAudioUnitSubType_HALOutput;
  desc.componentManufacturer = kAudioUnitManufacturer_Apple;

  AudioComponent comp = AudioComponentFindNext(nullptr, &desc);
  if (!comp) return false;

  OSStatus status = AudioComponentInstanceNew(comp, &m_audioUnit);
  if (status != noErr || !m_audioUnit) return false;

  UInt32 enableIO = 1;
  AudioUnitSetProperty(m_audioUnit, kAudioOutputUnitProperty_EnableIO, kAudioUnitScope_Output, 0, &enableIO, sizeof(enableIO));
  enableIO = 0;
  AudioUnitSetProperty(m_audioUnit, kAudioOutputUnitProperty_EnableIO, kAudioUnitScope_Input, 1, &enableIO, sizeof(enableIO));

  status = AudioUnitSetProperty(m_audioUnit, kAudioOutputUnitProperty_CurrentDevice, kAudioUnitScope_Global, 0, &deviceId, sizeof(deviceId));
  if (status != noErr) {
    teardownAudioUnit();
    return false;
  }

  // Dual-Path stream format setup:
  // Try integer format first if requested and bit-perfect unity gain
  m_streamIsInteger.store(false);
  AudioStreamBasicDescription format;
  memset(&format, 0, sizeof(format));
  format.mSampleRate = sampleRate;
  format.mFormatID = kAudioFormatLinearPCM;
  format.mChannelsPerFrame = channels;
  format.mFramesPerPacket = 1;

  if (m_hoggerPid == getpid() && m_targetGain.load() >= 0.99999) {
    format.mFormatFlags = kAudioFormatFlagIsSignedInteger | kAudioFormatFlagIsPacked;
    format.mBitsPerChannel = 32;
    format.mBytesPerFrame = (32 / 8) * channels;
    format.mBytesPerPacket = format.mBytesPerFrame;
    status = AudioUnitSetProperty(m_audioUnit, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Input, 0, &format, sizeof(format));
    if (status == noErr) {
      m_streamIsInteger.store(true);
    }
  }

  if (!m_streamIsInteger.load()) {
    format.mFormatFlags = kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked;
    format.mBitsPerChannel = 32;
    format.mBytesPerFrame = (32 / 8) * channels;
    format.mBytesPerPacket = format.mBytesPerFrame;
    status = AudioUnitSetProperty(m_audioUnit, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Input, 0, &format, sizeof(format));
    if (status != noErr) {
      teardownAudioUnit();
      return false;
    }
  }

  AURenderCallbackStruct callback;
  callback.inputProc = renderCallback;
  callback.inputProcRefCon = this;
  status = AudioUnitSetProperty(m_audioUnit, kAudioUnitProperty_SetRenderCallback, kAudioUnitScope_Input, 0, &callback, sizeof(callback));
  if (status != noErr) {
    teardownAudioUnit();
    return false;
  }

  status = AudioUnitInitialize(m_audioUnit);
  if (status != noErr) {
    teardownAudioUnit();
    return false;
  }

  return true;
}

void CoreAudioSink::teardownAudioUnit() {
  if (m_audioUnit) {
    m_isPlaying.store(false);
    AudioOutputUnitStop(m_audioUnit);
    AURenderCallbackStruct emptyCallback = {nullptr, nullptr};
    AudioUnitSetProperty(m_audioUnit, kAudioUnitProperty_SetRenderCallback, kAudioUnitScope_Input, 0, &emptyCallback, sizeof(emptyCallback));
    AudioUnitUninitialize(m_audioUnit);
    AudioComponentInstanceDispose(m_audioUnit);
    m_audioUnit = nullptr;
  }
}

bool CoreAudioSink::playTrack(const std::string &filePath) {
  std::lock_guard<std::recursive_mutex> lock(m_engineMutex);
  LOGI("CoreAudioSink: Loading track '%s'...", filePath.c_str());

  stopDecodeThread();
  teardownAudioUnit();

  auto decoder = std::make_unique<MacAudioDecoder>();
  if (!decoder->openStream(filePath)) {
    LOGE("CoreAudioSink: Failed to open '%s': %s", filePath.c_str(), decoder->lastOpenError.c_str());
    return false;
  }

  const uint32_t sr = decoder->getSampleRate();
  const uint32_t ch = decoder->getChannels();
  const uint32_t bits = decoder->getBitsPerSample();
  const uint64_t total = decoder->getTotalFrames();

  if (total == 0 || sr == 0 || ch == 0) return false;

  m_sampleRate.store(sr, std::memory_order_relaxed);
  m_channels.store(ch, std::memory_order_relaxed);
  m_bitsPerSample.store(bits, std::memory_order_relaxed);
  m_totalFrames.store(total, std::memory_order_relaxed);
  m_currentFilePath = filePath;

  if (m_pcmBuffer.size() != MAX_RING_BUFFER_SAMPLES) {
    m_pcmBuffer.assign(MAX_RING_BUFFER_SAMPLES, 0);
  }

  // Pre-decode first 5 seconds synchronously for instant playback (< 50ms startup)
  const uint64_t initialFrames = std::min<uint64_t>(total, static_cast<uint64_t>(sr) * 5);
  uint64_t framesRead = 0;
  {
    std::lock_guard<std::mutex> lk(m_decoderMutex);
    framesRead = decoder->readFramesChunk(m_pcmBuffer.data(), initialFrames);
    m_activeDecoder = std::move(decoder);
  }

  m_decodedFrames.store(framesRead, std::memory_order_release);
  m_currentFrameIndex.store(0, std::memory_order_release);
  m_trackFinished.store(false, std::memory_order_release);
  m_gaplessTransitioned.store(false, std::memory_order_release);

  applyRamLockState(m_ramLockEnabled.load());

  if (m_activeDevice == kAudioObjectUnknown) {
    init();
  }
  if (m_autoClockEnabled.load()) {
    setDeviceSampleRate(m_activeDevice, static_cast<Float64>(sr));
  }
  setHogMode(m_activeDevice, m_hogModeEnabled.load());

  if (!setupAudioUnit(m_activeDevice, static_cast<Float64>(sr), ch, bits)) {
    return false;
  }

  m_isPaused.store(false);
  m_isPlaying.store(true);

  OSStatus status = -1;
  for (int retry = 0; retry < 5; ++retry) {
    status = AudioOutputUnitStart(m_audioUnit);
    if (status == noErr) break;
    usleep(30000);
  }

  if (status != noErr) {
    m_isPlaying.store(false);
    return false;
  }

  // Start background decode thread for remainder of track
  const uint64_t gen = m_seekGeneration.fetch_add(1, std::memory_order_acq_rel) + 1;
  m_stopDecodeThread.store(false, std::memory_order_release);
  if (m_decodedFrames.load() < total) {
    m_decodeThread = std::thread(&CoreAudioSink::decodeThreadLoop, this, gen);
  }

  return true;
}

void CoreAudioSink::pause() {
  m_isPaused.store(true);
}

void CoreAudioSink::resume() {
  m_isPaused.store(false);
}

void CoreAudioSink::stop() {
  stopDecodeThread();
  m_isPlaying.store(false);
  m_isPaused.store(false);
  m_currentFrameIndex.store(0);
  m_decodedFrames.store(0);
  m_trackFinished.store(false);

  if (m_audioUnit) {
    AudioOutputUnitStop(m_audioUnit);
  }

  if (m_isMemoryLocked && !m_pcmBuffer.empty()) {
    munlock(m_pcmBuffer.data(), m_pcmBuffer.size() * sizeof(int32_t));
    m_isMemoryLocked = false;
  }
}

bool CoreAudioSink::seek(double seconds) {
  const uint32_t sr = m_sampleRate.load(std::memory_order_acquire);
  const uint32_t ch = m_channels.load(std::memory_order_acquire);
  const uint64_t total = m_totalFrames.load(std::memory_order_acquire);
  if (sr == 0 || ch == 0 || total == 0) return false;

  const double clampedSec = std::max(0.0, seconds);
  uint64_t targetFrame = static_cast<uint64_t>(clampedSec * sr);
  const uint64_t guardFrames = (sr * 250) / 1000; // 250ms guard
  const uint64_t maxSafeFrame = (total > guardFrames) ? (total - guardFrames) : 0;
  targetFrame = std::min(targetFrame, maxSafeFrame);

  stopDecodeThread();
  const uint64_t newGen = m_seekGeneration.fetch_add(1, std::memory_order_acq_rel) + 1;

  {
    std::lock_guard<std::mutex> lk(m_decoderMutex);
    if (m_activeDecoder && m_activeDecoder->seekToFrame(targetFrame)) {
      const uint64_t ringCapacityFrames = MAX_RING_BUFFER_SAMPLES / ch;
      const uint64_t preloadFrames = std::min<uint64_t>(total - targetFrame, static_cast<uint64_t>(sr) * 2);
      std::vector<int32_t> temp(preloadFrames * ch);
      const uint64_t got = m_activeDecoder->readFramesChunk(temp.data(), preloadFrames);
      for (uint64_t f = 0; f < got; ++f) {
        const uint64_t ringIdx = (targetFrame + f) % ringCapacityFrames;
        std::memcpy(&m_pcmBuffer[ringIdx * ch], &temp[f * ch], ch * sizeof(int32_t));
      }
      m_decodedFrames.store(targetFrame + got, std::memory_order_release);
      m_currentFrameIndex.store(targetFrame, std::memory_order_release);
      m_trackFinished.store(false, std::memory_order_release);
    }
  }

  if (m_decodedFrames.load(std::memory_order_acquire) < total) {
    m_stopDecodeThread.store(false, std::memory_order_release);
    m_decodeThread = std::thread(&CoreAudioSink::decodeThreadLoop, this, newGen);
  }
  return true;
}

bool CoreAudioSink::prepareNextTrack(const std::string& filePath) {
  std::lock_guard<std::mutex> lk(m_decoderMutex);
  m_nextTrackReady.store(false, std::memory_order_release);

  auto nextDec = std::make_unique<MacAudioDecoder>();
  if (!nextDec->openStream(filePath)) {
    return false;
  }

  const uint32_t ch = nextDec->getChannels();
  const uint32_t sr = nextDec->getSampleRate();
  const uint64_t total = nextDec->getTotalFrames();
  if (ch == 0 || sr == 0 || total == 0) return false;

  if (m_pcmBufferNext.size() < MAX_NEXT_BUFFER_SAMPLES) {
    m_pcmBufferNext.resize(MAX_NEXT_BUFFER_SAMPLES, 0);
  }

  const uint64_t maxPrebufferFrames = MAX_NEXT_BUFFER_SAMPLES / ch;
  const uint64_t framesToPreload = std::min<uint64_t>(total, maxPrebufferFrames);
  const uint64_t readFrames = nextDec->readFramesChunk(m_pcmBufferNext.data(), framesToPreload);
  if (readFrames == 0) return false;

  m_nextFilePath = filePath;
  m_nextSampleRate.store(sr, std::memory_order_relaxed);
  m_nextChannels.store(ch, std::memory_order_relaxed);
  m_nextBitsPerSample.store(nextDec->getBitsPerSample(), std::memory_order_relaxed);
  m_nextTotalFrames.store(total, std::memory_order_relaxed);
  m_nextDecodedFrames.store(readFrames, std::memory_order_relaxed);
  m_nextDecoder = std::move(nextDec);
  m_nextTrackReady.store(true, std::memory_order_release);
  return true;
}

void CoreAudioSink::clearNextTrack() {
  std::lock_guard<std::mutex> lk(m_decoderMutex);
  m_nextTrackReady.store(false, std::memory_order_release);
  m_nextDecoder.reset();
  m_nextFilePath.clear();
}

bool CoreAudioSink::consumeGaplessTransition() {
  return m_gaplessTransitioned.exchange(false, std::memory_order_acq_rel);
}

double CoreAudioSink::getCurrentTimeSeconds() const {
  const uint32_t sr = m_sampleRate.load(std::memory_order_acquire);
  if (sr == 0) return 0.0;
  return static_cast<double>(m_currentFrameIndex.load(std::memory_order_acquire)) / static_cast<double>(sr);
}

double CoreAudioSink::getTotalDurationSeconds() const {
  const uint32_t sr = m_sampleRate.load(std::memory_order_acquire);
  if (sr == 0) return 0.0;
  return static_cast<double>(m_totalFrames.load(std::memory_order_acquire)) / static_cast<double>(sr);
}

OSStatus CoreAudioSink::renderCallback(
    void *inRefCon,
    AudioUnitRenderActionFlags *ioActionFlags,
    const AudioTimeStamp *inTimeStamp,
    UInt32 inBusNumber,
    UInt32 inNumberFrames,
    AudioBufferList *ioData) {
  CoreAudioSink *sink = static_cast<CoreAudioSink *>(inRefCon);
  return sink->handleRender(ioActionFlags, inTimeStamp, inNumberFrames, ioData);
}

OSStatus CoreAudioSink::handleRender(
    AudioUnitRenderActionFlags *ioActionFlags,
    const AudioTimeStamp *inTimeStamp,
    UInt32 inNumberFrames,
    AudioBufferList *ioData) {
  if (!ioData || ioData->mNumberBuffers == 0) return noErr;

  const UInt32 channels = m_channels.load(std::memory_order_acquire);
  if (channels == 0) return noErr;

  const uint64_t ringCapacityFrames = MAX_RING_BUFFER_SAMPLES / channels;
  uint8_t* outBytes = static_cast<uint8_t*>(ioData->mBuffers[0].mData);
  const size_t bytesPerSample = sizeof(int32_t); // 4 bytes for both int32 and float32

  if (m_isPaused.load(std::memory_order_acquire) || !m_isPlaying.load(std::memory_order_acquire)) {
    std::memset(outBytes, 0, inNumberFrames * channels * bytesPerSample);
    return noErr;
  }

  // Handle DAC Warm-up frames
  uint32_t warmup = m_warmupFramesRemaining.load(std::memory_order_relaxed);
  if (warmup > 0) {
    const uint32_t burnFrames = std::min(warmup, inNumberFrames);
    m_warmupFramesRemaining.fetch_sub(burnFrames, std::memory_order_relaxed);
  }

  uint64_t currentFrame = m_currentFrameIndex.load(std::memory_order_acquire);
  const uint64_t totalFrames = m_totalFrames.load(std::memory_order_acquire);
  uint64_t framesAvailable = (totalFrames > currentFrame) ? (totalFrames - currentFrame) : 0;
  UInt32 framesToCopy = std::min(static_cast<UInt32>(framesAvailable), inNumberFrames);

  std::vector<int32_t> tempSrc(inNumberFrames * channels, 0);

  // Copy samples from circular ring buffer
  for (UInt32 f = 0; f < framesToCopy; ++f) {
    const uint64_t ringIdx = (currentFrame + f) % ringCapacityFrames;
    std::memcpy(&tempSrc[f * channels], &m_pcmBuffer[ringIdx * channels], channels * sizeof(int32_t));
  }

  // Check for seamless gapless transition if track reaches EOF
  if (framesToCopy < inNumberFrames && m_nextTrackReady.load(std::memory_order_acquire)) {
    if (m_nextSampleRate.load() == m_sampleRate.load() && m_nextChannels.load() == channels) {
      const UInt32 gaplessFramesNeeded = inNumberFrames - framesToCopy;
      const uint64_t nextTotal = m_nextTotalFrames.load();
      const uint64_t nextDecoded = m_nextDecodedFrames.load();
      const UInt32 gaplessFramesToCopy = std::min(gaplessFramesNeeded, static_cast<UInt32>(nextDecoded));

      // Copy from next track pre-buffer into the tail of tempSrc
      for (UInt32 f = 0; f < gaplessFramesToCopy; ++f) {
        std::memcpy(&tempSrc[(framesToCopy + f) * channels], &m_pcmBufferNext[f * channels], channels * sizeof(int32_t));
      }

      // Promote next track to active track
      std::memcpy(m_pcmBuffer.data(), m_pcmBufferNext.data(), nextDecoded * channels * sizeof(int32_t));
      m_totalFrames.store(nextTotal, std::memory_order_release);
      m_decodedFrames.store(nextDecoded, std::memory_order_release);
      m_activeDecoder = std::move(m_nextDecoder);
      m_currentFilePath = m_nextFilePath;
      m_nextTrackReady.store(false, std::memory_order_release);
      m_gaplessTransitioned.store(true, std::memory_order_release);

      m_currentFrameIndex.store(gaplessFramesToCopy, std::memory_order_release);
      framesToCopy += gaplessFramesToCopy;
    }
  }

  // Apply Dual-Path DSP & Output
  const double targetGain = m_targetGain.load(std::memory_order_relaxed);
  const bool ditherEnabled = m_ditherEnabled.load(std::memory_order_relaxed);

  if (m_streamIsInteger.load(std::memory_order_relaxed)) {
    int32_t* outInt = reinterpret_cast<int32_t*>(outBytes);
    TsrossaDsp::SampleProcessor::processInt32Buffer(
        tempSrc.data(), outInt, framesToCopy, channels,
        m_currentGain, targetGain, ditherEnabled, m_dither
    );
    if (framesToCopy < inNumberFrames) {
      std::memset(outInt + (framesToCopy * channels), 0, (inNumberFrames - framesToCopy) * channels * sizeof(int32_t));
    }
  } else {
    float* outFloat = reinterpret_cast<float*>(outBytes);
    TsrossaDsp::SampleProcessor::processFloat32Buffer(
        tempSrc.data(), outFloat, framesToCopy, channels,
        m_currentGain, targetGain, ditherEnabled, m_dither
    );
    if (framesToCopy < inNumberFrames) {
      std::memset(outFloat + (framesToCopy * channels), 0, (inNumberFrames - framesToCopy) * channels * sizeof(float));
    }
  }

  if (framesToCopy < inNumberFrames && !m_gaplessTransitioned.load()) {
    m_trackFinished.store(true, std::memory_order_release);
    m_isPlaying.store(false, std::memory_order_release);
  }

  // CAS advancement of playback head to prevent race with seekTo
  m_currentFrameIndex.compare_exchange_strong(currentFrame, currentFrame + framesToCopy);
  m_decodeCv.notify_one();
  return noErr;
}

std::string CoreAudioSink::getTelemetryJson() {
  const uint32_t sr = m_sampleRate.load();
  const uint32_t ch = m_channels.load();
  const uint64_t cur = m_currentFrameIndex.load();
  const uint64_t tot = m_totalFrames.load();
  const uint64_t dec = m_decodedFrames.load();

  double fillPct = 0.0;
  if (ch > 0) {
    const uint64_t cap = MAX_RING_BUFFER_SAMPLES / ch;
    if (dec > cur) {
      fillPct = std::min(100.0, (static_cast<double>(dec - cur) / static_cast<double>(cap)) * 100.0);
    }
  }

  std::stringstream ss;
  ss << "{\n";
  ss << "  \"activeDeviceId\": " << m_activeDevice << ",\n";
  ss << "  \"sampleRate\": " << sr << ",\n";
  ss << "  \"actualHardwareSampleRate\": " << m_actualHardwareSampleRate.load() << ",\n";
  ss << "  \"bitsPerSample\": " << m_bitsPerSample.load() << ",\n";
  ss << "  \"channels\": " << ch << ",\n";
  ss << "  \"streamIsInteger\": " << (m_streamIsInteger.load() ? "true" : "false") << ",\n";
  ss << "  \"hogModeStatus\": \"" << getHogModeStatusString() << "\",\n";
  ss << "  \"hoggerPid\": " << m_hoggerPid << ",\n";
  ss << "  \"bufferProfileIndex\": " << m_bufferProfile.load() << ",\n";
  ss << "  \"ditherEnabled\": " << (m_ditherEnabled.load() ? "true" : "false") << ",\n";
  ss << "  \"isMemoryLocked\": " << (m_isMemoryLocked ? "true" : "false") << ",\n";
  ss << "  \"ringBufferFillPercent\": " << fillPct << ",\n";
  ss << "  \"gaplessNextReady\": " << (m_nextTrackReady.load() ? "true" : "false") << ",\n";
  ss << "  \"isPlaying\": " << (m_isPlaying.load() ? "true" : "false") << ",\n";
  ss << "  \"isPaused\": " << (m_isPaused.load() ? "true" : "false") << ",\n";
  ss << "  \"currentSeconds\": " << getCurrentTimeSeconds() << ",\n";
  ss << "  \"totalSeconds\": " << getTotalDurationSeconds() << ",\n";
  ss << "  \"filePath\": \"" << json_escape(m_currentFilePath) << "\"\n";
  ss << "}";
  return ss.str();
}
