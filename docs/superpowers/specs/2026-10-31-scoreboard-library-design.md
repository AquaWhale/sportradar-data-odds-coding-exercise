# Live Football World Cup Scoreboard: Design Spec

Date: 2026-10-31
Status: approved

## 1. Purpose and context

A simple Java library, delivered as a Maven project, that manages a live football World Cup scoreboard across multiple simultaneous matches. It is a coding exercise for Sportradar's Data & Odds Platform. It is submitted as a git repository, reviewed like a teammate's work, and walked through in an interview.

### Requirements from the brief

- Start a new match.
- Update the score.
- Finish a match.
- Get a summary of matches in progress, ordered by total score descending, then most recently started first.
- Add exactly one extra operation, with a distinct git commit and a README section explaining what it is and why it was chosen.
- Provide `README.md` with assumptions, reasoning and trade-offs.
- Provide `AI.md` with a short summary of AI use, the prompt history and other context, and any artifact that guided the implementation.

The brief leaves open questions on purpose. Deciding them and documenting the reasoning is part of the task.

### Requirements added by us

- The library is thread-safe. The brief does not ask for this.
- Errors are intuitive and say exactly what went wrong.
- `README.md` also contains decisions, research with sources, explicit exclusions, and future improvements.

### Gaps in the brief, resolved here

| Gap | Resolution |
|---|---|
| Initial score not stated | Always 0-0 |
| Absolute or incremental updates not stated | Absolute score of both teams |
| Match identity not stated | `MatchId` returned by `startMatch` |
| Duplicate and overlapping matches not stated | A team can be in one live match |
| Invalid input and failure behaviour not stated | Throw, leave state unchanged |

## 2. Public API

Base package: `com.sportradar.scoreboard`. Target: Java 21 LTS. No runtime dependencies.

```java
public interface Scoreboard {
    static Scoreboard create();                        // default cap of 1000
    static Scoreboard create(int maxFinishedMatches);  // cap must be 1 or more

    MatchId startMatch(String homeTeam, String awayTeam);
    void updateScore(MatchId id, int homeScore, int awayScore);
    void finishMatch(MatchId id);
    List<Match> getSummary();
    List<Match> getFinishedMatches();
}

public record MatchId(UUID value) {

    public MatchId {
        if (value == null) {
            throw new IllegalArgumentException("Match ID value must not be null");
        }
    }
}

public record Match(
        MatchId id,
        String homeTeam,
        String awayTeam,
        int homeScore,
        int awayScore) {

    public long totalScore() {
        return (long) homeScore + awayScore;
    }

    // toString prints: Mexico 0 - Canada 5
}
```

- `Scoreboard` is the only entry point. The implementation is package-private, so its concurrency approach can change without affecting users.
- Static factories are used instead of a builder because there is one setting. A builder can be added if more settings appear.
- `MatchId` is a typed, opaque wrapper. An ID cannot be confused with a score, and an ID from another board gives match not found. Its constructor rejects a null value, so an invalid ID cannot exist.
- `Match` is one immutable record used by both lists. Callers format it as they like.
- `updateScore` and `finishMatch` return `void`. A no-op is purely internal, and a finished match's result is read from `getFinishedMatches`.

## 3. Operation behaviour

### 3.1 startMatch

1. Validates the input, then checks live state.
2. Creates a match at 0-0 with a new `MatchId`, and adds it after all current live matches, which records its start order.
3. Stores the stripped names as first given, for display. Each match keeps its own display names.
4. Returns the `MatchId`.

**Team name rules**

- Null, empty and whitespace-only names are invalid.
- Whitespace is removed with `strip()`, not `trim()`, so it is consistent with `isBlank()`.
- The maximum is 1000 characters, measured with `String.length()` after stripping. It is a fixed constant. Longer names are rejected with an error that gives the length and does not repeat the name.
- Teams are distinct home and away sides.

**Same-team comparison**

A single comparison key is used for both start checks: `name.strip().toLowerCase(Locale.ROOT)`. `Locale.ROOT` means behaviour does not depend on the server's locale.

- Home and away with the same key are rejected as a team playing itself: `Spain` vs ` SPAIN `.
- A team whose key is in a live match is rejected. With Mexico vs Canada live, these are all rejected: Mexico vs Canada, Canada vs Mexico, Mexico vs Spain, Spain vs Canada, and ` mexico ` vs Spain.
- Finishing a match frees both teams. A rematch gets a new ID, starts at 0-0, and counts as newly started.

Update and finish use `MatchId`, so this comparison is used only in `startMatch`.

**Error order**

Each step runs in full, home before away, before the next step starts. A too-long home name with a null away name gives the away null error.

1. Null or blank.
2. Length.
3. Same team on both sides.
4. Already live.

If both teams are live, one `TeamAlreadyPlayingException` names each team and the match it is in.

### 3.2 updateScore

- Takes the absolute current score of both teams.
- Scores are non-negative `int` values with no library upper bound.
- `totalScore()` is a `long`, so two `Integer.MAX_VALUE` scores add correctly and sort correctly.
- Lowering a score is allowed, for VAR corrections and mistakes. The match moves in the summary accordingly.
- Sending the current score is a detected no-op. It does not throw, changes nothing, and publishes nothing.
- Callers are assumed to deliver a match's updates in order, which fits one consumer per match. The no-op protects only against an immediate duplicate of the current score. After 1-0 then 2-0, a redelivered 1-0 lowers the board, because the library cannot tell it from a VAR correction. If two threads update one match, the last to take the lock wins.

**Error order**

1. Null ID: `IllegalArgumentException`.
2. Negative score: `IllegalArgumentException`. If both are negative, the message names both.
3. Unknown or evicted ID: `MatchNotFoundException`.
4. Retained finished ID: `MatchNotLiveException`, even when re-sending the final score.

The same bad input always gives the same error, whatever the match's state.

### 3.3 finishMatch

- Moves a live match to the finished matches and frees both teams.
- Any score is valid, including 0-0 with no updates. A 0-0 draw is a real result.
- Evicts the oldest finished match if the cap is exceeded.

**Error order**

1. Null ID: `IllegalArgumentException`, with the same message as `updateScore`.
2. Unknown or evicted ID: `MatchNotFoundException`.
3. Retained finished ID, including a second finish: `MatchNotLiveException`.

### 3.4 getSummary

- Returns live matches ordered by total score descending, then most recently started first.
- Start order is the insertion order of the live matches, never a timestamp, so two matches started in the same millisecond have a defined order.
- An empty board returns an empty list.
- The result is immutable and fixed. Later changes do not affect it, and it cannot alter the board.

### 3.5 getFinishedMatches (the extra operation)

- Returns finished matches with their final scores, most recently finished first.
- Order comes from finish order, with no timestamps, and does not depend on score or start order.
- No finished matches returns an empty list.
- The result is immutable and fixed.
- It was chosen because retained data already exists to support the distinct "not live" error, and it gives a results view beside the live summary.

**Retention**

- Finished matches are capped. Eviction is FIFO, so only the oldest is removed and the newest finished match is always kept.
- The cap defaults to 1000, about ten 104-match tournaments. With one instance per tournament, eviction never fires, so the cap protects against a long-lived instance.
- `Scoreboard.create(int)` requires 1 or more. 0 or negative throws `IllegalArgumentException`. The minimum cap is one so history retains at least the most recently finished match. History is bounded; a particular result may be evicted before a caller retrieves it.
- An evicted match's ID gives match not found. The library no longer holds a record of it, so old IDs cannot be told apart from unknown ones.
- Live matches are not capped. The strict memory bound covers finished matches only. Live matches are bounded in practice by the number of teams.

## 4. Error handling

All rejections throw and leave the scoreboard unchanged. Messages say exactly what went wrong.

| Failure | Exception |
|---|---|
| Null or blank name, name too long, same team, null ID, null value in `MatchId`, negative score, invalid cap | `IllegalArgumentException` |
| Team already in a live match | `TeamAlreadyPlayingException` |
| Unknown or evicted ID | `MatchNotFoundException` |
| Retained finished ID | `MatchNotLiveException` |

```
ScoreboardException (abstract, extends RuntimeException)
  MatchNotFoundException
  MatchNotLiveException
  TeamAlreadyPlayingException
```

- `ScoreboardException` and its subclasses live in the `com.sportradar.scoreboard.exception` subpackage.
- `IllegalArgumentException` is used for all invalid input, including null, because Java developers expect it. Null does not use `NullPointerException`.
- State failures are unchecked and specific, so callers can catch exactly what they need or catch the base type.

In messages, `<id>` is the UUID value, and match names use the stored display names. The name inside `Team '...'` is also the stored display name, so with `Mexico` live, ` mexico ` vs Spain prints `Team 'Mexico'`. A team-live error lists home before away, and a reversed pairing such as Canada vs Mexico names both teams. A same-team error shows the stripped home name.

Both negative scores are reported together because one error then shows the whole problem at no cost. Null and blank names report one team, following the home-before-away check order.

Example messages:

| Case | Message |
|---|---|
| Null name | `Home team name must not be null` |
| Blank name | `Away team name must not be blank` |
| Too long | `Home team name is 5230 characters, maximum is 1000` |
| Same team | `Home and away teams must be different: Spain` |
| Team live | `Team 'Mexico' is already playing in live match Mexico vs Canada` |
| Both live | `Team 'Mexico' is already playing in live match Mexico vs Canada; team 'Spain' is already playing in live match Spain vs Brazil` |
| Reversed pairing | `Team 'Canada' is already playing in live match Mexico vs Canada; team 'Mexico' is already playing in live match Mexico vs Canada` |
| Null ID | `Match ID must not be null` |
| Null value in `MatchId` | `Match ID value must not be null` |
| Negative score | `Home score must not be negative: -1` |
| Both negative | `Scores must not be negative: home -1, away -2` |
| Unknown ID | `No match found with ID <id>` |
| Finished ID | `Match <id> (Mexico vs Canada) is not live, it has already finished` |
| Invalid cap | `Maximum finished matches must be at least 1: 0` |

## 5. Internal design

Package-private implementation, with state guarded by one write lock:

| State | Structure | Purpose |
|---|---|---|
| Live matches | `LinkedHashMap<MatchId, Match>` | Lookup by ID, and insertion order records start order. `Match` is immutable and is replaced on update with `put`, which keeps its position |
| Live teams | `HashMap<String, MatchId>` | Comparison key to match, for the overlap check and error messages |
| Finished matches | `LinkedHashMap<MatchId, Match>` | ID lookup for "not live", and insertion order for FIFO eviction |
| Snapshot | `volatile Snapshot` | `record Snapshot(List<Match> live, List<Match> finished)`, both immutable |

`LinkedHashMap` is not thread-safe by itself. It is safe here because only code holding the write lock touches the maps, and readers use only the immutable snapshot.

**Summary order**

The rebuild takes the live values newest first with `sequencedValues().reversed()`, then sorts by total descending with `List.sort`. That sort is stable, so equal totals stay most recently started first. The code carries a comment explaining this, because the tiebreak does not appear in the comparator. An update must use `put` on the existing key. Removing and re-adding a match would make it look newly started. Test 29 catches this only by updating the newer match first, then the older one, because the opposite order hides the bug.

A start counter with a `LiveMatch` record was considered. It writes the tiebreak directly in the comparator, but adds a record and a counter, and a second ordering mechanism beside the finished matches.

**Team names**

A package-private record holds all name rules, so the board class stays focused on state and locking. The public API stays as strings.

```java
record TeamName(String displayName, String key) {
    static void requireNotBlank(String raw, String label) { ... }
    static void requireMaxLength(String raw, String label) { ... }
    static TeamName of(String raw) { ... }
}
```

`displayName` is the stripped name, and `key` is the comparison key from section 3.1. The checks are separate methods because `startMatch` must run each step for both teams before the next step, as section 3.1 requires. A public `Team` record was rejected because validating in its constructor would throw before the away team is checked, and a constructor does not know whether it is home or away, so the approved error order and messages could not hold.

`startMatch` runs the null or blank checks and the length checks for home then away, builds both `TeamName` values, and compares their keys for the same-team check. All of this happens before the lock. `finishMatch` releases both teams by recomputing their keys from the stored display names, which gives the same keys.

**Write flow**

1. Validate input outside the lock. `startMatch` also generates its UUID here, because that needs no lock.
2. Take the lock, inside `try/finally`, so it is released on every path.
3. Check state and apply the change.
4. Rebuild the affected list. `start` and `update` rebuild the sorted live list. `finish` rebuilds both.
5. Publish a new `Snapshot` through the `volatile` field.
6. Release the lock.

A no-op skips steps 4 and 5. A rejection changes nothing and publishes nothing. Both still release the lock.

**Read flow**

`getSummary` and `getFinishedMatches` read the `volatile` field and return the immutable list. They take no lock.

## 6. Concurrency

### Usage constraints

| Constraint | Value |
|---|---|
| Deployment | One instance per tournament, in one JVM |
| Reads | About 1-100 per second, from a backend with its own cache or push layer |
| Writes | Several threads, such as one consumer per match or partition, with each match's updates delivered in order |
| Freshness | Read-your-writes: a completed write is visible to any summary that starts afterwards |
| Live matches | At most 24, about 6 typical. Design sizing target of 100, not enforced |
| Writes per match | Typically 5-8, about 15 at worst |
| Change rate | Under 0.01 per second at 6 live matches, about 0.02-0.03 at 24, with bursts such as six kick-offs together |
| Feed rate | 10-30 seconds, adding about 0.6 no-op calls per second at 6 live matches on a 10 second feed, 2.4 at 24 |

### What must change together

- Start: check both teams, reserve both, insert the match.
- Update: find the match, replace it, rebuild the live list.
- Finish: remove from live, release both teams, add to finished, evict, rebuild.
- Summary: read one consistent list.

### Chosen approach: lock plus published snapshot

Writers hold one private, default non-fair `ReentrantLock`. Readers read one immutable snapshot through a `volatile` field.

- Reads never block, and writers never block reads.
- Writers block only each other, which is needed for atomicity, and only for microseconds. This is an estimate, not a measurement.
- A blocked virtual thread parks rather than pins on Java 21.
- There are no retries and no wasted work.
- Read-your-writes holds because the write publishes before it returns.
- A finishing match moves between lists in one snapshot.
- Copying and sorting happen in the write, under the lock, and only `finish` rebuilds the finished list. A very large custom cap makes all writers wait during a finish, but never readers.

### Alternatives considered

| Option | Why not chosen |
|---|---|
| Single `ReentrantLock` for everything, copy inside and sort outside | Least code, but readers and writers wait for each other, and each read copies and sorts |
| `ReentrantReadWriteLock` | Readers still wait for writers, and each read still copies and sorts. More overhead than a plain lock for little gain |
| Copy-on-write through a `volatile` field without a separate writer structure | Merged into the chosen approach, which keeps mutable writer state and publishes an immutable copy |
| CAS with immutable state (`AtomicReference.updateAndGet`) | The strongest alternative: lock-free writes and one source of truth. Rejected because retries repeat copying and sorting, there is no fairness, and the transition function must have no side effects. The chosen approach is more predictable and easier to maintain for this load |
| `ConcurrentHashMap` alone | Each map is safe, but check-then-insert across teams is not atomic, so two starts could both pass the live-team check |
| `synchronized` | Less code, and acceptable here: JEP 444 says `synchronized` guarding in-memory operations need not be replaced. `ReentrantLock` was still chosen because a blocked virtual thread parks and frees its carrier on Java 21, at the cost of one `try/finally`. JDK 24 removed this pinning, and we target 21 |
| Fair lock | Costs throughput, and waits are already microseconds |

### Known limit

`getSummary` and `getFinishedMatches` are separate calls, so a finish can land between them. A combined read would be a further operation, which the brief disallows. Calling `getSummary` first and `getFinishedMatches` second guarantees that any match started before the `getSummary` call appears in at least one list, unless it has been evicted.

## 7. Test plan

Stack: JUnit 5 and AssertJ, in test scope. Concurrency tests use plain JDK executors and latches, with timeouts so a hang fails the test. The suite runs on JDK 21 at least once, because Maven on this machine defaults to JDK 25, where the JEP 491 change applies.

Concurrency tests use a fresh board or a large cap unless they test eviction, so eviction cannot change the expected exception.

### startMatch

1. A new match returns an ID, starts at 0-0 and appears in the summary.
2. Null, blank and whitespace-only names are rejected, for home and for away.
3. A name of exactly 1000 characters after stripping is accepted, and 1001 is rejected with a length-only error.
4. Surrounding whitespace does not count towards the cap.
5. `Spain` vs `Spain` and `Spain` vs ` SPAIN ` are rejected as a team playing itself.
6. With Mexico vs Canada live, these are rejected: exact duplicate, reversed pairing, home overlap, away overlap, and overlap with different case or whitespace. When both teams are live in different matches, one error names each team and its match.
7. The summary shows stripped names as first given.
8. After `Mexico` finishes, a new `MEXICO` match displays `MEXICO`.
9. After a finish, both teams can start again, with a new ID at 0-0.
10. Documented name limits: inner spaces, differently encoded Unicode names, `TÜRKİYE` vs `Türkiye`, and `Spain` followed by a non-breaking space all count as different teams, and a name made only of a non-breaking space is accepted.
11. A rejected start leaves the board unchanged and reserves no team. With Mexico vs Canada live, Spain vs Mexico is rejected, and Spain vs Brazil then succeeds.

### updateScore

12. A score is reflected in the summary, and the board reorders by the new total.
13. A negative home score, away score or both is rejected, and a null ID is rejected. When both scores are negative, the error names both.
14. 0 is accepted, and so is `Integer.MAX_VALUE` for both teams, with the total correct and ordered as a `long`.
15. Lowering a score is accepted, and the match moves down the summary.
16. Re-sending the current score changes nothing and does not throw. `getSummary` returns the same list instance before and after, showing nothing was published.
17. An unknown ID gives match not found.
18. A retained finished ID gives match not live, even when re-sending the final score.
19. An evicted ID gives match not found.
20. Input errors come before state errors: a negative score on an unknown or finished match gives the input error.
21. A rejected update leaves the board unchanged. Second half: an update on a retained finished ID leaves its final score and the finished order unchanged.

### finishMatch

22. A finished match leaves the summary and appears in the finished list with its final score.
23. Finishing at 0-0 with no updates removes the match from the summary. Second half: the 0-0 result is in the finished list.
24. A second finish gives match not live, and an unknown ID gives match not found.
25. An evicted ID gives match not found, and a null ID gives the same error as `updateScore`.

### getSummary

26. An empty board returns an empty list.
27. The brief's example gives exactly the expected order.
28. Equal totals put the most recently started first, including when two matches reach a tie through updates.
29. Start order stays the tiebreaker after updates and no-ops. With A started before B, B is updated first and A second, and B stays ahead of A on equal totals.
30. A rematch is treated as newly started. An update with the old ID gets `MatchNotLiveException`, and the rematch stays untouched.
31. The summary contains no finished matches, and changing the returned list cannot alter the board.
32. A summary already returned does not change after later updates.

### getFinishedMatches

33. Multiple finishes list the most recently finished first.
34. Reaching the cap evicts the oldest finished match, and the list size stays at the cap.
35. Order depends on finish order, not on start order or score.
36. No finished matches returns an empty list.

### Configuration and public types

37. `Scoreboard.create()` applies a cap of 1000. After 1001 finishes, the first ID gives match not found and the second gives match not live, so this test needs only IDs.
38. `Scoreboard.create(1)` keeps only the newest finished match.
39. 0 and negative caps throw `IllegalArgumentException`.
40. `new MatchId(null)` throws `IllegalArgumentException`.
41. `Match.toString` prints `Mexico 0 - Canada 5`.
42. Each state exception can be caught as `ScoreboardException`.

### Error order and messages

43. A too-long home name with a null away name gives the away null error.
44. Two identical 1001-character names give the length error.
45. Mexico vs Mexico with Mexico live gives the same-team error.
46. Both names null gives the home error.
47. A null ID with a negative score gives the null ID error.
48. Section 4's messages are asserted for each case.

### Further coverage

49. A rejected finish leaves the board unchanged. Second half: a rejected second finish leaves the finished list, its order and its size unchanged.
50. `getFinishedMatches` cannot be altered through the returned list, and a list already returned does not change after later finishes.
51. An ID from another board gives match not found.
52. A name surrounded by U+2003 or U+3000 is stripped, which `trim()` would not do.
53. With the default locale set to Turkish and then restored, `INDIA` and `india` count as one team.

### Concurrency

54. Many threads start the same fixture at once: exactly one succeeds, and the others get `TeamAlreadyPlayingException`.
55. Overlapping fixtures sharing a team start at once: no team ends up in two live matches.
56. Many threads start different fixtures at once: all succeed, IDs are unique, and every match appears in the summary.
57. Many threads finish the same match: exactly one succeeds, and the others get `MatchNotLiveException`.
58. One updater thread races finish on one match: each update applies or gets `MatchNotLiveException`. Second half: the finished score equals the last successful update, or 0-0 if none succeeded.
59. Summary reads during mixed writes, including no-op sends: every read is sorted by total descending, and no team appears twice. A lock left held after a no-op would hang into the timeout. The tiebreak is checked only for matches started sequentially before the threads run, because `Match` does not expose start order.
60. A start and a finish on the same team, in three parts. Coordinated with a latch so the start completes before the finish begins: the start gets `TeamAlreadyPlayingException`. Coordinated so the finish completes before the start: the start succeeds. Uncoordinated race: either outcome is accepted, and the team is never in two live matches. The race part does not guarantee which outcome occurs, even when repeated.
61. With matches started beforehand and no starts during the run, reading `getSummary` then `getFinishedMatches` during finishes always finds each match in at least one list, with the cap large enough that nothing is evicted.
62. After `updateScore` returns, a summary from another thread shows that score or a newer one. This is a behavioural check that catches a late publish. The thread hand-off creates its own happens-before, so it cannot prove `volatile` is needed. That guarantee rests on the publish under the lock.
63. Concurrent finishes at the cap: the list never exceeds the cap, and evicted IDs give `MatchNotFoundException`.
64. Writes on virtual threads complete within a timeout. This detects failures and hangs, but cannot show parking versus pinning.

### TeamName

65. `TeamName.requireNotBlank` and `requireMaxLength` reject invalid names with the home or away label in the message.
66. `TeamName.of` gives the stripped display name and the comparison key, directly in a unit test.

## 8. Decisions and assumptions register

| # | Decision | Reason |
|---|---|---|
| 1 | Reject any team overlap with a live match | A team cannot play two matches at once |
| 2 | Finish frees both teams | Teams can play again |
| 3 | Teams are distinct home and away sides | Matches the brief's score format |
| 4 | Null, blank and same-team names are invalid | They cannot be a real match |
| 5 | Compare with `strip()` and `toLowerCase(Locale.ROOT)` | Stops surrounding whitespace and simple case differences creating a phantom team, independent of server locale. Known limits are in section 10 |
| 6 | Display stripped names as first given, per match | Keeps the caller's spelling |
| 7 | Name cap of 1000, fixed, after stripping, by `String.length()` | Catches garbage input without rejecting real names |
| 8 | Over-length error gives the length only | Says what is wrong and keeps garbage out of logs |
| 9 | New matches start at 0-0 | The brief does not state otherwise |
| 10 | Most recently started uses `LinkedHashMap` insertion order plus a stable sort, with no timestamps, counter or `LiveMatch` record | Deterministic within one millisecond, less code, and the same structure as finished matches |
| 11 | Rejections throw and leave state unchanged | Failures cannot be ignored |
| 12 | Error messages say exactly what went wrong | Intuitive for library users |
| 13 | `startMatch` returns a `MatchId`; update and finish use it | Rules out reversed teams, and a late update cannot reach a rematch |
| 14 | `MatchId` wraps a UUID | Type safety, and IDs are unique across boards |
| 15 | Updates carry absolute scores | An immediate duplicate is harmless, and a lost message is corrected by the next update |
| 16 | Scores are non-negative `int`, no upper bound | Limiting scores is not this library's job |
| 17 | Total is a `long` | No overflow |
| 18 | Lowering a score is allowed | VAR corrections and mistakes |
| 19 | Same-score update is a detected no-op, invisible to callers | Tolerates an immediate re-send of the current score |
| 20 | Input checks come before state checks | The same bad input always gives the same error |
| 21 | Start error order: null or blank, length, same team, already live; home before away | One predictable order |
| 22 | When both teams are live, one error names both | The caller sees the whole problem |
| 23 | Distinct errors for not found and not live | Exact errors |
| 24 | Finish accepts any score, including 0-0 | A 0-0 draw is a real result |
| 25 | Empty board and empty finished list return empty lists | Normal states |
| 26 | Lists are immutable and fixed | They cannot alter the board |
| 27 | Extra operation is `getFinishedMatches`, newest first | Uses retained data, gives a results view |
| 28 | Finished matches capped, FIFO eviction | Strict memory bound for finished matches |
| 29 | Default cap 1000, minimum 1 | Holds about ten tournaments, newest always kept |
| 30 | Evicted ID gives match not found | Keeps memory strictly bounded |
| 31 | No live match cap | Bounded by the number of teams, avoids a new failure |
| 32 | Interface plus static factories, no builder | One setting only |
| 33 | `Match` record for both lists | Immutable and format-free |
| 34 | `updateScore` and `finishMatch` return `void` | Small API |
| 35 | `IllegalArgumentException` for invalid input, custom unchecked state exceptions | Familiar, and callers can catch exactly |
| 36 | Lock plus published snapshot | Non-blocking reads, no retries, predictable |
| 37 | Default non-fair `ReentrantLock` | Waits are microseconds |
| 38 | Java 21 LTS | Compatibility, builds on JDK 25 |
| 39 | JUnit 5 and AssertJ | Readable tests, no runtime dependency |
| 40 | Core keeps the finished map, cap, eviction and "not live" error. The feature commit adds the finished list to `Snapshot`, its rebuild and `getFinishedMatches` | Core errors need retention, and core holds no dead code |
| 41 | Two-list consistency is not provided | A combined read would be another operation |
| 42 | `MatchId` rejects a null value when constructed | An invalid ID cannot exist |
| 43 | Both negative scores are named in one error | Shows the whole problem at once |
| 44 | Section 4's example messages are the agreed wording | Clear, exact errors |
| 45 | Callers deliver each match's updates in order | Fits one consumer per match, and keeps the API simple. Out-of-order delivery can lower a score |
| 46 | Messages show the UUID value and stored names, with home first | One defined format |
| 47 | The UUID is generated outside the lock | It needs no lock |
| 48 | The suite runs on JDK 21 at least once, with `maven.compiler.release=21` | JDK 25 would not exercise Java 21 behaviour |
| 49 | Name validation lives in a package-private `TeamName` record, and the public API stays as strings | Keeps name rules apart from locking, and keeps the approved error order and messages, which a public `Team` record would break |
| 50 | One instance per tournament, in one JVM, sized for up to 100 live matches with no enforced limit | Matches the World Cup scope, with a buffer above the 24 possible |
| 51 | Reads of about 1-100 per second from a backend with its own cache or push layer | Reads do not come directly from each viewer |
| 52 | Writes come from several threads, in low volume with short bursts | The library must handle real write contention |
| 53 | Read-your-writes across threads | A completed write is visible to any summary that starts afterwards |
| 54 | `Match` has no constructor validation | The library never accepts a `Match` from callers, so a hand-built one cannot reach the board |
| 55 | `create(int)` is configuration, not a second extra operation | It sets a limit rather than adding scoreboard behaviour, so `getFinishedMatches` stays the one extra operation |
| 56 | `Match.toString` prints `Mexico 0 - Canada 5` | Readable output, matching the brief's score format |
| 57 | Custom exceptions live in the `com.sportradar.scoreboard.exception` subpackage | Keeps them apart from the core API types |

### Other alternatives considered

Concurrency alternatives are in section 6.

| Topic | Alternative | Why not chosen |
|---|---|---|
| Team overlap | Reject only the same pairing | Allows one team in two matches |
| Team overlap | Reject only an exact duplicate | Allows Canada vs Mexico, the same real match |
| Team names | Trim only, or exact match | Case differences slip past the overlap rule |
| Team names | Fixed team list, such as an enum | Carries a list that changes with each tournament |
| Name cap | 32, 64 or 100 characters, or no limit | 1000 is generous and still catches garbage input. No limit accepts anything |
| Over-length error | Include a preview or the full name | Puts garbage input in logs |
| Rejections | Return a result, or silently ignore | A caller can miss the failure, and the board can drift |
| Match identity | Team names | Needs reversed-team handling, and a late update could reach a rematch |
| Match identity | Support both names and IDs | Two paths for each operation |
| ID type | `MatchId` wrapping `long`, plain `UUID`, plain `long` | Cross-board collisions with `long`, and less type safety with plain types |
| Score updates | Increment one goal, or support both | A duplicated or lost message corrupts the score, and both means more API |
| Initial score | Optional starting score | Adds a parameter the brief does not need |
| Start order | Timestamps | Same-tick ties have no defined order, and clocks can adjust |
| Score range | `long` scores, or `BigInteger` | More range and cost than football needs |
| Non-live ID | One combined error | The caller cannot tell a bad ID from a late update |
| Error order | State checks first | The error for bad input would depend on match state |
| Finish | Require one update first | A scoreless draw would need a pointless update |
| Summary | Throw on an empty board | An idle board is normal |
| Summary content | Formatted strings | Callers would parse text, and the ID is lost |
| Fifth operation | Another operation, such as finding a live match by team | Finished list uses data already retained |
| Retention | Keep all finished matches | Memory is not strictly bounded |
| Retention | Keep evicted IDs | The ID set grows forever |
| Finished order | Same as summary, or oldest first | The newest result would not be at the top |
| Live matches | Enforced or configurable cap | Adds a failure to `startMatch` |
| API shape | Single public class | The implementation becomes the API |
| API shape | Match handle objects | Mutating handles complicate thread safety |
| API shape | Builder | Only one setting exists |
| `updateScore` return | `boolean`, or the `Match` | The no-op stays internal, for a smaller API |
| `finishMatch` return | The finished `Match` | Smaller API, with the result in the finished list |
| Exceptions | All custom types, or JDK types only | Replaces a familiar exception, or forces parsing message text |
| Null input | `NullPointerException` | Invalid input would need two exception types |
| Default cap | 104 or 10000 | 104 leaves no margin, and 10000 is more than needed |
| Minimum cap | 0 | The newest finished match would not be kept |
| Name cap | Configurable | No real use case, and would need a second setting |
| Java version | 25 or 17 | 25 excludes reviewers on older JDKs, and 17 has no sequenced collections, which `sequencedValues()` needs |
| Test stack | JUnit only, or adding jcstress | Wordier assertions, or a heavy setup for a simple library |
| Feature commit | All retention work, or only the getter | Changes earlier error behaviour, or leaves dead code in core |
| Update order | Sequence number on updates | Changes the API, and is listed as a future improvement |
| Two-list reads | Combined read | A further operation, which the brief disallows |

## 9. Explicitly out of scope

Each of these is documented in the README under assumptions and trade-offs:

- Persistence and restart recovery. The board is in memory only.
- Match events and phases: goal scorers, minutes, half-time, extra-time, pause states.
- Penalty shootouts and tournament rules: groups, brackets, team lists.
- Cancelling or deleting a match. A wrongly started match can only be finished, so it appears in the finished list.

## 10. Future improvements

- Collapse inner whitespace in the comparison key.
- Unicode normalisation in the comparison key. Today, differently encoded forms of one name count as different teams.
- Accent handling in the comparison key.
- Full case-folding in the comparison key. Today, `TÜRKİYE` and `Türkiye` get different keys, so one real team could be live twice under two spellings. `STRASSE` and `Straße` also differ.
- Whitespace that `strip()` leaves in place, such as a non-breaking space (U+00A0), and invisible characters such as U+200B and U+FEFF. Today, a name made only of a non-breaking space is accepted, and `Spain` followed by one counts as a different team.
- Stale update protection. Each update would carry a per-match sequence number, and stale ones would be rejected, so out-of-order or redelivered updates could not lower a score. This changes the public API, so a correction would use a new sequence number.
- A builder, if more settings appear.
- A custom storage layer supplied by the library user through an interface.

The first four each change one method. The fifth stays within `TeamName`, but also affects the blank and length checks.

## 11. Research

### Team name length

| Name type | Examples | Length |
|---|---|---|
| Typical national team | Spain 5, Germany 7, Argentina 9, Saudi Arabia 12 | 4-12 |
| Long country name | Trinidad and Tobago 19, Bosnia and Herzegovina 22 | 19-22 |
| Longest found in common short form | Democratic Republic of the Congo 32, Saint Vincent and the Grenadines 32 | 32 |
| Formal state name | United Kingdom of Great Britain and Northern Ireland 52 | 52 |
| Longest reported club names | Llanfairpwll FC 90, MPKB BRI-Ubes FC 89, NAC Breda 86 | 86-90 |

Web sources were weak and disagreed, and no ranked FIFA list was found, so 32 is the longest found rather than a verified maximum. Club lengths are reported figures. Other lengths were counted directly.

### Load

- 172 goals in 64 matches at the 2022 World Cup, 2.69 per match.
- Highest-scoring match: Austria 7-5 Switzerland, 1954, 12 goals.
- 104 matches in the 48-team format, 72 of them in the group stage.
- Final group matches kick off together in pairs. One source says six at once, and sources conflict on the daily count.
- Live feeds range from 1-2 second push to 60 second polling, with 30 seconds most common for scores. There is no standard.
- Evidence on read-write locks over very short critical sections is indirect, from blogs and lecture notes rather than a head-to-head benchmark.
- Lock hold time is an estimate and has not been measured.

### Virtual threads

- JEP 444, delivered in Java 21: a virtual thread is pinned when it blocks inside `synchronized` code or a native method. Its carrier stays blocked for that time.
- JEP 444's concern is frequent pinning for long durations. It says `synchronized` that is used infrequently or guards in-memory operations need not be replaced. Our critical sections are in-memory, so `synchronized` would have been acceptable.
- JEP 444 recommends `ReentrantLock` for code that runs frequently and guards potentially long I/O. That does not describe this library.
- `java.util.concurrent` locks park through `LockSupport`, which releases the carrier. This is why `ReentrantLock` does not pin on Java 21.
- JEP 491 removed `synchronized` pinning in JDK 24.
- Pinning can be diagnosed with the `jdk.VirtualThreadPinned` JFR event or the `jdk.tracePinnedThreads` system property on Java 21.

### Sources

- [WorldAtlas: longest country name](https://www.worldatlas.com/articles/what-is-the-longest-country-name-in-the-world.html)
- [HowStuffWorks: longest country name](https://history.howstuffworks.com/world-history/longest-country-name.htm)
- [List of FIFA country codes](https://en.wikipedia.org/wiki/List_of_FIFA_country_codes)
- [Stadium Astro: long club names](https://english.stadiumastro.com/columns/malaysian-club-have-one-world-s-longest-football-team-names-187399)
- [C.P.D. Llanfairpwll F.C.](https://en.wikipedia.org/wiki/C.P.D._Llanfairpwll_F.C.)
- [World Cup stats by tournament](https://www.thestatsapi.com/world-cup/history/2022)
- [World Cup records](https://www.thestatsapi.com/world-cup/records)
- [2026 schedule guide](https://worldcupranking.com/blog/world-cup-2026-schedule-complete/)
- [Final group schedule](https://www.outlookindia.com/sports/football/fifa-world-cup-2026-complete-final-group-stage-schedule-guide-live-streaming-india-time)
- [TheStatsAPI live guide](https://www.thestatsapi.com/docs/guides/live-matches.md)
- [Sportmonks best practices](https://docs.sportmonks.com/v3/welcome/best-practices)
- [Explicit lock benchmark](https://dzone.com/articles/performance-benchmark-of-explicit-locks-part-1)
- [Read-write lock usage notes](https://www.cleverence.com/amp/articles/oracle-documentation/readwritelock-java-platform-se-8-5834/)
- [JEP 444](https://openjdk.org/jeps/444)
- [JEP 491](https://openjdk.org/jeps/491)
- [InfoQ: virtual threads after JDK 24](https://infoq.com/articles/virtual-threads-after-jdk24)

## 12. Deliverables

- `pom.xml` for a Java 21 Maven project, with `maven.compiler.release=21`.
- Library source and tests.
- `.gitignore` that covers `.idea/` and `target/`.
- `README.md`, with these parts:
  - How to build and test the library.
  - Usage.
  - Core operations supported.
  - A log of assumptions, including in-order update delivery.
  - Decisions.
  - Trade-offs, including every alternative in sections 5, 6 and 8, and the explicit exclusions.
  - Design and the reasoning behind it.
  - Research and sources.
  - A separate header for the additional feature, saying what it is and why it was chosen. It explains that `create(int)` is configuration rather than a second extra operation.
  - Tests.
  - Future improvements.
- `AI.md`, with these parts:
  - A summary of how AI tools were used, plus context such as the tools and models.
  - Artifacts that guided the implementation: this spec, the implementation plan and both review reports in `docs/superpowers/`, each linked. The assignment PDF is mentioned but not included.
  - Prompt history across every session, including implementation, either in `AI.md` or in a linked transcript. User prompts are kept verbatim where available. Model output is lightly summarised for readability, so it is clear how the user guided the AI to the implementation.
- `docs/superpowers/`, committed: the spec, the implementation plan, and the review reports in `reviews/`.
- Core commits contain the finished map, cap, eviction, the "not live" error, and tests that use IDs only, such as tests 19, 25 and 37.
- A distinct git commit introduces the feature: the finished list in `Snapshot`, its rebuild, `getFinishedMatches`, its README section, and the tests that read the list. Those are the second halves of 21, 22, 23, 49 and 58, plus 33-36, 38, 50, 61 and 63.
