# Telegram Music Playlist Bot

Forward or send audio files to this bot. It reads the tags embedded in the
file (title, artist, album, genre, year, track) with jaudiotagger and:

- Stores the song in a local SQLite database
- Auto-creates/updates a `Genre: X` playlist and an `Artist: Y` playlist
- Lets you build your own custom playlists too
- Supports advanced search across any tag field

## Requirements

- Java 17+
- Maven 3.8+
- A Telegram bot token from [@BotFather](https://t.me/BotFather)

## Build

```bash
mvn clean package
```

This produces a single runnable jar at `target/telegram-music-bot.jar`
(all dependencies bundled in via the shade plugin).

## Run

```bash
export BOT_USERNAME="YourBotUsername"     # without the @
export BOT_TOKEN="123456:ABC-your-token"
export DB_PATH="musicbot.db"              # optional, defaults to musicbot.db in the working dir

java -jar target/telegram-music-bot.jar
```

## Usage

Send or forward an audio file to the bot. It replies with the extracted
metadata and which playlists it filed the song into.

| Command | Description |
|---|---|
| `/playlists` | List your playlists (🔹 = auto-generated, 📁 = custom) |
| `/playlist <name>` | Show songs in a playlist |
| `/newplaylist <name>` | Create an empty custom playlist |
| `/addto <playlist> <song_id>` | Add a song to a playlist by its id |
| `/search <filters>` | Advanced search, see below |
| `/get <song_id>` | Re-send a specific song |

### Advanced search syntax

```
/search artist:queen genre:rock
/search album:"dark side of the moon" year:1973
/search genre:jazz love          <- combines a filter with free text
```

Recognized filter keys: `artist`, `title`, `album`, `genre`, `year`, `track`.
Anything else in the query is matched as free text against
title/artist/album/genre. Quote multi-word values.

## Notes / limitations

- **Metadata quality depends entirely on the file's embedded tags.** Many
  files forwarded through Telegram (especially voice-message-style audio
  or files someone re-encoded) have partial or missing tags — genre is
  the field most often empty. When genre/artist is missing, the bot still
  stores the song but skips auto-sorting into that particular playlist;
  you can still find it via `/search` on whatever fields *are* present,
  and add it to a custom playlist manually with `/addto`.
- Each chat's library is private to that chat (`added_by_chat_id`), so a
  group chat's library is shared among its members but not visible to
  other chats.
- SQLite is fine for a single-instance bot. If you ever run multiple
  bot instances against the same file, switch to Postgres/MySQL.
- File references (`file_id`) are re-sendable via Telegram indefinitely
  in practice, but Telegram doesn't guarantee this forever — if `/get`
  fails on an old song, that's why.
