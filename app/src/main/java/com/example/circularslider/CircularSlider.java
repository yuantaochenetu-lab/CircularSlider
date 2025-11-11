package com.example.circularslider;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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

    // 三层同心圆
    private Paint outerRingPaint;       // 最外层圆环（浅灰色）
    private Paint trackBackgroundPaint; // 可滑动轨道背景（亮粉红）
    private Paint progressPaint;        // 进度弧线（深紫红）
    private Paint innerCirclePaint;     // 内圆（橙黄色）
    private Paint longTickPaint;        // 长刻度线
    private Paint shortTickPaint;       // 短刻度线
    private Paint textPaint;            // 中心文字

    private RectF outerRingBounds;
    private RectF trackBounds;
    private GestureDetector gestureDetector;

    private float outerRingWidth = 18f; // 外环宽度
    private float trackWidth = 100f;    // 轨道宽度（大幅增加，延伸到内圆）

    public CircularSlider(Context context) {
        super(context);
        init(context);
    }

    public CircularSlider(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CircularSlider(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // 最外层圆环 - 浅灰色
        outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerRingPaint.setStyle(Paint.Style.STROKE);
        outerRingPaint.setStrokeWidth(outerRingWidth);
        outerRingPaint.setColor(Color.parseColor("#E0E0E0"));

        // 轨道背景 - 亮粉红色
        trackBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackBackgroundPaint.setStyle(Paint.Style.STROKE);
        trackBackgroundPaint.setStrokeWidth(trackWidth);
        trackBackgroundPaint.setColor(Color.parseColor("#EC407A"));

        // 进度弧线 - 深紫红色
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(trackWidth);
        progressPaint.setColor(Color.parseColor("#880E4F"));
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        // 内圆 - 亮橙黄色
        innerCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerCirclePaint.setStyle(Paint.Style.FILL);
        innerCirclePaint.setColor(Color.parseColor("#FFB74D"));

        // 长刻度 - 深色
        longTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        longTickPaint.setColor(Color.parseColor("#424242"));
        longTickPaint.setStrokeWidth(4f);
        longTickPaint.setStrokeCap(Paint.Cap.ROUND);

        // 短刻度 - 中灰色
        shortTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shortTickPaint.setColor(Color.parseColor("#757575"));
        shortTickPaint.setStrokeWidth(2f);
        shortTickPaint.setStrokeCap(Paint.Cap.ROUND);

        // 中心文字 - 深紫色
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#4A148C"));
        textPaint.setTextSize(80f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        outerRingBounds = new RectF();
        trackBounds = new RectF();

        // 双击归零
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        // 强制正方形（取较小值）
        int size = Math.min(widthSize, heightSize);

        // 计算0.25英寸的最小轨道半径
        float density = getResources().getDisplayMetrics().density;
        float minTrackRadiusPx = 0.25f * density * 160;

        // 最小视图尺寸 = (轨道半径 + 轨道宽度/2 + 外环宽度 + 边距) × 2
        float minViewSize = (minTrackRadiusPx + trackWidth / 2f + outerRingWidth + 20) * 2;

        if (size < minViewSize) {
            size = (int) Math.ceil(minViewSize);
        }

        if (size == 0) {
            size = 300;
        }

        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxRadius = Math.min(cx, cy) - 8;

        // 三层结构 - 轨道延伸到内圆边缘
        // 1. 最外层圆环
        float outerRingRadius = maxRadius - outerRingWidth / 2f;

        // 2. 可滑动轨道（紧贴外环内侧）
        float trackRadius = outerRingRadius - outerRingWidth / 2f - trackWidth / 2f;

        // 3. 内圆（紧贴轨道内侧，无留白）
        float innerRadius = trackRadius - trackWidth / 2f;

        // ===== 绘制最外层灰色圆环 =====
        outerRingBounds.set(
                cx - outerRingRadius, cy - outerRingRadius,
                cx + outerRingRadius, cy + outerRingRadius
        );
        canvas.drawArc(outerRingBounds, 0, 360, false, outerRingPaint);

        // ===== 绘制可滑动轨道背景（粉红色）- 延伸到内圆 =====
        trackBounds.set(
                cx - trackRadius, cy - trackRadius,
                cx + trackRadius, cy + trackRadius
        );
        canvas.drawArc(trackBounds, 0, 360, false, trackBackgroundPaint);

        // ===== 绘制进度弧线（深紫红）- 延伸到内圆 =====
        float sweepAngle = (currentValue / 100f) * 360f;
        if (sweepAngle > 0) {
            canvas.drawArc(trackBounds, -90, sweepAngle, false, progressPaint);
        }

        // ===== 绘制刻度线（在轨道内部）=====
        for (int i = 0; i < 16; i++) {
            float angle = (float) Math.toRadians(i * 22.5f - 90);
            boolean isLong = (i % 2 == 0);
            Paint tickPaint = isLong ? longTickPaint : shortTickPaint;

            // 刻度线在轨道内部，从外向内
            float tickOuterRadius = trackRadius + trackWidth / 2f - 5;
            float tickLength = isLong ? 35f : 20f;
            float tickInnerRadius = tickOuterRadius - tickLength;

            float x1 = cx + (float) Math.cos(angle) * tickOuterRadius;
            float y1 = cy + (float) Math.sin(angle) * tickOuterRadius;
            float x2 = cx + (float) Math.cos(angle) * tickInnerRadius;
            float y2 = cy + (float) Math.sin(angle) * tickInnerRadius;

            canvas.drawLine(x1, y1, x2, y2, tickPaint);
        }

        // ===== 绘制内圆（橙黄色）- 紧贴轨道内侧 =====
        canvas.drawCircle(cx, cy, innerRadius, innerCirclePaint);

        // ===== 绘制中心文字 =====
        String text = String.format("%.0f%%", currentValue);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2;
        canvas.drawText(text, cx, textY, textPaint);
    }

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
                    if (isFirstTouch) {
                        isFirstTouch = false;
                    } else {
                        updateValueWithDelta(currentAngle);
                    }
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

    // 监听接口
    public interface OnValueChangeListener {
        void onValueChanged(float newValue);
    }

    private OnValueChangeListener listener;

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