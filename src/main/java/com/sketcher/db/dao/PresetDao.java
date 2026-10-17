package com.sketcher.db.dao;

import com.sketcher.filter.Filter;
import com.sketcher.filter.FilterCodec;
import com.sketcher.model.OrderMode;
import com.sketcher.model.Preset;
import com.sketcher.model.Segment;
import com.sketcher.model.SessionSpec;
import com.sketcher.model.SourceKind;
import com.sketcher.model.TickBehavior;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A saved SessionSpec (PLAN.md 4.5): the `preset` row plus its ordered
 * `segment` rows, written and read together.
 */
public class PresetDao {
    private final Connection connection;

    public PresetDao(Connection connection) {
        this.connection = connection;
    }

    /** Saves a new preset with its segments in one transaction. */
    public int create(String name, SessionSpec spec) {
        if (nameExists(name, null)) {
            throw new IllegalStateException("A preset named \"" + name + "\" already exists");
        }
        try {
            connection.setAutoCommit(false);
            int presetId = insertPresetRow(name);
            insertSegments(presetId, spec.segments());
            connection.commit();
            return presetId;
        } catch (SQLException e) {
            rollbackQuietly();
            throw new RuntimeException("Failed to save preset " + name, e);
        } finally {
            setAutoCommitQuietly(true);
        }
    }

    public void rename(int presetId, String newName) {
        if (nameExists(newName, presetId)) {
            throw new IllegalStateException("A preset named \"" + newName + "\" already exists");
        }
        try (PreparedStatement ps = connection.prepareStatement("UPDATE preset SET name = ? WHERE id = ?")) {
            ps.setString(1, newName);
            ps.setInt(2, presetId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setPinned(int presetId, boolean pinned) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE preset SET pinned = ? WHERE id = ?")) {
            ps.setInt(1, pinned ? 1 : 0);
            ps.setInt(2, presetId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Bumps use_count and last_used_at; called once a session built from this preset actually starts. */
    public void recordUsed(int presetId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE preset SET use_count = use_count + 1, last_used_at = ? WHERE id = ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setInt(2, presetId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(int presetId) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM preset WHERE id = ?")) {
            ps.setInt(1, presetId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Preset> get(int presetId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM preset WHERE id = ?")) {
            ps.setInt(1, presetId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Pinned first, then most-recently-used, then most-used (PLAN.md 4.6). */
    public List<Preset> listAll() {
        String sql = """
                SELECT * FROM preset
                ORDER BY pinned DESC, (last_used_at IS NULL) ASC, last_used_at DESC, use_count DESC, name ASC""";
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Preset> presets = new ArrayList<>();
            while (rs.next()) presets.add(mapRow(rs));
            return presets;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public SessionSpec loadSpec(int presetId) {
        String sql = "SELECT * FROM segment WHERE preset_id = ? ORDER BY position ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, presetId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Segment> segments = new ArrayList<>();
                while (rs.next()) segments.add(mapSegment(rs));
                return new SessionSpec(segments, presetId);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int insertPresetRow(String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO preset(name, created_at, use_count, pinned) VALUES (?, ?, 0, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private void insertSegments(int presetId, List<Segment> segments) throws SQLException {
        String sql = """
                INSERT INTO segment(preset_id, position, source_kind, filter_json, playlist_id, item_count,
                                     order_mode, seconds_per_image, tick_behavior, break_seconds,
                                     memory_show_seconds, memory_hide_seconds, ramp_to_seconds)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int position = 0;
            for (Segment seg : segments) {
                ps.setInt(1, presetId);
                ps.setInt(2, position++);
                ps.setString(3, seg.sourceKind().name());
                if (seg.sourceKind() == SourceKind.FILTER) {
                    ps.setString(4, FilterCodec.encode(seg.filter()));
                } else {
                    ps.setNull(4, Types.VARCHAR);
                }
                setNullableInt(ps, 5, seg.playlistId());
                setNullableInt(ps, 6, seg.itemCount());
                ps.setString(7, seg.orderMode().name());
                ps.setInt(8, seg.secondsPerImage());
                ps.setString(9, seg.tickBehavior().name());
                ps.setInt(10, seg.breakSeconds());
                setNullableInt(ps, 11, seg.memoryShowSeconds());
                setNullableInt(ps, 12, seg.memoryHideSeconds());
                setNullableInt(ps, 13, seg.rampToSeconds());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private Segment mapSegment(ResultSet rs) throws SQLException {
        SourceKind sourceKind = SourceKind.valueOf(rs.getString("source_kind"));
        Filter filter = sourceKind == SourceKind.FILTER ? FilterCodec.decode(rs.getString("filter_json")) : null;
        return new Segment(sourceKind, filter, getNullableInt(rs, "playlist_id"), getNullableInt(rs, "item_count"),
                OrderMode.valueOf(rs.getString("order_mode")), rs.getInt("seconds_per_image"),
                TickBehavior.valueOf(rs.getString("tick_behavior")), rs.getInt("break_seconds"),
                getNullableInt(rs, "memory_show_seconds"), getNullableInt(rs, "memory_hide_seconds"),
                getNullableInt(rs, "ramp_to_seconds"));
    }

    private Preset mapRow(ResultSet rs) throws SQLException {
        return new Preset(rs.getInt("id"), rs.getString("name"), rs.getLong("created_at"),
                getNullableLong(rs, "last_used_at"), rs.getInt("use_count"), rs.getInt("pinned") == 1);
    }

    private boolean nameExists(String name, Integer excludingId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM preset WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    if (excludingId == null || id != excludingId) return true;
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) ps.setInt(index, value); else ps.setNull(index, Types.INTEGER);
    }

    private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private void setAutoCommitQuietly(boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
        }
    }
}
