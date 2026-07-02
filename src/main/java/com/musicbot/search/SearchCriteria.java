package com.musicbot.search;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds parsed filters from a query like:
 *   artist:queen genre:rock year:1975 love
 *
 * Recognized filter keys map directly to song columns: artist, title,
 * album, genre, year, track. Any words that aren't "key:value" are
 * treated as free text and matched against title/artist/album/genre.
 */
public class SearchCriteria {
    private final Map<String, String> filters = new LinkedHashMap<>();
    private String freeText = "";

    public void addFilter(String key, String value) {
        filters.put(key.toLowerCase(), value);
    }

    public Map<String, String> getFilters() {
        return filters;
    }

    public String getFreeText() {
        return freeText;
    }

    public void setFreeText(String freeText) {
        this.freeText = freeText;
    }

    public boolean isEmpty() {
        return filters.isEmpty() && (freeText == null || freeText.isBlank());
    }
}
