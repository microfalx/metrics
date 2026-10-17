package net.microfalx.metrics.statistics;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SimpleFloatDescriptiveStatisticsTest {

    @Test
    void invalidWindowSize() {
        assertThrows(IllegalArgumentException.class, () -> new SimpleFloatDescriptiveStatistics(0));
        assertThrows(IllegalArgumentException.class, () -> new SimpleFloatDescriptiveStatistics(5).setWindowSize(-1));
    }

    @Test
    void empty() {
        SimpleFloatDescriptiveStatistics statistics = new SimpleFloatDescriptiveStatistics(5);
        assertEquals(0, statistics.getN());
        assertEquals(0, statistics.getSum());
        assertTrue(Float.isNaN((float) statistics.getMean()));
        assertTrue(Float.isNaN((float) statistics.getMin()));
        assertTrue(Float.isNaN((float) statistics.getMax()));
        assertEquals(0, statistics.getValues().length);
    }

    @Test
    void variance() {
        SimpleFloatDescriptiveStatistics statistics = new SimpleFloatDescriptiveStatistics(5);
        assertTrue(Float.isNaN((float) statistics.getVariance()));
        assertTrue(Float.isNaN((float) statistics.getStandardDeviation()));
        statistics.add(3);
        assertEquals(0, statistics.getVariance());
        assertEquals(0, statistics.getStandardDeviation());
        for (int value : new int[]{1, 2, 3, 4, 5, 6}) statistics.add(value);
        // window keeps 2..6
        assertEquals(2.5, statistics.getVariance(), 1e-6);
        assertEquals(Math.sqrt(2.5), statistics.getStandardDeviation(), 1e-6);
    }

    @Test
    void addValues() {
        SimpleFloatDescriptiveStatistics statistics = new SimpleFloatDescriptiveStatistics(10);
        statistics.add(1.0f);
        statistics.add(5L);
        statistics.add(3);
        statistics.add(7.0);
        assertEquals(4, statistics.getN());
        assertEquals(16.0, statistics.getSum());
        assertEquals(4.0, statistics.getMean());
        assertEquals(1.0, statistics.getMin());
        assertEquals(7.0, statistics.getMax());
        assertArrayEquals(new float[]{1, 5, 3, 7}, statistics.getValues());
    }

    @Test
    void windowDiscardsOldestValues() {
        SimpleFloatDescriptiveStatistics statistics = new SimpleFloatDescriptiveStatistics(3);
        for (int i = 1; i <= 7; i++) statistics.add(i);
        assertEquals(3, statistics.getN());
        assertArrayEquals(new float[]{5, 6, 7}, statistics.getValues());
        assertEquals(5, statistics.getValue(0));
        assertEquals(7, statistics.getValue(2));
        assertEquals(5, statistics.getMin());
        assertEquals(7, statistics.getMax());
        assertEquals(6, statistics.getMean());
        assertEquals(18, statistics.getSum());
        assertThrows(IndexOutOfBoundsException.class, () -> statistics.getValue(3));
    }

    @Test
    void statisticsMatchWindowAfterEvictions() {
        Random random = new Random(42);
        SimpleFloatDescriptiveStatistics statistics = new SimpleFloatDescriptiveStatistics(7);
        for (int i = 0; i < 500; i++) {
            statistics.add((float) random.nextInt(100));
            float[] values = statistics.getValues();
            float sum = 0, min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            for (float value : values) {
                sum += value;
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            assertEquals(sum, statistics.getSum());
            assertEquals(min, statistics.getMin());
            assertEquals(max, statistics.getMax());
        }
    }

    @Test
    void getValuesWithLimit() {
        SimpleFloatDescriptiveStatistics statistics = new SimpleFloatDescriptiveStatistics(4);
        for (int i = 1; i <= 6; i++) statistics.add(i);
        assertArrayEquals(new float[]{5, 6}, statistics.getValues(2));
        assertArrayEquals(new float[]{3, 4, 5, 6}, statistics.getValues(10));
        assertArrayEquals(new float[0], statistics.getValues(0));
    }

    @Test
    void summaryIsRecalculatedAfterAdd() {
        SimpleFloatDescriptiveStatistics statistics = new SimpleFloatDescriptiveStatistics(2);
        statistics.add(1);
        statistics.add(2);
        assertEquals(2, statistics.getMax());
        assertEquals(1.5, statistics.getMean());
        statistics.add(10);
        assertEquals(10, statistics.getMax());
        assertEquals(2, statistics.getMin());
        assertEquals(6, statistics.getMean());
    }

    @Test
    void shrinkWindowKeepsLatestValues() {
        SimpleFloatDescriptiveStatistics statistics = new SimpleFloatDescriptiveStatistics(4);
        for (int i = 1; i <= 6; i++) statistics.add(i);
        statistics.setWindowSize(2);
        assertEquals(2, statistics.getWindowSize());
        assertArrayEquals(new float[]{5, 6}, statistics.getValues());
        assertEquals(5.5, statistics.getMean());
        statistics.add(7);
        assertArrayEquals(new float[]{6, 7}, statistics.getValues());
    }

    @Test
    void growWindowKeepsAllValues() {
        SimpleFloatDescriptiveStatistics statistics = new SimpleFloatDescriptiveStatistics(3);
        for (int i = 1; i <= 5; i++) statistics.add(i);
        statistics.setWindowSize(5);
        assertArrayEquals(new float[]{3, 4, 5}, statistics.getValues());
        statistics.add(6);
        statistics.add(7);
        statistics.add(8);
        assertArrayEquals(new float[]{4, 5, 6, 7, 8}, statistics.getValues());
    }
}
