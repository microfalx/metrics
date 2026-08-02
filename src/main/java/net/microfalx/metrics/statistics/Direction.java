package net.microfalx.metrics.statistics;

/**
 * Indicates which direction of change is considered desirable for a metric, since a rising
 * value is not always a good sign (for example, higher throughput is good, but higher latency is not).
 */
public enum Direction {

    /**
     * Higher values are considered better (for example, throughput).
     */
    HIGHER_IS_BETTER,

    /**
     * Lower values are considered better (for example, latency, error rate).
     */
    LOWER_IS_BETTER
}
