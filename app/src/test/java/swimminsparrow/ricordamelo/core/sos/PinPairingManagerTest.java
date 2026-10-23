package swimminsparrow.ricordamelo.core.sos;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class PinPairingManagerTest {

    @Test
    public void testGenerateNewOtp() {
        String otp = PinPairingManager.generateNewOtp();
        assertNotNull(otp);
        assertEquals(6, otp.length());
        int value = Integer.parseInt(otp);
        assertTrue(value >= 100000 && value <= 999999);
    }

    @Test
    public void testMultipleOtpsAreUnique() {
        String otp1 = PinPairingManager.generateNewOtp();
        String otp2 = PinPairingManager.generateNewOtp();
        assertNotNull(otp1);
        assertNotNull(otp2);
        // While theoretically possible to collide randomly (1 in 900,000), they should virtually always differ
        assertNotEquals(otp1, otp2);
    }

    @Test
    public void testIsResponseMatching_matchingReqId() throws Exception {
        long startTime = System.currentTimeMillis();
        JSONObject resp = new JSONObject();
        resp.put("topic", "user_b_uuid");
        resp.put("req_id", "req-12345");
        resp.put("status", "APPROVED");
        resp.put("ts", startTime + 100);

        assertTrue(PinPairingManager.isResponseMatching(resp, "req-12345", startTime));
    }

    @Test
    public void testIsResponseMatching_staleRejectionDifferentReqIdIsIgnored() throws Exception {
        long startTime = System.currentTimeMillis();
        // Stale rejection message from a previous attempt
        JSONObject staleReject = new JSONObject();
        staleReject.put("topic", "user_b_uuid");
        staleReject.put("req_id", "req-old-attempt");
        staleReject.put("status", "REJECTED");
        staleReject.put("ts", startTime - 5000);

        // When User A is waiting for response to "req-new-attempt", stale rejection must be ignored
        assertFalse(PinPairingManager.isResponseMatching(staleReject, "req-new-attempt", startTime));
    }

    @Test
    public void testIsResponseMatching_legacyWithoutReqId_freshTimestamp() throws Exception {
        long startTime = System.currentTimeMillis();
        JSONObject resp = new JSONObject();
        resp.put("topic", "user_b_uuid");
        resp.put("status", "APPROVED");
        resp.put("ts", startTime);

        assertTrue(PinPairingManager.isResponseMatching(resp, "req-new", startTime));
    }

    @Test
    public void testIsResponseMatching_legacyWithoutReqId_staleTimestampIsIgnored() throws Exception {
        long startTime = System.currentTimeMillis();
        JSONObject staleResp = new JSONObject();
        staleResp.put("topic", "user_b_uuid");
        staleResp.put("status", "REJECTED");
        // Sent 10 seconds before the current request started
        staleResp.put("ts", startTime - 10000);

        assertFalse(PinPairingManager.isResponseMatching(staleResp, "req-new", startTime));
    }

    @Test
    public void testIsResponseMatching_nullOrEmptyTopic() throws Exception {
        long startTime = System.currentTimeMillis();
        assertFalse(PinPairingManager.isResponseMatching(null, "req-1", startTime));

        JSONObject noTopic = new JSONObject();
        noTopic.put("req_id", "req-1");
        assertFalse(PinPairingManager.isResponseMatching(noTopic, "req-1", startTime));
    }

    @Test
    public void testIsRequestExpired() {
        long now = System.currentTimeMillis();
        // Just created -> not expired
        assertFalse(ConnectionRequestManager.isRequestExpired(now));
        // 30 seconds ago -> not expired
        assertFalse(ConnectionRequestManager.isRequestExpired(now - 30000));
        // 121 seconds ago -> expired (> 120s)
        assertTrue(ConnectionRequestManager.isRequestExpired(now - 121000));
        // Invalid / empty timestamp -> false
        assertFalse(ConnectionRequestManager.isRequestExpired(0));
    }
}
