# Live Football World Cup Scoreboard

A thread-safe Java library that manages a live football World Cup scoreboard across multiple simultaneous matches. It has no runtime dependencies.

## Build and test

Requires JDK 21 or newer. The Maven Wrapper downloads the right Maven version on first use.

```bash
./mvnw verify
```

On Windows, use `mvnw.cmd verify`.

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

## Project layout

```
src/main/java/com/sportradar/scoreboard/
  Scoreboard.java           Public interface and factories, the only entry point
  Match.java, MatchId.java  Public immutable types
  InMemoryScoreboard.java   Package-private implementation: state, locking, snapshots
  TeamName.java             Package-private name validation and comparison key
  exception/                Public state exceptions
src/test/java/com/sportradar/scoreboard/
                            Unit and concurrency tests, see Tests below
docs/superpowers/           Design spec, implementation plan and review reports
AI.md                       How AI was used, artifacts and prompt history
mvnw, mvnw.cmd, .mvn/       Maven Wrapper
```

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

`MatchNotFoundException` means the ID is unknown to this board, or its finished match has been evicted. `MatchNotLiveException` means the match finished and is still retained. All three state exceptions extend `ScoreboardException`, in the `com.sportradar.scoreboard.exception` package.

## Assumptions

The brief leaves these open on purpose.

- A new match always starts at 0-0.
- Updates carry the absolute score of both teams.
- A team can be in only one live match. Finishing a match frees both teams.
- Teams are distinct home and away sides.
- Team names are compared with surrounding whitespace removed, ignoring case. Each match displays the names it was started with.
- Callers spell a team consistently, apart from case and surrounding whitespace. For example, `TÜRKİYE` and `Türkiye` count as different teams.
- Callers keep the `MatchId` from `startMatch`, or read it from the summary. There is no lookup by team name.
- Scores are non-negative `int` values with no upper limit from the library.
- Lowering a score is allowed, for VAR corrections and mistakes.
- Callers deliver each match's updates in order, which fits one consumer per match. The no-op protects only against an immediate duplicate of the current score. After 1-0 then 2-0, a redelivered 1-0 lowers the board, because the library cannot tell it from a correction. If two threads update one match, the last to take the lock wins.
- A match can finish at any score, including 0-0 with no updates.
- Callers read a final result before it is evicted, because finished history is bounded.
- The library is thread-safe. The brief does not ask for this, so it is our own requirement.
- One instance serves one tournament in one JVM.
- Reads arrive at about 1-100 per second from a backend with its own cache or push layer, not from each viewer.
- Writes come from several threads in low volume, with short bursts.
- A completed write must be visible to any read that starts afterwards, across threads.
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

Each choice below shows what it gives, what it costs, and what was rejected.

### Main choice: lock plus published snapshot

Writers hold one `ReentrantLock`, change the maps, and publish an immutable snapshot through a `volatile` field. Readers only read that field.

**Benefits:**

- Reads never block. They wait for neither other readers nor writers, and a read is a single `volatile` field read.
- Writes are atomic. Checking and reserving teams on start, and moving a match between lists on finish, happen in one critical section, so no reader sees partial state.
- Read-your-writes holds, because a write publishes before it returns.
- There are no retries. Each write does its work once, so its cost stays predictable during bursts such as six kick-offs at once.
- A writer waiting for the lock parks on Java 21, so it does not pin a virtual thread.
- It is simple to reason about: one lock, and one place where state changes.

**Costs:**

- Each state-changing write copies and sorts the live list under the lock. Writes do this work so that reads do none. That is cheap at up to about 100 live matches, but it would be the wrong choice for a write-heavy load.
- Writers run one at a time, even for unrelated matches. The wait is estimated at microseconds, and has not been measured.
- Two representations must stay in sync: the mutable maps and the snapshot. A write path that forgot to publish would leave readers with old data.
- Each write allocates a new list and a new snapshot, which adds a little garbage collection work.
- `finish` rebuilds the finished list under the lock. A very large custom cap would make all writers wait during a finish, but never readers.

**Alternatives:**

| Alternative | What it offers | Why not chosen |
|---|---|---|
| CAS with immutable state (`AtomicReference.updateAndGet`) | The strongest alternative: lock-free writes, and one source of truth with nothing to keep in sync | Retries repeat the copying and sorting, there is no fairness, and the transition function must have no side effects. The chosen approach is more predictable and easier to maintain for this load |
| Single `ReentrantLock` for everything, copy inside and sort outside | The least code | Readers and writers wait for each other, and each read copies and sorts |
| `ReentrantReadWriteLock` | Readers run in parallel | Readers still wait for writers, and each read still copies and sorts. More overhead than a plain lock for little gain |
| `ConcurrentHashMap` alone | Each map is safe with no explicit lock | Check-then-insert across teams is not atomic, so two starts could both pass the live-team check |

### Other concurrency choices

| Choice | Benefit | Cost | Alternatives |
|---|---|---|---|
| `ReentrantLock` | Parks virtual threads on Java 21, never pins | One `try/finally` on each write | `synchronized`: less code and acceptable here, as JEP 444 says it need not be replaced around in-memory work, but it pins on Java 21. JDK 24 removed this pinning |
| Default non-fair lock | Higher throughput | A writer can be overtaken | Fair lock: costs throughput, and waits are already microseconds |
| Wrap the sorted live list instead of copying | Avoids a second copy on each write | Safe only while nothing else keeps a reference to that list | `List.copyOf`: safe by construction, but copies twice. `finishMatch` keeps it because it reads a live view of the map |
| Separate `getSummary` and `getFinishedMatches` | Keeps exactly one extra operation | A finish can land between the two calls | Combined read: a further operation, which the brief disallows |

Calling `getSummary` first and `getFinishedMatches` second guarantees that any match started before the `getSummary` call appears in at least one list, unless it has been evicted.

### Internal structure

| Choice | Benefit | Cost | Alternatives |
|---|---|---|---|
| `LinkedHashMap` insertion order plus a stable sort | Deterministic order even when ties | The tiebreak is implicit, so the code carries a comment and a test covers it | Start counter with a `LiveMatch` record: explicit in the comparator, but adds a record, a counter and a second ordering mechanism. Timestamps: same-tick ties have no defined order, and clocks can adjust |

### Public API

| Choice | Benefit | Cost | Alternatives |
|---|---|---|---|
| `MatchId` from `startMatch` | No reversed teams | The caller keeps the ID | Team names: needs reversed-team handling. Both: two paths for each operation |
| `MatchId` wraps a UUID | Type safe, and unique across boards | Long in logs | Wrapping `long`: cross-board collisions. Plain `UUID` or `long`: less type safety |
| Interface plus static factories | The implementation can change without affecting users | One more type | Single public class: the implementation becomes the API. Match handle objects: mutating handles complicate thread safety. Builder: only one setting exists |
| One immutable `Match` record | Format-free, and cannot alter the board | Callers format their own output | Formatted strings: callers would parse text, and the ID is lost |
| `updateScore` and `finishMatch` return `void` | A smaller API | Callers cannot see a no-op, or get a result directly from `finishMatch` | `boolean` or the `Match` from `updateScore`. The finished `Match` from `finishMatch` |
| `IllegalArgumentException` for input, custom unchecked state exceptions | Familiar, and callers can catch exactly | Two kinds of failure to document | All custom types: replaces a familiar exception. JDK types only: forces parsing message text. `NullPointerException` for null: invalid input would need two exception types |

### Behaviour

| Choice | Benefit | Cost | Alternatives |
|---|---|---|---|
| Compare team names with `strip()` and lower case | Small and easy to explain | `TÜRKİYE` and `Türkiye` count as different teams | Trim only, or exact match: case differences slip past the overlap rule. Expanding the comparison to inner spaces, Unicode normalisation, accents and full case-folding: discussed, and postponed as a future improvement |
| Team name cap of 1000, fixed | Catches garbage input without rejecting real names | Accepts names far longer than any real team | 32, 64 or 100 characters: less margin. No limit: accepts anything. Configurable: no real use case, and a second setting |
| Absolute score updates | An immediate duplicate is harmless, and a lost update is corrected by the next one | A late older score looks like a correction, which is why in-order delivery is assumed | Increment one goal, or both: a duplicated or lost message corrupts the score. Sequence number on updates: changes the API, and is a future improvement |
| Scores can be lowered | Supports VAR corrections and fixing mistakes | The library cannot tell a correction from a wrong or late update | Reject lower scores. Separate correction operation: more API, and the brief allows only one extra operation |
| `int` scores with a `long` total | No overflow, and simple to use | Scores stop at about 2.1 billion | `long` scores or `BigInteger`: more range and cost than football needs |
| Finish accepts any score | A 0-0 draw is a real result | An accidental finish is not caught | Require one update first: a scoreless draw would need a pointless update |

### Finished matches

| Choice | Benefit | Cost | Alternatives |
|---|---|---|---|
| Finished matches kept (as extra operation) | Results can be checked after a match has finished | Adds retention to a live scoreboard | Another operation, such as finding a live match by team |
| Capped history with FIFO eviction | Memory is strictly bounded | A result can be evicted before it is read | Keep all finished matches: memory is not strictly bounded |
| Evicted ID gives match not found | Keeps memory strictly bounded | An old ID cannot be told apart from an unknown one | Keep evicted IDs: the ID set grows forever |
| Most recently finished first | The newest result is at the top | Finish order must be tracked | Same as summary: the newest finished result would not be at the top |
| Default cap 1000, minimum 1 | Holds about ten tournaments, and the newest is always kept | One setting to document | 104: no margin. 10000: more than needed. Minimum of 0: the newest would not be kept |
| No live match cap | Avoids a new failure in `startMatch` | The strict memory bound covers finished matches only | Enforced or configurable cap: adds a failure to `startMatch` |

### Project

| Choice | Benefit | Cost | Alternatives |
|---|---|---|---|
| Java 21 LTS | Sequenced collections, and wide compatibility | Excludes newer language features | 25: lower compatibility with older JDKs. 17: has no sequenced collections, which `sequencedValues()` needs |
| JUnit 5 and AssertJ | Readable tests, and no runtime dependency | A second test dependency | Adding jcstress: a heavy setup for a simple library |

### Excluded scope

Persistence, match events, tournament rules and match cancellation are left out to keep the library simple, as listed under [Assumptions](#assumptions). The cost is that the board is lost when the process stops, and a wrongly started match can only be finished.

## Design and reasoning

`Scoreboard` is the only entry point. The implementation, `InMemoryScoreboard`, is package-private, so its concurrency approach can change without affecting users.

### Public API

- **Match identity:** `startMatch` returns a `MatchId`, and `updateScore` and `finishMatch` use it. With team names alone, reversed home and away teams would need handling.
- **ID type:** `MatchId` wraps a UUID. The wrapper means an ID cannot be confused with a score, and UUIDs are unique across boards, so an ID from another board is simply not found.
- **Results:** one immutable `Match` record serves both lists. Callers format it as they like and cannot alter the board through it.
- **Creation:** static factories are enough because there is one setting. A builder can be added if more settings appear.
- **Return values:** `updateScore` and `finishMatch` return `void`, which keeps the API small. A no-op stays internal, and a final result is read from `getFinishedMatches`.

### Error handling

Failures fall into two groups, because callers handle them differently:

- **Invalid input** is a caller bug, such as a null ID or a negative score. It uses `IllegalArgumentException`, which Java developers already expect.
- **State conflicts** depend on the board, such as a team that is already playing. They use specific unchecked exceptions under `ScoreboardException`, so callers can catch one case or all of them without parsing messages.

Input checks run before state checks, so the same bad input always gives the same error, whatever the board holds. Input is validated before the lock is taken, and state is checked before anything changes, so a rejection leaves the board untouched.

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

The two lists are built differently. `sortedLive` wraps a fresh list with `Collections.unmodifiableList` rather than copying it, because nothing else holds that list, so it can never change. `finishMatch` uses `List.copyOf` because `sequencedValues().reversed()` is a live view of the finished map, which later finishes would change.

### Why this concurrency approach

What must change together decides the design:

- Start: check both teams, reserve both, insert the match.
- Update: find the match, replace it, rebuild the live list.
- Finish: remove from live, release both teams, add to finished, evict, rebuild.

So a single atomic write section is needed in any approach. Reads far outnumber state-changing writes, as the [load figures](#load) show, and [usage assumptions](#assumptions) keep live matches few, which suits publishing a snapshot:

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
