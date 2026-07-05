package xyz.ngthav.opencheckin.db;

import xyz.ngthav.opencheckin.util.Files;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the single SQLite connection and bootstraps the schema on first use.
 *
 * <p>One connection, accessed serially: writes happen on the FX thread (or one single-threaded
 * executor) and the camera thread never touches JDBC. Serialising every DAO call through this
 * one connection is what avoids {@code SQLITE_BUSY}. Foreign keys are enabled
 * as a backstop for the room-delete guard.
 */
public final class Database implements AutoCloseable {

    private final Connection connection;

    private Database(String jdbcUrl) {
        try {
            this.connection = DriverManager.getConnection(jdbcUrl);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
            createSchema();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not open database: " + jdbcUrl, e);
        }
    }

    /** Opens the on-disk database, creating {@code data/} and the schema if missing. */
    public static Database open(Path dbFile) {
        Files.ensureDir(dbFile.toAbsolutePath().getParent());
        return new Database("jdbc:sqlite:" + dbFile.toAbsolutePath());
    }

    /** In-memory database for unit tests. */
    public static Database inMemory() {
        return new Database("jdbc:sqlite::memory:");
    }

    public Connection connection() {
        return connection;
    }

    private void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS room (
                      id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                      name                TEXT    NOT NULL,
                      checkin             TEXT,
                      checkout            TEXT,
                      manual_confirmation INTEGER NOT NULL DEFAULT 0,
                      created_at          TEXT    NOT NULL,
                      updated_at          TEXT    NOT NULL
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS member (
                      id           INTEGER PRIMARY KEY AUTOINCREMENT,
                      uuid         TEXT    NOT NULL UNIQUE,
                      name         TEXT    NOT NULL,
                      description  TEXT,
                      picture_name TEXT,
                      room_id      INTEGER NOT NULL REFERENCES room(id),
                      created_at   TEXT    NOT NULL,
                      updated_at   TEXT    NOT NULL
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS attendance (
                      id         INTEGER PRIMARY KEY AUTOINCREMENT,
                      room_id    INTEGER NOT NULL REFERENCES room(id),
                      member_id  INTEGER NOT NULL REFERENCES member(id),
                      created_at TEXT    NOT NULL
                    )""");
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            // Nothing useful to do on close failure; the process is exiting.
        }
    }
}
