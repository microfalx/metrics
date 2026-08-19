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

    /**
     * Returns a glyph from a standard (Unicode) font representing this trend.
     *
     * @return the glyph
     */
    public String toText() {
        switch (this) {
            case SHARPLY_WORSENING:
                return "⇊";
            case WORSENING:
                return "↓";
            case SLIGHTLY_WORSENING:
                return "↘";
            case STABLE:
                return "→";
            case FLUCTUATING:
                return "↕";
            case SLIGHTLY_IMPROVING:
                return "↗";
            case IMPROVING:
                return "↑";
            case SHARPLY_IMPROVING:
                return "⇈";
            default:
                throw new IllegalStateException("Unhandled trend: " + this);
        }
    }

    /**
     * Returns an HTML snippet with a Font Awesome icon representing this trend.
     *
     * @return the HTML snippet
     */
    public String toHtml() {
        switch (this) {
            case SHARPLY_WORSENING:
                return "<i class=\"fa-solid fa-angles-down text-red\"></i>";
            case WORSENING:
                return "<i class=\"fa-solid fa-arrow-trend-down text-red bg-opacity-75\"></i>";
            case SLIGHTLY_WORSENING:
                return "<i class=\"fa-solid fa-arrow-down-right text-red bg-opacity-50\"></i>";
            case STABLE:
                return "<i class=\"fa-solid fa-grip-lines text-green\"></i>";
            case FLUCTUATING:
                return "<i class=\"fa-solid fa-wave-pulse text-blue\"></i>";
            case SLIGHTLY_IMPROVING:
                return "<i class=\"fa-solid fa-arrow-up-right text-yellow bg-opacity-50\"></i>";
            case IMPROVING:
                return "<i class=\"fa-solid fa-arrow-trend-up text-yellow bg-opacity-75\"></i>";
            case SHARPLY_IMPROVING:
                return "<i class=\"fa-solid fa-angles-up text-yellow\"></i>";
            default:
                throw new IllegalStateException("Unhandled trend: " + this);
        }
    }
}
