package com.saynaa;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.widget.Toast;
import com.saynaa.crash.CrashHandler;
import com.saynaa.runtime.SaynaaContext;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SaynaaApplication extends Application implements SaynaaContext {
  private static SaynaaApplication mApp;

  public static SaynaaApplication getInstance() {
    return mApp;
  }
  
  @Override
  public void onCreate() {
    super.onCreate();
    mApp = this;
    CrashHandler crashHandler = CrashHandler.getInstance();
    crashHandler.init(this);
  }

  @Override
  public Context getContext() {
    // TODO: Implement this method
    return this;
  }

  @Override
  public void sendMsg(String msg) {
  }

  @Override
  public void sendError(String title, Exception msg) {
  }
}
