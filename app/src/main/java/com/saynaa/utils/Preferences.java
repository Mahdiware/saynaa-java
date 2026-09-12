package com.saynaa.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.Map;
import java.util.Set;

/**
 * Lightweight wrapper around Android SharedPreferences.
 *
 * Supported types:
 * String, Integer, Long, Float, Double, Boolean,
 * Set<String>, and Enum.
 *
 * No external dependencies are required.
 */
public final class Preferences {

  private final SharedPreferences preferences;

  public Preferences(Context context, String name) {
    if (context == null) {
      throw new IllegalArgumentException("Context cannot be null");
    }

    if (TextUtils.isEmpty(name)) {
      throw new IllegalArgumentException(
        "Preference name cannot be empty"
      );
    }

    preferences = context.getSharedPreferences(
      name,
      Context.MODE_PRIVATE
    );
  }
  
  public static Preferences getSharedPreferences(Context context) {
    return new Preferences(context, "saynaa_preferences");
  }

  /* =========================
     Generic Operations
     ========================= */

  public void set(String key, Object value) {
    checkKey(key);

    if (value == null) {
      remove(key);
      return;
    }

    Editor editor = edit();

    if (value instanceof String) {
      editor.putString(key, (String) value);

    } else if (value instanceof Integer) {
      editor.putInt(key, (Integer) value);

    } else if (value instanceof Long) {
      editor.putLong(key, (Long) value);

    } else if (value instanceof Float) {
      editor.putFloat(key, (Float) value);

    } else if (value instanceof Double) {
      editor.putDouble(key, (Double) value);

    } else if (value instanceof Boolean) {
      editor.putBoolean(key, (Boolean) value);

    } else if (value instanceof Enum) {
      editor.putEnum(key, (Enum<?>) value);

    } else if (value instanceof Set) {
      try {
        @SuppressWarnings("unchecked")
        Set<String> values = (Set<String>) value;

        editor.putStringSet(key, values);

      } catch (ClassCastException e) {
        throw new IllegalArgumentException(
          "Only Set<String> is supported"
        );
      }

    } else {
      throw new IllegalArgumentException(
        "Unsupported preference type: "
          + value.getClass().getName()
      );
    }

    editor.apply();
  }

  /* =========================
     String
     ========================= */

  public void putString(String key, String value) {
    checkKey(key);
    edit().putString(key, value).apply();
  }

  public String getString(String key, String defaultValue) {
    checkKey(key);
    return preferences.getString(key, defaultValue);
  }

  /* =========================
     Integer
     ========================= */

  public void putInt(String key, int value) {
    checkKey(key);
    edit().putInt(key, value).apply();
  }

  public int getInt(String key, int defaultValue) {
    checkKey(key);
    return preferences.getInt(key, defaultValue);
  }

  /* =========================
     Long
     ========================= */

  public void putLong(String key, long value) {
    checkKey(key);
    edit().putLong(key, value).apply();
  }

  public long getLong(String key, long defaultValue) {
    checkKey(key);
    return preferences.getLong(key, defaultValue);
  }

  /* =========================
     Float
     ========================= */

  public void putFloat(String key, float value) {
    checkKey(key);
    edit().putFloat(key, value).apply();
  }

  public float getFloat(String key, float defaultValue) {
    checkKey(key);
    return preferences.getFloat(key, defaultValue);
  }

  /* =========================
     Double
     ========================= */

  public void putDouble(String key, double value) {
    checkKey(key);
    edit().putDouble(key, value).apply();
  }

  public double getDouble(String key, double defaultValue) {
    checkKey(key);

    String value = preferences.getString(key, null);

    if (value == null) {
      return defaultValue;
    }

    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /* =========================
     Boolean
     ========================= */

  public void putBoolean(String key, boolean value) {
    checkKey(key);
    edit().putBoolean(key, value).apply();
  }

  public boolean getBoolean(String key, boolean defaultValue) {
    checkKey(key);
    return preferences.getBoolean(key, defaultValue);
  }

  /* =========================
     String Set
     ========================= */

  public void putStringSet(String key, Set<String> values) {
    checkKey(key);
    edit().putStringSet(key, values).apply();
  }

  public Set<String> getStringSet(
    String key,
    Set<String> defaultValue
  ) {
    checkKey(key);
    return preferences.getStringSet(key, defaultValue);
  }

  /* =========================
     Enum
     ========================= */

  public void putEnum(String key, Enum<?> value) {
    checkKey(key);
    edit().putEnum(key, value).apply();
  }

  public <T extends Enum<T>> T getEnum(
    String key,
    Class<T> enumClass,
    T defaultValue
  ) {
    checkKey(key);

    if (enumClass == null) {
      throw new IllegalArgumentException(
        "Enum class cannot be null"
      );
    }

    String name = preferences.getString(key, null);

    if (name == null) {
      return defaultValue;
    }

    try {
      return Enum.valueOf(enumClass, name);
    } catch (IllegalArgumentException e) {
      return defaultValue;
    }
  }

  /* =========================
     Listeners
     ========================= */

  public void registerListener(
    SharedPreferences.OnSharedPreferenceChangeListener listener
  ) {
    if (listener == null) {
      throw new IllegalArgumentException(
        "Listener cannot be null"
      );
    }

    preferences.registerOnSharedPreferenceChangeListener(
      listener
    );
  }

  public void unregisterListener(
    SharedPreferences.OnSharedPreferenceChangeListener listener
  ) {
    if (listener == null) {
      return;
    }

    preferences.unregisterOnSharedPreferenceChangeListener(
      listener
    );
  }

  /* =========================
     Information
     ========================= */

  public boolean contains(String key) {
    checkKey(key);
    return preferences.contains(key);
  }

  public int size() {
    return preferences.getAll().size();
  }

  public boolean isEmpty() {
    return preferences.getAll().isEmpty();
  }

  public Set<String> keys() {
    return preferences.getAll().keySet();
  }

  public Map<String, ?> getAll() {
    return preferences.getAll();
  }

  /* =========================
     Remove / Clear
     ========================= */

  public void remove(String key) {
    checkKey(key);
    edit().remove(key).apply();
  }

  public void clear() {
    edit().clear().apply();
  }

  /* =========================
     Editor
     ========================= */

  public Editor edit() {
    return new Editor(preferences.edit());
  }

  public static final class Editor {

    private final SharedPreferences.Editor editor;

    private Editor(SharedPreferences.Editor editor) {
      this.editor = editor;
    }

    public Editor putString(String key, String value) {
      checkKey(key);
      editor.putString(key, value);
      return this;
    }

    public Editor putInt(String key, int value) {
      checkKey(key);
      editor.putInt(key, value);
      return this;
    }

    public Editor putLong(String key, long value) {
      checkKey(key);
      editor.putLong(key, value);
      return this;
    }

    public Editor putFloat(String key, float value) {
      checkKey(key);
      editor.putFloat(key, value);
      return this;
    }

    public Editor putDouble(String key, double value) {
      checkKey(key);
      editor.putString(key, Double.toString(value));
      return this;
    }

    public Editor putBoolean(String key, boolean value) {
      checkKey(key);
      editor.putBoolean(key, value);
      return this;
    }

    public Editor putStringSet(
      String key,
      Set<String> values
    ) {
      checkKey(key);
      editor.putStringSet(key, values);
      return this;
    }

    public Editor putEnum(
      String key,
      Enum<?> value
    ) {
      checkKey(key);

      if (value == null) {
        editor.remove(key);
      } else {
        editor.putString(key, value.name());
      }

      return this;
    }

    public Editor remove(String key) {
      checkKey(key);
      editor.remove(key);
      return this;
    }

    public Editor clear() {
      editor.clear();
      return this;
    }

    public boolean commit() {
      return editor.commit();
    }

    public void apply() {
      editor.apply();
    }
  }

  /* =========================
     Validation
     ========================= */

  private static void checkKey(String key) {
    if (TextUtils.isEmpty(key)) {
      throw new IllegalArgumentException(
        "Preference key cannot be empty or null"
      );
    }
  }
}