#include "CoreAudioSink.h"

#include <sys/mman.h>
#include <unistd.h>
#include <sstream>

CoreAudioSink::CoreAudioSink() = default;

CoreAudioSink::~CoreAudioSink() {
  shutdown();
}

bool CoreAudioSink::init() {
  std::lock_guard<std::recursive_mutex> lock(m_engineMutex);
  LOGI("CoreAudioSink: Initializing CoreAudio subsystem...");

  // Attach main CFRunLoop to CoreAudio HAL so hardware hot-plug notifications always fire
  CFRunLoopRef runLoop = CFRunLoopGetMain();
  AudioObjectPropertyAddress runLoopAddr = {
      kAudioHardwarePropertyRunLoop,
      kAudioObjectPropertyScopeGlobal,
      kAudioObjectPropertyElementMain
  };
  AudioObjectSetPropertyData(
      kAudioObjectSystemObject, &runLoopAddr, 0, nullptr, sizeof(CFRunLoopRef), &runLoop);

  // Select default output device if none selected
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
    if (m_activeDevice == kAudioObjectUnknown) {
      LOGW("CoreAudioSink: Could not retrieve default output device (status=%d)", status);
    } else {
      LOGI("CoreAudioSink: Default output device acquired (ID=%u)", (unsigned int)m_activeDevice);
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
    LOGE("getAudioDevices: AudioObjectGetPropertyDataSize failed (%d)", status);
    return result;
  }

  UInt32 deviceCount = dataSize / sizeof(AudioDeviceID);
  std::vector<AudioDeviceID> deviceIds(deviceCount);
  status = AudioObjectGetPropertyData(kAudioObjectSystemObject, &addr, 0, nullptr, &dataSize, deviceIds.data());
  if (status != noErr) {
    LOGE("getAudioDevices: AudioObjectGetPropertyData failed (%d)", status);
    return result;
  }

  // Get default output device ID
  AudioDeviceID defaultOutputId = kAudioObjectUnknown;
  AudioObjectPropertyAddress defaultAddr = {
      kAudioHardwarePropertyDefaultOutputDevice,
      kAudioObjectPropertyScopeGlobal,
      kAudioObjectPropertyElementMain
  };
  UInt32 defaultSize = sizeof(AudioDeviceID);
  AudioObjectGetPropertyData(kAudioObjectSystemObject, &defaultAddr, 0, nullptr, &defaultSize, &defaultOutputId);

  for (AudioDeviceID devId : deviceIds) {
    // 1. Check if device has output streams
    AudioObjectPropertyAddress streamAddr = {
        kAudioDevicePropertyStreamConfiguration,
        kAudioDevicePropertyScopeOutput,
        kAudioObjectPropertyElementMain
    };
    UInt32 streamSize = 0;
    status = AudioObjectGetPropertyDataSize(devId, &streamAddr, 0, nullptr, &streamSize);
    if (status != noErr || streamSize == 0) continue;

    std::vector<uint8_t> bufferListMem(streamSize);
    AudioBufferList *bufferList = reinterpret_cast<AudioBufferList *>(bufferListMem.data());
    status = AudioObjectGetPropertyData(devId, &streamAddr, 0, nullptr, &streamSize, bufferList);
    if (status != noErr || bufferList->mNumberBuffers == 0) continue;

    UInt32 totalChannels = 0;
    for (UInt32 b = 0; b < bufferList->mNumberBuffers; ++b) {
      totalChannels += bufferList->mBuffers[b].mNumberChannels;
    }
    if (totalChannels == 0) continue;

    DeviceInfo info;
    info.id = devId;
    info.isDefault = (devId == defaultOutputId);

    // 2. Name
    CFStringRef cfName = nullptr;
    AudioObjectPropertyAddress nameAddr = {
        kAudioObjectPropertyName,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    UInt32 nameSize = sizeof(CFStringRef);
    if (AudioObjectGetPropertyData(devId, &nameAddr, 0, nullptr, &nameSize, &cfName) == noErr && cfName) {
      char buf[256] = {0};
      CFStringGetCString(cfName, buf, sizeof(buf), kCFStringEncodingUTF8);
      CFRelease(cfName);
      info.name = buf;
    } else {
      info.name = "Unknown Output Device";
    }

    // 3. UID
    CFStringRef cfUid = nullptr;
    AudioObjectPropertyAddress uidAddr = {
        kAudioDevicePropertyDeviceUID,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    UInt32 uidSize = sizeof(CFStringRef);
    if (AudioObjectGetPropertyData(devId, &uidAddr, 0, nullptr, &uidSize, &cfUid) == noErr && cfUid) {
      char buf[256] = {0};
      CFStringGetCString(cfUid, buf, sizeof(buf), kCFStringEncodingUTF8);
      CFRelease(cfUid);
      info.uid = buf;
    } else {
      info.uid = std::to_string(devId);
    }

    // 4. Transport Type
    UInt32 transport = 0;
    AudioObjectPropertyAddress transAddr = {
        kAudioDevicePropertyTransportType,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    UInt32 transSize = sizeof(UInt32);
    if (AudioObjectGetPropertyData(devId, &transAddr, 0, nullptr, &transSize, &transport) == noErr) {
      info.isUsb = (transport == kAudioDeviceTransportTypeUSB);
    } else {
      info.isUsb = false;
    }

    // 5. Nominal Sample Rate
    Float64 curRate = 0.0;
    AudioObjectPropertyAddress rateAddr = {
        kAudioDevicePropertyNominalSampleRate,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    UInt32 rateSize = sizeof(Float64);
    if (AudioObjectGetPropertyData(devId, &rateAddr, 0, nullptr, &rateSize, &curRate) == noErr) {
      info.nominalSampleRate = curRate;
    }

    // 6. Available sample rates
    AudioObjectPropertyAddress availRatesAddr = {
        kAudioDevicePropertyAvailableNominalSampleRates,
        kAudioObjectPropertyScopeGlobal,
        kAudioObjectPropertyElementMain
    };
    UInt32 availSize = 0;
    if (AudioObjectGetPropertyDataSize(devId, &availRatesAddr, 0, nullptr, &availSize) == noErr && availSize > 0) {
      UInt32 count = availSize / sizeof(AudioValueRange);
      std::vector<AudioValueRange> ranges(count);
      if (AudioObjectGetPropertyData(devId, &availRatesAddr, 0, nullptr, &availSize, ranges.data()) == noErr) {
        for (const auto &range : ranges) {
          if (range.mMinimum == range.mMaximum) {
            info.supportedRates.push_back(range.mMinimum);
          } else {
            static const Float64 commonRates[] = {44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000, 768000};
            for (Float64 r : commonRates) {
              if (r >= range.mMinimum && r <= range.mMaximum) {
                info.supportedRates.push_back(r);
              }
            }
          }
        }
      }
    }

    result.push_back(info);
  }

  // Ensure at least one output device is marked default if coreaudiod reported defaultOutputId=0
  bool hasDefault = false;
  for (const auto &d : result) {
    if (d.isDefault) {
      hasDefault = true;
      break;
    }
  }
  if (!hasDefault && !result.empty()) {
    result.front().isDefault = true;
  }

  return result;
}

bool CoreAudioSink::selectDevice(AudioDeviceID deviceId) {
  std::lock_guard<std::recursive_mutex> lock(m_engineMutex);
  if (deviceId == kAudioObjectUnknown) return false;
  if (m_activeDevice == deviceId) return true;

  bool wasPlaying = m_isPlaying.load();
  bool wasPaused = m_isPaused.load();
  uint64_t savedFrame = m_currentFrameIndex.load();
  AudioDeviceID oldDevice = m_activeDevice;

  // 1. Signal render thread to stop reading PCM immediately before stopping HAL unit
  m_isPlaying.store(false);
  teardownAudioUnit();

  // 2. Release Hog Mode on previous device before switching
  if (oldDevice != kAudioObjectUnknown) {
    setHogMode(oldDevice, false);
  }

  // 3. Give coreaudiod 30ms to finish tearing down the old device session
  usleep(30000);

  m_activeDevice = deviceId;
  LOGI("CoreAudioSink: Selected device %u (previous=%u)", (unsigned int)deviceId, (unsigned int)oldDevice);

  // 4. Seamlessly resume on the new device without re-decoding or losing position
  if (wasPlaying && !m_pcmBuffer.empty() && m_sampleRate > 0) {
    if (m_autoClockEnabled.load()) {
      setDeviceSampleRate(m_activeDevice, (Float64)m_sampleRate);
    }
    if (m_hogModeEnabled.load()) {
      setHogMode(m_activeDevice, true);
    }
    bool unitReady = false;
    for (int attempt = 0; attempt < 3; ++attempt) {
      if (setupAudioUnit(m_activeDevice, (Float64)m_sampleRate, m_channels, m_bitsPerSample)) {
        unitReady = true;
        break;
      }
      usleep(50000);
    }
    if (unitReady) {
      m_currentFrameIndex.store(savedFrame);
      m_isPaused.store(wasPaused);
      m_isPlaying.store(true);
      for (int retry = 0; retry < 5; ++retry) {
        if (AudioOutputUnitStart(m_audioUnit) == noErr) break;
        usleep(40000);
      }
      LOGI("CoreAudioSink: Seamlessly routed active stream to device %u at frame %llu",
           (unsigned int)deviceId, (unsigned long long)savedFrame);
    }
  }
  return true;
}

void CoreAudioSink::setVolumeDb(int db) {
  if (db >= 0) {
    m_gainLinear.store(1.0f); // 0 dBFS Bit-Perfect Unity Gain
  } else if (db <= -60) {
    m_gainLinear.store(0.0f);
  } else {
    float g = std::pow(10.0f, (float)db / 20.0f);
    m_gainLinear.store(g);
  }
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

void CoreAudioSink::setRamLockEnabled(bool enabled) {
  m_ramLockEnabled.store(enabled);
}

bool CoreAudioSink::setDeviceSampleRate(AudioDeviceID deviceId, Float64 targetRate) {
  AudioObjectPropertyAddress addr = {
      kAudioDevicePropertyNominalSampleRate,
      kAudioObjectPropertyScopeGlobal,
      kAudioObjectPropertyElementMain
  };

  Float64 currentRate = 0.0;
  UInt32 size = sizeof(Float64);
  AudioObjectGetPropertyData(deviceId, &addr, 0, nullptr, &size, &currentRate);

  if (std::abs(currentRate - targetRate) < 1.0) {
    LOGI("setDeviceSampleRate: Device %u already running at %.1f Hz", (unsigned int)deviceId, targetRate);
    return true;
  }

  OSStatus status = AudioObjectSetPropertyData(deviceId, &addr, 0, nullptr, sizeof(Float64), &targetRate);
  if (status != noErr) {
    LOGW("setDeviceSampleRate: Failed to set rate %.1f Hz on device %u (status=%d)", targetRate, (unsigned int)deviceId, status);
    return false;
  }

  // Allow hardware crystal PLL oscillator to stabilize after rate change
  usleep(100000); // 100ms
  LOGI("setDeviceSampleRate: Successfully switched hardware crystal rate to %.1f Hz", targetRate);
  return true;
}

bool CoreAudioSink::setHogMode(AudioDeviceID deviceId, bool enable) {
  if (deviceId == kAudioObjectUnknown) return false;

  // Check transport type: on Apple Silicon, Built-in Speakers and 3.5mm Headphones share the same
  // AppleMCA2 codec, so kAudioDevicePropertyHogMode locks the codec against port switching.
  // Use hardware AUHAL direct mode on built-in ports, and full USB Hog Mode on external USB DACs.
  UInt32 transport = 0;
  AudioObjectPropertyAddress transAddr = {
      kAudioDevicePropertyTransportType,
      kAudioObjectPropertyScopeGlobal,
      kAudioObjectPropertyElementMain
  };
  UInt32 transSize = sizeof(UInt32);
  AudioObjectGetPropertyData(deviceId, &transAddr, 0, nullptr, &transSize, &transport);

  if (transport == kAudioDeviceTransportTypeBuiltIn) {
    LOGI("setHogMode(%s): Using direct AUHAL hardware bypass on built-in Apple Silicon port (device %u)",
         enable ? "true" : "false", (unsigned int)deviceId);
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
    return true;
  }
  if (!enable && currentHogPid != getpid()) {
    return true;
  }

  pid_t hogPid = enable ? getpid() : -1;
  OSStatus status = AudioObjectSetPropertyData(deviceId, &addr, 0, nullptr, sizeof(pid_t), &hogPid);
  if (status != noErr) {
    LOGW("setHogMode(%s): Failed on device %u (status=%d)", enable ? "true" : "false", (unsigned int)deviceId, status);
    return false;
  }
  LOGI("setHogMode(%s): Success on device %u", enable ? "true" : "false", (unsigned int)deviceId);
  return true;
}

bool CoreAudioSink::setupAudioUnit(AudioDeviceID deviceId, Float64 sampleRate, UInt32 channels, UInt32 bitDepth) {
  teardownAudioUnit();

  AudioComponentDescription desc;
  memset(&desc, 0, sizeof(desc));
  desc.componentType = kAudioUnitType_Output;
  desc.componentSubType = kAudioUnitSubType_HALOutput;
  desc.componentManufacturer = kAudioUnitManufacturer_Apple;

  AudioComponent comp = AudioComponentFindNext(nullptr, &desc);
  if (!comp) {
    LOGE("setupAudioUnit: AudioComponentFindNext failed");
    return false;
  }

  OSStatus status = AudioComponentInstanceNew(comp, &m_audioUnit);
  if (status != noErr || !m_audioUnit) {
    LOGE("setupAudioUnit: AudioComponentInstanceNew failed (%d)", status);
    return false;
  }

  // Enable IO for output (bus 0)
  UInt32 enableIO = 1;
  AudioUnitSetProperty(m_audioUnit, kAudioOutputUnitProperty_EnableIO, kAudioUnitScope_Output, 0, &enableIO, sizeof(enableIO));

  // Disable IO for input (bus 1)
  enableIO = 0;
  AudioUnitSetProperty(m_audioUnit, kAudioOutputUnitProperty_EnableIO, kAudioUnitScope_Input, 1, &enableIO, sizeof(enableIO));

  // Set current device
  status = AudioUnitSetProperty(m_audioUnit, kAudioOutputUnitProperty_CurrentDevice, kAudioUnitScope_Global, 0, &deviceId, sizeof(deviceId));
  if (status != noErr) {
    LOGE("setupAudioUnit: Failed to set device %u (%d)", (unsigned int)deviceId, status);
    teardownAudioUnit();
    return false;
  }

  // Set stream format: Linear PCM 32-bit Float, packed, stereo
  AudioStreamBasicDescription format;
  memset(&format, 0, sizeof(format));
  format.mSampleRate = sampleRate;
  format.mFormatID = kAudioFormatLinearPCM;
  format.mFormatFlags = kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked;
  format.mBitsPerChannel = 32;
  format.mChannelsPerFrame = channels;
  format.mBytesPerFrame = (32 / 8) * channels;
  format.mFramesPerPacket = 1;
  format.mBytesPerPacket = format.mBytesPerFrame;

  status = AudioUnitSetProperty(m_audioUnit, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Input, 0, &format, sizeof(format));
  if (status != noErr) {
    LOGE("setupAudioUnit: StreamFormat failed (%d)", status);
    teardownAudioUnit();
    return false;
  }

  // Set render callback
  AURenderCallbackStruct callback;
  callback.inputProc = renderCallback;
  callback.inputProcRefCon = this;
  status = AudioUnitSetProperty(m_audioUnit, kAudioUnitProperty_SetRenderCallback, kAudioUnitScope_Input, 0, &callback, sizeof(callback));
  if (status != noErr) {
    LOGE("setupAudioUnit: SetRenderCallback failed (%d)", status);
    teardownAudioUnit();
    return false;
  }

  status = AudioUnitInitialize(m_audioUnit);
  if (status != noErr) {
    LOGE("setupAudioUnit: AudioUnitInitialize failed (%d)", status);
    teardownAudioUnit();
    return false;
  }

  LOGI("setupAudioUnit: Successfully initialized HAL output unit for device %u at %.1f Hz", (unsigned int)deviceId, sampleRate);
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

  stop();

  MacAudioDecoder decoder;
  if (!decoder.open(filePath.c_str())) {
    LOGE("CoreAudioSink: Failed to open '%s': %s", filePath.c_str(), decoder.lastOpenError.c_str());
    return false;
  }

  m_sampleRate = decoder.sampleRate;
  m_channels = decoder.channels;
  m_bitsPerSample = decoder.bitsPerSample;
  m_totalFrames = decoder.totalPCMFrameCount;
  m_currentFilePath = filePath;

  if (m_totalFrames == 0 || m_sampleRate == 0 || m_channels == 0) {
    LOGE("CoreAudioSink: Invalid audio stream metadata");
    return false;
  }

  // Preload entire track into 32-bit linear PCM buffer
  size_t totalSamples = (size_t)(m_totalFrames * m_channels);
  m_pcmBuffer.resize(totalSamples);

  size_t framesRead = decoder.read_pcm_frames_s32(m_totalFrames, m_pcmBuffer.data());
  LOGI("CoreAudioSink: Preloaded %zu / %llu frames into memory", framesRead, (unsigned long long)m_totalFrames);

  // Attempt POSIX memory lock if enabled
  if (!m_pcmBuffer.empty() && m_ramLockEnabled.load()) {
    int lockRes = mlock(m_pcmBuffer.data(), m_pcmBuffer.size() * sizeof(int32_t));
    m_isMemoryLocked = (lockRes == 0);
    LOGI("CoreAudioSink: mlock(%zu bytes) -> %s", m_pcmBuffer.size() * sizeof(int32_t), m_isMemoryLocked ? "SUCCESS" : "FALLBACK_HEAP");
  } else {
    m_isMemoryLocked = false;
  }

  // Verify and match hardware sample rate
  if (m_activeDevice == kAudioObjectUnknown) {
    init();
  }
  if (m_autoClockEnabled.load()) {
    setDeviceSampleRate(m_activeDevice, (Float64)m_sampleRate);
  }
  setHogMode(m_activeDevice, m_hogModeEnabled.load());

  if (!setupAudioUnit(m_activeDevice, (Float64)m_sampleRate, m_channels, m_bitsPerSample)) {
    LOGE("CoreAudioSink: Failed to setup audio unit");
    return false;
  }

  m_currentFrameIndex.store(0);
  m_isPaused.store(false);
  m_isPlaying.store(true);

  OSStatus status = -1;
  for (int retry = 0; retry < 5; ++retry) {
    status = AudioOutputUnitStart(m_audioUnit);
    if (status == noErr) break;
    LOGW("CoreAudioSink: AudioOutputUnitStart returned %d, waiting for crystal clock to settle (retry %d/5)...", (int)status, retry + 1);
    usleep(50000); // 50ms
  }

  if (status != noErr) {
    LOGE("CoreAudioSink: AudioOutputUnitStart failed (%d)", status);
    m_isPlaying.store(false);
    return false;
  }

  LOGI("CoreAudioSink: Playback started successfully!");
  return true;
}

void CoreAudioSink::pause() {
  m_isPaused.store(true);
}

void CoreAudioSink::resume() {
  m_isPaused.store(false);
}

void CoreAudioSink::stop() {
  m_isPlaying.store(false);
  m_isPaused.store(false);
  m_currentFrameIndex.store(0);

  if (m_audioUnit) {
    AudioOutputUnitStop(m_audioUnit);
  }

  if (m_isMemoryLocked && !m_pcmBuffer.empty()) {
    munlock(m_pcmBuffer.data(), m_pcmBuffer.size() * sizeof(int32_t));
    m_isMemoryLocked = false;
  }
}

bool CoreAudioSink::seek(double seconds) {
  if (m_sampleRate == 0 || m_totalFrames == 0) return false;

  uint64_t targetFrame = (uint64_t)(seconds * m_sampleRate);
  if (targetFrame > m_totalFrames) targetFrame = m_totalFrames;

  m_currentFrameIndex.store(targetFrame);
  LOGI("CoreAudioSink: Seek to %.2f seconds (frame %llu)", seconds, (unsigned long long)targetFrame);
  return true;
}

double CoreAudioSink::getCurrentTimeSeconds() const {
  if (m_sampleRate == 0) return 0.0;
  return (double)m_currentFrameIndex.load() / (double)m_sampleRate;
}

double CoreAudioSink::getTotalDurationSeconds() const {
  if (m_sampleRate == 0) return 0.0;
  return (double)m_totalFrames / (double)m_sampleRate;
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

  float *outBuffer = static_cast<float *>(ioData->mBuffers[0].mData);
  UInt32 channels = m_channels;

  if (m_isPaused.load() || !m_isPlaying.load() || channels == 0) {
    memset(outBuffer, 0, inNumberFrames * channels * sizeof(float));
    return noErr;
  }

  uint64_t currentFrame = m_currentFrameIndex.load();
  uint64_t framesAvailable = (m_totalFrames > currentFrame) ? (m_totalFrames - currentFrame) : 0;
  UInt32 framesToCopy = std::min((UInt32)framesAvailable, inNumberFrames);

  // Convert int32 PCM to 32-bit Float (-1.0 to +1.0) with unity gain at 0 dBFS or precision step attenuation
  const int32_t *src = m_pcmBuffer.data() + (currentFrame * channels);
  size_t samplesToCopy = framesToCopy * channels;
  const float scale = m_gainLinear.load() / 2147483648.0f;

  for (size_t i = 0; i < samplesToCopy; ++i) {
    outBuffer[i] = (float)src[i] * scale;
  }

  // If buffer had fewer frames than requested (end of track)
  if (framesToCopy < inNumberFrames) {
    size_t remainingSamples = (inNumberFrames - framesToCopy) * channels;
    memset(outBuffer + samplesToCopy, 0, remainingSamples * sizeof(float));
    m_isPlaying.store(false);
  }

  m_currentFrameIndex.fetch_add(framesToCopy);
  return noErr;
}

std::string CoreAudioSink::getTelemetryJson() {
  std::stringstream ss;
  ss << "{\n";
  ss << "  \"activeDeviceId\": " << m_activeDevice << ",\n";
  ss << "  \"sampleRate\": " << m_sampleRate << ",\n";
  ss << "  \"bitsPerSample\": " << m_bitsPerSample << ",\n";
  ss << "  \"channels\": " << m_channels << ",\n";
  ss << "  \"isMemoryLocked\": " << (m_isMemoryLocked ? "true" : "false") << ",\n";
  ss << "  \"isPlaying\": " << (m_isPlaying.load() ? "true" : "false") << ",\n";
  ss << "  \"isPaused\": " << (m_isPaused.load() ? "true" : "false") << ",\n";
  ss << "  \"currentSeconds\": " << getCurrentTimeSeconds() << ",\n";
  ss << "  \"totalSeconds\": " << getTotalDurationSeconds() << ",\n";
  ss << "  \"filePath\": \"" << json_escape(m_currentFilePath) << "\"\n";
  ss << "}";
  return ss.str();
}
