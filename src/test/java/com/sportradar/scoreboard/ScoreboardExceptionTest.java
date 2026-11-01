package com.sportradar.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportradar.scoreboard.exception.MatchNotFoundException;
import com.sportradar.scoreboard.exception.MatchNotLiveException;
import com.sportradar.scoreboard.exception.ScoreboardException;
import com.sportradar.scoreboard.exception.TeamAlreadyPlayingException;
import org.junit.jupiter.api.Test;

// Sits outside the exception package so it proves callers can catch the base type.
class ScoreboardExceptionTest {

    @Test
    void matchNotFoundCanBeCaughtAsBaseType() {
        ScoreboardException exception = new MatchNotFoundException("message");

        assertThat(exception).isInstanceOf(RuntimeException.class).hasMessage("message");
    }

    @Test
    void matchNotLiveCanBeCaughtAsBaseType() {
        ScoreboardException exception = new MatchNotLiveException("message");

        assertThat(exception).isInstanceOf(RuntimeException.class).hasMessage("message");
    }

    @Test
    void teamAlreadyPlayingCanBeCaughtAsBaseType() {
        ScoreboardException exception = new TeamAlreadyPlayingException("message");

        assertThat(exception).isInstanceOf(RuntimeException.class).hasMessage("message");
    }
}
