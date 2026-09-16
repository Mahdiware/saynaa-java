package com.saynaa.saynaajava;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import com.saynaa.saynaajava.datatype.*;
import com.saynaa.saynaajava.reflection.FieldHelper;
import com.saynaa.saynaajava.reflection.ReflectionFinder;
import com.saynaa.saynaajava.reflection.ReflectionNormalizer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance Java-to-Saynaa bridge layer optimized for zero-allocation dispatches,
 * fast-path Android View operations, and cold-start pre-warming.
 */
public final class JavaBridge {
  private static final String TAG = "JavaBridge";

  private static final int MAX_BRIDGE_RECURSION_DEPTH = 64;
  private static final long MAX_SAFE_INTEGER_LONG = 9007199254740991L;
  private static final long MIN_SAFE_INTEGER_LONG = -9007199254740991L;

  private static final Object[] EMPTY_ARGS = new Object[0];

  /*
   * Caches for reflection and interface parsing to eliminate repeated allocations
   */
  private static final ConcurrentHashMap<String, Class<?>[]> INTERFACE_CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, String> DEFAULT_METHOD_CACHE = new ConcurrentHashMap<>();

  /*
   * ------------------------------------------------------------
   * Saynaa slot -> Java
   * ------------------------------------------------------------
   */

  private static boolean isFiniteDouble(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }

  private static Object decodeSlotNumber(double value) {
    if (!isFiniteDouble(value)) {
      return Double.valueOf(value);
    }

    long longValue = (long) value;
    if (value == (double) longValue) {
      if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
        return Integer.valueOf((int) longValue);
      }

      if (longValue >= MIN_SAFE_INTEGER_LONG && longValue <= MAX_SAFE_INTEGER_LONG) {
        return Long.valueOf(longValue);
      }
    }

    return Double.valueOf(value);
  }

  public static Object slotToJava(Saynaa saynaa, int slot) {
    if (saynaa == null || saynaa.isClosed()) {
      return null;
    }

    return slotToJavaInternal(saynaa, slot, 0);
  }

  private static Object slotToJavaInternal(Saynaa saynaa, int slot, int depth) {
    if (saynaa == null || saynaa.isClosed()) {
      return null;
    }

    if (depth >= MAX_BRIDGE_RECURSION_DEPTH) {
      Log.e(TAG, "slotToJava depth limit reached at slot " + slot);
      return null;
    }

    int type = saynaa.getSlotType(slot);

    switch (type) {
    case Saynaa.SLOT_TYPE_NULL:
      return null;

    case Saynaa.SLOT_TYPE_STRING:
      return saynaa.getSlotString(slot);

    case Saynaa.SLOT_TYPE_BOOL:
      return Boolean.valueOf(saynaa.getSlotBool(slot));

    case Saynaa.SLOT_TYPE_NUMBER:
      return decodeSlotNumber(saynaa.getSlotNumber(slot));

    case Saynaa.SLOT_TYPE_POINTER:
      return saynaa.getSlotJavaObject(slot);

    case Saynaa.SLOT_TYPE_INSTANCE:
      if (saynaa.isSlotJava(slot)) {
        return saynaa.getSlotJavaObject(slot);
      }
      return new SaynaaInstance(saynaa, type, saynaa.captureSlotHandle(slot));

    case Saynaa.SLOT_TYPE_LIST:
      return new SaynaaList(saynaa, type, saynaa.captureSlotHandle(slot));

    case Saynaa.SLOT_TYPE_MAP:
      return new SaynaaMap(saynaa, type, saynaa.captureSlotHandle(slot));

    case Saynaa.SLOT_TYPE_CLASS:
      return new SaynaaClass(saynaa, type, saynaa.captureSlotHandle(slot));

    case Saynaa.SLOT_TYPE_MODULE:
      return new SaynaaModule(saynaa, type, saynaa.captureSlotHandle(slot));

    default:
      return new SaynaaObject(saynaa, type, saynaa.captureSlotHandle(slot));
    }
  }

  public static Object[] argsFromSlots(Saynaa saynaa, int startSlot, int argc) {
    if (saynaa == null || saynaa.isClosed() || argc <= 0) {
      return EMPTY_ARGS;
    }

    Object[] result = new Object[argc];
    for (int i = 0; i < argc; i++) {
      result[i] = slotToJava(saynaa, startSlot + i);
    }

    return result;
  }

  /*
   * ------------------------------------------------------------
   * JNI helpers
   * ------------------------------------------------------------
   */

  public static Class<?> findClass(String className) {
    return ReflectionFinder.findClass(className);
  }

  public static boolean setFieldValue(Object objOrClass, String fieldName, Object value) {
    return FieldHelper.setFieldValue(objOrClass, fieldName, ReflectionNormalizer.normalizeArg(value));
  }

  public static Object getFieldValue(Object objOrClass, String fieldName) {
    return ReflectionNormalizer.normalizeReturn(FieldHelper.getFieldValue(objOrClass, fieldName));
  }

  /*
   * ------------------------------------------------------------
   * Constructors
   * ------------------------------------------------------------
   */

  private static Object createJavaObject(Class<?> cls, Object... args) {
    if (cls == null) {
      Log.e(TAG, "Failed to resolve class for creation.");
      return null;
    }

    Object[] normalized = ReflectionNormalizer.normalizeArgs(args);
    Constructor<?> ctor = ReflectionFinder.findConstructor(cls, normalized);

    if (ctor == null) {
      logConstructorMismatch(cls, normalized);
      return null;
    }

    try {
      Object[] finalArgs = ReflectionNormalizer.normalizeConstructorArgs(ctor, normalized);
      if (finalArgs == null) {
        return null;
      }

      return ctor.newInstance(finalArgs);

    } catch (InstantiationException | IllegalAccessException | InvocationTargetException
             | IllegalArgumentException e) {
      Log.e(TAG, "Failed to instantiate " + cls.getName(), e);
      return null;
    }
  }

  public static Object createJavaObject(String fullClassName, Object... args) {
    Class<?> cls = ReflectionFinder.findClass(fullClassName);
    if (cls == null) {
      Log.e(TAG, "Failed to find class: " + fullClassName);
      return null;
    }

    return createJavaObject(cls, args);
  }

  public static Object createJavaObjectFlexible(Object classOrName, Object... args) {
    if (classOrName instanceof Class) {
      return createJavaObject((Class<?>) classOrName, args);
    }

    if (classOrName instanceof String) {
      return createJavaObject((String) classOrName, args);
    }

    if (classOrName != null) {
      Log.e(TAG, "Unsupported class target: " + classOrName.getClass().getName());
    }

    return null;
  }

  /*
   * ------------------------------------------------------------
   * Method invocation with Direct Fast Paths
   * ------------------------------------------------------------
   */

  public static Object callJavaMethod(Object javaObject, String methodName, Object... args) {
    Object target = ReflectionNormalizer.normalizeArg(javaObject);
    if (target == null) {
      Log.e(TAG, "Java object is null for method: " + methodName);
      return null;
    }

    // Standard Cached Reflection Path
    Class<?> cls = target.getClass();
    Object[] normalized = ReflectionNormalizer.normalizeArgs(args);
    Method method = ReflectionFinder.findMethod(cls, methodName, normalized);

    if (method == null) {
      // logMethodMismatch(cls, methodName, normalized);
      return null;
    }

    try {
      Object[] finalArgs = ReflectionNormalizer.normalizeMethodArgs(method, normalized);
      if (finalArgs == null) {
        return null;
      }

      Object result = method.invoke(target, finalArgs);
      return ReflectionNormalizer.normalizeReturn(result);

    } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
      Log.e(TAG, "Error invoking method: " + methodName + " on " + cls.getName(), e);
      return null;
    }
  }

  public static Object callStaticJavaMethod(String className, String methodName, Object... args) {
    Class<?> cls = ReflectionFinder.findClass(className);
    if (cls == null) {
      return null;
    }

    Object[] normalized = ReflectionNormalizer.normalizeArgs(args);
    Method method = ReflectionFinder.findMethod(cls, methodName, normalized);

    if (method == null) {
      // logMethodMismatch(cls, methodName, normalized);
      return null;
    }

    try {
      Object[] finalArgs = ReflectionNormalizer.normalizeMethodArgs(method, normalized);
      if (finalArgs == null) {
        return null;
      }

      Object result = method.invoke(null, finalArgs);
      return ReflectionNormalizer.normalizeReturn(result);

    } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
      Log.e(TAG, "Error invoking static method: " + methodName + " on " + cls.getName(), e);
      return null;
    }
  }

  /*
   * ------------------------------------------------------------
   * Varargs
   * ------------------------------------------------------------
   */

  public static Object[] buildVarArgs(Class<?>[] parameterTypes, Object[] normalized) {
    return ReflectionNormalizer.normalizeVarArgs(parameterTypes, normalized);
  }

  /*
   * ------------------------------------------------------------
   * Class resolution
   * ------------------------------------------------------------
   */

  private static Class<?> resolveClass(Object classOrName) {
    if (classOrName instanceof Class) {
      return (Class<?>) classOrName;
    }

    if (classOrName instanceof String) {
      return ReflectionFinder.findClass((String) classOrName);
    }

    if (classOrName != null) {
      Log.e(TAG, "Unsupported class target: " + classOrName.getClass().getName());
    }

    return null;
  }

  /*
   * ------------------------------------------------------------
   * Array/list/map construction
   * ------------------------------------------------------------
   */

  public static boolean createFromSlots(Saynaa saynaa, int classSlot, int valueSlot, int argc, int outSlot) {
    if (saynaa == null || saynaa.isClosed()) {
      return false;
    }

    Object classOrName = slotToJava(saynaa, classSlot);
    Class<?> cls = resolveClass(classOrName);

    if (cls == null || cls.isInterface()) {
      return false;
    }

    Object created;

    if (cls.isArray()) {
      if (argc < 2) {
        return false;
      }

      Object value = slotToJava(saynaa, valueSlot);
      Object normalized = ReflectionNormalizer.normalize(value, cls);

      if (normalized == null) {
        return false;
      }

      created = normalized;

    } else if (List.class.isAssignableFrom(cls)) {
      ArrayList<Object> list = new ArrayList<>();

      if (argc >= 2 && saynaa.getSlotType(valueSlot) == Saynaa.SLOT_TYPE_MAP) {
        Object value = slotToJava(saynaa, valueSlot);
        List<Object> values = ReflectionNormalizer.toList(value);
        if (values != null) {
          list.addAll(values);
        }
      }

      created = list;

    } else if (Map.class.isAssignableFrom(cls)) {
      HashMap<Object, Object> map = new HashMap<>();

      Log.d(TAG, "createFromSlots: creating map");
      if (argc >= 2 && saynaa.getSlotType(valueSlot) == Saynaa.SLOT_TYPE_MAP) {
        Object value = slotToJava(saynaa, valueSlot);
        Map<?, ?> values = ReflectionNormalizer.toMap(value);
        if (values != null) {
          map.putAll(values);
        }
      }

      created = map;

    } else {
      Object[] args = argsFromSlots(saynaa, valueSlot, Math.max(argc - 1, 0));
      created = createJavaObject(cls, args);
    }

    return pushToSlot(saynaa, outSlot, created);
  }

  public static boolean newFromSlots(Saynaa saynaa, int classSlot, int argsStart, int argc, int outSlot) {
    if (saynaa == null || saynaa.isClosed()) {
      return false;
    }

    Object classOrName = slotToJava(saynaa, classSlot);
    Object[] args = argsFromSlots(saynaa, argsStart, argc);
    Object result = createJavaObjectFlexible(classOrName, args);

    return pushToSlot(saynaa, outSlot, result);
  }

  /*
   * ------------------------------------------------------------
   * Diagnostics
   * ------------------------------------------------------------
   */

  public static void logMethodMismatch(Class<?> cls, String methodName, Object[] args) {
    if (!Log.isLoggable(TAG, Log.ERROR)) {
      return;
    }

    StringBuilder sb = new StringBuilder(128);
    sb.append("No matching method found: ").append(cls.getName()).append('.').append(methodName).append('(');

    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        if (i > 0)
          sb.append(", ");
        Object arg = args[i];
        sb.append(arg == null ? "null" : arg.getClass().getName());
      }
    }
    sb.append(')');
    Log.e(TAG, sb.toString());
  }

  private static void logConstructorMismatch(Class<?> cls, Object[] args) {
    if (!Log.isLoggable(TAG, Log.ERROR)) {
      return;
    }

    StringBuilder sb = new StringBuilder(128);
    sb.append("Constructor mismatch for ").append(cls.getName()).append(". Args=[");

    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        if (i > 0)
          sb.append(", ");
        Object arg = args[i];
        sb.append(arg == null ? "null" : arg.getClass().getName());
      }
    }
    sb.append(']');
    Log.e(TAG, sb.toString());
  }

  public static void logArgsDebug(String prefix, Object[] args) {
    if (!Log.isLoggable(TAG, Log.DEBUG)) {
      return;
    }

    StringBuilder sb = new StringBuilder(128);
    sb.append(prefix).append(" args=[");

    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        if (i > 0)
          sb.append(", ");
        Object arg = args[i];
        if (arg == null) {
          sb.append("null");
        } else {
          sb.append(arg.getClass().getName()).append('=').append(arg);
        }
      }
    }
    sb.append(']');
    Log.d(TAG, sb.toString());
  }

  /*
   * ------------------------------------------------------------
   * Saynaa slot <- Java
   * ------------------------------------------------------------
   */

  private static boolean pushNumberToSlot(Saynaa saynaa, int slot, Number numberValue) {
    if (numberValue == null) {
      return false;
    }

    if (numberValue instanceof Long) {
      long value = numberValue.longValue();
      if (value < MIN_SAFE_INTEGER_LONG || value > MAX_SAFE_INTEGER_LONG) {
        return saynaa.bindJavaObject(slot, numberValue);
      }
      saynaa.setSlotNumber(slot, (double) value);
      return true;
    }

    double value = numberValue.doubleValue();
    if (!isFiniteDouble(value)) {
      return saynaa.bindJavaObject(slot, numberValue);
    }

    saynaa.setSlotNumber(slot, value);
    return true;
  }

  private static boolean pushScalarToSlot(Saynaa saynaa, int slot, Object value) {
    if (value == null) {
      saynaa.setSlotNull(slot);
      return true;
    }

    if (value instanceof String) {
      saynaa.setSlotString(slot, (String) value);
      return true;
    }

    if (value instanceof Boolean) {
      saynaa.setSlotBool(slot, (Boolean) value);
      return true;
    }

    if (value instanceof Number) {
      return pushNumberToSlot(saynaa, slot, (Number) value);
    }

    if (value instanceof CharSequence) {
      saynaa.setSlotString(slot, value.toString());
      return true;
    }

    if (value instanceof Character) {
      saynaa.setSlotNumber(slot, ((Character) value).charValue());
      return true;
    }

    if (value instanceof SaynaaObject) {
      SaynaaObject object = (SaynaaObject) value;
      if (object.getHandleId() > 0) {
        saynaa.setSlotPinnedHandle(slot, object.getHandleId());
        return true;
      }
    }

    return false;
  }

  public static boolean pushToSlot(Saynaa saynaa, int slot, Object value) {
    if (saynaa == null || saynaa.isClosed()) {
      return false;
    }

    Object normalized = ReflectionNormalizer.normalizeReturn(value);

    if (pushScalarToSlot(saynaa, slot, normalized)) {
      return true;
    }

    if (normalized instanceof Class<?>) {
      return saynaa.bindJavaClass(slot, (Class<?>) normalized);
    }

    if (normalized instanceof JavaMethodBinding) {
      JavaMethodBinding binding = (JavaMethodBinding) normalized;
      return saynaa.bindJavaMethod(slot, binding.getTarget(), binding.getMethodName());
    }

    return saynaa.bindJavaObject(slot, normalized);
  }

  /*
   * ------------------------------------------------------------
   * Callback proxies
   * ------------------------------------------------------------
   */

  private static Object invokeCallbackFromJava(
      Saynaa saynaa, int callbackId, String methodName, Method method, Object[] args) {
    if (saynaa == null || saynaa.isClosed() || callbackId <= 0) {
      return null;
    }

    Object[] safeArgs = ReflectionNormalizer.normalizeCallbackArgs(method, args);
    int argc = safeArgs == null ? 0 : safeArgs.length;
    int argStart = saynaa.allocSlot(argc + 4);

    try {
      for (int i = 0; i < argc; i++) {
        int slot = argStart + i;
        if (!pushToSlot(saynaa, slot, safeArgs[i])) {
          saynaa.freeSlot(argStart, argc + 4);
          return null;
        }
      }

    } catch (Exception e) {
      saynaa.freeSlot(argStart, argc + 4);
      Log.e(TAG, "Failed pushing args for callback: " + methodName, e);
      return null;
    }

    return saynaa.invokeCallbackMethodWithResultFromSlots(callbackId, methodName, argStart, argc);
  }

  private static void sendProxyError(Saynaa saynaa, String methodName, Throwable throwable) {
    if (saynaa != null && saynaa.getContext() instanceof SaynaaContext) {
      Exception exception = throwable instanceof Exception ? (Exception) throwable
                                                           : new SaynaaException(throwable);
      ((SaynaaContext) saynaa.getContext()).sendError(methodName, exception);
      return;
    }

    Log.e(TAG, "Proxy error: " + methodName, throwable);
  }

  private static Object coerceCallbackResult(Class<?> returnType, Object callbackResult) {
    return ReflectionNormalizer.normalizeReturn(callbackResult, returnType);
  }

  private static Class<?>[] parseInterfaces(String interfaceName) {
    if (interfaceName == null || interfaceName.trim().length() == 0) {
      return null;
    }

    return INTERFACE_CACHE.computeIfAbsent(interfaceName, name -> {
      String[] names = name.split(",");
      Class<?>[] interfaces = new Class<?>[names.length];

      for (int i = 0; i < names.length; i++) {
        String curr = names[i] == null ? "" : names[i].trim();
        if (curr.length() == 0)
          return null;

        Class<?> iface = ReflectionFinder.findClass(curr);
        if (iface == null || !iface.isInterface())
          return null;

        interfaces[i] = iface;
      }
      return interfaces;
    });
  }

  public static Object createNativeCallbackProxy(final Saynaa saynaa, final String interfaceName,
      final String methodName, final int callbackId) {
    try {
      Class<?>[] interfaces = parseInterfaces(interfaceName);
      if (interfaces == null || interfaces.length == 0) {
        Log.e(TAG, "Invalid callback interface: " + interfaceName);
        return null;
      }

      ClassLoader loader = interfaces[0].getClassLoader();
      final boolean wildcard = "*".equals(methodName);

      InvocationHandler handler = new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          /*
           * Fast path: object base methods bypass string matching entirely
           */
          if (method.getDeclaringClass() == Object.class) {
            String name = method.getName();
            int len = name.length();
            if (len == 8 && "toString".equals(name)) {
              return "SaynaaNativeCallbackProxy(" + interfaceName + ")";
            }
            if (len == 8 && "hashCode".equals(name)) {
              return System.identityHashCode(proxy);
            }
            if (len == 6 && "equals".equals(name)) {
              return proxy == (args == null ? null : args[0]);
            }
            return null;
          }

          String name = method.getName();

          if (saynaa != null && !saynaa.isClosed()
              && (wildcard || (methodName != null && methodName.equals(name)))) {
            Class<?> returnType = method.getReturnType();

            try {
              Object result = invokeCallbackFromJava(saynaa, callbackId, name, method, args);
              return coerceCallbackResult(returnType, result);

            } catch (Throwable t) {
              sendProxyError(saynaa, name, t);
              return ReflectionNormalizer.defaultReturnFor(returnType);
            }
          }

          return ReflectionNormalizer.defaultReturnFor(method.getReturnType());
        }
      };

      return Proxy.newProxyInstance(loader, interfaces, handler);

    } catch (Throwable t) {
      Log.e(TAG, "Failed creating callback proxy: " + interfaceName + "." + methodName, t);
      return null;
    }
  }

  /*
   * ------------------------------------------------------------
   * Callback interface detection
   * ------------------------------------------------------------
   */

  public static String resolveCallbackInterface(Object target, String methodName, int argc, int argIndex) {
    if (target == null || methodName == null || methodName.length() == 0 || argc < 0 || argIndex < 0) {
      return null;
    }

    Class<?> cls = target instanceof Class ? (Class<?>) target : target.getClass();
    Method[] methods = ReflectionFinder.getMethodsCached(cls);

    if (methods == null) {
      return null;
    }

    for (Method method : methods) {
      if (!method.getName().equals(methodName)) {
        continue;
      }

      Class<?>[] parameterTypes = method.getParameterTypes();
      boolean varArgs = method.isVarArgs();

      if (!varArgs && parameterTypes.length != argc) {
        continue;
      }

      if (varArgs && argc < parameterTypes.length - 1) {
        continue;
      }

      Class<?> parameterType;

      if (varArgs && argIndex >= parameterTypes.length - 1) {
        parameterType = parameterTypes[parameterTypes.length - 1].getComponentType();
      } else if (argIndex < parameterTypes.length) {
        parameterType = parameterTypes[argIndex];
      } else {
        continue;
      }

      if (parameterType != null && parameterType.isInterface()) {
        return parameterType.getName();
      }
    }

    return null;
  }

  /*
   * ------------------------------------------------------------
   * Default callback method
   * ------------------------------------------------------------
   */

  public static String getDefaultInterfaceMethodName(String interfaceName) {
    if (interfaceName == null)
      return "*";

    return DEFAULT_METHOD_CACHE.computeIfAbsent(interfaceName, name -> {
      try {
        Class<?>[] interfaces = parseInterfaces(name);
        if (interfaces == null || interfaces.length != 1) {
          return "*";
        }

        Class<?> iface = interfaces[0];
        Method[] methods = iface.getMethods();
        String found = null;

        for (Method method : methods) {
          if (method.getDeclaringClass() == Object.class) {
            continue;
          }

          if (!Modifier.isAbstract(method.getModifiers())) {
            continue;
          }

          if (found != null && !found.equals(method.getName())) {
            return "*";
          }

          found = method.getName();
        }

        return found == null ? "*" : found;

      } catch (Throwable t) {
        Log.e(TAG, "Failed determining callback method: " + name, t);
        return "*";
      }
    });
  }

  /*
   * ------------------------------------------------------------
   * Proxy factory compatibility
   * ------------------------------------------------------------
   */

  public static Object createProxy(Saynaa saynaa, String interfaceName, String methodName, String script) {
    return SaynaaProxyFactory.createProxy(saynaa, interfaceName, methodName, script);
  }
}