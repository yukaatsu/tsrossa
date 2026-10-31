#pragma once

#include <android/log.h>
#include <jni.h>
#include <mutex>
#include <string>
#include <unistd.h>
#include <cstdint>
#include <cmath>
#include <algorithm>

#define TAG "KewAudioEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern JavaVM *g_jvm;
extern std::mutex g_apiMutex;

// Security & Streaming Ring Buffer Constants
// 64 MB sliding-window ring buffer (~87 sec window @ 96kHz stereo, unbounded total track duration)
constexpr uint64_t MAX_RING_BUFFER_BYTES = 64ULL * 1024 * 1024;
constexpr uint64_t MAX_NEXT_RING_BUFFER_BYTES = 32ULL * 1024 * 1024;
constexpr uint64_t RING_BUFFER_SAFETY_FRAMES = 65536ULL; // Keep 64K frames safety gap before read pointer

// Legacy aliases kept for compatibility
constexpr uint64_t MAX_TRACK_BUFFER_BYTES = MAX_RING_BUFFER_BYTES;
constexpr uint64_t MAX_NEXT_TRACK_BUFFER_BYTES = MAX_NEXT_RING_BUFFER_BYTES;

// Unified Psychoacoustic Volume Curve: dB(v) = -48.0f * (1 - v) * sqrt(1 - v)
// Calibrated Audiophile Studio Taper for powerful dynamics without clipping
inline float compute_perceptual_db(float slider) {
  if (slider <= 0.0f) return -128.0f;
  if (slider >= 1.0f) return 0.0f;
  float comp = 1.0f - slider;
  return -48.0f * comp * std::sqrt(comp);
}

inline double compute_perceptual_db(double slider) {
  if (slider <= 0.0) return -128.0;
  if (slider >= 1.0) return 0.0;
  double comp = 1.0 - slider;
  return -48.0 * comp * std::sqrt(comp);
}

inline float db_to_linear_gain(float db) {
  if (db <= -127.0f) return 0.0f;
  if (db >= 0.0f) return 1.0f;
  return std::pow(10.0f, db / 20.0f);
}

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

// Dual-Channel Independent SplitMix64 PRNG for Decorrelated Stereo TPDF Dithering
// Ensures interaural cross-correlation IACC = 0.0 for wide soundstage & holographic instrument separation
inline double fast_uniform_rand_channel(uint32_t ch) {
  static thread_local uint64_t states[2] = {
      0x9E3779B97F4A7C15ULL,
      0xBF58476D1CE4E5B9ULL
  };
  uint64_t z = (states[ch & 1U] += 0x9E3779B97F4A7C15ULL);
  z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
  z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
  z = z ^ (z >> 31);
  return static_cast<double>(z >> 11) * (1.0 / 9007199254740992.0);
}

inline double fast_tpdf_dither(uint32_t ch) {
  // Symmetric [-1.0, +1.0) TPDF dither without inter-channel correlation
  return fast_uniform_rand_channel(ch) - fast_uniform_rand_channel(ch);
}

// Backward-compatible uniform random generator
inline float fast_uniform_rand() {
  return static_cast<float>(fast_uniform_rand_channel(0));
}

// Perceptual exponential volume curve with hard clipping limits [0.0f, 1.0f]
inline float calculate_scaled_volume(float vol) {
  if (vol < 0.001f) return 0.0f;
  if (vol >= 0.999f) return 1.0f;
  float scaled = (std::exp(vol * 4.0f) - 1.0f) / (std::exp(4.0f) - 1.0f);
  return std::min(1.0f, std::max(0.0f, scaled));
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

inline std::string get_file_basename(const std::string &path) {
  if (path.empty()) return "";
  size_t lastSlash = path.find_last_of('/');
  if (lastSlash != std::string::npos && lastSlash + 1 < path.size()) {
    return path.substr(lastSlash + 1);
  } else if (lastSlash != std::string::npos) {
    return "";
  }
  return path;
}
