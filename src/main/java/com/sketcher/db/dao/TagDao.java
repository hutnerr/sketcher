package com.sketcher.db.dao;

import com.sketcher.model.Tag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TagDao {
    private final Connection connection;

    public TagDao(Connection connection) {
        this.connection = connection;
    }

    public int create(String name, Integer parentId) {
        if (siblingExists(name, parentId, null)) {
            throw new IllegalStateException("A tag named \"" + name + "\" already exists at this level");
        }
        String sql = "INSERT INTO tag(name, parent_id, created_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            if (parentId != null) ps.setInt(2, parentId); else ps.setNull(2, java.sql.Types.INTEGER);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create tag " + name, e);
        }
    }

    public void rename(int tagId, String newName) {
        Tag tag = get(tagId).orElseThrow();
        if (siblingExists(newName, tag.parentId(), tagId)) {
            throw new IllegalStateException("A tag named \"" + newName + "\" already exists at this level");
        }
        try (PreparedStatement ps = connection.prepareStatement("UPDATE tag SET name = ? WHERE id = ?")) {
            ps.setString(1, newName);
            ps.setInt(2, tagId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void reparent(int tagId, Integer newParentId) {
        if (newParentId != null) {
            if (newParentId == tagId || isDescendant(tagId, newParentId)) {
                throw new IllegalStateException("Cannot move a tag under its own descendant");
            }
        }
        Tag tag = get(tagId).orElseThrow();
        if (siblingExists(tag.name(), newParentId, tagId)) {
            throw new IllegalStateException("A tag named \"" + tag.name() + "\" already exists at this level");
        }
        try (PreparedStatement ps = connection.prepareStatement("UPDATE tag SET parent_id = ? WHERE id = ?")) {
            if (newParentId != null) ps.setInt(1, newParentId); else ps.setNull(1, java.sql.Types.INTEGER);
            ps.setInt(2, tagId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Deletes a tag; its children are re-parented to the deleted tag's own parent. */
    public void deleteReassignChildren(int tagId) {
        Tag tag = get(tagId).orElseThrow();
        for (Tag child : listChildren(tagId)) {
            reparent(child.id(), tag.parentId());
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM tag WHERE id = ?")) {
            ps.setInt(1, tagId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Moves all images and children from source onto target, then deletes source. */
    public void merge(int sourceId, int targetId) {
        if (sourceId == targetId) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO image_tag(image_id, tag_id) SELECT image_id, ? FROM image_tag WHERE tag_id = ?")) {
            ps.setInt(1, targetId);
            ps.setInt(2, sourceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        for (Tag child : listChildren(sourceId)) {
            reparent(child.id(), targetId);
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM tag WHERE id = ?")) {
            ps.setInt(1, sourceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Tag> get(int tagId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM tag WHERE id = ?")) {
            ps.setInt(1, tagId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Tag> listAll() {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM tag ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            List<Tag> tags = new ArrayList<>();
            while (rs.next()) tags.add(mapRow(rs));
            return tags;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Tag> listChildren(Integer parentId) {
        String sql = parentId == null
                ? "SELECT * FROM tag WHERE parent_id IS NULL ORDER BY name"
                : "SELECT * FROM tag WHERE parent_id = ? ORDER BY name";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (parentId != null) ps.setInt(1, parentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Tag> tags = new ArrayList<>();
                while (rs.next()) tags.add(mapRow(rs));
                return tags;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String fullPath(int tagId) {
        List<String> parts = new ArrayList<>();
        Optional<Tag> current = get(tagId);
        while (current.isPresent()) {
            parts.add(0, current.get().name());
            Integer parentId = current.get().parentId();
            current = parentId == null ? Optional.empty() : get(parentId);
        }
        return String.join(" / ", parts);
    }

    public boolean isDescendant(int ancestorId, int candidateId) {
        try (PreparedStatement ps = connection.prepareStatement("""
                WITH RECURSIVE sub(id) AS (
                    SELECT ?
                    UNION ALL
                    SELECT t.id FROM tag t JOIN sub ON t.parent_id = sub.id
                )
                SELECT 1 FROM sub WHERE id = ?""")) {
            ps.setInt(1, ancestorId);
            ps.setInt(2, candidateId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int countDirect(int tagId) {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COUNT(*) FROM image_tag it JOIN image i ON i.id = it.image_id
                WHERE it.tag_id = ? AND i.deleted_at IS NULL""")) {
            ps.setInt(1, tagId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int countIncludingDescendants(int tagId) {
        try (PreparedStatement ps = connection.prepareStatement("""
                WITH RECURSIVE sub(id) AS (
                    SELECT ?
                    UNION ALL
                    SELECT t.id FROM tag t JOIN sub ON t.parent_id = sub.id
                )
                SELECT COUNT(DISTINCT it.image_id) FROM image_tag it
                JOIN image i ON i.id = it.image_id
                WHERE it.tag_id IN (SELECT id FROM sub) AND i.deleted_at IS NULL""")) {
            ps.setInt(1, tagId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean siblingExists(String name, Integer parentId, Integer excludingId) {
        String sql = parentId == null
                ? "SELECT id FROM tag WHERE parent_id IS NULL AND name = ?"
                : "SELECT id FROM tag WHERE parent_id = ? AND name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (parentId == null) {
                ps.setString(1, name);
            } else {
                ps.setInt(1, parentId);
                ps.setString(2, name);
            }
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

    private Tag mapRow(ResultSet rs) throws SQLException {
        Object parent = rs.getObject("parent_id");
        return new Tag(rs.getInt("id"), rs.getString("name"),
                parent == null ? null : ((Number) parent).intValue(), rs.getLong("created_at"));
    }
}
