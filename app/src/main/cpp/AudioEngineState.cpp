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
  if (g_lastUsbDiagnostic.size() + msg.size() > 8192) {
    g_lastUsbDiagnostic.clear();
  }
  g_lastUsbDiagnostic.append(msg);
  g_lastUsbDiagnostic.push_back('\n');
}
