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

    // 三个同心圆
    private Paint outerCirclePaint;     // 外圆（浅紫色）
    private Paint trackBackgroundPaint; // 中间轨道背景（淡蓝色）
    private Paint progressPaint;        // 深色弧线（红色）
    private Paint innerCirclePaint;     // 内圆（橙色）
    private Paint longTickPaint;        // 长刻度线
    private Paint shortTickPaint;       // 短刻度线
    private Paint textPaint;            // 中心文字

    private RectF trackBounds;
    private GestureDetector gestureDetector;

    private float trackWidth = 60f;     // 调节轨道宽度

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
        // 外圆 - 浅紫
        outerCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerCirclePaint.setStyle(Paint.Style.FILL);
        outerCirclePaint.setColor(Color.parseColor("#E1BEE7"));

        // 调节轨道背景 - 淡蓝
        trackBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackBackgroundPaint.setStyle(Paint.Style.STROKE);
        trackBackgroundPaint.setStrokeWidth(trackWidth);
        trackBackgroundPaint.setColor(Color.parseColor("#B3E5FC"));

        // 深色弧线（表示当前值）
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(trackWidth);
        progressPaint.setColor(Color.parseColor("#F44336"));
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        // 内圆 - 橙
        innerCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerCirclePaint.setStyle(Paint.Style.FILL);
        innerCirclePaint.setColor(Color.parseColor("#FFB74D"));

        // 长刻度
        longTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        longTickPaint.setColor(Color.parseColor("#283593"));
        longTickPaint.setStrokeWidth(4f);

        // 短刻度
        shortTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shortTickPaint.setColor(Color.parseColor("#5C6BC0"));
        shortTickPaint.setStrokeWidth(2f);

        // 中心文字
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#1A237E"));
        textPaint.setTextSize(72f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

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
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy);

        // 外圆半径最大
        float outerRadius = radius - 10;
        float trackRadius = outerRadius - 70;  // 中间轨道
        float innerRadius = trackRadius - trackWidth / 2f - 35; // 内圆更小

        // 绘制外圆
        canvas.drawCircle(cx, cy, outerRadius, outerCirclePaint);

        // 绘制调节轨道背景
        trackBounds.set(cx - trackRadius, cy - trackRadius, cx + trackRadius, cy + trackRadius);
        canvas.drawArc(trackBounds, 0, 360, false, trackBackgroundPaint);

        // 绘制进度弧线
        float sweepAngle = (currentValue / 100f) * 360f;
        if (sweepAngle > 0) {
            canvas.drawArc(trackBounds, -90, sweepAngle, false, progressPaint);
        }

        // 绘制刻度（1/16 圈）
        for (int i = 0; i < 16; i++) {
            float angle = (float) Math.toRadians(i * 22.5f - 90);
            boolean isLong = (i % 2 == 0);
            Paint tickPaint = isLong ? longTickPaint : shortTickPaint;

            float tickLength = isLong ? 25f : 15f;
            float innerTickR = trackRadius - trackWidth / 2 - 5;
            float outerTickR = innerTickR + tickLength;

            float x1 = cx + (float) Math.cos(angle) * innerTickR;
            float y1 = cy + (float) Math.sin(angle) * innerTickR;
            float x2 = cx + (float) Math.cos(angle) * outerTickR;
            float y2 = cy + (float) Math.sin(angle) * outerTickR;
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
        }

        // 绘制内圆
        canvas.drawCircle(cx, cy, innerRadius, innerCirclePaint);

        // 中心文字
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
}
