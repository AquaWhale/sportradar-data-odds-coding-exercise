# Spec review 2: scoreboard library design

Date: 2026-10-31
Reviewer: independent AI subagent, read-only, with no prior context
Reviewed: `docs/superpowers/specs/2026-10-31-scoreboard-library-design.md`, line by line against the assignment PDF, after the changes that followed review 1
Outcome: the user approved all fixes, and they were applied to the spec. Decisions 55 and 56 were added, and the 19-24 range was corrected to 19-22

Line numbers refer to the spec as it stood at this review.

**Result:** no blockers. There is one definite commit-split error and two test gaps, one proved by running a mutant. The rest are small text fixes.

## Confirmed defects

**1. Should fix: section 12 and test 58. Test 58 cannot run at the commit it is assigned to.**
- Test 58 asserts "the finished score equals the last successful update". The only way to read a finished match's score is `getFinishedMatches`.
- Test 58 is not in section 12's feature list, so it falls into core, where that method does not exist.
- Fix: add 58 to the feature list, or split it as test 22 is split: outcomes in core, final score in feature.

**2. Should fix: tests 21, 49 and 23 against section 12. "Unchanged" can only be half checked in core, and nothing later completes it.**
- Scenario: a bug makes `updateScore` on a retained finished ID write the new score into the finished map before throwing. Test 18 still sees `MatchNotLiveException` and test 21 still sees an unchanged summary, so the corrupted final score passes everything.
- Test 49 has the same gap: a rejected second finish could disturb the finished order or evict an entry.
- Test 23 says finishing at 0-0 "works". In core that can only mean the match left the summary.
- Fix: add second halves for 21, 49 and 23 to the feature list.

**3. Should fix: tests 28-29 and line 232. "Tests 28-29 catch" remove-and-re-add holds only if the older match is updated last.** Verified by running a mutant on JDK 21.
- With A started before B and both reaching 1-0, the correct order is B, A.
- Mutant, updating A then B: gives B, A, which is correct, so the bug is hidden.
- Mutant, updating B then A: gives A, B, so the bug is caught.
- Fix: test 28 or 29 should state "the newer match is updated first, then the older one".

**4. Should fix: section 12. AI.md scope is narrower than the PDF.**
- "Prompt history exported from this session" covers only this session, and the PDF names code generation as AI use.
- The PDF's "other contextual information" is not in the deliverable line.
- The spec and reviews are named as artifacts, but no location is given.
- Fix: cover all sessions including implementation, add context, and name committed paths.

**5. Minor: section 12. `.gitignore` covers `.idea/` but not `target/`.** Add `target/`.

**6. Minor: section 12. "Every alternative in sections 6 and 8" misses two alternatives.** The start counter with `LiveMatch` and the public `Team` record are rejected in section 5 only.

**7. Minor: section 4. The name inside `Team '...'` is undefined when spelling differs.** With `Mexico` live, ` mexico ` vs Spain could print the caller's spelling or the stored one. Test 48 needs one answer.

**8. Minor: section 10. "The first five each change one method" is wrong for the fifth item.** Handling non-breaking and invisible characters affects the blank check, the length check and `TeamName.of`.

## Stated behaviour with weak or no test (minor)

- **No-op publishes nothing:** test 16 could assert the same list instance before and after.
- **Lock release on no-op:** a single-thread test cannot see a leak, because `ReentrantLock` is reentrant. Include no-op sends in test 59's mixed writes.
- **Late update cannot reach a rematch:** no test updates an old ID after a rematch. It fits into test 30.
- **Reversed pairing error names both teams:** add a message row for this case.
- **Test 37:** it must work by IDs in core, by finishing 1001 matches and checking the first ID gives not found and the second gives not live. Say so, or move it to the feature commit.

## Judgement calls

- `create(int)` counts as configuration, not a second extra operation. This is the interpretation most likely to be challenged, so give it a register entry. The `Match.toString` format is also not in the register.
- Test 60 is not flaky, but a single run may exercise only one interleaving. Repeat it in a loop.

## Sound areas

**Verified by running code, on JDK 21.0.5 and 21.0.2-graalce with `--release 21`:**
- `new ArrayList<>(map.sequencedValues().reversed())` followed by `sort(Comparator.comparingLong(Match::totalScore).reversed())` compiles.
- The PDF example gives Uruguay, Spain, Mexico, Argentina, Germany, with updates applied forwards or backwards.
- `put` on existing keys keeps position, and `.reversed()` on the comparator keeps ties stable.
- `Integer.MAX_VALUE` twice gives 4294967294 and sorts first.
- Stripping is idempotent, and the key recomputed from the display name matches the original.
- U+2003 and U+3000 are stripped where `trim()` leaves them. A non-breaking space is kept and is not blank.
- `TÜRKİYE`/`Türkiye` and `STRASSE`/`Straße` get different keys. `INDIA`/`india` get one key under a Turkish default locale.
- FIFO `pollFirstEntry` plus a newest-first read is correct.
- `--release 17` fails on `sequencedValues`, so the Java 17 reason is accurate.

**Fetched and read through a summariser:** the JEP 444 and JEP 491 claims hold.

**Traced by hand:**
- All PDF requirements and deliverables are met or planned, apart from finding 4.
- There are no stale references. Tests 1-66 and decisions 1-54 are continuous, and section references are correct.
- `TeamName` order satisfies section 3.1 and tests 43-46.
- Error orders in sections 3.2 and 3.3 match tests 20 and 47.
- The two-list guarantee and test 61 are correct.
- No rejected alternative has an inaccurate reason.

**Not verified:** the non-JEP web sources were not opened, World Cup figures were checked from memory, and club-name lengths were not checked. The 24 upper bound in the 19-24 range has no listed example.

## Verdict

Ready for an implementation plan once findings 1-4 are fixed. All are small text edits to sections 7 and 12.
