#ifndef TSROSSA_AUDIO_BRIDGE_H
#define TSROSSA_AUDIO_BRIDGE_H

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

bool tsrossa_init(void);
void tsrossa_shutdown(void);

int tsrossa_get_device_count(void);
const char* tsrossa_get_device_name(int index);
uint32_t tsrossa_get_device_id(int index);
bool tsrossa_get_device_is_usb(int index);
bool tsrossa_get_device_is_default(int index);
double tsrossa_get_device_sample_rate(int index);

bool tsrossa_select_device(uint32_t deviceId);
bool tsrossa_play(const char *path);
void tsrossa_pause(void);
void tsrossa_resume(void);
void tsrossa_stop(void);
bool tsrossa_seek(double seconds);

void tsrossa_set_volume_db(int db);
void tsrossa_set_hog_mode_enabled(bool enabled);
void tsrossa_set_auto_clock_enabled(bool enabled);
void tsrossa_set_ram_lock_enabled(bool enabled);

bool tsrossa_is_playing(void);
double tsrossa_get_current_time(void);
double tsrossa_get_total_duration(void);
uint32_t tsrossa_get_track_sample_rate(void);
uint32_t tsrossa_get_track_bit_depth(void);
uint32_t tsrossa_get_track_channels(void);

const char* tsrossa_get_telemetry_json(void);

#ifdef __cplusplus
}
#endif

#endif
