package com.example.circularslider;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class CircularSlider extends View {

    private float currentValue = 0f;
    private float lastAngle = 0f;
    private boolean isDragging = false;
    private boolean isFirstTouch = true;

    // ---- Paramètres configurables ----
    private float outerRingWidth = 18f;
    private float trackWidth = 100f;
    private float innerCircleRatio = 0.4f;
    private int outerRingColor = Color.parseColor("#E0E0E0");
    private int trackColor = Color.parseColor("#EC407A");
    private int progressColor = Color.parseColor("#880E4F");
    private int innerCircleColor = Color.parseColor("#FFB74D");
    private int textColor = Color.parseColor("#4A148C");
    private float defaultValue = 0f;

    // ---- Pinceaux ----
    private Paint outerRingPaint;
    private Paint trackBackgroundPaint;
    private Paint progressPaint;
    private Paint innerCirclePaint;
    private Paint longTickPaint;
    private Paint shortTickPaint;
    private Paint textPaint;

    private RectF outerRingBounds;
    private RectF trackBounds;
    private GestureDetector gestureDetector;

    private OnValueChangeListener listener;

    // ---- Constructeurs ----
    public CircularSlider(Context context) {
        super(context);
        init(context, null);
    }

    public CircularSlider(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CircularSlider(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    // ---- Initialisation ----
    private void init(Context context, @Nullable AttributeSet attrs) {

        // ① Lecture des attributs personnalisés
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CircularSlider);

            trackWidth = a.getDimension(R.styleable.CircularSlider_trackWidth, 100f);
            outerRingWidth = a.getDimension(R.styleable.CircularSlider_outerRingWidth, 18f);
            outerRingColor = a.getColor(R.styleable.CircularSlider_outerRingColor, Color.parseColor("#E0E0E0"));
            trackColor = a.getColor(R.styleable.CircularSlider_trackColor, Color.parseColor("#EC407A"));
            progressColor = a.getColor(R.styleable.CircularSlider_progressColor, Color.parseColor("#880E4F"));
            innerCircleColor = a.getColor(R.styleable.CircularSlider_innerCircleColor, Color.parseColor("#FFB74D"));
            textColor = a.getColor(R.styleable.CircularSlider_textColor, Color.parseColor("#4A148C"));
            defaultValue = a.getFloat(R.styleable.CircularSlider_defaultValue, 0f);
            innerCircleRatio = a.getFloat(R.styleable.CircularSlider_innerCircleRatio, 0.4f);

            a.recycle();
        }

        currentValue = defaultValue;

        // ② Initialisation des pinceaux
        outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerRingPaint.setStyle(Paint.Style.STROKE);
        outerRingPaint.setStrokeWidth(outerRingWidth);
        outerRingPaint.setColor(outerRingColor);

        trackBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackBackgroundPaint.setStyle(Paint.Style.STROKE);
        trackBackgroundPaint.setStrokeWidth(trackWidth);
        trackBackgroundPaint.setColor(trackColor);

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(trackWidth);
        progressPaint.setColor(progressColor);
        progressPaint.setStrokeCap(Paint.Cap.BUTT);

        innerCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerCirclePaint.setStyle(Paint.Style.FILL);
        innerCirclePaint.setColor(innerCircleColor);

        longTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        longTickPaint.setColor(Color.parseColor("#424242"));
        longTickPaint.setStrokeWidth(4f);
        longTickPaint.setStrokeCap(Paint.Cap.ROUND);

        shortTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shortTickPaint.setColor(Color.parseColor("#757575"));
        shortTickPaint.setStrokeWidth(2f);
        shortTickPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(textColor);
        textPaint.setTextSize(80f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        outerRingBounds = new RectF();
        trackBounds = new RectF();

        // Double tape pour réinitialiser à zéro
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                currentValue = 0;
                invalidate();
                if (listener != null) listener.onValueChanged(currentValue);
                return true;
            }
        });
    }

    // ---- Mesure ----
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(widthSize, heightSize);

        float density = getResources().getDisplayMetrics().density;
        float minTrackRadiusPx = 0.25f * density * 160; // Rayon minimal de la piste : 0,25 pouce
        float minViewSize = (minTrackRadiusPx + trackWidth / 2f + outerRingWidth + 20) * 2;

        if (size < minViewSize) size = (int) Math.ceil(minViewSize);
        if (size == 0) size = 300;
        setMeasuredDimension(size, size);
    }

    // ---- Dessin ----
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxRadius = Math.min(cx, cy) - 8;

        float outerRingRadius = maxRadius - outerRingWidth / 2f;
        float trackRadius = outerRingRadius - outerRingWidth / 2f - trackWidth / 2f;
        float innerRadius = trackRadius - trackWidth * innerCircleRatio;

        // Anneau extérieur
        outerRingBounds.set(cx - outerRingRadius, cy - outerRingRadius, cx + outerRingRadius, cy + outerRingRadius);
        canvas.drawArc(outerRingBounds, 0, 360, false, outerRingPaint);

        // Piste
        trackBounds.set(cx - trackRadius, cy - trackRadius, cx + trackRadius, cy + trackRadius);
        canvas.drawArc(trackBounds, 0, 360, false, trackBackgroundPaint);

        // Progression
        float sweepAngle = (currentValue / 100f) * 360f;
        if (sweepAngle > 0) canvas.drawArc(trackBounds, -90, sweepAngle, false, progressPaint);

        // Graduations
        for (int i = 0; i < 16; i++) {
            float angle = (float) Math.toRadians(i * 22.5f - 90);
            boolean isLong = (i % 2 == 0);
            Paint tickPaint = isLong ? longTickPaint : shortTickPaint;

            float tickOuterRadius = trackRadius + trackWidth / 2f - 5;
            float tickLength = isLong ? 35f : 20f;
            float tickInnerRadius = tickOuterRadius - tickLength;

            float x1 = cx + (float) Math.cos(angle) * tickOuterRadius;
            float y1 = cy + (float) Math.sin(angle) * tickOuterRadius;
            float x2 = cx + (float) Math.cos(angle) * tickInnerRadius;
            float y2 = cy + (float) Math.sin(angle) * tickInnerRadius;
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
        }

        // Cercle intérieur
        canvas.drawCircle(cx, cy, innerRadius, innerCirclePaint);

        // Texte
        String text = String.format("%.0f%%", currentValue);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2;
        canvas.drawText(text, cx, textY, textPaint);
    }

    // ---- Événements tactiles ----
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean isDoubleTap = gestureDetector.onTouchEvent(event);
        if (isDoubleTap) return true;

        float x = event.getX() - getWidth() / 2f;
        float y = event.getY() - getHeight() / 2f;
        double angleRad = Math.atan2(y, x);
        float currentAngle = (float) (Math.toDegrees(angleRad) + 90);
        if (currentAngle < 0) currentAngle += 360;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                isFirstTouch = true;
                lastAngle = currentAngle;
                break;
            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    if (isFirstTouch) isFirstTouch = false;
                    else updateValueWithDelta(currentAngle);
                    lastAngle = currentAngle;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                isFirstTouch = true;
                break;
        }
        return true;
    }

    // ---- Mise à jour de la valeur ----
    private void updateValueWithDelta(float currentAngle) {
        float delta = currentAngle - lastAngle;
        if (delta > 180) delta -= 360;
        else if (delta < -180) delta += 360;

        float newValue = currentValue + (delta / 360f) * 100f;
        newValue = Math.max(0, Math.min(100, newValue));

        if (Math.abs(newValue - currentValue) > 0.01f) {
            currentValue = newValue;
            invalidate();
            if (listener != null) listener.onValueChanged(currentValue);
        }
    }

    // ---- Sauvegarde et restauration de l’état ----
    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState, currentValue);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            SavedState ss = (SavedState) state;
            super.onRestoreInstanceState(ss.getSuperState());
            this.currentValue = ss.currentValue;
            invalidate();
            if (listener != null) listener.onValueChanged(currentValue);
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    private static class SavedState extends BaseSavedState {
        float currentValue;

        SavedState(Parcelable superState, float currentValue) {
            super(superState);
            this.currentValue = currentValue;
        }

        private SavedState(Parcel in) {
            super(in);
            this.currentValue = in.readFloat();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeFloat(currentValue);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    // ---- Interface d’écoute ----
    public interface OnValueChangeListener {
        void onValueChanged(float newValue);
    }

    public void setOnValueChangeListener(OnValueChangeListener listener) {
        this.listener = listener;
    }

    public float getValue() {
        return currentValue;
    }

    public void setValue(float value) {
        currentValue = Math.max(0, Math.min(100, value));
        invalidate();
    }
}
