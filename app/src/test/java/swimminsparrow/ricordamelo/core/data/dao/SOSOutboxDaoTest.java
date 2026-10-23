package swimminsparrow.ricordamelo.core.data.dao;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import swimminsparrow.ricordamelo.core.data.models.SOSOutboxMessage;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class SOSOutboxDaoTest {

    private Context context;
    private SOSOutboxDao dao;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        dao = SOSOutboxDao.getInstance(context);

        // Clear existing outbox messages
        List<SOSOutboxMessage> pending = dao.getPendingMessages();
        for (SOSOutboxMessage m : pending) {
            dao.removeMessage(m.getId());
        }
    }

    @Test
    public void testAddAndGetPendingMessages() {
        assertFalse(dao.hasPendingMessages());

        long id = dao.addPendingMessage("topic_sos_1", "ALARM_SOS:Need help!", System.currentTimeMillis());
        assertTrue(id > 0);
        assertTrue(dao.hasPendingMessages());

        List<SOSOutboxMessage> list = dao.getPendingMessages();
        assertEquals(1, list.size());

        SOSOutboxMessage msg = list.get(0);
        assertEquals(id, msg.getId());
        assertEquals("topic_sos_1", msg.getTopic());
        assertEquals("ALARM_SOS:Need help!", msg.getMessage());
        assertEquals(0, msg.getAttempts());
    }

    @Test
    public void testIncrementAttemptsAndRemove() {
        long id = dao.addPendingMessage("topic_sos_2", "IM_OK_SOS:All good", System.currentTimeMillis());
        assertTrue(id > 0);

        dao.incrementAttempts(id);
        List<SOSOutboxMessage> list = dao.getPendingMessages();
        assertEquals(1, list.size());
        assertEquals(1, list.get(0).getAttempts());

        dao.removeMessage(id);
        assertFalse(dao.hasPendingMessages());
        assertTrue(dao.getPendingMessages().isEmpty());
    }

    @Test
    public void testClearExpiredMessages() {
        long oldTs = System.currentTimeMillis() - 100000;
        dao.addPendingMessage("topic_old", "OLD_MSG", oldTs);

        dao.clearExpiredMessages(50000);
        assertFalse(dao.hasPendingMessages());
    }
}
