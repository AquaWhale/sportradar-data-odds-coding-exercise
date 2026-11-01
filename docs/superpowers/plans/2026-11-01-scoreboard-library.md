# Live Football World Cup Scoreboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a thread-safe Java 21 scoreboard library with start, update, finish and summary operations, one extra finished-matches operation, and the required README and AI.md.

**Architecture:** A public `Scoreboard` interface with static factories, backed by a package-private `InMemoryScoreboard`. Writers change mutable maps under one `ReentrantLock` and publish an immutable `Snapshot` through a `volatile` field, so reads never lock.

**Tech Stack:** Java 21, Maven, JUnit 5, AssertJ. No runtime dependencies.

**Spec:** `docs/superpowers/specs/2026-10-31-scoreboard-library-design.md`. Test numbers below are the spec's section 7 numbers, and each test must assert what that numbered entry says. Exact messages come from spec section 4.

## Global Constraints

- Base package `com.sportradar.scoreboard`, with custom exceptions in `com.sportradar.scoreboard.exception`.
- `maven.compiler.release=21`.
- No runtime dependencies. JUnit 5 and AssertJ are test scope only.
- Only `Scoreboard`, `Match`, `MatchId` and the exception types are public.
- Name cap 1000, default finished cap 1000, minimum cap 1.
- Javadoc on the public API only: `Scoreboard`, `Match`, `MatchId` and the exceptions, stating thrown exceptions and thread-safety. Internal classes get comments only where the reason is not obvious.
- Never use timestamps for ordering.
- All rejections throw and leave state unchanged.
- Input validation runs before the lock. State checks run under it.
- Only code holding the write lock touches the maps. Reads use only the snapshot.
- Commit subjects are imperative sentences, matching the repository history, such as `Add Match and MatchId records`.
- Work on the branch `feature/scoreboard-library`. Do not push.
- Do not stage `.idea/`.

## Review Focus

Each has its test added to the owning task.

1. Eviction must not release a team. With a rematch live, evicting the old finished match leaves the teams reserved (Task 5).
2. A rejected finish of an old ID must not release a team in a rematch (Task 5).
3. A team started as ` Mexico ` must be released on finish, so `MEXICO` can start afterwards (Task 5).
4. A team name containing `%`, such as `100%`, must appear literally in messages, which rules out using a name as a format string (Task 3).
5. A board at the 100 live match sizing target gives a correct summary (Task 4).

---

### Task 1: Project setup and public types

**Files:**
- Create: `pom.xml`, `.gitignore`
- Create: `src/main/java/com/sportradar/scoreboard/MatchId.java`, `Match.java`
- Create: `src/main/java/com/sportradar/scoreboard/exception/ScoreboardException.java`, `MatchNotFoundException.java`, `MatchNotLiveException.java`, `TeamAlreadyPlayingException.java`
- Test: `src/test/java/com/sportradar/scoreboard/MatchIdTest.java`, `MatchTest.java`, `ScoreboardExceptionTest.java`. This last test sits in the parent package on purpose, so it proves callers outside `exception` can catch the base type.

**Interfaces:**
- Produces: `record MatchId(UUID value)`, with a compact constructor rejecting null.
- Produces: `record Match(MatchId id, String homeTeam, String awayTeam, int homeScore, int awayScore)`, with `long totalScore()`.
- Produces: `public abstract class ScoreboardException extends RuntimeException` with `protected ScoreboardException(String message)`. Each subclass is `public final` with `public X(String message)`.

- [ ] **Step 1: Create `pom.xml` and `.gitignore`**

`pom.xml`: `groupId` `com.sportradar`, `artifactId` `scoreboard`, version `1.0.0-SNAPSHOT`, `maven.compiler.release` 21, UTF-8 source encoding, `org.junit.jupiter:junit-jupiter:5.11.4` and `org.assertj:assertj-core:3.27.3` in test scope, `maven-surefire-plugin` 3.5.5.

`.gitignore`: `.idea/` and `target/`.

- [ ] **Step 2: Write failing tests 40, 41 and 42**

```java
assertThatThrownBy(() -> new MatchId(null))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessage("Match ID value must not be null");

assertThat(new Match(id, "Mexico", "Canada", 0, 5)).hasToString("Mexico 0 - Canada 5");
assertThat(new Match(id, "A", "B", Integer.MAX_VALUE, Integer.MAX_VALUE).totalScore())
    .isEqualTo(4294967294L);
assertThat(new MatchNotFoundException("x")).isInstanceOf(ScoreboardException.class);
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `mvn -q test`
Expected: compilation failure on the missing types.

- [ ] **Step 4: Implement the types**

`Match.toString` returns `homeTeam + " " + homeScore + " - " + awayTeam + " " + awayScore`. `Match` has no constructor validation (decision 54).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q test`
Expected: exit 0 with no output.

- [ ] **Step 6: Commit**

```bash
git add pom.xml .gitignore src
git commit -m "Add Maven project and public types"
```

### Task 2: TeamName

**Files:**
- Create: `src/main/java/com/sportradar/scoreboard/TeamName.java`
- Test: `src/test/java/com/sportradar/scoreboard/TeamNameTest.java`

**Interfaces:**
- Produces: `record TeamName(String displayName, String key)`, package-private.
- Produces: `static final int MAX_LENGTH = 1000`.
- Produces: `static void requireNotBlank(String raw, String label)`, `static void requireMaxLength(String raw, String label)`, `static TeamName of(String raw)`. `label` is `"Home"` or `"Away"`.

- [ ] **Step 1: Write failing tests 65 and 66**

```java
assertThatThrownBy(() -> TeamName.requireNotBlank(null, "Home"))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessage("Home team name must not be null");
assertThatThrownBy(() -> TeamName.requireNotBlank("  ", "Away"))
    .hasMessage("Away team name must not be blank");
assertThatThrownBy(() -> TeamName.requireMaxLength("a".repeat(5230), "Home"))
    .hasMessage("Home team name is 5230 characters, maximum is 1000");
assertThatCode(() -> TeamName.requireMaxLength("  " + "a".repeat(1000) + "  ", "Home"))
    .doesNotThrowAnyException();
assertThat(TeamName.of(" Mexico ")).isEqualTo(new TeamName("Mexico", "mexico"));
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q test -Dtest=TeamNameTest`
Expected: compilation failure on the missing type.

- [ ] **Step 3: Implement `TeamName`**

The key is `raw.strip().toLowerCase(Locale.ROOT)`. Length is `raw.strip().length()`. Blank uses `isBlank()`.

- [ ] **Step 4: Run the full suite to verify it passes**

Run: `mvn -q test`
Expected: exit 0 with no output.

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "Add TeamName validation and comparison key"
```

### Task 3: Start a match and the summary

**Files:**
- Create: `src/main/java/com/sportradar/scoreboard/Scoreboard.java`, `InMemoryScoreboard.java`
- Test: `src/test/java/com/sportradar/scoreboard/StartMatchTest.java`

**Interfaces:**
- Consumes: `TeamName`, `Match`, `MatchId`, `TeamAlreadyPlayingException`.
- Produces: `public interface Scoreboard` with `static Scoreboard create()`, `MatchId startMatch(String homeTeam, String awayTeam)`, `List<Match> getSummary()`.
- Produces: `final class InMemoryScoreboard implements Scoreboard`, package-private.

Fields: `ReentrantLock lock`, `LinkedHashMap<MatchId, Match> live`, `Map<String, MatchId> liveTeams` (`HashMap`), `volatile Snapshot snapshot`, and private `record Snapshot(List<Match> live)`. Task 8 adds the finished list to `Snapshot`, so core has no dead code.

- [ ] **Step 1: Write failing tests**

In `StartMatchTest`: 1, 2, 3, 4, 5, 6, 7, 10, 11, 26, 43, 44, 45, 46, 52, 53, and the start rows of 48. Add one for Review Focus 4.

Key assertions:

```java
// 6, both live in different matches
board.startMatch("Mexico", "Canada");
board.startMatch("Spain", "Brazil");
assertThatThrownBy(() -> board.startMatch("Mexico", "Spain"))
    .isInstanceOf(TeamAlreadyPlayingException.class)
    .hasMessage("Team 'Mexico' is already playing in live match Mexico vs Canada; "
        + "team 'Spain' is already playing in live match Spain vs Brazil");

// 11
assertThatThrownBy(() -> board.startMatch("Spain", "Mexico"));
assertThatCode(() -> board.startMatch("Spain", "Brazil")).doesNotThrowAnyException();

// 43
assertThatThrownBy(() -> board.startMatch("a".repeat(1001), null))
    .hasMessage("Away team name must not be null");

// 53: restore the default locale in finally
Locale.setDefault(Locale.forLanguageTag("tr"));

// Review Focus 4
board.startMatch("100%", "Canada");
assertThatThrownBy(() -> board.startMatch("100%", "Spain"))
    .hasMessage("Team '100%' is already playing in live match 100% vs Canada");
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q test -Dtest=StartMatchTest`
Expected: compilation failure on `Scoreboard`.

- [ ] **Step 3: Implement `Scoreboard` and `InMemoryScoreboard`**

`startMatch` order, per spec sections 3.1 and 5:

1. `requireNotBlank` home then away, then `requireMaxLength` home then away.
2. `TeamName.of` for both, compare keys, and throw the same-team error using the home display name.
3. Generate the `MatchId`.
4. Under the lock in `try/finally`: collect conflicts home then away, throw if any, insert, reserve both keys, publish.

`Team '...'` uses the stored display name from the live match, found by comparing keys. Build messages by concatenation, not `String.format`.

The rebuild, with the comment kept:

```java
// Newest first, then a stable sort, so equal totals stay most recently started first.
List<Match> sorted = new ArrayList<>(live.sequencedValues().reversed());
sorted.sort(Comparator.comparingLong(Match::totalScore).reversed());
snapshot = new Snapshot(List.copyOf(sorted));
```

- [ ] **Step 4: Run the full suite to verify it passes**

Run: `mvn -q test`
Expected: exit 0 with no output.

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "Add startMatch and getSummary"
```

### Task 4: Update the score

**Files:**
- Modify: `src/main/java/com/sportradar/scoreboard/Scoreboard.java`, `InMemoryScoreboard.java`
- Test: `src/test/java/com/sportradar/scoreboard/UpdateScoreTest.java`, `SummaryTest.java`

**Interfaces:**
- Produces: `void updateScore(MatchId id, int homeScore, int awayScore)`.
- Produces: private `Match requireLive(MatchId id)` in `InMemoryScoreboard`, reused by Task 5, which adds the finished check.

- [ ] **Step 1: Write failing tests**

In `UpdateScoreTest`: 12, 13, 14, 15, 16, 17, 20 (unknown ID half), the first half of 21, 47, 51, and the update rows of 48.

In `SummaryTest`: 27, 28, 29, 31 (immutability half), 32. Add one for Review Focus 5.

Key assertions:

```java
// 16
List<Match> before = board.getSummary();
board.updateScore(id, 0, 0);
assertThat(board.getSummary()).isSameAs(before);

// 27: use the brief's five matches, in the brief's order
assertThat(board.getSummary()).extracting(Match::toString).containsExactly(
    "Uruguay 6 - Italy 6", "Spain 10 - Brazil 2", "Mexico 0 - Canada 5",
    "Argentina 3 - Australia 1", "Germany 2 - France 2");

// 29: A starts first, and B must be updated first
board.updateScore(b, 1, 0);
board.updateScore(a, 1, 0);
assertThat(board.getSummary()).extracting(Match::id).containsExactly(b, a);

// 51
assertThatThrownBy(() -> other.updateScore(idFromBoard, 1, 0))
    .isInstanceOf(MatchNotFoundException.class);

// Review Focus 5: 100 matches, where match i scores i goals at home
assertThat(board.getSummary()).hasSize(100);
assertThat(board.getSummary()).extracting(Match::totalScore).isSortedAccordingTo(Comparator.reverseOrder());
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q test -Dtest='UpdateScoreTest,SummaryTest'`
Expected: compilation failure on `updateScore`.

- [ ] **Step 3: Implement `updateScore`**

Check the null ID, then negatives, outside the lock. Under the lock, find the match, return if both scores are unchanged, otherwise `live.put(id, updated)` and publish. The message for both scores negative is the combined form.

- [ ] **Step 4: Run the full suite to verify it passes**

Run: `mvn -q test`
Expected: exit 0 with no output.

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "Add updateScore"
```

### Task 5: Finish a match, retention and eviction

**Files:**
- Modify: `src/main/java/com/sportradar/scoreboard/Scoreboard.java`, `InMemoryScoreboard.java`
- Test: `src/test/java/com/sportradar/scoreboard/FinishMatchTest.java`, `ConfigurationTest.java`, and additions to `StartMatchTest.java`, `UpdateScoreTest.java`, `SummaryTest.java`

**Interfaces:**
- Consumes: `requireLive` from Task 4.
- Produces: `void finishMatch(MatchId id)`, `static Scoreboard create(int maxFinishedMatches)`, the field `LinkedHashMap<MatchId, Match> finished`, and `static final int DEFAULT_MAX_FINISHED_MATCHES = 1000` in `InMemoryScoreboard`.

- [ ] **Step 1: Write failing tests**

In `FinishMatchTest`: first halves of 22 and 23, 24, 25, first half of 49, and Review Focus 1, 2 and 3.

In `ConfigurationTest`: 37, and 39 asserting the invalid cap row of 48.

Add 8 and 9 to `StartMatchTest`. Add 18, 19 and the finished half of 20 to `UpdateScoreTest`. Add 30 and the rest of 31 to `SummaryTest`. Add the `MatchNotLiveException` row of 48.

Key assertions:

```java
// 19 and 25: cap of 1
Scoreboard board = Scoreboard.create(1);
board.finishMatch(first);
board.finishMatch(second);
assertThatThrownBy(() -> board.updateScore(first, 1, 0))
    .isInstanceOf(MatchNotFoundException.class);

// 37: finish 1001 matches with distinct teams
assertThatThrownBy(() -> board.finishMatch(ids.get(0))).isInstanceOf(MatchNotFoundException.class);
assertThatThrownBy(() -> board.finishMatch(ids.get(1))).isInstanceOf(MatchNotLiveException.class);

// Review Focus 1: cap of 1
MatchId old = board.startMatch("Mexico", "Canada");
board.finishMatch(old);
board.startMatch("Mexico", "Canada");
board.finishMatch(board.startMatch("Spain", "Brazil"));  // evicts old
assertThatThrownBy(() -> board.startMatch("Mexico", "Italy"))
    .isInstanceOf(TeamAlreadyPlayingException.class);

// Review Focus 2
MatchId old = board.startMatch("Mexico", "Canada");
board.finishMatch(old);
board.startMatch("Mexico", "Canada");
assertThatThrownBy(() -> board.finishMatch(old)).isInstanceOf(MatchNotLiveException.class);
assertThatThrownBy(() -> board.startMatch("Mexico", "Italy"))
    .isInstanceOf(TeamAlreadyPlayingException.class);

// Review Focus 3
board.finishMatch(board.startMatch(" Mexico ", "Canada"));
assertThatCode(() -> board.startMatch("MEXICO", "Canada")).doesNotThrowAnyException();
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q test`
Expected: compilation failure on `finishMatch`.

- [ ] **Step 3: Implement `finishMatch` and retention**

`requireLive` now checks `live`, then `finished` for `MatchNotLiveException`, then throws `MatchNotFoundException`. `finishMatch`, under the lock: remove from `live`, remove both keys using `TeamName.of(...).key()` on the stored names, put in `finished`, call `finished.pollFirstEntry()` if the size exceeds the cap, publish.

Add `create(int)`, which throws `IllegalArgumentException` with `Maximum finished matches must be at least 1: <value>`. `create()` now passes `DEFAULT_MAX_FINISHED_MATCHES`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q test`
Expected: exit 0 with no output.

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "Add finishMatch with bounded retention"
```

### Task 6: Concurrency tests

**Files:**
- Test: `src/test/java/com/sportradar/scoreboard/ConcurrencyTest.java`

**Interfaces:**
- Consumes: the public `Scoreboard` API only.

- [ ] **Step 1: Write tests 54, 55, 56, 57, 59, 60, 62 and 64, and the first half of 58**

Use `ExecutorService` with a `CountDownLatch` start gate. Give every test `@Timeout(30)`. Use a fresh board in each test.

Do not use try-with-resources on the executor. `close()` waits for tasks, and `lock.lock()` cannot be interrupted, so a held lock would hang the build instead of failing the test. Use `try { ... } finally { executor.shutdownNow(); }`.
 Test 64 uses `Executors.newVirtualThreadPerTaskExecutor()`. Test 60 has three separate test methods, and the race method accepts either outcome. Test 59 includes no-op sends.

- [ ] **Step 2: Run the tests**

Run: `mvn -q test -Dtest=ConcurrencyTest`
Expected: exit 0 with no output. These pass against the existing design. If one fails, treat it as a defect in `InMemoryScoreboard` and use superpowers:systematic-debugging.

- [ ] **Step 3: Run them five times to look for flakiness**

Run: `for i in 1 2 3 4 5; do mvn -q test -Dtest=ConcurrencyTest || break; done`
Expected: five clean runs.

- [ ] **Step 4: Commit**

```bash
git add src
git commit -m "Add concurrency tests"
```

### Task 7: README for the core library

**Files:**
- Create: `README.md`

**Interfaces:**
- Produces: `README.md` without the additional feature, which Task 8 adds under its own header.

- [ ] **Step 1: Write `README.md`**

Use these headers, in this order, drawing the content from the spec:

- `## Build and test`: `mvn verify`, and the requirement of JDK 21 or newer.
- `## Usage`: a short example with `Scoreboard.create()`.
- `## Core operations`
- `## Assumptions`: includes in-order update delivery and spec section 9's exclusions.
- `## Decisions`: spec section 8's register, without the held-back items.
- `## Trade-offs`: every alternative in spec sections 5, 6 and 8, without the held-back items, plus spec section 9's exclusions.
- `## Design and reasoning`: spec sections 5 and 6, describing `Snapshot` with its live list only.
- `## Research`: spec section 11, with sources.
- `## Tests`: how they are organised and how concurrency is tested, stating what they cannot prove.
- `## Future improvements`: spec section 10.

Describe only what exists in this commit. These items are held back for Task 8: decisions 27, 41 and 55, the section 8 alternatives "Fifth operation", "Finished order", "Feature commit" and "Two-list reads", the section 6 known two-list limit, and the two-list `Snapshot`.

- [ ] **Step 2: Check the usage example against the code**

Copy the example into a scratch test, then run `mvn -q test`.
Expected: exit 0 with no output. Delete the scratch test afterwards.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "Add README"
```

### Task 8: getFinishedMatches, the distinct feature commit

**Files:**
- Modify: `src/main/java/com/sportradar/scoreboard/Scoreboard.java`, `InMemoryScoreboard.java`, `README.md`
- Test: `src/test/java/com/sportradar/scoreboard/FinishedMatchesTest.java`, and additions to `ConfigurationTest.java`, `ConcurrencyTest.java`

**Interfaces:**
- Produces: `List<Match> getFinishedMatches()`.
- Produces: `record Snapshot(List<Match> live, List<Match> finished)`.

This whole task is one commit, so the feature is reviewable alone.

- [ ] **Step 1: Write failing tests**

In `FinishedMatchesTest`: 33, 34, 35, 36, 50, and the second halves of 21, 22, 23 and 49.

In `ConfigurationTest`: 38.

In `ConcurrencyTest`: 61, 63, and the second half of 58.

```java
// 33
assertThat(board.getFinishedMatches()).extracting(Match::id).containsExactly(second, first);

// 36
assertThat(board.getFinishedMatches()).isEmpty();
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q test`
Expected: compilation failure on `getFinishedMatches`.

- [ ] **Step 3: Implement the feature**

Add the `finished` list to `Snapshot`. `finishMatch` rebuilds it as `List.copyOf(finished.sequencedValues().reversed())` and publishes both lists in one snapshot. `start` and `update` carry the finished list over unchanged. `getFinishedMatches` reads the snapshot without the lock.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q verify`
Expected: exit 0 with no output.

- [ ] **Step 5: Add the README section**

Add `## Additional feature: recently finished matches` after `## Research` and before `## Tests`, as its own section. Cover what `getFinishedMatches` does, why it was chosen, bounded retention, the eviction consequence for callers, and that `create(int)` is configuration rather than a second extra operation.

Add the finished list to `## Usage`, the items Task 7 held back to `## Decisions`, `## Trade-offs` and `## Design and reasoning`, and the new tests to `## Tests`.

- [ ] **Step 6: Check the usage example against the code**

Copy the updated example into a scratch test, then run `mvn -q test`.
Expected: exit 0 with no output. Delete the scratch test afterwards.

- [ ] **Step 7: Run the concurrency tests five times to look for flakiness**

Run: `for i in 1 2 3 4 5; do mvn -q test -Dtest=ConcurrencyTest || break; done`
Expected: five clean runs.

- [ ] **Step 8: Commit**

```bash
git add src README.md
git commit -m "Add getFinishedMatches operation"
```

### Task 9: Full verification and AI.md

**Files:**
- Create: `AI.md`

- [ ] **Step 1: Run the full suite on JDK 21**

Run: `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.2-graalce" mvn -q clean verify`
Expected: exit 0 with no output.

- [ ] **Step 2: Run the full suite on JDK 25**

Run: `mvn -q clean verify`
Expected: exit 0 with no output.

- [ ] **Step 3: Check every spec test has an implementation**

Confirm tests 1-66 each exist, by mapping them to test methods. Run `git log --oneline` and confirm the feature commit is distinct.
Expected: no gaps.

- [ ] **Step 4: Write `AI.md`**

Use these headers:

- `## How AI tools were used`: a summary, plus tools and models. Includes the independent spec and plan reviews.
- `## Artifacts`: links to the spec, this plan and all review reports in `docs/superpowers/`, which are committed before implementation. Mentions the assignment PDF without including it.
- `## Prompt history`: covers every session, including implementation. User prompts are verbatim where available, and model output is lightly summarised. It may link a committed transcript instead.

The human partner confirms the prompt history is complete before the commit.

- [ ] **Step 5: Commit**

```bash
git add AI.md
git commit -m "Add AI.md"
```
