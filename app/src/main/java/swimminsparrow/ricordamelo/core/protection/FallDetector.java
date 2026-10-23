package swimminsparrow.ricordamelo.core.protection;

/**
 * High-accuracy multi-phase fall detection algorithm.
 *
 * Phases:
 * 1. FREE_FALL:
 *    Acceleration magnitude drops below FREE_FALL_THRESHOLD (default 0.42 G)
 *    and stays continuously low for at least MIN_FREE_FALL_MS (75 ms)
 *    up to MAX_FREE_FALL_MS (650 ms).
 *
 * 2. WAIT_IMPACT:
 *    Within MAX_IMPACT_WAIT_MS (700 ms) from free fall, a sharp deceleration peak
 *    occurs, exceeding impactThreshold (typically 4.0 - 7.0 G).
 *
 * 3. POST_IMPACT_SETTLE:
 *    1000 ms grace period to let immediate bounces and device tumbling dissipate.
 *
 * 4. POST_IMPACT_STILLNESS:
 *    Monitors motion for STILLNESS_WINDOW_MS (2500 ms).
 *    If the person is walking, jogging, or picks up the phone immediately,
 *    acceleration fluctuates significantly (variance or peak deviations from 1.0 G).
 *    In that case, the fall is classified as a false positive and discarded.
 *    If stillness is maintained for the entire window, onFallConfirmed() is triggered.
 */
public class FallDetector {

    public interface Listener {
        void onFallCandidateDetected();
        void onFallConfirmed();
        void onFallDiscarded(String reason);
    }

    public enum State {
        IDLE,
        FREE_FALL,
        WAIT_IMPACT,
        POST_IMPACT_SETTLE,
        POST_IMPACT_STILLNESS
    }

    public static final double DEFAULT_FREE_FALL_THRESHOLD_G = 0.42;
    public static final long MIN_FREE_FALL_MS = 75;
    public static final long MAX_FREE_FALL_MS = 650;
    public static final long MAX_IMPACT_WAIT_MS = 700;
    public static final double DEFAULT_IMPACT_THRESHOLD_G = 5.0;
    public static final long POST_IMPACT_SETTLE_MS = 1000;
    public static final long STILLNESS_WINDOW_MS = 2500;
    public static final double STILLNESS_MIN_G = 0.65;
    public static final double STILLNESS_MAX_G = 1.35;
    public static final double STILLNESS_MAX_VARIANCE = 0.08;
    public static final long COOLDOWN_MS = 8000;

    private final Listener listener;
    private double impactThreshold = DEFAULT_IMPACT_THRESHOLD_G;
    private double freeFallThreshold = DEFAULT_FREE_FALL_THRESHOLD_G;

    private State state = State.IDLE;
    private long freeFallStartTime = 0;
    private long freeFallEndTime = 0;
    private long impactTime = 0;
    private long stillnessStartTime = 0;
    private long lastConfirmedTime = -COOLDOWN_MS;

    // Running stats for stillness window
    private int stillnessSampleCount = 0;
    private double stillnessSumG = 0;
    private double stillnessSumSqG = 0;

    public FallDetector(Listener listener) {
        this.listener = listener;
    }

    public void setImpactThreshold(double impactThreshold) {
        if (impactThreshold > 1.5) {
            this.impactThreshold = impactThreshold;
        }
    }

    public double getImpactThreshold() {
        return impactThreshold;
    }

    public void setFreeFallThreshold(double freeFallThreshold) {
        this.freeFallThreshold = freeFallThreshold;
    }

    public double getFreeFallThreshold() {
        return freeFallThreshold;
    }

    public State getState() {
        return state;
    }

    public void reset() {
        state = State.IDLE;
        freeFallStartTime = 0;
        freeFallEndTime = 0;
        impactTime = 0;
        stillnessStartTime = 0;
        stillnessSampleCount = 0;
        stillnessSumG = 0;
        stillnessSumSqG = 0;
    }

    public void processSample(float x, float y, float z, long timestampMs) {
        double gForce = Math.sqrt(x * x + y * y + z * z) / 9.80665;
        processGForce(gForce, timestampMs);
    }

    public void processGForce(double gForce, long timestampMs) {
        if (timestampMs - lastConfirmedTime < COOLDOWN_MS) {
            return;
        }

        switch (state) {
            case IDLE:
                if (gForce < freeFallThreshold) {
                    state = State.FREE_FALL;
                    freeFallStartTime = timestampMs;
                }
                break;

            case FREE_FALL:
                if (gForce < freeFallThreshold) {
                    if (timestampMs - freeFallStartTime > MAX_FREE_FALL_MS) {
                        reset();
                        if (listener != null) listener.onFallDiscarded("Free-fall exceeded max duration");
                    }
                } else {
                    long duration = timestampMs - freeFallStartTime;
                    if (duration >= MIN_FREE_FALL_MS && duration <= MAX_FREE_FALL_MS) {
                        if (gForce >= impactThreshold) {
                            state = State.POST_IMPACT_SETTLE;
                            impactTime = timestampMs;
                            if (listener != null) listener.onFallCandidateDetected();
                        } else {
                            state = State.WAIT_IMPACT;
                            freeFallEndTime = timestampMs;
                        }
                    } else {
                        // Duration too short (noise glitch)
                        reset();
                    }
                }
                break;

            case WAIT_IMPACT:
                if (gForce >= impactThreshold) {
                    state = State.POST_IMPACT_SETTLE;
                    impactTime = timestampMs;
                    if (listener != null) listener.onFallCandidateDetected();
                } else if (timestampMs - freeFallEndTime > MAX_IMPACT_WAIT_MS) {
                    reset();
                    if (listener != null) listener.onFallDiscarded("No impact after free-fall");
                }
                break;

            case POST_IMPACT_SETTLE:
                if (timestampMs - impactTime >= POST_IMPACT_SETTLE_MS) {
                    state = State.POST_IMPACT_STILLNESS;
                    stillnessStartTime = timestampMs;
                    stillnessSampleCount = 0;
                    stillnessSumG = 0;
                    stillnessSumSqG = 0;
                }
                break;

            case POST_IMPACT_STILLNESS:
                if (gForce < STILLNESS_MIN_G || gForce > STILLNESS_MAX_G) {
                    reset();
                    if (listener != null) listener.onFallDiscarded("Movement detected during stillness check");
                    return;
                }

                stillnessSampleCount++;
                stillnessSumG += gForce;
                stillnessSumSqG += (gForce * gForce);

                if (stillnessSampleCount >= 10) {
                    double mean = stillnessSumG / stillnessSampleCount;
                    double variance = (stillnessSumSqG / stillnessSampleCount) - (mean * mean);
                    if (variance > STILLNESS_MAX_VARIANCE) {
                        reset();
                        if (listener != null) listener.onFallDiscarded("High variance during stillness check");
                        return;
                    }
                }

                if (timestampMs - stillnessStartTime >= STILLNESS_WINDOW_MS) {
                    lastConfirmedTime = timestampMs;
                    reset();
                    if (listener != null) listener.onFallConfirmed();
                }
                break;
        }
    }
}
