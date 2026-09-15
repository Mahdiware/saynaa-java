package com.saynaa.saynaajava.reflection;

import android.util.Log;
import com.saynaa.saynaajava.reflection.ReflectionKeys.ConstructorKey;
import com.saynaa.saynaajava.reflection.ReflectionKeys.FieldKey;
import com.saynaa.saynaajava.reflection.ReflectionKeys.MethodKey;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ReflectionFinder {
  private static final String TAG = "ReflectionFinder";

  // Thread-safe caches
  private static final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();
  private static final Map<MethodKey, Method> methodCache = new ConcurrentHashMap<>();
  private static final Map<FieldKey, Field> fieldCache = new ConcurrentHashMap<>();
  private static final Map<ConstructorKey, Constructor<?>> constructorCache = new ConcurrentHashMap<>();
  private static final Map<Class<?>, Method[]> classMethodsCache = new ConcurrentHashMap<>();

  // OPTIMIZATION: Use highly efficient concurrent Sets instead of Map<Key, Boolean> for misses
  private static final Set<MethodKey> missingMethodCache = ConcurrentHashMap.newKeySet();
  private static final Set<FieldKey> missingFieldCache = ConcurrentHashMap.newKeySet();
  private static final Set<ConstructorKey> missingConstructorCache = ConcurrentHashMap.newKeySet();

  // OPTIMIZATION: CopyOnWriteArrayList eliminates the need for synchronized blocks and lock contention
  private static final List<ClassLoader> extraClassLoaders = new CopyOnWriteArrayList<>();

  // --- Find Class ---
  public static Class<?> findClass(String className) {
    Class<?> cls = classCache.get(className);
    if (cls != null)
      return cls;

    // OPTIMIZATION: Check primitives and known types BEFORE Class.forName().
    // Throwing ClassNotFoundException is extremely expensive (~1-2ms). This fixes the slow "First" benchmarks.
    switch (className) {
    case "String":
      cls = String.class;
      break;
    case "Integer":
    case "int":
      cls = Integer.class;
      break;
    case "Boolean":
    case "boolean":
      cls = Boolean.class;
      break;
    case "Long":
    case "long":
      cls = Long.class;
      break;
    case "Float":
    case "float":
      cls = Float.class;
      break;
    case "Double":
    case "double":
      cls = Double.class;
      break;
    case "Short":
    case "short":
      cls = Short.class;
      break;
    case "Byte":
    case "byte":
      cls = Byte.class;
      break;
    case "Character":
    case "char":
      cls = Character.class;
      break;
    case "Object":
      cls = Object.class;
      break;
    }

    if (cls != null) {
      classCache.put(className, cls);
      return cls;
    }

    try {
      cls = Class.forName(className);
      classCache.put(className, cls);
      return cls;
    } catch (ClassNotFoundException ignored) {
      // Fall through
    }

    // Iteration is completely lock-free now
    for (ClassLoader loader : extraClassLoaders) {
      if (loader == null)
        continue;
      try {
        cls = Class.forName(className, false, loader);
        if (cls != null) {
          classCache.put(className, cls);
          return cls;
        }
      } catch (ClassNotFoundException ignored) {
        // Try next loader
      }
    }

    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "Class not found: " + className);
    }
    return null;
  }

  // --- Find Matching Method ---
  public static Method findMethod(Class<?> cls, String methodName, Object... args) {
    if (cls == null || methodName == null)
      return null;

    Object[] normalized = ReflectionNormalizer.normalizeArgs(args);
    Class<?>[] argTypes = new Class<?>[normalized.length];
    for (int i = 0; i < normalized.length; i++) {
      argTypes[i] = normalized[i] == null ? null : normalized[i].getClass();
    }

    MethodKey key = new MethodKey(cls, methodName, argTypes);

    Method cached = methodCache.get(key);
    if (cached != null)
      return cached;
    if (missingMethodCache.contains(key))
      return null;

    Method bestMatch = null;
    int bestScore = Integer.MAX_VALUE;

    for (Method method : getMethodsCached(cls)) {
      if (!method.getName().equals(methodName))
        continue;

      Class<?>[] paramTypes = method.getParameterTypes();
      boolean isVarArgs = method.isVarArgs();
      if (!isVarArgs && paramTypes.length != normalized.length)
        continue;

      boolean match = true;
      int score = 0;

      if (!isVarArgs) {
        for (int i = 0; i < paramTypes.length; i++) {
          int s = matchScore(paramTypes[i], argTypes[i]);
          if (s < 0) {
            match = false;
            break;
          }
          score += s;
        }
      } else {
        int fixedCount = paramTypes.length - 1;
        if (normalized.length < fixedCount) {
          match = false;
        } else {
          for (int i = 0; i < fixedCount; i++) {
            int s = matchScore(paramTypes[i], argTypes[i]);
            if (s < 0) {
              match = false;
              break;
            }
            score += s;
          }
          if (match) {
            Class<?> varType = paramTypes[fixedCount].getComponentType();
            for (int i = fixedCount; i < normalized.length; i++) {
              int s = matchScore(varType, argTypes[i]);
              if (s < 0) {
                match = false;
                break;
              }
              score += s;
            }
          }
        }
      }

      if (match && score < bestScore) {
        bestMatch = method;
        bestScore = score;
      }
    }

    if (bestMatch != null) {
      try {
        bestMatch.setAccessible(true);
      } catch (Exception ignored) {
      }
      methodCache.put(key, bestMatch);
    } else {
      missingMethodCache.add(key);
    }
    return bestMatch;
  }

  // --- Find Matching Constructor ---
  public static Constructor<?> findConstructor(Class<?> cls, Object... args) {
    if (cls == null)
      return null;

    Object[] normalized = ReflectionNormalizer.normalizeArgs(args);
    Class<?>[] argTypes = new Class<?>[normalized.length];
    for (int i = 0; i < normalized.length; i++) {
      argTypes[i] = normalized[i] == null ? null : normalized[i].getClass();
    }

    ConstructorKey key = new ConstructorKey(cls, argTypes);
    Constructor<?> cached = constructorCache.get(key);
    if (cached != null)
      return cached;
    if (missingConstructorCache.contains(key))
      return null;

    Constructor<?> best = null;
    int bestScore = Integer.MAX_VALUE;

    Constructor<?>[] constructors = cls.getDeclaredConstructors();
    for (Constructor<?> ctor : constructors) {
      Class<?>[] paramTypes = ctor.getParameterTypes();
      boolean isVarArgs = ctor.isVarArgs();

      if (!isVarArgs && paramTypes.length != normalized.length)
        continue;

      boolean match = true;
      int score = 0;

      // Loop logic kept identical for reliability
      if (!isVarArgs) {
        for (int i = 0; i < paramTypes.length; i++) {
          int s = matchScore(paramTypes[i], argTypes[i]);
          if (s < 0) {
            match = false;
            break;
          }
          score += s;
        }
      } else {
        int fixedCount = paramTypes.length - 1;
        if (normalized.length < fixedCount) {
          match = false;
        } else {
          for (int i = 0; i < fixedCount; i++) {
            int s = matchScore(paramTypes[i], argTypes[i]);
            if (s < 0) {
              match = false;
              break;
            }
            score += s;
          }
          if (match) {
            Class<?> varType = paramTypes[fixedCount].getComponentType();
            for (int i = fixedCount; i < normalized.length; i++) {
              int s = matchScore(varType, argTypes[i]);
              if (s < 0) {
                match = false;
                break;
              }
              score += s;
            }
          }
        }
      }

      if (match && score < bestScore) {
        best = ctor;
        bestScore = score;
      }
    }

    if (best != null) {
      try {
        best.setAccessible(true);
      } catch (Exception ignored) {
      }
      constructorCache.put(key, best);
      return best;
    }

    missingConstructorCache.add(key);
    Log.e(TAG, "No matching constructor found for " + cls.getName());
    return null;
  }

  // --- Find Field ---
  public static Field findFieldQuietly(Class<?> cls, String fieldName) {
    if (cls == null || fieldName == null)
      return null;

    FieldKey key = new FieldKey(cls, fieldName);
    Field cached = fieldCache.get(key);
    if (cached != null)
      return cached;
    if (missingFieldCache.contains(key))
      return null;

    Field field = null;
    try {
      field = cls.getField(fieldName);
    } catch (NoSuchFieldException ignored) {
      try {
        field = cls.getDeclaredField(fieldName);
      } catch (NoSuchFieldException ignored2) {
        missingFieldCache.add(key);
        return null;
      }
    }

    if (field != null) {
      try {
        field.setAccessible(true);
      } catch (Exception ignored) {
      }
      fieldCache.put(key, field);
    }
    return field;
  }

  // --- Scoring & Helper utilities ---
  public static int matchScore(Class<?> paramType, Class<?> argType) {
    if (paramType == argType)
      return 0;
    if (argType == null)
      return paramType.isPrimitive() ? -1 : 4;

    if (paramType.isPrimitive()) {
      if (paramType == toPrimitive(argType))
        return 1;

      if (Number.class.isAssignableFrom(argType)
          && (paramType == int.class || paramType == long.class || paramType == short.class
              || paramType == byte.class || paramType == float.class || paramType == double.class)) {
        return 2;
      }
      return -1;
    }

    if (paramType.isAssignableFrom(argType))
      return 3;
    if (Number.class.isAssignableFrom(paramType) && Number.class.isAssignableFrom(argType))
      return 3;
    if (paramType == ArrayList.class && List.class.isAssignableFrom(argType))
      return 4;
    if (paramType.isArray() && List.class.isAssignableFrom(argType))
      return 5;

    return -1;
  }

  // --- Argument Coercion before Constructor/Method Invocation ---
  public static Object[] coerceArgs(Class<?>[] paramTypes, Object[] args) {
    if (args == null || paramTypes == null || args.length == 0)
      return args;

    // OPTIMIZATION: Zero-allocation fast path. Do not create a new Object[] array
    // unless a coercion is actually needed. This saves massive overhead on every method call.
    boolean needsCoercion = false;
    for (int i = 0; i < args.length; i++) {
      Object arg = args[i];
      if (arg != null) {
        Class<?> paramType = i < paramTypes.length ? paramTypes[i] : paramTypes[paramTypes.length - 1];
        if (paramType == ArrayList.class && arg instanceof List && !(arg instanceof ArrayList)) {
          needsCoercion = true;
          break;
        }
      }
    }

    if (!needsCoercion)
      return args;

    // Only allocate and copy if we actually have to coerce an argument
    Object[] coerced = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      Object arg = args[i];
      if (arg == null) {
        coerced[i] = null;
        continue;
      }

      Class<?> paramType = i < paramTypes.length ? paramTypes[i] : paramTypes[paramTypes.length - 1];
      if (paramType == ArrayList.class && arg instanceof List && !(arg instanceof ArrayList)) {
        coerced[i] = new ArrayList<>((List<?>) arg);
      } else {
        coerced[i] = arg;
      }
    }
    return coerced;
  }

  public static Class<?> toPrimitive(Class<?> cls) {
    // OPTIMIZATION: Reordered to put the most common Android reflection primitives at the top
    if (cls == Integer.class)
      return int.class;
    if (cls == Boolean.class)
      return boolean.class;
    if (cls == Float.class)
      return float.class;
    if (cls == Long.class)
      return long.class;
    if (cls == Double.class)
      return double.class;
    if (cls == Byte.class)
      return byte.class;
    if (cls == Short.class)
      return short.class;
    if (cls == Character.class)
      return char.class;
    return cls;
  }

  public static Method[] getMethodsCached(Class<?> cls) {
    Method[] methods = classMethodsCache.get(cls);
    if (methods != null)
      return methods;

    methods = cls.getMethods();
    classMethodsCache.put(cls, methods);
    return methods;
  }

  // OPTIMIZATION: Removed all 'synchronized' keywords by using CopyOnWriteArrayList
  public static void setExtraClassLoaders(List<ClassLoader> loaders) {
    extraClassLoaders.clear();
    if (loaders != null) {
      extraClassLoaders.addAll(loaders);
    }
  }

  public static void addExtraClassLoader(ClassLoader loader) {
    if (loader != null) {
      extraClassLoaders.add(loader);
    }
  }

  public static void removeExtraClassLoader(ClassLoader loader) {
    if (loader != null) {
      extraClassLoaders.remove(loader);
    }
  }

  public static List<ClassLoader> getExtraClassLoaders() {
    return new ArrayList<>(extraClassLoaders); // Safe to copy lock-free
  }
}