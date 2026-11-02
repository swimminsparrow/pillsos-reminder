package swimminsparrow.ricordamelo.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder;
import swimminsparrow.ricordamelo.BuildConfig;
import swimminsparrow.ricordamelo.core.utils.BatteryOptimizationsHelper;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.config.Constants;

public class SettingsPreferenceFragment extends PreferenceFragmentCompat {

    private String chosenRingtone;
    private ActivityResultLauncher<Intent> ringtonePickerLauncher;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);

        setupLanguage();
        setupPersonalName();
        setupSilentVibration();
        setupAlarmDuration();
        setupRingtone();
        setupAboutAndCredits();
        setupHealthCheck();
        setupTestAlarm();
        setupCoffee();
        setupRateApp();

        tintPreferenceIcons(getPreferenceScreen());

        // Inside onCreatePreferences or onViewCreated
        ringtonePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                            Preference gen_8 = findPreference("d_8");
                            if (uri != null) {
                                chosenRingtone = uri.toString();
                            } else {
                                chosenRingtone = "Default";
                            }
                            SettingsDB.getInstance(getContext()).setSound(chosenRingtone);
                            if (gen_8 != null) {
                                updateSoundSummary(gen_8);
                            }
                        }
                    }
                });
    }

    private void setupLanguage() {
        ListPreference gen_lang = findPreference("d_app_language");
        if (gen_lang != null) {
            LocaleListCompat currentLocales = AppCompatDelegate.getApplicationLocales();
            String currentLang = "DEFAULT";
            if (!currentLocales.isEmpty() && currentLocales.get(0) != null) {
                String tag = currentLocales.get(0).getLanguage();
                String[] values = getResources().getStringArray(R.array.appLanguageValues);
                for (String val : values) {
                    if (val.equalsIgnoreCase(tag)) {
                        currentLang = val;
                        break;
                    }
                }
            }
            gen_lang.setValue(currentLang);

            gen_lang.setOnPreferenceChangeListener((preference, newValue) -> {
                String selectedLang = (String) newValue;
                if ("DEFAULT".equalsIgnoreCase(selectedLang) || selectedLang.isEmpty()) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
                } else {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selectedLang));
                }
                return true;
            });
        }
    }

    private void setupPersonalName() {
        EditTextPreference gen_2 = findPreference("d_2");
        if (gen_2 != null) {
            String name = SettingsDB.getInstance(getContext()).getPersonalName();
            gen_2.setText(name);
            gen_2.setSummary(name);
            gen_2.setOnPreferenceChangeListener((preference, newValue) -> {
                String trimmedName = newValue != null ? newValue.toString().trim() : "";
                SettingsDB.getInstance(getContext()).setPersonalName(trimmedName);
                preference.setSummary(SettingsDB.getInstance(getContext()).getPersonalName());
                // Notify all connected SOS contacts of the name change
                swimminsparrow.ricordamelo.core.ntfy.NtfyManager
                        .sendNameUpdateToAllContacts(getContext(), trimmedName);
                DialogBuilder.successDialog(getContext(), "Perfect", "Name updated!");
                return true;
            });
        }
    }

    private void setupSilentVibration() {
        ListPreference soundModePref = findPreference("d_alarm_sound_mode");
        SwitchPreference gen_tts = findPreference("d_tts");
        SwitchPreference gen_fall_detection = findPreference("d_fall_detection");

        if (soundModePref != null) {
            String currentMode = SettingsDB.getInstance(getContext()).getAlarmSoundMode();
            soundModePref.setValue(currentMode);
            soundModePref.setOnPreferenceChangeListener((preference, newValue) -> {
                SettingsDB.getInstance(getContext()).setAlarmSoundMode((String) newValue);
                return true;
            });
        }

        if (gen_tts != null) {
            boolean engineInstalled = swimminsparrow.ricordamelo.core.utils.TTSManager.isTTSEngineInstalled(getContext());
            boolean ttsEnabled = SettingsDB.getInstance(getContext()).getTTSMode() && engineInstalled;
            gen_tts.setChecked(ttsEnabled);
            if (!engineInstalled) {
                gen_tts.setSummary(R.string.tts_summary_not_installed);
            } else {
                gen_tts.setSummary(R.string.tts_summary);
            }

            gen_tts.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean requested = (Boolean) newValue;
                if (requested && !swimminsparrow.ricordamelo.core.utils.TTSManager.isTTSEngineInstalled(getContext())) {
                    showTtsMissingDialog();
                    return false;
                }
                SettingsDB.getInstance(getContext()).setTTSMode(requested);
                if (requested) {
                    swimminsparrow.ricordamelo.PillSOS.getTtsManager(getContext());
                }
                return true;
            });
        }

        if (gen_fall_detection != null)
            gen_fall_detection.setChecked(SettingsDB.getInstance(getContext()).getFallDetectionMode());

        if (gen_fall_detection != null)
            gen_fall_detection.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                SettingsDB.getInstance(getContext()).setFallDetectionMode(enabled);
                swimminsparrow.ricordamelo.services.ProtectionService.sync(getContext());
                if (enabled && getContext() != null) {
                    if (!swimminsparrow.ricordamelo.core.utils.OverlayPermissionHelper.canDrawOverlays(getContext())) {
                        new DialogBuilder(getContext(), DialogBuilder.Type.WARNING)
                                .setTitleText(getString(R.string.overlay_permission_dialog_title))
                                .setContentText(getString(R.string.overlay_permission_dialog_message))
                                .setConfirmText(getString(R.string.overlay_permission_enable_btn))
                                .setCancelText(getString(R.string.dialog_cancel))
                                .setConfirmClickListener(d -> swimminsparrow.ricordamelo.core.utils.OverlayPermissionHelper.requestOverlayPermission(getContext()))
                                .show();
                    } else if (!swimminsparrow.ricordamelo.core.sos.SOSContactsManager.hasConfiguredContacts(getContext())) {
                        new DialogBuilder(getContext(), DialogBuilder.Type.INFO)
                                .setTitleText(getString(R.string.fall_detection_no_contacts_dialog_title))
                                .setContentText(getString(R.string.fall_detection_no_contacts_dialog_message))
                                .show();
                    }
                }
                return true;
            });

        SeekBarPreference fallSensitivityPref = findPreference("d_fall_sensitivity_threshold");
        if (fallSensitivityPref != null) {
            fallSensitivityPref.setUpdatesContinuously(true);
            int currentVal = SettingsDB.getInstance(getContext()).getFallDetectionSensitivity();
            fallSensitivityPref.setValue(currentVal);
            updateFallSensitivitySummary(fallSensitivityPref, currentVal);

            fallSensitivityPref.setOnPreferenceChangeListener((preference, newValue) -> {
                int val = (Integer) newValue;
                SettingsDB.getInstance(getContext()).setFallDetectionSensitivity(val);
                updateFallSensitivitySummary(preference, val);
                return true;
            });
        }
    }

    private void updateFallSensitivitySummary(Preference preference, int val) {
        if (preference == null || getContext() == null) return;
        double gValue = Math.max(2.5, Math.min(8.5, 8.5 - (val * 0.05)));
        String level;
        if (val >= 75) {
            level = getString(R.string.fall_sensitivity_high);
        } else if (val >= 45) {
            level = getString(R.string.fall_sensitivity_medium);
        } else {
            level = getString(R.string.fall_sensitivity_low);
        }
        preference.setSummary(getString(R.string.fall_sensitivity_summary_format, level, String.format(java.util.Locale.getDefault(), "%.1f", gValue)));
    }

    private void setupAlarmDuration() {
        Preference gen_12 = findPreference("d_12");
        if (gen_12 != null) {
            String localizedMin = getString(R.string.min);
            long initialMin = SettingsDB.getInstance(getContext()).getAlarmDuration() / 60000;
            if (initialMin < 1 || initialMin > 20) {
                initialMin = 10;
            }
            gen_12.setSummary(initialMin + " " + localizedMin);

            gen_12.setOnPreferenceClickListener(preference -> {
                Context context = getContext();
                if (context == null) return false;

                long currentMin = SettingsDB.getInstance(context).getAlarmDuration() / 60000;
                if (currentMin < 1 || currentMin > 20) {
                    currentMin = 10;
                }

                android.widget.NumberPicker numberPicker = new android.widget.NumberPicker(context);
                numberPicker.setMinValue(1);
                numberPicker.setMaxValue(20);
                numberPicker.setValue((int) currentMin);
                numberPicker.setWrapSelectorWheel(false);

                // Horizontal layout: [ NumberPicker ] [ "minuti" ]
                android.widget.LinearLayout pickerRow = new android.widget.LinearLayout(context);
                pickerRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                pickerRow.setGravity(android.view.Gravity.CENTER);
                pickerRow.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                pickerRow.addView(numberPicker);

                android.widget.TextView tvUnit = new android.widget.TextView(context);
                tvUnit.setText(getString(R.string.minutes_unit));
                tvUnit.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17);
                tvUnit.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.defaultColor));
                tvUnit.setTypeface(null, android.graphics.Typeface.BOLD);
                android.widget.LinearLayout.LayoutParams tvParams = new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                );
                tvParams.setMarginStart(32);
                tvUnit.setLayoutParams(tvParams);
                pickerRow.addView(tvUnit);

                android.widget.FrameLayout container = new android.widget.FrameLayout(context);
                container.addView(pickerRow);
                container.setPadding(32, 24, 32, 8);

                new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.alarmDuration)
                        .setIcon(R.drawable.baseline_history_24)
                        .setMessage(R.string.alarm_duration_dialog_desc)
                        .setView(container)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            int selectedMin = numberPicker.getValue();
                            SettingsDB.getInstance(context).setAlarmDuration(selectedMin);
                            gen_12.setSummary(selectedMin + " " + localizedMin);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();

                return true;
            });
        }
    }

    private void showTtsMissingDialog() {
        Context context = getContext();
        if (context == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(R.string.tts_dialog_missing_title)
                .setIcon(R.drawable.ic_record_voice_over_24)
                .setMessage(R.string.tts_dialog_missing_desc)
                .setPositiveButton(R.string.tts_dialog_open_settings, (dialog, which) -> {
                    try {
                        Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e) {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        } catch (Exception ignored) {}
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private MediaPlayer previewMediaPlayer;
    private Ringtone previewRingtone;
    private final android.os.Handler previewHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private void stopPreview() {
        previewHandler.removeCallbacksAndMessages(null);
        if (previewMediaPlayer != null) {
            try {
                if (previewMediaPlayer.isPlaying()) {
                    previewMediaPlayer.stop();
                }
                previewMediaPlayer.release();
            } catch (Exception ignored) {}
            previewMediaPlayer = null;
        }
        if (previewRingtone != null) {
            try {
                if (previewRingtone.isPlaying()) {
                    previewRingtone.stop();
                }
            } catch (Exception ignored) {}
            previewRingtone = null;
        }
    }

    private void playTonePreview(Uri uri) {
        stopPreview();
        Context context = getContext();
        if (context == null) return;
        try {
            if (uri == null) {
                previewMediaPlayer = MediaPlayer.create(context, R.raw.notifica);
                if (previewMediaPlayer != null) {
                    previewMediaPlayer.setOnCompletionListener(mp -> {
                        try {
                            mp.release();
                        } catch (Exception ignored) {}
                        if (previewMediaPlayer == mp) {
                            previewMediaPlayer = null;
                        }
                    });
                    previewMediaPlayer.start();
                    previewHandler.postDelayed(this::stopPreview, 5000);
                }
            } else {
                previewRingtone = RingtoneManager.getRingtone(context, uri);
                if (previewRingtone != null) {
                    previewRingtone.play();
                    previewHandler.postDelayed(this::stopPreview, 5000);
                }
            }
        } catch (Exception ignored) {}
    }

    private void updateSoundSummary(Preference gen_8) {
        if (gen_8 == null || getContext() == null) return;
        String sound = SettingsDB.getInstance(getContext()).getSound();
        if ("Default".equalsIgnoreCase(sound)) {
            gen_8.setSummary(getString(R.string.default_sound_title, getString(R.string.app_name)));
        } else {
            try {
                Uri uri = Uri.parse(sound);
                Uri defaultAlarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (uri != null && uri.equals(defaultAlarm)) {
                    gen_8.setSummary(getString(R.string.system_alarm_sound));
                } else {
                    Ringtone r = RingtoneManager.getRingtone(getContext(), uri);
                    if (r != null) {
                        String title = r.getTitle(getContext());
                        gen_8.setSummary(title != null ? title : sound);
                    } else {
                        gen_8.setSummary(sound);
                    }
                }
            } catch (Exception e) {
                gen_8.setSummary(sound);
            }
        }
    }

    private void setupRingtone() {
        Preference gen_8 = findPreference("d_8");
        if (gen_8 != null) {
            updateSoundSummary(gen_8);

            gen_8.setOnPreferenceClickListener(preference -> {
                stopPreview();
                CharSequence opzioni[] = new CharSequence[] {
                        getString(R.string.default_sound_title, getString(R.string.app_name)),
                        getString(R.string.system_alarm_sound),
                        getString(R.string.selectTone)
                };

                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(getString(R.string.selectopt));
                builder.setItems(opzioni, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            chosenRingtone = "Default";
                            SettingsDB.getInstance(getContext()).setSound(chosenRingtone);
                            updateSoundSummary(gen_8);
                            playTonePreview(null);
                            break;
                        case 1:
                            Uri defaultAlarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                            chosenRingtone = defaultAlarmUri != null ? defaultAlarmUri.toString() : "Default";
                            SettingsDB.getInstance(getContext()).setSound(chosenRingtone);
                            updateSoundSummary(gen_8);
                            playTonePreview(defaultAlarmUri);
                            break;
                        case 2:
                            stopPreview();
                            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
                            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.selectTone));
                            String currentSound = SettingsDB.getInstance(getContext()).getSound();
                            if (!"Default".equalsIgnoreCase(currentSound)) {
                                try {
                                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentSound));
                                } catch (Exception ignored) {}
                            }
                            ringtonePickerLauncher.launch(intent);
                            break;
                    }
                });
                builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> stopPreview());
                builder.show();
                return true;
            });
        }
    }

    private int devClickCount = 0;

    private void setupAboutAndCredits() {
        Preference aboutPref = findPreference("d_about");
        if (aboutPref != null) {
            aboutPref.setSummary(getString(R.string.about_app_summary, BuildConfig.VERSION_NAME));
            aboutPref.setOnPreferenceClickListener(preference -> {
                Context context = getContext();
                if (context == null) return true;

                devClickCount++;
                if (devClickCount >= 7) {
                    devClickCount = 0;
                    boolean currentState = SettingsDB.getInstance(context).isDeveloperModeEnabled();
                    boolean newState = !currentState;
                    SettingsDB.getInstance(context).setDeveloperModeEnabled(newState);
                    String msg = newState ? "Developer mode enabled! Test Alarm is now visible."
                            : "Developer mode disabled!";
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show();
                    setPreferencesFromResource(R.xml.settings, null);
                    onCreatePreferences(null, null);
                    return true;
                }

                String aboutMessage = getString(R.string.about_app_dialog_body, BuildConfig.VERSION_NAME);

                new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.about_app_title)
                        .setIcon(R.drawable.baseline_info_24)
                        .setMessage(aboutMessage)
                        .setPositiveButton(R.string.btn_close, null)
                        .setNeutralButton(R.string.privacy_policy_title, (dialog, which) -> openPrivacyPolicyOnline())
                        .show();

                return true;
            });
        }
    }

    private void openPrivacyPolicyOnline() {
        Context context = getContext();
        if (context == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(swimminsparrow.ricordamelo.config.Constants.PRIVACY_POLICY_URL));
            startActivity(intent);
        } catch (Exception e) {
            android.widget.Toast.makeText(context, R.string.cannot_open_browser, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void setupHealthCheck() {
        Preference gen_11 = findPreference("d_11");

        if (gen_11 != null)
            gen_11.setOnPreferenceClickListener(preference -> {
                BatteryOptimizationsHelper.openBatteryOptimizationSettings(getContext());
                return true;
            });
    }

    private void setupTestAlarm() {
        Preference gen_test = findPreference("d_test_alarm");
        if (gen_test != null) {
            if (!BuildConfig.DEBUG) {
                gen_test.setVisible(false);
                androidx.preference.PreferenceCategory category = findPreference("set_troubleshooting_tools");
                if (category != null) {
                    category.removePreference(gen_test);
                }
                return;
            }
            gen_test.setOnPreferenceClickListener(preference -> {
                Context context = getContext();
                if (context != null) {
                    showNotificationTestChooser(context);
                }
                return true;
            });
        }
    }

    private void showNotificationTestChooser(Context context) {
        String[] options = new String[] {
                getString(R.string.test_notif_pill_alarm),
                getString(R.string.test_notif_sos_alert),
                getString(R.string.test_notif_fall_alert),
                getString(R.string.test_notif_pairing_req),
                getString(R.string.test_notif_im_ok),
                getString(R.string.test_notif_delivered)
        };

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(R.string.test_notification_dialog_title)
                .setIcon(R.drawable.ic_record_voice_over_24)
                .setItems(options, (dialog, which) -> {
                    // Reset any active alarms/sounds first for a clean test
                    swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().stop(context);
                    swimminsparrow.ricordamelo.core.protection.FallAlertController.getInstance().cancelAlert(context);
                    androidx.core.app.NotificationManagerCompat.from(context).cancel(swimminsparrow.ricordamelo.config.Constants.TEST_ALARM_NOTIFICATION_ID);
                    androidx.core.app.NotificationManagerCompat.from(context).cancel(swimminsparrow.ricordamelo.core.notification.AppNotificationManager.ID_SOS_ALERT);
                    androidx.core.app.NotificationManagerCompat.from(context).cancel(swimminsparrow.ricordamelo.config.Constants.FALL_ALERT_NOTIFICATION_ID);

                    switch (which) {
                        case 0: // Medication Reminder (5s delay via AlarmManager)
                            scheduleAlarmTest(context, false);
                            break;
                        case 1: // Received SOS Siren (5s delay via AlarmManager)
                            scheduleAlarmTest(context, true);
                            break;
                        case 2: // Fall Detection Pre-Alarm (5s delay)
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                swimminsparrow.ricordamelo.core.protection.FallAlertController.getInstance().startAlert(context);
                            }, 5000);
                            showTestScheduledDialog(context);
                            break;
                        case 3: // Connection Request (Immediate)
                            swimminsparrow.ricordamelo.core.sos.ConnectionRequest testReq =
                                    new swimminsparrow.ricordamelo.core.sos.ConnectionRequest("test_req", "Mariomoto", "test_topic", "dummy_key", "", System.currentTimeMillis());
                            swimminsparrow.ricordamelo.core.notification.AppNotificationManager.getInstance()
                                    .showConnectionRequestNotification(context, testReq);
                            android.widget.Toast.makeText(context, R.string.test_notif_sent_toast, android.widget.Toast.LENGTH_SHORT).show();
                            break;
                        case 4: // "I'm OK" status message (Immediate)
                            swimminsparrow.ricordamelo.core.notification.AppNotificationManager.getInstance()
                                    .showImOk(context, "Mariomoto", "test_topic", "Sto bene! Tutto sotto controllo.");
                            android.widget.Toast.makeText(context, R.string.test_notif_sent_toast, android.widget.Toast.LENGTH_SHORT).show();
                            break;
                        case 5: // SOS Delivery Status (Immediate)
                            swimminsparrow.ricordamelo.core.notification.AppNotificationManager.getInstance()
                                    .showOutboxDelivered(context);
                            android.widget.Toast.makeText(context, R.string.test_notif_sent_toast, android.widget.Toast.LENGTH_SHORT).show();
                            break;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void scheduleAlarmTest(Context context, boolean isSosAlert) {
        int testId = swimminsparrow.ricordamelo.config.Constants.TEST_ALARM_NOTIFICATION_ID;
        Intent intent = new Intent(context, swimminsparrow.ricordamelo.services.ReminderReceiver.class);
        if (isSosAlert) {
            intent.putExtra("is_sos_alert", true);
        } else {
            intent.putExtra("ID", testId);
            intent.putExtra("Orario", "TEST");
        }

        PendingIntent pendingIntent = swimminsparrow.ricordamelo.core.utils.PendingIntentHelper
                .getBroadcast(context, testId, intent, true);

        android.app.AlarmManager am = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(pendingIntent);
            long triggerAt = System.currentTimeMillis() + 5000;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                am.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        }

        showTestScheduledDialog(context);
    }

    private void showTestScheduledDialog(Context context) {
        DialogBuilder.successDialog(context, getString(R.string.test_alarm_title),
                getString(R.string.test_notif_scheduled_toast) + "\n\n" +
                "• A schermo bloccato: premi subito il tasto laterale per spegnere lo schermo e testare l'avviso a schermo intero.\n\n" +
                "• A schermo sbloccato: premi Home o apri un'altra app per testare il banner Heads-Up in alto!")
                .show();
    }



    private void setupRateApp() {
        Preference ratePref = findPreference("d_rate_app");
        if (ratePref != null) {
            ratePref.setOnPreferenceClickListener(preference -> {
                if (getActivity() != null) {
                    swimminsparrow.ricordamelo.ui.rateme.RateMeManager.show(getActivity());
                }
                return true;
            });
        }
    }

    private void setupCoffee() {
        Preference gen_coffee = findPreference("d_coffee");
        if (gen_coffee != null) {
            gen_coffee.setOnPreferenceClickListener(preference -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.DONATE_URL));
                    startActivity(intent);
                } catch (Exception e) {
                    android.widget.Toast
                            .makeText(getContext(), "Impossibile aprire il browser", android.widget.Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            });
        }
    }

    private void tintPreferenceIcons(Preference preference) {
        if (preference == null || getContext() == null) return;

        int primaryColor = ContextCompat.getColor(requireContext(), R.color.defaultColor);

        Drawable icon = preference.getIcon();
        if (icon != null) {
            Drawable tinted = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTint(tinted, primaryColor);
            preference.setIcon(tinted);
        }

        if (preference instanceof PreferenceGroup) {
            PreferenceGroup group = (PreferenceGroup) preference;
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                tintPreferenceIcons(group.getPreference(i));
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPreview();
    }

}