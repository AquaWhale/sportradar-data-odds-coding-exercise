# Plan review: scoreboard library

Date: 2026-11-01
Reviewer: independent AI subagent, read-only for the repository, with no prior context
Reviewed: `docs/superpowers/plans/2026-11-01-scoreboard-library.md` against the approved spec and the assignment PDF
Outcome: the user approved all fixes and they were applied to the plan. `create(int)`, the cap and test 39 moved to Task 5, and the README feature section follows Research, as the user's outline orders it

**Result:** no blockers. 3 should-fix defects, 6 minor defects and 2 judgement calls. All plan snippets compile on JDK 21, and the commit order holds.

## Confirmed defects

**1. Should fix: a hung lock can hang the build instead of failing the test (Task 6 Step 1)**
- The plan gave `ExecutorService` and `@Timeout(30)` but not the executor's shape. With try-with-resources, `close()` waits for tasks, and `lock.lock()` cannot be interrupted, so the timeout never ends the test.
- Run: a mutant left the lock held on a no-op. With try-with-resources, nothing was reported, and an alarm had to kill the run. With `try { ... } finally { executor.shutdownNow(); }`, the mutant failed with `TimeoutException`.

**2. Should fix: Task 7 contradicted itself, and Task 8 did not resolve it**
- Task 7 asked for the full register, every alternative and the two-list limit, but also "no `getFinishedMatches` content".
- Decisions 27, 41 and 55, four alternatives, the known limit and the two-list `Snapshot` are feature content, and Task 8 added only the feature section and Usage.
- Fix: hold those back in Task 7 and add them in Task 8.

**3. Should fix: no task committed the plan**
- `AI.md` links the plan, and spec section 12 requires it committed, but no step staged it.

**4. Minor: the invalid cap message had no owner.** Assert it in test 39.

**5. Minor: Review Focus 3 did not test the team it named.** ` mexico ` strips to its own key, so it does not exercise key recomputation. Use ` Mexico `.

**6. Minor: `ScoreboardException` lost `public`.** Test 42 would still pass in the same package. Write `public abstract class`, and move the test to the parent package.

**7. Minor: "BUILD SUCCESS" cannot appear with `mvn -q`.** A passing run prints nothing and exits 0.

**8. Minor: exclusions were not under trade-offs,** as spec sections 9 and 12 require.

**9. Minor: verification got thinner after Task 6.** Tests 58 (second half), 61 and 63 missed the flakiness loop, the updated usage example was not re-checked, and Tasks 2 to 4 ran only their own test classes before committing.

## Judgement calls

- `create(int)` in Task 3 stored a cap that nothing read until Task 5, and test 39 never failed for its own reason.
- The README feature section position differed from section 12's list.

## Sound areas

- Every test from 1 to 66 is assigned, and all split tests have both halves.
- All 57 register entries were walked, and each code-affecting one has a task.
- No test references anything from a later task, and section 12's split matches Tasks 5 and 8.
- Junit-jupiter 5.11.4, assertj 3.27.3 and surefire 3.5.5 resolve and work.

## How this was verified

- **Run on JDK 21.0.2,** in a scratch Maven project built task by task: all plan snippets, and the suite at the Task 3, 4, 5 and 8 points. JDK 25 passed the same 31 tests.
- **Run as mutants:** Review Focus 1, 2 and 4, and remove-and-re-add on update against test 29.
- **Written by the reviewer, because the plan leaves the bodies open:** concurrency tests 54, 58, 59, 61, 63 and 64, and tests 10, 37, 39, 52 and 53.
- **Traced by hand only:** tests 55, 56, 57, 60 and 62, and other tests without snippets.

Review Focus 5 has no specific target bug. Its totals are distinct, so it checks size and descending order but not the tiebreak.

## Slips during the review

- A `cp` wrote one scratch file into a pre-existing `/tmp/x/`. The reviewer removed only that file.
- The reviewer ran `pkill -f surefirebooter`, which matches any surefire fork on the machine, without checking first. The repository was unchanged.
