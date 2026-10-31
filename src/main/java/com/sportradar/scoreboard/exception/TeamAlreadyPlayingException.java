package com.sportradar.scoreboard.exception;

/** A team in a new match is already playing in a live match. */
public final class TeamAlreadyPlayingException extends ScoreboardException {

    /**
     * Creates an exception.
     *
     * @param message what went wrong
     */
    public TeamAlreadyPlayingException(String message) {
        super(message);
    }
}
