package com.musicbot.model;

/**
 * Represents a single track stored in the library.
 * fileId/uniqueId come from Telegram; everything else comes from
 * embedded audio tags (falling back to whatever Telegram itself sent,
 * e.g. the "performer"/"title" fields on an Audio message).
 */
public class Song {
    private long id;
    private String fileId;      // used to re-send the audio via Telegram, changes rarely
    private String uniqueId;    // stable identifier Telegram gives per file
    private String title;
    private String artist;
    private String album;
    private String genre;
    private String year;
    private String trackNumber;
    private Integer durationSeconds;
    private long addedByChatId;

    public Song() {}

    // --- getters / setters ---
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getUniqueId() { return uniqueId; }
    public void setUniqueId(String uniqueId) { this.uniqueId = uniqueId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }

    public String getTrackNumber() { return trackNumber; }
    public void setTrackNumber(String trackNumber) { this.trackNumber = trackNumber; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public long getAddedByChatId() { return addedByChatId; }
    public void setAddedByChatId(long addedByChatId) { this.addedByChatId = addedByChatId; }

    @Override
    public String toString() {
        String a = (artist == null || artist.isBlank()) ? "Unknown Artist" : artist;
        String t = (title == null || title.isBlank()) ? "Unknown Title" : title;
        String g = (genre == null || genre.isBlank()) ? "Unknown Genre" : genre;
        return t + " — " + a + " [" + g + "]";
    }
}
