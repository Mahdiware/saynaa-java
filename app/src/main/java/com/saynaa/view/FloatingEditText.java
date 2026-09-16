package com.saynaa.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class FloatingEditText extends FrameLayout {
  public static final int END_ICON_NONE = 0;
  public static final int END_ICON_CLEAR = 1;
  public static final int END_ICON_PASSWORD_TOGGLE = 2;
  public static final int END_ICON_CUSTOM = 3;

  private OutlineField fieldContainer;
  private EditText editText;
  private TextView label;
  private TextView helperTextView;
  private ImageView endIconView;

  private String hint = "";
  private String helperText = "";

  private int normalColor;
  private int focusColor;
  private int errorColor;
  private int disabledColor;
  private int backgroundColor;

  private float cornerRadius = 8f;
  private int defaultFieldHeight = 52;
  private final int TOP_LABEL_MARGIN = 14; // Space for floating label floating outside outline

  private boolean hasError;
  private boolean enabled = true;
  private int endIconMode = END_ICON_NONE;
  private boolean isPasswordShowing = false;

  private float labelProgress = 0f;
  private ValueAnimator labelAnimator;
  private ValueAnimator errorAnimator;

  private int horizontalPadding = 12;

  public FloatingEditText(Context context) {
    this(context, null);
  }

  public FloatingEditText(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public FloatingEditText(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    setClipChildren(false);
    setClipToPadding(false);

    initThemeColors();
    init(context);
  }

  private void initThemeColors() {
    backgroundColor = Color.TRANSPARENT;
    normalColor = getThemeColor(android.R.attr.textColorSecondary);
    focusColor = getThemeColor(android.R.attr.colorAccent);
    disabledColor = getThemeColor(android.R.attr.textColorTertiary);

    if (normalColor == Color.TRANSPARENT)
      normalColor = Color.GRAY;
    if (focusColor == Color.TRANSPARENT)
      focusColor = normalColor;
    if (disabledColor == Color.TRANSPARENT)
      disabledColor = normalColor;

    errorColor = Color.rgb(211, 47, 47);
  }

  private int getThemeColor(int attribute) {
    TypedValue value = new TypedValue();
    boolean found = getContext().getTheme().resolveAttribute(attribute, value, true);
    if (!found)
      return Color.TRANSPARENT;

    if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
      return value.data;
    }

    if (value.resourceId != 0) {
      try {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
          return getResources().getColor(value.resourceId, getContext().getTheme());
        } else {
          return getResources().getColor(value.resourceId);
        }
      } catch (Exception ignored) {
      }
    }
    return Color.TRANSPARENT;
  }

  private void init(Context context) {
    fieldContainer = new OutlineField(context);
    fieldContainer.setClipChildren(false);
    fieldContainer.setClipToPadding(false);

    LayoutParams fieldParams = new LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    fieldParams.topMargin = dp(TOP_LABEL_MARGIN);
    addView(fieldContainer, fieldParams);

    // Native EditText
    editText = new EditText(context);
    editText.setSingleLine(true);
    editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    editText.setGravity(Gravity.CENTER_VERTICAL);
    editText.setBackgroundColor(Color.TRANSPARENT);
    editText.setPadding(dp(horizontalPadding), 0, dp(horizontalPadding + 32), 0);

    LayoutParams editParams = new LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    fieldContainer.addView(editText, editParams);

    // Floating Label Text
    label = new TextView(context);
    label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    label.setSingleLine(true);
    label.setGravity(Gravity.CENTER_VERTICAL);
    label.setTypeface(Typeface.DEFAULT);
    label.setBackgroundColor(Color.TRANSPARENT);

    LayoutParams labelParams = new LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    labelParams.leftMargin = dp(horizontalPadding);
    fieldContainer.addView(label, labelParams);
    label.bringToFront();

    // End Action Icon
    endIconView = new ImageView(context);
    endIconView.setScaleType(ImageView.ScaleType.CENTER);
    endIconView.setVisibility(GONE);

    LayoutParams iconParams = new LayoutParams(dp(36), ViewGroup.LayoutParams.MATCH_PARENT);
    iconParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
    fieldContainer.addView(endIconView, iconParams);

    // Helper & Error Subtext
    helperTextView = new TextView(context);
    helperTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
    helperTextView.setGravity(Gravity.TOP);
    helperTextView.setPadding(dp(horizontalPadding), dp(4), dp(horizontalPadding), 0);
    helperTextView.setVisibility(GONE);

    LayoutParams helperParams = new LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    addView(helperTextView, helperParams);

    // Listeners
    editText.setOnFocusChangeListener((view, focused) -> updateState(true));

    editText.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        updateState(true);
        updateEndIconVisibility();
      }
      @Override
      public void afterTextChanged(Editable editable) {
      }
    });

    fieldContainer.setOnClickListener(v -> {
      if (editText.isEnabled()) {
        editText.requestFocus();
        editText.setSelection(editText.length());
      }
    });

    endIconView.setOnClickListener(v -> handleEndIconClick());

    updateState(false);
  }

  // --- State & Color Management ---

  private void updateState(boolean animate) {
    boolean floating = editText.hasFocus() || editText.length() > 0;
    float target = floating ? 1f : 0f;

    animateLabel(target, animate);
    updateColors();
  }

  private void updateColors() {
    int activeColor = !enabled ? disabledColor
                               : (hasError ? errorColor : (editText.hasFocus() ? focusColor : normalColor));

    fieldContainer.setBorderColor(activeColor);
    fieldContainer.setBorderWidth(editText.hasFocus() ? dp(2) : dp(1));
    label.setTextColor(activeColor);

    if (hasError) {
      helperTextView.setTextColor(errorColor);
    } else {
      helperTextView.setTextColor(normalColor);
    }
  }

  private void animateLabel(float target, boolean animate) {
    if (labelAnimator != null)
      labelAnimator.cancel();

    if (!animate) {
      labelProgress = target;
      applyLabelProgress();
      return;
    }

    labelAnimator = ValueAnimator.ofFloat(labelProgress, target);
    labelAnimator.setDuration(160);
    labelAnimator.setInterpolator(new DecelerateInterpolator());
    labelAnimator.addUpdateListener(animation -> {
      labelProgress = (Float) animation.getAnimatedValue();
      applyLabelProgress();
    });
    labelAnimator.start();
  }

  private void applyLabelProgress() {
    float containerHeight = fieldContainer.getHeight() > 0 ? fieldContainer.getHeight() : dp(defaultFieldHeight);
    float labelHeight = label.getMeasuredHeight() > 0 ? label.getMeasuredHeight() : dp(16);

    float centerY = (containerHeight / 2f) - (labelHeight / 2f);
    float floatingY = -(labelHeight / 2f);

    float y = centerY + (floatingY - centerY) * labelProgress;
    label.setTranslationY(y);

    float scale = 1f - 0.22f * labelProgress;
    label.setScaleX(scale);
    label.setScaleY(scale);

    label.setPivotX(0);
    label.setPivotY(labelHeight / 2f);

    fieldContainer.invalidate();
  }

  // --- End Icon Functionality ---

  public void setEndIconMode(int mode) {
    this.endIconMode = mode;
    updateEndIconVisibility();
  }

  private void updateEndIconVisibility() {
    if (!enabled) {
      endIconView.setVisibility(GONE);
      return;
    }

    if (hasError) {
      endIconView.setImageDrawable(new ErrorIconDrawable(errorColor));
      endIconView.setVisibility(VISIBLE);
      endIconView.setClickable(false);
      return;
    }

    switch (endIconMode) {
    case END_ICON_CLEAR:
      endIconView.setImageDrawable(new ClearIconDrawable(normalColor));
      endIconView.setVisibility(editText.length() > 0 ? VISIBLE : GONE);
      endIconView.setClickable(true);
      break;

    case END_ICON_PASSWORD_TOGGLE:
      endIconView.setImageDrawable(new EyeIconDrawable(normalColor, isPasswordShowing));
      endIconView.setVisibility(VISIBLE);
      endIconView.setClickable(true);
      break;

    case END_ICON_CUSTOM:
      endIconView.setVisibility(VISIBLE);
      endIconView.setClickable(true);
      break;

    case END_ICON_NONE:
    default:
      endIconView.setVisibility(GONE);
      break;
    }
  }

  private void handleEndIconClick() {
    if (hasError)
      return;

    if (endIconMode == END_ICON_CLEAR) {
      editText.setText("");
    } else if (endIconMode == END_ICON_PASSWORD_TOGGLE) {
      int selectionStart = editText.getSelectionStart();
      int selectionEnd = editText.getSelectionEnd();

      isPasswordShowing = !isPasswordShowing;
      if (isPasswordShowing) {
        editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
      } else {
        editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
      }

      editText.setSelection(selectionStart, selectionEnd);
      updateEndIconVisibility();
    }
  }

  public void setEndIconDrawable(Drawable drawable) {
    endIconMode = END_ICON_CUSTOM;
    endIconView.setImageDrawable(drawable);
    endIconView.setVisibility(VISIBLE);
  }

  public void setEndIconOnClickListener(OnClickListener listener) {
    endIconView.setOnClickListener(v -> {
      if (!hasError && listener != null) {
        listener.onClick(v);
      }
    });
  }

  // --- TextWatcher & Forwarding Delegation ---

  public void addTextChangedListener(TextWatcher watcher) {
    if (editText != null)
      editText.addTextChangedListener(watcher);
  }

  public void removeTextChangedListener(TextWatcher watcher) {
    if (editText != null)
      editText.removeTextChangedListener(watcher);
  }

  public void setOnEditorActionListener(TextView.OnEditorActionListener listener) {
    editText.setOnEditorActionListener(listener);
  }

  public void setFilters(InputFilter[] filters) {
    editText.setFilters(filters);
  }

  public void setMaxLength(int maxLength) {
    editText.setFilters(new InputFilter[] {new InputFilter.LengthFilter(maxLength)});
  }

  public void setSelection(int index) {
    editText.setSelection(index);
  }
  public void setSelection(int start, int stop) {
    editText.setSelection(start, stop);
  }
  public void selectAll() {
    editText.selectAll();
  }

  // --- Getter & Setter API ---

  public void setHint(String text) {
    hint = text == null ? "" : text;
    label.setText(hint);
    updateState(false);
  }

  public String getHint() {
    return hint;
  }

  public void setText(String text) {
    editText.setText(text == null ? "" : text);
    editText.setSelection(editText.length());
    updateState(false);
  }

  public String getText() {
    return editText.getText().toString();
  }

  public EditText getEditText() {
    return editText;
  }

  public void setHelperText(String message) {
    this.helperText = message == null ? "" : message;
    if (!hasError) {
      if (helperText.isEmpty()) {
        helperTextView.setVisibility(GONE);
      } else {
        helperTextView.setText(helperText);
        helperTextView.setVisibility(VISIBLE);
      }
      updateColors();
      requestLayout();
    }
  }

  public void setError(String message) {
    if (message == null || message.isEmpty()) {
      clearError();
      return;
    }
    hasError = true;
    helperTextView.setText(message);
    updateColors();
    updateEndIconVisibility();
    animateError(true);
  }

  public void clearError() {
    hasError = false;
    animateError(false);
    updateColors();
    updateEndIconVisibility();

    if (!helperText.isEmpty()) {
      helperTextView.setText(helperText);
      helperTextView.setVisibility(VISIBLE);
    }
  }

  public boolean hasError() {
    return hasError;
  }

  private void animateError(boolean show) {
    if (errorAnimator != null)
      errorAnimator.cancel();

    if (show)
      helperTextView.setVisibility(VISIBLE);

    errorAnimator = ValueAnimator.ofFloat(show ? 0f : 1f, show ? 1f : 0f);
    errorAnimator.setDuration(150);
    errorAnimator.setInterpolator(new DecelerateInterpolator());
    errorAnimator.addUpdateListener(animation -> {
      float value = (Float) animation.getAnimatedValue();
      helperTextView.setAlpha(value);
    });
    errorAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(android.animation.Animator animation) {
        if (!hasError && helperText.isEmpty()) {
          helperTextView.setVisibility(GONE);
        }
        requestLayout();
      }
    });
    errorAnimator.start();
    requestLayout();
  }

  public void setInputType(int type) {
    editText.setInputType(type);
    if ((type & InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD
        || (type & InputType.TYPE_NUMBER_VARIATION_PASSWORD) == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
      setEndIconMode(END_ICON_PASSWORD_TOGGLE);
    }
  }

  public int getInputType() {
    return editText.getInputType();
  }
  public void setSingleLine(boolean value) {
    editText.setSingleLine(value);
  }
  public void setTextSize(float size) {
    editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
  }

  public void setErrorColor(int color) {
    errorColor = color;
    updateColors();
  }
  public void setFocusColor(int color) {
    focusColor = color;
    updateColors();
  }
  public void setNormalColor(int color) {
    normalColor = color;
    updateColors();
  }
  public void setDisabledColor(int color) {
    disabledColor = color;
    updateColors();
  }

  public void setFieldBackground(int color) {
    backgroundColor = color;
    fieldContainer.setBackgroundColor(color);
    updateColors();
  }

  public void setCornerRadius(float radius) {
    cornerRadius = radius;
    fieldContainer.invalidate();
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    this.enabled = enabled;
    if (editText != null)
      editText.setEnabled(enabled);
    if (!enabled)
      editText.clearFocus();
    updateColors();
    updateEndIconVisibility();
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public boolean requestFocus(int direction, android.graphics.Rect previouslyFocusedRect) {
    return editText != null ? editText.requestFocus(direction, previouslyFocusedRect)
                            : super.requestFocus(direction, previouslyFocusedRect);
  }

  @Override
  public void clearFocus() {
    if (editText != null)
      editText.clearFocus();
    else
      super.clearFocus();
  }

  // --- Layout & Measurement ---

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int mode = MeasureSpec.getMode(heightMeasureSpec);
    int specSize = MeasureSpec.getSize(heightMeasureSpec);

    int topMargin = dp(TOP_LABEL_MARGIN);
    int desiredFieldHeight = dp(defaultFieldHeight);

    if (mode == MeasureSpec.EXACTLY) {
      desiredFieldHeight = Math.max(dp(32), specSize - topMargin);
    } else if (mode == MeasureSpec.AT_MOST) {
      desiredFieldHeight = Math.min(desiredFieldHeight, specSize - topMargin);
    }

    int totalHeight = desiredFieldHeight + topMargin;

    if (helperTextView.getVisibility() != GONE) {
      helperTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
      totalHeight += helperTextView.getMeasuredHeight() + dp(4);
    }

    fieldContainer.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(desiredFieldHeight, MeasureSpec.EXACTLY));

    setMeasuredDimension(width, totalHeight);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int topMargin = dp(TOP_LABEL_MARGIN);
    int fieldHeight = fieldContainer.getMeasuredHeight();

    fieldContainer.layout(0, topMargin, getMeasuredWidth(), topMargin + fieldHeight);

    if (helperTextView.getVisibility() != GONE) {
      int topPosition = topMargin + fieldHeight + dp(2);
      helperTextView.layout(
          0, topPosition, getMeasuredWidth(), topPosition + helperTextView.getMeasuredHeight());
    }

    applyLabelProgress();
  }

  private int dp(float value) {
    return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
  }

  // --- Inner View: Custom Outline Canvas Drawing ---

  private class OutlineField extends FrameLayout {
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int borderColor = normalColor;
    private float borderWidth = dp(1);

    private final RectF rect = new RectF();
    private final Path path = new Path();

    public OutlineField(Context context) {
      super(context);
      setWillNotDraw(false);
      setBackgroundColor(backgroundColor);
      borderPaint.setStyle(Paint.Style.STROKE);
      borderPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setBorderColor(int color) {
      borderColor = color;
      invalidate();
    }

    public void setBorderWidth(float width) {
      borderWidth = width;
      invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);

      borderPaint.setColor(borderColor);
      borderPaint.setStrokeWidth(borderWidth);

      float half = borderWidth / 2f;
      rect.set(half, half, getWidth() - half, getHeight() - half);
      float radius = dp(cornerRadius);

      if (labelProgress < 0.01f) {
        canvas.drawRoundRect(rect, radius, radius, borderPaint);
        return;
      }

      float labelLeft = label.getLeft();
      float labelScaledWidth = label.getMeasuredWidth() * label.getScaleX();

      float gapLeft = labelLeft - dp(4);
      float gapRight = labelLeft + labelScaledWidth + dp(4);

      path.reset();

      // Top-left segment before label gap
      path.moveTo(rect.left + radius, rect.top);
      path.lineTo(gapLeft, rect.top);

      // Top-right segment after label gap
      path.moveTo(gapRight, rect.top);
      path.lineTo(rect.right - radius, rect.top);

      // Remaining frame segments
      path.moveTo(rect.left, rect.top + radius);
      path.lineTo(rect.left, rect.bottom - radius);

      path.moveTo(rect.left + radius, rect.bottom);
      path.lineTo(rect.right - radius, rect.bottom);

      path.moveTo(rect.right, rect.top + radius);
      path.lineTo(rect.right, rect.bottom - radius);

      // Corners
      path.addArc(rect.left, rect.top, rect.left + radius * 2, rect.top + radius * 2, 180, 90);
      path.addArc(rect.right - radius * 2, rect.top, rect.right, rect.top + radius * 2, 270, 90);
      path.addArc(rect.left, rect.bottom - radius * 2, rect.left + radius * 2, rect.bottom, 90, 90);
      path.addArc(rect.right - radius * 2, rect.bottom - radius * 2, rect.right, rect.bottom, 0, 90);

      canvas.drawPath(path, borderPaint);
    }
  }

  // --- Vector Drawables for Icons ---

  private static class ErrorIconDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int color;

    ErrorIconDrawable(int color) {
      this.color = color;
      paint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public void draw(Canvas canvas) {
      RectF bounds = new RectF(getBounds());
      float cx = bounds.centerX();
      float cy = bounds.centerY();
      float size = Math.min(bounds.width(), bounds.height()) * 0.50f;
      float radius = size * 0.5f;

      path.reset();
      path.moveTo(cx, cy - radius);
      path.lineTo(cx - radius, cy + radius);
      path.lineTo(cx + radius, cy + radius);
      path.close();

      paint.setStyle(Paint.Style.FILL);
      paint.setColor(color);
      canvas.drawPath(path, paint);

      paint.setColor(Color.WHITE);
      paint.setStrokeWidth(size * 0.10f);
      paint.setStyle(Paint.Style.STROKE);
      canvas.drawLine(cx, cy - size * 0.18f, cx, cy + size * 0.12f, paint);

      paint.setStyle(Paint.Style.FILL);
      canvas.drawCircle(cx, cy + size * 0.28f, size * 0.055f, paint);
    }

    @Override
    public void setAlpha(int alpha) {
      paint.setAlpha(alpha);
    }
    @Override
    public void setColorFilter(android.graphics.ColorFilter filter) {
      paint.setColorFilter(filter);
    }
    @Override
    public int getOpacity() {
      return android.graphics.PixelFormat.TRANSLUCENT;
    }
  }

  private static class ClearIconDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color;

    ClearIconDrawable(int color) {
      this.color = color;
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStyle(Paint.Style.STROKE);
    }

    @Override
    public void draw(Canvas canvas) {
      RectF bounds = new RectF(getBounds());
      float cx = bounds.centerX();
      float cy = bounds.centerY();
      float radius = Math.min(bounds.width(), bounds.height()) * 0.22f;

      paint.setColor(color);
      paint.setStrokeWidth(radius * 0.35f);

      canvas.drawLine(cx - radius, cy - radius, cx + radius, cy + radius, paint);
      canvas.drawLine(cx + radius, cy - radius, cx - radius, cy + radius, paint);
    }

    @Override
    public void setAlpha(int alpha) {
      paint.setAlpha(alpha);
    }
    @Override
    public void setColorFilter(android.graphics.ColorFilter filter) {
      paint.setColorFilter(filter);
    }
    @Override
    public int getOpacity() {
      return android.graphics.PixelFormat.TRANSLUCENT;
    }
  }

  private static class EyeIconDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color;
    private final boolean visible;

    EyeIconDrawable(int color, boolean visible) {
      this.color = color;
      this.visible = visible;
      paint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public void draw(Canvas canvas) {
      RectF bounds = new RectF(getBounds());
      float cx = bounds.centerX();
      float cy = bounds.centerY();
      float rx = Math.min(bounds.width(), bounds.height()) * 0.30f;
      float ry = rx * 0.6f;

      paint.setColor(color);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(rx * 0.2f);

      RectF oval = new RectF(cx - rx, cy - ry, cx + rx, cy + ry);
      canvas.drawOval(oval, paint);

      paint.setStyle(Paint.Style.FILL);
      canvas.drawCircle(cx, cy, ry * 0.5f, paint);

      if (!visible) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(cx - rx, cy + ry, cx + rx, cy - ry, paint);
      }
    }

    @Override
    public void setAlpha(int alpha) {
      paint.setAlpha(alpha);
    }
    @Override
    public void setColorFilter(android.graphics.ColorFilter filter) {
      paint.setColorFilter(filter);
    }
    @Override
    public int getOpacity() {
      return android.graphics.PixelFormat.TRANSLUCENT;
    }
  }
}