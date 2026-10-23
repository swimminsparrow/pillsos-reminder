package swimminsparrow.ricordamelo.ui.rateme;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;

import swimminsparrow.ricordamelo.R;

public class RateMeDialogFragment extends DialogFragment {

    public static final String TAG = "RateMeDialogFragment";

    private ImageView[] starViews = new ImageView[5];
    private TextView tvRateTitle;
    private TextView tvRateSubtitle;
    private TextView tvRatingHint;
    private MaterialButton btnRateSubmit;
    private int selectedRating = 0;

    public static RateMeDialogFragment newInstance() {
        return new RateMeDialogFragment();
    }

    public static void show(@NonNull FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) == null && !fragmentManager.isStateSaved()) {
            newInstance().show(fragmentManager, TAG);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return inflater.inflate(R.layout.dialog_rate_me, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvRateTitle = view.findViewById(R.id.tvRateTitle);
        tvRateSubtitle = view.findViewById(R.id.tvRateSubtitle);
        tvRatingHint = view.findViewById(R.id.tvRatingHint);
        btnRateSubmit = view.findViewById(R.id.btnRateSubmit);

        starViews[0] = view.findViewById(R.id.ivStar1);
        starViews[1] = view.findViewById(R.id.ivStar2);
        starViews[2] = view.findViewById(R.id.ivStar3);
        starViews[3] = view.findViewById(R.id.ivStar4);
        starViews[4] = view.findViewById(R.id.ivStar5);

        for (int i = 0; i < 5; i++) {
            final int starIndex = i + 1;
            if (starViews[i] != null) {
                starViews[i].setOnClickListener(v -> setRating(starIndex, true));
            }
        }

        View btnClose = view.findViewById(R.id.btnCloseDialog);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                actionTaken = true;
                RateMeManager.snooze(requireContext());
                dismissAllowingStateLoss();
            });
        }

        MaterialButton btnRemindLater = view.findViewById(R.id.btnRemindLater);
        if (btnRemindLater != null) {
            btnRemindLater.setOnClickListener(v -> {
                actionTaken = true;
                RateMeManager.snooze(requireContext());
                dismissAllowingStateLoss();
            });
        }

        MaterialButton btnNeverAskAgain = view.findViewById(R.id.btnNeverAskAgain);
        if (btnNeverAskAgain != null) {
            btnNeverAskAgain.setOnClickListener(v -> {
                actionTaken = true;
                RateMeManager.dismissPermanently(requireContext());
                dismissAllowingStateLoss();
            });
        }

        if (btnRateSubmit != null) {
            btnRateSubmit.setOnClickListener(v -> {
                actionTaken = true;
                if (selectedRating >= 4) {
                    // Only 4 or 5 stars go to Google Play Store
                    RateMeManager.openPlayStore(requireContext());
                } else if (selectedRating > 0) {
                    // Reviews <= 3 stars NEVER go to Google Play; strictly sent to developer email
                    RateMeManager.sendFeedbackEmail(requireContext(), selectedRating);
                }
                RateMeManager.markAsRated(requireContext());
                dismissAllowingStateLoss();
            });
        }

        setRating(0, false);
    }

    private boolean actionTaken = false;

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (!actionTaken && getContext() != null) {
            RateMeManager.snooze(getContext());
        }
    }

    private void setRating(int rating, boolean animate) {
        selectedRating = Math.max(0, Math.min(5, rating));

        for (int i = 0; i < 5; i++) {
            if (starViews[i] != null) {
                if (i < selectedRating) {
                    starViews[i].setImageResource(R.drawable.ic_star_filled_24dp);
                } else {
                    starViews[i].setImageResource(R.drawable.ic_star_outline_24dp);
                }

                if (animate && i == selectedRating - 1) {
                    animateStar(starViews[i]);
                }
            }
        }

        if (btnRateSubmit == null) return;

        if (selectedRating == 0) {
            // Initial unselected state
            if (tvRateTitle != null) tvRateTitle.setText(R.string.rateme__dialog_first_title);
            if (tvRateSubtitle != null) tvRateSubtitle.setText(R.string.rateme__dialog_first_message);
            if (tvRatingHint != null) {
                tvRatingHint.setText(R.string.rateme_touch_stars);
                tvRatingHint.setTextColor(getResources().getColor(R.color.secondary_text));
            }
            btnRateSubmit.setEnabled(false);
            btnRateSubmit.setAlpha(0.45f);
            btnRateSubmit.setText(R.string.rateme_select_rating);
            btnRateSubmit.setIcon(null);
        } else if (selectedRating <= 3) {
            // Bad review / constructive feedback: strictly developer email
            if (tvRateTitle != null) tvRateTitle.setText(R.string.rateme_help_us_improve);
            if (tvRateSubtitle != null) tvRateSubtitle.setText(R.string.rateme__dialog_feedback_message);
            if (tvRatingHint != null) {
                tvRatingHint.setText(selectedRating == 3 ? R.string.rateme_feedback_prompt_suggestion : R.string.rateme_feedback_prompt_negative);
                tvRatingHint.setTextColor(getResources().getColor(R.color.secondary_text));
            }
            btnRateSubmit.setEnabled(true);
            btnRateSubmit.setAlpha(1.0f);
            btnRateSubmit.setText(R.string.rateme_send_email_button);
            btnRateSubmit.setIconResource(R.drawable.ic_send_plane_right);
        } else {
            // Good review (4 or 5 stars): prompt for Play Store
            if (tvRateTitle != null) tvRateTitle.setText(R.string.rateme_thanks_heart);
            if (tvRateSubtitle != null) tvRateSubtitle.setText(R.string.rateme__dialog_first_thanks);
            if (tvRatingHint != null) {
                tvRatingHint.setText(selectedRating == 5 ? R.string.rateme_play_store_prompt_5 : R.string.rateme_play_store_prompt_4);
                tvRatingHint.setTextColor(getResources().getColor(R.color.defaultColor));
            }
            btnRateSubmit.setEnabled(true);
            btnRateSubmit.setAlpha(1.0f);
            btnRateSubmit.setText(R.string.rateme__dialog_first_button_rate);
            btnRateSubmit.setIconResource(R.drawable.ic_star_filled_24dp);
        }
    }

    private void animateStar(View star) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(star, "scaleX", 1f, 1.35f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(star, "scaleY", 1f, 1.35f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(250);
        set.start();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
