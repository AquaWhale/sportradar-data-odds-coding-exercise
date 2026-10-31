package com.sportradar.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SummaryTest {

    private final Scoreboard board = Scoreboard.create();

    @Test
    void briefExampleGivesExpectedOrder() {
        board.updateScore(board.startMatch("Mexico", "Canada"), 0, 5);
        board.updateScore(board.startMatch("Spain", "Brazil"), 10, 2);
        board.updateScore(board.startMatch("Germany", "France"), 2, 2);
        board.updateScore(board.startMatch("Uruguay", "Italy"), 6, 6);
        board.updateScore(board.startMatch("Argentina", "Australia"), 3, 1);

        assertThat(board.getSummary()).extracting(Match::toString).containsExactly(
                "Uruguay 6 - Italy 6",
                "Spain 10 - Brazil 2",
                "Mexico 0 - Canada 5",
                "Argentina 3 - Australia 1",
                "Germany 2 - France 2");
    }

    @Test
    void equalTotalsPutMostRecentlyStartedFirst() {
        MatchId first = board.startMatch("Mexico", "Canada");
        MatchId second = board.startMatch("Spain", "Brazil");
        MatchId third = board.startMatch("Germany", "France");

        assertThat(board.getSummary()).extracting(Match::id).containsExactly(third, second, first);
    }

    @Test
    void matchesReachingATieThroughUpdatesPutMostRecentlyStartedFirst() {
        MatchId first = board.startMatch("Mexico", "Canada");
        MatchId second = board.startMatch("Spain", "Brazil");
        board.updateScore(first, 3, 0);
        board.updateScore(second, 1, 1);

        board.updateScore(second, 2, 1);

        assertThat(board.getSummary()).extracting(Match::id).containsExactly(second, first);
    }

    @Test
    void updatesDoNotChangeStartOrderTiebreak() {
        MatchId older = board.startMatch("Mexico", "Canada");
        MatchId newer = board.startMatch("Spain", "Brazil");

        // The newer match goes first: the opposite order would hide a remove-and-re-add bug.
        board.updateScore(newer, 1, 0);
        board.updateScore(older, 1, 0);

        assertThat(board.getSummary()).extracting(Match::id).containsExactly(newer, older);
    }

    @Test
    void noOpDoesNotChangeStartOrderTiebreak() {
        MatchId older = board.startMatch("Mexico", "Canada");
        MatchId newer = board.startMatch("Spain", "Brazil");
        board.updateScore(newer, 1, 0);
        board.updateScore(older, 1, 0);

        board.updateScore(older, 1, 0);

        assertThat(board.getSummary()).extracting(Match::id).containsExactly(newer, older);
    }

    @Test
    void returnedSummaryCannotAlterTheBoard() {
        board.startMatch("Mexico", "Canada");
        List<Match> summary = board.getSummary();
        Match extra = new Match(new MatchId(UUID.randomUUID()), "Spain", "Brazil", 0, 0);

        assertThatThrownBy(() -> summary.add(extra))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnedSummaryDoesNotChangeAfterLaterUpdates() {
        MatchId id = board.startMatch("Mexico", "Canada");
        List<Match> summary = board.getSummary();

        board.updateScore(id, 1, 0);

        assertThat(summary).containsExactly(new Match(id, "Mexico", "Canada", 0, 0));
    }

    @Test
    void summaryIsCorrectAtHundredLiveMatches() {
        for (int i = 0; i < 100; i++) {
            MatchId id = board.startMatch("Home " + i, "Away " + i);
            board.updateScore(id, i, 0);
        }

        assertThat(board.getSummary()).hasSize(100);
        assertThat(board.getSummary())
                .extracting(Match::totalScore)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }
}
