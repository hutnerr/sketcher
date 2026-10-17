package com.sketcher.db.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Tracks practice_session rows; per-image view_event rows are written by ImageDao.recordView. */
public class ViewDao {
    private final Connection connection;

    public ViewDao(Connection connection) {
        this.connection = connection;
    }

    public int startSession(Integer presetId, long startedAt) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO practice_session(preset_id, started_at, images_shown) VALUES (?, ?, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            if (presetId != null) ps.setInt(1, presetId); else ps.setNull(1, java.sql.Types.INTEGER);
            ps.setLong(2, startedAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void endSession(int sessionId, long endedAt, int imagesShown) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE practice_session SET ended_at = ?, images_shown = ? WHERE id = ?")) {
            ps.setLong(1, endedAt);
            ps.setInt(2, imagesShown);
            ps.setInt(3, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
