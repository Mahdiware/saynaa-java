package com.saynaa.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * High-feature wrapper around Android SharedPreferences.
 * Zero third-party dependencies (Uses standard Android framework & Java SE APIs).
 */
public final class Preferences {
  private static final String TTL_SUFFIX = "_ttl_exp_time_ms_";
  private final SharedPreferences preferences;
  
  // Strong reference hold to prevent Android's SharedPreferences weak-ref listener GC bug
  private final Set<SharedPreferences.OnSharedPreferenceChangeListener> listeners = new HashSet<>();

  public Preferences(Context context, String name) {
    if (context == null) {
      throw new IllegalArgumentException("Context cannot be null");
    }

    if (TextUtils.isEmpty(name)) {
      throw new IllegalArgumentException("Preference name cannot be empty");
    }

    preferences = context.getApplicationContext().getSharedPreferences(name, Context.MODE_PRIVATE);
  }

  public Preferences(Context context) {
    this(context, "saynaa_preferences");
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
    } else if (value instanceof byte[]) {
      editor.putBytes(key, (byte[]) value);
    } else if (value instanceof JSONObject) {
      editor.putJsonObject(key, (JSONObject) value);
    } else if (value instanceof JSONArray) {
      editor.putJsonArray(key, (JSONArray) value);
    } else if (value instanceof Serializable) {
      editor.putObject(key, (Serializable) value);
    } else if (value instanceof Set) {
      try {
        @SuppressWarnings("unchecked") Set<String> values = (Set<String>) value;
        editor.putStringSet(key, values);
      } catch (ClassCastException e) {
        throw new IllegalArgumentException("Only Set<String> is supported for sets");
      }
    } else {
      throw new IllegalArgumentException("Unsupported preference type: " + value.getClass().getName());
    }

    editor.apply();
  }

  /* =========================
     Primitives & Native Types
     ========================= */

  public void putString(String key, String value) {
    checkKey(key);
    edit().putString(key, value).apply();
  }

  public String getString(String key, String defaultValue) {
    checkKey(key);
    if (isExpired(key)) return defaultValue;
    return preferences.getString(key, defaultValue);
  }

  public void putInt(String key, int value) {
    checkKey(key);
    edit().putInt(key, value).apply();
  }

  public int getInt(String key, int defaultValue) {
    checkKey(key);
    if (isExpired(key)) return defaultValue;
    return preferences.getInt(key, defaultValue);
  }

  public void putLong(String key, long value) {
    checkKey(key);
    edit().putLong(key, value).apply();
  }

  public long getLong(String key, long defaultValue) {
    checkKey(key);
    if (isExpired(key)) return defaultValue;
    return preferences.getLong(key, defaultValue);
  }

  public void putFloat(String key, float value) {
    checkKey(key);
    edit().putFloat(key, value).apply();
  }

  public float getFloat(String key, float defaultValue) {
    checkKey(key);
    if (isExpired(key)) return defaultValue;
    return preferences.getFloat(key, defaultValue);
  }

  public void putDouble(String key, double value) {
    checkKey(key);
    edit().putDouble(key, value).apply();
  }

  public double getDouble(String key, double defaultValue) {
    checkKey(key);
    if (isExpired(key)) return defaultValue;
    String value = preferences.getString(key, null);
    if (value == null) return defaultValue;

    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public void putBoolean(String key, boolean value) {
    checkKey(key);
    edit().putBoolean(key, value).apply();
  }

  public boolean getBoolean(String key, boolean defaultValue) {
    checkKey(key);
    if (isExpired(key)) return defaultValue;
    return preferences.getBoolean(key, defaultValue);
  }

  /* =========================
     JSON & Collections
     ========================= */

  public void putJsonObject(String key, JSONObject jsonObject) {
    checkKey(key);
    edit().putJsonObject(key, jsonObject).apply();
  }

  public JSONObject getJsonObject(String key, JSONObject defaultValue) {
    checkKey(key);
    if (isExpired(key)) return defaultValue;
    String rawJson = preferences.getString(key, null);
    if (rawJson == null) return defaultValue;

    try {
      return new JSONObject(rawJson);
    } catch (JSONException e) {
      return defaultValue;
    }
  }

  public void putJsonArray(String key, JSONArray jsonArray) {
    checkKey(key);
    edit().putJsonArray(key, jsonArray).apply();
  }

  public JSONArray getJsonArray(String key, JSONArray defaultValue) {
    checkKey(key);
    if (isExpired(key)) return defaultValue;
    String rawJson = preferences.getString(key, null);
    if (rawJson == null) return defaultValue;

    try {
      return new JSONArray(rawJson);
    } catch (JSONException e) {
      return defaultValue;
    }
  }

  public void putStringList(String key, List<String> list) {
    checkKey(key);
    if (list == null) {
      remove(key);
      return;
    }
    JSONArray array = new JSONArray();
    for (String item : list) {
      array.put(item);
    }
    putJsonArray(key, array);
  }

  public List<String> getStringList(String key, List<String> defaultValue) {
    checkKey(key);
    JSONArray array = getJsonArray(key, null);
    if (array == null) return defaultValue;

    List<String> list = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) {
      list.add(array.optString(i, null));
    }
    return list;
  }

  /* =========================
     Java Objects & Byte Arrays
     ========================= */

  public void putObject(String key, Serializable object) {
    checkKey(key);
    edit().putObject(key, object).apply();
  }

  @SuppressWarnings("unchecked")
  public <T extends Serializable> T getObject(String key, T defaultValue) {
    checkKey(key);
    if (isExpired(key)) return defaultValue;
    String encoded = preferences.getString(key, null);
    if (encoded == null) return defaultValue;

    try {
      byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
      ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
      ObjectInputStream ois = new ObjectInputStream(bais);
      return (T) ois.readObject();
    } catch (Exception e) {
      return defaultValue;
    }
  }

  public void putBytes(String key, byte[] bytes) {
    checkKey(key);
    edit().putBytes(key, bytes).apply();
  }

  public byte[] getBytes(String key, byte[] defaultValue) {
    checkKey(key);
    if (isExpired(key)) return defaultValue;
    String encoded = preferences.getString(key, null);
    if (encoded == null) return defaultValue;

    try {
      return Base64.decode(encoded, Base64.DEFAULT);
    } catch (IllegalArgumentException e) {
      return defaultValue;
    }
  }

  /* =========================
     Enums & Sets
     ========================= */

  public void putStringSet(String key, Set<String> values) {
    checkKey(key);
    edit().putStringSet(key, values).apply();
  }

  public Set<String> getStringSet(String key, Set<String> defaultValue) {
    checkKey(key);
    if (isExpired(key)) return defaultValue;
    return preferences.getStringSet(key, defaultValue);
  }

  public void putEnum(String key, Enum<?> value) {
    checkKey(key);
    edit().putEnum(key, value).apply();
  }

  public <T extends Enum<T>> T getEnum(String key, Class<T> enumClass, T defaultValue) {
    checkKey(key);
    if (enumClass == null) throw new IllegalArgumentException("Enum class cannot be null");
    if (isExpired(key)) return defaultValue;

    String name = preferences.getString(key, null);
    if (name == null) return defaultValue;

    try {
      return Enum.valueOf(enumClass, name);
    } catch (IllegalArgumentException e) {
      return defaultValue;
    }
  }

  /* =========================
     Convenience Mutators
     ========================= */

  public int increment(String key, int delta) {
    int val = getInt(key, 0) + delta;
    putInt(key, val);
    return val;
  }

  public long increment(String key, long delta) {
    long val = getLong(key, 0L) + delta;
    putLong(key, val);
    return val;
  }

  public boolean toggle(String key) {
    boolean val = !getBoolean(key, false);
    putBoolean(key, val);
    return val;
  }

  /* =========================
     TTL / Time-To-Live Support
     ========================= */

  public void putWithTtl(String key, Object value, long ttlMillis) {
    checkKey(key);
    long expiryTime = System.currentTimeMillis() + ttlMillis;
    set(key, value);
    edit().putLong(key + TTL_SUFFIX, expiryTime).apply();
  }

  public boolean isExpired(String key) {
    long expiryTime = preferences.getLong(key + TTL_SUFFIX, -1);
    if (expiryTime != -1 && System.currentTimeMillis() > expiryTime) {
      remove(key);
      remove(key + TTL_SUFFIX);
      return true;
    }
    return false;
  }

  /* =========================
     Listeners & Housekeeping
     ========================= */

  public synchronized void registerListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
    if (listener == null) throw new IllegalArgumentException("Listener cannot be null");
    listeners.add(listener);
    preferences.registerOnSharedPreferenceChangeListener(listener);
  }

  public synchronized void unregisterListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
    if (listener == null) return;
    listeners.remove(listener);
    preferences.unregisterOnSharedPreferenceChangeListener(listener);
  }

  public boolean contains(String key) {
    checkKey(key);
    return !isExpired(key) && preferences.contains(key);
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

  public void remove(String key) {
    checkKey(key);
    edit().remove(key).remove(key + TTL_SUFFIX).apply();
  }

  public void clear() {
    edit().clear().apply();
  }

  public Editor edit() {
    return new Editor(preferences.edit());
  }

  /* =========================
     Rich Transactional Editor
     ========================= */

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

    public Editor putJsonObject(String key, JSONObject jsonObject) {
      checkKey(key);
      if (jsonObject == null) {
        editor.remove(key);
      } else {
        editor.putString(key, jsonObject.toString());
      }
      return this;
    }

    public Editor putJsonArray(String key, JSONArray jsonArray) {
      checkKey(key);
      if (jsonArray == null) {
        editor.remove(key);
      } else {
        editor.putString(key, jsonArray.toString());
      }
      return this;
    }

    public Editor putObject(String key, Serializable object) {
      checkKey(key);
      if (object == null) {
        editor.remove(key);
        return this;
      }
      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(object);
        oos.close();
        String encoded = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        editor.putString(key, encoded);
      } catch (Exception e) {
        throw new IllegalArgumentException("Failed to serialize object", e);
      }
      return this;
    }

    public Editor putBytes(String key, byte[] bytes) {
      checkKey(key);
      if (bytes == null) {
        editor.remove(key);
      } else {
        editor.putString(key, Base64.encodeToString(bytes, Base64.DEFAULT));
      }
      return this;
    }

    public Editor putStringSet(String key, Set<String> values) {
      checkKey(key);
      editor.putStringSet(key, values);
      return this;
    }

    public Editor putEnum(String key, Enum<?> value) {
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

  private static void checkKey(String key) {
    if (TextUtils.isEmpty(key)) {
      throw new IllegalArgumentException("Preference key cannot be empty or null");
    }
  }
}
