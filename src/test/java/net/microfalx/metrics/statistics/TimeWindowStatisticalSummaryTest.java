package net.microfalx.metrics.statistics;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static net.microfalx.lang.ThreadUtils.sleepMillis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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

    @Test
    void defaultDirectionIsHigherIsBetter() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(Duration.ofSeconds(10));
        assertEquals(Direction.HIGHER_IS_BETTER, summary.getDirection());
    }

    @Test
    void setDirectionChangesDirection() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(Duration.ofSeconds(10))
                .setDirection(Direction.LOWER_IS_BETTER);
        assertEquals(Direction.LOWER_IS_BETTER, summary.getDirection());
    }

    @Test
    void trendWithTooFewSamplesIsStable() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(Duration.ofSeconds(10));
        summary.add(1.0);
        summary.add(2.0);
        assertEquals(Trend.STABLE, summary.getTrend());
    }

    @Test
    void trendIsStableForConstantValues() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(Duration.ofSeconds(10));
        for (int i = 0; i < 5; i++) summary.add(100.0);
        assertEquals(Trend.STABLE, summary.getTrend());
    }

    @Test
    void trendIsFluctuatingForOscillatingValuesWithNoSlope() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(Duration.ofSeconds(10));
        // palindrome sequence: no net slope (cancels by symmetry), but large swings around the mean
        double[] values = {50, 150, 50, 150, 100, 100, 150, 50, 150, 50};
        for (double value : values) summary.add(value);
        assertEquals(Trend.FLUCTUATING, summary.getTrend());
    }

    @Test
    void trendIsSlightlyImprovingForGentleRise() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(Duration.ofSeconds(10));
        for (int i = 0; i < 10; i++) summary.add(100 + i);
        assertEquals(Trend.SLIGHTLY_IMPROVING, summary.getTrend());
    }

    @Test
    void trendIsImprovingForModerateRise() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(Duration.ofSeconds(10));
        for (int i = 0; i < 10; i++) summary.add(100 + i * 3);
        assertEquals(Trend.IMPROVING, summary.getTrend());
    }

    @Test
    void trendIsSharplyImprovingForSteepRise() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(Duration.ofSeconds(10));
        for (int i = 0; i < 10; i++) summary.add(100 + i * 10);
        assertEquals(Trend.SHARPLY_IMPROVING, summary.getTrend());
    }

    @Test
    void trendIsSlightlyWorseningForGentleFall() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(Duration.ofSeconds(10));
        for (int i = 0; i < 10; i++) summary.add(109 - i);
        assertEquals(Trend.SLIGHTLY_WORSENING, summary.getTrend());
    }

    @Test
    void trendIsWorseningForModerateFall() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(Duration.ofSeconds(10));
        for (int i = 0; i < 10; i++) summary.add(127 - i * 3);
        assertEquals(Trend.WORSENING, summary.getTrend());
    }

    @Test
    void trendIsSharplyWorseningForSteepFall() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(Duration.ofSeconds(10));
        for (int i = 0; i < 10; i++) summary.add(190 - i * 10);
        assertEquals(Trend.SHARPLY_WORSENING, summary.getTrend());
    }

    @Test
    void trendRespectsLowerIsBetterDirection() {
        TimeWindowStatisticalSummary rising = new TimeWindowStatisticalSummary(Duration.ofSeconds(10))
                .setDirection(Direction.LOWER_IS_BETTER);
        for (int i = 0; i < 10; i++) rising.add(100 + i * 10);
        assertEquals(Trend.SHARPLY_WORSENING, rising.getTrend());

        TimeWindowStatisticalSummary falling = new TimeWindowStatisticalSummary(Duration.ofSeconds(10))
                .setDirection(Direction.LOWER_IS_BETTER);
        for (int i = 0; i < 10; i++) falling.add(190 - i * 10);
        assertEquals(Trend.SHARPLY_IMPROVING, falling.getTrend());
    }

    @Test
    void trendWithLimitAndTooFewSamplesIsStable() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(Duration.ofSeconds(10));
        for (int i = 0; i < 10; i++) summary.add(100 + i * 10);
        assertEquals(Trend.STABLE, summary.getTrend(3));
    }

    @Test
    void trendWithLimitUsesMostRecentSamples() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(Duration.ofSeconds(10));
        double[] values = {200, 190, 180, 170, 160, 150, 140, 130, 140, 150, 160, 170};
        for (double value : values) summary.add(value);
        assertEquals(Trend.SHARPLY_IMPROVING, summary.getTrend(4));
    }

    @Test
    void getValuesWithLimitReturnsLatestSamples() {
        TimeWindowStatisticalSummary summary = new TimeWindowStatisticalSummary(Duration.ofSeconds(10));
        for (int i = 1; i <= 6; i++) summary.add(i);

        assertArrayEquals(new double[]{4, 5, 6}, summary.getValues(3), 0.0001);
        assertArrayEquals(new double[]{1, 2, 3, 4, 5, 6}, summary.getValues(10), 0.0001);
    }

}
