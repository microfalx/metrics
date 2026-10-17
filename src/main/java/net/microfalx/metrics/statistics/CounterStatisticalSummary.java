package net.microfalx.metrics.statistics;

import org.apache.commons.math3.stat.descriptive.StatisticalSummary;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.StringJoiner;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * A {@link StatisticalSummary} which tracks a monotonically increasing counter in time buckets.
 *
 * <p>Each bucket holds how much the counter increased during a fixed interval of time. The bucket size is rounded
 * up to one of the supported sizes (1m, 5m, 1h) and buckets are aligned to the wall clock (a 1m bucket
 * starts at the beginning of a minute).
 *
 * <p>Buckets are kept with float precision in a circular buffer, so no data is moved around when the buffer is full
 * (the oldest bucket is overwritten). Buckets which elapse without any change are recorded as zero, so the values
 * always describe consecutive intervals of time, the last one being the current (still in progress) bucket.
 *
 * <p>The statistics ({@link #getN()}, {@link #getMean()}, {@link #getMin()}, etc.) are calculated over the
 * buckets: the count is the number of buckets, the sum is the total increase of the counter and the mean is the
 * average increase per bucket.
 *
 * <p>Methods which cannot provide a value (statistics without any bucket) return {@link Float#NaN}.
 *
 * <p>This class is not thread-safe.
 */
public class CounterStatisticalSummary implements MutableStatisticalSummary {

    private static final long serialVersionUID = 5873296150146381034L;

    private static final Duration[] BUCKET_SIZES = {Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofHours(1)};

    private final Clock clock;
    private final long bucketSize;
    private final float[] values;
    private int head;
    private int n;
    private long lastBucket;
    private float lastCounter = Float.NaN;

    public CounterStatisticalSummary() {
        this(Duration.ofMinutes(1), Duration.ofHours(1));
    }

    public CounterStatisticalSummary(Duration bucketSize, Duration retention) {
        this(bucketSize, retention, Clock.systemUTC());
    }

    CounterStatisticalSummary(Duration bucketSize, Duration retention, Clock clock) {
        requireNonNull(retention);
        requireNonNull(clock);
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Retention must be positive: " + retention);
        }
        this.bucketSize = roundBucketSize(bucketSize).toMillis();
        this.values = new float[Math.max(1, bucketCount(retention.toMillis()))];
        this.clock = clock;
    }

    /**
     * Returns the size of a bucket, after rounding.
     *
     * @return a non-null duration
     */
    public Duration getBucketSize() {
        return Duration.ofMillis(bucketSize);
    }

    /**
     * Returns the maximum number of buckets kept by these statistics.
     *
     * @return a positive integer
     */
    public int getBucketCount() {
        return values.length;
    }

    /**
     * Returns the time interval covered by all the buckets.
     *
     * @return a non-null duration
     */
    public Duration getRetention() {
        return Duration.ofMillis(bucketSize * values.length);
    }

    /**
     * Increments the counter by one.
     */
    public void increment() {
        add(1f);
    }

    /**
     * Increments the counter.
     *
     * @param value the increment, must be zero or positive
     */
    public void add(float value) {
        if (!(value >= 0)) throw new IllegalArgumentException("Increment must be zero or positive: " + value);
        advance();
        values[lastIndex()] += value;
    }

    /**
     * Increments the counter.
     * <p>
     * The value is narrowed to a float.
     *
     * @param value the increment, must be zero or positive
     */
    @Override
    public void add(double value) {
        add((float) value);
    }

    /**
     * Records the current value of an external counter (a total which only increases over time).
     * <p>
     * The difference from the previously recorded value is added to the current bucket. The first recorded value
     * only establishes a reference and if the counter goes down, it is considered to be reset (it restarted
     * from zero).
     *
     * @param counter the current value of the counter
     */
    public void update(float counter) {
        if (Float.isNaN(counter)) throw new IllegalArgumentException("Counter cannot be NaN");
        advance();
        if (!Float.isNaN(lastCounter)) {
            values[lastIndex()] += counter >= lastCounter ? counter - lastCounter : counter;
        }
        lastCounter = counter;
    }

    /**
     * Records the current value of an external counter (a total which only increases over time).
     * <p>
     * The value is narrowed to a float.
     *
     * @param counter the current value of the counter
     * @see #update(float)
     */
    public void update(double counter) {
        update((float) counter);
    }

    /**
     * Returns the values of all the buckets, from the oldest to the most recent (current) bucket.
     *
     * @return a non-null instance
     */
    public float[] getValues() {
        return getValues(Integer.MAX_VALUE);
    }

    /**
     * Returns the values of the most recent buckets, from the oldest to the most recent (current) bucket.
     *
     * @param limit the maximum number of buckets to return
     * @return a non-null instance
     */
    public float[] getValues(int limit) {
        advance();
        int count = Math.max(0, Math.min(n, limit));
        float[] result = new float[count];
        int capacity = values.length;
        int start = (head + n - count) % capacity;
        int firstChunk = Math.min(count, capacity - start);
        System.arraycopy(values, start, result, 0, firstChunk);
        System.arraycopy(values, 0, result, firstChunk, count - firstChunk);
        return result;
    }

    /**
     * Returns the values of the buckets covering the last interval of time, from the oldest to the most recent
     * (current) bucket.
     * <p>
     * The interval is converted in a number of buckets, rounded up, and the current bucket counts as one of them.
     *
     * @param duration the interval of time
     * @return a non-null instance
     */
    public float[] getValues(Duration duration) {
        return getValues(bucketCount(duration));
    }

    /**
     * Returns the total increase of the counter, across all buckets.
     *
     * @return a positive number
     */
    public float getCount() {
        return getCount(Integer.MAX_VALUE);
    }

    /**
     * Returns the total increase of the counter, across the most recent buckets.
     *
     * @param limit the maximum number of buckets
     * @return a positive number
     */
    public float getCount(int limit) {
        advance();
        int count = Math.max(0, Math.min(n, limit));
        float sum = 0;
        int capacity = values.length;
        for (int i = 0, index = (head + n - count) % capacity; i < count; i++) {
            sum += values[index];
            if (++index == capacity) index = 0;
        }
        return sum;
    }

    /**
     * Returns the total increase of the counter, across the buckets covering the last interval of time.
     *
     * @param duration the interval of time
     * @return a positive number
     * @see #getValues(Duration)
     */
    public float getCount(Duration duration) {
        return getCount(bucketCount(duration));
    }

    @Override
    public double getMean() {
        advance();
        return n == 0 ? Float.NaN : sum() / n;
    }

    @Override
    public double getVariance() {
        advance();
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
        advance();
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
        advance();
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
        advance();
        return n;
    }

    @Override
    public double getSum() {
        return getCount();
    }

    /**
     * Moves the most recent bucket to the current time, recording elapsed buckets as zero.
     * <p>
     * Nothing happens until the first change of the counter, and if the clock moves backwards, changes keep being
     * added to the most recent bucket.
     */
    private void advance() {
        long bucket = clock.millis() / bucketSize;
        if (n == 0) {
            lastBucket = bucket;
            return;
        }
        if (bucket <= lastBucket) return;
        long gap = bucket - lastBucket;
        int capacity = values.length;
        if (gap >= capacity) {
            Arrays.fill(values, 0);
            head = 0;
            n = capacity;
        } else {
            for (int i = 0; i < gap; i++) {
                if (n < capacity) {
                    values[(head + n) % capacity] = 0;
                    n++;
                } else {
                    values[head] = 0;
                    if (++head == capacity) head = 0;
                }
            }
        }
        lastBucket = bucket;
    }

    private int lastIndex() {
        if (n == 0) {
            values[0] = 0;
            head = 0;
            n = 1;
        }
        return (head + n - 1) % values.length;
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

    private int bucketCount(Duration duration) {
        requireNonNull(duration);
        if (duration.isNegative() || duration.isZero()) return 0;
        return bucketCount(duration.toMillis());
    }

    private int bucketCount(long millis) {
        return (int) Math.min(Integer.MAX_VALUE, (millis + bucketSize - 1) / bucketSize);
    }

    private static Duration roundBucketSize(Duration bucketSize) {
        requireNonNull(bucketSize);
        if (bucketSize.isNegative() || bucketSize.isZero()) {
            throw new IllegalArgumentException("Bucket size must be positive: " + bucketSize);
        }
        for (Duration size : BUCKET_SIZES) {
            if (bucketSize.compareTo(size) <= 0) return size;
        }
        return BUCKET_SIZES[BUCKET_SIZES.length - 1];
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", CounterStatisticalSummary.class.getSimpleName() + "[", "]")
                .add("bucketSize=" + getBucketSize())
                .add("bucketCount=" + values.length)
                .add("n=" + getN())
                .add("count=" + getCount())
                .toString();
    }
}
