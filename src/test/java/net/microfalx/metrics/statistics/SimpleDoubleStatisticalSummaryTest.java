package net.microfalx.metrics.statistics;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SimpleDoubleStatisticalSummaryTest {

    @Test
    void empty() {
        SimpleDoubleStatisticalSummary summary = new SimpleDoubleStatisticalSummary();
        assertEquals(0, summary.getN());
        assertEquals(0, summary.getSum());
        assertEquals(0, summary.getMean());
        assertTrue(Double.isNaN(summary.getVariance()));
        assertTrue(Double.isNaN(summary.getStandardDeviation()));
    }

    @Test
    void addValues() {
        SimpleDoubleStatisticalSummary summary = new SimpleDoubleStatisticalSummary();
        summary.add(1.0);
        summary.add(5L);
        summary.add(3);
        assertEquals(3, summary.getN());
        assertEquals(9.0, summary.getSum());
        assertEquals(3.0, summary.getMean());
        assertEquals(1.0, summary.getMin());
        assertEquals(5.0, summary.getMax());
    }

    @Test
    void addNegativeValues() {
        SimpleDoubleStatisticalSummary summary = new SimpleDoubleStatisticalSummary();
        summary.add(-3.0);
        summary.add(-1.0);
        assertEquals(-3.0, summary.getMin());
        assertEquals(-1.0, summary.getMax());
        assertEquals(-2.0, summary.getMean());
    }

    @Test
    void addDuration() {
        SimpleDoubleStatisticalSummary summary = new SimpleDoubleStatisticalSummary();
        summary.add(Duration.ofSeconds(2));
        assertEquals(2000.0, summary.getSum());
    }

    @Test
    void addSummary() {
        SimpleDoubleStatisticalSummary first = new SimpleDoubleStatisticalSummary();
        first.add(1.0);
        first.add(2.0);
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
        SimpleDoubleStatisticalSummary summary = new SimpleDoubleStatisticalSummary();
        summary.add(1.0);
        summary.add(new SimpleDoubleStatisticalSummary());
        assertEquals(1, summary.getN());
        assertEquals(1.0, summary.getMin());
        assertEquals(1.0, summary.getMax());
    }

    @Test
    void copy() {
        SummaryStatistics statistics = new SummaryStatistics();
        statistics.addValue(2.0);
        statistics.addValue(4.0);
        SimpleDoubleStatisticalSummary summary = new SimpleDoubleStatisticalSummary(statistics);
        assertEquals(2, summary.getN());
        assertEquals(6.0, summary.getSum());
        assertEquals(2.0, summary.getMin());
        assertEquals(4.0, summary.getMax());
    }

    @Test
    void setters() {
        SimpleDoubleStatisticalSummary summary = new SimpleDoubleStatisticalSummary().setN(4).setSum(10).setMin(1).setMax(7);
        assertEquals(4, summary.getN());
        assertEquals(2.5, summary.getMean());
        assertEquals(1, summary.getMin());
        assertEquals(7, summary.getMax());
    }

    @Test
    void keepsDoublePrecision() {
        SimpleDoubleStatisticalSummary summary = new SimpleDoubleStatisticalSummary();
        summary.add(0.1);
        assertEquals(0.1, summary.getSum());
    }

    @Test
    void equalsAndHashCode() {
        SimpleDoubleStatisticalSummary first = new SimpleDoubleStatisticalSummary();
        first.add(1.0);
        first.add(2.0);
        SimpleDoubleStatisticalSummary second = new SimpleDoubleStatisticalSummary(first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        second.add(3.0);
        assertNotEquals(first, second);
    }

    @Test
    void toStringContainsValues() {
        SimpleDoubleStatisticalSummary summary = new SimpleDoubleStatisticalSummary();
        summary.add(2.0);
        assertEquals("SimpleStatisticalSummary[n=1, sum=2.0, min=2.0, max=2.0]", summary.toString());
    }
}
