package swimminsparrow.ricordamelo;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder;
import swimminsparrow.ricordamelo.ui.common.IProFeatureManager;

/**
 * Nonfree / Google Play implementation:
 * Manages RevenueCat Billing, dynamic Pro Toolbar button (Diamond),
 * dynamic Pro Banner card in Home, and Pro Preferences in Settings.
 */
public class ProFeatureManager implements IProFeatureManager {

    private static final String TAG = "ProFeatureManager";
    private static ProFeatureManager instance;
    private final List<Runnable> bannerUpdateListeners = new ArrayList<>();
    private BillingManager.StateListener billingListener;

    public static synchronized ProFeatureManager getInstance() {
        if (instance == null) {
            instance = new ProFeatureManager();
        }
        return instance;
    }

    public ProFeatureManager() {
        billingListener = isPro -> {
            notifyBannerListeners();
        };
        BillingManager.getInstance().addStateListener(billingListener);
    }

    @Override
    public void init(Activity activity) {
        if (activity != null) {
            BillingManager.getInstance().init(activity);
        }
    }

    @Override
    public void setupToolbarMenu(Activity activity, Menu menu, boolean isTopLevel) {
        if (menu == null || activity == null) return;

        // Hide external donation coffee link to strictly follow Google Play Payments Policy
        MenuItem coffeeItem = menu.findItem(R.id.action_coffee);
        if (coffeeItem != null) {
            coffeeItem.setVisible(false);
        }

        boolean isPro = BillingManager.getInstance().isPro();
        MenuItem proItem = menu.findItem(R.id.action_pro);

        if (isTopLevel && !isPro) {
            if (proItem == null) {
                proItem = menu.add(Menu.NONE, R.id.action_pro, 1, R.string.pro_upgrade_title);
                proItem.setIcon(R.drawable.ic_diamond_24dp);
                proItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            }
            proItem.setVisible(true);
        } else if (proItem != null) {
            proItem.setVisible(false);
        }
    }

    @Override
    public boolean onToolbarItemSelected(Activity activity, MenuItem item) {
        if (activity == null || item == null) return false;

        if (item.getItemId() == R.id.action_pro) {
            BillingManager.getInstance().purchasePro(activity, new BillingManager.BillingCallback() {
                @Override
                public void onSuccess(String message) {
                    showProSuccessDialog(activity, activity.getString(R.string.billing_purchase_success), activity.getString(R.string.pro_unlocked_summary));
                    activity.invalidateOptionsMenu();
                    notifyBannerListeners();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onCanceled() {}
            });
            return true;
        }
        return false;
    }

    @Override
    public void updateHomeBanner(Activity activity, ViewGroup bannerContainer, boolean hasWarningAbove) {
        if (bannerContainer == null || activity == null) return;

        boolean isPro = BillingManager.getInstance().isPro();
        if (isPro || hasWarningAbove) {
            bannerContainer.setVisibility(View.GONE);
            return;
        }

        if (bannerContainer.getChildCount() == 0) {
            View card = LayoutInflater.from(activity).inflate(R.layout.card_pro_banner, bannerContainer, false);
            bannerContainer.addView(card);

            View.OnClickListener clickListener = v -> {
                BillingManager.getInstance().purchasePro(activity, new BillingManager.BillingCallback() {
                    @Override
                    public void onSuccess(String message) {
                        showProSuccessDialog(activity, activity.getString(R.string.billing_purchase_success), activity.getString(R.string.pro_unlocked_summary));
                        bannerContainer.setVisibility(View.GONE);
                        activity.invalidateOptionsMenu();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCanceled() {}
                });
            };

            card.setOnClickListener(clickListener);
            View btnAction = card.findViewById(R.id.btn_unlock_pro);
            if (btnAction != null) {
                btnAction.setOnClickListener(clickListener);
            }
        }

        TextView tvDesc = bannerContainer.findViewById(R.id.tv_pro_banner_desc);
        if (tvDesc != null) {
            String price = BillingManager.getInstance().getFormattedPrice();
            if (price == null || price.trim().isEmpty()) {
                price = BuildConfig.DEFAULT_PRO_PRICE;
            }
            tvDesc.setText(activity.getString(R.string.pro_banner_desc, price));
        }

        bannerContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void setupSettingsPreferences(PreferenceFragmentCompat fragment) {
        if (fragment == null || fragment.getContext() == null) return;
        Context context = fragment.requireContext();

        // Hide external donation coffee link
        Preference coffeePref = fragment.findPreference("d_coffee");
        if (coffeePref != null) {
            coffeePref.setVisible(false);
        }

        PreferenceScreen screen = fragment.getPreferenceScreen();
        if (screen == null) return;

        PreferenceCategory proCat = fragment.findPreference("set_cat_pro");
        if (proCat == null) {
            proCat = new PreferenceCategory(context);
            proCat.setKey("set_cat_pro");
            proCat.setTitle("PillSOS Pro");
            proCat.setOrder(50);
            screen.addPreference(proCat);
        }

        Preference proPref = fragment.findPreference("d_pro_upgrade");
        if (proPref == null) {
            proPref = new Preference(context);
            proPref.setKey("d_pro_upgrade");
            proPref.setOrder(1);
            proCat.addPreference(proPref);
        }

        Preference restorePref = fragment.findPreference("d_restore_purchases");
        if (restorePref == null) {
            restorePref = new Preference(context);
            restorePref.setKey("d_restore_purchases");
            restorePref.setTitle(R.string.restore_purchases_title);
            restorePref.setSummary(R.string.restore_purchases_summary);
            restorePref.setIcon(R.drawable.baseline_history_24);
            restorePref.setOrder(2);
            proCat.addPreference(restorePref);
        }

        tintPreferenceIcon(context, restorePref);
        updateProPreferenceState(context, proPref, restorePref);

        final Preference finalProPref = proPref;
        final Preference finalRestorePref = restorePref;

        BillingManager.StateListener prefListener = isPro -> {
            if (fragment.isAdded() && fragment.getContext() != null) {
                updateProPreferenceState(fragment.requireContext(), finalProPref, finalRestorePref);
            }
        };
        BillingManager.getInstance().addStateListener(prefListener);
        fragment.getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event == Lifecycle.Event.ON_DESTROY) {
                BillingManager.getInstance().removeStateListener(prefListener);
            }
        });

        proPref.setOnPreferenceClickListener(pref -> {
            if (BillingManager.getInstance().isPro()) {
                if (fragment.getActivity() != null) {
                    showProSuccessDialog(fragment.getActivity(), fragment.getString(R.string.pro_upgrade_title), fragment.getString(R.string.pro_unlocked_summary));
                }
                return true;
            }
            if (fragment.getActivity() != null) {
                BillingManager.getInstance().purchasePro(fragment.getActivity(), new BillingManager.BillingCallback() {
                    @Override
                    public void onSuccess(String message) {
                        showProSuccessDialog(fragment.getActivity(), fragment.getString(R.string.billing_purchase_success), fragment.getString(R.string.pro_unlocked_summary));
                        updateProPreferenceState(context, finalProPref, finalRestorePref);
                        notifyBannerListeners();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCanceled() {}
                });
            }
            return true;
        });

        restorePref.setOnPreferenceClickListener(pref -> {
            if (fragment.getActivity() != null) {
                BillingManager.getInstance().restorePurchases(fragment.getActivity(), new BillingManager.BillingCallback() {
                    @Override
                    public void onSuccess(String message) {
                        showProSuccessDialog(fragment.getActivity(), fragment.getString(R.string.billing_restore_success), fragment.getString(R.string.pro_unlocked_summary));
                        updateProPreferenceState(context, finalProPref, finalRestorePref);
                        notifyBannerListeners();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCanceled() {}
                });
            }
            return true;
        });
    }

    private void updateProPreferenceState(Context context, Preference proPref, Preference restorePref) {
        if (proPref == null) return;
        boolean isPro = BillingManager.getInstance().isPro();

        if (isPro) {
            proPref.setTitle(R.string.pro_upgrade_title);
            proPref.setSummary(R.string.pro_unlocked_summary);
            proPref.setIcon(R.drawable.ic_diamond_24dp);
            tintPreferenceIcon(context, proPref);
            if (restorePref != null) {
                restorePref.setVisible(false);
            }
        } else {
            proPref.setTitle(R.string.pro_upgrade_title);
            String price = BillingManager.getInstance().getFormattedPrice();
            if (price == null || price.trim().isEmpty()) {
                price = BuildConfig.DEFAULT_PRO_PRICE;
            }
            proPref.setSummary(context.getString(R.string.pro_upgrade_summary, price));
            proPref.setIcon(R.drawable.ic_diamond_24dp);
            tintPreferenceIcon(context, proPref);
            if (restorePref != null) {
                restorePref.setVisible(true);
            }
        }
    }

    private void tintPreferenceIcon(Context context, Preference preference) {
        if (preference == null || context == null) return;
        Drawable icon = preference.getIcon();
        if (icon != null) {
            int primaryColor = ContextCompat.getColor(context, R.color.defaultColor);
            Drawable tinted = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTint(tinted, primaryColor);
            preference.setIcon(tinted);
        }
    }

    @Override
    public synchronized void addBannerUpdateListener(Runnable listener) {
        if (listener != null && !bannerUpdateListeners.contains(listener)) {
            bannerUpdateListeners.add(listener);
        }
    }

    @Override
    public synchronized void removeBannerUpdateListener(Runnable listener) {
        if (listener != null) {
            bannerUpdateListeners.remove(listener);
        }
    }

    private synchronized void notifyBannerListeners() {
        for (Runnable r : new ArrayList<>(bannerUpdateListeners)) {
            if (r != null) {
                r.run();
            }
        }
    }

    private void showProSuccessDialog(Activity activity, String title, String message) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        activity.runOnUiThread(() -> {
            try {
                DialogBuilder builder = new DialogBuilder(activity, DialogBuilder.Type.SUCCESS)
                        .setIcon(R.drawable.ic_diamond_24dp)
                        .setTitleText(title != null ? title : activity.getString(R.string.billing_purchase_success))
                        .setContentText(message != null ? message : activity.getString(R.string.pro_unlocked_summary))
                        .setConfirmText(activity.getString(android.R.string.ok));

                Dialog dialog = builder.create();

                FrameLayout iconContainer = dialog.findViewById(R.id.dialogIconContainer);
                if (iconContainer != null) {
                    iconContainer.setBackgroundResource(R.drawable.bg_badge_pro);
                }

                ImageView ivIcon = dialog.findViewById(R.id.ivDialogIcon);
                if (ivIcon != null) {
                    ivIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#0284C7")));
                }

                MaterialButton btnPositive = dialog.findViewById(R.id.btnDialogPositive);
                if (btnPositive != null) {
                    btnPositive.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0284C7")));
                }

                builder.show();
            } catch (Exception e) {
                Log.e(TAG, "Error displaying Pro confirmation dialog", e);
            }
        });
    }

    @Override
    public void onDestroy() {
        if (billingListener != null) {
            BillingManager.getInstance().removeStateListener(billingListener);
            billingListener = null;
        }
        bannerUpdateListeners.clear();
    }
}
