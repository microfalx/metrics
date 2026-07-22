package net.microfalx.metrics.statistics;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static net.microfalx.lang.ThreadUtils.sleepMillis;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeWindowStatisticalSummaryTest {

    @Test
    void addAndGetMean() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(java.time.Duration.ofSeconds(10));
        summary.add(1.0);
        summary.add(2.0);
        summary.add(3.0);
        assertEquals(2.0, summary.getMean(), 0.0001);
    }

    @Test
    void addAndGetMax() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(java.time.Duration.ofSeconds(10));
        summary.add(1.0);
        summary.add(5.0);
        summary.add(3.0);
        assertEquals(5.0, summary.getMax(), 0.0001);
    }

    @Test
    void addAndGetMin() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(java.time.Duration.ofSeconds(10));
        summary.add(4.0);
        summary.add(2.0);
        summary.add(3.0);
        assertEquals(2.0, summary.getMin(), 0.0001);
    }

    @Test
    void recalculateWindow() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(java.time.Duration.ofSeconds(60))
                .setRefreshInterval(Duration.ofSeconds(1));
        for (int i = 0; i < 20; i++) {
            summary.add(i);
            sleepMillis(100);
        }

    }

}