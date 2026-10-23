package swimminsparrow.ricordamelo.ui.common;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import swimminsparrow.ricordamelo.R;

/**
 * Reusable utility to display beautiful, brand-coherent Snackbars across the app.
 * Features a floating emerald pill card with subtle elevation, frosted icon badge,
 * and clean typography.
 * Strictly anchored to the bottom of the screen, perfectly centered horizontally.
 */
public class CommonSnackbar {

    public static void show(@Nullable Activity activity, @StringRes int resId) {
        show(activity, resId, Snackbar.LENGTH_SHORT);
    }

    public static void show(@Nullable Activity activity, @StringRes int resId, int duration) {
        if (activity == null) return;
        show(activity, activity.getString(resId), duration);
    }

    public static void show(@Nullable Activity activity, @NonNull CharSequence message) {
        show(activity, message, Snackbar.LENGTH_SHORT);
    }

    public static void show(@Nullable Activity activity, @NonNull CharSequence message, int duration) {
        showCustom(activity, message, R.drawable.baseline_done_24, null, null, duration);
    }

    public static void showWithIcon(@Nullable Activity activity, @NonNull CharSequence message, @DrawableRes int iconRes) {
        showCustom(activity, message, iconRes, null, null, Snackbar.LENGTH_SHORT);
    }

    public static void show(@Nullable Fragment fragment, @StringRes int resId) {
        if (fragment == null) return;
        show(fragment.getActivity(), resId);
    }

    public static void show(@Nullable Fragment fragment, @NonNull CharSequence message) {
        if (fragment == null) return;
        show(fragment.getActivity(), message);
    }

    public static void showWithAction(@Nullable Activity activity,
                                      @StringRes int messageResId,
                                      @StringRes int actionResId,
                                      @NonNull View.OnClickListener actionListener) {
        if (activity == null) return;
        showCustom(activity, activity.getString(messageResId), R.drawable.baseline_done_24, activity.getString(actionResId), actionListener, Snackbar.LENGTH_LONG);

    }

    public static void show(@Nullable View view, @StringRes int resId) {
        if (view == null) return;
        show(view, view.getContext().getString(resId), Snackbar.LENGTH_SHORT);
    }

    public static void show(@Nullable View view, @NonNull CharSequence message) {
        show(view, message, Snackbar.LENGTH_SHORT);
    }

    public static void show(@Nullable View view, @NonNull CharSequence message, int duration) {
        if (view == null) return;
        Activity activity = view.getContext() instanceof Activity ? (Activity) view.getContext() : null;
        if (activity != null) {
            show(activity, message, duration);
        }
    }

    private static void showCustom(@Nullable Activity activity,
                                   @NonNull CharSequence message,
                                   @DrawableRes int iconRes,
                                   @Nullable CharSequence actionText,
                                   @Nullable View.OnClickListener actionListener,
                                   int duration) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        content.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;

            Snackbar snackbar = Snackbar.make(content, "", duration);
            snackbar.setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE);

            Snackbar.SnackbarLayout layout = (Snackbar.SnackbarLayout) snackbar.getView();
            layout.setBackgroundColor(Color.TRANSPARENT);
            layout.setPadding(0, 0, 0, 0);

            // Hide default text and action views
            View defaultText = layout.findViewById(com.google.android.material.R.id.snackbar_text);
            if (defaultText != null) defaultText.setVisibility(View.GONE);
            View defaultAction = layout.findViewById(com.google.android.material.R.id.snackbar_action);
            if (defaultAction != null) defaultAction.setVisibility(View.GONE);

            // Inflate modern custom pill view
            View customView = LayoutInflater.from(activity).inflate(R.layout.layout_custom_snackbar, layout, false);

            TextView tvMessage = customView.findViewById(R.id.tvSnackbarMessage);
            if (tvMessage != null) {
                tvMessage.setText(message);
            }

            ImageView ivIcon = customView.findViewById(R.id.ivSnackbarIcon);
            if (ivIcon != null && iconRes != 0) {
                ivIcon.setImageResource(iconRes);
            }

            TextView btnAction = customView.findViewById(R.id.btnSnackbarAction);
            if (btnAction != null) {
                if (actionText != null && actionListener != null) {
                    btnAction.setText(actionText);
                    btnAction.setVisibility(View.VISIBLE);
                    btnAction.setOnClickListener(v -> {
                        actionListener.onClick(v);
                        snackbar.dismiss();
                    });
                } else {
                    btnAction.setVisibility(View.GONE);
                }
            }

            // Explicitly set symmetric margins and width on customView so it floats centered
            int marginH = (int) (16 * activity.getResources().getDisplayMetrics().density);
            int marginB = (int) (12 * activity.getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams customLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            customLp.setMargins(marginH, 0, marginH, marginB);
            customLp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            customView.setLayoutParams(customLp);

            layout.addView(customView, 0);

            // Calculate bottom margin offset if bottom navigation is visible
            View bottomNav = activity.findViewById(R.id.bottom_navigation);
            final int navBarOffset;
            if (bottomNav != null && bottomNav.getVisibility() == View.VISIBLE) {
                int h = bottomNav.getHeight();
                navBarOffset = h > 0 ? h : (int) (64 * activity.getResources().getDisplayMetrics().density);
            } else {
                navBarOffset = 0;
            }

            // Strictly enforce full width, zero lateral margins on the container, and bottom gravity
            ViewGroup.LayoutParams lp = layout.getLayoutParams();
            FrameLayout.LayoutParams flp;
            if (lp instanceof FrameLayout.LayoutParams) {
                flp = (FrameLayout.LayoutParams) lp;
            } else {
                flp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }
            flp.width = FrameLayout.LayoutParams.MATCH_PARENT;
            flp.leftMargin = 0;
            flp.rightMargin = 0;
            flp.bottomMargin = navBarOffset;
            flp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            layout.setLayoutParams(flp);

            // Guard: Re-enforce full-width and centered layout if Material resets params
            layout.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                ViewGroup.LayoutParams curLp = v.getLayoutParams();
                if (curLp instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams curFlp = (FrameLayout.LayoutParams) curLp;
                    boolean needUpdate = false;
                    if (curFlp.gravity != (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)) {
                        curFlp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                        needUpdate = true;
                    }
                    if (curFlp.width != FrameLayout.LayoutParams.MATCH_PARENT) {
                        curFlp.width = FrameLayout.LayoutParams.MATCH_PARENT;
                        needUpdate = true;
                    }
                    if (curFlp.leftMargin != 0 || curFlp.rightMargin != 0) {
                        curFlp.leftMargin = 0;
                        curFlp.rightMargin = 0;
                        needUpdate = true;
                    }
                    if (curFlp.bottomMargin != navBarOffset) {
                        curFlp.bottomMargin = navBarOffset;
                        needUpdate = true;
                    }
                    if (needUpdate) {
                        v.setLayoutParams(curFlp);
                    }
                }
            });

            snackbar.show();
        });
    }
}
