#include "saynaa_exports.h"
#include "saynaa_internal.h"
#include "saynaa_jni.h"

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
  (void) reserved;
  JNIEnv* env = NULL;

  if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_6) != JNI_OK) {
    return JNI_ERR;
  }

  return JNI_VERSION_1_6;
}
