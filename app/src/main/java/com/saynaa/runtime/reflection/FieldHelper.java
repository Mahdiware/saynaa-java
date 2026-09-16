package com.saynaa.runtime.reflection;

import android.util.Log;
import com.saynaa.runtime.reflection.ReflectionKeys.FieldKey;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance field accessor optimized for zero unnecessary allocation and fast primitive coercion.
 */
public class FieldHelper {
  private static final String TAG = "FieldHelper";

  // Sentinel to represent null values in ConcurrentHashMap
  private static final Object NULL_SENTINEL = new Object();

  // Cache for static final fields (e.g., View.VISIBLE, Color.RED)
  private static final Map<FieldKey, Object> staticFinalValueCache = new ConcurrentHashMap<>();

  // --- Set field value (instance or static) ---
  public static boolean setFieldValue(Object objOrClass, String fieldName, Object value) {
    if (objOrClass == null || fieldName == null) {
      Log.e(TAG, "Object/Class or fieldName is null for setFieldValue");
      return false;
    }

    Class<?> cls;
    boolean isStaticAccess;
    if (objOrClass instanceof Class) {
      cls = (Class<?>) objOrClass;
      isStaticAccess = true;
    } else {
      cls = objOrClass.getClass();
      isStaticAccess = false;
    }

    Field field = ReflectionFinder.findFieldQuietly(cls, fieldName);
    if (field == null) {
      return false;
    }

    try {
      Object coercedValue = coerceFieldValue(field.getType(), value);
      Object normalizedValue = ReflectionNormalizer.normalizeArg(coercedValue);

      field.set(isStaticAccess ? null : objOrClass, normalizedValue);

      // Invalidate static final cache if a static final field was modified
      if (isStaticAccess && Modifier.isFinal(field.getModifiers())) {
        staticFinalValueCache.remove(new FieldKey(cls, fieldName));
      }
      return true;
    } catch (IllegalAccessException | IllegalArgumentException e) {
      Log.e(TAG, "Error setting field: " + fieldName + " on class: " + cls.getName(), e);
      return false;
    }
  }

  // --- Get field value (instance or static) ---
  public static Object getFieldValue(Object objOrClass, String fieldName) {
    if (objOrClass == null || fieldName == null) {
      return null;
    }

    Class<?> cls;
    boolean isStaticAccess;
    if (objOrClass instanceof Class) {
      cls = (Class<?>) objOrClass;
      isStaticAccess = true;
    } else {
      cls = objOrClass.getClass();
      isStaticAccess = false;
    }

    Field field = ReflectionFinder.findFieldQuietly(cls, fieldName);
    if (field == null) {
      return null;
    }

    int modifiers = field.getModifiers();
    boolean isStatic = Modifier.isStatic(modifiers);
    boolean isFinal = Modifier.isFinal(modifiers);

    FieldKey key = null;

    // Fast Path: Check static final constant cache only if field is static & final
    if (isStaticAccess && isStatic && isFinal) {
      key = new FieldKey(cls, fieldName);
      Object cachedValue = staticFinalValueCache.get(key);
      if (cachedValue != null) {
        return cachedValue == NULL_SENTINEL ? null : cachedValue;
      }
    }

    try {
      Object rawValue = field.get(isStatic ? null : objOrClass);
      Object result = ReflectionNormalizer.normalizeReturn(rawValue);

      // Cache static final values permanently to avoid future field.get() calls
      if (key != null) {
        staticFinalValueCache.put(key, result == null ? NULL_SENTINEL : result);
      }

      return result;
    } catch (IllegalAccessException e) {
      Log.e(TAG, "Error accessing field: " + fieldName + " in class: " + cls.getName(), e);
      return null;
    }
  }

  // --- Fast Primitive Coercion ---
  private static Object coerceFieldValue(Class<?> fieldType, Object value) {
    if (value == null) {
      return null;
    }

    // Direct instance match fast path
    if (fieldType.isInstance(value)) {
      return value;
    }

    // Exact boxed primitive matching to bypass unboxing/boxing cycles
    if (fieldType.isPrimitive()) {
      if (fieldType == int.class&& value instanceof Integer)
        return value;
      if (fieldType == boolean.class && value instanceof Boolean)
        return value;
      if (fieldType == long.class&& value instanceof Long)
        return value;
      if (fieldType == double.class&& value instanceof Double)
        return value;
      if (fieldType == float.class&& value instanceof Float)
        return value;
      if (fieldType == byte.class && value instanceof Byte)
        return value;
      if (fieldType == short.class&& value instanceof Short)
        return value;
      if (fieldType == char.class&& value instanceof Character)
        return value;
    }

    // Numeric conversion path
    if (value instanceof Number) {
      Number num = (Number) value;
      if (fieldType == int.class || fieldType == Integer.class)
        return num.intValue();
      if (fieldType == boolean.class || fieldType == Boolean.class)
        return num.intValue() != 0;
      if (fieldType == long.class || fieldType == Long.class)
        return num.longValue();
      if (fieldType == float.class || fieldType == Float.class)
        return num.floatValue();
      if (fieldType == double.class || fieldType == Double.class)
        return num.doubleValue();
      if (fieldType == byte.class || fieldType == Byte.class)
        return num.byteValue();
      if (fieldType == short.class || fieldType == Short.class)
        return num.shortValue();
    }

    // String to Character conversion
    if ((fieldType == char.class || fieldType == Character.class) && value instanceof String) {
      String str = (String) value;
      return str.isEmpty() ? value : str.charAt(0);
    }

    return value;
  }
}