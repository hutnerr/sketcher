package com.sketcher.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies ordered SQL migration scripts from resources/db/migrations.
 * Each script is named V<number>__<description>.sql; applied scripts are
 * recorded in schema_version so re-opening a library is a no-op.
 */
public class Migrations {
    private static final Pattern FILE_PATTERN = Pattern.compile("V(\\d+)__.*\\.sql");
    private static final String[] KNOWN_MIGRATIONS = {"V001__init.sql", "V002__ramp_timer.sql"};

    public static void applyAll(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied_at INTEGER)");
        }

        int current = currentVersion(connection);

        List<String> pending = new ArrayList<>();
        for (String name : KNOWN_MIGRATIONS) {
            Matcher m = FILE_PATTERN.matcher(name);
            if (m.matches() && Integer.parseInt(m.group(1)) > current) {
                pending.add(name);
            }
        }
        pending.sort(Comparator.naturalOrder());

        for (String name : pending) {
            Matcher versionMatcher = FILE_PATTERN.matcher(name);
            versionMatcher.matches();
            int version = Integer.parseInt(versionMatcher.group(1));
            String sql = readResource(name);
            connection.setAutoCommit(false);
            try (Statement st = connection.createStatement()) {
                for (String statement : sql.split(";")) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        st.execute(trimmed);
                    }
                }
                try (var ps = connection.prepareStatement(
                        "INSERT INTO schema_version(version, applied_at) VALUES (?, ?)")) {
                    ps.setInt(1, version);
                    ps.setLong(2, System.currentTimeMillis());
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new RuntimeException("Migration " + name + " failed", e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static int currentVersion(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String readResource(String name) {
        String path = "/db/migrations/" + name;
        try (InputStream in = Migrations.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing migration resource: " + path);
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read migration " + name, e);
        }
    }
}
