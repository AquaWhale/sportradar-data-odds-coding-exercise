# Spec review 1: scoreboard library design

Date: 2026-10-31
Reviewer: independent AI subagent, read-only, with no prior context
Reviewed: `docs/superpowers/specs/2026-10-31-scoreboard-library-design.md`, the assignment PDF and the approved decisions
Outcome: the user decided on each finding, and the agreed fixes were applied to the spec

Line numbers and test numbers refer to the spec as it stood at this review. Tests were later renumbered.

**Result:** no blockers. The design behaves as specified. The defects are in test specifications that cannot hold or cannot detect what they claim, two overclaims, and one unsettled commit-boundary question.

The reviewer ran the JDK string behaviour and the arithmetic on JDK 21.0.5, with string behaviour also on 25. Concurrency was traced by hand, and nothing was built.

## Confirmed defects

### Should fix

**1. Test 41 can fail on a correct implementation (line 361)**
- It asserts "the finished score is one that was actually sent".
- Scenario: `finishMatch` wins the lock before any update. The final score is 0-0, which nobody sent, so the test is flaky.
- Fix: send one score before the race starts, or allow the initial 0-0. Stronger: use a single updater thread and assert the final score equals its last successful update.
- If rounds reuse one board with a small cap, an update can also get `MatchNotFoundException` after eviction. Use a fresh board or a large cap. Test 40 has the same caveat.

**2. Test 11 cannot detect a leaked team reservation (line 305)**
- Live-team state is not visible in `getSummary`.
- Scenario: Mexico vs Canada is live. Spain vs Mexico is rejected, but an implementation that reserves home first leaves Spain reserved. The summary looks identical, so the test passes.
- Fix: follow the rejected start with a start using the free team, Spain vs Brazil, and assert it succeeds.

**3. Test 44 cannot prove what it claims (line 364)**
- The test needs a hand-off to "another thread" (latch, join or atomic). That hand-off itself creates happens-before, so the test would still pass if `volatile` were removed.
- It is achievable and not flaky. It catches a late publish, but not a missing `volatile`.
- Fix: state that it is a behavioural check and that the guarantee rests on the `volatile` publish under the lock.

**4. "Repeated messages are harmless" is overclaimed (register 15 line 386, register 19 line 390)**
- This holds only for an immediate duplicate of the current score.
- Scenario: 1-0, then 2-0, then an at-least-once redelivery of 1-0. The board silently regresses to 1-0. Lowering is allowed, so the library cannot tell this from a VAR correction.
- Two threads updating one match has the same effect: the last lock holder wins, not the latest score.
- No version number is needed. Add a stated assumption that callers deliver updates for a match in order, which fits "one consumer per match", and narrow both register reasons. This needs the user's sign-off.

**5. The feature-commit boundary is not resolved (line 490, register 40 line 411; compare lines 222 and 229)**
- The core commits keep retention and eviction, but `Snapshot.finished` and "finish rebuilds both" have no reader until `getFinishedMatches` exists.
- Either the core commits contain dead code, or the feature commit changes the write path and is not "only" `getFinishedMatches`.
- Tests 22 (second half), 33-36, 43, 45 and `create(1)` read that list, so they must land in the feature commit. Tests 19 and 25 can stay in core because they use IDs only.
- Fix: state that the core keeps the finished map and the feature commit adds the list to `Snapshot` and the rebuild step. Assign each test to a commit.

**6. The two-list guarantee is too broad (line 287)**
- "A match appears in at least one list" is false for a match started after `getSummary` returns, which is in neither list.
- Fix: say "any match started before the `getSummary` call". Test 43 must use only pre-started matches and must not start matches during the run.

**7. Stated behaviour with no test (section 7)**
- `getFinishedMatches` is immutable and fixed. Tests 31-32 cover only the summary.
- A rejected finish leaves the board unchanged. Tests 11 and 21 cover only start and update.
- `strip()` rather than `trim()`. An ASCII-only test passes with `trim()`, so use U+2003 or U+3000.
- `Locale.ROOT`. Add a test that sets the default locale to Turkish and restores it, using `INDIA` vs `india`.
- Start error order: two identical 1001-character names give the length error, Mexico vs Mexico with Mexico live gives the same-team error, and both names null gives the home error.
- Null ID together with a negative score gives the null ID error.
- An ID from another board gives match not found.
- `Match.toString` format.
- Each state exception can be caught as `ScoreboardException`.
- Agreed wording. No test pins the messages in section 4.
- A start racing a finish on the same team. This is the one atomicity path with no direct test.

**8. Name comparison limits are undocumented, and "stops feed noise" is overstated (lines 96, 102, 376, 429-430)**

All confirmed on JDK 21:
- `TÜRKİYE` and `Türkiye` get different keys, so a real team can be live twice under two spellings.
- `STRASSE` and `Straße` also differ.
- U+00A0 is neither blank nor stripped, so a name of only a non-breaking space is accepted, and `Spain` followed by NBSP is a different team. U+200B and U+FEFF behave the same way.

Fix: list case-folding and non-`strip()` spaces in section 10 and the README, and narrow the reason in register 5. The approved rule stands.

**9. "A very large custom cap slows `finish` only" is wrong (line 271)**
- The O(cap) copy runs under the single writer lock, so every start and update waits too.
- Reads stay unaffected. Fix: "slows all writers during a finish, not readers".

**10. Test 46 does not run on Java 21 by default (lines 366, 409, 485)**
- `mvn` on this machine runs on JDK 25, so with `release=21` the tests run on 25, where JEP 491 applies.
- JDK 21.0.5 is installed, so run the suite on JDK 21 at least once and specify `maven.compiler.release=21`.
- Test 46 also passes with `synchronized`, so it cannot show parking versus pinning. It needs a timeout to detect a hang.

### Minor

**11. `<id>` in messages is undefined (lines 208-209).** The record default prints `MatchId[value=...]`.

**12. Team name in the already-live error is undefined (line 202).** For ` mexico ` vs Spain it could show the input or the stored name. The home-first order, the reversed case and the same-team error's name are also unspecified.

**13. Write flow omits the release path (lines 224-233).** Say `try/finally`, and state that the UUID is generated outside the lock.

**14. Test 42 tiebreak (line 362).** `Match` exposes no sequence, so "correctly sorted" is verifiable only for matches started sequentially before the threads run.

**15. Load figures need their conditions stated (lines 251-252).** "Under 0.01 per second" holds for 6 live matches but not 24 (0.017-0.027). 0.6 no-ops per second is 6 matches at 10 seconds, and 2.4 at 24. Neither threatens the design.

**16. JEP 444 is still an open action (line 463).** It is missing from the sources, and the pinning wording could be more measured.

## Judgement calls

- Both negative scores are reported together, but both null names report home only. This is defensible, and the README should give the reason.
- "About ten tournaments" sits oddly beside "one instance per tournament". Say the cap protects against a long-lived instance.
- `create(int)` landing in core could be questioned as a second operation. Be ready to explain that it is configuration.

## Sound areas

- All assignment requirements and deliverables are covered.
- The brief's example gives the expected order by hand.
- Each write is atomic under the single lock, and validation outside the lock uses only immutable inputs.
- Read-your-writes holds, and a finish moves a match between lists in one snapshot.
- Eviction at cap 1 keeps the newest match.
- The `long` total is correct, with a maximum of 4294967294.
- `strip()` and `isBlank()` agree on every code point.
- The JEP 491 and `ReentrantLock` claims, and the arithmetic, are correct.

## Verdict

Ready for an implementation plan once findings 1-6 and the assumptions list are resolved. All are spec text edits, and none requires a design change.
