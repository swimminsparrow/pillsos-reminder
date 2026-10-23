package swimminsparrow.ricordamelo.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import swimminsparrow.ricordamelo.R;

public class CountdownWheelView extends View {

    private Paint trackPaint, arcPaint, textPaint;
    private float progress = 1f; // 0.0 to 1.0
    private String label = "00";
    private int arcColor;

    public CountdownWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        arcColor = ContextCompat.getColor(context, R.color.defaultColor);

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(6.5f));
        trackPaint.setColor(Color.parseColor("#E8F5E9")); // Soft mint tonal track

        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dp(6.5f));
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(arcColor);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dp(22));
        textPaint.setFakeBoldText(true);
        textPaint.setColor(Color.parseColor("#111827")); // primary_text
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float strokeInset = dp(6.5f) / 2f + dp(2);
        float r  = Math.min(cx, cy) - strokeInset;
        RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);

        canvas.drawArc(oval, 0, 360, false, trackPaint);
        if (progress > 0) {
            canvas.drawArc(oval, -90, progress * 360, false, arcPaint);
        }

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        canvas.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
    }

    public void setProgress(float progress) { this.progress = progress; invalidate(); }
    public void setLabel(String label)       { this.label = label;       invalidate(); }
    public void setArcColor(int color)       { arcPaint.setColor(color); invalidate(); }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}