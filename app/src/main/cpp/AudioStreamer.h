#pragma once

#include <cstdint>
#include <string>
#include <libusb.h>
#include <jni.h>

void LIBUSB_CALL iso_callback(struct libusb_transfer *transfer);
void stop_and_join_iso_thread(const char* caller_reason);
void stop_and_join_decode_thread();
void playAudio_cleanup_on_negotiation_failure();

// High level streaming controls
int play_audio_internal(const std::string &path, jobject thiz, JNIEnv *env);
void pause_audio_internal();
bool resume_audio_internal();
void stop_audio_internal();
bool prepare_next_track_internal(const std::string &path);
bool clear_next_track_internal();
void clean_garbage_internal();
bool seek_to_internal(double targetSeconds);
double get_position_internal();
