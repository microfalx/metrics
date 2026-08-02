package net.microfalx.metrics.statistics;

import org.apache.commons.math3.stat.descriptive.StatisticalSummary;

/**
 * A statistical summary that tracks the {@link Trend} of the metrics over time, indicating
 * whether recent values are improving, worsening or holding steady, and how quickly.
 */
public interface TrendStatisticalSummary extends StatisticalSummary {

    /**
     * Returns the direction of change that is considered desirable for these metrics
     * (for example, lower latency is better, while higher throughput is better).
     *
     * @return a non-null instance
     */
    Direction getDirection();

    /**
     * Returns the trend of the metrics.
     *
     * @return a non-null instance
     */
    Trend getTrend();
}
