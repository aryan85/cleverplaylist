package com.musicbot.model;

public class Playlist {
    private long id;
    private String name;
    private long ownerChatId;
    private boolean auto; // true = auto-generated (Genre:/Artist:), false = user-created

    public Playlist() {}

    public Playlist(String name, long ownerChatId, boolean auto) {
        this.name = name;
        this.ownerChatId = ownerChatId;
        this.auto = auto;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getOwnerChatId() { return ownerChatId; }
    public void setOwnerChatId(long ownerChatId) { this.ownerChatId = ownerChatId; }

    public boolean isAuto() { return auto; }
    public void setAuto(boolean auto) { this.auto = auto; }
}
