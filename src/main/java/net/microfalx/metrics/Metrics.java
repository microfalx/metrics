package net.microfalx.metrics;

import net.microfalx.lang.StringUtils;

import java.lang.module.ResolutionException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableMap;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ExceptionUtils.rethrowExceptionAndReturn;
import static net.microfalx.lang.StringUtils.capitalizeWords;
import static net.microfalx.lang.TimeUtils.toLocalDateTime;

/**
 * An abstraction to track various metrics.
 * <p>
 * It also carries a group name (namespace) so all metrics are covering the same group of metrics without the new to
 * keep adding the same namespace.
 * <p>
 * It also abstracts counters, gauges, timers and histograms with simple method calls.
 */
public abstract class Metrics implements Cloneable {

    private static final String METRICS_IMPLEMENTATION_CLASS = "net.microfalx.metrics.MicrometerMetrics";
    protected static final String GROUP_TAG = "group";
    protected static final String NAME_TAG = "name";

    static final Map<String, Metrics> METRICS = new ConcurrentHashMap<>();
    protected final Map<String, Counter> counters = new ConcurrentHashMap<>();
    protected final Map<String, Gauge> gauges = new ConcurrentHashMap<>();
    protected final Map<String, Timer> timers = new ConcurrentHashMap<>();
    protected final Map<String, Summary> summaries = new ConcurrentHashMap<>();
    static ThreadLocal<Timer> LAST = new ThreadLocal<>();
    static ThreadLocal<Timer> CURRENT = new ThreadLocal<>();

    public static final Metrics ROOT = Metrics.of("");
    public static final Metrics SYSTEM = Metrics.of("System");

    private String group;
    private Map<String, String> tags = new HashMap<>();

    /**
     * Returns an instance of a metric group.
     *
     * @return a non-null instance
     */
    public static Metrics of(String group) {
        requireNonNull(group);
        String id = StringUtils.toIdentifier(group);
        return METRICS.computeIfAbsent(id, s -> doCreate(group));
    }

    /**
     * Creates an instance with a given group (namespace)
     *
     * @param group the group name
     */
    Metrics(String group) {
        requireNonNull(group);
        this.group = capitalizeWords(group);
        this.tags.put(GROUP_TAG, group);
    }

    /**
     * Returns the group name (namespace) associated with this instance of metrics.
     *
     * @return a non-null instance
     */
    public final String getGroup() {
        return group;
    }

    /**
     * Returns the tags associated with this metrics.
     *
     * @return a non-null instance
     */
    public Map<String, String> getTags() {
        return unmodifiableMap(tags);
    }

    /**
     * Registers a common tag.
     *
     * @param key   the key
     * @param value the value
     */
    public void addTag(String key, String value) {
        requireNonNull(key);
        requireNonNull(value);
        tags.put(key, value);
    }

    /**
     * Creates a copy of the current metrics and adds a new subgroup.
     *
     * @param name the name of the group
     * @return a new instance
     */
    public Metrics withGroup(String name) {
        requireNonNull(name);
        Metrics copy = copy();
        copy.group += MetricUtils.GROUP_SEPARATOR + name;
        return copy;
    }

    /**
     * Creates a copy of the current metrics and adds a new tag.
     *
     * @param key   the key
     * @param value the value
     * @return a new instance
     */
    public Metrics withTag(String key, String value) {
        Metrics copy = copy();
        copy.addTag(key, value);
        return copy;
    }

    /**
     * Increments a counter within a group.
     *
     * @param name the name of the counter
     */
    public long count(String name) {
        return getCounter(name).increment();
    }

    /**
     * Increments a counter within a group.
     *
     * @param name the name of the counter
     */
    public long count(String name, int delta) {
        return getCounter(name).increment(delta);
    }

    /**
     * Increases a gauge by one.
     *
     * @param name the name of the counter
     */
    public long increment(String name) {
        return getGauge(name).increment();
    }

    /**
     * Decreases a gauge by one.
     *
     * @param name the name of the counter
     */
    public long decrement(String name) {
        return getGauge(name).decrement();
    }

    /**
     * Times a block of code.
     *
     * @param name     the name of the timer
     * @param supplier the supplier
     */
    public <T> T time(String name, Supplier<T> supplier) {
        return getTimer(name, Timer.Type.SHORT).record(supplier);
    }

    /**
     * Times a block of code.
     *
     * @param name     the name of the timer
     * @param callable the callable
     */
    public <T> T timeCallable(String name, Callable<T> callable) {
        return getTimer(name, Timer.Type.SHORT).recordCallable(callable);
    }

    /**
     * Times a block of code.
     *
     * @param name     the name of the timer
     * @param consumer the consumer
     */
    public void time(String name, Consumer<Timer> consumer) {
        getTimer(name, Timer.Type.SHORT).record(consumer);
    }

    /**
     * Times a block of code.
     *
     * @param name     the name of the timer
     * @param consumer the consumer
     * @param value    the value passed to the consumer
     */
    public <T> void time(String name, Consumer<T> consumer, T value) {
        getTimer(name, Timer.Type.SHORT).record(consumer, value);
    }

    /**
     * Returns a counter within a group.
     *
     * @param name the name of the counter
     */
    public abstract Counter getCounter(String name);

    /**
     * Returns a gauge within a group.
     *
     * @param name the name of the counter
     */
    public abstract Gauge getGauge(String name);

    /**
     * Registers a gauge which extracts the value from a supplier.
     *
     * @param name the name of the counter
     */
    public abstract Gauge getGauge(String name, Supplier<Double> supplier);

    /**
     * Returns a timer to time a block of code.
     * <p>
     * The method returns an auto-closable resource and this should be called in a "try with resource" pattern.
     *
     * @param name the name of the timer
     * @return the resource to stop the timer
     */
    public Timer getTimer(String name) {
        return getTimer(name, Timer.Type.SHORT);
    }

    /**
     * Returns a timer to time a block of code.
     * <p>
     * The method returns an auto-closable resource and this should be called in a "try with resource" pattern.
     *
     * @param name the name of the timer
     * @param type the type of the timer
     * @return the resource to stop the timer
     */
    public abstract Timer getTimer(String name, Timer.Type type);

    /**
     * Returns a summary to time a block of code using percentile.
     * <p>
     * The method returns an auto-closable resource and this should be called in a "try with resource" pattern.
     *
     * @param name the name of the summary
     * @return the resource to stop the summary
     */
    public abstract Summary getSummary(String name);

    /**
     * Starts a timer to time a block of code.
     * <p>
     * The method returns an auto-closable resource and this should be called in a "try with resource" pattern.
     *
     * @param name the name of the timer
     * @return the resource to stop the timer
     */
    public Timer startTimer(String name) {
        return getTimer(name, Timer.Type.LONG).start();
    }

    /**
     * Returns a collection of counters active in this process.
     *
     * @return a non-null instance
     */
    public final Collection<Counter> getCounters() {
        if (ROOT.equals(this)) {
            return METRICS.values().stream().flatMap(metrics -> metrics.counters.values().stream()).collect(Collectors.toList());
        } else {
            return unmodifiableCollection(counters.values());
        }
    }

    /**
     * Returns a collection of gauges active in this process.
     *
     * @return a non-null instance
     */
    public final Collection<Gauge> getGauges() {
        if (ROOT.equals(this)) {
            return METRICS.values().stream().flatMap(metrics -> metrics.gauges.values().stream()).collect(Collectors.toList());
        } else {
            return unmodifiableCollection(gauges.values());
        }
    }

    /**
     * Returns a collection of timers active in this process.
     *
     * @return a non-null instance
     */
    public final Collection<Timer> getTimers() {
        if (ROOT.equals(this)) {
            return METRICS.values().stream().flatMap(metrics -> metrics.timers.values().stream()).collect(Collectors.toList());
        } else {
            return unmodifiableCollection(timers.values());
        }
    }

    /**
     * Returns a collection of summaries active in this process.
     *
     * @return a non-null instance
     */
    public final Collection<Summary> getSummaries() {
        if (ROOT.equals(this)) {
            return METRICS.values().stream().flatMap(metrics -> metrics.summaries.values().stream()).collect(Collectors.toList());
        } else {
            return unmodifiableCollection(summaries.values());
        }
    }

    protected final <M extends Meter> M touch(M meter) {
        if (meter instanceof AbstractMeter) ((AbstractMeter) meter).touch();
        return meter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Metrics metrics = (Metrics) o;
        return Objects.equals(group, metrics.group);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group);
    }

    /**
     * Creates the first available implementation of metrics.
     *
     * @param group the group name, can be EMPTY for {@code root} group.
     * @return the instance
     */
    private static Metrics doCreate(String group) {
        requireNonNull(group);
        try {
            Class<?> clazz = Metrics.class.getClassLoader().loadClass(METRICS_IMPLEMENTATION_CLASS);
            return (Metrics) clazz.getConstructor(String.class).newInstance(group);
        } catch (Throwable e) {
            return new NoMetrics(group);
        }
    }

    /**
     * Creates a deep copy of the metrics.
     *
     * @return a new copy
     */
    private Metrics copy() {
        try {
            Metrics metrics = (Metrics) clone();
            metrics.tags = new HashMap<>(tags);
            return metrics;
        } catch (CloneNotSupportedException e) {
            throw new ResolutionException("Cannot clone ", e);
        }
    }

    /**
     * Default implementation in case there is no other present.
     */
    static class NoMetrics extends Metrics {

        public NoMetrics(String group) {
            super(group);
        }

        @Override
        public Counter getCounter(String name) {
            return new NoCounter(getGroup(), name);
        }

        @Override
        public Gauge getGauge(String name) {
            return new NoGauge(getGroup(), name);
        }

        @Override
        public Gauge getGauge(String name, Supplier<Double> supplier) {
            return new NoGauge(getGroup(), name);
        }

        @Override
        public Timer getTimer(String name, Timer.Type type) {
            return new NoTimer(getGroup(), name);
        }

        @Override
        public Summary getSummary(String name) {
            return new NoSummary(getGroup(), name);
        }
    }

    static class AbstractMeter implements Meter {

        private final String id;
        private final String name;
        private final String group;

        private volatile long firstAccess;
        private volatile long lastAccess;

        public AbstractMeter(String group, String name) {
            requireNonNull(group);
            requireNonNull(name);
            this.id = MetricUtils.computeId(group, name);
            this.name = name;
            this.group = group;
        }

        @Override
        public final String getId() {
            return id;
        }

        @Override
        public final String getName() {
            return name;
        }

        @Override
        public final String getGroup() {
            return group;
        }

        @Override
        public LocalDateTime getFirstAccess() {
            return toLocalDateTime(firstAccess);
        }

        @Override
        public LocalDateTime getLastAccess() {
            return toLocalDateTime(lastAccess);
        }

        protected final void touch() {
            if (firstAccess == 0) firstAccess = currentTimeMillis();
            lastAccess = currentTimeMillis();
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
                    .add("id='" + id + "'")
                    .add("name='" + name + "'")
                    .add("group='" + group + "'")
                    .toString();
        }
    }

    static class NoCounter extends AbstractMeter implements Counter {

        public NoCounter(String group, String name) {
            super(group, name);
        }

        @Override
        public long getValue() {
            return 0;
        }

        @Override
        public long increment() {
            return 0;
        }

        @Override
        public long increment(int delta) {
            return 0;
        }
    }

    static class NoGauge extends AbstractMeter implements Gauge {

        public NoGauge(String group, String name) {
            super(group, name);
        }

        @Override
        public long increment() {
            return 0;
        }

        @Override
        public long decrement() {
            return 0;
        }

        @Override
        public long getValue() {
            return 0;
        }
    }

    static class NoTimer extends AbstractMeter implements Timer {

        public NoTimer(String group, String name) {
            super(group, name);
        }

        @Override
        public Type getType() {
            return Type.SHORT;
        }

        @Override
        public Timer start() {
            return this;
        }

        @Override
        public Timer stop() {
            return this;
        }

        @Override
        public <T> T record(Supplier<T> supplier) {
            return supplier.get();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> void record(Consumer<T> consumer) {
            consumer.accept((T) this);
        }

        @Override
        public <T> void record(Consumer<T> consumer, T value) {
            consumer.accept(value);
        }

        @Override
        public <T> T recordCallable(Callable<T> callable) {
            try {
                return callable.call();
            } catch (Exception e) {
                return rethrowExceptionAndReturn(e);
            }
        }

        @Override
        public void record(Runnable runnable) {
            runnable.run();
        }

        @Override
        public void record(Duration duration) {
            // nothing to record
        }

        @Override
        public Duration getDuration() {
            return Duration.ZERO;
        }

        @Override
        public long getCount() {
            return 0;
        }

        @Override
        public Duration getAverageDuration() {
            return Duration.ZERO;
        }

        @Override
        public Duration getMinimumDuration() {
            return Duration.ZERO;
        }

        @Override
        public Duration getMaximumDuration() {
            return Duration.ZERO;
        }

        @Override
        public Runnable wrap(Runnable runnable) {
            return runnable;
        }

        @Override
        public <T> Callable<T> wrap(Callable<T> callable) {
            return callable;
        }

        @Override
        public <T> Supplier<T> wrap(Supplier<T> supplier) {
            return supplier;
        }

        @Override
        public void close() {
            // nothing to close
        }
    }

    static class NoSummary extends NoTimer implements Summary {

        public NoSummary(String group, String name) {
            super(group, name);
        }

        @Override
        public Duration getPercentile(Percentile percentile) {
            return Duration.ZERO;
        }

        @Override
        public Duration[] getPercentiles() {
            return new Duration[]{Duration.ZERO, Duration.ZERO, Duration.ZERO};
        }

    }
}
