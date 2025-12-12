package com.dtech.automation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class PieChartView extends View {
    private Paint paint;
    private RectF rectF;
    private int successCount = 0;
    private int failureCount = 0;

    public PieChartView(Context context) {
        super(context);
        init();
    }
    public PieChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        rectF = new RectF();
    }

    public void setData(int success, int failure) {
        this.successCount = success;
        this.failureCount = failure;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int total = successCount + failureCount;
        if (total == 0) return;

        float width = getWidth();
        float height = getHeight();
        float radius = Math.min(width, height) / 2 * 0.8f;
        float cx = width / 2;
        float cy = height / 2;

        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius);

        float successAngle = 360f * successCount / total;
        float failureAngle = 360f * failureCount / total;

        // Draw Success (Green)
        paint.setColor(Color.parseColor("#4CAF50"));
        canvas.drawArc(rectF, -90, successAngle, true, paint);

        // Draw Failure (Red)
        paint.setColor(Color.parseColor("#F44336"));
        canvas.drawArc(rectF, -90 + successAngle, failureAngle, true, paint);
    }
}
