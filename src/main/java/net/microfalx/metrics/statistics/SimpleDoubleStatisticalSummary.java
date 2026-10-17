package net.microfalx.metrics.statistics;

import org.apache.commons.math3.stat.descriptive.StatisticalSummary;

import java.util.Objects;
import java.util.StringJoiner;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * An implementation of a {@link StatisticalSummary} which only cares about average, min and max.
 *
 * <p>Values are kept with double precision and the count as a long. Use {@link SimpleFloatStatisticalSummary}
 * when a smaller memory footprint matters more than precision.
 * <p>
 */
public class SimpleDoubleStatisticalSummary implements MutableStatisticalSummary {

    private static final long serialVersionUID = -7796746446080742315L;

    private long n = 0;
    private double sum;
    private double min = Double.NaN;
    private double max = Double.NaN;

    public SimpleDoubleStatisticalSummary() {
    }

    public SimpleDoubleStatisticalSummary(StatisticalSummary summary) {
        requireNonNull(summary);
        this.n = summary.getN();
        this.sum = summary.getSum();
        this.max = summary.getMax();
        this.min = summary.getMin();
    }

    @Override
    public double getMean() {
        return n == 0 ? Double.NaN : sum / (double) n;
    }

    @Override
    public double getVariance() {
        return Double.NaN;
    }

    @Override
    public double getStandardDeviation() {
        return Double.NaN;
    }

    @Override
    public double getMax() {
        return max;
    }

    public SimpleDoubleStatisticalSummary setMax(double max) {
        this.max = max;
        return this;
    }

    @Override
    public double getMin() {
        return min;
    }

    public SimpleDoubleStatisticalSummary setMin(double min) {
        this.min = min;
        return this;
    }

    @Override
    public long getN() {
        return n;
    }

    public SimpleDoubleStatisticalSummary setN(long n) {
        this.n = n;
        return this;
    }

    @Override
    public double getSum() {
        return sum;
    }

    public SimpleDoubleStatisticalSummary setSum(double sum) {
        this.sum = sum;
        return this;
    }

    /**
     * Adds a new value to the statistics.
     *
     * @param value the value
     */
    public void add(double value) {
        this.sum += value;
        this.n++;
        this.min = Double.isNaN(this.min) ? value : Math.min(this.min, value);
        this.max = Double.isNaN(this.max) ? value : Math.max(this.max, value);
    }

    /**
     * Updates the summary by adding all the relevant values from another summary (count, sum, min and max).
     *
     * @param summary the value
     */
    public void add(StatisticalSummary summary) {
        if (summary.getN() == 0) return;
        this.sum += summary.getSum();
        this.n += summary.getN();
        this.min = Double.isNaN(this.min) ? summary.getMin() : Math.min(this.min, summary.getMin());
        this.max = Double.isNaN(this.max) ? summary.getMax() : Math.max(this.max, summary.getMax());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleDoubleStatisticalSummary that = (SimpleDoubleStatisticalSummary) o;
        return n == that.n && Double.compare(that.sum, sum) == 0 && Double.compare(that.min, min) == 0
                && Double.compare(that.max, max) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(n, sum, min, max);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SimpleDoubleStatisticalSummary.class.getSimpleName() + "[", "]")
                .add("n=" + n)
                .add("sum=" + sum)
                .add("min=" + min)
                .add("max=" + max)
                .toString();
    }
}
