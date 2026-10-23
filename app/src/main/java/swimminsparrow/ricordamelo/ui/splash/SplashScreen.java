package swimminsparrow.ricordamelo.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.sos.SOSManager;
import swimminsparrow.ricordamelo.core.utils.CheckPermissionsHandler;
import swimminsparrow.ricordamelo.core.utils.PermissionCallback;
import swimminsparrow.ricordamelo.core.utils.StorageUtils;
import swimminsparrow.ricordamelo.ui.BaseActivity;
import swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder;
import swimminsparrow.ricordamelo.ui.onboarding.OnboardingActivity;

public class SplashScreen extends BaseActivity {

    private boolean isTransitioning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        View iconCard = findViewById(R.id.splash_icon_card);
        View title = findViewById(R.id.splash_title);
        View subtitle = findViewById(R.id.splash_subtitle);

        // Subtle Material entrance animation (scale + fade-in)
        if (iconCard != null) {
            iconCard.setScaleX(0.85f);
            iconCard.setScaleY(0.85f);
            iconCard.setAlpha(0f);
            iconCard.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(450)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        if (title != null) {
            title.setAlpha(0f);
            title.setTranslationY(20f);
            title.animate()
                    .alpha(1.0f)
                    .translationY(0f)
                    .setDuration(450)
                    .setStartDelay(100)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        if (subtitle != null) {
            subtitle.setAlpha(0f);
            subtitle.setTranslationY(20f);
            subtitle.animate()
                    .alpha(0.9f)
                    .translationY(0f)
                    .setDuration(450)
                    .setStartDelay(150)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(this::proceedAfterAnimation)
                    .start();
        } else {
            proceedAfterAnimation();
        }
    }

    private void proceedAfterAnimation() {
        if (isFinishing() || isDestroyed() || isTransitioning) return;
        startPillSOS();
    }

    private void startPillSOS() {
        if (isTransitioning) return;
        isTransitioning = true;

        StorageUtils.isProjectDirsExisting(SplashScreen.this);
        SettingsDB.getInstance(getApplicationContext())
                .setAppLaunchTime(SettingsDB.getInstance(getApplicationContext()).getAppLaunchTime() + 1);

        if (!SettingsDB.getInstance(getApplicationContext()).getPrimoAvvio()) {
            Intent intent = new Intent(SplashScreen.this, OnboardingActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            overridePendingTransition(R.anim.unzoom_in, R.anim.unzoom_out);
            finish();
        }
    }
}
