package net.microfalx.metrics.statistics;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.time.Duration;

import static java.lang.System.currentTimeMillis;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.TimeUtils.ONE_MINUTE;
import static net.microfalx.lang.TimeUtils.millisSince;

/**
 * A {@link org.apache.commons.math3.stat.descriptive.StatisticalSummary} implementation which calculates
 * a window size to be used with a {@link DescriptiveStatistics} to track the averages, and also tracks
 * the {@link Trend} of the metrics based on the slope of a linear regression over the current window.
 */
public class TimeWindowStatisticalSummary implements MutableStatisticalSummary, TrendStatisticalSummary {

    private final static int WINDOW_ROUNDING = 5;
    private final static int MINIMUM_WINDOW = WINDOW_ROUNDING * 2;

    private final static int MINIMUM_TREND_SAMPLES = 4;
    private final static double SLOPE_EPSILON = 1e-9;

    /**
     * Below this relative slope (change per sample, as a fraction of the mean), the metrics are
     * considered to have no clear direction (either {@link Trend#STABLE} or {@link Trend#FLUCTUATING}).
     */
    private final static double STABLE_SLOPE_THRESHOLD = 0.005;

    /**
     * Below this relative slope, the trend is reported as slight ({@link Trend#SLIGHTLY_IMPROVING}
     * or {@link Trend#SLIGHTLY_WORSENING}).
     */
    private final static double SLIGHT_SLOPE_THRESHOLD = 0.02;

    /**
     * At or above this relative slope, the trend is reported as sharp ({@link Trend#SHARPLY_IMPROVING}
     * or {@link Trend#SHARPLY_WORSENING}).
     */
    private final static double SHARP_SLOPE_THRESHOLD = 0.05;

    /**
     * Above this coefficient of variation (standard deviation over mean), a trend with no clear
     * slope is reported as {@link Trend#FLUCTUATING} instead of {@link Trend#STABLE}.
     */
    private final static double FLUCTUATION_THRESHOLD = 0.15;

    private final DescriptiveStatistics statistics = new DescriptiveStatistics();
    private final SimpleStatisticalSummary windowSummary = new SimpleStatisticalSummary();
    private volatile Duration interval;
    private volatile long refreshInterval = ONE_MINUTE;
    private volatile Direction direction = Direction.HIGHER_IS_BETTER;

    private volatile long lastUpdate = -1;
    private volatile long lastWindowUpdate = currentTimeMillis();

    public TimeWindowStatisticalSummary(Duration interval) {
        requireNonNull(interval);
        statistics.setWindowSize(MINIMUM_WINDOW);
        this.interval = interval;
        updateWindow();
    }

    public TimeWindowStatisticalSummary setInterval(Duration interval) {
        requireNonNull(interval);
        this.interval = interval;
        return this;
    }

    public TimeWindowStatisticalSummary setRefreshInterval(Duration refreshInterval) {
        requireNonNull(refreshInterval);
        this.refreshInterval = refreshInterval.toMillis();
        return this;
    }

    public TimeWindowStatisticalSummary setDirection(Direction direction) {
        requireNonNull(direction);
        this.direction = direction;
        return this;
    }

    @Override
    public Direction getDirection() {
        return direction;
    }

    @Override
    public Trend getTrend() {
        double[] values = statistics.getValues();
        if (values.length < MINIMUM_TREND_SAMPLES) return Trend.STABLE;
        SimpleRegression regression = new SimpleRegression();
        for (int index = 0; index < values.length; index++) {
            regression.addData(index, values[index]);
        }
        double mean = statistics.getMean();
        double base = Math.abs(mean) > SLOPE_EPSILON ? Math.abs(mean) : 1d;
        double relativeSlope = regression.getSlope() / base;
        double magnitude = Math.abs(relativeSlope);
        if (magnitude < STABLE_SLOPE_THRESHOLD) {
            double coefficientOfVariation = statistics.getStandardDeviation() / base;
            return coefficientOfVariation > FLUCTUATION_THRESHOLD ? Trend.FLUCTUATING : Trend.STABLE;
        }
        boolean rising = relativeSlope > 0;
        boolean improving = direction == Direction.HIGHER_IS_BETTER ? rising : !rising;
        if (magnitude >= SHARP_SLOPE_THRESHOLD) {
            return improving ? Trend.SHARPLY_IMPROVING : Trend.SHARPLY_WORSENING;
        } else if (magnitude >= SLIGHT_SLOPE_THRESHOLD) {
            return improving ? Trend.IMPROVING : Trend.WORSENING;
        } else {
            return improving ? Trend.SLIGHTLY_IMPROVING : Trend.SLIGHTLY_WORSENING;
        }
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
