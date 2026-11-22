#include "AudioEngineState.h"

// Define the global engine state instances
AudioEngineState g_audioState;
std::mutex g_stateMutex;
std::queue<ControlRequest> g_controlQueue;
std::mutex g_controlMutex;
std::condition_variable g_controlCv;
std::mutex g_isoStartMutex;
std::condition_variable g_isoStartCv;
std::string g_lastUsbDiagnostic;
std::mutex g_usbDiagMutex;

// Global JVM pointer and API Mutex
JavaVM *g_jvm = nullptr;
std::mutex g_apiMutex;

void record_usb_diag(const std::string &msg) {
  std::lock_guard<std::mutex> lock(g_usbDiagMutex);
  LOGI("USB_DIAG: %s", msg.c_str());
  constexpr size_t kMaxDiagBytes = 8192;
  constexpr size_t kKeepTailBytes = 6144;
  if (g_lastUsbDiagnostic.size() + msg.size() + 1 > kMaxDiagBytes) {
    if (g_lastUsbDiagnostic.size() > kKeepTailBytes) {
      size_t cutPos = g_lastUsbDiagnostic.size() - kKeepTailBytes;
      size_t nextNewline = g_lastUsbDiagnostic.find('\n', cutPos);
      if (nextNewline != std::string::npos && nextNewline + 1 < g_lastUsbDiagnostic.size()) {
        g_lastUsbDiagnostic.erase(0, nextNewline + 1);
      } else {
        g_lastUsbDiagnostic.erase(0, cutPos);
      }
    } else {
      g_lastUsbDiagnostic.clear();
    }
  }
  g_lastUsbDiagnostic.append(msg);
  g_lastUsbDiagnostic.push_back('\n');
}
