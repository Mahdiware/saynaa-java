package com.saynaa.adapter;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import com.saynaa.saynaajava.datatype.SaynaaList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArrayPageAdapter extends BasePageAdapter {
  private final List<View> mListViews;

  public ArrayPageAdapter() {
    this.mListViews = new ArrayList<>();
  }

  public ArrayPageAdapter(List<View> views) {
    this.mListViews = new ArrayList<>(views);
  }

  public ArrayPageAdapter(View[] views) {
    this.mListViews = new ArrayList<>(Arrays.asList(views));
  }

  @Override
  public int getCount() {
    return mListViews.size();
  }

  @Override
  public Object instantiateItem(ViewGroup container, int position) {
    View view = mListViews.get(position);

    if (view.getParent() != null) {
      ViewParent parent = view.getParent();

      if (parent instanceof ViewGroup && parent != container) {
        ((ViewGroup) parent).removeView(view);
      }
    }

    if (view.getParent() == null) {
      container.addView(view);
    }

    return view;
  }

  @Override
  public void destroyItem(ViewGroup container, int position, Object object) {
    // FIX: Remove the view using the 'object' parameter, NOT mListViews.get(position).
    // Because the list changes dynamically, 'position' might be out of bounds or point to the wrong view.
    container.removeView((View) object);
  }

  @Override
  public boolean isViewFromObject(View view, Object object) {
    return view == object;
  }

  // FIX: This is strictly required for removing items dynamically!
  // It tells the Pager which items moved and which are gone.
  @Override
  public int getItemPosition(Object object) {
    int index = mListViews.indexOf(object);
    if (index == -1) {
      return POSITION_NONE; // View was removed from the list, destroy it.
    }
    return index; // View is still there, return its current index.
  }

  public void add(View view) {
    mListViews.add(view);
    notifyDataSetChanged(); // FIX: Tell the pager to refresh
  }

  public void insert(int index, View view) {
    mListViews.add(index, view);
    notifyDataSetChanged(); // FIX: Tell the pager to refresh
  }

  public View remove(int index) {
    View removedView = mListViews.remove(index);
    notifyDataSetChanged(); // FIX: Tell the pager to refresh
    return removedView;
  }

  public boolean remove(View view) {
    boolean isRemoved = mListViews.remove(view);
    if (isRemoved) {
      notifyDataSetChanged(); // FIX: Tell the pager to refresh
    }
    return isRemoved;
  }

  public View getItem(int index) {
    return mListViews.get(index);
  }

  public List<View> getData() {
    return mListViews;
  }
}