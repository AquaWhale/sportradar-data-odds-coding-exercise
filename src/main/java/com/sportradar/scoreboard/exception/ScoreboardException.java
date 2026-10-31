package com.sportradar.scoreboard.exception;

/**
 * Base type for operations rejected because of the scoreboard's state. Invalid input is reported
 * with {@link IllegalArgumentException} instead.
 */
public abstract class ScoreboardException extends RuntimeException {

    /**
     * Creates an exception.
     *
     * @param message what went wrong
     */
    protected ScoreboardException(String message) {
        super(message);
    }
}
