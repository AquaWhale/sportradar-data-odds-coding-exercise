package com.sportradar.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportradar.scoreboard.exception.TeamAlreadyPlayingException;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StartMatchTest {

    private final Scoreboard board = Scoreboard.create();

    @Test
    void newMatchStartsAtNilNilAndAppearsInSummary() {
        MatchId id = board.startMatch("Mexico", "Canada");

        assertThat(board.getSummary())
                .containsExactly(new Match(id, "Mexico", "Canada", 0, 0));
    }

    @Test
    void emptyBoardHasEmptySummary() {
        assertThat(board.getSummary()).isEmpty();
    }

    @Test
    void rejectsNullHomeTeam() {
        assertThatThrownBy(() -> board.startMatch(null, "Canada"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Home team name must not be null");
    }

    @Test
    void rejectsNullAwayTeam() {
        assertThatThrownBy(() -> board.startMatch("Mexico", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Away team name must not be null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void rejectsBlankHomeTeam(String name) {
        assertThatThrownBy(() -> board.startMatch(name, "Canada"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Home team name must not be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void rejectsBlankAwayTeam(String name) {
        assertThatThrownBy(() -> board.startMatch("Mexico", name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Away team name must not be blank");
    }

    @Test
    void acceptsNameAtCap() {
        assertThatCode(() -> board.startMatch("a".repeat(1000), "Canada"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNameOverCapWithLengthOnly() {
        assertThatThrownBy(() -> board.startMatch("a".repeat(1001), "Canada"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Home team name is 1001 characters, maximum is 1000");
    }

    @Test
    void surroundingWhitespaceDoesNotCountTowardsCap() {
        assertThatCode(() -> board.startMatch("  " + "a".repeat(1000) + "  ", "Canada"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsIdenticalTeams() {
        assertThatThrownBy(() -> board.startMatch("Spain", "Spain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Home and away teams must be different: Spain");
    }

    @Test
    void rejectsSameTeamWithDifferentCaseAndWhitespace() {
        assertThatThrownBy(() -> board.startMatch("Spain", " SPAIN "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Home and away teams must be different: Spain");
    }

    @Test
    void rejectsExactDuplicateOfLiveMatch() {
        board.startMatch("Mexico", "Canada");

        assertThatThrownBy(() -> board.startMatch("Mexico", "Canada"))
                .isInstanceOf(TeamAlreadyPlayingException.class)
                .hasMessage("Team 'Mexico' is already playing in live match Mexico vs Canada; "
                        + "team 'Canada' is already playing in live match Mexico vs Canada");
    }

    @Test
    void rejectsReversedPairingNamingBothTeams() {
        board.startMatch("Mexico", "Canada");

        assertThatThrownBy(() -> board.startMatch("Canada", "Mexico"))
                .isInstanceOf(TeamAlreadyPlayingException.class)
                .hasMessage("Team 'Canada' is already playing in live match Mexico vs Canada; "
                        + "team 'Mexico' is already playing in live match Mexico vs Canada");
    }

    @Test
    void rejectsHomeTeamAlreadyLive() {
        board.startMatch("Mexico", "Canada");

        assertThatThrownBy(() -> board.startMatch("Mexico", "Spain"))
                .isInstanceOf(TeamAlreadyPlayingException.class)
                .hasMessage("Team 'Mexico' is already playing in live match Mexico vs Canada");
    }

    @Test
    void rejectsAwayTeamAlreadyLive() {
        board.startMatch("Mexico", "Canada");

        assertThatThrownBy(() -> board.startMatch("Spain", "Canada"))
                .isInstanceOf(TeamAlreadyPlayingException.class)
                .hasMessage("Team 'Canada' is already playing in live match Mexico vs Canada");
    }

    @Test
    void overlapIgnoresCaseAndWhitespaceAndReportsStoredName() {
        board.startMatch("Mexico", "Canada");

        assertThatThrownBy(() -> board.startMatch(" mexico ", "Spain"))
                .isInstanceOf(TeamAlreadyPlayingException.class)
                .hasMessage("Team 'Mexico' is already playing in live match Mexico vs Canada");
    }

    @Test
    void oneErrorNamesBothTeamsLiveInDifferentMatches() {
        board.startMatch("Mexico", "Canada");
        board.startMatch("Spain", "Brazil");

        assertThatThrownBy(() -> board.startMatch("Mexico", "Spain"))
                .isInstanceOf(TeamAlreadyPlayingException.class)
                .hasMessage("Team 'Mexico' is already playing in live match Mexico vs Canada; "
                        + "team 'Spain' is already playing in live match Spain vs Brazil");
    }

    @Test
    void summaryShowsStrippedNamesAsFirstGiven() {
        board.startMatch(" Mexico ", "  CANADA");

        assertThat(board.getSummary()).extracting(Match::homeTeam).containsExactly("Mexico");
        assertThat(board.getSummary()).extracting(Match::awayTeam).containsExactly("CANADA");
    }

    @Test
    void innerSpacesCountAsDifferentTeams() {
        board.startMatch("South Korea", "Mexico");

        assertThatCode(() -> board.startMatch("South  Korea", "Canada"))
                .doesNotThrowAnyException();
    }

    @Test
    void differentlyEncodedNamesCountAsDifferentTeams() {
        board.startMatch("Curaçao", "Mexico");

        assertThatCode(() -> board.startMatch("Curaçao", "Canada"))
                .doesNotThrowAnyException();
    }

    @Test
    void caseFoldingLimitTreatsTurkiyeSpellingsAsDifferentTeams() {
        board.startMatch("TÜRKİYE", "Mexico");

        assertThatCode(() -> board.startMatch("Türkiye", "Canada"))
                .doesNotThrowAnyException();
    }

    @Test
    void nonBreakingSpaceIsNotStripped() {
        board.startMatch("Spain", "Mexico");

        assertThatCode(() -> board.startMatch("Spain ", "Canada"))
                .doesNotThrowAnyException();
    }

    @Test
    void nameOfOnlyNonBreakingSpaceIsAccepted() {
        assertThatCode(() -> board.startMatch(" ", "Canada")).doesNotThrowAnyException();
    }

    @Test
    void rejectedStartLeavesBoardUnchangedAndReservesNoTeam() {
        board.startMatch("Mexico", "Canada");
        List<Match> before = board.getSummary();

        assertThatThrownBy(() -> board.startMatch("Spain", "Mexico"))
                .isInstanceOf(TeamAlreadyPlayingException.class);

        assertThat(board.getSummary()).isEqualTo(before);
        assertThatCode(() -> board.startMatch("Spain", "Brazil")).doesNotThrowAnyException();
    }

    @Test
    void nullCheckRunsForBothTeamsBeforeLengthCheck() {
        assertThatThrownBy(() -> board.startMatch("a".repeat(1001), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Away team name must not be null");
    }

    @Test
    void lengthCheckRunsBeforeSameTeamCheck() {
        String name = "a".repeat(1001);

        assertThatThrownBy(() -> board.startMatch(name, name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Home team name is 1001 characters, maximum is 1000");
    }

    @Test
    void sameTeamCheckRunsBeforeLiveCheck() {
        board.startMatch("Mexico", "Canada");

        assertThatThrownBy(() -> board.startMatch("Mexico", "Mexico"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Home and away teams must be different: Mexico");
    }

    @Test
    void bothNamesNullReportsHome() {
        assertThatThrownBy(() -> board.startMatch(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Home team name must not be null");
    }

    @Test
    void unicodeWhitespaceIsStripped() {
        board.startMatch(" Mexico　", "Canada");

        assertThat(board.getSummary()).extracting(Match::homeTeam).containsExactly("Mexico");
    }

    @Test
    void comparisonDoesNotDependOnDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            board.startMatch("INDIA", "Canada");

            assertThatThrownBy(() -> board.startMatch("india", "Spain"))
                    .isInstanceOf(TeamAlreadyPlayingException.class);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void percentSignInNameIsPrintedLiterally() {
        board.startMatch("100%", "Canada");

        assertThatThrownBy(() -> board.startMatch("100%", "Spain"))
                .isInstanceOf(TeamAlreadyPlayingException.class)
                .hasMessage("Team '100%' is already playing in live match 100% vs Canada");
    }
}
