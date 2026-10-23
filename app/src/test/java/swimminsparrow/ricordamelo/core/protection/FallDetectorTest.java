package swimminsparrow.ricordamelo.core.protection;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class FallDetectorTest {

    private FallDetector detector;
    private AtomicBoolean fallCandidate;
    private AtomicBoolean fallConfirmed;
    private AtomicInteger fallDiscardedCount;

    @Before
    public void setUp() {
        fallCandidate = new AtomicBoolean(false);
        fallConfirmed = new AtomicBoolean(false);
        fallDiscardedCount = new AtomicInteger(0);

        detector = new FallDetector(new FallDetector.Listener() {
            @Override
            public void onFallCandidateDetected() {
                fallCandidate.set(true);
            }

            @Override
            public void onFallConfirmed() {
                fallConfirmed.set(true);
            }

            @Override
            public void onFallDiscarded(String reason) {
                fallDiscardedCount.incrementAndGet();
            }
        });
        detector.setImpactThreshold(5.0);
    }

    @Test
    public void testNormalWalkingDoesNotTrigger() {
        long t = 1000;
        for (int i = 0; i < 100; i++) {
            // Walking pattern: 0.85G to 1.25G, 20ms steps
            double g = 1.0 + 0.2 * Math.sin(i * 0.2);
            detector.processGForce(g, t);
            t += 20;
        }

        assertFalse(fallCandidate.get());
        assertFalse(fallConfirmed.get());
        assertEquals(FallDetector.State.IDLE, detector.getState());
    }

    @Test
    public void testGlitchFreeFallTooShortIsIgnored() {
        long t = 1000;
        detector.processGForce(1.0, t); // IDLE
        t += 20;
        detector.processGForce(0.1, t); // FREE_FALL begins (duration 0)
        t += 30; // 30ms < 75ms min duration
        detector.processGForce(5.5, t); // Spikes, but duration was only 30ms -> reset

        assertFalse(fallCandidate.get());
        assertFalse(fallConfirmed.get());
        assertEquals(FallDetector.State.IDLE, detector.getState());
    }

    @Test
    public void testFreeFallWithoutImpactTimesOut() {
        long t = 1000;
        // 120ms free fall
        for (int i = 0; i < 6; i++) {
            detector.processGForce(0.1, t);
            t += 20;
        }
        assertEquals(FallDetector.State.FREE_FALL, detector.getState());

        // Exit free fall with normal gravity, no impact
        detector.processGForce(1.0, t);
        assertEquals(FallDetector.State.WAIT_IMPACT, detector.getState());

        // Advance beyond MAX_IMPACT_WAIT_MS (700ms) with normal values
        t += 800;
        detector.processGForce(1.0, t);

        assertFalse(fallCandidate.get());
        assertFalse(fallConfirmed.get());
        assertEquals(FallDetector.State.IDLE, detector.getState());
        assertTrue(fallDiscardedCount.get() > 0);
    }

    @Test
    public void testImmediateMovementAfterImpactDiscardsFall() {
        long t = 1000;
        // 120ms free fall
        for (int i = 0; i < 6; i++) {
            detector.processGForce(0.1, t);
            t += 20;
        }
        // Impact!
        detector.processGForce(6.0, t);
        t += 20;
        assertTrue(fallCandidate.get());
        assertEquals(FallDetector.State.POST_IMPACT_SETTLE, detector.getState());

        // Wait settle period (1000ms)
        t += 1050;
        detector.processGForce(1.0, t);
        assertEquals(FallDetector.State.POST_IMPACT_STILLNESS, detector.getState());

        // Phone is moved / picked up immediately (g-force swings)
        t += 100;
        detector.processGForce(1.8, t);

        // Fall should be discarded!
        assertFalse(fallConfirmed.get());
        assertEquals(FallDetector.State.IDLE, detector.getState());
        assertTrue(fallDiscardedCount.get() > 0);
    }

    @Test
    public void testValidFallWithStillnessTriggersConfirmation() {
        long t = 1000;
        // 1. Free fall: 140ms
        for (int i = 0; i < 7; i++) {
            detector.processGForce(0.15, t);
            t += 20;
        }
        // 2. Impact peak: 6.2G
        detector.processGForce(6.2, t);
        assertTrue(fallCandidate.get());
        assertEquals(FallDetector.State.POST_IMPACT_SETTLE, detector.getState());

        // 3. Settle period: 1000ms (oscillations allowed)
        for (int i = 0; i < 50; i++) {
            t += 20;
            detector.processGForce(1.0 + (i % 2 == 0 ? 0.3 : -0.3), t);
        }

        // 4. Stillness period: 2500ms at ~1.0G
        for (int i = 0; i < 130; i++) {
            t += 20;
            detector.processGForce(1.0 + (i % 2 == 0 ? 0.02 : -0.02), t);
        }

        // Must be confirmed!
        assertTrue(fallConfirmed.get());
        assertEquals(FallDetector.State.IDLE, detector.getState());
    }

    @Test
    public void testTossOnBedSoftImpactDoesNotTrigger() {
        long t = 1000;
        // 180ms free fall
        for (int i = 0; i < 9; i++) {
            detector.processGForce(0.1, t);
            t += 20;
        }
        // Soft impact on pillow/mattress: 2.2 G (< 5.0 G threshold)
        detector.processGForce(2.2, t);
        t += 20;

        assertFalse(fallCandidate.get());
        assertEquals(FallDetector.State.WAIT_IMPACT, detector.getState());

        // Wait past MAX_IMPACT_WAIT_MS
        t += 800;
        detector.processGForce(1.0, t);

        assertFalse(fallCandidate.get());
        assertFalse(fallConfirmed.get());
        assertEquals(FallDetector.State.IDLE, detector.getState());
    }

    @Test
    public void testHighVarianceDuringStillnessDiscards() {
        long t = 1000;
        // 140ms free fall
        for (int i = 0; i < 7; i++) {
            detector.processGForce(0.15, t);
            t += 20;
        }
        detector.processGForce(6.0, t);
        t += 20;
        assertTrue(fallCandidate.get());

        // Settle period 1000ms
        t += 1050;
        detector.processGForce(1.0, t);
        assertEquals(FallDetector.State.POST_IMPACT_STILLNESS, detector.getState());

        // Jittering (e.g. vibrating motor or shaking hand) between 0.7G and 1.3G
        for (int i = 0; i < 20; i++) {
            t += 20;
            detector.processGForce(i % 2 == 0 ? 0.70 : 1.30, t);
        }

        assertFalse(fallConfirmed.get());
        assertEquals(FallDetector.State.IDLE, detector.getState());
        assertTrue(fallDiscardedCount.get() > 0);
    }
}
