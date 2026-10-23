package swimminsparrow.ricordamelo;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.concurrent.atomic.AtomicBoolean;

import swimminsparrow.ricordamelo.ui.common.IAdsManager;

public class AdsManager implements IAdsManager {

    private AdView adView;
    private final AtomicBoolean isMobileAdsInitializeCalled = new AtomicBoolean(false);

    @Override
    public void init(Activity activity, ViewGroup container) {
        if (activity == null || activity.isFinishing() || container == null) {
            return;
        }

        // Initially hide container until an ad is successfully loaded to avoid awkward blank spacing
        container.setVisibility(View.GONE);

        // UMP GDPR / EU Consent handling
        ConsentRequestParameters params = new ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build();

        ConsentInformation consentInformation = UserMessagingPlatform.getConsentInformation(activity);
        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                        activity,
                        formError -> {
                            if (consentInformation.canRequestAds()) {
                                initializeMobileAdsSdk(activity, container);
                            }
                        }
                ),
                requestConsentError -> {
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAdsSdk(activity, container);
                    }
                }
        );

        if (consentInformation.canRequestAds()) {
            initializeMobileAdsSdk(activity, container);
        }
    }

    private void initializeMobileAdsSdk(Activity activity, ViewGroup container) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed() || container == null) {
                return;
            }

            if (!isMobileAdsInitializeCalled.getAndSet(true)) {
                MobileAds.initialize(activity.getApplicationContext(), initializationStatus -> {});
            }

            loadBanner(activity, container);
        });
    }

    private void loadBanner(Activity activity, ViewGroup container) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || container == null) {
            return;
        }

        if (adView != null) {
            adView.destroy();
            container.removeAllViews();
        }

        adView = new AdView(activity);
        adView.setAdUnitId(activity.getString(R.string.bannerInferiore));

        AdSize adSize = getAdSize(activity, container);
        adView.setAdSize(adSize);

        adView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                container.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                container.setVisibility(View.GONE);
            }
        });

        container.removeAllViews();
        container.addView(adView);

        AdRequest request = new AdRequest.Builder().build();
        adView.loadAd(request);
    }

    private AdSize getAdSize(Activity activity, ViewGroup container) {
        DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
        int containerWidth = container.getWidth();
        float density = displayMetrics.density;
        int adWidth;
        if (containerWidth > 0) {
            adWidth = (int) (containerWidth / density);
        } else {
            adWidth = (int) (displayMetrics.widthPixels / density);
        }
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth);
    }

    @Override
    public void onResume() {
        if (adView != null) {
            adView.resume();
        }
    }

    @Override
    public void onPause() {
        if (adView != null) {
            adView.pause();
        }
    }

    @Override
    public void onDestroy() {
        if (adView != null) {
            adView.destroy();
            adView = null;
        }
    }
}