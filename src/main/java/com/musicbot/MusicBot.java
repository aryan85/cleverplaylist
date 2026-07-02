package com.musicbot;

import com.musicbot.db.Database;
import com.musicbot.db.PlaylistDao;
import com.musicbot.db.SongDao;
import com.musicbot.model.Playlist;
import com.musicbot.model.Song;
import com.musicbot.search.SearchCriteria;
import com.musicbot.search.SearchParser;
import com.musicbot.tagging.TagExtractor;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Audio;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

public class MusicBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final String botToken;
    private final SongDao songDao;
    private final PlaylistDao playlistDao;
    private final TagExtractor tagExtractor = new TagExtractor();

    public MusicBot(String botUsername, String botToken, Database db) {
        this.botUsername = botUsername;
        this.botToken = botToken;
        this.songDao = new SongDao(db);
        this.playlistDao = new PlaylistDao(db);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() == false) return;
        Message msg = update.getMessage();
        long chatId = msg.getChatId();

        try {
            if (msg.hasAudio()) {
                handleAudio(chatId, msg.getAudio());
            } else if (msg.hasDocument() && isAudioDocument(msg.getDocument())) {
                handleDocument(chatId, msg.getDocument());
            } else if (msg.hasText()) {
                handleText(chatId, msg.getText());
            }
        } catch (Exception e) {
            e.printStackTrace();
            reply(chatId, "Something went wrong handling that: " + e.getMessage());
        }
    }

    private boolean isAudioDocument(Document doc) {
        return doc.getMimeType() != null && doc.getMimeType().startsWith("audio/");
    }

    // ---------------------------------------------------------------
    // Audio ingestion
    // ---------------------------------------------------------------

    private void handleAudio(long chatId, Audio audio) throws TelegramApiException, SQLException {
        File downloaded = downloadTelegramFile(audio.getFileId());
        TagExtractor.ExtractedTags tags = tagExtractor.extract(downloaded);
        downloaded.delete(); // cleanup temp file, we keep the file_id for future sends

        Song song = new Song();
        song.setFileId(audio.getFileId());
        song.setUniqueId(audio.getFileUniqueId());
        song.setAddedByChatId(chatId);

        // Prefer embedded tags, fall back to whatever Telegram itself carries on the Audio object.
        song.setTitle(firstNonBlank(tags.title, audio.getTitle()));
        song.setArtist(firstNonBlank(tags.artist, audio.getPerformer()));
        song.setAlbum(tags.album);
        song.setGenre(tags.genre);
        song.setYear(tags.year);
        song.setTrackNumber(tags.trackNumber);
        song.setDurationSeconds(tags.durationSeconds != null ? tags.durationSeconds : audio.getDuration());

        ingest(chatId, song);
    }

    private void handleDocument(long chatId, Document doc) throws TelegramApiException, SQLException {
        File downloaded = downloadTelegramFile(doc.getFileId());
        TagExtractor.ExtractedTags tags = tagExtractor.extract(downloaded);
        downloaded.delete();

        Song song = new Song();
        song.setFileId(doc.getFileId());
        song.setUniqueId(doc.getFileUniqueId());
        song.setAddedByChatId(chatId);
        song.setTitle(firstNonBlank(tags.title, doc.getFileName()));
        song.setArtist(tags.artist);
        song.setAlbum(tags.album);
        song.setGenre(tags.genre);
        song.setYear(tags.year);
        song.setTrackNumber(tags.trackNumber);
        song.setDurationSeconds(tags.durationSeconds);

        ingest(chatId, song);
    }

    private void ingest(long chatId, Song song) throws SQLException {
        songDao.insert(song);

        StringBuilder addedTo = new StringBuilder();

        if (song.getGenre() != null) {
            Playlist genrePlaylist = playlistDao.findOrCreate("Genre: " + song.getGenre(), chatId, true);
            playlistDao.addSong(genrePlaylist.getId(), song.getId());
            addedTo.append("\n• ").append(genrePlaylist.getName());
        }
        if (song.getArtist() != null) {
            Playlist artistPlaylist = playlistDao.findOrCreate("Artist: " + song.getArtist(), chatId, true);
            playlistDao.addSong(artistPlaylist.getId(), song.getId());
            addedTo.append("\n• ").append(artistPlaylist.getName());
        }

        String summary = "Added: " + song +
                (song.getDurationSeconds() != null ? " (" + formatDuration(song.getDurationSeconds()) + ")" : "") +
                "\nSong id: " + song.getId() +
                (addedTo.length() > 0 ? "\nAuto-sorted into:" + addedTo : "\n(no genre/artist tag found, not auto-sorted -- you can still add it manually with /addto)");

        reply(chatId, summary);
    }

    // ---------------------------------------------------------------
    // Text commands
    // ---------------------------------------------------------------

    private void handleText(long chatId, String text) throws SQLException {
        String[] parts = text.trim().split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String rest = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "/start", "/help" -> reply(chatId, helpText());
            case "/playlists" -> handleListPlaylists(chatId);
            case "/playlist" -> handleShowPlaylist(chatId, rest);
            case "/newplaylist" -> handleNewPlaylist(chatId, rest);
            case "/addto" -> handleAddTo(chatId, rest);
            case "/search" -> handleSearch(chatId, rest);
            case "/get" -> handleGet(chatId, rest);
            default -> {
                if (text.startsWith("/")) {
                    reply(chatId, "Unknown command. Send /help to see what I can do.");
                }
                // plain text with no leading slash is ignored
            }
        }
    }

    private String helpText() {
        return """
            🎵 Forward or send me audio files and I'll file them into playlists automatically by genre and artist (based on the tags embedded in the file).

            Commands:
            /playlists - list your playlists
            /playlist <name> - show songs in a playlist
            /newplaylist <name> - create an empty custom playlist
            /addto <playlist> <song_id> - add a song (by id) to a playlist
            /search <filters> - advanced search, e.g.:
                /search artist:queen genre:rock
                /search album:"dark side" year:1973
                /search love (free text across title/artist/album/genre)
            /get <song_id> - re-send a specific song by its id
            """;
    }

    private void handleListPlaylists(long chatId) throws SQLException {
        List<Playlist> playlists = playlistDao.listForChat(chatId);
        if (playlists.isEmpty()) {
            reply(chatId, "No playlists yet -- forward me some music or use /newplaylist <name>.");
            return;
        }
        StringBuilder sb = new StringBuilder("Your playlists:\n");
        for (Playlist p : playlists) {
            int count = songDao.countByPlaylist(p.getId());
            sb.append(p.isAuto() ? "🔹 " : "📁 ").append(p.getName())
              .append(" (").append(count).append(" songs)\n");
        }
        reply(chatId, sb.toString());
    }

    private void handleShowPlaylist(long chatId, String name) throws SQLException {
        if (name.isBlank()) {
            reply(chatId, "Usage: /playlist <name>");
            return;
        }
        Playlist p = playlistDao.findByName(name, chatId);
        if (p == null) {
            reply(chatId, "No playlist named \"" + name + "\". Check /playlists for the exact name.");
            return;
        }
        List<Song> songs = songDao.getByPlaylist(p.getId(), 30, 0);
        if (songs.isEmpty()) {
            reply(chatId, "\"" + p.getName() + "\" is empty.");
            return;
        }
        StringBuilder sb = new StringBuilder(p.getName()).append(":\n");
        for (Song s : songs) {
            sb.append("#").append(s.getId()).append(" - ").append(s).append("\n");
        }
        int total = songDao.countByPlaylist(p.getId());
        if (total > songs.size()) {
            sb.append("\n(showing first ").append(songs.size()).append(" of ").append(total).append(")");
        }
        sb.append("\n\nUse /get <id> to have me re-send any of these.");
        reply(chatId, sb.toString());
    }

    private void handleNewPlaylist(long chatId, String name) throws SQLException {
        if (name.isBlank()) {
            reply(chatId, "Usage: /newplaylist <name>");
            return;
        }
        Playlist existing = playlistDao.findByName(name, chatId);
        if (existing != null) {
            reply(chatId, "You already have a playlist called \"" + name + "\".");
            return;
        }
        Playlist p = playlistDao.create(name, chatId, false);
        reply(chatId, "Created playlist \"" + p.getName() + "\". Add songs with /addto " + p.getName() + " <song_id>.");
    }

    private void handleAddTo(long chatId, String rest) throws SQLException {
        String[] args = rest.trim().split("\\s+");
        if (args.length < 2) {
            reply(chatId, "Usage: /addto <playlist name> <song_id>");
            return;
        }
        String songIdStr = args[args.length - 1];
        String playlistName = rest.substring(0, rest.lastIndexOf(songIdStr)).trim();

        long songId;
        try {
            songId = Long.parseLong(songIdStr);
        } catch (NumberFormatException e) {
            reply(chatId, "The last argument should be a numeric song id (see /search or /playlist output).");
            return;
        }

        Song song = songDao.getById(songId);
        if (song == null || song.getAddedByChatId() != chatId) {
            reply(chatId, "No song with id " + songId + " found in your library.");
            return;
        }

        Playlist playlist = playlistDao.findOrCreate(playlistName, chatId, false);
        playlistDao.addSong(playlist.getId(), songId);
        reply(chatId, "Added \"" + song + "\" to \"" + playlist.getName() + "\".");
    }

    private void handleSearch(long chatId, String query) throws SQLException {
        SearchCriteria criteria = SearchParser.parse(query);
        if (criteria.isEmpty()) {
            reply(chatId, "Usage: /search artist:name genre:name album:name year:1999 track:3 freeword...\nAll parts are optional and combinable.");
            return;
        }
        List<Song> results = songDao.search(criteria, chatId, 25);
        if (results.isEmpty()) {
            reply(chatId, "No songs matched that search.");
            return;
        }
        StringBuilder sb = new StringBuilder("Found ").append(results.size()).append(" song(s):\n");
        for (Song s : results) {
            sb.append("#").append(s.getId()).append(" - ").append(s).append("\n");
        }
        sb.append("\nUse /get <id> to have me re-send any of these.");
        reply(chatId, sb.toString());
    }

    private void handleGet(long chatId, String idStr) throws SQLException {
        long songId;
        try {
            songId = Long.parseLong(idStr.trim());
        } catch (NumberFormatException e) {
            reply(chatId, "Usage: /get <song_id>");
            return;
        }
        Song song = songDao.getById(songId);
        if (song == null || song.getAddedByChatId() != chatId) {
            reply(chatId, "No song with id " + songId + " found in your library.");
            return;
        }
        try {
            SendAudio sendAudio = new SendAudio();
            sendAudio.setChatId(chatId);
            sendAudio.setAudio(new InputFile(song.getFileId()));
            sendAudio.setCaption(song.toString());
            execute(sendAudio);
        } catch (TelegramApiException e) {
            reply(chatId, "Couldn't re-send that file (Telegram may have expired the file reference).");
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private File downloadTelegramFile(String fileId) throws TelegramApiException {
        org.telegram.telegrambots.meta.api.objects.File tgFile = execute(new GetFile(fileId));
        return downloadFile(tgFile);
    }

    private void reply(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private String formatDuration(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
