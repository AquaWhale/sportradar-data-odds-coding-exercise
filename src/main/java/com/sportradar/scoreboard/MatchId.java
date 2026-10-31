package com.sportradar.scoreboard;

import java.util.UUID;

/**
 * Identifies one match on a scoreboard. Returned by {@link Scoreboard#startMatch} and treated as
 * opaque by callers. An ID from another board is an unknown ID.
 *
 * @param value the unique value, never null
 */
public record MatchId(UUID value) {

    /**
     * Creates an ID.
     *
     * @throws IllegalArgumentException if {@code value} is null
     */
    public MatchId {
        if (value == null) {
            throw new IllegalArgumentException("Match ID value must not be null");
        }
    }
}
