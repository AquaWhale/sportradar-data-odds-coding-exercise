package com.sportradar.scoreboard;

import com.sportradar.scoreboard.exception.MatchNotFoundException;
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
     * Creates an empty scoreboard.
     *
     * @return a new scoreboard
     */
    static Scoreboard create() {
        return new InMemoryScoreboard();
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
     * @throws MatchNotFoundException if the ID is unknown to this board
     */
    void updateScore(MatchId id, int homeScore, int awayScore);

    /**
     * Returns the live matches, ordered by total score descending, then most recently started
     * first.
     *
     * @return an immutable list that later changes to the board do not affect, empty if no match
     *     is live
     */
    List<Match> getSummary();
}
