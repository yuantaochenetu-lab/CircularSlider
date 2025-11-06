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

    private float currentValue = 0f;     // 当前值 (0–100)
    private float lastAngle = 0f;        // 上一次的角度
    private boolean isFirstTouch = true; // 是否首次触摸

    // 三个同心圆盘的画笔
    private Paint outerDiskPaint;        // 外圆盘
    private Paint middleTrackPaint;      // 中间调节轨道（背景）
    private Paint innerDiskPaint;        // 内圆盘

    // 深色弧线画笔
    private Paint darkArcPaint;          // 表示当前数值的深色弧线

    // 刻度画笔
    private Paint longTickPaint;         // 长刻度线（每 1/8 圈）
    private Paint shortTickPaint;        // 短刻度线（每 1/16 圈）

    // 文字画笔
    private Paint textPaint;             // 百分比文字

    private RectF trackBounds;           // 调节轨道边界
    private RectF arcBounds;             // 深色弧线边界

    private boolean isDragging = false;
    private GestureDetector gestureDetector;

    // 圆盘尺寸配置
    private float trackWidth = 50f;      // 调节轨道宽度

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
        // 外圆盘 - 鲜艳的紫色
        outerDiskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerDiskPaint.setStyle(Paint.Style.FILL);
        outerDiskPaint.setColor(Color.parseColor("#E1BEE7"));

        // 中间调节轨道 - 亮白色背景
        middleTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        middleTrackPaint.setStyle(Paint.Style.STROKE);
        middleTrackPaint.setStrokeWidth(trackWidth);
        middleTrackPaint.setColor(Color.parseColor("#F3E5F5"));

        // 内圆盘 - 鲜艳的青色
        innerDiskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerDiskPaint.setStyle(Paint.Style.FILL);
        innerDiskPaint.setColor(Color.parseColor("#B2EBF2"));

        // 深色弧线 - 鲜艳的洋红色
        darkArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        darkArcPaint.setStyle(Paint.Style.STROKE);
        darkArcPaint.setStrokeWidth(trackWidth);
        darkArcPaint.setColor(Color.parseColor("#E91E63"));
        darkArcPaint.setStrokeCap(Paint.Cap.ROUND);

        // 长刻度线 - 深紫色
        longTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        longTickPaint.setColor(Color.parseColor("#7B1FA2"));
        longTickPaint.setStrokeWidth(4f);
        longTickPaint.setStrokeCap(Paint.Cap.ROUND);

        // 短刻度线 - 中紫色
        shortTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shortTickPaint.setColor(Color.parseColor("#BA68C8"));
        shortTickPaint.setStrokeWidth(2f);
        shortTickPaint.setStrokeCap(Paint.Cap.ROUND);

        // 百分比文字 - 深色
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#4A148C"));
        textPaint.setTextSize(72f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        trackBounds = new RectF();
        arcBounds = new RectF();

        // 手势检测器 - 双击归零
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                currentValue = 0;
                lastAngle = 0;
                invalidate();
                if (listener != null) listener.onValueChanged(currentValue);
                return true;
            }
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int size;

        if (widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
            size = Math.min(widthSize, heightSize);
        } else if (widthMode == MeasureSpec.EXACTLY) {
            size = widthSize;
        } else if (heightMode == MeasureSpec.EXACTLY) {
            size = heightSize;
        } else {
            size = Math.min(widthSize, heightSize);
            if (size == 0) {
                size = 300;
            }
        }

        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (width == 0 || height == 0) {
            return;
        }

        float centerX = width / 2f;
        float centerY = height / 2f;
        float maxRadius = Math.min(width, height) / 2f - 20;

        // 计算三个圆盘的半径
        float outerRadius = maxRadius;
        float trackRadius = maxRadius - 40;  // 调节轨道的中心半径
        float innerRadius = trackRadius - trackWidth / 2 - 15;

        // ===== 1. 绘制外圆盘 =====
        canvas.drawCircle(centerX, centerY, outerRadius, outerDiskPaint);

        // ===== 2. 绘制中间调节轨道（白色背景）=====
        trackBounds.set(centerX - trackRadius, centerY - trackRadius,
                centerX + trackRadius, centerY + trackRadius);
        canvas.drawArc(trackBounds, 0, 360, false, middleTrackPaint);

        // ===== 3. 绘制深色弧线（表示当前数值）=====
        // 起始角度：-90°（顶部），扫过角度：(currentValue / 100) * 360°
        float sweepAngle = (currentValue / 100f) * 360f;
        arcBounds.set(trackBounds);
        if (sweepAngle > 0) {
            canvas.drawArc(arcBounds, -90, sweepAngle, false, darkArcPaint);
        }

        // ===== 4. 绘制刻度线 =====
        // 总共 16 个刻度位置（每 1/16 圈）
        for (int i = 0; i < 16; i++) {
            float angle = (float) Math.toRadians(i * 22.5f - 90); // 从顶部开始

            boolean isLongTick = (i % 2 == 0); // 偶数位置是长刻度
            Paint tickPaint = isLongTick ? longTickPaint : shortTickPaint;

            // 长刻度从轨道内侧延伸到外侧
            float tickLength = isLongTick ? trackWidth + 10 : trackWidth / 2;
            float innerTickRadius = trackRadius - trackWidth / 2 - 5;
            float outerTickRadius = innerTickRadius + tickLength;

            float x1 = centerX + (float) Math.cos(angle) * innerTickRadius;
            float y1 = centerY + (float) Math.sin(angle) * innerTickRadius;
            float x2 = centerX + (float) Math.cos(angle) * outerTickRadius;
            float y2 = centerY + (float) Math.sin(angle) * outerTickRadius;

            canvas.drawLine(x1, y1, x2, y2, tickPaint);
        }

        // ===== 5. 绘制内圆盘 =====
        canvas.drawCircle(centerX, centerY, innerRadius, innerDiskPaint);

        // ===== 6. 绘制中心百分比文字 =====
        String text = String.format("%.0f%%", currentValue);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = centerY - (fm.ascent + fm.descent) / 2;
        canvas.drawText(text, centerX, textY, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 先让手势检测器处理双击
        boolean isDoubleTap = gestureDetector.onTouchEvent(event);
        if (isDoubleTap) {
            return true;
        }

        float x = event.getX() - getWidth() / 2f;
        float y = event.getY() - getHeight() / 2f;

        // 计算角度（从顶部开始，顺时针，0-360°）
        double angleRad = Math.atan2(y, x);
        float currentAngle = (float) (Math.toDegrees(angleRad) + 90);
        if (currentAngle < 0) currentAngle += 360;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                isFirstTouch = true;
                lastAngle = currentAngle;
                // 首次触摸时，直接设置到触摸位置
                updateValueFromAngle(currentAngle);
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    if (isFirstTouch) {
                        // 第一次移动，直接使用当前角度
                        updateValueFromAngle(currentAngle);
                        isFirstTouch = false;
                    } else {
                        // 后续移动，使用增量更新
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

    // 使用角度增量更新数值（防止跳变）
    private void updateValueWithDelta(float currentAngle) {
        // 计算角度差
        float delta = currentAngle - lastAngle;

        // 处理角度跨越0°/360°的情况
        if (delta > 180) {
            delta -= 360; // 实际是逆时针移动
        } else if (delta < -180) {
            delta += 360; // 实际是顺时针移动
        }

        // 将角度差转换为百分比差
        float valueDelta = (delta / 360f) * 100f;

        // 更新数值，限制在0-100范围内
        float newValue = currentValue + valueDelta;

        // 严格限制在0-100之间
        if (newValue < 0) {
            newValue = 0;
        } else if (newValue > 100) {
            newValue = 100;
        }

        // 只有数值真正改变时才更新
        if (Math.abs(newValue - currentValue) > 0.01f) {
            currentValue = newValue;
            invalidate();
            if (listener != null) listener.onValueChanged(currentValue);
        }
    }

    // 从角度直接设置数值（首次触摸时使用）
    private void updateValueFromAngle(float angle) {
        currentValue = Math.max(0, Math.min(100, angle / 360f * 100f));
        invalidate();
        if (listener != null) listener.onValueChanged(currentValue);
    }

    // ===== 公共 API =====

    public void setValue(float value) {
        currentValue = Math.max(0, Math.min(100, value));
        invalidate();
    }

    public float getValue() {
        return currentValue;
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