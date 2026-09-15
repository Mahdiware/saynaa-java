package com.saynaa.saynaajava.reflection;

import android.content.Context;
import com.saynaa.saynaajava.SaynaaContext;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Central Java value normalization and type conversion layer for Saynaa.
 * (Highly Optimized for Zero-Allocation on exact matches)
 */
public final class ReflectionNormalizer {
  private static final Object[] EMPTY_ARGS = new Object[0];

  /*
   * Pre-cached primitive boxed defaults to avoid allocation on default returns
   */
  private static final Byte ZERO_BYTE = (byte) 0;
  private static final Short ZERO_SHORT = (short) 0;
  private static final Integer ZERO_INT = 0;
  private static final Integer ONE_INT = 1; // Added for Boolean-to-Int fix
  private static final Long ZERO_LONG = 0L;
  private static final Float ZERO_FLOAT = 0.0f;
  private static final Double ZERO_DOUBLE = 0.0d;
  private static final Character ZERO_CHAR = '\0';

  private ReflectionNormalizer() {
  }

  /*
   * ------------------------------------------------------------
   * Basic normalization
   * ------------------------------------------------------------
   */

  public static Object normalizeArg(Object arg) {
    // OPTIMIZATION: Check instance before casting to avoid method overhead
    if (arg instanceof SaynaaContext) {
      Context context = ((SaynaaContext) arg).getContext();
      return context != null ? context : arg;
    }
    return arg;
  }

  public static Object[] normalizeArgs(Object... args) {
    if (args == null || args.length == 0) {
      return EMPTY_ARGS;
    }

    // OPTIMIZATION: Zero-Allocation Fast Path.
    // If no SaynaaContext exists in the array, return the original array directly.
    boolean needsCopy = false;
    for (Object arg : args) {
      if (arg instanceof SaynaaContext) {
        needsCopy = true;
        break;
      }
    }

    if (!needsCopy)
      return args;

    Object[] result = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      result[i] = normalizeArg(args[i]);
    }
    return result;
  }

  /*
   * ------------------------------------------------------------
   * Target-type conversion
   * ------------------------------------------------------------
   */

  public static Object normalize(Object value, Class<?> targetType) {
    return normalize(value, targetType, null);
  }

  public static Object normalize(Object value, Class<?> targetType, java.lang.reflect.Type genericType) {
    if (targetType == null) {
      return normalizeArg(value);
    }

    value = normalizeArg(value);

    if (value == null) {
      return targetType.isPrimitive() ? defaultReturnFor(targetType) : null;
    }

    Class<?> valClass = value.getClass();

    // OPTIMIZATION: Absolute exact match fast path.
    if (targetType == Object.class || targetType == valClass) {
      return value;
    }

    // OPTIMIZATION: Most frequent UI property types prioritized at the top of the chain
    if (targetType == int.class || targetType == Integer.class)
      return toInt(value);
    if (targetType == boolean.class || targetType == Boolean.class)
      return toBoolean(value);
    if (targetType == float.class || targetType == Float.class)
      return toFloat(value);

    if (!targetType.isPrimitive() && targetType.isInstance(value)) {
      return value;
    }

    /*
     * String / CharSequence (High frequency)
     */
    if (targetType == String.class) {
      if (value instanceof CharSequence)
        return value.toString();
      return value;
    }

    if (targetType == CharSequence.class) {
      if (value instanceof CharSequence)
        return value;
      return String.valueOf(value);
    }

    /*
     * Remaining Scalar Types
     */
    if (targetType == long.class || targetType == Long.class)
      return toLong(value);
    if (targetType == double.class || targetType == Double.class)
      return toDouble(value);
    if (targetType == char.class || targetType == Character.class)
      return toCharacter(value);
    if (targetType == byte.class || targetType == Byte.class)
      return toByte(value);
    if (targetType == short.class || targetType == Short.class)
      return toShort(value);

    /*
     * Class.
     */
    if (targetType == Class.class) {
      if (value instanceof Class)
        return value;
      if (value instanceof String)
        return ReflectionFinder.findClass((String) value);
    }

    /*
     * Collections & Data Structures
     */
    if (targetType.isArray())
      return toArray(value, targetType.getComponentType());
    if (List.class.isAssignableFrom(targetType))
      return toList(value);
    if (Collection.class.isAssignableFrom(targetType))
      return toCollection(value, targetType);
    if (Map.class.isAssignableFrom(targetType))
      return toMap(value);

    if (Iterator.class.isAssignableFrom(targetType) && targetType.isInstance(value))
      return value;
    if (Enumeration.class.isAssignableFrom(targetType) && targetType.isInstance(value))
      return value;
    if (targetType.isAssignableFrom(valClass))
      return value;

    if (targetType.isEnum() && value instanceof CharSequence) {
      try {
        @SuppressWarnings("unchecked")
        Class<? extends Enum> enumType = (Class<? extends Enum>) targetType;
        return Enum.valueOf(enumType, value.toString());
      } catch (Exception ignored) {
      }
    }

    return value;
  }

  /*
   * ------------------------------------------------------------
   * Number conversion
   * ------------------------------------------------------------
   */

  private static Number numberValue(Object value) {
    if (value instanceof Number)
      return (Number) value;

    // FIX: Original code returned ZERO_INT for both true and false. Fixed to 1 / 0.
    if (value instanceof Boolean)
      return ((Boolean) value) ? ONE_INT : ZERO_INT;

    if (value instanceof Character)
      return (int) ((Character) value).charValue();

    if (value instanceof CharSequence) {
      String text = value.toString();
      if (text.length() == 0)
        return null;

      try {
        // OPTIMIZATION: Check for decimals before trimming to save String allocations
        if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0) {
          return Double.valueOf(text); // Double.valueOf auto-trims
        }
        return Long.valueOf(text.trim());
      } catch (NumberFormatException ignored) {
      }
    }

    return null;
  }

  private static Byte toByte(Object value) {
    Number number = numberValue(value);
    return number != null ? number.byteValue() : null;
  }

  private static Short toShort(Object value) {
    Number number = numberValue(value);
    return number != null ? number.shortValue() : null;
  }

  private static Integer toInt(Object value) {
    Number number = numberValue(value);
    return number != null ? number.intValue() : null;
  }

  private static Long toLong(Object value) {
    Number number = numberValue(value);
    return number != null ? number.longValue() : null;
  }

  private static Float toFloat(Object value) {
    Number number = numberValue(value);
    return number != null ? number.floatValue() : null;
  }

  private static Double toDouble(Object value) {
    Number number = numberValue(value);
    return number != null ? number.doubleValue() : null;
  }

  private static Boolean toBoolean(Object value) {
    if (value instanceof Boolean)
      return (Boolean) value;
    if (value instanceof Number)
      return ((Number) value).doubleValue() != 0.0d;
    if (value instanceof Character)
      return ((Character) value).charValue() != 0;

    if (value instanceof CharSequence) {
      String text = value.toString();
      int len = text.length();

      // OPTIMIZATION: Avoid String.trim() and String.equalsIgnoreCase() overhead by checking length first
      if (len == 4 && "true".equalsIgnoreCase(text))
        return Boolean.TRUE;
      if (len == 1 && "1".equals(text))
        return Boolean.TRUE;
      if (len == 5 && "false".equalsIgnoreCase(text))
        return Boolean.FALSE;
      if (len == 1 && "0".equals(text))
        return Boolean.FALSE;
    }

    return Boolean.TRUE;
  }

  private static Character toCharacter(Object value) {
    if (value instanceof Character)
      return (Character) value;
    if (value instanceof Number)
      return (char) ((Number) value).intValue();

    if (value instanceof CharSequence) {
      CharSequence text = (CharSequence) value;
      return text.length() == 0 ? ZERO_CHAR : text.charAt(0);
    }

    return ZERO_CHAR;
  }

  /*
   * ------------------------------------------------------------
   * Method / constructor arguments
   * ------------------------------------------------------------
   */

  public static Object[] normalizeArgs(Class<?>[] parameterTypes, Object... args) {
    if (parameterTypes == null || parameterTypes.length == 0)
      return EMPTY_ARGS;
    if (args == null)
      args = EMPTY_ARGS;

    if (parameterTypes.length > 0 && parameterTypes[parameterTypes.length - 1].isArray()) {
      return normalizeVarArgs(parameterTypes, args);
    }
    return normalizeExecutableArgs(parameterTypes, false, args);
  }

  public static Object[] normalizeMethodArgs(Method method, Object... args) {
    if (method == null)
      return null;
    return normalizeExecutableArgs(method.getParameterTypes(), method.isVarArgs(), args);
  }

  public static Object[] normalizeConstructorArgs(Constructor<?> constructor, Object... args) {
    if (constructor == null)
      return null;
    return normalizeExecutableArgs(constructor.getParameterTypes(), constructor.isVarArgs(), args);
  }

  private static Object[] normalizeExecutableArgs(Class<?>[] parameterTypes, boolean varArgs, Object[] args) {
    if (parameterTypes == null)
      return EMPTY_ARGS;
    if (args == null)
      args = EMPTY_ARGS;
    if (varArgs)
      return normalizeVarArgs(parameterTypes, args);
    if (args.length != parameterTypes.length)
      return null;

    // OPTIMIZATION: Zero-Allocation check. If all types are perfectly assignable
    // (including Wrapper types for Primitives), return the exact 'args' array.
    boolean needsConversion = false;
    for (int i = 0; i < parameterTypes.length; i++) {
      if (!isSafelyAssignable(parameterTypes[i], args[i])) {
        needsConversion = true;
        break;
      }
    }

    if (!needsConversion)
      return args; // FAST PATH: No Object[] allocation

    Object[] result = new Object[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      result[i] = normalize(args[i], parameterTypes[i]);
    }
    return result;
  }

  // Helper to determine if an argument can be passed to invoke() without conversion
  private static boolean isSafelyAssignable(Class<?> targetType, Object arg) {
    if (arg instanceof SaynaaContext)
      return false;
    if (arg == null)
      return !targetType.isPrimitive();

    Class<?> argClass = arg.getClass();
    if (targetType == argClass || targetType.isAssignableFrom(argClass))
      return true;

    // Allow wrappers to fulfill primitive requirements natively
    if (targetType.isPrimitive()) {
      if (targetType == int.class)
        return argClass == Integer.class;
      if (targetType == boolean.class)
        return argClass == Boolean.class;
      if (targetType == float.class)
        return argClass == Float.class;
      if (targetType == double.class)
        return argClass == Double.class;
      if (targetType == long.class)
        return argClass == Long.class;
      if (targetType == byte.class)
        return argClass == Byte.class;
      if (targetType == short.class)
        return argClass == Short.class;
      if (targetType == char.class)
        return argClass == Character.class;
    }
    return false;
  }

  public static Object[] normalizeVarArgs(Class<?>[] parameterTypes, Object[] args) {
    if (parameterTypes == null || parameterTypes.length == 0)
      return EMPTY_ARGS;

    int fixedCount = parameterTypes.length - 1;
    if (args.length < fixedCount)
      return null;

    Class<?> arrayType = parameterTypes[fixedCount];
    Class<?> componentType = arrayType.getComponentType();
    int varCount = args.length - fixedCount;

    Object[] result = new Object[parameterTypes.length];
    for (int i = 0; i < fixedCount; i++) {
      result[i] = normalize(args[i], parameterTypes[i]);
    }

    if (varCount == 1 && args[fixedCount] != null && arrayType.isInstance(args[fixedCount])) {
      result[fixedCount] = normalize(args[fixedCount], arrayType);
      return result;
    }

    Object array = Array.newInstance(componentType, varCount);
    for (int i = 0; i < varCount; i++) {
      Object item = normalize(args[fixedCount + i], componentType);
      Array.set(array, i, item);
    }

    result[fixedCount] = array;
    return result;
  }

  // --- Collection and remaining logic unchanged structurally but tightened ---

  public static Object toArray(Object value, Class<?> componentType) {
    if (value == null)
      return null;
    if (value.getClass().isArray() && value.getClass().getComponentType() == componentType)
      return value;

    List<?> values = toList(value);
    if (values == null)
      return value;

    int size = values.size();
    Object array = Array.newInstance(componentType, size);
    for (int i = 0; i < size; i++) {
      Array.set(array, i, normalize(values.get(i), componentType));
    }
    return array;
  }

  public static List<Object> toList(Object value) {
    if (value == null)
      return null;

    if (value instanceof List) {
      @SuppressWarnings("unchecked") List<Object> list = (List<Object>) value;
      return list;
    }

    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      ArrayList<Object> result = new ArrayList<>(length);
      for (int i = 0; i < length; i++)
        result.add(Array.get(value, i));
      return result;
    }

    if (value instanceof Collection) {
      return new ArrayList<>((Collection<?>) value);
    }

    if (value instanceof Iterable) {
      ArrayList<Object> result = new ArrayList<>();
      for (Object item : (Iterable<?>) value)
        result.add(item);
      return result;
    }

    if (value instanceof Iterator) {
      ArrayList<Object> result = new ArrayList<>();
      Iterator<?> iterator = (Iterator<?>) value;
      while (iterator.hasNext())
        result.add(iterator.next());
      return result;
    }

    if (value instanceof Enumeration) {
      ArrayList<Object> result = new ArrayList<>();
      Enumeration<?> enumeration = (Enumeration<?>) value;
      while (enumeration.hasMoreElements())
        result.add(enumeration.nextElement());
      return result;
    }
    return null;
  }

  public static Collection<?> toCollection(Object value, Class<?> targetType) {
    if (value == null)
      return null;
    if (targetType.isInstance(value))
      return (Collection<?>) value;

    List<Object> list = toList(value);
    if (list == null)
      return null;
    if (targetType.isAssignableFrom(ArrayList.class))
      return new ArrayList<>(list);

    return list;
  }

  public static Map<?, ?> toMap(Object value) {
    return (value instanceof Map) ? (Map < ?, ? >) value : null;
  }

  public static Object[] normalizeCallbackArgs(Method method, Object[] args) {
    if (method == null)
      return args;

    Class<?>[] parameterTypes = method.getParameterTypes();
    if (parameterTypes == null || parameterTypes.length == 0)
      return EMPTY_ARGS;
    if (method.isVarArgs())
      return normalizeVarArgs(parameterTypes, args == null ? EMPTY_ARGS : args);

    Object[] result = new Object[parameterTypes.length];
    int count = args == null ? 0 : Math.min(args.length, parameterTypes.length);

    for (int i = 0; i < count; i++)
      result[i] = normalize(args[i], parameterTypes[i]);
    for (int i = count; i < parameterTypes.length; i++)
      result[i] = defaultArgFor(parameterTypes[i]);

    return result;
  }

  public static Object normalizeReturn(Object value) {
    if (value == null)
      return null;

    if (value instanceof SaynaaContext) {
      Context context = ((SaynaaContext) value).getContext();
      return context != null ? context : value;
    }
    if (value instanceof CharSequence && !(value instanceof String)) {
      return value.toString();
    }
    return value;
  }

  public static Object normalizeReturn(Object value, Class<?> returnType) {
    if (returnType == null)
      return normalizeReturn(value);
    if (returnType == void.class || returnType == Void.class)
      return null;

    Object normalized = normalizeReturn(value);
    if (normalized == null)
      return defaultReturnFor(returnType);

    return normalize(normalized, returnType);
  }

  public static Object defaultReturnFor(Class<?> returnType) {
    if (returnType == null || returnType == void.class || returnType == Void.class)
      return null;

    if (returnType == int.class || returnType == Integer.class)
      return ZERO_INT;
    if (returnType == boolean.class || returnType == Boolean.class)
      return Boolean.FALSE;
    if (returnType == float.class || returnType == Float.class)
      return ZERO_FLOAT;
    if (returnType == double.class || returnType == Double.class)
      return ZERO_DOUBLE;
    if (returnType == long.class || returnType == Long.class)
      return ZERO_LONG;
    if (returnType == byte.class || returnType == Byte.class)
      return ZERO_BYTE;
    if (returnType == short.class || returnType == Short.class)
      return ZERO_SHORT;
    if (returnType == char.class || returnType == Character.class)
      return ZERO_CHAR;

    return null;
  }

  public static Object defaultArgFor(Class<?> parameterType) {
    return defaultReturnFor(parameterType);
  }
}