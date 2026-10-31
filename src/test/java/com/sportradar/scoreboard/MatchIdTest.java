package com.sportradar.scoreboard;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MatchIdTest {

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new MatchId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Match ID value must not be null");
    }
}
