package com.sportradar.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportradar.scoreboard.exception.MatchNotFoundException;
import com.sportradar.scoreboard.exception.MatchNotLiveException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinishedMatchesTest {

    private final Scoreboard board = Scoreboard.create();

    @Test
    void noFinishedMatchesGivesEmptyList() {
        board.startMatch("Mexico", "Canada");

        assertThat(board.getFinishedMatches()).isEmpty();
    }

    @Test
    void finishedMatchAppearsWithFinalScore() {
        MatchId id = board.startMatch("Mexico", "Canada");
        board.updateScore(id, 2, 1);

        board.finishMatch(id);

        assertThat(board.getFinishedMatches())
                .containsExactly(new Match(id, "Mexico", "Canada", 2, 1));
    }

    @Test
    void matchFinishedWithoutUpdatesAppearsAtNilNil() {
        MatchId id = board.startMatch("Mexico", "Canada");

        board.finishMatch(id);

        assertThat(board.getFinishedMatches())
                .containsExactly(new Match(id, "Mexico", "Canada", 0, 0));
    }

    @Test
    void mostRecentlyFinishedComesFirst() {
        MatchId first = board.startMatch("Mexico", "Canada");
        MatchId second = board.startMatch("Spain", "Brazil");

        board.finishMatch(first);
        board.finishMatch(second);

        assertThat(board.getFinishedMatches()).extracting(Match::id).containsExactly(second, first);
    }

    @Test
    void orderDependsOnFinishOrderNotStartOrderOrScore() {
        MatchId first = board.startMatch("Mexico", "Canada");
        MatchId second = board.startMatch("Spain", "Brazil");
        board.updateScore(first, 5, 5);

        board.finishMatch(second);
        board.finishMatch(first);

        assertThat(board.getFinishedMatches()).extracting(Match::id).containsExactly(first, second);
    }

    @Test
    void reachingCapEvictsOldestAndKeepsListAtCap() {
        Scoreboard smallBoard = Scoreboard.create(2);
        MatchId first = smallBoard.startMatch("Mexico", "Canada");
        MatchId second = smallBoard.startMatch("Spain", "Brazil");
        MatchId third = smallBoard.startMatch("Germany", "France");

        smallBoard.finishMatch(first);
        smallBoard.finishMatch(second);
        smallBoard.finishMatch(third);

        assertThat(smallBoard.getFinishedMatches())
                .extracting(Match::id)
                .containsExactly(third, second);
    }

    @Test
    void capOfOneKeepsOnlyNewestFinishedMatch() {
        Scoreboard smallBoard = Scoreboard.create(1);
        MatchId first = smallBoard.startMatch("Mexico", "Canada");
        MatchId second = smallBoard.startMatch("Spain", "Brazil");

        smallBoard.finishMatch(first);
        smallBoard.finishMatch(second);

        assertThat(smallBoard.getFinishedMatches()).extracting(Match::id).containsExactly(second);
    }

    @Test
    void returnedListCannotAlterTheBoard() {
        board.finishMatch(board.startMatch("Mexico", "Canada"));
        List<Match> finished = board.getFinishedMatches();
        Match extra = new Match(new MatchId(UUID.randomUUID()), "Spain", "Brazil", 0, 0);

        assertThatThrownBy(() -> finished.add(extra))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnedListDoesNotChangeAfterLaterFinishes() {
        MatchId first = board.startMatch("Mexico", "Canada");
        board.finishMatch(first);
        List<Match> finished = board.getFinishedMatches();

        board.finishMatch(board.startMatch("Spain", "Brazil"));

        assertThat(finished).extracting(Match::id).containsExactly(first);
    }

    @Test
    void rejectedUpdateLeavesFinalScoreAndOrderUnchanged() {
        MatchId first = board.startMatch("Mexico", "Canada");
        MatchId second = board.startMatch("Spain", "Brazil");
        board.updateScore(first, 2, 1);
        board.finishMatch(first);
        board.finishMatch(second);
        List<Match> before = board.getFinishedMatches();

        assertThatThrownBy(() -> board.updateScore(first, 9, 9))
                .isInstanceOf(MatchNotLiveException.class);

        assertThat(board.getFinishedMatches()).isEqualTo(before);
    }

    @Test
    void rejectedFinishLeavesFinishedListUnchanged() {
        Scoreboard smallBoard = Scoreboard.create(2);
        MatchId first = smallBoard.startMatch("Mexico", "Canada");
        MatchId second = smallBoard.startMatch("Spain", "Brazil");
        smallBoard.finishMatch(first);
        smallBoard.finishMatch(second);
        List<Match> before = smallBoard.getFinishedMatches();

        assertThatThrownBy(() -> smallBoard.finishMatch(first))
                .isInstanceOf(MatchNotLiveException.class);
        assertThatThrownBy(() -> smallBoard.finishMatch(new MatchId(UUID.randomUUID())))
                .isInstanceOf(MatchNotFoundException.class);

        assertThat(smallBoard.getFinishedMatches()).isEqualTo(before);
    }
}
