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
  return resume_audio_internal() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_stopAudio(JNIEnv *env, jobject thiz) {
  ApiMutexLock lock(__func__);
  stop_audio_internal();
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_setSoftwareVolume(JNIEnv *env, jobject thiz, jfloat vol) {
  ApiMutexLock lock(__func__);
  g_audioState.rawLinearVolume = vol;

  if (g_audioState.isHardwareVolumeActive.load()) {
    ControlRequest req;
    req.type = 1;
    req.volume = vol;
    {
      std::lock_guard<std::mutex> lock2(g_controlMutex);
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

  float scaled_vol = vol;
  if (vol < 0.001f) {
    scaled_vol = 0.0f;
  } else {
    scaled_vol = (std::exp(vol * 4.0f) - 1.0f) / (std::exp(4.0f) - 1.0f);
  }

  if (!g_audioState.isHardwareVolumeActive.load()) {
    g_audioState.targetVolume.store(scaled_vol);
  } else {
    g_audioState.targetVolume.store(1.0f); // Bit-Perfect
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
Java_com_yuka_musicplayer_audio_AudioEngine_getSourceSampleRate(JNIEnv *env, jobject thiz) {
  return g_audioState.sampleRate.load();
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
  std::string result;
  for (size_t i = 0; i < g_audioState.supportedBitDepths.size(); i++) {
    if (i > 0) result += " / ";
    result += std::to_string(g_audioState.supportedBitDepths[i]);
  }
  return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getSupportedSampleRates(JNIEnv *env, jobject thiz) {
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
  json += "\"isForceSoftwareVolume\":" + std::string(g_audioState.isForceSoftwareVolume.load() ? "true" : "false") + ",";
  json += "\"minVolumeDb\":" + std::to_string(g_audioState.minVolumeDb.load()) + ",";
  json += "\"maxVolumeDb\":" + std::to_string(g_audioState.maxVolumeDb.load()) + ",";
  json += "\"uacVersion\":" + std::to_string(g_audioState.uacVersion.load());
  json += "}";

  return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getOutputBitDepth(JNIEnv *env, jobject thiz) {
  return g_audioState.subframeSize.load() * 8;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getOutputSampleRate(JNIEnv *env, jobject thiz) {
  return g_audioState.sampleRate.load();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yuka_musicplayer_audio_AudioEngine_getLastUsbDiagnostic(JNIEnv *env, jobject thiz) {
  std::lock_guard<std::mutex> lock(g_usbDiagMutex);
  return env->NewStringUTF(g_lastUsbDiagnostic.c_str());
}
