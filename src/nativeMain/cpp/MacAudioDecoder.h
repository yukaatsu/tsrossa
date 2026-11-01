#pragma once

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <cerrno>

#include "MacAudioCommon.h"

// Do not define DR_FLAC_NO_STDIO or DR_WAV_NO_STDIO so drflac_open_file and drwav_init_file are enabled
#undef DR_FLAC_NO_STDIO
#include "dr_flac.h"

#undef DR_WAV_NO_STDIO
#include "dr_wav.h"

enum class TrackFormat { UNKNOWN, FLAC, WAV };

struct MacAudioDecoder {
  TrackFormat format = TrackFormat::UNKNOWN;
  drflac *pFlac = nullptr;
  drwav wav;
  bool isWavInit = false;

  uint32_t sampleRate = 0;
  uint32_t channels = 0;
  uint32_t bitsPerSample = 0;
  uint64_t totalPCMFrameCount = 0;

  MacAudioDecoder() {
    memset(&wav, 0, sizeof(wav));
  }

  std::string lastOpenError;

  bool open(const char *path) {
    close();
    lastOpenError.clear();

    FILE *testFile = fopen(path, "rb");
    if (!testFile) {
      int err = errno;
      char errBuf[256];
      snprintf(errBuf, sizeof(errBuf), "fopen failed (errno=%d: %s)", err, strerror(err));
      lastOpenError = errBuf;
      LOGE("MacAudioDecoder: Cannot open '%s': %s", path, errBuf);
      return false;
    }
    fclose(testFile);

    // 1. Try FLAC
    pFlac = drflac_open_file(path, nullptr);
    if (pFlac != nullptr) {
      format = TrackFormat::FLAC;
      sampleRate = pFlac->sampleRate;
      channels = pFlac->channels;
      bitsPerSample = pFlac->bitsPerSample;
      totalPCMFrameCount = pFlac->totalPCMFrameCount;
      LOGI("MacAudioDecoder: Opened FLAC '%s' (%u Hz, %u ch, %u-bit, %llu frames)",
           path, sampleRate, channels, bitsPerSample, (unsigned long long)totalPCMFrameCount);
      return true;
    }

    // 2. Try WAV
    if (drwav_init_file(&wav, path, nullptr)) {
      format = TrackFormat::WAV;
      isWavInit = true;
      sampleRate = wav.sampleRate;
      channels = wav.channels;
      bitsPerSample = wav.bitsPerSample;
      totalPCMFrameCount = wav.totalPCMFrameCount;
      LOGI("MacAudioDecoder: Opened WAV '%s' (%u Hz, %u ch, %u-bit, %llu frames)",
           path, sampleRate, channels, bitsPerSample, (unsigned long long)totalPCMFrameCount);
      return true;
    }

    lastOpenError = "Unsupported or corrupt format (only FLAC/WAV supported)";
    LOGE("MacAudioDecoder: '%s' is neither valid FLAC nor WAV", path);
    return false;
  }

  size_t read_pcm_frames_s32(size_t framesToRead, int32_t *pBufferOut) {
    if (format == TrackFormat::FLAC && pFlac != nullptr) {
      return (size_t)drflac_read_pcm_frames_s32(pFlac, framesToRead, pBufferOut);
    } else if (format == TrackFormat::WAV && isWavInit) {
      return (size_t)drwav_read_pcm_frames_s32(&wav, framesToRead, pBufferOut);
    }
    return 0;
  }

  bool seek_to_pcm_frame(uint64_t frameIndex) {
    if (format == TrackFormat::FLAC && pFlac != nullptr) {
      return drflac_seek_to_pcm_frame(pFlac, frameIndex);
    } else if (format == TrackFormat::WAV && isWavInit) {
      return drwav_seek_to_pcm_frame(&wav, frameIndex);
    }
    return false;
  }

  void close() {
    if (format == TrackFormat::FLAC && pFlac != nullptr) {
      drflac_close(pFlac);
      pFlac = nullptr;
    } else if (format == TrackFormat::WAV && isWavInit) {
      drwav_uninit(&wav);
      isWavInit = false;
    }
    format = TrackFormat::UNKNOWN;
    sampleRate = 0;
    channels = 0;
    bitsPerSample = 0;
    totalPCMFrameCount = 0;
  }

  ~MacAudioDecoder() {
    close();
  }
};
