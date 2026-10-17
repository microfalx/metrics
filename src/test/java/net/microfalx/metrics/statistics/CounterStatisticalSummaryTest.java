package net.microfalx.metrics.statistics;

import net.microfalx.metrics.Series;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class CounterStatisticalSummaryTest {

    private TestClock clock;

    @BeforeEach
    void setup() {
        clock = new TestClock();
    }

    @Test
    void invalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> create(Duration.ZERO, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> create(Duration.ofMinutes(1), Duration.ofSeconds(-1)));
        CounterStatisticalSummary summary = create(Duration.ofMinutes(1), Duration.ofMinutes(5));
        assertThrows(IllegalArgumentException.class, () -> summary.add(-1));
        assertThrows(IllegalArgumentException.class, () -> summary.add(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> summary.update(Double.NaN));
    }

    @Test
    void bucketSizeRounding() {
        assertEquals(Duration.ofMinutes(1), create(Duration.ofSeconds(1), Duration.ofHours(1)).getBucketSize());
        assertEquals(Duration.ofMinutes(1), create(Duration.ofSeconds(30), Duration.ofHours(1)).getBucketSize());
        assertEquals(Duration.ofMinutes(1), create(Duration.ofMinutes(1), Duration.ofHours(1)).getBucketSize());
        assertEquals(Duration.ofMinutes(5), create(Duration.ofSeconds(61), Duration.ofHours(1)).getBucketSize());
        assertEquals(Duration.ofMinutes(5), create(Duration.ofMinutes(5), Duration.ofHours(1)).getBucketSize());
        assertEquals(Duration.ofHours(1), create(Duration.ofMinutes(30), Duration.ofHours(1)).getBucketSize());
        assertEquals(Duration.ofHours(1), create(Duration.ofHours(3), Duration.ofHours(1)).getBucketSize());
    }

    @Test
    void bucketCount() {
        CounterStatisticalSummary summary = create(Duration.ofMinutes(1), Duration.ofMinutes(6));
        assertEquals(6, summary.getBucketCount());
        assertEquals(Duration.ofMinutes(6), summary.getRetention());
        assertEquals(7, create(Duration.ofMinutes(1), Duration.ofSeconds(366)).getBucketCount());
        assertEquals(1, create(Duration.ofMinutes(1), Duration.ofSeconds(1)).getBucketCount());
    }

    @Test
    void empty() {
        CounterStatisticalSummary summary = create(Duration.ofMinutes(1), Duration.ofMinutes(6));
        assertEquals(0, summary.getN());
        assertEquals(0, summary.getSum());
        assertEquals(0, summary.getCount());
        assertEquals(0, summary.getValues().length);
        assertTrue(Double.isNaN(summary.getMean()));
        assertTrue(Double.isNaN(summary.getVariance()));
        assertTrue(Double.isNaN(summary.getMin()));
        assertTrue(Double.isNaN(summary.getMax()));
        clock.advance(Duration.ofMinutes(30));
        assertEquals(0, summary.getN());
    }

    @Test
    void sameBucket() {
        CounterStatisticalSummary summary = create(Duration.ofMinutes(1), Duration.ofMinutes(6));
        summary.increment();
        summary.add(2);
        clock.advance(Duration.ofSeconds(54));
        summary.add(3L);
        assertEquals(1, summary.getN());
        assertArrayEquals(new float[]{6}, summary.getValues());
        assertEquals(6, summary.getCount());
    }

    @Test
    void bucketsAlignedToClock() {
        clock.advance(Duration.ofSeconds(54));
        CounterStatisticalSummary summary = create(Duration.ofMinutes(1), Duration.ofMinutes(6));
        summary.add(1);
        clock.advance(Duration.ofSeconds(6));
        summary.add(2);
        assertArrayEquals(new float[]{1, 2}, summary.getValues());
    }

    @Test
    void elapsedBucketsAreZero() {
        CounterStatisticalSummary summary = create(Duration.ofMinutes(1), Duration.ofMinutes(6));
        summary.add(5);
        clock.advance(Duration.ofMinutes(3));
        summary.add(2);
        assertArrayEquals(new float[]{5, 0, 0, 2}, summary.getValues());
        clock.advance(Duration.ofMinutes(1));
        assertArrayEquals(new float[]{5, 0, 0, 2, 0}, summary.getValues());
        assertEquals(5, summary.getN());
    }

    @Test
    void circularBuffer() {
        CounterStatisticalSummary summary = create(Duration.ofMinutes(1), Duration.ofMinutes(3));
        for (int i = 1; i <= 5; i++) {
            summary.add(i);
            clock.advance(Duration.ofMinutes(1));
        }
        clock.advance(Duration.ofMinutes(-1));
        assertArrayEquals(new float[]{3, 4, 5}, summary.getValues());
        assertEquals(12, summary.getCount());
        assertEquals(3, summary.getN());
    }

    @Test
    void gapLargerThanRetention() {
        CounterStatisticalSummary summary = create(Duration.ofMinutes(1), Duration.ofMinutes(3));
        summary.add(5);
        clock.advance(Duration.ofMinutes(60));
        assertArrayEquals(new float[]{0, 0, 0}, summary.getValues());
        summary.add(1);
        assertArrayEquals(new float[]{0, 0, 1}, summary.getValues());
    }

    @Test
    void valuesAndCountForDuration() {
        CounterStatisticalSummary summary = create(Duration.ofMinutes(1), Duration.ofMinutes(6));
        for (int i = 1; i <= 6; i++) {
            summary.add(i);
            if (i < 6) clock.advance(Duration.ofMinutes(1));
        }
        assertArrayEquals(new float[]{1, 2, 3, 4, 5, 6}, summary.getValues());
        assertArrayEquals(new float[]{5, 6}, summary.getValues(Duration.ofMinutes(2)));
        assertArrayEquals(new float[]{4, 5, 6}, summary.getValues(Duration.ofSeconds(126)));
        assertArrayEquals(new float[]{1, 2, 3, 4, 5, 6}, summary.getValues(Duration.ofHours(1)));
        assertArrayEquals(new float[]{6}, summary.getValues(1));
        assertEquals(0, summary.getValues(Duration.ZERO).length);
        assertEquals(11, summary.getCount(Duration.ofMinutes(2)));
        assertEquals(15, summary.getCount(Duration.ofSeconds(126)));
        assertEquals(21, summary.getCount(Duration.ofHours(1)));
        assertEquals(6, summary.getCount(1));
        assertEquals(0, summary.getCount(Duration.ZERO));
    }

    @Test
    void series() {
        CounterStatisticalSummary summary = create(Duration.ofMinutes(1), Duration.ofMinutes(6));
        assertTrue(summary.getSeries("empty").isEmpty());
        clock.advance(Duration.ofSeconds(30));
        long start = Duration.ofDays(1).toMillis();
        for (int i = 1; i <= 3; i++) {
            summary.add(i);
            if (i < 3) clock.advance(Duration.ofMinutes(1));
        }
        Series series = summary.getSeries("counter");
        assertEquals("counter", series.getName());
        assertEquals(Duration.ofMinutes(6), series.getRetention());
        assertEquals(3, series.getCount());
        for (int i = 0; i < 3; i++) {
            assertEquals(start + Duration.ofMinutes(i).toMillis(), series.get(i).getTimestamp());
            assertEquals(i + 1, series.get(i).getValue());
        }
        Series recent = summary.getSeries("counter", Duration.ofMinutes(2));
        assertEquals(2, recent.getCount());
        assertEquals(start + Duration.ofMinutes(1).toMillis(), recent.get(0).getTimestamp());
        assertEquals(2, recent.get(0).getValue());
        assertEquals(1, summary.getSeries("counter", 1).getCount());
    }

    @Test
    void statistics() {
        CounterStatisticalSummary summary = create(Duration.ofMinutes(1), Duration.ofMinutes(6));
        for (int value : new int[]{2, 4, 6}) {
            summary.add(value);
            clock.advance(Duration.ofMinutes(1));
        }
        clock.advance(Duration.ofMinutes(-1));
        assertEquals(3, summary.getN());
        assertEquals(12, summary.getSum());
        assertEquals(4, summary.getMean());
        assertEquals(2, summary.getMin());
        assertEquals(6, summary.getMax());
        assertEquals(4, summary.getVariance(), 1e-9);
        assertEquals(2, summary.getStandardDeviation(), 1e-9);
    }

    @Test
    void update() {
        CounterStatisticalSummary summary = create(Duration.ofMinutes(1), Duration.ofMinutes(6));
        summary.update(100);
        assertEquals(0, summary.getN());
        summary.update(105);
        clock.advance(Duration.ofMinutes(1));
        summary.update(112);
        clock.advance(Duration.ofMinutes(1));
        // counter reset
        summary.update(3);
        assertArrayEquals(new float[]{5, 7, 3}, summary.getValues());
    }

    @Test
    void clockMovesBackwards() {
        CounterStatisticalSummary summary = create(Duration.ofMinutes(1), Duration.ofMinutes(6));
        clock.advance(Duration.ofMinutes(1));
        summary.add(1);
        clock.advance(Duration.ofMinutes(-1));
        summary.add(2);
        assertArrayEquals(new float[]{3}, summary.getValues());
    }

    private CounterStatisticalSummary create(Duration bucketSize, Duration retention) {
        return new CounterStatisticalSummary(bucketSize, retention, clock);
    }

    private static class TestClock extends Clock {

        private long millis = Duration.ofDays(1).toMillis();

        void advance(Duration duration) {
            millis += duration.toMillis();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }
}
