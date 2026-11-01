# Whole-branch review: scoreboard library

Date: 2026-11-01
Reviewer: independent AI subagent on Claude Opus, read-only for the repository, with no prior context
Reviewed: `feature/scoreboard-library`, 18b229f..ba5e9eb
Outcome: both Important findings were fixed in `Strengthen finished list tests`. The user chose to fix all six Minor findings too, in `Address branch review minor findings`. The suite now has 122 tests

**Verdict:** ready to merge with fixes. No production defects. Two test gaps, both small test-only changes.

## Important

**1. Nothing tested that the finished list survives later live activity.** Changing `publishLive` to drop the finished list left all 120 tests passing, although every result would vanish on the next kick-off or goal. Fix: a test that starts and updates after a finish, then compares the list.

**2. Spec test 35 did not prove what it claimed.** The high score went on the match expected first, so score order and finish order agreed. Sorting the finished list by total left all 120 tests passing. Fix: put the high score on the other match.

## Minor

1. Test 61 weakly detects a split publish. It missed a two-step publish in 6 of 6 runs, because nothing ensures the reader has started and the reader spends most of its time in AssertJ.
2. Reader threads keep spinning after a timeout, because the stop flags are set after `runTogether`. This costs only CPU after a failure.
3. A lock leak on a rejection path is not caught. `try/finally` makes this safe by construction.
4. README "Reads vastly outnumber writes" is overstated. At 1 read per second it is fewer than 2.4 no-op calls per second at 24 live matches. It holds for state-changing writes.
5. README "Both state exceptions and `TeamAlreadyPlayingException`" reads oddly, since all three are state exceptions.
6. `StartMatchTest` has raw invisible characters in literals, which an editor could normalise. Use `\u` escapes.

## Review Focus

| # | Item | Result |
|---|---|---|
| 1 | Eviction does not release a team | Pass, its mutant fails the test |
| 2 | Rejected finish of an old ID does not release a team | Pass, its mutant fails the test |
| 3 | ` Mexico ` released on finish | Pass, its mutant fails the test |
| 4 | `%` appears literally | Pass, no format calls in `src/main` |
| 5 | 100 live matches give a correct summary | Pass, also probed with ties |

## Sound areas

- Every map access is under the lock, `snapshot` is written only under the lock, and reads touch only the volatile field.
- Conflict paths throw before any change and reserve nothing.
- All 8 commits with a `pom.xml` pass on JDK 21 on their own, with 6, 14, 51, 74, 96, 107, 107 and 120 tests.
- Feature commit ba5e9eb is self-contained.
- All 66 spec tests exist and assert what the spec says, except test 35. Test 38 sits in `FinishedMatchesTest`, a harmless deviation.
- The README matches the code, apart from Minor 4. The usage example prints what it documents.
- Javadoc states thread-safety and thrown exceptions, and `javadoc -Xdoclint:all` printed nothing.
- `ConcurrencyTest` passed 8 of 8 runs on each of JDK 21 and JDK 25.

## What was run

- `mvn -o clean verify` on each commit on JDK 21, and HEAD on JDK 21 and 25, all on exports in a scratch folder.
- 16 implementation mutants. Survivors: dropped finished list and sorted finished list (both now caught), lock leak on rejection, and split publish. An unlocked start check was caught in 3 of 6 runs, as expected for a race test.

## Declined to judge

- `AI.md` missing, excluded by the brief.
- `Match` public constructor, decision 54.
- `snapshot` non-final, assuming normal safe publication of the board.
- `Scoreboard` not sealed, approved API shape.
- Control characters in names, a neighbour of the listed future improvements.
- Only surefire pinned in the `pom.xml`, following the plan.
- The change rate at 24 matches is arguably low (about 0.036), an approved research figure.
- README length, approved scope.
