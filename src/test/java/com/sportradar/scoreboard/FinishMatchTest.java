package com.sportradar.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportradar.scoreboard.exception.MatchNotFoundException;
import com.sportradar.scoreboard.exception.MatchNotLiveException;
import com.sportradar.scoreboard.exception.TeamAlreadyPlayingException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinishMatchTest {

    private final Scoreboard board = Scoreboard.create();

    @Test
    void finishedMatchLeavesSummary() {
        MatchId id = board.startMatch("Mexico", "Canada");
        board.updateScore(id, 2, 1);

        board.finishMatch(id);

        assertThat(board.getSummary()).isEmpty();
    }

    @Test
    void matchCanFinishAtNilNilWithNoUpdates() {
        MatchId id = board.startMatch("Mexico", "Canada");

        board.finishMatch(id);

        assertThat(board.getSummary()).isEmpty();
    }

    @Test
    void secondFinishGivesMatchNotLive() {
        MatchId id = board.startMatch("Mexico", "Canada");
        board.finishMatch(id);

        assertThatThrownBy(() -> board.finishMatch(id))
                .isInstanceOf(MatchNotLiveException.class)
                .hasMessage("Match " + id.value()
                        + " (Mexico vs Canada) is not live, it has already finished");
    }

    @Test
    void unknownIdGivesMatchNotFound() {
        MatchId unknown = new MatchId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertThatThrownBy(() -> board.finishMatch(unknown))
                .isInstanceOf(MatchNotFoundException.class)
                .hasMessage("No match found with ID 00000000-0000-0000-0000-000000000001");
    }

    @Test
    void evictedIdGivesMatchNotFound() {
        Scoreboard smallBoard = Scoreboard.create(1);
        MatchId first = smallBoard.startMatch("Mexico", "Canada");
        MatchId second = smallBoard.startMatch("Spain", "Brazil");
        smallBoard.finishMatch(first);
        smallBoard.finishMatch(second);

        assertThatThrownBy(() -> smallBoard.finishMatch(first))
                .isInstanceOf(MatchNotFoundException.class);
    }

    @Test
    void nullIdGivesSameErrorAsUpdate() {
        assertThatThrownBy(() -> board.finishMatch(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Match ID must not be null");
    }

    @Test
    void rejectedFinishLeavesBoardUnchanged() {
        MatchId id = board.startMatch("Mexico", "Canada");
        List<Match> before = board.getSummary();

        assertThatThrownBy(() -> board.finishMatch(new MatchId(UUID.randomUUID())))
                .isInstanceOf(MatchNotFoundException.class);

        assertThat(board.getSummary()).isEqualTo(before);
        assertThat(board.getSummary()).extracting(Match::id).containsExactly(id);
    }

    @Test
    void evictionDoesNotReleaseTeamsInRematch() {
        Scoreboard smallBoard = Scoreboard.create(1);
        MatchId old = smallBoard.startMatch("Mexico", "Canada");
        smallBoard.finishMatch(old);
        smallBoard.startMatch("Mexico", "Canada");

        smallBoard.finishMatch(smallBoard.startMatch("Spain", "Brazil"));

        assertThatThrownBy(() -> smallBoard.startMatch("Mexico", "Italy"))
                .isInstanceOf(TeamAlreadyPlayingException.class);
    }

    @Test
    void rejectedFinishOfOldIdDoesNotReleaseTeamsInRematch() {
        MatchId old = board.startMatch("Mexico", "Canada");
        board.finishMatch(old);
        board.startMatch("Mexico", "Canada");

        assertThatThrownBy(() -> board.finishMatch(old)).isInstanceOf(MatchNotLiveException.class);

        assertThatThrownBy(() -> board.startMatch("Mexico", "Italy"))
                .isInstanceOf(TeamAlreadyPlayingException.class);
    }

    @Test
    void finishReleasesTeamStartedWithDifferentSpelling() {
        board.finishMatch(board.startMatch(" Mexico ", "Canada"));

        assertThatCode(() -> board.startMatch("MEXICO", "Canada")).doesNotThrowAnyException();
    }
}
