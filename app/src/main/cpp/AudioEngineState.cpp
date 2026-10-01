#include "AudioEngineState.h"

// Define the global engine state instances
AudioEngineState g_audioState;
std::mutex g_stateMutex;
std::queue<ControlRequest> g_controlQueue;
std::mutex g_controlMutex;
std::condition_variable g_controlCv;
std::string g_lastUsbDiagnostic;
std::mutex g_usbDiagMutex;

// Global JVM pointer and API Mutex
JavaVM *g_jvm = nullptr;
std::mutex g_apiMutex;
uint32_t g_fast_rand_state = 123456789;

void record_usb_diag(const std::string &msg) {
  std::lock_guard<std::mutex> lock(g_usbDiagMutex);
  LOGI("USB_DIAG: %s", msg.c_str());
  if (g_lastUsbDiagnostic.size() > 16000) {
    g_lastUsbDiagnostic = g_lastUsbDiagnostic.substr(g_lastUsbDiagnostic.size() - 8000);
  }
  g_lastUsbDiagnostic += msg;
  g_lastUsbDiagnostic += "\n";
}
