package net.microfalx.metrics.statistics;

import org.apache.commons.math3.stat.descriptive.StatisticalSummary;

/**
 * A statistical summary that tracks the {@link Trend} of the metrics over time, indicating
 * whether recent values are improving, worsening or holding steady, and how quickly.
 */
public interface TrendStatisticalSummary extends StatisticalSummary {

    /**
     * Creates the default implementation of the {@link TrendStatisticalSummary}.
     *
     * @return a non-null instance
     */
    static TrendStatisticalSummary create() {
        return new TimeWindowStatisticalSummary();
    }

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

    /**
     * Returns the values used to calculate the trend.
     *
     * @return a non-null instance
     */
    double[] getValues();
}
