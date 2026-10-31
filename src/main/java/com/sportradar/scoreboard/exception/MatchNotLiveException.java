package com.sportradar.scoreboard.exception;

/** The match has already finished and is still retained. */
public final class MatchNotLiveException extends ScoreboardException {

    /**
     * Creates an exception.
     *
     * @param message what went wrong
     */
    public MatchNotLiveException(String message) {
        super(message);
    }
}
