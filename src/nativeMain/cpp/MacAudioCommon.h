#pragma once

#include <cstdio>
#include <cstdint>
#include <cmath>
#include <algorithm>
#include <string>
#include <mutex>
#include <chrono>
#include <unistd.h>
#include <pthread.h>

#define TAG "TsrossaMac"
#define LOGI(...) do { printf("[INFO][%s] ", TAG); printf(__VA_ARGS__); printf("\n"); fflush(stdout); } while(0)
#define LOGW(...) do { printf("[WARN][%s] ", TAG); printf(__VA_ARGS__); printf("\n"); fflush(stdout); } while(0)
#define LOGE(...) do { fprintf(stderr, "[ERROR][%s] ", TAG); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); fflush(stderr); } while(0)

inline uint64_t get_time_ms() {
  using namespace std::chrono;
  return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
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
