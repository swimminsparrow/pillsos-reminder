package swimminsparrow.ricordamelo.core.data;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class SettingsDBTest {

    private Context context;
    private SettingsDB settingsDB;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("databasePreferences", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("personalInfo", Context.MODE_PRIVATE).edit().clear().commit();
        settingsDB = SettingsDB.getInstance(context);
    }

    @Test
    public void testPersonalNamePersistence() {
        settingsDB.setPersonalName("Giuseppe Garibaldi");
        assertEquals("Giuseppe Garibaldi", settingsDB.getPersonalName());
    }

    @Test
    public void testSOSSendEnabledPersistence() {
        settingsDB.setSOSSendEnabled(true);
        assertTrue(settingsDB.isSOSSendEnabled());

        settingsDB.setSOSSendEnabled(false);
        assertFalse(settingsDB.isSOSSendEnabled());
    }

    @Test
    public void testSOSSendEnabledDefaultWithContacts() {
        swimminsparrow.ricordamelo.core.sos.SOSContact contact =
                new swimminsparrow.ricordamelo.core.sos.SOSContact("c1", "Caregiver", "topic_caregiver", "k1", "MUTUAL", 1000L);
        swimminsparrow.ricordamelo.core.sos.SOSContactsManager.addContact(context, contact);

        assertTrue("SOSContactsManager should have configured contacts",
                swimminsparrow.ricordamelo.core.sos.SOSContactsManager.hasConfiguredContacts(context));
        assertTrue("When at least one SOS contact is configured, SOS send should be enabled by default",
                settingsDB.isSOSSendEnabled());

        swimminsparrow.ricordamelo.core.sos.SOSContactsManager.removeContact(context, "topic_caregiver");
    }

    @Test
    public void testSilentAndVibrationModes() {
        settingsDB.setSilentMode(true);
        assertTrue(settingsDB.getSilentMode());
        settingsDB.setSilentMode(false);
        assertFalse(settingsDB.getSilentMode());

        settingsDB.setVibrationMode(true);
        assertTrue(settingsDB.getVibrationMode());
        settingsDB.setVibrationMode(false);
        assertFalse(settingsDB.getVibrationMode());
    }

    @Test
    public void testAlarmSoundMode() {
        assertTrue(settingsDB.isMaxVolumeMode());
        settingsDB.setAlarmSoundMode(SettingsDB.SOUND_MODE_SYSTEM);
        assertFalse(settingsDB.isMaxVolumeMode());
        settingsDB.setAlarmSoundMode(SettingsDB.SOUND_MODE_MAX_VOLUME);
        assertTrue(settingsDB.isMaxVolumeMode());
    }

    @Test
    public void testTTSMode() {
        assertTrue(settingsDB.getTTSMode());
        settingsDB.setTTSMode(false);
        assertFalse(settingsDB.getTTSMode());
        settingsDB.setTTSMode(true);
        assertTrue(settingsDB.getTTSMode());
    }

    @Test
    public void testFallDetectionModeDefaultAndToggle() {
        // Fall detection must be active by default
        assertTrue(settingsDB.getFallDetectionMode());

        settingsDB.setFallDetectionMode(false);
        assertFalse(settingsDB.getFallDetectionMode());

        settingsDB.setFallDetectionMode(true);
        assertTrue(settingsDB.getFallDetectionMode());
    }

    @Test
    public void testFallDetectionSensitivity() {
        // High sensitivity (slider = 80) -> lower threshold (4.5 G)
        settingsDB.setFallDetectionSensitivity(80);
        double highSensThreshold = settingsDB.getFallDetectionImpactThreshold();
        assertEquals(4.5, highSensThreshold, 0.01);

        // Low sensitivity (slider = 20) -> higher threshold (7.5 G)
        settingsDB.setFallDetectionSensitivity(20);
        double lowSensThreshold = settingsDB.getFallDetectionImpactThreshold();
        assertEquals(7.5, lowSensThreshold, 0.01);

        // Verify higher sensitivity slider value produces lower G threshold
        assertTrue(highSensThreshold < lowSensThreshold);
    }
}
