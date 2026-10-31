package com.sportradar.scoreboard;

/**
 * An immutable view of a match at one moment. Changing the board later does not change this value.
 *
 * @param id the match ID
 * @param homeTeam the home team name, stripped of surrounding whitespace
 * @param awayTeam the away team name, stripped of surrounding whitespace
 * @param homeScore the home team score
 * @param awayScore the away team score
 */
public record Match(MatchId id, String homeTeam, String awayTeam, int homeScore, int awayScore) {

    /**
     * Returns the sum of both scores. It is a {@code long} because two {@code int} scores can
     * exceed the {@code int} range.
     *
     * @return the total score
     */
    public long totalScore() {
        return (long) homeScore + awayScore;
    }

    /**
     * Returns the match in the form {@code Mexico 0 - Canada 5}.
     *
     * @return the readable match
     */
    @Override
    public String toString() {
        return homeTeam + " " + homeScore + " - " + awayTeam + " " + awayScore;
    }
}
