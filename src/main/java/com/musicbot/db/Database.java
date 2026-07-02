package com.musicbot.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Single shared SQLite connection + schema bootstrap.
 * SQLite handles one writer at a time just fine for a single-server bot;
 * we synchronize writes at the DAO level to keep it simple and safe.
 */
public class Database {
    private final Connection connection;

    public Database(String dbFilePath) {
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFilePath);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON;");
            }
            initSchema();
        } catch (ClassNotFoundException | SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    private void initSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS songs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_id TEXT NOT NULL,
                    unique_id TEXT,
                    title TEXT,
                    artist TEXT,
                    album TEXT,
                    genre TEXT,
                    year TEXT,
                    track_number TEXT,
                    duration_seconds INTEGER,
                    added_by_chat_id INTEGER NOT NULL,
                    added_at TEXT DEFAULT CURRENT_TIMESTAMP
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS playlists (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    owner_chat_id INTEGER NOT NULL,
                    is_auto INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(name, owner_chat_id)
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS playlist_songs (
                    playlist_id INTEGER NOT NULL,
                    song_id INTEGER NOT NULL,
                    PRIMARY KEY (playlist_id, song_id),
                    FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
                    FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE
                );
            """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_songs_artist ON songs(artist);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_songs_genre ON songs(genre);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_songs_album ON songs(album);");
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {}
    }
}
