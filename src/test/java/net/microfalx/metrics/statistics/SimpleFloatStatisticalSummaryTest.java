package net.microfalx.metrics.statistics;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SimpleFloatStatisticalSummaryTest {

    @Test
    void empty() {
        SimpleFloatStatisticalSummary summary = new SimpleFloatStatisticalSummary();
        assertEquals(0, summary.getN());
        assertEquals(0, summary.getSum());
        assertEquals(0, summary.getMean());
        assertTrue(Double.isNaN(summary.getVariance()));
        assertTrue(Double.isNaN(summary.getStandardDeviation()));
    }

    @Test
    void addValues() {
        SimpleFloatStatisticalSummary summary = new SimpleFloatStatisticalSummary();
        summary.add(1.0f);
        summary.add(5L);
        summary.add(3);
        summary.add(7.0);
        assertEquals(4, summary.getN());
        assertEquals(16.0, summary.getSum());
        assertEquals(4.0, summary.getMean());
        assertEquals(1.0, summary.getMin());
        assertEquals(7.0, summary.getMax());
    }

    @Test
    void addNegativeValues() {
        SimpleFloatStatisticalSummary summary = new SimpleFloatStatisticalSummary();
        summary.add(-3.0f);
        summary.add(-1.0f);
        assertEquals(-3.0, summary.getMin());
        assertEquals(-1.0, summary.getMax());
        assertEquals(-2.0, summary.getMean());
    }

    @Test
    void addDuration() {
        SimpleFloatStatisticalSummary summary = new SimpleFloatStatisticalSummary();
        summary.add(Duration.ofSeconds(2));
        assertEquals(2000.0, summary.getSum());
    }

    @Test
    void addSummary() {
        SimpleFloatStatisticalSummary first = new SimpleFloatStatisticalSummary();
        first.add(1.0f);
        first.add(2.0f);
        SummaryStatistics second = new SummaryStatistics();
        second.addValue(10.0);
        second.addValue(-4.0);
        first.add(second);
        assertEquals(4, first.getN());
        assertEquals(9.0, first.getSum());
        assertEquals(-4.0, first.getMin());
        assertEquals(10.0, first.getMax());
    }

    @Test
    void addEmptySummary() {
        SimpleFloatStatisticalSummary summary = new SimpleFloatStatisticalSummary();
        summary.add(1.0f);
        summary.add(new SimpleFloatStatisticalSummary());
        assertEquals(1, summary.getN());
        assertEquals(1.0, summary.getMin());
        assertEquals(1.0, summary.getMax());
    }

    @Test
    void addSummaryWithTooManyValues() {
        SimpleFloatStatisticalSummary summary = new SimpleFloatStatisticalSummary().setN(Integer.MAX_VALUE);
        SimpleDoubleStatisticalSummary other = new SimpleDoubleStatisticalSummary().setN(1).setSum(1).setMin(1).setMax(1);
        assertThrows(ArithmeticException.class, () -> summary.add(other));
    }

    @Test
    void copy() {
        SummaryStatistics statistics = new SummaryStatistics();
        statistics.addValue(2.0);
        statistics.addValue(4.0);
        SimpleFloatStatisticalSummary summary = new SimpleFloatStatisticalSummary(statistics);
        assertEquals(2, summary.getN());
        assertEquals(6.0, summary.getSum());
        assertEquals(2.0, summary.getMin());
        assertEquals(4.0, summary.getMax());
    }

    @Test
    void copyWithTooManyValues() {
        SimpleDoubleStatisticalSummary other = new SimpleDoubleStatisticalSummary().setN(Integer.MAX_VALUE + 1L);
        assertThrows(ArithmeticException.class, () -> new SimpleFloatStatisticalSummary(other));
    }

    @Test
    void setters() {
        SimpleFloatStatisticalSummary summary = new SimpleFloatStatisticalSummary().setN(4).setSum(10).setMin(1).setMax(7);
        assertEquals(4, summary.getN());
        assertEquals(2.5, summary.getMean());
        assertEquals(1, summary.getMin());
        assertEquals(7, summary.getMax());
    }

    @Test
    void keepsFloatPrecision() {
        SimpleFloatStatisticalSummary summary = new SimpleFloatStatisticalSummary();
        summary.add(0.1);
        assertEquals((double) 0.1f, summary.getSum());
        assertNotEquals(0.1, summary.getSum());
    }

    @Test
    void equalsAndHashCode() {
        SimpleFloatStatisticalSummary first = new SimpleFloatStatisticalSummary();
        first.add(1.0f);
        first.add(2.0f);
        SimpleFloatStatisticalSummary second = new SimpleFloatStatisticalSummary(first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        second.add(3.0f);
        assertNotEquals(first, second);
    }

    @Test
    void notEqualToDoubleVersion() {
        SimpleFloatStatisticalSummary first = new SimpleFloatStatisticalSummary();
        first.add(1.0f);
        assertNotEquals(first, new SimpleDoubleStatisticalSummary(first));
    }

    @Test
    void toStringContainsValues() {
        SimpleFloatStatisticalSummary summary = new SimpleFloatStatisticalSummary();
        summary.add(2.0f);
        assertEquals("SimpleFloatStatisticalSummary[n=1, sum=2.0, min=2.0, max=2.0]", summary.toString());
    }
}
