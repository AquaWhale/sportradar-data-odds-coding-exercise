/**
 * A live football World Cup scoreboard for multiple simultaneous matches.
 *
 * <p>{@link com.sportradar.scoreboard.Scoreboard} is the only entry point. Create a board with
 * {@link com.sportradar.scoreboard.Scoreboard#create()}, start matches to get a
 * {@link com.sportradar.scoreboard.MatchId}, and read results as immutable
 * {@link com.sportradar.scoreboard.Match} values.
 *
 * <p>Scoreboards are thread-safe. Reads never block, and a completed write is visible to any read
 * that starts afterwards. The board is held in memory only.
 */
package com.sportradar.scoreboard;
