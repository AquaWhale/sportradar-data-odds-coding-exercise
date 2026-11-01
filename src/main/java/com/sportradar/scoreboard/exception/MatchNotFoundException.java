package com.sportradar.scoreboard.exception;

/** The match ID is unknown to this board, or its finished match has been evicted. */
public final class MatchNotFoundException extends ScoreboardException {

    /**
     * Creates an exception.
     *
     * @param message what went wrong
     */
    public MatchNotFoundException(String message) {
        super(message);
    }
}
