package swimminsparrow.ricordamelo.ui.common.Dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import swimminsparrow.ricordamelo.R;

/**
 * Modern Material 3 Dialog Builder.
 * Replaces legacy SweetAlert with clean, accessible, consistent dialogs.
 */
public class DialogBuilder {

    public enum Type {
        SUCCESS,
        ERROR,
        WARNING,
        INFO,
        DESTRUCTIVE,
        APPROVAL,
        CUSTOM_IMAGE,
        PROGRESS
    }

    public interface OnClickListener {
        void onClick(DialogBuilder dialog);
    }

    private final Context context;
    private final Type type;

    private String titleText;
    private CharSequence contentText;
    private Integer messageGravity = null;
    private String confirmText = "OK";
    private String cancelText = "Annulla";
    private boolean showCancelButton = false;
    private boolean cancelable = true;
    private int customImageResId = 0;
    private Boolean stackedButtons = null;
    private boolean confirmButtonDestructive = false;
    private int customIconResId = 0;
    private Integer customIconTint = null;
    private Integer customConfirmButtonColor = null;
    private boolean keepScreenOn = false;

    private OnClickListener confirmClickListener;
    private OnClickListener cancelClickListener;

    private Dialog dialog;
    private TextView tvTitle;
    private TextView tvMessage;

    public DialogBuilder(@NonNull Context context, @NonNull Type type) {
        this.context = context;
        this.type = type;
        this.confirmText = context.getString(android.R.string.ok);
        this.cancelText = context.getString(R.string.dialog_cancel);

        if (type == Type.DESTRUCTIVE || type == Type.APPROVAL) {
            this.showCancelButton = true;
        }
    }

    public DialogBuilder setTitleText(String title) {
        this.titleText = title;
        if (tvTitle != null) {
            tvTitle.setText(title != null ? title : "");
            tvTitle.setVisibility(title != null && !title.isEmpty() ? View.VISIBLE : View.GONE);
        }
        return this;
    }

    public DialogBuilder setContentText(CharSequence content) {
        this.contentText = content;
        if (tvMessage != null) {
            tvMessage.setText(content != null ? content : "");
            tvMessage.setVisibility(content != null && content.length() > 0 ? View.VISIBLE : View.GONE);
        }
        return this;
    }

    public DialogBuilder setMessageGravity(int gravity) {
        this.messageGravity = gravity;
        if (tvMessage != null) {
            tvMessage.setGravity(gravity);
            if (gravity == Gravity.START || gravity == Gravity.LEFT) {
                tvMessage.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            } else if (gravity == Gravity.CENTER || gravity == Gravity.CENTER_HORIZONTAL) {
                tvMessage.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            }
        }
        return this;
    }

    public DialogBuilder setConfirmText(String confirmText) {
        this.confirmText = confirmText;
        return this;
    }

    public DialogBuilder setCancelText(String cancelText) {
        this.cancelText = cancelText;
        this.showCancelButton = true;
        return this;
    }

    public DialogBuilder showCancelButton(boolean show) {
        this.showCancelButton = show;
        return this;
    }

    public DialogBuilder setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
        if (dialog != null) {
            dialog.setCancelable(cancelable);
            dialog.setCanceledOnTouchOutside(cancelable);
        }
        return this;
    }

    public Dialog getDialog() {
        return dialog;
    }

    public DialogBuilder setCustomImage(int resId) {
        this.customImageResId = resId;
        return this;
    }

    public DialogBuilder setIcon(int iconResId) {
        this.customIconResId = iconResId;
        return this;
    }

    public DialogBuilder setIconTint(int color) {
        this.customIconTint = color;
        return this;
    }

    public DialogBuilder setConfirmButtonDestructive(boolean destructive) {
        this.confirmButtonDestructive = destructive;
        return this;
    }

    public DialogBuilder setConfirmClickListener(OnClickListener listener) {
        this.confirmClickListener = listener;
        return this;
    }

    public DialogBuilder setCancelClickListener(OnClickListener listener) {
        this.cancelClickListener = listener;
        this.showCancelButton = true;
        return this;
    }

    public Dialog create() {
        if (dialog != null) return dialog;

        Context themedContext = new androidx.appcompat.view.ContextThemeWrapper(context, R.style.AppTheme);
        dialog = new Dialog(themedContext);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);

        if (type == Type.PROGRESS) {
            View view = LayoutInflater.from(themedContext).inflate(R.layout.dialog_app_progress, null);
            dialog.setContentView(view);

            tvTitle = view.findViewById(R.id.tvProgressTitle);
            tvMessage = view.findViewById(R.id.tvProgressMessage);

            if (tvTitle != null && titleText != null) {
                tvTitle.setText(titleText);
            }
            if (tvMessage != null) {
                if (contentText != null && contentText.length() > 0) {
                    tvMessage.setText(contentText);
                    tvMessage.setVisibility(View.VISIBLE);
                } else {
                    tvMessage.setVisibility(View.GONE);
                }
            }
        } else {
            View view = LayoutInflater.from(themedContext).inflate(R.layout.dialog_app_alert, null);
            dialog.setContentView(view);

            FrameLayout iconContainer = view.findViewById(R.id.dialogIconContainer);
            ImageView ivIcon = view.findViewById(R.id.ivDialogIcon);
            MaterialCardView cardCustomImage = view.findViewById(R.id.cardDialogCustomImage);
            ImageView ivCustomImage = view.findViewById(R.id.ivDialogCustomImage);

            tvTitle = view.findViewById(R.id.tvDialogTitle);
            tvMessage = view.findViewById(R.id.tvDialogMessage);

            MaterialButton btnNegative = view.findViewById(R.id.btnDialogNegative);
            MaterialButton btnPositive = view.findViewById(R.id.btnDialogPositive);
            LinearLayout buttonsContainer = view.findViewById(R.id.dialogButtonsContainer);

            if (tvTitle != null) {
                tvTitle.setText(titleText != null ? titleText : "");
                tvTitle.setVisibility(titleText != null && !titleText.isEmpty() ? View.VISIBLE : View.GONE);
            }

            if (tvMessage != null) {
                tvMessage.setText(contentText != null ? contentText : "");
                tvMessage.setVisibility(contentText != null && contentText.length() > 0 ? View.VISIBLE : View.GONE);
                if (messageGravity != null) {
                    tvMessage.setGravity(messageGravity);
                    if (messageGravity == Gravity.START || messageGravity == Gravity.LEFT) {
                        tvMessage.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                    } else if (messageGravity == Gravity.CENTER || messageGravity == Gravity.CENTER_HORIZONTAL) {
                        tvMessage.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                    }
                }
            }

            setupVisualTheme(iconContainer, ivIcon, cardCustomImage, ivCustomImage, btnNegative, btnPositive);

            if (btnPositive != null) {
                btnPositive.setText(confirmText);
                btnPositive.setOnClickListener(v -> {
                    dismiss();
                    if (confirmClickListener != null) {
                        confirmClickListener.onClick(this);
                    }
                });
            }

            if (btnNegative != null) {
                btnNegative.setText(cancelText);
                btnNegative.setOnClickListener(v -> {
                    dismiss();
                    if (cancelClickListener != null) {
                        cancelClickListener.onClick(this);
                    }
                });
            }

            // Arrange buttons: stack vertically if long labels, or keep horizontal
            boolean isStacked = stackedButtons != null ? stackedButtons : (
                    showCancelButton && (
                            (confirmText != null && confirmText.length() > 12) ||
                            (cancelText != null && cancelText.length() > 12) ||
                            ((confirmText != null ? confirmText.length() : 0) + (cancelText != null ? cancelText.length() : 0) > 20)
                    )
            );

            float density = context.getResources().getDisplayMetrics().density;
            int gap8dp = (int) (8 * density);

            buttonsContainer.removeAllViews();
            if (isStacked) {
                buttonsContainer.setOrientation(LinearLayout.VERTICAL);

                // In stacked mode: Affirmative/Confirm button on top (full width)
                LinearLayout.LayoutParams posParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                btnPositive.setLayoutParams(posParams);
                buttonsContainer.addView(btnPositive);

                // Dismissive/Cancel button below (full width with top margin)
                if (showCancelButton) {
                    btnNegative.setVisibility(View.VISIBLE);
                    LinearLayout.LayoutParams negParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    negParams.topMargin = gap8dp;
                    btnNegative.setLayoutParams(negParams);
                    buttonsContainer.addView(btnNegative);
                } else {
                    btnNegative.setVisibility(View.GONE);
                }
            } else {
                buttonsContainer.setOrientation(LinearLayout.HORIZONTAL);

                if (showCancelButton) {
                    btnNegative.setVisibility(View.VISIBLE);
                    LinearLayout.LayoutParams negParams = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    negParams.setMarginEnd(gap8dp);
                    btnNegative.setLayoutParams(negParams);
                    buttonsContainer.addView(btnNegative);
                } else {
                    btnNegative.setVisibility(View.GONE);
                }

                LinearLayout.LayoutParams posParams = new LinearLayout.LayoutParams(
                        showCancelButton ? 0 : ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        showCancelButton ? 1f : 0f);
                if (showCancelButton) {
                    posParams.setMarginStart(gap8dp);
                }
                btnPositive.setLayoutParams(posParams);
                buttonsContainer.addView(btnPositive);
            }
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int maxDialogWidth = (int) (480 * context.getResources().getDisplayMetrics().density);
            int dialogWidth = Math.min(screenWidth, maxDialogWidth);
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (keepScreenOn) {
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }

        return dialog;
    }

    private void setupVisualTheme(FrameLayout iconContainer, ImageView ivIcon,
                                  MaterialCardView cardCustomImage, ImageView ivCustomImage,
                                  MaterialButton btnNegative, MaterialButton btnPositive) {
        int primaryGreen = ContextCompat.getColor(context, R.color.defaultColor);
        int destructiveRed = ContextCompat.getColor(context, R.color.redButton);

        switch (type) {
            case SUCCESS:
                iconContainer.setVisibility(View.VISIBLE);
                cardCustomImage.setVisibility(View.GONE);
                iconContainer.setBackgroundResource(R.drawable.bg_badge_receiver);
                ivIcon.setImageResource(customIconResId != 0 ? customIconResId : R.drawable.ic_check_circle_24);
                ivIcon.setImageTintList(ColorStateList.valueOf(customIconTint != null ? customIconTint : primaryGreen));
                btnPositive.setBackgroundTintList(ColorStateList.valueOf(primaryGreen));
                btnPositive.setTextColor(Color.WHITE);
                break;

            case ERROR:
                iconContainer.setVisibility(View.VISIBLE);
                cardCustomImage.setVisibility(View.GONE);
                iconContainer.setBackgroundResource(R.drawable.bg_badge_destructive);
                ivIcon.setImageResource(customIconResId != 0 ? customIconResId : R.drawable.ic_error_outline_24);
                ivIcon.setImageTintList(ColorStateList.valueOf(customIconTint != null ? customIconTint : destructiveRed));
                btnPositive.setBackgroundTintList(ColorStateList.valueOf(destructiveRed));
                btnPositive.setTextColor(Color.WHITE);
                break;

            case WARNING:
                iconContainer.setVisibility(View.VISIBLE);
                cardCustomImage.setVisibility(View.GONE);
                iconContainer.setBackgroundResource(R.drawable.bg_badge_warning);
                ivIcon.setImageResource(customIconResId != 0 ? customIconResId : R.drawable.ic_warning_amber_24);
                ivIcon.setImageTintList(ColorStateList.valueOf(customIconTint != null ? customIconTint : Color.parseColor("#F57C00")));
                btnPositive.setBackgroundTintList(ColorStateList.valueOf(primaryGreen));
                btnPositive.setTextColor(Color.WHITE);
                if (btnNegative != null) {
                    btnNegative.setStrokeColor(ColorStateList.valueOf(primaryGreen));
                    btnNegative.setTextColor(primaryGreen);
                }
                break;

            case DESTRUCTIVE:
                iconContainer.setVisibility(View.VISIBLE);
                cardCustomImage.setVisibility(View.GONE);
                iconContainer.setBackgroundResource(R.drawable.bg_badge_destructive);
                ivIcon.setImageResource(customIconResId != 0 ? customIconResId : R.drawable.ic_delete_outline_24);
                ivIcon.setImageTintList(ColorStateList.valueOf(customIconTint != null ? customIconTint : destructiveRed));
                btnPositive.setBackgroundTintList(ColorStateList.valueOf(destructiveRed));
                btnPositive.setTextColor(Color.WHITE);
                if (btnNegative != null) {
                    btnNegative.setStrokeColor(ColorStateList.valueOf(primaryGreen));
                    btnNegative.setTextColor(primaryGreen);
                }
                break;

            case APPROVAL:
                iconContainer.setVisibility(View.VISIBLE);
                cardCustomImage.setVisibility(View.GONE);
                iconContainer.setBackgroundResource(R.drawable.bg_badge_receiver);
                ivIcon.setImageResource(customIconResId != 0 ? customIconResId : R.drawable.baseline_done_24);
                ivIcon.setImageTintList(ColorStateList.valueOf(customIconTint != null ? customIconTint : primaryGreen));
                btnPositive.setBackgroundTintList(ColorStateList.valueOf(primaryGreen));
                btnPositive.setTextColor(Color.WHITE);
                if (btnNegative != null) {
                    btnNegative.setStrokeColor(ColorStateList.valueOf(destructiveRed));
                    btnNegative.setTextColor(destructiveRed);
                }
                break;

            case INFO:
                iconContainer.setVisibility(View.VISIBLE);
                cardCustomImage.setVisibility(View.GONE);
                iconContainer.setBackgroundResource(R.drawable.bg_badge_sender);
                ivIcon.setImageResource(customIconResId != 0 ? customIconResId : R.drawable.baseline_info_24);
                ivIcon.setImageTintList(ColorStateList.valueOf(customIconTint != null ? customIconTint : Color.parseColor("#1565C0")));
                btnPositive.setBackgroundTintList(ColorStateList.valueOf(primaryGreen));
                btnPositive.setTextColor(Color.WHITE);
                break;

            case CUSTOM_IMAGE:
                iconContainer.setVisibility(View.GONE);
                cardCustomImage.setVisibility(View.VISIBLE);
                if (customImageResId != 0) {
                    ivCustomImage.setImageResource(customImageResId);
                }
                btnPositive.setBackgroundTintList(ColorStateList.valueOf(primaryGreen));
                btnPositive.setTextColor(Color.WHITE);
                if (btnNegative != null) {
                    btnNegative.setStrokeColor(ColorStateList.valueOf(primaryGreen));
                    btnNegative.setTextColor(primaryGreen);
                }
                break;

            default:
                break;
        }

        if (confirmButtonDestructive) {
            btnPositive.setBackgroundTintList(ColorStateList.valueOf(destructiveRed));
        }
        if (customConfirmButtonColor != null) {
            btnPositive.setBackgroundTintList(ColorStateList.valueOf(customConfirmButtonColor));
        }
    }

    public DialogBuilder show() {
        if (dialog == null) {
            create();
        }
        if (dialog != null && !isHostFinished()) {
            dialog.show();
        }
        return this;
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {}
        }
    }

    private boolean isHostFinished() {
        Activity a = getActivityFromContext(context);
        if (a != null) {
            return a.isFinishing() || a.isDestroyed();
        }
        return !(context instanceof Activity);
    }

    public static Activity getActivityFromContext(Context c) {
        while (c instanceof android.content.ContextWrapper) {
            if (c instanceof Activity) {
                return (Activity) c;
            }
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    // =========================================================================
    // Static Convenience Helpers
    // =========================================================================

    public static DialogBuilder successDialog(Context c, String title, String message) {
        return new DialogBuilder(c, Type.SUCCESS)
                .setTitleText(title)
                .setContentText(message);
    }

    public static DialogBuilder errorDialog(Context c, String title, String message) {
        DialogBuilder d = new DialogBuilder(c, Type.ERROR)
                .setTitleText(title)
                .setContentText(message);
        d.show();
        return d;
    }

    public static DialogBuilder errorDialog(Context c, int titleId, int messageId) {
        return errorDialog(c, c.getString(titleId), c.getString(messageId));
    }

}
