#pragma once
#include <jni.h>
#include <array>
#include <cstdint>

namespace player2::platform {
inline int fileDescriptor(JNIEnv* env,jobject descriptor) {
    if(!descriptor)return -1;
    auto type=env->GetObjectClass(descriptor);if(!type)return -1;
    // Android names this field descriptor; the host JVM uses fd.
    auto field=env->GetFieldID(type,"descriptor","I");
    if(!field) {
        auto exception=env->ExceptionOccurred();env->ExceptionClear();
        auto missing=env->FindClass("java/lang/NoSuchFieldError");
        const bool fallback=missing && env->IsInstanceOf(exception,missing);
        if(missing)env->DeleteLocalRef(missing);
        if(!fallback) {
            if(!env->ExceptionCheck() && exception)env->Throw(exception);
            if(exception)env->DeleteLocalRef(exception);
            env->DeleteLocalRef(type);return -1;
        }
        if(exception)env->DeleteLocalRef(exception);
        field=env->GetFieldID(type,"fd","I");
    }
    const auto value=field ? env->GetIntField(descriptor,field) : -1;
    env->DeleteLocalRef(type);return value;
}
inline bool sourceDigest(JNIEnv* env,jbyteArray input,std::array<std::uint8_t,32>& storage,
    const std::array<std::uint8_t,32>*& expected) {
    expected=nullptr;
    if(!input)return true;
    if(env->GetArrayLength(input)!=32)return false;
    env->GetByteArrayRegion(input,0,32,reinterpret_cast<jbyte*>(storage.data()));
    if(env->ExceptionCheck())return false;
    expected=&storage;return true;
}
}
