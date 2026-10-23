package swimminsparrow.ricordamelo.ui.alarms;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import swimminsparrow.ricordamelo.AdsManager;
import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.ui.BaseActivity;
import swimminsparrow.ricordamelo.ui.common.IAdsManager;
import swimminsparrow.ricordamelo.ui.onboarding.WhatsNewBottomSheetDialogFragment;
import swimminsparrow.ricordamelo.ui.splash.SplashScreen;

public class MainActivity extends BaseActivity {

    private Toolbar toolbar;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;
    private IAdsManager adsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            int defaultGreen = androidx.core.content.ContextCompat.getColor(this, R.color.defaultColor);
            getWindow().setStatusBarColor(defaultGreen);
            getWindow().setNavigationBarColor(defaultGreen);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getWindow().setNavigationBarContrastEnforced(false);
                getWindow().setStatusBarContrastEnforced(false);
            }
        }

        setupNavigator();
        setupToolbar();
        setupBottomNavigation();
        initAdsProvider();

        boolean isAlarmLaunch = getIntent() != null &&
                (getIntent().hasExtra("notification_id") || getIntent().getBooleanExtra("restore_volume", false)
                        || getIntent().getBooleanExtra("skip_splash", false)
                        || getIntent().getBooleanExtra("open_sos", false)
                        || getIntent().hasExtra(swimminsparrow.ricordamelo.core.sos.ConnectionRequest.EXTRA_REQUEST_ID));

        // Show splash on first launch only (unless launched from full-screen alarm
        // notification)
        if (savedInstanceState == null) {
            checkNotificationIntent(getIntent());

            if (getIntent().getBooleanExtra("openNewAlarm", false)) {
                navController.navigate(R.id.nav_nuovo_ricordamelo);
            } else if (!isAlarmLaunch) {
                startActivity(new Intent(this, SplashScreen.class));
            }
        } else {
            checkNotificationIntent(getIntent());
        }
    }

    // Also handle intent if app is already running
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkNotificationIntent(intent);
    }

    private void checkNotificationIntent(Intent intent) {
        if (intent == null)
            return;

        if (intent.hasExtra(swimminsparrow.ricordamelo.core.sos.ConnectionRequest.EXTRA_REQUEST_ID)) {
            swimminsparrow.ricordamelo.core.sos.ConnectionRequest req =
                    swimminsparrow.ricordamelo.core.sos.ConnectionRequest.fromIntent(intent);
            if (req != null) {
                if (req.isExpired()) {
                    swimminsparrow.ricordamelo.core.sos.SOSContactsManager.markRequestHandled(this, req.topic, req.timestamp);
                    swimminsparrow.ricordamelo.core.sos.ConnectionRequestManager.cancelConnectionNotification(this);
                    android.widget.Toast.makeText(this, R.string.sos_request_expired, android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    swimminsparrow.ricordamelo.core.sos.ConnectionRequestManager.showApprovalDialog(this, req);
                }
            }
            return;
        }

        if (intent.getBooleanExtra("restore_volume", false) || intent.hasExtra("notification_id")) {
            int notifId = intent.getIntExtra("notification_id", -1);
            if (notifId != -1) {
                showAlarmDialog(notifId);
            } else {
                swimminsparrow.ricordamelo.core.utils.VolumeUtils.restoreVolume(this);
            }
        }

        if (intent.getBooleanExtra("open_sos", false)) {
            String topic = intent.getStringExtra("topic");
            String username = intent.getStringExtra("username");
            boolean isSosAlert = intent.getBooleanExtra("is_sos_alert", false);

            if (isSosAlert || (swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().isRinging() &&
                    swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().getActiveAlarmId() ==
                            swimminsparrow.ricordamelo.core.notification.AppNotificationManager.ID_SOS_ALERT)) {
                showSosAlarmDialog(username);
            }
            if (topic != null && !topic.isEmpty()) {
                swimminsparrow.ricordamelo.core.sos.SOSContact contact =
                        swimminsparrow.ricordamelo.core.sos.SOSContactsManager.getContactByTopic(this, topic);
                String name = contact != null ? contact.getName() : (username != null ? username : "SOS");
                String avatar = contact != null ? contact.getAvatarBase64() : "";
                android.os.Bundle args = new android.os.Bundle();
                args.putString(swimminsparrow.ricordamelo.ui.sos.SOSMessageHistoryFragment.ARG_TOPIC, topic);
                args.putString(swimminsparrow.ricordamelo.ui.sos.SOSMessageHistoryFragment.ARG_NAME, name);
                args.putString(swimminsparrow.ricordamelo.ui.sos.SOSMessageHistoryFragment.ARG_AVATAR, avatar);
                if (navController != null) {
                    if (navController.getCurrentDestination() != null
                            && navController.getCurrentDestination().getId() == R.id.nav_sos_history) {
                        navController.popBackStack(R.id.nav_sos_history, true);
                    }
                    navController.navigate(R.id.nav_sos_history, args);
                }
            } else if (navController != null) {
                if (navController.getCurrentDestination() != null
                        && navController.getCurrentDestination().getId() != R.id.nav_sos) {
                    navController.navigate(R.id.nav_sos);
                }
            }
        }
    }

    private void showAlarmDialog(int id) {
        swimminsparrow.ricordamelo.core.data.models.Farmaco farmaco = swimminsparrow.ricordamelo.core.data.dao.FarmacoDao
                .getInstance(this).getById(id);
        String name = (farmaco != null && farmaco.getNome() != null && !farmaco.getNome().isEmpty())
                ? farmaco.getNome()
                : (id == 9999 ? "Test Allarme" : getString(R.string.app_name));

        new swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder(this, swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder.Type.CUSTOM_IMAGE)
                .setTitleText(name)
                .setContentText(getString(R.string.alarm_dismiss) + " / " + getString(R.string.snooze))
                .setCustomImage(R.drawable.icon)
                .setConfirmText(getString(R.string.alarm_dismiss))
                .setConfirmClickListener(sDialog -> {
                    swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().stop(this);
                    androidx.core.app.NotificationManagerCompat.from(this).cancel(id);
                    swimminsparrow.ricordamelo.core.utils.VolumeUtils.restoreVolume(this);
                    try {
                        Intent missedIntent = new Intent(this, swimminsparrow.ricordamelo.services.MissedAlarmReceiver.class);
                        android.app.PendingIntent missedPending = swimminsparrow.ricordamelo.core.utils.PendingIntentHelper
                                .getBroadcast(this, id, missedIntent, true);
                        android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        if (am != null) {
                            am.cancel(missedPending);
                        }
                    } catch (Exception ignored) {}
                })
                .setCancelText(getString(R.string.snooze))
                .setCancelClickListener(sDialog -> {
                    swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().stop(this);
                    androidx.core.app.NotificationManagerCompat.from(this).cancel(id);
                    swimminsparrow.ricordamelo.core.utils.VolumeUtils.restoreVolume(this);
                    try {
                        Intent missedIntent = new Intent(this, swimminsparrow.ricordamelo.services.MissedAlarmReceiver.class);
                        android.app.PendingIntent missedPending = swimminsparrow.ricordamelo.core.utils.PendingIntentHelper
                                .getBroadcast(this, id, missedIntent, true);
                        android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        if (am != null) {
                            am.cancel(missedPending);
                        }
                    } catch (Exception ignored) {}
                    Intent snoozeIntent = new Intent(this, swimminsparrow.ricordamelo.services.SnoozeReceiver.class);
                    snoozeIntent.putExtra("ID", id);
                    sendBroadcast(snoozeIntent);
                })
                .show();
    }

    private void showSosAlarmDialog(String sender) {
        String title = (sender != null && !sender.trim().isEmpty()) ? sender.trim() : getString(R.string.sos_detected_title);
        new swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder(this, swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder.Type.CUSTOM_IMAGE)
                .setTitleText(title)
                .setContentText(getString(R.string.sos_detected_title))
                .setCustomImage(R.mipmap.ic_launcher_round)
                .setConfirmText(getString(R.string.alarm_dismiss))
                .setConfirmClickListener(sDialog -> {
                    swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().stop(this);
                    androidx.core.app.NotificationManagerCompat.from(this)
                            .cancel(swimminsparrow.ricordamelo.core.notification.AppNotificationManager.ID_SOS_ALERT);
                    swimminsparrow.ricordamelo.core.utils.VolumeUtils.restoreVolume(this);
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adsManager != null) {
            adsManager.onResume();
        }
        swimminsparrow.ricordamelo.core.sos.SOSManager.startServices(this);
        checkWhatsNew();
    }

    @Override
    protected void onPause() {
        if (adsManager != null) {
            adsManager.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (adsManager != null) {
            adsManager.onDestroy();
        }
        super.onDestroy();
    }

    private void checkWhatsNew() {
        if (SettingsDB.getInstance(this).getPrimoAvvio() && !SettingsDB.getInstance(this).getWhatsNew(2)) {
            if (getSupportFragmentManager().findFragmentByTag("WhatsNewBottomSheet") == null) {
                WhatsNewBottomSheetDialogFragment dialog = WhatsNewBottomSheetDialogFragment.newInstance();
                dialog.show(getSupportFragmentManager(), "WhatsNewBottomSheet");
            }
        }
    }

    private void setupNavigator() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();
    }

    // ─── Toolbar ────────────────────────────────────────────────────────────
    @Override
    public boolean onSupportNavigateUp() {
        if (getOnBackPressedDispatcher().hasEnabledCallbacks()) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (navController != null && navController.getCurrentDestination() != null) {
            int destId = navController.getCurrentDestination().getId();
            boolean isTopLevel = (destId == R.id.nav_ricordamelo || destId == R.id.nav_sos);
            MenuItem settings = menu.findItem(R.id.action_settings);
            MenuItem coffee = menu.findItem(R.id.action_coffee);
            MenuItem help = menu.findItem(R.id.action_help);
            if (settings != null) settings.setVisible(isTopLevel);
            if (coffee != null) coffee.setVisible(isTopLevel);
            if (help != null) help.setVisible(isTopLevel);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if (item.getItemId() == R.id.action_settings) {
            navController.navigate(R.id.nav_settings);
            return true;
        }

        if (item.getItemId() == R.id.action_coffee) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(swimminsparrow.ricordamelo.config.Constants.DONATE_URL));
                startActivity(intent);
            } catch (Exception e) {
                android.widget.Toast
                        .makeText(this, getString(R.string.cannot_open_browser), android.widget.Toast.LENGTH_SHORT)
                        .show();
            }
            return true;
        }

        if (item.getItemId() == R.id.action_help) {
            navController.navigate(R.id.nav_help);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_ricordamelo,
                R.id.nav_sos).build();

        NavigationUI.setupWithNavController(
                toolbar,
                navController,
                appBarConfiguration);

        // NavigationUI overrides the toolbar nav-click listener with a direct call to
        // navigateUp(), which bypasses onSupportNavigateUp() and any registered
        // OnBackPressedCallbacks (e.g. the unsaved-changes guard in NewAlarmFragment).
        // Re-setting it here restores the correct dispatch order.
        toolbar.setNavigationOnClickListener(v -> onSupportNavigateUp());

    }

    // ─── Bottom Navigation ───────────────────────────────────────────────────

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        // Top-level destinations — no back arrow shown on these
        NavigationUI.setupWithNavController(bottomNav, navController);

        // Safeguard against internal NavigationUI NPE (NavigationUI.kt:73) and handle multi-backstack desync
        bottomNav.setOnItemSelectedListener(item -> {
            if (navController.getCurrentDestination() == null || navController.getCurrentDestination().getParent() == null) {
                return false;
            }
            int itemId = item.getItemId();
            if (navController.getCurrentDestination().getId() == itemId) {
                return true;
            }
            try {
                boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
                if (!handled || (navController.getCurrentDestination() != null && navController.getCurrentDestination().getId() != itemId)) {
                    NavOptions navOptions = new NavOptions.Builder()
                            .setLaunchSingleTop(true)
                            .setPopUpTo(R.id.nav_ricordamelo, false)
                            .build();
                    navController.navigate(itemId, null, navOptions);
                }
                return true;
            } catch (Exception e) {
                android.util.Log.w("MainActivity", "Failed to navigate safely to item: " + itemId, e);
                try {
                    navController.navigate(itemId);
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        });

        bottomNav.setOnItemReselectedListener(item -> {
            int itemId = item.getItemId();
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() != itemId) {
                if (!navController.popBackStack(itemId, false)) {
                    navController.navigate(itemId);
                }
            }
        });

        // Hide bottom bar on detail screens
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            boolean isTopLevel = destination.getId() == R.id.nav_ricordamelo ||
                    destination.getId() == R.id.nav_sos;
            bottomNav.setVisibility(isTopLevel ? View.VISIBLE : View.GONE);
            invalidateOptionsMenu();
        });
    }

    // ─── Ads ─────────────────────────────────────────────────────────────────

    private void initAdsProvider() {
        ViewGroup container = findViewById(R.id.ad_container);
        adsManager = new AdsManager();
        adsManager.init(this, container);
    }

}