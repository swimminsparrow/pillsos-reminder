package swimminsparrow.ricordamelo.core.sos;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class SOSContactsManagerTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        // Clear all contacts from SharedPreferences before each test
        List<SOSContact> contacts = SOSContactsManager.getContacts(context);
        for (SOSContact c : contacts) {
            SOSContactsManager.removeContact(context, c.getTopic());
        }
    }

    @Test
    public void testAddAndGetContact() {
        SOSContact contact = new SOSContact("c_1", "Dottore", "topic_doc", "key123", "MUTUAL", System.currentTimeMillis());
        SOSContactsManager.addContact(context, contact);

        assertTrue(SOSContactsManager.isContactPaired(context, "topic_doc"));
        assertFalse(SOSContactsManager.isContactPaired(context, "non_existent"));

        SOSContact retrieved = SOSContactsManager.getContactByTopic(context, "topic_doc");
        assertNotNull(retrieved);
        assertEquals("Dottore", retrieved.getName());

        boolean removed = SOSContactsManager.removeContact(context, "topic_doc");
        assertTrue(removed);
        assertFalse(SOSContactsManager.isContactPaired(context, "topic_doc"));
    }

    @Test
    public void testAddMutualContact() {
        SOSContactsManager.addMutualContact(context, "topic_nonna", "Nonna", "key456", "avatar_base64");

        assertTrue(SOSContactsManager.isContactPaired(context, "topic_nonna"));
        assertFalse(SOSContactsManager.isContactPaired(context, "topic_altro"));

        List<SOSContact> contacts = SOSContactsManager.getContacts(context);
        assertEquals(1, contacts.size());
        assertEquals("Nonna", contacts.get(0).getName());
        assertEquals("avatar_base64", contacts.get(0).getAvatarBase64());

        boolean removed = SOSContactsManager.removeContact(context, "topic_nonna");
        assertTrue(removed);
        assertFalse(SOSContactsManager.isContactPaired(context, "topic_nonna"));
    }

    @Test
    public void testDuplicateTopicUpdatesExistingContact() {
        SOSContact initial = new SOSContact("id_1", "Mario", "topic_common", "key1", "MUTUAL", 1000L);
        SOSContactsManager.addContact(context, initial);

        SOSContact updated = new SOSContact("id_1", "Super Mario", "topic_common", "key2", "MUTUAL", 2000L);
        SOSContactsManager.addContact(context, updated);

        List<SOSContact> list = SOSContactsManager.getContacts(context);
        assertEquals(1, list.size());
        assertEquals("Super Mario", list.get(0).getName());
        assertEquals("key2", list.get(0).getKey());
    }

    @Test
    public void testRequestHandlingPersistence() {
        String topic = "test_request_topic";
        long ts = 1700000000000L;

        assertFalse(SOSContactsManager.isRequestHandled(context, topic, ts));

        SOSContactsManager.markRequestHandled(context, topic, ts);
        assertTrue(SOSContactsManager.isRequestHandled(context, topic, ts));
        assertTrue(SOSContactsManager.isRequestHandled(context, topic, ts - 1000L));
    }

    @Test
    public void testGetSenderNameForTopic() {
        SOSContact c1 = new SOSContact("s1", "Alice", "topic_alice", "k1", "MUTUAL", 1000L);
        SOSContactsManager.addContact(context, c1);

        SOSContact c2 = new SOSContact("r1", "Bob", "topic_bob", "k2", "MUTUAL", 1000L);
        SOSContactsManager.addContact(context, c2);

        assertEquals("Alice", SOSContactsManager.getSenderNameForTopic(context, "topic_alice"));
        assertEquals("Bob", SOSContactsManager.getSenderNameForTopic(context, "topic_bob"));
        assertNull(SOSContactsManager.getSenderNameForTopic(context, "unknown_topic"));
    }

    @Test
    public void testImOkStatusMsgDoesNotContainFormatSpecifiers() {
        String msg = context.getString(swimminsparrow.ricordamelo.R.string.im_ok_status_msg);
        assertNotNull(msg);
        assertFalse("Message should not contain format specifiers: " + msg, msg.contains("%s"));
        assertFalse("Message should not contain format specifiers: " + msg, msg.contains("%1$s"));
    }

    @Test
    public void testHasConfiguredContacts() {
        assertFalse(SOSContactsManager.hasConfiguredContacts(context));

        SOSContact contact = new SOSContact("r1", "Bob", "topic_bob", "k2", "MUTUAL", 1000L);
        SOSContactsManager.addContact(context, contact);

        assertTrue(SOSContactsManager.hasConfiguredContacts(context));

        SOSContactsManager.removeContact(context, "topic_bob");
        assertFalse(SOSContactsManager.hasConfiguredContacts(context));
    }

    @Test
    public void testContactsListAndFormattedTimestamp() {
        SOSContactsManager.addMutualContact(context, "topic_mutual", "Mario", "k1", "avatar1");
        SOSContactsManager.addMutualContact(context, "topic_nonna", "Nonna", "k2", "avatar2");

        List<SOSContact> contacts = SOSContactsManager.getContacts(context);
        assertEquals(2, contacts.size());

        SOSContact mContact = null;
        SOSContact nContact = null;
        for (SOSContact c : contacts) {
            if ("topic_mutual".equals(c.getTopic())) mContact = c;
            if ("topic_nonna".equals(c.getTopic())) nContact = c;
        }

        assertNotNull(mContact);
        assertEquals("Mario", mContact.getName());
        assertFalse(mContact.getFormattedTimestamp().isEmpty());

        assertNotNull(nContact);
        assertEquals("Nonna", nContact.getName());
        assertFalse(nContact.getFormattedTimestamp().isEmpty());
    }

    @Test
    public void testHandleNameUpdate_updatesMatchingContact() {
        SOSContact c1 = new SOSContact("c1", "Vecchio Nome", "topic_contact_1", "key1", "MUTUAL", 1000L);
        SOSContactsManager.addContact(context, c1);

        // Remote contact sent name update with their uuid
        SOSContactsManager.handleNameUpdate(context, "topic_contact_1", "stream_topic", "Nuovo Nome");

        SOSContact updated = SOSContactsManager.getContactByTopic(context, "topic_contact_1");
        assertNotNull(updated);
        assertEquals("Nuovo Nome", updated.getName());
    }

    @Test
    public void testHandleNameUpdate_ignoresSelfUpdate() {
        String myUuid = swimminsparrow.ricordamelo.core.data.SettingsDB.getInstance(context).getUserUUID();
        SOSContact c1 = new SOSContact("c1", "Amico", "topic_amico", "key1", "MUTUAL", 1000L);
        SOSContactsManager.addContact(context, c1);

        // Echoed update sent by myself
        SOSContactsManager.handleNameUpdate(context, myUuid, "topic_amico", "Mio Nome");

        SOSContact c = SOSContactsManager.getContactByTopic(context, "topic_amico");
        assertNotNull(c);
        // Name should NOT be changed to my name!
        assertEquals("Amico", c.getName());
    }

    @Test
    public void testHandleNameUpdate_doesNotUpdateByStreamTopicAlone() {
        SOSContact c1 = new SOSContact("c1", "Amico", "topic_amico", "key1", "MUTUAL", 1000L);
        SOSContactsManager.addContact(context, c1);

        // senderUuid is different from contact topic, even if streamTopic matches contact topic
        SOSContactsManager.handleNameUpdate(context, "another_sender_uuid", "topic_amico", "Nome Sbagliato");

        SOSContact c = SOSContactsManager.getContactByTopic(context, "topic_amico");
        assertNotNull(c);
        // Contact should NOT have its name overwritten
        assertEquals("Amico", c.getName());
    }
}
