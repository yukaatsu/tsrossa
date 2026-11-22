# ============================================================================
# PROGUARD / R8 RULES UNTUK TSROSSA (NATIVE C++17 JNI & USB DAC ENGINE)
# ============================================================================

# 1. WAJIB: Jaga seluruh method external JNI dan 5 callback C++ -> Kotlin di AudioEngine
#    (onTrackFinished, onTrackTransition, onUsbStallFault, onDeviceForceDisconnected, onFormatIncompatible)
-keep class com.yuka.musicplayer.audio.AudioEngine {
    *;
}
-keep class com.yuka.musicplayer.audio.AudioEngine$Companion {
    *;
}

# 2. Jaga semua method native di seluruh proyek
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# 3. Jaga model data PEQ & TrackInfo
-keep class com.yuka.musicplayer.audio.PeqBand { *; }
-keep class com.yuka.musicplayer.audio.PeqConfiguration { *; }
-keep class com.yuka.musicplayer.audio.UserPeqPreset { *; }

# 4. Abaikan warning refleksi Hidden API Android AudioSystem di PhoneSpeakerRoutingCoordinator
-dontwarn android.media.AudioSystem
