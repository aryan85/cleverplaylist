package com.musicbot;

import com.musicbot.db.Database;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class BotRunner {
    public static void main(String[] args) {
        Properties props = loadProperties();

        String botUsername = getRequired(props, "bot.username");
        String botToken = getRequired(props, "bot.token");
        String dbPath = props.getProperty("db.path", "musicbot.db");

        Database db = new Database(dbPath);
        MusicBot bot = new MusicBot(botUsername, botToken, db);

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            System.out.println("Bot started as @" + botUsername + " (DB: " + dbPath + ")");
        } catch (TelegramApiException e) {
            throw new RuntimeException("Failed to start bot", e);
        }
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = BotRunner.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in == null) {
                throw new IllegalStateException(
                        "application.properties not found on the classpath (expected in src/main/resources)");
            }
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
        return props;
    }

    private static String getRequired(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + key);
        }
        return value;
    }
}