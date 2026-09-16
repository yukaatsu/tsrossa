#ifndef JNI_H
#define JNI_H
#define JNIEXPORT
#define JNICALL
typedef int jint;
typedef int jboolean;
typedef void* jobject;
typedef void* jstring;
typedef void* jclass;
typedef void* jmethodID;
typedef void* jfloat;
typedef double jdouble;
#define JNI_TRUE 1
#define JNI_FALSE 0
struct JNIEnv {
    int GetMethodID(...) { return 0; }
    void CallVoidMethod(...) {}
    const char* GetStringUTFChars(...) { return "mock"; }
    void ReleaseStringUTFChars(...) {}
    jobject NewGlobalRef(...) { return nullptr; }
    void DeleteGlobalRef(...) {}
    jclass GetObjectClass(...) { return nullptr; }
    jstring NewStringUTF(...) { return nullptr; }
    void DeleteLocalRef(...) {}
};
#endif
