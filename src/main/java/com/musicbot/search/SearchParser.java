package com.musicbot.search;

import java.util.Set;

public class SearchParser {

    private static final Set<String> VALID_KEYS = Set.of(
            "artist", "title", "album", "genre", "year", "track"
    );

    /**
     * Parses a query string into filters + free text.
     * Supports quoted values for multi-word filters, e.g.:
     *   artist:"guns n roses" genre:rock 1987
     */
    public static SearchCriteria parse(String rawQuery) {
        SearchCriteria criteria = new SearchCriteria();
        if (rawQuery == null || rawQuery.isBlank()) {
            return criteria;
        }

        StringBuilder freeText = new StringBuilder();
        int i = 0;
        int len = rawQuery.length();

        while (i < len) {
            // skip whitespace
            while (i < len && Character.isWhitespace(rawQuery.charAt(i))) i++;
            if (i >= len) break;

            int tokenStart = i;
            // read token, respecting quotes after a colon
            StringBuilder token = new StringBuilder();
            while (i < len && !Character.isWhitespace(rawQuery.charAt(i))) {
                char c = rawQuery.charAt(i);
                if (c == '"') {
                    i++; // skip opening quote
                    while (i < len && rawQuery.charAt(i) != '"') {
                        token.append(rawQuery.charAt(i));
                        i++;
                    }
                    i++; // skip closing quote
                } else {
                    token.append(c);
                    i++;
                }
            }

            String t = token.toString();
            int colonIdx = t.indexOf(':');
            if (colonIdx > 0) {
                String key = t.substring(0, colonIdx).toLowerCase();
                String value = t.substring(colonIdx + 1);
                if (VALID_KEYS.contains(key) && !value.isBlank()) {
                    criteria.addFilter(key, value);
                    continue;
                }
            }
            // not a recognized filter -> free text
            if (freeText.length() > 0) freeText.append(' ');
            freeText.append(t);
        }

        criteria.setFreeText(freeText.toString().trim());
        return criteria;
    }
}
