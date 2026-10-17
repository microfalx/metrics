package net.microfalx.metrics.statistics;

import org.apache.commons.math3.stat.descriptive.StatisticalSummary;

import java.util.Objects;
import java.util.StringJoiner;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * An implementation of a {@link StatisticalSummary} which only cares about average, min and max.
 *
 * <p>Values are kept with float precision and the count as an int, which halves the memory footprint compared
 * with {@link SimpleDoubleStatisticalSummary}. Use it when many summaries are kept around and the highest precision
 * is not needed.
 */
public class SimpleFloatStatisticalSummary implements MutableStatisticalSummary {

    private static final long serialVersionUID = 2817436021845839271L;

    private int n = 0;
    private float sum;
    private float min = Float.NaN;
    private float max = Float.NaN;

    public SimpleFloatStatisticalSummary() {
    }

    public SimpleFloatStatisticalSummary(StatisticalSummary summary) {
        requireNonNull(summary);
        this.n = Math.toIntExact(summary.getN());
        this.sum = (float) summary.getSum();
        this.max = (float) summary.getMax();
        this.min = (float) summary.getMin();
    }

    @Override
    public double getMean() {
        return n == 0 ? Double.NaN : (double) sum / n;
    }

    @Override
    public double getVariance() {
        return Float.NaN;
    }

    @Override
    public double getStandardDeviation() {
        return Float.NaN;
    }

    @Override
    public double getMax() {
        return max;
    }

    public SimpleFloatStatisticalSummary setMax(float max) {
        this.max = max;
        return this;
    }

    @Override
    public double getMin() {
        return min;
    }

    public SimpleFloatStatisticalSummary setMin(float min) {
        this.min = min;
        return this;
    }

    @Override
    public long getN() {
        return n;
    }

    public SimpleFloatStatisticalSummary setN(int n) {
        this.n = n;
        return this;
    }

    @Override
    public double getSum() {
        return sum;
    }

    public SimpleFloatStatisticalSummary setSum(float sum) {
        this.sum = sum;
        return this;
    }

    /**
     * Adds a new value to the statistics.
     *
     * @param value the value
     */
    public void add(float value) {
        this.sum += value;
        this.n++;
        this.min = Float.isNaN(this.min) ? value : Math.min(this.min, value);
        this.max = Float.isNaN(this.max) ? value : Math.max(this.max, value);
    }

    /**
     * Adds a new value to the statistics.
     * <p>
     * The value is narrowed to a float.
     *
     * @param value the value
     */
    @Override
    public void add(double value) {
        add((float) value);
    }

    /**
     * Updates the summary by adding all the relevant values from another summary (count, sum, min and max).
     *
     * @param summary the value
     */
    public void add(StatisticalSummary summary) {
        if (summary.getN() == 0) return;
        this.sum += (float) summary.getSum();
        this.n = Math.toIntExact(this.n + summary.getN());
        float otherMin = (float) summary.getMin();
        float otherMax = (float) summary.getMax();
        this.min = Float.isNaN(this.min) ? otherMin : Math.min(this.min, otherMin);
        this.max = Float.isNaN(this.max) ? otherMax : Math.max(this.max, otherMax);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleFloatStatisticalSummary that = (SimpleFloatStatisticalSummary) o;
        return n == that.n && Float.compare(that.sum, sum) == 0 && Float.compare(that.min, min) == 0
                && Float.compare(that.max, max) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(n, sum, min, max);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SimpleFloatStatisticalSummary.class.getSimpleName() + "[", "]")
                .add("n=" + n)
                .add("sum=" + sum)
                .add("min=" + min)
                .add("max=" + max)
                .toString();
    }
}
