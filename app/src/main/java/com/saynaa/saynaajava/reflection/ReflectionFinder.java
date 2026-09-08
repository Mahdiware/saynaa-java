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
import java.util.concurrent.ConcurrentHashMap;

public class ReflectionFinder {
  private static final String TAG = "ReflectionFinder";

  // Thread-safe caches
  private static final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();
  private static final Map<MethodKey, Method> methodCache = new ConcurrentHashMap<>();
  private static final Map<MethodKey, Boolean> missingMethodCache = new ConcurrentHashMap<>();
  private static final Map<FieldKey, Field> fieldCache = new ConcurrentHashMap<>();
  private static final Map<FieldKey, Boolean> missingFieldCache = new ConcurrentHashMap<>();
  private static final Map<ConstructorKey, Constructor<?>> constructorCache = new ConcurrentHashMap<>();
  private static final Map<Class<?>, Method[]> classMethodsCache = new ConcurrentHashMap<>();

  private static final List<ClassLoader> extraClassLoaders = new ArrayList<>();

  // --- Find Class ---
  public static Class<?> findClass(String className) {
    Class<?> cls = classCache.get(className);
    if (cls != null)
      return cls;

    try {
      cls = Class.forName(className);
      classCache.put(className, cls);
      return cls;
    } catch (ClassNotFoundException ignored) {
      // Fall through
    }

    for (ClassLoader loader : getExtraClassLoaders()) {
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

    switch (className) {
    case "String":
      cls = String.class;
      break;
    case "Integer":
      cls = Integer.class;
      break;
    case "Long":
      cls = Long.class;
      break;
    case "Short":
      cls = Short.class;
      break;
    case "Byte":
      cls = Byte.class;
      break;
    case "Character":
      cls = Character.class;
      break;
    case "Boolean":
      cls = Boolean.class;
      break;
    case "Float":
      cls = Float.class;
      break;
    case "Double":
      cls = Double.class;
      break;
    default:
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "Class not found: " + className);
      }
      return null;
    }
    classCache.put(className, cls);
    return cls;
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

    // Fast Path: Checked Cache First
    Method cached = methodCache.get(key);
    if (cached != null)
      return cached;
    if (missingMethodCache.containsKey(key))
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
        bestMatch.setAccessible(true); // Speeds up reflection execution
      } catch (Exception ignored) {
      }
      methodCache.put(key, bestMatch);
    } else {
      missingMethodCache.put(key, Boolean.TRUE);
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

    Constructor<?> best = null;
    int bestScore = Integer.MAX_VALUE;

    for (Constructor<?> ctor : cls.getConstructors()) {
      Class<?>[] paramTypes = ctor.getParameterTypes();
      if (paramTypes.length != normalized.length)
        continue;

      boolean match = true;
      int score = 0;
      for (int i = 0; i < paramTypes.length; i++) {
        int s = matchScore(paramTypes[i], argTypes[i]);
        if (s < 0) {
          match = false;
          break;
        }
        score += s;
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

    Log.e(TAG, "No matching constructor found for " + cls.getName());
    return null;
  }

  // --- Find Field ---
  public static Field findFieldQuietly(Class<?> cls, String fieldName) {
    if (cls == null || fieldName == null)
      return null;

    FieldKey key = new FieldKey(cls, fieldName);
    if (missingFieldCache.containsKey(key))
      return null;

    Field cached = fieldCache.get(key);
    if (cached != null)
      return cached;

    Field field = null;
    try {
      field = cls.getField(fieldName);
    } catch (NoSuchFieldException ignored) {
      try {
        field = cls.getDeclaredField(fieldName);
      } catch (NoSuchFieldException ignored2) {
        missingFieldCache.put(key, Boolean.TRUE);
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
    if (argType == null) {
      return paramType.isPrimitive() ? -1 : 4;
    }

    if (paramType == argType)
      return 0;

    if (paramType.isPrimitive()) {
      Class<?> prim = toPrimitive(argType);
      if (paramType == prim)
        return 1;
      if (Number.class.isAssignableFrom(argType)
          && (paramType == int.class || paramType == long.class || paramType == short.class
              || paramType == byte.class || paramType == float.class || paramType == double.class)) {
        return 2;
      }
      return -1;
    }

    if (Number.class.isAssignableFrom(paramType) && Number.class.isAssignableFrom(argType))
      return 2;
    if (paramType.isAssignableFrom(argType))
      return 3;

    return -1;
  }

  public static Class<?> toPrimitive(Class<?> cls) {
    if (cls == Integer.class)
      return int.class;
    if (cls == Boolean.class)
      return boolean.class;
    if (cls == Byte.class)
      return byte.class;
    if (cls == Character.class)
      return char.class;
    if (cls == Double.class)
      return double.class;
    if (cls == Float.class)
      return float.class;
    if (cls == Long.class)
      return long.class;
    if (cls == Short.class)
      return short.class;
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

  public static synchronized void setExtraClassLoaders(List<ClassLoader> loaders) {
    extraClassLoaders.clear();
    if (loaders != null) {
      extraClassLoaders.addAll(loaders);
    }
  }

  public static synchronized void addExtraClassLoader(ClassLoader loader) {
    if (loader != null) {
      extraClassLoaders.add(loader);
    }
  }

  public static synchronized void removeExtraClassLoader(ClassLoader loader) {
    extraClassLoaders.remove(loader);
  }

  public static synchronized List<ClassLoader> getExtraClassLoaders() {
    return new ArrayList<>(extraClassLoaders);
  }
}