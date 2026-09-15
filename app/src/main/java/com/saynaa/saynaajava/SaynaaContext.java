package com.saynaa.saynaajava;

import android.content.*;
import java.util.*;

public interface SaynaaContext {
  public Context getContext();

  public void sendMsg(String msg);

  public void sendError(String title, Exception msg);
}
