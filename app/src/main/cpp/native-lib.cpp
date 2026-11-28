#include <jni.h>
#include <string>
#include <cmath>

#define DR_FLAC_IMPLEMENTATION
#include "dr_flac.h"
#define DR_WAV_IMPLEMENTATION
#include "dr_wav.h"

#include "AudioCommon.h"
#include "AudioEngineState.h"
#include "AudioDecoder.h"
#include "UsbDacController.h"
#include "AudioStreamer.h"

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  g_jvm = vm;
  return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_initUsbDac(JNIEnv *env, jobject thiz, jint fd) {
  ApiMutexLock lock(__func__);
  return init_usb_dac_internal(fd) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_closeUsbDac(JNIEnv *env, jobject thiz) {
  ApiMutexLock lock(__func__);
  close_usb_dac_internal();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_playAudio(JNIEnv *env, jobject thiz, jstring filePath) {
  ApiMutexLock lock(__func__);
  const char *path = env->GetStringUTFChars(filePath, 0);
  std::string savedPath = path;
  env->ReleaseStringUTFChars(filePath, path);

  return play_audio_internal(savedPath, thiz, env);
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_pauseAudio(JNIEnv *env, jobject thiz) {
  pause_audio_internal();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_resumeAudio(JNIEnv *env, jobject thiz) {
  ApiMutexLock lock(__func__);
  return resume_audio_internal() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_stopAudio(JNIEnv *env, jobject thiz) {
  ApiMutexLock lock(__func__);
  stop_audio_internal();
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_setSoftwareVolume(JNIEnv *env, jobject thiz, jfloat vol) {
  const float clampedVol = std::max(0.0f, std::min(1.0f, static_cast<float>(vol)));
  g_audioState.rawLinearVolume.store(clampedVol, std::memory_order_release);

  if (g_audioState.preferHardwareVolume.load(std::memory_order_acquire) &&
      g_audioState.isHardwareVolumeSupported.load(std::memory_order_acquire)) {
    set_volume(clampedVol);
  }
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getPosition(JNIEnv *env, jobject thiz) {
  return get_position_internal();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_seekTo(JNIEnv *env, jobject thiz, jdouble targetSeconds) {
  return seek_to_internal(targetSeconds) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getSampleRate(JNIEnv *env, jobject thiz) {
  return g_audioState.sampleRate.load();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getNegotiatedBitDepth(JNIEnv *env, jobject thiz) {
  return g_audioState.subframeSize.load() * 8;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getUacVersion(JNIEnv *env, jobject thiz) {
  return g_audioState.uacVersion.load();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getClaimedInterfaces(JNIEnv *env, jobject thiz) {
  std::string interfaces;
  {
    std::lock_guard<std::mutex> lock(g_stateMutex);
    for (size_t i = 0; i < g_audioState.claimedInterfaces.size(); ++i) {
      interfaces += std::to_string(g_audioState.claimedInterfaces[i]);
      if (i < g_audioState.claimedInterfaces.size() - 1) interfaces += ", ";
    }
  }
  return env->NewStringUTF(interfaces.c_str());
}


extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getSourceBitDepth(JNIEnv *env, jobject thiz) {
  return g_audioState.sourceBitDepth.load();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getRecentErrorCount(JNIEnv *env, jobject thiz) {
  return g_audioState.consecutiveErrors.load();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isDacConnected(JNIEnv *env, jobject thiz) {
  return g_audioState.usbHandle != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isPlaying(JNIEnv *env, jobject thiz) {
  return g_audioState.isPlaying.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isFinished(JNIEnv *env, jobject thiz) {
  return g_audioState.isFinished.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_cleanGarbage(JNIEnv *env, jobject thiz) {
  clean_garbage_internal();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_clearNextTrack(JNIEnv *env, jobject thiz) {
  return clear_next_track_internal() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_prepareNextTrack(JNIEnv *env, jobject thiz, jstring filePath) {
  const char *path = env->GetStringUTFChars(filePath, 0);
  std::string savedPath = path;
  env->ReleaseStringUTFChars(filePath, path);

  return prepare_next_track_internal(savedPath) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isHardwareVolumeSupported(JNIEnv *env, jobject thiz) {
  return g_audioState.isHardwareVolumeSupported.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_setHardwareVolumeEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
  ApiMutexLock lock(__func__);
  set_hardware_volume_enabled(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isHardwareVolumeEnabled(JNIEnv *env, jobject thiz) {
  return is_hardware_volume_enabled() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isHardwareVolumeActive(JNIEnv *env, jobject thiz) {
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
  std::lock_guard<std::mutex> lock(g_stateMutex);
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
Java_com_yuka_musicplayer_audio_AudioEngine_getSupportedBitDepths(JNIEnv *env, jobject thiz) {
  std::lock_guard<std::mutex> lock(g_stateMutex);
  std::string result;
  for (size_t i = 0; i < g_audioState.supportedBitDepths.size(); i++) {
    if (i > 0) result += " / ";
    result += std::to_string(g_audioState.supportedBitDepths[i]);
  }
  return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getSupportedSampleRates(JNIEnv *env, jobject thiz) {
  std::lock_guard<std::mutex> lock(g_stateMutex);
  std::string result;
  for (size_t i = 0; i < g_audioState.supportedSampleRates.size(); i++) {
    if (i > 0) result += ",";
    result += std::to_string(g_audioState.supportedSampleRates[i]);
  }
  return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getRefusedTrackHistory(JNIEnv *env, jobject thiz) {
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
  json += "\"isHardwareVolumeSupported\":" + std::string(g_audioState.isHardwareVolumeSupported.load() ? "true" : "false") + ",";
  json += "\"isHardwareVolumeEnabled\":" + std::string(g_audioState.preferHardwareVolume.load() ? "true" : "false") + ",";
  json += "\"isForceSoftwareVolume\":" + std::string(g_audioState.isForceSoftwareVolume.load() ? "true" : "false") + ",";
  json += "\"minVolumeDb\":" + std::to_string(g_audioState.minVolumeDb.load()) + ",";
  json += "\"maxVolumeDb\":" + std::to_string(g_audioState.maxVolumeDb.load()) + ",";
  json += "\"uacVersion\":" + std::to_string(g_audioState.uacVersion.load());
  json += "}";

  return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getLastUsbDiagnostic(JNIEnv *env, jobject thiz) {
  std::lock_guard<std::mutex> lock(g_usbDiagMutex);
  return env->NewStringUTF(g_lastUsbDiagnostic.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_triggerWarmup(JNIEnv *env, jobject thiz, jint durationMs) {
  ApiMutexLock lock(__func__);
  return trigger_warmup_internal(durationMs) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_cancelWarmup(JNIEnv *env, jobject thiz) {
  ApiMutexLock lock(__func__);
  return cancel_warmup_internal() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isWarmingUp(JNIEnv *env, jobject thiz) {
  return is_warming_up_internal() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isBitPerfectIntegerPassActive(JNIEnv *env, jobject thiz) {
  return g_audioState.bitPerfectDirectPassActive.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_setUsbBufferProfile(JNIEnv *env, jobject thiz, jint transfers, jint packets) {
  if (transfers >= 4 && transfers <= 64) {
    g_audioState.isoTransfersCount.store(transfers);
  }
  if (packets >= 4 && packets <= 128) {
    g_audioState.isoPacketsCount.store(packets);
  }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getTimingTelemetry(JNIEnv *env, jobject thiz) {
  std::string json = "{";
  json += "\"interfaceSettleUs\":" + std::to_string(g_audioState.lastInterfaceSettleUs.load()) + ",";
  json += "\"pllRelockUs\":" + std::to_string(g_audioState.lastPllRelockUs.load()) + ",";
  json += "\"antiPopPaddingUs\":" + std::to_string(g_audioState.lastAntiPopPaddingUs.load()) + ",";
  json += "\"maxIsoJitterNs\":" + std::to_string(g_audioState.maxIsoJitterNs.load());
  json += "}";
  return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_setPeqEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
  const bool enableBool = (enabled == JNI_TRUE);
  const bool wasEnabled = g_audioState.peqState.enabled.load(std::memory_order_relaxed);
  if (enableBool) {
    uint32_t sr = g_audioState.sampleRate.load(std::memory_order_relaxed);
    if (sr > 0) {
      g_audioState.peqState.updateCoefficients(sr);
    }
    if (!wasEnabled) {
      g_audioState.peqState.requestStateReset();
    }
  }
  g_audioState.peqState.enabled.store(enableBool, std::memory_order_release);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isPeqEnabled(JNIEnv *env, jobject thiz) {
  return g_audioState.peqState.enabled.load(std::memory_order_relaxed) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_setPeqPreampDb(JNIEnv *env, jobject thiz, jfloat preampDb) {
  g_audioState.peqState.preampDb.store(preampDb, std::memory_order_release);
  uint32_t sr = g_audioState.sampleRate.load(std::memory_order_relaxed);
  if (sr > 0) {
    g_audioState.peqState.updateCoefficients(sr);
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_setPeqBand(
    JNIEnv *env, jobject thiz,
    jint bandIndex, jfloat freqHz, jfloat gainDb, jfloat q, jint filterType, jboolean enabled) {
  uint32_t sr = g_audioState.sampleRate.load(std::memory_order_relaxed);
  g_audioState.peqState.setSingleBand(
      static_cast<int>(bandIndex),
      freqHz,
      gainDb,
      q,
      static_cast<int>(filterType),
      enabled == JNI_TRUE,
      sr
  );
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_setPeqAllBands(
    JNIEnv *env, jobject thiz,
    jfloatArray freqs, jfloatArray gains, jfloatArray qs, jintArray filterTypes) {
  if (!freqs || !gains || !qs || !filterTypes) return;
  jsize len = env->GetArrayLength(freqs);
  if (len > kNumPeqBands) len = kNumPeqBands;

  jfloat *fBuf = env->GetFloatArrayElements(freqs, nullptr);
  jfloat *gBuf = env->GetFloatArrayElements(gains, nullptr);
  jfloat *qBuf = env->GetFloatArrayElements(qs, nullptr);
  jint *tBuf = env->GetIntArrayElements(filterTypes, nullptr);

  uint32_t sr = g_audioState.sampleRate.load(std::memory_order_relaxed);
  g_audioState.peqState.setAllBands(fBuf, gBuf, qBuf, tBuf, len, sr);

  env->ReleaseFloatArrayElements(freqs, fBuf, JNI_ABORT);
  env->ReleaseFloatArrayElements(gains, gBuf, JNI_ABORT);
  env->ReleaseFloatArrayElements(qs, qBuf, JNI_ABORT);
  env->ReleaseIntArrayElements(filterTypes, tBuf, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_setPeqFullConfig(
    JNIEnv *env, jobject thiz,
    jboolean enabled, jfloat preampDb,
    jfloatArray freqs, jfloatArray gains, jfloatArray qs, jintArray filterTypes) {
  if (!freqs || !gains || !qs || !filterTypes) return;
  jsize len = env->GetArrayLength(freqs);
  if (len > kNumPeqBands) len = kNumPeqBands;

  jfloat *fBuf = env->GetFloatArrayElements(freqs, nullptr);
  jfloat *gBuf = env->GetFloatArrayElements(gains, nullptr);
  jfloat *qBuf = env->GetFloatArrayElements(qs, nullptr);
  jint *tBuf = env->GetIntArrayElements(filterTypes, nullptr);

  uint32_t sr = g_audioState.sampleRate.load(std::memory_order_relaxed);
  g_audioState.peqState.setFullConfig(
      enabled == JNI_TRUE,
      preampDb,
      fBuf,
      gBuf,
      qBuf,
      tBuf,
      len,
      sr
  );

  env->ReleaseFloatArrayElements(freqs, fBuf, JNI_ABORT);
  env->ReleaseFloatArrayElements(gains, gBuf, JNI_ABORT);
  env->ReleaseFloatArrayElements(qs, qBuf, JNI_ABORT);
  env->ReleaseIntArrayElements(filterTypes, tBuf, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_resetPeqState(JNIEnv *env, jobject thiz) {
  g_audioState.peqState.requestStateReset();
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_setCompressorFullConfig(
    JNIEnv *env, jobject thiz,
    jboolean enabled,
    jfloat downThresholdDb,
    jfloat downRatio,
    jfloat upThresholdDb,
    jfloat upRatio,
    jfloat maxUpwardBoostDb,
    jfloat floorThresholdDb,
    jfloat kneeDb,
    jfloat attackMs,
    jfloat releaseMs,
    jfloat makeupGainDb,
    jfloat sidechainHpfHz,
    jboolean useRmsDetector
) {
  uint32_t sr = g_audioState.sampleRate.load(std::memory_order_relaxed);
  g_audioState.compressorState.setFullConfig(
      enabled == JNI_TRUE,
      downThresholdDb,
      downRatio,
      upThresholdDb,
      upRatio,
      maxUpwardBoostDb,
      floorThresholdDb,
      kneeDb,
      attackMs,
      releaseMs,
      makeupGainDb,
      sidechainHpfHz,
      useRmsDetector == JNI_TRUE,
      sr
  );
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_isCompressorEnabled(JNIEnv *env, jobject thiz) {
  return g_audioState.compressorState.enabled.load(std::memory_order_relaxed) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_resetCompressorState(JNIEnv *env, jobject thiz) {
  g_audioState.compressorState.requestStateReset();
  g_audioState.lastCompressorGainDb.store(0.0f, std::memory_order_relaxed);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getCompressorGainDb(JNIEnv *env, jobject thiz) {
  return static_cast<jfloat>(g_audioState.lastCompressorGainDb.load(std::memory_order_relaxed));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_readLiveSignalPathSnapshot(
    JNIEnv *env, jobject thiz, jfloatArray outFloats, jintArray outInts) {
  if (!outFloats || !outInts) return JNI_FALSE;
  if (env->GetArrayLength(outFloats) < 7 || env->GetArrayLength(outInts) < 6) {
    return JNI_FALSE;
  }

  const bool bitPerfect = g_audioState.bitPerfectDirectPassActive.load(std::memory_order_relaxed);
  const bool hwActive = g_audioState.isHardwareVolumeActive.load(std::memory_order_acquire);
  const bool peqEff = g_audioState.lastPeqEffective.load(std::memory_order_relaxed);
  const bool compEff = g_audioState.lastCompEffective.load(std::memory_order_relaxed);
  const bool hasNext = g_audioState.hasNextTrack.load(std::memory_order_relaxed);
  const bool isAsync = g_audioState.isAsync.load(std::memory_order_relaxed);
  const bool mlockOk = g_audioState.isMlockActive.load(std::memory_order_relaxed);
  const bool rtFifoOk = g_audioState.isRtSchedFifoActive.load(std::memory_order_relaxed);
  const bool isFloatPcm = g_audioState.sourceIsFloatPcm.load(std::memory_order_relaxed);

  const float appliedHwDb = hwActive ? g_audioState.appliedHardwareDb.load(std::memory_order_relaxed) : 0.0f;
  const float curVol = g_audioState.currentVolume.load();
  const float dspGainDb = (curVol >= 0.9999f)
      ? 0.0f
      : (curVol <= 0.00001f ? -128.0f : 20.0f * std::log10(curVol));
  const float compGainDb = g_audioState.lastCompressorGainDb.load(std::memory_order_relaxed);
  const float limiterGrDb = g_audioState.lastLimiterGainReductionDb.load(std::memory_order_relaxed);
  const float resVolDb = static_cast<float>(g_audioState.resVolumeDb.load(std::memory_order_relaxed)) / 256.0f;
  const float maxVolDb = static_cast<float>(g_audioState.maxVolumeDb.load(std::memory_order_relaxed)) / 256.0f;

  const int activeTransfers = g_audioState.activeIsoTransfers.load(std::memory_order_relaxed);
  const int activePkts = g_audioState.activeIsoPacketsPerTransfer.load(std::memory_order_relaxed);
  const int fps = g_audioState.usb_frames_per_sec.load(std::memory_order_relaxed);
  const uint32_t sr = g_audioState.sampleRate.load(std::memory_order_relaxed);

  float latencyMs = 0.0f;
  if (activeTransfers > 0 && activePkts > 0 && fps > 0) {
    latencyMs = (static_cast<float>(activeTransfers * activePkts) * 1000.0f) / static_cast<float>(fps);
    if ((peqEff || compEff) && sr > 0U) {
      int la = 64;
      if (sr > 240000U) la = 512;
      else if (sr > 120000U) la = 256;
      else if (sr > 60000U) la = 128;
      latencyMs += (static_cast<float>(la) * 1000.0f) / static_cast<float>(sr);
    }
  }

  jfloat fBuf[7] = {
      appliedHwDb, dspGainDb, compGainDb, limiterGrDb, resVolDb, maxVolDb, latencyMs
  };
  jint flags = 0;
  if (bitPerfect) flags |= (1 << 0);
  if (hwActive)   flags |= (1 << 1);
  if (peqEff)     flags |= (1 << 2);
  if (compEff)    flags |= (1 << 3);
  if (hasNext)    flags |= (1 << 4);
  if (isAsync)    flags |= (1 << 5);
  if (mlockOk)    flags |= (1 << 6);
  if (rtFifoOk)   flags |= (1 << 7);
  if (isFloatPcm) flags |= (1 << 8);

  jint iBuf[6] = {
      flags,
      g_audioState.negotiatedBitResolution.load(std::memory_order_relaxed),
      static_cast<jint>(g_audioState.channels.load(std::memory_order_relaxed)),
      activeTransfers,
      activePkts,
      fps
  };

  env->SetFloatArrayRegion(outFloats, 0, 7, fBuf);
  env->SetIntArrayRegion(outInts, 0, 6, iBuf);
  return JNI_TRUE;
}
