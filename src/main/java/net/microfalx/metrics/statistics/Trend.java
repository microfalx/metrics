package net.microfalx.metrics.statistics;

/**
 * An enum which tracks the trend of metrics, indicating whether recent values are improving,
 * worsening or holding steady, and how fast that movement is happening.
 */
public enum Trend {

    /**
     * The metrics are worsening quickly.
     */
    SHARPLY_WORSENING,

    /**
     * The metrics are worsening at a moderate pace.
     */
    WORSENING,

    /**
     * The metrics are worsening, but only slightly.
     */
    SLIGHTLY_WORSENING,

    /**
     * The metrics are stable with a small standard deviation.
     */
    STABLE,

    /**
     * The metrics are stable but are widely oscillating up and down, without a clear direction.
     */
    FLUCTUATING,

    /**
     * The metrics are improving, but only slightly.
     */
    SLIGHTLY_IMPROVING,

    /**
     * The metrics are improving at a moderate pace.
     */
    IMPROVING,

    /**
     * The metrics are improving quickly.
     */
    SHARPLY_IMPROVING;

    /**
     * Returns whether this trend represents metrics moving in a favorable direction.
     *
     * @return {@code true} if improving, at any intensity
     */
    public boolean isImproving() {
        return this == SLIGHTLY_IMPROVING || this == IMPROVING || this == SHARPLY_IMPROVING;
    }

    /**
     * Returns whether this trend represents metrics moving in an unfavorable direction.
     *
     * @return {@code true} if worsening, at any intensity
     */
    public boolean isWorsening() {
        return this == SLIGHTLY_WORSENING || this == WORSENING || this == SHARPLY_WORSENING;
    }

    /**
     * Returns whether this trend represents metrics without a clear improving or worsening direction.
     *
     * @return {@code true} if stable or fluctuating
     */
    public boolean isStable() {
        return this == STABLE || this == FLUCTUATING;
    }
}
