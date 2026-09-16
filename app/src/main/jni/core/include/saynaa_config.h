#pragma once

#include <android/log.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <stdint.h>


#define SAYNAA_TAG "saynaaruntime"

#ifdef DEBUG
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, SAYNAA_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, SAYNAA_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, SAYNAA_TAG, __VA_ARGS__)

#define TID() ((long) syscall(SYS_gettid))

#define TRACE(fmt, ...) LOGI("[TRACE] tid=%ld " fmt, TID(), ##__VA_ARGS__)
#else
#define LOGD(...) ((void) 0)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, SAYNAA_TAG, __VA_ARGS__)
#define LOGI(...) ((void) 0)
#define TRACE(fmt, ...) ((void) 0)

#endif