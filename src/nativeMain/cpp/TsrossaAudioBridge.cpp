#include "TsrossaAudioBridge.h"
#include "CoreAudioSink.h"
#include <string>
#include <vector>
#include <mutex>

static std::vector<DeviceInfo> s_cachedDevices;
static std::string s_lastTelemetry;
static std::mutex s_bridgeMutex;

extern "C" {

bool tsrossa_init(void) {
  std::lock_guard<std::mutex> lock(s_bridgeMutex);
  return CoreAudioSink::getInstance().init();
}

void tsrossa_shutdown(void) {
  std::lock_guard<std::mutex> lock(s_bridgeMutex);
  CoreAudioSink::getInstance().shutdown();
}

int tsrossa_get_device_count(void) {
  std::lock_guard<std::mutex> lock(s_bridgeMutex);
  s_cachedDevices = CoreAudioSink::getInstance().getAudioDevices();
  return (int)s_cachedDevices.size();
}

const char* tsrossa_get_device_name(int index) {
  std::lock_guard<std::mutex> lock(s_bridgeMutex);
  if (index >= 0 && index < (int)s_cachedDevices.size()) {
    return s_cachedDevices[index].name.c_str();
  }
  return "";
}

uint32_t tsrossa_get_device_id(int index) {
  std::lock_guard<std::mutex> lock(s_bridgeMutex);
  if (index >= 0 && index < (int)s_cachedDevices.size()) {
    return (uint32_t)s_cachedDevices[index].id;
  }
  return 0;
}

bool tsrossa_get_device_is_usb(int index) {
  std::lock_guard<std::mutex> lock(s_bridgeMutex);
  if (index >= 0 && index < (int)s_cachedDevices.size()) {
    return s_cachedDevices[index].isUsb;
  }
  return false;
}

bool tsrossa_get_device_is_default(int index) {
  std::lock_guard<std::mutex> lock(s_bridgeMutex);
  if (index >= 0 && index < (int)s_cachedDevices.size()) {
    return s_cachedDevices[index].isDefault;
  }
  return false;
}

double tsrossa_get_device_sample_rate(int index) {
  std::lock_guard<std::mutex> lock(s_bridgeMutex);
  if (index >= 0 && index < (int)s_cachedDevices.size()) {
    return s_cachedDevices[index].nominalSampleRate;
  }
  return 0.0;
}

bool tsrossa_select_device(uint32_t deviceId) {
  return CoreAudioSink::getInstance().selectDevice((AudioDeviceID)deviceId);
}

bool tsrossa_play(const char *path) {
  if (!path) return false;
  return CoreAudioSink::getInstance().playTrack(path);
}

void tsrossa_pause(void) {
  CoreAudioSink::getInstance().pause();
}

void tsrossa_resume(void) {
  CoreAudioSink::getInstance().resume();
}

void tsrossa_stop(void) {
  CoreAudioSink::getInstance().stop();
}

bool tsrossa_seek(double seconds) {
  return CoreAudioSink::getInstance().seek(seconds);
}

void tsrossa_set_volume_db(int db) {
  CoreAudioSink::getInstance().setVolumeDb(db);
}

void tsrossa_set_slider_volume(double sliderValue, bool useHardwareVolume) {
  CoreAudioSink::getInstance().setSliderVolume(sliderValue, useHardwareVolume);
}

void tsrossa_set_buffer_profile(int profileIndex) {
  CoreAudioSink::getInstance().setBufferProfile(profileIndex);
}

void tsrossa_trigger_warmup(uint32_t durationMs) {
  CoreAudioSink::getInstance().triggerWarmup(durationMs);
}

void tsrossa_set_dither_enabled(bool enabled) {
  CoreAudioSink::getInstance().setDitherEnabled(enabled);
}

void tsrossa_set_hog_mode_enabled(bool enabled) {
  CoreAudioSink::getInstance().setHogModeEnabled(enabled);
}

void tsrossa_set_auto_clock_enabled(bool enabled) {
  CoreAudioSink::getInstance().setAutoClockEnabled(enabled);
}

void tsrossa_set_ram_lock_enabled(bool enabled) {
  CoreAudioSink::getInstance().setRamLockEnabled(enabled);
}

bool tsrossa_prepare_next_track(const char* filePath) {
  if (!filePath) return false;
  return CoreAudioSink::getInstance().prepareNextTrack(filePath);
}

void tsrossa_clear_next_track(void) {
  CoreAudioSink::getInstance().clearNextTrack();
}

bool tsrossa_is_track_finished(void) {
  return CoreAudioSink::getInstance().isTrackFinished();
}

bool tsrossa_consume_gapless_transition(void) {
  return CoreAudioSink::getInstance().consumeGaplessTransition();
}

bool tsrossa_is_playing(void) {
  return CoreAudioSink::getInstance().isPlaying();
}

double tsrossa_get_current_time(void) {
  return CoreAudioSink::getInstance().getCurrentTimeSeconds();
}

double tsrossa_get_total_duration(void) {
  return CoreAudioSink::getInstance().getTotalDurationSeconds();
}

uint32_t tsrossa_get_track_sample_rate(void) {
  return CoreAudioSink::getInstance().getSampleRate();
}

uint32_t tsrossa_get_track_bit_depth(void) {
  return CoreAudioSink::getInstance().getBitDepth();
}

uint32_t tsrossa_get_track_channels(void) {
  return CoreAudioSink::getInstance().getChannels();
}

double tsrossa_get_actual_hardware_sample_rate(void) {
  return CoreAudioSink::getInstance().getActualHardwareSampleRate();
}

const char* tsrossa_get_telemetry_json(void) {
  std::lock_guard<std::mutex> lock(s_bridgeMutex);
  s_lastTelemetry = CoreAudioSink::getInstance().getTelemetryJson();
  return s_lastTelemetry.c_str();
}

}
