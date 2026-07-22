package net.microfalx.metrics.statistics;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.time.Duration;

import static java.lang.System.currentTimeMillis;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.TimeUtils.ONE_MINUTE;
import static net.microfalx.lang.TimeUtils.millisSince;

/**
 * A {@link org.apache.commons.math3.stat.descriptive.StatisticalSummary} implementation which calculates
 * a window size to be used with a {@link DescriptiveStatistics} to track the averages.
 */
public class TimeWindowStatisticalSummary implements MutableStatisticalSummary {

    private final static int WINDOW_ROUNDING = 5;
    private final static int MINIMUM_WINDOW = WINDOW_ROUNDING * 2;

    private final DescriptiveStatistics statistics = new DescriptiveStatistics();
    private final SimpleStatisticalSummary windowSummary = new SimpleStatisticalSummary();
    private final Duration interval;
    private long refreshInterval = ONE_MINUTE;

    private volatile long lastUpdate = -1;
    private volatile long lastWindowUpdate = currentTimeMillis();

    public TimeWindowStatisticalSummary(Duration interval) {
        requireNonNull(interval);
        statistics.setWindowSize(MINIMUM_WINDOW);
        this.interval = interval;
        updateWindow();
    }

    public TimeWindowStatisticalSummary setRefreshInterval(Duration refreshInterval) {
        requireNonNull(refreshInterval);
        this.refreshInterval = refreshInterval.toMillis();
        return this;
    }

    @Override
    public double getMean() {
        return statistics.getMean();
    }

    @Override
    public double getVariance() {
        return statistics.getVariance();
    }

    @Override
    public double getStandardDeviation() {
        return statistics.getStandardDeviation();
    }

    @Override
    public double getMax() {
        return statistics.getMax();
    }

    @Override
    public double getMin() {
        return statistics.getMin();
    }

    @Override
    public long getN() {
        return statistics.getN();
    }

    @Override
    public double getSum() {
        return statistics.getSum();
    }

    @Override
    public void add(double value) {
        statistics.addValue(value);
        long now = currentTimeMillis();
        if (lastUpdate > 0) {
            long interval = now - lastUpdate;
            windowSummary.add(interval);
        }
        lastUpdate = now;
        if (millisSince(lastWindowUpdate) > refreshInterval) updateWindow();
    }

    private void updateWindow() {
        long averageUpdateInterval = (long) windowSummary.getMean();
        if (averageUpdateInterval == 0) return;
        int currentWindow = (int) (interval.toMillis() / averageUpdateInterval);
        currentWindow = Math.min(MINIMUM_WINDOW, (currentWindow / WINDOW_ROUNDING) * WINDOW_ROUNDING);
        if (currentWindow != statistics.getWindowSize()) statistics.setWindowSize(currentWindow);
        lastWindowUpdate = currentTimeMillis();
    }
}
