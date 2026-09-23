package net.microfalx.metrics;

import net.microfalx.lang.TimeUtils;
import net.microfalx.lang.annotation.Order;
import net.microfalx.lang.annotation.Provider;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;
import org.sqlite.SQLiteOpenMode;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.FileUtils.validateFileExists;
import static net.microfalx.lang.IOUtils.closeQuietly;
import static net.microfalx.lang.JvmUtils.getVariableDirectory;
import static net.microfalx.lang.TimeUtils.toMillis;

/**
 * A store implementation backed by <a href="https://www.sqlite.org/">SQLite</a>
 */
@Provider
@Order(Order.LOW)
final class SqliteSeriesStore extends AbstractSeriesStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqliteSeriesStore.class);

    private static final Metrics METRICS = Metrics.of("Series").withGroup("Store");
    private static final String DEFAULT_NAME = "metrics";
    private static final String FILE_EXTENSION = ".db";

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock rlock = lock.readLock();
    private final Lock wlock = lock.writeLock();
    private final Set<String> metricsCreated = new ConcurrentSkipListSet<>();
    private final String name;
    private volatile File db;
    private volatile Driver driver;
    private Properties properties;

    private static final ThreadLocal<Connection> CONNECTION = new ThreadLocal<>();

    public SqliteSeriesStore() {
        this(DEFAULT_NAME);
    }

    public SqliteSeriesStore(String name) {
        requireNotEmpty(name);
        this.name = name + FILE_EXTENSION;
    }

    @Override
    public Set<Metric> getMetrics() {
        Set<Metric> extractMetrics = new HashSet<>();
        try {
            for (String tableName : getTableNames()) {
                extractMetrics.add(Metric.get(tableName));
            }
        } catch (SQLException e) {
            throw new MetricException("Failed to clear storage", e);
        }
        return extractMetrics;
    }

    @Override
    public Series get(Metric metric) {
        checkMetricTable(metric);
        DefaultSeries series = new DefaultSeries(metric.getDisplayName());
        try {
            doWithResultSet(String.format(EXTRACT_SERIES, getTableName(metric)), resultSet -> {
                extractSeries(series, resultSet);
                return null;
            });
        } catch (SQLException e) {
            throw new MetricException("Failed to extract series for metric '" + metric.getName()
                    + "', store '" + name + "'", e);
        }
        return series;
    }


    @Override
    public Series get(Metric metric, Temporal from, Temporal to) {
        checkMetricTable(metric);
        DefaultSeries series = new DefaultSeries(metric.getDisplayName());
        try {
            doWithResultSet(String.format(EXTRACT_SERIES_WITH_RANGE, getTableName(metric)), resultSet -> {
                extractSeries(series, resultSet);
                return null;
            }, toMillis(from), toMillis(to));
        } catch (SQLException e) {
            throw new MetricException("Failed to extract series for metric '" + metric.getName()
                    + "', store '" + name + "'", e);
        }
        return series;
    }

    @Override
    public OptionalDouble getAverage(Metric metric, Temporal from, Temporal to) {
        checkMetricTable(metric);
        try {
            Double value = doWithResultSet(String.format(EXTRACT_SERIES_AVERAGE, getTableName(metric)),
                    SqliteSeriesStore::getFirstDouble, toMillis(from), toMillis(to));
            return OptionalDouble.of(value);
        } catch (SQLException e) {
            throw new MetricException("Failed to extract series for metric '" + metric.getName()
                    + "', store '" + name + "'", e);
        }
    }

    @Override
    public void add(Metric metric, Value value) {
        checkMetricTable(metric);
        requireNonNull(value);
        try {
            update(createInsertSql(metric), value.getTimestamp(), value.asFloat());
        } catch (SQLException e) {
            throw new MetricException("Failed to store value '" + value + "' for metrics '" + metric.getName()
                    + "', store '" + name + "'", e);
        }
    }

    @Override
    public void add(Batch batch) {
        requireNonNull(batch);
        try {
            doInConnection(connection -> {
                for (Pair<Metric, Value> pair : batch) {
                    Metric metric = pair.getKey();
                    Value value = pair.getValue();
                    checkMetricTable(metric);
                    update(createInsertSql(metric), value.getTimestamp(), value.asFloat());
                }
                return null;
            });
        } catch (SQLException e) {
            throw new MetricException("Failed to store batch (" + batch.size() + " metrics), store '" + name + "'", e);
        }
    }

    @Override
    public Optional<LocalDateTime> getEarliestTimestamp(Metric metric) {
        return getTimestamp(metric, EXTRACT_SERIES_EARLIEST);
    }

    @Override
    public Optional<LocalDateTime> getLatestTimestamp(Metric metric) {
        return getTimestamp(metric, EXTRACT_SERIES_LATEST);
    }

    private Optional<LocalDateTime> getTimestamp(Metric metric, String sql) {
        checkMetricTable(metric);
        try {
            Long timestamp = doWithResultSet(String.format(sql, getTableName(metric)), SqliteSeriesStore::getFirstLong);
            return timestamp != null ? Optional.of(TimeUtils.toLocalDateTime(timestamp)) : Optional.empty();
        } catch (SQLException e) {
            throw new MetricException("Failed to extract earliest or latest timestamp for metric '" + metric.getId()
                    + "', store '" + name + "'", e);
        }
    }

    @Override
    public void clear() {
        try {
            for (String tableName : getTableNames()) {
                execute(String.format(DELETE_SERIES, tableName));
            }
        } catch (SQLException e) {
            throw new MetricException("Failed to clear storage", e);
        }
    }

    private void checkMetricTable(Metric metric) {
        requireNonNull(metric);
        String id = metric.getId();
        if (metricsCreated.contains(id)) return;
        wlock.lock();
        try {
            execute(createCreateSQL(metric));
            metricsCreated.add(id);
        } catch (SQLException e) {
            SQLiteErrorCode resultCode = ((SQLiteException) e).getResultCode();
            if (resultCode != SQLiteErrorCode.SQLITE_ERROR) {
                throw new MetricException("Failed to create storage for metric " + metric.getName(), e);
            }
        } finally {
            wlock.unlock();
        }
    }

    private void extractSeries(DefaultSeries series, ResultSet resultSet) throws SQLException {
        while (resultSet.next()) {
            Value value = Value.create(resultSet.getLong(1), resultSet.getFloat(2));
            series.values.add(value);
        }
    }

    private void execute(String sql) throws SQLException {
        doInConnection(connection -> {
            Statement statement = connection.createStatement();
            try (Timer ignored = METRICS.startTimer("Execute")) {
                statement.execute(sql);
            } finally {
                closeQuietly(statement);
            }
            return null;
        });
    }

    private int update(String sql, Object... args) throws SQLException {
        requireNonNull(sql);
        return doInConnection(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            try (Timer ignored = METRICS.startTimer("Update")) {
                int index = 1;
                for (Object value : args) {
                    statement.setObject(index++, value);
                }
                return statement.executeUpdate();
            } finally {
                closeQuietly(statement);
            }
        });
    }

    private <T> T doWithResultSet(String sql, ResultSetCallback<T> callback, Object... args) throws SQLException {
        requireNonNull(sql);
        requireNonNull(callback);
        return doInConnection(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = null;
            try {
                try (Timer ignored = METRICS.startTimer("Query")) {
                    int index = 1;
                    for (Object arg : args) {
                        statement.setObject(index++, arg);
                    }
                    resultSet = statement.executeQuery();
                }
                return callback.doWithResultSet(resultSet);
            } finally {
                closeQuietly(resultSet);
                closeQuietly(statement);
            }
        });
    }

    private <T> T doInConnection(ConnectionCallback<T> callback) throws SQLException {
        requireNonNull(callback);
        Connection connection = CONNECTION.get();
        if (connection != null) {
            return callback.doInConnection(connection);
        } else {
            try {
                connection = getConnection();
                return callback.doInConnection(connection);
            } finally {
                closeQuietly(connection);
            }
        }
    }

    private Connection getConnection() throws SQLException {
        return getDriver().connect(getJdbcUrl(), properties);
    }

    private Driver getDriver() {
        if (driver != null) return driver;
        rlock.lock();
        try {
            SQLiteConfig config = new SQLiteConfig();
            config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
            config.setJournalMode(SQLiteConfig.JournalMode.WAL);
            config.setOpenMode(SQLiteOpenMode.READWRITE);
            properties = config.toProperties();
            String url = getJdbcUrl();
            boolean exists = getFile().exists();
            if (!exists) LOGGER.info("Create database {}", url);
            try {
                driver = DriverManager.getDriver(url);
                if (!exists) {
                    LOGGER.info("SQLite database opened, version {}.{}", driver.getMajorVersion(), driver.getMinorVersion());
                }
            } catch (SQLException e) {
                throw new MetricException("Failed to initialize SQLite database '" + url + "'");
            }
            return driver;
        } finally {
            rlock.unlock();
        }
    }

    private String getJdbcUrl() {
        return "jdbc:sqlite:" + getFile().getAbsolutePath();
    }

    private File getFile() {
        if (db == null) {
            db = validateFileExists(new File(new File(getVariableDirectory(), "metrics"), name));
        }
        return db;
    }

    private static Long getFirstLong(ResultSet resultSet) throws SQLException {
        return resultSet.next() ? resultSet.getLong(1) : null;
    }

    private static Double getFirstDouble(ResultSet resultSet) throws SQLException {
        return resultSet.next() ? resultSet.getDouble(1) : null;
    }

    private static String getTableName(Metric metric) {
        return metric.getId();
    }

    private static Metric getMetric(String tableName) {
        return Metric.get(tableName);
    }

    private String createCreateSQL(Metric metric) {
        StringBuilder builder = new StringBuilder();
        builder.append("CREATE TABLE ").append(getTableName(metric)).append(" (\n")
                .append("  timestamp INTEGER PRIMARY KEY ASC,\n")
                .append("  value REAL\n")
                .append("\n) WITHOUT ROWID");
        return builder.toString();
    }

    private String createInsertSql(Metric metric) {
        StringBuilder builder = new StringBuilder();
        builder.append("INSERT INTO ").append(getTableName(metric)).append(" (")
                .append("timestamp, value)")
                .append("VALUES (?,?)");
        return builder.toString();
    }

    private Set<String> getTableNames() throws SQLException {
        return doWithResultSet(EXTRACT_TABLE_NAMES, resultSet -> {
            Set<String> tableNames = new HashSet<>();
            while (resultSet.next()) {
                tableNames.add(resultSet.getString(1));
            }
            return tableNames;
        });
    }

    interface ConnectionCallback<T> {

        T doInConnection(Connection connection) throws SQLException;
    }

    interface ResultSetCallback<T> {

        T doWithResultSet(ResultSet resultSet) throws SQLException;
    }

    private static final String EXTRACT_SERIES = "SELECT * FROM %s";
    private static final String EXTRACT_SERIES_WITH_RANGE = "SELECT * FROM %s where timestamp >= ? and timestamp <= ?";
    private static final String EXTRACT_SERIES_AVERAGE = "SELECT AVG(value) FROM %s where timestamp >= ? and timestamp <= ?";
    private static final String EXTRACT_SERIES_EARLIEST = "SELECT min(timestamp) FROM %s";
    private static final String EXTRACT_SERIES_LATEST = "SELECT max(timestamp) FROM %s";
    private static final String DELETE_SERIES = "DELETE FROM %s";
    private static final String EXTRACT_TABLE_NAMES = "SELECT name FROM sqlite_master WHERE type='table'";
}
