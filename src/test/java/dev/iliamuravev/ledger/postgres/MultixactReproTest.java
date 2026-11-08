package dev.iliamuravev.ledger.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("postgres-bug")
class MultixactReproTest {

    static final String MULTIXACT_ERROR = "new multixact has more than one updating member";
    static final int THREADS = Integer.getInteger("repro.threads", 32);
    static final int ATTEMPTS_PER_THREAD = Integer.getInteger("repro.attempts", 300);
    static final int ACCOUNTS = 8;

    @ParameterizedTest(name = "{0} {1} foreignKeys={2}")
    @CsvSource({
            "postgres:16-alpine, INSERTS_FIRST, true",
            "postgres:16-alpine, UPDATES_FIRST, true",
            "postgres:18-alpine, INSERTS_FIRST, true",
            "postgres:18-alpine, UPDATES_FIRST, true",
            "postgres:18-alpine, INSERTS_FIRST, false"})
    void optimisticTransfersWithForeignKeys(String image, Order order, boolean foreignKeys) throws Exception {
        try (var postgres = new PostgreSQLContainer<>(
                DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"))
                .withCommand("postgres", "-c", "max_connections=200")) {
            postgres.start();
            createSchema(postgres, foreignKeys);

            var result = run(postgres, order);

            System.out.printf("PostgreSQL %s, %s, foreign keys %s: %d committed, %d version conflicts, "
                            + "%d multixact errors, other errors %s%n",
                    result.serverVersion(), order, foreignKeys ? "on" : "off", result.commits(), result.conflicts(),
                    result.multixactErrors(), result.otherErrors());
            assertThat(result.otherErrors()).isEmpty();
            if (order == Order.UPDATES_FIRST || !foreignKeys) {
                assertThat(result.multixactErrors()).isZero();
            }
        }
    }

    enum Order {
        INSERTS_FIRST,
        UPDATES_FIRST
    }

    record Result(String serverVersion, long commits, long conflicts, long multixactErrors,
            Map<String, Long> otherErrors) {
    }

    private static void createSchema(PostgreSQLContainer<?> postgres, boolean foreignKeys) throws SQLException {
        var reference = foreignKeys ? " references account (id)" : "";
        try (var connection = connect(postgres); var statement = connection.createStatement()) {
            statement.execute("create table account (id int primary key, balance bigint not null, "
                    + "version bigint not null)");
            statement.execute("create table transfer (id bigserial primary key, from_id int not null" + reference
                    + ", to_id int not null" + reference + ")");
            statement.execute("create table entry (id bigserial primary key, "
                    + "transfer_id bigint not null references transfer (id), account_id int not null" + reference
                    + ", amount bigint not null)");
            statement.execute("insert into account select g, 1000000000, 0 from generate_series(1, " + ACCOUNTS
                    + ") g");
        }
    }

    private static Result run(PostgreSQLContainer<?> postgres, Order order) throws Exception {
        var commits = new AtomicLong();
        var conflicts = new AtomicLong();
        var multixactErrors = new AtomicLong();
        var otherErrors = new ConcurrentHashMap<String, AtomicLong>();
        var executor = Executors.newFixedThreadPool(THREADS);
        for (int t = 0; t < THREADS; t++) {
            executor.submit(() -> {
                try (var connection = connect(postgres)) {
                    connection.setAutoCommit(false);
                    for (int i = 0; i < ATTEMPTS_PER_THREAD; i++) {
                        var random = ThreadLocalRandom.current();
                        int from = 1 + random.nextInt(ACCOUNTS);
                        int to = 1 + (from + random.nextInt(ACCOUNTS - 1)) % ACCOUNTS;
                        try {
                            if (attempt(connection, from, to, order)) {
                                connection.commit();
                                commits.incrementAndGet();
                            } else {
                                connection.rollback();
                                conflicts.incrementAndGet();
                            }
                        } catch (SQLException e) {
                            connection.rollback();
                            if (e.getMessage().contains(MULTIXACT_ERROR)) {
                                multixactErrors.incrementAndGet();
                            } else {
                                otherErrors.computeIfAbsent(e.getSQLState() + " " + e.getMessage(),
                                        key -> new AtomicLong()).incrementAndGet();
                            }
                        }
                    }
                }
                return null;
            });
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.MINUTES)).isTrue();

        String version;
        try (var connection = connect(postgres);
                var rows = connection.createStatement().executeQuery("show server_version")) {
            rows.next();
            version = rows.getString(1);
        }
        var others = new TreeMap<String, Long>();
        otherErrors.forEach((message, count) -> others.put(message, count.get()));
        return new Result(version, commits.get(), conflicts.get(), multixactErrors.get(), others);
    }

    private static boolean attempt(Connection connection, int from, int to, Order order) throws SQLException {
        long fromVersion = version(connection, from);
        long toVersion = version(connection, to);
        if (order == Order.INSERTS_FIRST) {
            insertTransfer(connection, from, to);
        }
        var fromFirst = from < to;
        var applied = fromFirst
                ? update(connection, from, -1, fromVersion) && update(connection, to, 1, toVersion)
                : update(connection, to, 1, toVersion) && update(connection, from, -1, fromVersion);
        if (applied && order == Order.UPDATES_FIRST) {
            insertTransfer(connection, from, to);
        }
        return applied;
    }

    private static long version(Connection connection, int id) throws SQLException {
        try (var select = connection.prepareStatement("select version from account where id = ?")) {
            select.setInt(1, id);
            try (var rows = select.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static void insertTransfer(Connection connection, int from, int to) throws SQLException {
        long transferId;
        try (var insert = connection.prepareStatement(
                "insert into transfer (from_id, to_id) values (?, ?) returning id")) {
            insert.setInt(1, from);
            insert.setInt(2, to);
            try (var rows = insert.executeQuery()) {
                rows.next();
                transferId = rows.getLong(1);
            }
        }
        try (var insert = connection.prepareStatement(
                "insert into entry (transfer_id, account_id, amount) values (?, ?, -1), (?, ?, 1)")) {
            insert.setLong(1, transferId);
            insert.setInt(2, from);
            insert.setLong(3, transferId);
            insert.setInt(4, to);
            insert.executeUpdate();
        }
    }

    private static boolean update(Connection connection, int id, long delta, long expectedVersion)
            throws SQLException {
        try (var update = connection.prepareStatement(
                "update account set balance = balance + ?, version = version + 1 where id = ? and version = ?")) {
            update.setLong(1, delta);
            update.setInt(2, id);
            update.setLong(3, expectedVersion);
            return update.executeUpdate() == 1;
        }
    }

    private static Connection connect(PostgreSQLContainer<?> postgres) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
