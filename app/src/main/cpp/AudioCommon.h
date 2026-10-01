#pragma once

#include <android/log.h>
#include <jni.h>
#include <mutex>
#include <string>
#include <unistd.h>
#include <cstdint>

#define TAG "KewAudioEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern JavaVM *g_jvm;
extern std::mutex g_apiMutex;

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
extern uint32_t g_fast_rand_state;
inline float fast_uniform_rand() {
  g_fast_rand_state = (1103515245 * g_fast_rand_state + 12345);
  return (float)(g_fast_rand_state & 0x7FFFFFFF) / (float)0x7FFFFFFF;
}

inline uint64_t get_time_ms() {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return (uint64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

inline std::string json_escape(const std::string& s) {
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
