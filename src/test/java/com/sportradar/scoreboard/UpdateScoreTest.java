package com.sportradar.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportradar.scoreboard.exception.MatchNotFoundException;
import com.sportradar.scoreboard.exception.MatchNotLiveException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateScoreTest {

    private final Scoreboard board = Scoreboard.create();

    @Test
    void updateIsReflectedInSummary() {
        MatchId id = board.startMatch("Mexico", "Canada");

        board.updateScore(id, 0, 5);

        assertThat(board.getSummary()).containsExactly(new Match(id, "Mexico", "Canada", 0, 5));
    }

    @Test
    void updateReordersBoardByNewTotal() {
        MatchId first = board.startMatch("Mexico", "Canada");
        MatchId second = board.startMatch("Spain", "Brazil");

        board.updateScore(first, 1, 0);

        assertThat(board.getSummary()).extracting(Match::id).containsExactly(first, second);
    }

    @Test
    void rejectsNegativeHomeScore() {
        MatchId id = board.startMatch("Mexico", "Canada");

        assertThatThrownBy(() -> board.updateScore(id, -1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Home score must not be negative: -1");
    }

    @Test
    void rejectsNegativeAwayScore() {
        MatchId id = board.startMatch("Mexico", "Canada");

        assertThatThrownBy(() -> board.updateScore(id, 0, -2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Away score must not be negative: -2");
    }

    @Test
    void bothNegativeScoresAreNamedInOneError() {
        MatchId id = board.startMatch("Mexico", "Canada");

        assertThatThrownBy(() -> board.updateScore(id, -1, -2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Scores must not be negative: home -1, away -2");
    }

    @Test
    void rejectsNullId() {
        assertThatThrownBy(() -> board.updateScore(null, 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Match ID must not be null");
    }

    @Test
    void acceptsMaximumScoresAndOrdersByLongTotal() {
        MatchId small = board.startMatch("Mexico", "Canada");
        MatchId large = board.startMatch("Spain", "Brazil");
        board.updateScore(small, 1, 0);

        board.updateScore(large, Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertThat(board.getSummary()).extracting(Match::id).containsExactly(large, small);
        assertThat(board.getSummary().get(0).totalScore()).isEqualTo(4294967294L);
    }

    @Test
    void acceptsZeroScores() {
        MatchId id = board.startMatch("Mexico", "Canada");
        board.updateScore(id, 2, 1);

        board.updateScore(id, 0, 0);

        assertThat(board.getSummary()).containsExactly(new Match(id, "Mexico", "Canada", 0, 0));
    }

    @Test
    void loweringScoreMovesMatchDownSummary() {
        MatchId first = board.startMatch("Mexico", "Canada");
        MatchId second = board.startMatch("Spain", "Brazil");
        board.updateScore(first, 3, 0);
        board.updateScore(second, 2, 0);

        board.updateScore(first, 1, 0);

        assertThat(board.getSummary()).extracting(Match::id).containsExactly(second, first);
    }

    @Test
    void resendingCurrentScorePublishesNothing() {
        MatchId id = board.startMatch("Mexico", "Canada");
        board.updateScore(id, 1, 0);
        List<Match> before = board.getSummary();

        assertThatCode(() -> board.updateScore(id, 1, 0)).doesNotThrowAnyException();

        assertThat(board.getSummary()).isSameAs(before);
    }

    @Test
    void unknownIdGivesMatchNotFound() {
        MatchId unknown = new MatchId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertThatThrownBy(() -> board.updateScore(unknown, 1, 0))
                .isInstanceOf(MatchNotFoundException.class)
                .hasMessage("No match found with ID 00000000-0000-0000-0000-000000000001");
    }

    @Test
    void idFromAnotherBoardGivesMatchNotFound() {
        MatchId idFromOtherBoard = Scoreboard.create().startMatch("Mexico", "Canada");

        assertThatThrownBy(() -> board.updateScore(idFromOtherBoard, 1, 0))
                .isInstanceOf(MatchNotFoundException.class);
    }

    @Test
    void retainedFinishedIdGivesMatchNotLive() {
        MatchId id = board.startMatch("Mexico", "Canada");
        board.updateScore(id, 2, 1);
        board.finishMatch(id);

        assertThatThrownBy(() -> board.updateScore(id, 3, 1))
                .isInstanceOf(MatchNotLiveException.class)
                .hasMessage("Match " + id.value()
                        + " (Mexico vs Canada) is not live, it has already finished");
    }

    @Test
    void resendingFinalScoreGivesMatchNotLive() {
        MatchId id = board.startMatch("Mexico", "Canada");
        board.updateScore(id, 2, 1);
        board.finishMatch(id);

        assertThatThrownBy(() -> board.updateScore(id, 2, 1))
                .isInstanceOf(MatchNotLiveException.class);
    }

    @Test
    void evictedIdGivesMatchNotFound() {
        Scoreboard smallBoard = Scoreboard.create(1);
        MatchId first = smallBoard.startMatch("Mexico", "Canada");
        MatchId second = smallBoard.startMatch("Spain", "Brazil");
        smallBoard.finishMatch(first);
        smallBoard.finishMatch(second);

        assertThatThrownBy(() -> smallBoard.updateScore(first, 1, 0))
                .isInstanceOf(MatchNotFoundException.class);
    }

    @Test
    void negativeScoreOnFinishedIdGivesInputError() {
        MatchId id = board.startMatch("Mexico", "Canada");
        board.finishMatch(id);

        assertThatThrownBy(() -> board.updateScore(id, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeScoreOnUnknownIdGivesInputError() {
        MatchId unknown = new MatchId(UUID.randomUUID());

        assertThatThrownBy(() -> board.updateScore(unknown, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullIdIsReportedBeforeNegativeScore() {
        assertThatThrownBy(() -> board.updateScore(null, -1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Match ID must not be null");
    }

    @Test
    void rejectedUpdateLeavesBoardUnchanged() {
        MatchId id = board.startMatch("Mexico", "Canada");
        board.updateScore(id, 1, 0);
        List<Match> before = board.getSummary();

        assertThatThrownBy(() -> board.updateScore(id, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(board.getSummary()).isEqualTo(before);
    }
}
