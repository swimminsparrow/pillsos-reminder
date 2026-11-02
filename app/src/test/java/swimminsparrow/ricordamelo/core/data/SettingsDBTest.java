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
    public void testAlarmSoundMode() {
        assertTrue("Default sound mode must be max volume", settingsDB.isMaxVolumeMode());
        settingsDB.setAlarmSoundMode(SettingsDB.SOUND_MODE_SYSTEM);
        assertFalse(settingsDB.isMaxVolumeMode());
        assertEquals(SettingsDB.SOUND_MODE_SYSTEM, settingsDB.getAlarmSoundMode());
        settingsDB.setAlarmSoundMode(SettingsDB.SOUND_MODE_MAX_VOLUME);
        assertTrue(settingsDB.isMaxVolumeMode());
        assertEquals(SettingsDB.SOUND_MODE_MAX_VOLUME, settingsDB.getAlarmSoundMode());
    }

    @Test
    public void testVolumeOverrideState() {
        assertFalse(settingsDB.isVolumeOverridden());
        settingsDB.savePreOverrideVolumes(5, 7);
        assertTrue(settingsDB.isVolumeOverridden());
        assertEquals(5, settingsDB.getSavedAlarmVolume());
        assertEquals(7, settingsDB.getSavedRingVolume());

        settingsDB.clearVolumeOverrideState();
        assertFalse(settingsDB.isVolumeOverridden());
        assertEquals(-1, settingsDB.getSavedAlarmVolume());
        assertEquals(-1, settingsDB.getSavedRingVolume());
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

    @Test
    public void testPendingConnectionRequestPersistence() {
        assertNull(settingsDB.getPendingConnectionRequest());

        long now = System.currentTimeMillis();
        swimminsparrow.ricordamelo.core.sos.ConnectionRequest request =
                new swimminsparrow.ricordamelo.core.sos.ConnectionRequest("req_123", "Mario Rossi", "topic_mario", "key_secret", "avatar_b64", now);

        settingsDB.savePendingConnectionRequest(request);

        swimminsparrow.ricordamelo.core.sos.ConnectionRequest retrieved = settingsDB.getPendingConnectionRequest();
        assertNotNull(retrieved);
        assertEquals("req_123", retrieved.reqId);
        assertEquals("Mario Rossi", retrieved.name);
        assertEquals("topic_mario", retrieved.topic);
        assertEquals("key_secret", retrieved.key);
        assertEquals("avatar_b64", retrieved.avatar);
        assertEquals(now, retrieved.timestamp);
        assertFalse(retrieved.isExpired());

        settingsDB.clearPendingConnectionRequest();
        assertNull(settingsDB.getPendingConnectionRequest());
    }
}
