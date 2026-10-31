package com.sportradar.scoreboard;

import com.sportradar.scoreboard.exception.MatchNotFoundException;
import com.sportradar.scoreboard.exception.TeamAlreadyPlayingException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps state in maps that only code holding {@link #lock} may touch, and publishes an immutable
 * {@link Snapshot} through a volatile field, so reads never take the lock.
 */
final class InMemoryScoreboard implements Scoreboard {

    private final ReentrantLock lock = new ReentrantLock();
    // Insertion order records start order. Updates use put, which keeps a match's position.
    private final LinkedHashMap<MatchId, Match> live = new LinkedHashMap<>();
    private final Map<String, MatchId> liveTeams = new HashMap<>();
    private volatile Snapshot snapshot = new Snapshot(List.of());

    @Override
    public MatchId startMatch(String homeTeam, String awayTeam) {
        TeamName.requireNotBlank(homeTeam, "Home");
        TeamName.requireNotBlank(awayTeam, "Away");
        TeamName.requireMaxLength(homeTeam, "Home");
        TeamName.requireMaxLength(awayTeam, "Away");
        TeamName home = TeamName.of(homeTeam);
        TeamName away = TeamName.of(awayTeam);
        if (home.key().equals(away.key())) {
            throw new IllegalArgumentException(
                    "Home and away teams must be different: " + home.displayName());
        }
        MatchId id = new MatchId(UUID.randomUUID());

        lock.lock();
        try {
            requireNotPlaying(home, away);
            live.put(id, new Match(id, home.displayName(), away.displayName(), 0, 0));
            liveTeams.put(home.key(), id);
            liveTeams.put(away.key(), id);
            publish();
            return id;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateScore(MatchId id, int homeScore, int awayScore) {
        requireNotNull(id);
        requireNotNegative(homeScore, awayScore);

        lock.lock();
        try {
            Match match = requireLive(id);
            if (match.homeScore() == homeScore && match.awayScore() == awayScore) {
                return;
            }
            live.put(id, new Match(id, match.homeTeam(), match.awayTeam(), homeScore, awayScore));
            publish();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Match> getSummary() {
        return snapshot.live();
    }

    private static void requireNotNull(MatchId id) {
        if (id == null) {
            throw new IllegalArgumentException("Match ID must not be null");
        }
    }

    private static void requireNotNegative(int homeScore, int awayScore) {
        if (homeScore < 0 && awayScore < 0) {
            throw new IllegalArgumentException(
                    "Scores must not be negative: home " + homeScore + ", away " + awayScore);
        }
        if (homeScore < 0) {
            throw new IllegalArgumentException("Home score must not be negative: " + homeScore);
        }
        if (awayScore < 0) {
            throw new IllegalArgumentException("Away score must not be negative: " + awayScore);
        }
    }

    private Match requireLive(MatchId id) {
        Match match = live.get(id);
        if (match == null) {
            throw new MatchNotFoundException("No match found with ID " + id.value());
        }
        return match;
    }

    private void requireNotPlaying(TeamName home, TeamName away) {
        String homeConflict = conflict(home);
        String awayConflict = conflict(away);
        if (homeConflict != null && awayConflict != null) {
            throw new TeamAlreadyPlayingException("Team " + homeConflict + "; team " + awayConflict);
        }
        if (homeConflict != null || awayConflict != null) {
            String only = homeConflict != null ? homeConflict : awayConflict;
            throw new TeamAlreadyPlayingException("Team " + only);
        }
    }

    private String conflict(TeamName team) {
        MatchId id = liveTeams.get(team.key());
        if (id == null) {
            return null;
        }
        Match match = live.get(id);
        boolean isHome = TeamName.of(match.homeTeam()).key().equals(team.key());
        String storedName = isHome ? match.homeTeam() : match.awayTeam();
        return "'" + storedName + "' is already playing in live match "
                + match.homeTeam() + " vs " + match.awayTeam();
    }

    private void publish() {
        // Newest first, then a stable sort, so equal totals stay most recently started first.
        List<Match> sorted = new ArrayList<>(live.sequencedValues().reversed());
        sorted.sort(Comparator.comparingLong(Match::totalScore).reversed());
        snapshot = new Snapshot(List.copyOf(sorted));
    }

    private record Snapshot(List<Match> live) {
    }
}
