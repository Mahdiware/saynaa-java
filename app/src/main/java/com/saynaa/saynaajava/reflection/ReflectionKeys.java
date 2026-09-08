package com.saynaa.saynaajava.reflection;

public class ReflectionKeys {
  private static final Class<?>[] EMPTY_TYPES = new Class<?>[0];

  // --- Method Cache Key ---
  public static final class MethodKey {
    private final Class<?> cls;
    private final String methodName;
    private final Class<?>[] paramTypes;
    private final int hash;

    public MethodKey(Class<?> cls, String methodName, Class<?>[] paramTypes) {
      this.cls = cls;
      this.methodName = methodName != null ? methodName : "";
      this.paramTypes = (paramTypes == null || paramTypes.length == 0) ? EMPTY_TYPES : paramTypes;

      int h = this.cls != null ? this.cls.hashCode() : 0;
      h = 31 * h + this.methodName.hashCode();
      for (Class<?> p : this.paramTypes) {
        h = 31 * h + (p != null ? p.hashCode() : 0);
      }
      this.hash = h;
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (!(o instanceof MethodKey))
        return false;
      MethodKey other = (MethodKey) o;

      if (this.hash != other.hash)
        return false;
      if (this.cls != other.cls)
        return false;
      if (!this.methodName.equals(other.methodName))
        return false;
      if (this.paramTypes.length != other.paramTypes.length)
        return false;

      for (int i = 0; i < this.paramTypes.length; i++) {
        if (this.paramTypes[i] != other.paramTypes[i])
          return false;
      }
      return true;
    }
  }

  // --- Constructor Cache Key ---
  public static final class ConstructorKey {
    private final Class<?> cls;
    private final Class<?>[] paramTypes;
    private final int hash;

    public ConstructorKey(Class<?> cls, Class<?>[] paramTypes) {
      this.cls = cls;
      this.paramTypes = (paramTypes == null || paramTypes.length == 0) ? EMPTY_TYPES : paramTypes;

      int h = this.cls != null ? this.cls.hashCode() : 0;
      for (Class<?> p : this.paramTypes) {
        h = 31 * h + (p != null ? p.hashCode() : 0);
      }
      this.hash = h;
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (!(o instanceof ConstructorKey))
        return false;
      ConstructorKey other = (ConstructorKey) o;

      if (this.hash != other.hash)
        return false;
      if (this.cls != other.cls)
        return false;
      if (this.paramTypes.length != other.paramTypes.length)
        return false;

      for (int i = 0; i < this.paramTypes.length; i++) {
        if (this.paramTypes[i] != other.paramTypes[i])
          return false;
      }
      return true;
    }
  }

  // --- Field Cache Key ---
  public static final class FieldKey {
    private final Class<?> cls;
    private final String fieldName;
    private final int hash;

    public FieldKey(Class<?> cls, String fieldName) {
      this.cls = cls;
      this.fieldName = fieldName != null ? fieldName : "";
      this.hash = (this.cls != null ? this.cls.hashCode() : 0) * 31 + this.fieldName.hashCode();
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (!(o instanceof FieldKey))
        return false;
      FieldKey other = (FieldKey) o;

      return this.hash == other.hash && this.cls == other.cls && this.fieldName.equals(other.fieldName);
    }
  }
}