# Live Football World Cup Scoreboard

A thread-safe Java library that manages a live football World Cup scoreboard across multiple simultaneous matches. It has no runtime dependencies.

## Build and test

Requires JDK 21 or newer and Maven 3.9.

```bash
mvn verify
```

## Usage

```java
Scoreboard board = Scoreboard.create();

MatchId mexicoCanada = board.startMatch("Mexico", "Canada");
MatchId spainBrazil = board.startMatch("Spain", "Brazil");

board.updateScore(mexicoCanada, 0, 5);
board.updateScore(spainBrazil, 10, 2);

for (Match match : board.getSummary()) {
    System.out.println(match);
}
// Spain 10 - Brazil 2
// Mexico 0 - Canada 5

board.finishMatch(mexicoCanada);

for (Match match : board.getFinishedMatches()) {
    System.out.println(match);
}
// Mexico 0 - Canada 5
```

`Scoreboard.create(int)` sets how many finished matches are retained. The default is 1000.

## Core operations

| Operation | Behaviour | Rejected with |
|---|---|---|
| `startMatch(home, away)` | Starts a match at 0-0 and returns its `MatchId` | `IllegalArgumentException` for a null, blank or over-long name, or the same team twice. `TeamAlreadyPlayingException` if either team is live |
| `updateScore(id, home, away)` | Sets the absolute score of both teams. Sending the current score does nothing | `IllegalArgumentException` for a null ID or negative score. `MatchNotFoundException`, `MatchNotLiveException` |
| `finishMatch(id)` | Finishes a live match and frees both teams | `IllegalArgumentException` for a null ID. `MatchNotFoundException`, `MatchNotLiveException` |
| `getSummary()` | Returns live matches by total score descending, then most recently started first | Never. An empty board returns an empty list |

Rejected operations throw and leave the board unchanged. Messages say exactly what went wrong, for example:

```
Team 'Mexico' is already playing in live match Mexico vs Canada
Home team name is 5230 characters, maximum is 1000
Match 1b4e... (Mexico vs Canada) is not live, it has already finished
```

`MatchNotFoundException` means the ID is unknown to this board, or its finished match has been evicted. `MatchNotLiveException` means the match finished and is still retained. Both state exceptions and `TeamAlreadyPlayingException` extend `ScoreboardException`, in the `com.sportradar.scoreboard.exception` package.

## Assumptions

The brief leaves these open on purpose.

- A new match always starts at 0-0.
- Updates carry the absolute score of both teams.
- A team can be in only one live match. Finishing a match frees both teams.
- Teams are distinct home and away sides.
- Team names are compared with surrounding whitespace removed, ignoring case. Each match displays the names it was started with.
- Scores are non-negative `int` values with no upper limit from the library.
- Lowering a score is allowed, for VAR corrections and mistakes.
- Callers deliver each match's updates in order, which fits one consumer per match. The no-op protects only against an immediate duplicate of the current score. After 1-0 then 2-0, a redelivered 1-0 lowers the board, because the library cannot tell it from a correction. If two threads update one match, the last to take the lock wins.
- A match can finish at any score, including 0-0 with no updates.
- The library is thread-safe. The brief does not ask for this, so it is our own requirement.
- One instance serves one tournament in one JVM.
- Reads arrive at about 1-100 per second from a backend with its own cache or push layer, not from each viewer.
- Writes come from several threads in low volume, with short bursts.
- At most 24 matches are live, about 6 typically. The design is sized for 100, with no enforced limit.

These are explicitly out of scope:

- Persistence and restart recovery. The board is in memory only.
- Match events and phases: goal scorers, minutes, half-time, extra-time, pause states.
- Penalty shootouts and tournament rules: groups, brackets, team lists.
- Cancelling or deleting a match. A wrongly started match can only be finished.

## Decisions

Numbers match the design spec's register.

| # | Decision | Reason |
|---|---|---|
| 1 | Reject any team overlap with a live match | A team cannot play two matches at once |
| 2 | Finish frees both teams | Teams can play again |
| 3 | Teams are distinct home and away sides | Matches the brief's score format |
| 4 | Null, blank and same-team names are invalid | They cannot be a real match |
| 5 | Compare with `strip()` and `toLowerCase(Locale.ROOT)` | Stops surrounding whitespace and simple case differences creating a phantom team, independent of server locale |
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
| 44 | Error messages use one agreed wording | Clear, exact errors |
| 45 | Callers deliver each match's updates in order | Fits one consumer per match, and keeps the API simple. Out-of-order delivery can lower a score |
| 46 | Messages show the UUID value and stored names, with home first | One defined format |
| 47 | The UUID is generated outside the lock | It needs no lock |
| 48 | The suite runs on JDK 21 at least once, with `maven.compiler.release=21` | JDK 25 would not exercise Java 21 behaviour |
| 49 | Name validation lives in a package-private `TeamName` record, and the public API stays as strings | Keeps name rules apart from locking, and keeps the error order and messages, which a public `Team` record would break |
| 50 | One instance per tournament, in one JVM, sized for up to 100 live matches with no enforced limit | Matches the World Cup scope, with a buffer above the 24 possible |
| 51 | Reads of about 1-100 per second from a backend with its own cache or push layer | Reads do not come directly from each viewer |
| 52 | Writes come from several threads, in low volume with short bursts | The library must handle real write contention |
| 53 | Read-your-writes across threads | A completed write is visible to any summary that starts afterwards |
| 54 | `Match` has no constructor validation | The library never accepts a `Match` from callers, so a hand-built one cannot reach the board |
| 55 | `create(int)` is configuration, not a second extra operation | It sets a limit rather than adding scoreboard behaviour, so `getFinishedMatches` stays the one extra operation |
| 56 | `Match.toString` prints `Mexico 0 - Canada 5` | Readable output, matching the brief's score format |
| 57 | Custom exceptions live in the `com.sportradar.scoreboard.exception` subpackage | Keeps them apart from the core API types |

## Trade-offs

### Concurrency

| Option | Why not chosen |
|---|---|
| Single `ReentrantLock` for everything, copy inside and sort outside | Least code, but readers and writers wait for each other, and each read copies and sorts |
| `ReentrantReadWriteLock` | Readers still wait for writers, and each read still copies and sorts. More overhead than a plain lock for little gain |
| Copy-on-write through a `volatile` field without a separate writer structure | Merged into the chosen approach, which keeps mutable writer state and publishes an immutable copy |
| CAS with immutable state (`AtomicReference.updateAndGet`) | The strongest alternative: lock-free writes and one source of truth. Rejected because retries repeat copying and sorting, there is no fairness, and the transition function must have no side effects. The chosen approach is more predictable and easier to maintain for this load |
| `ConcurrentHashMap` alone | Each map is safe, but check-then-insert across teams is not atomic, so two starts could both pass the live-team check |
| `synchronized` | Less code, and acceptable here: JEP 444 says `synchronized` guarding in-memory operations need not be replaced. `ReentrantLock` was still chosen because a blocked virtual thread parks and frees its carrier on Java 21, at the cost of one `try/finally`. JDK 24 removed this pinning, and we target 21 |
| Fair lock | Costs throughput, and waits are already microseconds |

The chosen approach copies and sorts in the write, under the lock, and only `finish` rebuilds the finished list. A very large custom cap would make all writers wait during a finish, but never readers.

`getSummary` and `getFinishedMatches` are separate calls, so a finish can land between them. A combined read would be a further operation, which the brief disallows. Calling `getSummary` first and `getFinishedMatches` second guarantees that any match started before the `getSummary` call appears in at least one list, unless it has been evicted.

### Internal structure

| Option | Why not chosen |
|---|---|
| Start counter with a `LiveMatch` record | Writes the tiebreak directly in the comparator, but adds a record and a counter, and a second ordering mechanism beside the finished matches. The chosen approach's tiebreak is implicit, so the code carries a comment and a test covers it |
| Public `Team` record | Validating in its constructor would throw before the away team is checked, and a constructor does not know whether it is home or away, so the agreed error order and messages could not hold |

### Other alternatives

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

### Excluded scope

Persistence, match events, tournament rules and match cancellation are left out to keep the library simple, as listed under [Assumptions](#assumptions). The cost is that the board is lost when the process stops, and a wrongly started match can only be finished.

## Design and reasoning

`Scoreboard` is the only entry point. The implementation, `InMemoryScoreboard`, is package-private, so its concurrency approach can change without affecting users.

### State

All state below is touched only by code holding one private `ReentrantLock`.

| State | Structure | Purpose |
|---|---|---|
| Live matches | `LinkedHashMap<MatchId, Match>` | Lookup by ID, and insertion order records start order |
| Live teams | `HashMap<String, MatchId>` | Comparison key to match, for the overlap check and error messages |
| Finished matches | `LinkedHashMap<MatchId, Match>` | ID lookup for "not live", and insertion order for FIFO eviction |

`LinkedHashMap` is not thread-safe by itself. It is safe here because only code holding the lock touches the maps, and readers use only an immutable snapshot.

### Summary order

The rebuild takes the live values newest first, then sorts by total descending with `List.sort`. That sort is stable, so equal totals stay most recently started first. An update uses `put` on the existing key, which keeps the match's position.

### Team names

`TeamName` holds all name rules, so `InMemoryScoreboard` stays focused on state and locking. Its checks are separate methods because `startMatch` runs each step for both teams before the next step. `finishMatch` releases both teams by recomputing their keys from the stored names, which gives the same keys.

### Writes and reads

1. A write validates input outside the lock, and `startMatch` generates its UUID there too.
2. It takes the lock in `try/finally`, checks state and applies the change.
3. It rebuilds the affected list and publishes a new immutable `Snapshot` through a `volatile` field. `start` and `update` rebuild the sorted live list, and `finish` rebuilds both lists.
4. It releases the lock.

A no-op skips the rebuild and the publish. A rejection changes nothing.

`getSummary` and `getFinishedMatches` read the `volatile` field and return the immutable list, without taking the lock.

### Why this concurrency approach

What must change together decides the design:

- Start: check both teams, reserve both, insert the match.
- Update: find the match, replace it, rebuild the live list.
- Finish: remove from live, release both teams, add to finished, evict, rebuild.

So a single atomic write section is needed in any approach. Reads vastly outnumber writes, which suits publishing a snapshot:

- Reads never block, and writers never block reads.
- Writers block only each other, and only for microseconds. This is an estimate, not a measurement.
- A blocked virtual thread parks rather than pins on Java 21.
- There are no retries and no wasted work.
- Read-your-writes holds because the write publishes before it returns.
- A finishing match moves between lists in one snapshot.

## Research

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

| Constraint | Value |
|---|---|
| Goals per match | 172 goals in 64 matches at the 2022 World Cup, 2.69 per match |
| Highest-scoring match | Austria 7-5 Switzerland, 1954, 12 goals |
| Tournament size | 104 matches in the 48-team format, 72 of them in the group stage |
| Writes per match | Typically 5-8, about 15 at worst |
| Change rate | Under 0.01 per second at 6 live matches, about 0.02-0.03 at 24, with bursts such as six kick-offs together |
| Feed rate | 10-30 seconds, adding about 0.6 no-op calls per second at 6 live matches on a 10 second feed, 2.4 at 24 |

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

## Additional feature: recently finished matches

`getFinishedMatches()` returns finished matches with their final scores, most recently finished first. An empty history returns an empty list, and the result is immutable and fixed, like the summary.

**Why this feature:** the board has to retain finished matches anyway, so it can tell a late update on a finished match (`MatchNotLiveException`) from an unknown ID (`MatchNotFoundException`). Exposing that data gives callers a results view beside the live summary, at very little extra cost. `finishMatch` returns nothing, so this is also how a caller reads a final result.

**Bounded retention:** history keeps the last 1000 finished matches by default, about ten 104-match tournaments. With one instance per tournament, eviction never fires, so the cap protects against a long-lived instance. `Scoreboard.create(int)` changes the limit, and it must be at least 1. Eviction is first in, first out.

**What this means for callers:**

- The minimum cap is one so history retains at least the most recently finished match. History is bounded; a particular result may be evicted before a caller retrieves it.
- An evicted match's ID gives `MatchNotFoundException`, because the library no longer holds a record of it. Old IDs cannot be told apart from unknown ones.
- The strict memory bound covers finished matches only. Live matches are bounded in practice by the number of teams.

`Scoreboard.create(int)` is configuration rather than a second extra operation: it sets a limit, and adds no scoreboard behaviour.

## Tests

Tests use JUnit 5 and AssertJ, and each test class covers one area:

| Test class | Covers |
|---|---|
| `StartMatchTest` | Validation, team overlap, error order, name handling and its documented limits |
| `UpdateScoreTest` | Absolute updates, no-ops, corrections, overflow and error order |
| `FinishMatchTest` | Finishing, releasing teams, retention and eviction |
| `SummaryTest` | The brief's example, ordering, tiebreaks and immutability |
| `FinishedMatchesTest` | Finished order, final scores, the cap and immutability |
| `ConfigurationTest` | The default cap and invalid caps |
| `TeamNameTest`, `MatchTest`, `MatchIdTest`, `ScoreboardExceptionTest` | The supporting types |
| `ConcurrencyTest` | Races between threads, using JDK executors and latches |

Concurrency tests have limits:

- They use timeouts and `shutdownNow`, so a held lock fails the test instead of hanging the build.
- Running them against a board with the lock removed fails several of them. A race test cannot guarantee which ordering occurs, so passing is evidence rather than proof.
- The read-your-writes test catches a late publish, but it cannot prove `volatile` is needed, because the hand-off between threads creates its own happens-before edge. That guarantee rests on publishing under the lock.
- The virtual thread test detects failures and hangs, but cannot show parking versus pinning.

## Future improvements

- Collapse inner whitespace in the comparison key.
- Unicode normalisation in the comparison key. Today, differently encoded forms of one name count as different teams.
- Accent handling in the comparison key.
- Full case-folding in the comparison key. Today, `TÜRKİYE` and `Türkiye` get different keys, so one real team could be live twice under two spellings. `STRASSE` and `Straße` also differ.
- Whitespace that `strip()` leaves in place, such as a non-breaking space (U+00A0), and invisible characters such as U+200B and U+FEFF. Today, a name made only of a non-breaking space is accepted, and `Spain` followed by one counts as a different team.
- Stale update protection. Each update would carry a per-match sequence number, and stale ones would be rejected, so out-of-order or redelivered updates could not lower a score. This changes the public API, so a correction would use a new sequence number.
- A builder, if more settings appear.
- A custom storage layer supplied by the library user through an interface.
