package com.sketcher.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the single JDBC connection to a library's sketcher.db.
 * Not thread-safe by design: all callers must go through the single-threaded
 * DB executor (see ui.common.AsyncTask).
 */
public class Database implements AutoCloseable {
    private final Connection connection;

    public Database(Path dbFile) {
        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA journal_mode = WAL");
                st.execute("PRAGMA synchronous = NORMAL");
            }
            Migrations.applyAll(connection);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open library database at " + dbFile, e);
        }
    }

    public Connection connection() {
        return connection;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
