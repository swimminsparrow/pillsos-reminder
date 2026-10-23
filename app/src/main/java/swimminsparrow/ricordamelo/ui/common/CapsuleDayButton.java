package swimminsparrow.ricordamelo.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.Checkable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;

/**
 * Premium Day Chip featuring a clean ergonomic stadium pill body and an authentic
 * 3D/vector miniature capsule icon on the left of the day label.
 *
 * Active state: Glowing emerald chip with radiant mint+pearl capsule and crisp white text.
 * Inactive state: Crisp white card with neutral blister/slate capsule and slate text.
 */
public class CapsuleDayButton extends AppCompatButton implements Checkable {

    private boolean isChecked = false;

    // Chip background & border paints
    private final Paint paintChipBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintChipBorder = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Mini capsule paints
    private final Paint paintCap = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBody = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintSeam = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCapsuleBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGloss = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Geometry caches
    private final Path pathCap = new Path();
    private final Path pathBody = new Path();
    private final RectF chipBounds = new RectF();
    private final RectF arcRect = new RectF();
    private final RectF capsuleOutline = new RectF();

    public CapsuleDayButton(@NonNull Context context) {
        super(context);
        init();
    }

    public CapsuleDayButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CapsuleDayButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setBackground(null);
        setTransformationMethod(null);
        setGravity(Gravity.CENTER);

        // Generous padding: left leaves space for the mini capsule (32dp), right balances it (14dp)
        int pLeft = (int) dpToPx(32f);
        int pRight = (int) dpToPx(14f);
        setPadding(pLeft, 0, pRight, 0);

        paintChipBg.setStyle(Paint.Style.FILL);

        paintChipBorder.setStyle(Paint.Style.STROKE);
        paintChipBorder.setStrokeWidth(dpToPx(1.5f));

        paintCap.setStyle(Paint.Style.FILL);
        paintBody.setStyle(Paint.Style.FILL);
        paintSeam.setStyle(Paint.Style.FILL);

        paintCapsuleBorder.setStyle(Paint.Style.STROKE);
        paintCapsuleBorder.setStrokeWidth(dpToPx(0.8f));

        paintGloss.setStyle(Paint.Style.STROKE);
        paintGloss.setStrokeCap(Paint.Cap.ROUND);
        paintGloss.setStrokeWidth(dpToPx(1.2f));

        updateColors();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void updateColors() {
        if (isChecked) {
            // Active: Brand Emerald Chip with Glowing Radiant Capsule
            paintChipBg.setColor(Color.parseColor("#1E6F3E"));     // Deep brand emerald
            paintChipBorder.setColor(Color.parseColor("#144D29")); // Subtle darker boundary
            setTextColor(Color.WHITE);

            // Capsule: Radiant Mint cap, pure Pearl white body, deep emerald seam
            paintCap.setColor(Color.parseColor("#34D399"));         // Vibrant mint
            paintBody.setColor(Color.WHITE);                       // Pearl white
            paintSeam.setColor(Color.parseColor("#14532D"));       // Deep emerald seam
            paintCapsuleBorder.setColor(Color.parseColor("#14532D"));
            paintGloss.setColor(Color.argb(230, 255, 255, 255));   // Bright specular highlight
        } else {
            // Inactive: Neutral White Chip with Empty Blister/Slate Capsule
            paintChipBg.setColor(Color.WHITE);
            paintChipBorder.setColor(Color.parseColor("#CBD5E1")); // Slate 300
            setTextColor(Color.parseColor("#374151"));             // Slate 700

            // Capsule: Cool slate cap, soft pearl body
            paintCap.setColor(Color.parseColor("#94A3B8"));         // Slate 400
            paintBody.setColor(Color.parseColor("#F1F5F9"));        // Slate 100
            paintSeam.setColor(Color.parseColor("#94A3B8"));
            paintCapsuleBorder.setColor(Color.parseColor("#94A3B8"));
            paintGloss.setColor(Color.argb(160, 255, 255, 255));   // Soft highlight
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            super.onDraw(canvas);
            return;
        }

        // 1. Draw Chip Stadium Pill Background & Border
        float strokeInset = paintChipBorder.getStrokeWidth() / 2f;
        chipBounds.set(strokeInset, strokeInset, w - strokeInset, h - strokeInset);
        float r = chipBounds.height() / 2f;

        canvas.drawRoundRect(chipBounds, r, r, paintChipBg);
        canvas.drawRoundRect(chipBounds, r, r, paintChipBorder);

        // 2. Draw Miniature 3D Vector Capsule on the left
        canvas.save();

        float capsuleCenterX = dpToPx(18f);
        float capsuleCenterY = chipBounds.centerY();
        canvas.translate(capsuleCenterX, capsuleCenterY);
        canvas.rotate(-25f); // 25 degree energetic inclination

        float capLen = dpToPx(17f);
        float capDiam = dpToPx(7.5f);
        float rad = capDiam / 2f;
        float halfL = capLen / 2f;
        float cylW = halfL - rad;

        // A. Left Cap (Hemisphere on the far left + cylinder half to center)
        pathCap.reset();
        arcRect.set(-halfL, -rad, -halfL + 2 * rad, rad);
        pathCap.arcTo(arcRect, 90, 180, false);
        pathCap.lineTo(0, -rad);
        pathCap.lineTo(0, rad);
        pathCap.close();
        canvas.drawPath(pathCap, paintCap);

        // B. Right Body (Cylinder half from center + hemisphere on far right)
        pathBody.reset();
        pathBody.moveTo(0, -rad);
        pathBody.lineTo(cylW, -rad);
        arcRect.set(halfL - 2 * rad, -rad, halfL, rad);
        pathBody.arcTo(arcRect, 270, 180, false);
        pathBody.lineTo(0, rad);
        pathBody.close();
        canvas.drawPath(pathBody, paintBody);

        // C. Central Interlocking Seam
        float seamW = dpToPx(1.4f);
        canvas.drawRect(-seamW / 2f, -rad, seamW / 2f, rad, paintSeam);

        // D. Gelatin / Enamel Specular Gloss Streak along the top
        canvas.drawLine(-halfL * 0.65f, -rad * 0.45f, halfL * 0.65f, -rad * 0.45f, paintGloss);

        // E. Crisp Capsule Outline
        capsuleOutline.set(-halfL, -rad, halfL, rad);
        canvas.drawRoundRect(capsuleOutline, rad, rad, paintCapsuleBorder);

        canvas.restore();

        // 3. Draw text via AppCompatButton
        super.onDraw(canvas);
    }

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        if (this.isChecked != checked) {
            this.isChecked = checked;
            updateColors();
        }
    }

    @Override
    public void toggle() {
        setChecked(!isChecked);
    }

    @Override
    public boolean performClick() {
        toggle();
        return super.performClick();
    }
}
