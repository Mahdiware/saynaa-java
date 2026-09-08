#include "saynaa_internal.h"

/* ============================================================================
 * Internal Helpers & Allocation
 * ============================================================================ */

static inline void* alloc_java_instance(JavaNativeType type) {
  JavaNativeBase* inst = (JavaNativeBase*) calloc(1, sizeof(JavaNativeBase));
  if (inst != NULL) {
    inst->type = type;
  }
  return inst;
}

static inline bool get_bridge_and_env(VM* vm, JavaNativeBase* thiz, BridgeState** out_bridge, JNIEnv** out_env) {
  if (thiz == NULL || thiz->reference == NULL)
    return false;

  BridgeState* bridge = bridge_from_vm(vm);
  if (bridge == NULL || bridge->jvm == NULL)
    return false;

  JNIEnv* env = env_from_jvm(bridge->jvm);
  if (env == NULL) {
    SetRuntimeError(vm, "Invalid JNI Environment.");
    return false;
  }

  *out_bridge = bridge;
  *out_env = env;
  return true;
}

/* ============================================================================
 * Lifecycle Callbacks
 * ============================================================================ */

void* new_java_method_instance(VM* vm) {
  (void) vm;
  return alloc_java_instance(JAVA_METHOD);
}
void* new_java_class_instance(VM* vm) {
  (void) vm;
  return alloc_java_instance(JAVA_CLASS);
}
void* new_java_object_instance(VM* vm) {
  (void) vm;
  return alloc_java_instance(JAVA_OBJECT);
}

void delete_java_instance(VM* vm, void* ptr) {
  (void) vm;
  JavaNativeBase* inst = (JavaNativeBase*) ptr;
  if (inst == NULL)
    return;

  if (inst->reference != NULL) {
    java_ref_destructor(inst->reference);
  }
  if (inst->type == JAVA_METHOD && inst->method_name != NULL) {
    free(inst->method_name);
  }
  free(inst);
}

void java_init(VM* vm) {
  JavaNativeBase* thiz = (JavaNativeBase*) GetThis(vm);
  if (thiz == NULL || !ValidateSlotType(vm, 1, vPOINTER))
    return;

  if (thiz->type == JAVA_METHOD) {
    if (!ValidateSlotString(vm, 2, NULL, NULL))
      return;

    const char* method_name = GetSlotString(vm, 2, NULL);
    thiz->method_name = str_dup_c(method_name);
    if (GetArgc(vm) >= 3) {
      thiz->is_static = GetSlotBool(vm, 3);
    }
  }

  thiz->reference = (JavaRef*) GetSlotPointer(vm, 1, NULL, NULL);
}

/* ============================================================================
 * JavaClass Operations
 * ============================================================================ */

static bool try_resolve_nested_class(
    VM* vm, JNIEnv* env, BridgeState* bridge, jobject classObj, const char* name) {
  if (name == NULL || name[0] == '\0' || bridge->mFindClass == NULL)
    return false;

  bool handled = false;
  jstring ownerNameObj = get_java_object_name(env, vm, bridge, classObj,
      "JavaClass._getter getName() failed", "JavaClass._getter failed to resolve class name.");

  if (ownerNameObj == NULL)
    return false;

  const char* ownerName = (*env)->GetStringUTFChars(env, ownerNameObj, NULL);
  if (ownerName != NULL) {
    size_t ownerLen = strlen(ownerName);
    size_t childLen = strlen(name);
    char* nestedName = (char*) malloc(ownerLen + 1 + childLen + 1);

    if (nestedName != NULL) {
      memcpy(nestedName, ownerName, ownerLen);
      nestedName[ownerLen] = '$';
      memcpy(nestedName + ownerLen + 1, name, childLen);
      nestedName[ownerLen + 1 + childLen] = '\0';

      jstring jNested = (*env)->NewStringUTF(env, nestedName);
      free(nestedName);

      if (jNested != NULL) {
        jobject nestedClass = (*env)->CallStaticObjectMethod(
            env, bridge->javaBridgeClass, bridge->mFindClass, jNested);
        (*env)->DeleteLocalRef(env, jNested);

        if ((*env)->ExceptionCheck(env)) {
          throw_if_exception(vm, env, "JavaClass._getter nested class lookup failed");
          handled = true; // Exception raised; halt further getter steps
        } else if (nestedClass != NULL) {
          JavaRef* ref = make_java_ref(env, bridge->jvm, nestedClass);
          (*env)->DeleteLocalRef(env, nestedClass);

          if (ref == NULL) {
            SetRuntimeError(vm, "Failed to wrap nested Java class reference.");
          } else {
            create_java_instance(vm, &bridge->clsJavaClass, ref, 0);
          }
          handled = true;
        }
      }
    } else {
      SetRuntimeError(vm, "Out of memory.");
      handled = true;
    }
    (*env)->ReleaseStringUTFChars(env, ownerNameObj, ownerName);
  }
  (*env)->DeleteLocalRef(env, ownerNameObj);
  return handled;
}

static bool try_resolve_static_field(
    VM* vm, JNIEnv* env, BridgeState* bridge, jobject classObj, const char* name) {
  if (bridge->mGetFieldValue == NULL)
    return false;

  jstring jField = (*env)->NewStringUTF(env, name == NULL ? "" : name);
  if (jField == NULL)
    return false;

  jobject fieldValue = (*env)->CallStaticObjectMethod(
      env, bridge->javaBridgeClass, bridge->mGetFieldValue, classObj, jField);
  (*env)->DeleteLocalRef(env, jField);

  if ((*env)->ExceptionCheck(env)) {
    throw_if_exception(vm, env, "JavaClass._getter field access failed");
    return true;
  }

  if (fieldValue != NULL) {
    object_to_slot(env, vm, bridge, 0, fieldValue, "Failed to wrap Java result object.");
    (*env)->DeleteLocalRef(env, fieldValue);
    return true;
  }

  return false;
}

void java_class_getter(VM* vm) {
  JavaNativeBase* thiz = (JavaNativeBase*) GetThis(vm);
  BridgeState* bridge;
  JNIEnv* env;

  if (!get_bridge_and_env(vm, thiz, &bridge, &env) || !ValidateSlotString(vm, 1, NULL, NULL))
    return;

  if (bridge->javaBridgeClass == NULL) {
    SetRuntimeError(vm, "Java bridge is not initialized.");
    return;
  }

  const char* name = GetSlotString(vm, 1, NULL);
  jobject classObj = (*env)->NewLocalRef(env, thiz->reference->global);

  if (classObj != NULL) {
    // 1. Attempt nested class resolution first
    if (try_resolve_nested_class(vm, env, bridge, classObj, name)) {
      (*env)->DeleteLocalRef(env, classObj);
      return;
    }

    // 2. Attempt static field access next
    if (try_resolve_static_field(vm, env, bridge, classObj, name)) {
      (*env)->DeleteLocalRef(env, classObj);
      return;
    }

    (*env)->DeleteLocalRef(env, classObj);
  }

  // 3. Fallback to method instance target creation
  JavaRef* target = clone_java_ref(env, thiz->reference);
  if (target == NULL) {
    SetRuntimeError(vm, "Failed to clone Java class reference.");
    return;
  }

  create_java_method_instance(vm, target, name, true, 0);
}

void java_class_str(VM* vm) {
  (void) vm;
  setSlotString(vm, 0, "<JavaClass>");
}

static bool handle_interface_proxy_call(
    VM* vm, JNIEnv* env, BridgeState* bridge, jobject classNameObj, jobject classObj) {
  VarType callbackType = GetSlotType(vm, 1);
  if (callbackType != vMAP && callbackType != vCLOSURE && callbackType != vSTRING) {
    return false;
  }

  const char* methodName = "*";
  jobject inferredObj = NULL;
  const char* inferred = NULL;

  if (bridge->mGetDefaultInterfaceMethodName != NULL) {
    inferredObj = (*env)->CallStaticObjectMethod(
        env, bridge->javaBridgeClass, bridge->mGetDefaultInterfaceMethodName, classNameObj);
    if ((*env)->ExceptionCheck(env)) {
      throw_if_exception(vm, env, "JavaClass._call infer method name failed");
      return true;
    }

    if (inferredObj != NULL) {
      inferred = (*env)->GetStringUTFChars(env, (jstring) inferredObj, NULL);
      if (inferred != NULL && inferred[0] != '\0') {
        methodName = inferred;
      }
    }
  }

  bool handled = false;

  if (callbackType == vCLOSURE && strcmp(methodName, "*") == 0) {
    SetRuntimeError(vm, "InterfaceClass(function) requires SAM interface or explicit "
                        "createProxy(interface, method, fn).");
    handled = true;
  } else if (callbackType == vSTRING) {
    const char* script = GetSlotString(vm, 1, NULL);
    jobject saynaaObj = (*env)->NewLocalRef(env, bridge->saynaaObject);
    if (saynaaObj == NULL) {
      SetRuntimeError(vm, "Failed to access Saynaa object.");
      handled = true;
    } else {
      jstring jMethod = (*env)->NewStringUTF(env, methodName);
      jstring jScript = (*env)->NewStringUTF(env, script == NULL ? "" : script);

      jobject proxy = (*env)->CallStaticObjectMethod(env, bridge->javaBridgeClass,
          bridge->mCreateProxy, saynaaObj, classNameObj, jMethod, jScript);

      if (jScript)
        (*env)->DeleteLocalRef(env, jScript);
      if (jMethod)
        (*env)->DeleteLocalRef(env, jMethod);
      (*env)->DeleteLocalRef(env, saynaaObj);

      if ((*env)->ExceptionCheck(env)) {
        throw_if_exception(vm, env, "JavaClass._call createProxy failed");
      } else {
        object_to_slot(env, vm, bridge, 0, proxy, "Failed to wrap Java result object.");
        if (proxy != NULL)
          (*env)->DeleteLocalRef(env, proxy);
      }
      handled = true;
    }
  } else {
    int callbackId = (callbackType == vCLOSURE) ? register_callback(vm, 1)
                                                : register_map_callback(vm, 1, methodName);
    if (callbackId > 0) {
      jobject proxy = create_native_callback_proxy(env, vm, bridge, classNameObj, methodName, callbackId);
      if (proxy != NULL) {
        object_to_slot(env, vm, bridge, 0, proxy, "Failed to wrap Java result object.");
        (*env)->DeleteLocalRef(env, proxy);
      }
      handled = true;
    }
  }

  // Cleanup inferred resources safely
  if (inferredObj != NULL) {
    if (inferred != NULL)
      (*env)->ReleaseStringUTFChars(env, (jstring) inferredObj, inferred);
    (*env)->DeleteLocalRef(env, inferredObj);
  }

  return handled;
}

void java_class_call(VM* vm) {
  JavaNativeBase* thiz = (JavaNativeBase*) GetThis(vm);
  BridgeState* bridge;
  JNIEnv* env;

  if (!get_bridge_and_env(vm, thiz, &bridge, &env)) {
    LOGE("Invalid JavaClass instance or JVM state.");
    SetRuntimeError(vm, "Invalid JavaClass instance.");
    return;
  }

  jobject classObj = (*env)->NewLocalRef(env, thiz->reference->global);
  if (classObj == NULL) {
    LOGE("JavaClass._call failed to access class reference.");
    SetRuntimeError(vm, "JavaClass._call failed to access class reference.");
    return;
  }

  bool isInterface = false;
  jclass clsClass = (*env)->FindClass(env, "java/lang/Class");
  if (clsClass != NULL) {
    jmethodID midIsInterface = (*env)->GetMethodID(env, clsClass, "isInterface", "()Z");
    if (midIsInterface != NULL) {
      jboolean result = (*env)->CallBooleanMethod(env, classObj, midIsInterface);
      if ((*env)->ExceptionCheck(env)) {
        (*env)->DeleteLocalRef(env, clsClass);
        (*env)->DeleteLocalRef(env, classObj);
        throw_if_exception(vm, env, "JavaClass._call isInterface failed");
        return;
      }
      isInterface = (result == JNI_TRUE);
    }
    (*env)->DeleteLocalRef(env, clsClass);
  }

  jstring classNameObj = get_java_object_name(env, vm, bridge, classObj,
      "JavaClass._call getName() failed", "JavaClass._call failed to resolve class name.");
  if (classNameObj == NULL) {
    (*env)->DeleteLocalRef(env, classObj);
    return;
  }

  int argc = GetArgc(vm);

  // AndLua-compatible interface proxy creation sugar
  if (isInterface && argc == 1) {
    if (handle_interface_proxy_call(vm, env, bridge, classNameObj, classObj)) {
      (*env)->DeleteLocalRef(env, classNameObj);
      (*env)->DeleteLocalRef(env, classObj);
      return;
    }
  }

  jobjectArray args = make_args_array(env, vm, bridge, 1, argc);
  if (VM_HAS_ERROR(vm) || (args == NULL && argc > 0)) {
    if (!VM_HAS_ERROR(vm))
      LOGE("JavaClass._call argument conversion failed.");
    SetRuntimeError(vm, "JavaClass._call argument conversion failed.");
    if (args != NULL)
      (*env)->DeleteLocalRef(env, args);
    (*env)->DeleteLocalRef(env, classNameObj);
    (*env)->DeleteLocalRef(env, classObj);
    return;
  }

  jobject obj = (*env)->CallStaticObjectMethod(
      env, bridge->javaBridgeClass, bridge->mCreateJavaObject, classNameObj, args);

  if (args != NULL)
    (*env)->DeleteLocalRef(env, args);

  if ((*env)->ExceptionCheck(env)) {
    (*env)->DeleteLocalRef(env, classNameObj);
    (*env)->DeleteLocalRef(env, classObj);
    throw_if_exception(vm, env, "JavaClass._call constructor failed");
    return;
  }

  if (obj == NULL) {
    const char* clsName = (*env)->GetStringUTFChars(env, classNameObj, NULL);
    LOGE("JavaClass._call returned null for class=%s", clsName ? clsName : "<unknown>");
    if (clsName != NULL)
      (*env)->ReleaseStringUTFChars(env, classNameObj, clsName);

    SetRuntimeError(
        vm, "JavaClass._call returned null. Check logcat for constructor mismatch or exception.");
  } else {
    object_to_slot(env, vm, bridge, 0, obj, "Failed to wrap Java result object.");
    (*env)->DeleteLocalRef(env, obj);
  }

  (*env)->DeleteLocalRef(env, classNameObj);
  (*env)->DeleteLocalRef(env, classObj);
}

/* ============================================================================
 * JavaObject Operations
 * ============================================================================ */

void java_object_getter(VM* vm) {
  JavaNativeBase* thiz = (JavaNativeBase*) GetThis(vm);
  BridgeState* bridge;
  JNIEnv* env;

  if (!get_bridge_and_env(vm, thiz, &bridge, &env) || !ValidateSlotString(vm, 1, NULL, NULL))
    return;

  const char* name = GetSlotString(vm, 1, NULL);

  if (bridge->javaBridgeClass != NULL && bridge->mGetFieldValue != NULL) {
    jobject obj = (*env)->NewLocalRef(env, thiz->reference->global);
    jstring jField = (*env)->NewStringUTF(env, name == NULL ? "" : name);

    if (obj != NULL && jField != NULL) {
      jobject fieldValue = (*env)->CallStaticObjectMethod(
          env, bridge->javaBridgeClass, bridge->mGetFieldValue, obj, jField);
      (*env)->DeleteLocalRef(env, jField);
      (*env)->DeleteLocalRef(env, obj);

      if ((*env)->ExceptionCheck(env)) {
        throw_if_exception(vm, env, "JavaObject._getter field access failed");
        return;
      }

      if (fieldValue != NULL) {
        object_to_slot(env, vm, bridge, 0, fieldValue, "Failed to wrap Java result object.");
        (*env)->DeleteLocalRef(env, fieldValue);
        return;
      }
    } else {
      if (jField)
        (*env)->DeleteLocalRef(env, jField);
      if (obj)
        (*env)->DeleteLocalRef(env, obj);
    }
  }

  JavaRef* target = clone_java_ref(env, thiz->reference);
  if (target == NULL) {
    SetRuntimeError(vm, "Failed to clone Java object reference.");
    return;
  }

  LOGD("Creating JavaMethod instance for field '%s' of JavaObject %p", name, (void*) thiz->reference->global);
  create_java_method_instance(vm, target, name, false, 0);
}

void java_object_setter(VM* vm) {
  JavaNativeBase* thiz = (JavaNativeBase*) GetThis(vm);
  BridgeState* bridge;
  JNIEnv* env;

  if (!get_bridge_and_env(vm, thiz, &bridge, &env) || !ValidateSlotString(vm, 1, NULL, NULL))
    return;

  jobject target = (*env)->NewLocalRef(env, thiz->reference->global);
  const char* fieldName = GetSlotString(vm, 1, NULL);
  jobject value = slot_to_java(env, vm, bridge, 2);

  jstring jFieldName = (*env)->NewStringUTF(env, fieldName == NULL ? "" : fieldName);

  jboolean ok = JNI_FALSE;
  if (target != NULL && jFieldName != NULL) {
    ok = (*env)->CallStaticBooleanMethod(
        env, bridge->javaBridgeClass, bridge->mSetFieldValue, target, jFieldName, value);
  }

  if (value != NULL)
    (*env)->DeleteLocalRef(env, value);
  if (target != NULL)
    (*env)->DeleteLocalRef(env, target);
  if (jFieldName != NULL)
    (*env)->DeleteLocalRef(env, jFieldName);

  if ((*env)->ExceptionCheck(env)) {
    throw_if_exception(vm, env, "JavaObject._setter failed");
    return;
  }

  setSlotBool(vm, 0, ok == JNI_TRUE);
}

void java_object_str(VM* vm) {
  JavaNativeBase* thiz = (JavaNativeBase*) GetThis(vm);
  BridgeState* bridge;
  JNIEnv* env;

  if (!get_bridge_and_env(vm, thiz, &bridge, &env) || thiz->reference->global == NULL
      || bridge->javaBridgeClass == NULL || bridge->mCallJavaMethod == NULL) {
    setSlotString(vm, 0, "<JavaObject>");
    return;
  }

  jobject target = (*env)->NewLocalRef(env, thiz->reference->global);
  if (target == NULL) {
    setSlotString(vm, 0, "<JavaObject>");
    return;
  }

  jstring jMethodName = (*env)->NewStringUTF(env, "toString");
  jclass objClass = safe_find_class(vm, env, "java/lang/Object", "java_object_str:Object");

  if (jMethodName == NULL || objClass == NULL) {
    if (jMethodName)
      (*env)->DeleteLocalRef(env, jMethodName);
    if (objClass)
      (*env)->DeleteLocalRef(env, objClass);
    (*env)->DeleteLocalRef(env, target);
    setSlotString(vm, 0, "<JavaObject>");
    return;
  }

  jobjectArray noArgs = (*env)->NewObjectArray(env, 0, objClass, NULL);
  (*env)->DeleteLocalRef(env, objClass);

  if (noArgs == NULL) {
    clear_jni_exception_with_log(env, "java_object_str:NewObjectArray");
    (*env)->DeleteLocalRef(env, jMethodName);
    (*env)->DeleteLocalRef(env, target);
    setSlotString(vm, 0, "<JavaObject>");
    return;
  }

  jobject ret = (*env)->CallStaticObjectMethod(
      env, bridge->javaBridgeClass, bridge->mCallJavaMethod, target, jMethodName, noArgs);

  (*env)->DeleteLocalRef(env, noArgs);
  (*env)->DeleteLocalRef(env, jMethodName);
  (*env)->DeleteLocalRef(env, target);

  if ((*env)->ExceptionCheck(env)) {
    throw_if_exception(vm, env, "JavaObject.__tostring failed");
    setSlotString(vm, 0, "<JavaObject>");
    return;
  }

  if (ret != NULL) {
    jclass stringClass = safe_find_class(vm, env, "java/lang/String", "java_object_str:String");
    if (stringClass != NULL) {
      if ((*env)->IsInstanceOf(env, ret, stringClass) == JNI_TRUE) {
        const char* s = (*env)->GetStringUTFChars(env, (jstring) ret, NULL);
        setSlotString(vm, 0, s == NULL ? "" : s);
        if (s != NULL)
          (*env)->ReleaseStringUTFChars(env, (jstring) ret, s);
        (*env)->DeleteLocalRef(env, stringClass);
        (*env)->DeleteLocalRef(env, ret);
        return;
      }
      (*env)->DeleteLocalRef(env, stringClass);
    }
    (*env)->DeleteLocalRef(env, ret);
  }

  setSlotString(vm, 0, "<JavaObject>");
}

/* ============================================================================
 * JavaMethod Operations
 * ============================================================================ */

void java_method_call(VM* vm) {
  JavaNativeBase* thiz = (JavaNativeBase*) GetThis(vm);
  BridgeState* bridge;
  JNIEnv* env;

  if (!get_bridge_and_env(vm, thiz, &bridge, &env) || thiz->type != JAVA_METHOD || thiz->method_name == NULL) {
    SetRuntimeError(vm, "Invalid JavaMethod instance.");
    return;
  }

  int argc = GetArgc(vm);

  // Callback interface resolution loop
  if (bridge->javaBridgeClass != NULL && bridge->mResolveCallbackInterface != NULL && argc > 0) {
    jstring jMethod = (*env)->NewStringUTF(env, thiz->method_name);
    jobject targetRef = (*env)->NewLocalRef(env, thiz->reference->global);

    if (jMethod != NULL && targetRef != NULL) {
      for (int i = 0; i < argc; i++) {
        int slot = 1 + i;
        if (GetSlotType(vm, slot) != vMAP)
          continue;

        jobject iface = (*env)->CallStaticObjectMethod(env, bridge->javaBridgeClass,
            bridge->mResolveCallbackInterface, targetRef, jMethod, (jint) argc, (jint) i);

        if ((*env)->ExceptionCheck(env)) {
          throw_if_exception(vm, env, "resolveCallbackInterface failed");
          if (iface != NULL)
            (*env)->DeleteLocalRef(env, iface);
          continue;
        }

        if (iface != NULL) {
          int callbackId = register_map_callback(vm, slot, "*");
          if (callbackId > 0) {
            jobject proxy = create_native_callback_proxy(env, vm, bridge, (jstring) iface, "*", callbackId);
            if (proxy != NULL) {
              object_to_slot(env, vm, bridge, slot, proxy, "Failed to wrap Java callback object.");
              (*env)->DeleteLocalRef(env, proxy);
            }
          }
          (*env)->DeleteLocalRef(env, iface);
        }
      }
    }
    if (targetRef != NULL)
      (*env)->DeleteLocalRef(env, targetRef);
    if (jMethod != NULL)
      (*env)->DeleteLocalRef(env, jMethod);
  }

  jobjectArray args = make_args_array(env, vm, bridge, 1, argc);
  jobject ret = NULL;

  if (thiz->is_static) {
    jobject classObj = (*env)->NewLocalRef(env, thiz->reference->global);
    jstring classNameObj = get_java_object_name(env, vm, bridge, classObj,
        "JavaMethod._call static getName failed", "JavaMethod._call static getName failed");
    (*env)->DeleteLocalRef(env, classObj);

    if (classNameObj == NULL) {
      if (args != NULL)
        (*env)->DeleteLocalRef(env, args);
      return;
    }

    jstring jMethod = (*env)->NewStringUTF(env, thiz->method_name);
    ret = (*env)->CallStaticObjectMethod(
        env, bridge->javaBridgeClass, bridge->mCallStaticJavaMethod, classNameObj, jMethod, args);

    if (jMethod)
      (*env)->DeleteLocalRef(env, jMethod);
    (*env)->DeleteLocalRef(env, classNameObj);
  } else {
    jobject target = (*env)->NewLocalRef(env, thiz->reference->global);
    jstring jMethod = (*env)->NewStringUTF(env, thiz->method_name);

    ret = (*env)->CallStaticObjectMethod(
        env, bridge->javaBridgeClass, bridge->mCallJavaMethod, target, jMethod, args);

    if (jMethod)
      (*env)->DeleteLocalRef(env, jMethod);
    if (target)
      (*env)->DeleteLocalRef(env, target);
  }

  if (args != NULL)
    (*env)->DeleteLocalRef(env, args);

  if ((*env)->ExceptionCheck(env)) {
    throw_if_exception(vm, env, "JavaMethod._call failed");
    return;
  }

  object_to_slot(env, vm, bridge, 0, ret, "Failed to wrap Java result object.");
  if (ret != NULL)
    (*env)->DeleteLocalRef(env, ret);
}

void java_method_str(VM* vm) {
  JavaNativeBase* thiz = (JavaNativeBase*) GetThis(vm);
  if (thiz != NULL && thiz->method_name != NULL) {
    char buffer[256];
    snprintf(buffer, sizeof(buffer), "<JavaMethod %s>", thiz->method_name);
    setSlotString(vm, 0, buffer);
  } else {
    setSlotString(vm, 0, "<JavaMethod>");
  }
}