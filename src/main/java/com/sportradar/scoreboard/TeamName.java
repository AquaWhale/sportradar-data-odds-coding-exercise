package com.sportradar.scoreboard;

import java.util.Locale;

/** A validated team name: the stripped name for display and the key for comparing teams. */
record TeamName(String displayName, String key) {

    static final int MAX_LENGTH = 1000;

    static void requireNotBlank(String raw, String label) {
        if (raw == null) {
            throw new IllegalArgumentException(label + " team name must not be null");
        }
        if (raw.isBlank()) {
            throw new IllegalArgumentException(label + " team name must not be blank");
        }
    }

    static void requireMaxLength(String raw, String label) {
        int length = raw.strip().length();
        if (length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    label + " team name is " + length + " characters, maximum is " + MAX_LENGTH);
        }
    }

    static TeamName of(String raw) {
        String stripped = raw.strip();
        return new TeamName(stripped, stripped.toLowerCase(Locale.ROOT));
    }
}
