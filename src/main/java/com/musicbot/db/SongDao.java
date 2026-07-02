package com.musicbot.db;

import com.musicbot.model.Song;
import com.musicbot.search.SearchCriteria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SongDao {
    private final Connection conn;

    public SongDao(Database db) {
        this.conn = db.getConnection();
    }

    public synchronized long insert(Song s) throws SQLException {
        String sql = """
            INSERT INTO songs (file_id, unique_id, title, artist, album, genre, year,
                                track_number, duration_seconds, added_by_chat_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getFileId());
            ps.setString(2, s.getUniqueId());
            ps.setString(3, s.getTitle());
            ps.setString(4, s.getArtist());
            ps.setString(5, s.getAlbum());
            ps.setString(6, s.getGenre());
            ps.setString(7, s.getYear());
            ps.setString(8, s.getTrackNumber());
            if (s.getDurationSeconds() != null) {
                ps.setInt(9, s.getDurationSeconds());
            } else {
                ps.setNull(9, java.sql.Types.INTEGER);
            }
            ps.setLong(10, s.getAddedByChatId());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    s.setId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Insert failed, no generated key returned");
    }

    public Song getById(long id) throws SQLException {
        String sql = "SELECT * FROM songs WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<Song> getByPlaylist(long playlistId, int limit, int offset) throws SQLException {
        String sql = """
            SELECT s.* FROM songs s
            JOIN playlist_songs ps ON ps.song_id = s.id
            WHERE ps.playlist_id = ?
            ORDER BY s.artist, s.title
            LIMIT ? OFFSET ?
        """;
        List<Song> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, playlistId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    public int countByPlaylist(long playlistId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM playlist_songs WHERE playlist_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, playlistId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Advanced search: combines exact-ish LIKE filters (artist/title/album/genre/year/track)
     * with an OR'd free-text match across the main text fields.
     * Everything is parameterized -> safe against SQL injection.
     */
    public List<Song> search(SearchCriteria criteria, long requestingChatId, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM songs WHERE added_by_chat_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(requestingChatId);

        for (Map.Entry<String, String> f : criteria.getFilters().entrySet()) {
            String column = switch (f.getKey()) {
                case "artist" -> "artist";
                case "title" -> "title";
                case "album" -> "album";
                case "genre" -> "genre";
                case "year" -> "year";
                case "track" -> "track_number";
                default -> null;
            };
            if (column == null) continue;
            sql.append(" AND ").append(column).append(" LIKE ? COLLATE NOCASE");
            params.add("%" + f.getValue() + "%");
        }

        if (criteria.getFreeText() != null && !criteria.getFreeText().isBlank()) {
            sql.append(" AND (title LIKE ? COLLATE NOCASE OR artist LIKE ? COLLATE NOCASE " +
                       "OR album LIKE ? COLLATE NOCASE OR genre LIKE ? COLLATE NOCASE)");
            String like = "%" + criteria.getFreeText() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }

        sql.append(" ORDER BY artist, title LIMIT ?");
        params.add(limit);

        List<Song> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    private Song mapRow(ResultSet rs) throws SQLException {
        Song s = new Song();
        s.setId(rs.getLong("id"));
        s.setFileId(rs.getString("file_id"));
        s.setUniqueId(rs.getString("unique_id"));
        s.setTitle(rs.getString("title"));
        s.setArtist(rs.getString("artist"));
        s.setAlbum(rs.getString("album"));
        s.setGenre(rs.getString("genre"));
        s.setYear(rs.getString("year"));
        s.setTrackNumber(rs.getString("track_number"));
        int dur = rs.getInt("duration_seconds");
        s.setDurationSeconds(rs.wasNull() ? null : dur);
        s.setAddedByChatId(rs.getLong("added_by_chat_id"));
        return s;
    }
}
