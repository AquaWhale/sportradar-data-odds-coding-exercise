package com.sportradar.scoreboard;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportradar.scoreboard.exception.MatchNotFoundException;
import com.sportradar.scoreboard.exception.MatchNotLiveException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurationTest {

    @Test
    void defaultCapKeepsLastThousandFinishedMatches() {
        Scoreboard board = Scoreboard.create();
        List<MatchId> ids = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            MatchId id = board.startMatch("Home " + i, "Away " + i);
            ids.add(id);
            board.finishMatch(id);
        }

        assertThatThrownBy(() -> board.finishMatch(ids.get(0)))
                .isInstanceOf(MatchNotFoundException.class);
        assertThatThrownBy(() -> board.finishMatch(ids.get(1)))
                .isInstanceOf(MatchNotLiveException.class);
    }

    @Test
    void rejectsZeroCap() {
        assertThatThrownBy(() -> Scoreboard.create(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum finished matches must be at least 1: 0");
    }

    @Test
    void rejectsNegativeCap() {
        assertThatThrownBy(() -> Scoreboard.create(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum finished matches must be at least 1: -1");
    }
}
