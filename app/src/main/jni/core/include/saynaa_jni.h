#pragma once

#include <jni.h>

VM* vm_from_saynaa(JNIEnv* env, jobject saynaaObject);
void set_vm_ptr_on_saynaa(JNIEnv* env, jobject saynaaObject, jlong ptr);

