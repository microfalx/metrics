package net.microfalx.metrics.statistics;

import net.microfalx.lang.StringUtils;
import net.microfalx.lang.ThreadUtils;
import net.microfalx.lang.TimeUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static java.lang.System.currentTimeMillis;
import static net.microfalx.lang.FormatterUtils.formatBytes;
import static net.microfalx.lang.TimeUtils.THIRTY_SECONDS;
import static net.microfalx.lang.TimeUtils.millisSince;

@Disabled
class TimeWindowStatisticalSummaryIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeWindowStatisticalSummaryIntegrationTest.class);

    private final Random random = ThreadLocalRandom.current();
    private long lastStatsPrinted = TimeUtils.oneHourAgo();
    private TimeWindowStatisticalSummary summary;

    @Test
    void with10sCollection() {
        summary = create("10 Seconds");
        collectStats(Duration.ofSeconds(10));
    }

    @Test
    void with20sCollection() {
        summary = create("20 Seconds");
        collectStats(Duration.ofSeconds(20));
    }

    @Test
    void with30sCollection() {
        summary = create("30 Seconds");
        collectStats(Duration.ofSeconds(30));
    }

    private TimeWindowStatisticalSummary create(String name) {
        return (TimeWindowStatisticalSummary) new TimeWindowStatisticalSummary()
                .setRefreshInterval(Duration.ofSeconds(5))
                .withName(name).withId(StringUtils.toIdentifier(name));
    }


    private void collectStats(Duration interval) {
        printStats();
        while (true) {
            summary.add(10 + random.nextInt(10));
            ThreadUtils.sleep(interval);
            printStats();
        }
    }

    private void printStats() {
        if (millisSince(lastStatsPrinted) < THIRTY_SECONDS) return;
        LOGGER.info("Time Window Stats: time window={}, window size={}, update interval={}, " +
                        "entries={}, array memory={}, array size={}",
                summary.getInterval(), summary.getWindowSize(), summary.getUpdateInterval(),
                summary.getN(), formatBytes(summary.getArraySizeOf()), summary.getArrayCountOf());
        lastStatsPrinted = currentTimeMillis();
    }

}