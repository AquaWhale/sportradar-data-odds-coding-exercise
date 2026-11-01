package com.sportradar.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TeamNameTest {

    @Test
    void rejectsNullWithLabel() {
        assertThatThrownBy(() -> TeamName.requireNotBlank(null, "Home"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Home team name must not be null");
    }

    @Test
    void rejectsBlankWithLabel() {
        assertThatThrownBy(() -> TeamName.requireNotBlank("  ", "Away"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Away team name must not be blank");
    }

    @Test
    void rejectsEmptyName() {
        assertThatThrownBy(() -> TeamName.requireNotBlank("", "Home"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Home team name must not be blank");
    }

    @Test
    void acceptsNameWithContent() {
        assertThatCode(() -> TeamName.requireNotBlank("Mexico", "Home"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsLongNameWithLengthOnly() {
        assertThatThrownBy(() -> TeamName.requireMaxLength("a".repeat(5230), "Home"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Home team name is 5230 characters, maximum is 1000");
    }

    @Test
    void rejectsNameOneOverCap() {
        assertThatThrownBy(() -> TeamName.requireMaxLength("a".repeat(1001), "Away"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Away team name is 1001 characters, maximum is 1000");
    }

    @Test
    void surroundingWhitespaceDoesNotCountTowardsCap() {
        assertThatCode(() -> TeamName.requireMaxLength("  " + "a".repeat(1000) + "  ", "Home"))
                .doesNotThrowAnyException();
    }

    @Test
    void ofKeepsStrippedCasingForDisplayAndLowersKey() {
        assertThat(TeamName.of(" Mexico ")).isEqualTo(new TeamName("Mexico", "mexico"));
    }
}
