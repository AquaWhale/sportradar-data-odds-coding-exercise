package com.sportradar.scoreboard;

import com.sportradar.scoreboard.exception.MatchNotFoundException;
import com.sportradar.scoreboard.exception.MatchNotLiveException;
import com.sportradar.scoreboard.exception.TeamAlreadyPlayingException;
import java.util.List;

/**
 * A live football scoreboard for multiple simultaneous matches.
 *
 * <p>Implementations are thread-safe. A completed write is visible to any read that starts
 * afterwards. Rejected operations throw and leave the board unchanged.
 */
public interface Scoreboard {

    /**
     * Creates an empty scoreboard that retains the last 1000 finished matches.
     *
     * @return a new scoreboard
     */
    static Scoreboard create() {
        return new InMemoryScoreboard(InMemoryScoreboard.DEFAULT_MAX_FINISHED_MATCHES);
    }

    /**
     * Creates an empty scoreboard with a limit on retained finished matches. When the limit is
     * exceeded, the oldest finished match is evicted, and its ID becomes unknown to the board.
     *
     * @param maxFinishedMatches the most finished matches to retain, at least 1
     * @return a new scoreboard
     * @throws IllegalArgumentException if {@code maxFinishedMatches} is less than 1
     */
    static Scoreboard create(int maxFinishedMatches) {
        return new InMemoryScoreboard(maxFinishedMatches);
    }

    /**
     * Starts a match at 0-0. Team names are stripped of surrounding whitespace for display and
     * compared ignoring case.
     *
     * @param homeTeam the home team name, at most 1000 characters after stripping
     * @param awayTeam the away team name, at most 1000 characters after stripping
     * @return the ID of the new match
     * @throws IllegalArgumentException if a name is null, blank or too long, or both names are the
     *     same team
     * @throws TeamAlreadyPlayingException if either team is already in a live match
     */
    MatchId startMatch(String homeTeam, String awayTeam);

    /**
     * Sets the absolute score of both teams. Scores may be lowered, for corrections. Sending the
     * current score changes nothing. Callers are expected to deliver a match's updates in order,
     * because an older score delivered late is applied like a correction.
     *
     * @param id the match ID
     * @param homeScore the home team score, not negative
     * @param awayScore the away team score, not negative
     * @throws IllegalArgumentException if {@code id} is null or a score is negative
     * @throws MatchNotFoundException if the ID is unknown to this board or was evicted
     * @throws MatchNotLiveException if the match has finished and is still retained
     */
    void updateScore(MatchId id, int homeScore, int awayScore);

    /**
     * Finishes a live match at its current score, which may be 0-0, and frees both teams to start
     * another match.
     *
     * @param id the match ID
     * @throws IllegalArgumentException if {@code id} is null
     * @throws MatchNotFoundException if the ID is unknown to this board or was evicted
     * @throws MatchNotLiveException if the match has finished and is still retained
     */
    void finishMatch(MatchId id);

    /**
     * Returns the live matches, ordered by total score descending, then most recently started
     * first.
     *
     * @return an immutable list that later changes to the board do not affect, empty if no match
     *     is live
     */
    List<Match> getSummary();
}
