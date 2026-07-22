package net.microfalx.metrics.statistics;

import org.apache.commons.math3.stat.descriptive.StatisticalSummary;

/**
 * A subclass of {@link org.apache.commons.math3.stat.descriptive.SummaryStatistics} which allows adding new values.
 */
public interface MutableStatisticalSummary extends StatisticalSummary {

    /**
     * Adds a new value to the statistics.
     *
     * @param value the value
     */
    void add(double value);
}
