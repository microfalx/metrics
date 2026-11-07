package net.microfalx.metrics;

import net.microfalx.lang.Serde;
import net.microfalx.lang.annotation.Order;
import net.microfalx.lang.annotation.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class SeriesMemoryStoreTest {

    private static final LocalDateTime START = LocalDateTime.now();
    private static final LocalDateTime END = START.plusMinutes(5);
    private static final LocalDateTime MIDDLE = START.plusMinutes(2).plusSeconds(30);

    private static final int STORE_COUNT = 5;

    private SeriesStore store;
    private final Metric metric1 = Metric.create("g1");
    private final Metric metric2 = Metric.create("g2");
    private final Metric metric3 = Metric.create("c1").withType(Metric.Type.COUNTER);

    @BeforeEach
    void setup() {
        store = SeriesStore.create();
    }

    @Test
    void create() {
        assertSame(TestSeriesStore.class, SeriesStore.create().getClass());
    }

    @Test
    void get() {
        store.add(metric1, Value.create(LocalDateTime.now(), 1));
        Series series = store.get(metric1);
        assertEquals(1, series.getCount());
        series = store.get(metric2);
        assertEquals(0, series.getCount());
    }

    @Test
    void getWithInterval() {
        generateMetric1();
        Series series = store.get(metric1);
        assertEquals(30, series.getCount());
        series = store.get(metric1, START, MIDDLE);
        assertEquals(16, series.getCount());
    }

    @Test
    void addWithCounter() {
        store.add(metric3, Value.create(LocalDateTime.now(), 1));
        store.add(metric3, Value.create(LocalDateTime.now(), 5));
        store.add(metric3, Value.create(LocalDateTime.now(), 8));
        store.add(metric3, Value.create(LocalDateTime.now(), 3));
        store.add(metric3, Value.create(LocalDateTime.now(), 5));
        store.add(metric3, Value.create(LocalDateTime.now(), 11));
        double value = store.getAverage(metric3, Duration.ofHours(1)).orElse(0);
        assertEquals(3.75, value, 0.001);
    }

    @Test
    void add() {
        store.add(Collections.emptyList(), false);
        assertEquals(0, store.get(metric1).getCount());
        store.add(getStores(), false);
        Series series = store.get(metric1);
        assertEquals(15, series.getCount());
        assertEquals(2.33, series.getAverage().getAsDouble(), 0.01);
    }

    @Test
    void addAverage() {
        store.add(getStores(), true);
        Series series = store.get(metric1);
        assertEquals(5, series.getCount());
        assertEquals(2, series.getAverage().getAsDouble(), 0.01);
    }

    @Test
    void serialize() {
        store = new SeriesMemoryStore();
        generateMetric1();
        byte[] data = Serde.serialize(store);
        SeriesMemoryStore copy = Serde.deserialize(data);
        assertNotSame(store, copy);
        assertEquals(1, copy.getMetrics().size());
        assertEquals(30, copy.get(metric1).getCount());
        assertEquals(15.5, copy.get(metric1).getAverage().getAsDouble(), 0.001);
    }

    private void generateMetric1() {
        LocalDateTime current = START;
        int value = 1;
        while (current.isBefore(END)) {
            store.add(metric1, Value.create(current, value++));
            current = current.plusSeconds(10);
        }
    }

    private Collection<SeriesStore> getStores() {
        Collection<SeriesStore> stores = new ArrayList<>();
        for (int i = 1; i <= STORE_COUNT; i++) {
            SeriesStore memory = SeriesStore.memory();
            stores.add(memory);
            for (int j = 0; j < i; j++) {
                memory.add(metric1, Value.create(START.plusSeconds(j), j + 1));
            }
        }
        return stores;
    }

    @Provider
    @Order
    public static class TestSeriesStore extends SeriesMemoryStore {

    }

}
