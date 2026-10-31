package com.sportradar.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportradar.scoreboard.exception.MatchNotFoundException;
import com.sportradar.scoreboard.exception.MatchNotLiveException;
import com.sportradar.scoreboard.exception.TeamAlreadyPlayingException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// Executors are shut down with shutdownNow, never try-with-resources: close() waits for tasks, and
// lock() cannot be interrupted, so a held lock would hang the build instead of failing the test.
@Timeout(30)
class ConcurrencyTest {

    private static final int THREADS = 16;

    private final ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    private final Scoreboard board = Scoreboard.create();

    @AfterEach
    void stopThreads() {
        executor.shutdownNow();
    }

    @Test
    void onlyOneStartOfTheSameFixtureSucceeds() throws Exception {
        List<Callable<MatchId>> starts = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            starts.add(() -> board.startMatch("Mexico", "Canada"));
        }

        List<Outcome<MatchId>> outcomes = runTogether(executor, starts);

        assertThat(successes(outcomes)).hasSize(1);
        assertThat(failures(outcomes))
                .hasSize(THREADS - 1)
                .allMatch(TeamAlreadyPlayingException.class::isInstance);
        assertThat(board.getSummary()).hasSize(1);
    }

    @Test
    void overlappingFixturesNeverPutATeamInTwoLiveMatches() throws Exception {
        List<Callable<MatchId>> starts = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String home = "Team " + i;
            String away = "Team " + (i + 1) % THREADS;
            starts.add(() -> board.startMatch(home, away));
        }

        List<Outcome<MatchId>> outcomes = runTogether(executor, starts);

        assertThat(failures(outcomes)).allMatch(TeamAlreadyPlayingException.class::isInstance);
        assertThat(successes(outcomes)).isNotEmpty();
        assertThat(board.getSummary()).hasSameSizeAs(successes(outcomes));
        assertNoTeamAppearsTwice(board.getSummary());
    }

    @Test
    void differentFixturesAllStart() throws Exception {
        List<Callable<MatchId>> starts = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String home = "Home " + i;
            String away = "Away " + i;
            starts.add(() -> board.startMatch(home, away));
        }

        List<Outcome<MatchId>> outcomes = runTogether(executor, starts);

        assertThat(failures(outcomes)).isEmpty();
        assertThat(successes(outcomes)).doesNotHaveDuplicates();
        assertThat(board.getSummary())
                .extracting(Match::id)
                .containsExactlyInAnyOrderElementsOf(successes(outcomes));
    }

    @Test
    void onlyOneFinishOfTheSameMatchSucceeds() throws Exception {
        MatchId id = board.startMatch("Mexico", "Canada");
        List<Callable<Boolean>> finishes = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            finishes.add(() -> {
                board.finishMatch(id);
                return true;
            });
        }

        List<Outcome<Boolean>> outcomes = runTogether(executor, finishes);

        assertThat(successes(outcomes)).hasSize(1);
        assertThat(failures(outcomes))
                .hasSize(THREADS - 1)
                .allMatch(MatchNotLiveException.class::isInstance);
        assertThat(board.getSummary()).isEmpty();
    }

    @Test
    void updateRacingFinishEitherAppliesOrGetsMatchNotLive() throws Exception {
        MatchId id = board.startMatch("Mexico", "Canada");
        CountDownLatch updaterStarted = new CountDownLatch(1);

        Future<Integer> updater = executor.submit(() -> {
            int lastSuccessful = 0;
            for (int score = 1; score < Integer.MAX_VALUE; score++) {
                try {
                    board.updateScore(id, score, 0);
                    lastSuccessful = score;
                    updaterStarted.countDown();
                } catch (MatchNotLiveException finished) {
                    return lastSuccessful;
                }
            }
            return lastSuccessful;
        });
        updaterStarted.await();
        board.finishMatch(id);

        // A different exception would surface here as an ExecutionException.
        int lastSuccessful = updater.get();
        assertThat(lastSuccessful).isPositive();
        assertThat(board.getFinishedMatches())
                .extracting(Match::homeScore)
                .containsExactly(lastSuccessful);
    }

    @Test
    void readingSummaryThenFinishedAlwaysFindsEachMatch() throws Exception {
        List<MatchId> ids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ids.add(board.startMatch("Home " + i, "Away " + i));
        }
        AtomicBoolean finishing = new AtomicBoolean(true);

        Future<?> reader = executor.submit(() -> {
            while (finishing.get()) {
                Set<MatchId> seen = new HashSet<>();
                board.getSummary().forEach(match -> seen.add(match.id()));
                board.getFinishedMatches().forEach(match -> seen.add(match.id()));
                assertThat(seen).containsAll(ids);
            }
        });

        List<Callable<Boolean>> finishes = new ArrayList<>();
        for (MatchId id : ids) {
            finishes.add(() -> {
                board.finishMatch(id);
                return true;
            });
        }
        List<Outcome<Boolean>> outcomes = runTogether(executor, finishes);
        finishing.set(false);

        assertThat(failures(outcomes)).isEmpty();
        reader.get(20, TimeUnit.SECONDS);
    }

    @Test
    void concurrentFinishesNeverExceedTheCap() throws Exception {
        int cap = 4;
        Scoreboard smallBoard = Scoreboard.create(cap);
        List<MatchId> ids = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            ids.add(smallBoard.startMatch("Home " + i, "Away " + i));
        }
        AtomicBoolean finishing = new AtomicBoolean(true);

        Future<?> reader = executor.submit(() -> {
            while (finishing.get()) {
                assertThat(smallBoard.getFinishedMatches().size()).isLessThanOrEqualTo(cap);
            }
        });

        List<Callable<Boolean>> finishes = new ArrayList<>();
        for (MatchId id : ids) {
            finishes.add(() -> {
                smallBoard.finishMatch(id);
                return true;
            });
        }
        List<Outcome<Boolean>> outcomes = runTogether(executor, finishes);
        finishing.set(false);

        assertThat(failures(outcomes)).isEmpty();
        reader.get(20, TimeUnit.SECONDS);
        List<MatchId> retained = smallBoard.getFinishedMatches().stream().map(Match::id).toList();
        assertThat(retained).hasSize(cap);
        for (MatchId id : ids) {
            if (!retained.contains(id)) {
                assertThatThrownBy(() -> smallBoard.updateScore(id, 1, 0))
                        .isInstanceOf(MatchNotFoundException.class);
            }
        }
    }

    @Test
    void summaryStaysConsistentDuringMixedWrites() throws Exception {
        MatchId oldest = board.startMatch("Anchor 1", "Anchor 2");
        MatchId middle = board.startMatch("Anchor 3", "Anchor 4");
        MatchId newest = board.startMatch("Anchor 5", "Anchor 6");
        AtomicBoolean writing = new AtomicBoolean(true);

        List<Future<?>> readers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            readers.add(executor.submit(() -> {
                while (writing.get()) {
                    assertSummaryConsistent(board.getSummary(), List.of(newest, middle, oldest));
                }
            }));
        }

        List<Callable<Boolean>> writers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int writer = i;
            writers.add(() -> {
                MatchId own = board.startMatch("Writer " + writer, "Rival " + writer);
                for (int score = 1; score <= 300; score++) {
                    board.updateScore(own, score, 0);
                    board.updateScore(own, score, 0);
                    board.finishMatch(board.startMatch("Guest " + writer, "Visitor " + writer));
                }
                return true;
            });
        }

        List<Outcome<Boolean>> outcomes = runTogether(executor, writers);
        writing.set(false);

        assertThat(failures(outcomes)).isEmpty();
        for (Future<?> reader : readers) {
            reader.get(20, TimeUnit.SECONDS);
        }
    }

    @Test
    void startAfterFinishCompletesSucceeds() throws Exception {
        MatchId id = board.startMatch("Mexico", "Canada");
        CountDownLatch finished = new CountDownLatch(1);

        executor.submit(() -> {
            board.finishMatch(id);
            finished.countDown();
        });
        Future<MatchId> start = executor.submit(() -> {
            finished.await();
            return board.startMatch("Mexico", "Spain");
        });

        MatchId started = start.get(20, TimeUnit.SECONDS);
        assertThat(board.getSummary()).extracting(Match::id).containsExactly(started);
    }

    @Test
    void startBeforeFinishBeginsIsRejected() throws Exception {
        MatchId id = board.startMatch("Mexico", "Canada");
        CountDownLatch rejected = new CountDownLatch(1);

        Future<Throwable> start = executor.submit(() -> {
            try {
                board.startMatch("Mexico", "Spain");
                return null;
            } catch (RuntimeException e) {
                return e;
            } finally {
                rejected.countDown();
            }
        });
        Future<Boolean> finish = executor.submit(() -> {
            rejected.await();
            board.finishMatch(id);
            return true;
        });

        assertThat(start.get(20, TimeUnit.SECONDS)).isInstanceOf(TeamAlreadyPlayingException.class);
        assertThat(finish.get(20, TimeUnit.SECONDS)).isTrue();
        assertThat(board.getSummary()).isEmpty();
    }

    @Test
    void startRacingFinishGivesEitherValidOutcome() throws Exception {
        MatchId id = board.startMatch("Mexico", "Canada");
        List<Callable<MatchId>> actions = List.of(
                () -> board.startMatch("Mexico", "Spain"),
                () -> {
                    board.finishMatch(id);
                    return id;
                });

        List<Outcome<MatchId>> outcomes = runTogether(executor, actions);

        Outcome<MatchId> start = outcomes.get(0);
        assertThat(outcomes.get(1).failure()).isNull();
        if (start.failure() == null) {
            assertThat(board.getSummary()).extracting(Match::id).containsExactly(start.value());
        } else {
            assertThat(start.failure()).isInstanceOf(TeamAlreadyPlayingException.class);
            assertThat(board.getSummary()).isEmpty();
        }
    }

    // Catches a publish that happens after updateScore returns. It cannot prove volatile is
    // needed, because the hand-off between threads creates its own happens-before edge.
    @Test
    void completedUpdateIsVisibleToSummaryOnAnotherThread() throws Exception {
        MatchId id = board.startMatch("Mexico", "Canada");

        for (int score = 1; score <= 1000; score++) {
            int expected = score;
            executor.submit(() -> board.updateScore(id, expected, 0)).get(20, TimeUnit.SECONDS);

            List<Match> summary = executor.submit(board::getSummary).get(20, TimeUnit.SECONDS);

            assertThat(summary.get(0).homeScore()).isGreaterThanOrEqualTo(expected);
        }
    }

    @Test
    void writesCompleteOnVirtualThreads() throws Exception {
        ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Callable<Boolean>> writers = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                String home = "Home " + i;
                String away = "Away " + i;
                writers.add(() -> {
                    MatchId id = board.startMatch(home, away);
                    board.updateScore(id, 1, 0);
                    board.finishMatch(id);
                    return true;
                });
            }

            List<Outcome<Boolean>> outcomes = runTogether(virtualThreads, writers);

            assertThat(failures(outcomes)).isEmpty();
            assertThat(board.getSummary()).isEmpty();
        } finally {
            virtualThreads.shutdownNow();
        }
    }

    private static void assertSummaryConsistent(List<Match> summary, List<MatchId> newestFirst) {
        assertThat(summary)
                .extracting(Match::totalScore)
                .isSortedAccordingTo(Comparator.reverseOrder());
        assertNoTeamAppearsTwice(summary);
        assertThat(summary)
                .extracting(Match::id)
                .filteredOn(newestFirst::contains)
                .containsExactlyElementsOf(newestFirst);
    }

    private static void assertNoTeamAppearsTwice(List<Match> matches) {
        Set<String> teams = new HashSet<>();
        for (Match match : matches) {
            assertThat(teams.add(match.homeTeam())).as(match.homeTeam()).isTrue();
            assertThat(teams.add(match.awayTeam())).as(match.awayTeam()).isTrue();
        }
    }

    private static <T> List<Outcome<T>> runTogether(ExecutorService executor, List<Callable<T>> tasks)
            throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(executor.submit(() -> {
                gate.await();
                return task.call();
            }));
        }
        gate.countDown();

        List<Outcome<T>> outcomes = new ArrayList<>();
        for (Future<T> future : futures) {
            try {
                outcomes.add(new Outcome<>(future.get(20, TimeUnit.SECONDS), null));
            } catch (ExecutionException e) {
                outcomes.add(new Outcome<>(null, e.getCause()));
            }
        }
        return outcomes;
    }

    private static <T> List<T> successes(List<Outcome<T>> outcomes) {
        return outcomes.stream().filter(o -> o.failure() == null).map(Outcome::value).toList();
    }

    private static <T> List<Throwable> failures(List<Outcome<T>> outcomes) {
        return outcomes.stream().map(Outcome::failure).filter(f -> f != null).toList();
    }

    private record Outcome<T>(T value, Throwable failure) {
    }
}
