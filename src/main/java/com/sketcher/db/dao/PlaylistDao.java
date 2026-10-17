package com.sketcher.db.dao;

import com.sketcher.model.Playlist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class PlaylistDao {
    private final Connection connection;

    public PlaylistDao(Connection connection) {
        this.connection = connection;
    }

    public int create(String name) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO playlist(name, created_at) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create playlist " + name, e);
        }
    }

    public void rename(int playlistId, String name) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE playlist SET name = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setInt(2, playlistId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(int playlistId) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM playlist WHERE id = ?")) {
            ps.setInt(1, playlistId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Playlist> listAll() {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM playlist ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            List<Playlist> playlists = new ArrayList<>();
            while (rs.next()) {
                playlists.add(new Playlist(rs.getInt("id"), rs.getString("name"), rs.getLong("created_at")));
            }
            return playlists;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addItem(int playlistId, int imageId) {
        try (PreparedStatement count = connection.prepareStatement(
                "SELECT COALESCE(MAX(position), -1) FROM playlist_item WHERE playlist_id = ?")) {
            count.setInt(1, playlistId);
            int nextPosition;
            try (ResultSet rs = count.executeQuery()) {
                rs.next();
                nextPosition = rs.getInt(1) + 1;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO playlist_item(playlist_id, image_id, position) VALUES (?, ?, ?)")) {
                ps.setInt(1, playlistId);
                ps.setInt(2, imageId);
                ps.setInt(3, nextPosition);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeItem(int playlistId, int imageId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM playlist_item WHERE playlist_id = ? AND image_id = ?")) {
            ps.setInt(1, playlistId);
            ps.setInt(2, imageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Integer> listImageIds(int playlistId, boolean random) {
        String sql = "SELECT image_id FROM playlist_item WHERE playlist_id = ? ORDER BY "
                + (random ? "RANDOM()" : "position ASC");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, playlistId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getInt("image_id"));
                return ids;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
