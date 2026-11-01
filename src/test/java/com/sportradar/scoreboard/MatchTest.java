package com.sportradar.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchTest {

    private final MatchId id = new MatchId(UUID.randomUUID());

    @Test
    void toStringShowsTeamsAndScores() {
        assertThat(new Match(id, "Mexico", "Canada", 0, 5)).hasToString("Mexico 0 - Canada 5");
    }

    @Test
    void totalScoreDoesNotOverflowAtMaximumScores() {
        Match match = new Match(id, "Mexico", "Canada", Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertThat(match.totalScore()).isEqualTo(4294967294L);
    }
}
