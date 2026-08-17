package net.microfalx.metrics.statistics;

import org.apache.commons.math3.stat.descriptive.StatisticalSummary;

import java.io.Serializable;
import java.time.Duration;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * A subclass of {@link org.apache.commons.math3.stat.descriptive.SummaryStatistics} which allows adding new values.
 */
public interface MutableStatisticalSummary extends StatisticalSummary, Serializable {

    /**
     * Adds a new value to the statistics.
     *
     * @param value the value
     */
    void add(double value);

    /**
     * Adds a new value to the statistics.
     *
     * @param value the value
     */
    default void add(long value) {
        add((double) value);
    }

    /**
     * Adds a new value to the statistics.
     *
     * @param value the value
     */
    default void add(int value) {
        add((double) value);
    }

    /**
     * Adds a new duration to the statistics.
     *
     * @param value the value
     */
    default void add(Duration value) {
        requireNonNull(value);
        add(value.toMillis());
    }
}
