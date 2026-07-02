package com.musicbot.db;

import com.musicbot.model.Playlist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class PlaylistDao {
    private final Connection conn;

    public PlaylistDao(Database db) {
        this.conn = db.getConnection();
    }

    /** Finds a playlist by exact name for a given chat, or null if it doesn't exist. */
    public synchronized Playlist findByName(String name, long ownerChatId) throws SQLException {
        String sql = "SELECT * FROM playlists WHERE name = ? COLLATE NOCASE AND owner_chat_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setLong(2, ownerChatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public synchronized Playlist create(String name, long ownerChatId, boolean auto) throws SQLException {
        String sql = "INSERT INTO playlists (name, owner_chat_id, is_auto) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setLong(2, ownerChatId);
            ps.setInt(3, auto ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    Playlist p = new Playlist(name, ownerChatId, auto);
                    p.setId(keys.getLong(1));
                    return p;
                }
            }
        }
        throw new SQLException("Failed to create playlist");
    }

    /** Gets an existing playlist by name or creates it (used for auto Genre:/Artist: playlists). */
    public synchronized Playlist findOrCreate(String name, long ownerChatId, boolean auto) throws SQLException {
        Playlist existing = findByName(name, ownerChatId);
        if (existing != null) return existing;
        return create(name, ownerChatId, auto);
    }

    public List<Playlist> listForChat(long ownerChatId) throws SQLException {
        String sql = """
            SELECT p.*, COUNT(ps.song_id) AS song_count
            FROM playlists p
            LEFT JOIN playlist_songs ps ON ps.playlist_id = p.id
            WHERE p.owner_chat_id = ?
            GROUP BY p.id
            ORDER BY p.is_auto, p.name
        """;
        List<Playlist> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ownerChatId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    public synchronized void addSong(long playlistId, long songId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO playlist_songs (playlist_id, song_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, playlistId);
            ps.setLong(2, songId);
            ps.executeUpdate();
        }
    }

    public synchronized void removeSong(long playlistId, long songId) throws SQLException {
        String sql = "DELETE FROM playlist_songs WHERE playlist_id = ? AND song_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, playlistId);
            ps.setLong(2, songId);
            ps.executeUpdate();
        }
    }

    private Playlist mapRow(ResultSet rs) throws SQLException {
        Playlist p = new Playlist();
        p.setId(rs.getLong("id"));
        p.setName(rs.getString("name"));
        p.setOwnerChatId(rs.getLong("owner_chat_id"));
        p.setAuto(rs.getInt("is_auto") == 1);
        return p;
    }
}
