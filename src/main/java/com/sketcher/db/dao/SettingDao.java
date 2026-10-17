package com.sketcher.db.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Generic key/value store for library-local settings (PLAN.md §2, the `setting` table). */
public class SettingDao {
    private final Connection connection;

    public SettingDao(Connection connection) {
        this.connection = connection;
    }

    public Optional<String> get(String key) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT value FROM setting WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void set(String key, String value) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO setting(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
