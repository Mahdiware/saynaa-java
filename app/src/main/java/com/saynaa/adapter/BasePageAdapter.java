package com.saynaa.adapter;

import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class BasePageAdapter {
  private final DataSetObservable mObservable = new DataSetObservable();

  public static final int POSITION_UNCHANGED = -1;
  public static final int POSITION_NONE = -2;

  public abstract int getCount();

  public void startUpdate(@NonNull ViewGroup container) {
    startUpdate((View) container);
  }

  @NonNull
  public Object instantiateItem(@NonNull ViewGroup container, int position) {
    return instantiateItem((View) container, position);
  }

  public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
    destroyItem((View) container, position, object);
  }

  public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
    setPrimaryItem((View) container, position, object);
  }

  public void finishUpdate(@NonNull ViewGroup container) {
    finishUpdate((View) container);
  }

  @Deprecated
  public void startUpdate(@NonNull View container) {
  }

  @Deprecated
  @NonNull
  public Object instantiateItem(@NonNull View container, int position) {
    throw new UnsupportedOperationException("Required method instantiateItem was not overridden");
  }

  @Deprecated
  public void destroyItem(@NonNull View container, int position, @NonNull Object object) {
    throw new UnsupportedOperationException("Required method destroyItem was not overridden");
  }

  @Deprecated
  public void setPrimaryItem(@NonNull View container, int position, @NonNull Object object) {
  }

  @Deprecated
  public void finishUpdate(@NonNull View container) {
  }

  public abstract boolean isViewFromObject(@NonNull View view, @NonNull Object object);

  @Nullable
  public Parcelable saveState() {
    return null;
  }

  public void restoreState(@Nullable Parcelable state, @Nullable ClassLoader loader) {
  }

  public int getItemPosition(@NonNull Object object) {
    return POSITION_UNCHANGED;
  }

  public void notifyDataSetChanged() {
    mObservable.notifyChanged();
  }

  public void registerDataSetObserver(@NonNull DataSetObserver observer) {
    mObservable.registerObserver(observer);
  }

  public void unregisterDataSetObserver(@NonNull DataSetObserver observer) {
    mObservable.unregisterObserver(observer);
  }

  @Nullable
  public CharSequence getPageTitle(int position) {
    return null;
  }

  public float getPageWidth(int position) {
    return 1.f;
  }
}