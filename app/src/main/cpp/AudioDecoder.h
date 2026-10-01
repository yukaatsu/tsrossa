#pragma once

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <cerrno>

#include "AudioCommon.h"
#include "dr_flac.h"
#include "dr_wav.h"

enum class AudioCodec { UNKNOWN, FLAC, WAV };

struct AudioDecoder {
  AudioCodec codec = AudioCodec::UNKNOWN;
  drflac *pFlac = nullptr;
  drwav wav;
  bool isWavInit = false;

  uint32_t sampleRate = 0;
  uint32_t channels = 0;
  uint32_t bitsPerSample = 0;
  uint64_t totalPCMFrameCount = 0;

  AudioDecoder() {
    memset(&wav, 0, sizeof(wav));
  }

  std::string lastOpenError;

  bool open(const char *path) {
    close();
    lastOpenError.clear();

    // Check if file can be opened via POSIX fopen to distinguish permission/existence from decode error
    FILE *testFile = fopen(path, "rb");
    if (!testFile) {
      int err = errno;
      char errBuf[256];
      snprintf(errBuf, sizeof(errBuf), "fopen failed (errno=%d: %s)", err, strerror(err));
      lastOpenError = errBuf;
      LOGE("AudioDecoder: Cannot open '%s': %s", path, errBuf);
      return false;
    }
    fclose(testFile);

    // 1. Coba buka sebagai FLAC
    pFlac = drflac_open_file(path, nullptr);
    if (pFlac != nullptr) {
      codec = AudioCodec::FLAC;
      sampleRate = pFlac->sampleRate;
      channels = pFlac->channels;
      bitsPerSample = pFlac->bitsPerSample;
      totalPCMFrameCount = pFlac->totalPCMFrameCount;
      return true;
    }

    // 2. Jika bukan FLAC, coba buka sebagai WAV
    if (drwav_init_file(&wav, path, nullptr)) {
      codec = AudioCodec::WAV;
      isWavInit = true;
      sampleRate = wav.sampleRate;
      channels = wav.channels;
      bitsPerSample = wav.bitsPerSample;
      totalPCMFrameCount = wav.totalPCMFrameCount;
      return true;
    }

    lastOpenError = "Unsupported or corrupt format (only FLAC/WAV supported)";
    LOGE("AudioDecoder: '%s' is neither valid FLAC nor WAV", path);
    return false;
  }

  size_t read_pcm_frames_s32(size_t framesToRead, int32_t *pBufferOut) {
    if (codec == AudioCodec::FLAC && pFlac != nullptr) {
      return (size_t)drflac_read_pcm_frames_s32(pFlac, framesToRead, pBufferOut);
    } else if (codec == AudioCodec::WAV && isWavInit) {
      return (size_t)drwav_read_pcm_frames_s32(&wav, framesToRead, pBufferOut);
    }
    return 0;
  }

  void close() {
    if (codec == AudioCodec::FLAC && pFlac != nullptr) {
      drflac_close(pFlac);
      pFlac = nullptr;
    } else if (codec == AudioCodec::WAV && isWavInit) {
      drwav_uninit(&wav);
      isWavInit = false;
    }
    codec = AudioCodec::UNKNOWN;
    sampleRate = 0;
    channels = 0;
    bitsPerSample = 0;
    totalPCMFrameCount = 0;
  }

  ~AudioDecoder() {
    close();
  }
};
