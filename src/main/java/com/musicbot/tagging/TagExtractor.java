package com.musicbot.tagging;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.File;

/**
 * Wraps jaudiotagger to pull the fields we care about out of an audio file.
 * Works for MP3, FLAC, OGG, M4A, WAV (with tags), etc.
 * Any field jaudiotagger can't find comes back null -- callers should
 * fall back to whatever Telegram itself provided (performer/title/duration).
 */
public class TagExtractor {

    public static class ExtractedTags {
        public String title;
        public String artist;
        public String album;
        public String genre;
        public String year;
        public String trackNumber;
        public Integer durationSeconds;
    }

    public ExtractedTags extract(File audioFile) {
        ExtractedTags result = new ExtractedTags();
        try {
            AudioFile f = AudioFileIO.read(audioFile);
            Tag tag = f.getTag();
            AudioHeader header = f.getAudioHeader();

            if (header != null) {
                result.durationSeconds = header.getTrackLength();
            }

            if (tag != null) {
                result.title = emptyToNull(tag.getFirst(FieldKey.TITLE));
                result.artist = emptyToNull(tag.getFirst(FieldKey.ARTIST));
                result.album = emptyToNull(tag.getFirst(FieldKey.ALBUM));
                result.genre = emptyToNull(tag.getFirst(FieldKey.GENRE));
                result.year = emptyToNull(tag.getFirst(FieldKey.YEAR));
                result.trackNumber = emptyToNull(tag.getFirst(FieldKey.TRACK));
            }
        } catch (Exception e) {
            // Corrupt tags, unsupported format, etc. -- not fatal, caller falls back to Telegram metadata.
            System.err.println("Tag extraction failed for " + audioFile.getName() + ": " + e.getMessage());
        }
        return result;
    }

    private String emptyToNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }
}
