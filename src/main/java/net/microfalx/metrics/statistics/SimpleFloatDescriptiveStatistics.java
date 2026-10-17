package net.microfalx.metrics.statistics;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.StringJoiner;

/**
 * A lightweight replacement for {@link DescriptiveStatistics} which keeps a rolling window of the most recent values
 * and only cares about average, min, max and standard deviation.
 *
 * <p>Values are kept with float precision in a circular buffer, so no data is moved around when the window is
 * full (the oldest value is overwritten). All statistics are calculated on the spot, which is fast since the
 * window is usually small.
 *
 * <p>Methods which cannot provide a value (statistics of an empty window) return {@link Float#NaN}.
 *
 * <p>This class is not thread-safe.
 */
public class SimpleFloatDescriptiveStatistics implements MutableStatisticalSummary {

    private static final long serialVersionUID = -4213787052237651129L;

    private float[] values;
    private int head;
    private int n;

    /**
     * A shared object to synchronize on when using multiple instances of this class in the same thread.
     */
    public static final Object LOCK = new Object();

    public SimpleFloatDescriptiveStatistics(int windowSize) {
        this.values = new float[requireWindowSize(windowSize)];
    }

    /**
     * Returns the maximum number of values kept by these statistics.
     *
     * @return a positive integer
     */
    public int getWindowSize() {
        return values.length;
    }

    /**
     * Changes the maximum number of values kept by these statistics.
     * <p>
     * If the window shrinks below the number of values available, the oldest values are discarded.
     *
     * @param windowSize the new window size
     * @return self
     */
    public SimpleFloatDescriptiveStatistics setWindowSize(int windowSize) {
        requireWindowSize(windowSize);
        if (windowSize == values.length) return this;
        float[] newValues = new float[windowSize];
        int count = copyLatest(newValues, windowSize);
        this.values = newValues;
        this.head = 0;
        this.n = count;
        return this;
    }

    /**
     * Returns the value at a given position in the window, where zero is the oldest value.
     *
     * @param index the index of the value
     * @return the value
     */
    public float getValue(int index) {
        if (index < 0 || index >= n) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + n);
        return values[(head + index) % values.length];
    }

    /**
     * Returns the values in the window, from the oldest to the most recent.
     *
     * @return a non-null instance
     */
    public float[] getValues() {
        return getValues(n);
    }

    /**
     * Returns the most recent values in the window, from the oldest to the most recent.
     *
     * @param limit the maximum number of values to return
     * @return a non-null instance
     */
    public float[] getValues(int limit) {
        float[] result = new float[Math.max(0, Math.min(n, limit))];
        copyLatest(result, result.length);
        return result;
    }

    @Override
    public double getMean() {
        return n == 0 ? Float.NaN : sum() / n;
    }

    @Override
    public double getVariance() {
        if (n == 0) return Float.NaN;
        if (n == 1) return 0;
        float mean = sum() / n;
        float squares = 0;
        int capacity = values.length;
        for (int i = 0, index = head; i < n; i++) {
            float deviation = values[index] - mean;
            squares += deviation * deviation;
            if (++index == capacity) index = 0;
        }
        return squares / (n - 1);
    }

    @Override
    public double getStandardDeviation() {
        return (float) Math.sqrt(getVariance());
    }

    @Override
    public double getMax() {
        if (n == 0) return Float.NaN;
        float max = -Float.MAX_VALUE;
        int capacity = values.length;
        for (int i = 0, index = head; i < n; i++) {
            max = Math.max(max, values[index]);
            if (++index == capacity) index = 0;
        }
        return max;
    }

    @Override
    public double getMin() {
        if (n == 0) return Float.NaN;
        float min = Float.MAX_VALUE;
        int capacity = values.length;
        for (int i = 0, index = head; i < n; i++) {
            min = Math.min(min, values[index]);
            if (++index == capacity) index = 0;
        }
        return min;
    }

    @Override
    public long getN() {
        return n;
    }

    @Override
    public double getSum() {
        return sum();
    }

    /**
     * Adds a new value to the statistics.
     * <p>
     * If the window is full, the oldest value is discarded.
     *
     * @param value the value
     */
    public void add(float value) {
        int capacity = values.length;
        if (n < capacity) {
            values[(head + n) % capacity] = value;
            n++;
        } else {
            values[head] = value;
            if (++head == capacity) head = 0;
        }
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

    private float sum() {
        float sum = 0;
        int capacity = values.length;
        for (int i = 0, index = head; i < n; i++) {
            sum += values[index];
            if (++index == capacity) index = 0;
        }
        return sum;
    }

    private int copyLatest(float[] target, int limit) {
        int count = Math.min(n, limit);
        int capacity = values.length;
        int start = (head + n - count) % capacity;
        int firstChunk = Math.min(count, capacity - start);
        System.arraycopy(values, start, target, 0, firstChunk);
        System.arraycopy(values, 0, target, firstChunk, count - firstChunk);
        return count;
    }

    private static int requireWindowSize(int windowSize) {
        if (windowSize <= 0) throw new IllegalArgumentException("Window size must be positive: " + windowSize);
        return windowSize;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SimpleFloatDescriptiveStatistics.class.getSimpleName() + "[", "]")
                .add("windowSize=" + values.length)
                .add("n=" + n)
                .add("mean=" + getMean())
                .add("min=" + getMin())
                .add("max=" + getMax())
                .toString();
    }
}
