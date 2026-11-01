# AI usage

## How AI tools were used

AI was used for design, reasoning, code generation, testing and review. The human partner, Tjaša Hočevar, made every decision. The AI proposed options with trade-offs and recorded what was chosen.

The work moved through these stages:

1. **Behaviour.** Every operation was discussed one question at a time, including edge cases and test scenarios, before any design.
2. **Design.** Public API, exceptions, configuration and concurrency were decided next, backed by web research on load and on JDK behaviour.
3. **Spec.** All decisions went into a written spec, reviewed twice by independent agents.
4. **Plan.** The spec became a task-by-task implementation plan, also reviewed by an independent agent.
5. **Implementation.** The AI implemented the plan with test-driven development, one commit per task.
6. **Branch review.** A fresh agent reviewed the whole branch, ran mutants and found two test gaps, which were fixed.

**Tools and models:**

| Tool | Use |
|---|---|
| Claude Code desktop app | The main session, with Claude Opus 5.5 |
| Superpowers skills | The workflow: brainstorming, writing plans, executing plans, test-driven development |
| AskUserQuestion | Putting each decision to the human partner |
| Subagents | Independent reviews, with fresh context. Spec and plan reviews used the session model. The branch review used Claude Opus, after a first attempt on Claude Fable failed because usage credits were unavailable |
| WebSearch and WebFetch | Research on team names, match load, feed rates, locks and JEPs |

**What the human partner changed:** the AI's proposals were not simply accepted. Examples from the history below:

- Stopped a question that rested on an assumption, and required every assumption to be approved.
- Chose `MatchId` over team names for identity, after the AI had leaned towards names.
- Asked for research on name length, and set a much higher cap than proposed.
- Questioned read blocking, which changed the AI's lock recommendation, then raised CAS and chose between the two.
- Proposed removing the start counter in favour of an ordered map.
- Asked whether a `Team` type was needed, which led to `TeamName`.
- Requested each independent review, and caught compile and wording errors in the plan and spec.

The AI also made mistakes, which were corrected along the way. It overstated the case against `synchronized` until JEP 444 was read, and it declared the maps as `Map` in the plan, which would not have compiled.

## Artifacts

These guided the implementation:

- [Design spec](docs/superpowers/specs/2026-10-31-scoreboard-library-design.md)
- [Implementation plan](docs/superpowers/plans/2026-11-01-scoreboard-library.md)
- [Spec review 1](docs/superpowers/reviews/2026-10-31-spec-review-1.md)
- [Spec review 2](docs/superpowers/reviews/2026-10-31-spec-review-2.md)
- [Plan review](docs/superpowers/reviews/2026-11-01-plan-review.md)
- [Whole-branch review](docs/superpowers/reviews/2026-11-01-branch-review.md)

The assignment PDF, *ODDS and Data - JAVA Coding Task*, was the starting point. It is not included here.

## Prompt history

All of this happened in one session. User prompts are verbatim, and model output is summarised. Answers given through the question tool are marked **Answer**. Where the partner picked an offered option without typing, it is marked **Selected**.

### 1. Starting the brainstorm

**Prompt:**

> @"/Users/tasha/Downloads/ODDS and Data - JAVA Coding Task.pdf"
> /superpowers:brainstorming  Lets discuss the design and implementation of a simple Java library. Ask me as many questions as needed so we achieve a common understanding how a library should look and behave. The requirements are in the attached PDF file. Read them first. First lets thoroughly go through each operation that this library needs to support and how each operation should behave. Lets cover all possible edge cases and test scenarios. Do not disucss the design plan once we cover all behaviour of all operations and their test cases. After this we will discuss the library design, how public API should look like, what should be configurable by user, how to handle concurrency because library needs to be thread safe.

**Model:** Read the PDF, summarised the requirements and gaps, proposed an order of discussion, and asked its first question on team overlap in prose.

### 2. How to ask

**Prompt:**

> Use the question tool when asking me questions.

**Model:** Saved this as a preference and re-asked with the question tool.

### 3. Start a match

**Answer (team overlap):**

> Reject any team overlap. One team can play in exactly one match at once. Mexico vs Canada should block both Canada vs Mexico and Mexico vs Spain. After this match finishes both teams are released and free too play in another match. Teams are home and away.

**Answer (team names):**

> Yes I agree null, blank, and identical home/away names treat as invalid.
> Starting  ' mexico ' or 'MEXICO' counts as the same team when 'Mexico' is live. For team comparison trim surrounding whitespace and compares case-insensitively so all three are Mexico and the start is rejected. The summary shows the trimmed name as first given. The trimming and case-insensitive is used for team name comparison if a team is already playing.

**Answer (rejections):**

> Throws an exception and leaves the scoreboard exactly as it was. Failures can't be silently ignored, and the caller can tell bad input from a conflict with live state. Exact exception types come in the API discussion. Make error handling intuitive to the library user. The error should exactly tell what went wrong.

**Model:** Recorded each decision and moved on to the update operation.

### 4. Name length research

**Prompt:**

> Before proceeding with the update score operation. Lets discuss in we should limit the team input name length when passing home and away team string parameters. Make a research how long team names typically are.

**Model:** Researched name lengths and found 32 characters was the longest common country name, with club names up to about 90. Recommended a cap of 100.

**Answer:**

> Rejects names over MAX characters with a clear error. Fits every name found, including the longest clubs, and still catches garbage input. But lets make the maximum length even more generous at cap 1000 to be safe.

### 5. Update the score

**Answer (update shape):**

> Score updates should be absolute, this means call to update score has current score of both. Non-negative values only. The library should not prevent upper bounded scores because it is not this library's job to limit this. But it should make sure it does not get overflow exceptions if two scores are added together. Lowering a score is allowed, we want to support VAR corrections and mistakes. Repeater or re-sent score update to same score as currently is should be a detected no-op.

### 6. No assumptions

**Prompt:**

> Do not make any assumptions. Each assumption should be checked by me before approving. So lets discuss:  assuming matches are identified by team names rather than a match ID

**Model:** Saved the rule, listed its other unapproved assumptions, and put match identity to the partner.

**Answer:**

> Lets use Match ID from start. This will rule out reversed teams and team matching by names on future updates.

### 7. Clearing the assumptions

**Answer (initial score):**

> New match start with 0-0 always. Yes all decisions/assumptions we make go to the README. The README should contain assumptions, trade offs, decisions, research and sources, future improvements if we come up with any.

**Answer (start order):**

> Start call order or use of a dedicated Java structure that insures the order. Lets avoid timestamps to ensure deterministic ordering when two matches start at the same millisecond.

**Answer (same team):**

> Where is this check again needed in library? In update score operation? Further elaborate before I answer and check back again with me with this question.

**Answer (length error):**

> Length only, says exactly what is wrong.

**Answer (score range):**

> Int score, long total to prevent overflow. Each score is a non-negative int, up to about 2.1 billion. The total is computed as a long, so two maximum scores add correctly and sorting stays right.

**Model:** Explained the same-team check runs only in `startMatch`.

**Answer:**

> Yes same comparison in start. Start uses one definition of 'same team' for both checks. Spain vs ' SPAIN ' is rejected as a team playing itself. update and finish are unaffected because they use the match ID.

### 8. Non-live IDs and error order

**Answer (non-live ID):**

> Two distinct errors. Match not found for unknown, match not live for finished and retained. I am thinking the fifth operation (when we come to there do not ask me about it now) should be return a list of all finished matches. We will discuss limiting memory when we come to the fifth operation. Keep distinct error for now as decided. Again library should be intiutive to the user and return exact errors what went wrong.

**Answer (error order):**

> Checks the null ID and negative scores first, then whether the match is unknown or finished. The same bad input always gives the same error, whatever the match's state. Re-sending the final score of a finished match gives 'not live', not a no-op. Also we will need to discuss separately the design of exceptions but that will be part of API design. Again all decision go through me question by question do not make any assumptions yourself.

### 9. Finish and summary

**Answer (finish score):**

> Yes any score. 0-0 score is allowed as a draw result. It is a real result.

**Answer (null ID):**

> Same as update.  Null ID is invalid inut, checked before any lookup, with the same error as update. One rule across both operations.

**Answer (empty board):**

> Empty result. Returns an empty list. An idle scoreboard is a normal state, and callers can loop over the result without special handling.

**Answer (summary view):**

> This is part of the concurrency synchronization pattern we choose.  Skip for now until we discuss concurrency. So lets postpone this question to answer later. But yeah callers should get a fixed summary and can not alter the board through it. If each copy gets a small copy and sort will discuss later.

### 10. Fifth operation

**Answer (operation):**

> Yes finished list. Returns finished matches with their final scores. Uses data already retained for the 'not live' error, and gives a results view next to the live summary.

**Answer (retention):**

> Capped, oldest evicted. This is something it goes into trade offs and assumptions. Lets strictly bound memory to prevent out of memory errors. Lets make this configurable by the library so library users can pick their own upper limit with default set if not overriden.

**Answer (order):**

> Most recent finished first. Latest results appear at the top, the same style as the summary's 'most recent first'. Needs finish order tracked, and it's deterministic without timestamps.

**Answer (evicted ID):**

> When finished match is evicted we get match not found exception. If match is finished and still retained we get match not live. The library no longer holds any record of the match, so it gives the same error as an unknown ID. Memory stays strictly bounded. README notes that old IDs can't be told apart from unknown ones.

**Selected (live cap):** No live cap.

### 11. Name edge cases

**Answer (inner spaces):**

> Lets weigh this option before I decide. The requirements leave this up to me. This could also be something we put into future improvements section inside the README. How much additional code and tests would be needed to cover this. How much does this add to the code complexity?

**Answer (Unicode):**

> Again lets discuss how much does this add to the simple library complexity and if it would just be better to pust in a recommended future improvements section. This is also up to me to decide and is not part of initial set of requirements.

**Answer (cap counting):**

> Uses String.length() after trimming.

**Model:** Compared the code and test cost of each rule, and recommended deferring them.

**Answer:**

> Agreed. Trim surrounding whitespace and ignore case with Locale.ROOT. Inner spaces, Unicode normalisation and accent handling go to Future improvements, with the limit documented.

### 12. Audit before design

**Answer (empty finished list):**

> Empty list, same as the empty summary: no matches finished yet is a normal state. Adds one test to the list.

**Answer (test review):**

> Behavior and test are agreed and look good for each operation. Before we proceed: Have we covered all the requirements stated in the assignment? Have we discussed behavior, edge cases, ambiguities, and explicit exclusions. Have we resolved and remembered all the assumptions that we made for later documentation? Are there any questions that genuinely need clarification.

**Model:** Audited the conversation and found gaps: no start error order, an unresolved `strip()` vs `trim()` difference, and no exclusions. Saved all decisions to memory.

**Answer (start error order):**

> Input then live state. Order: null or blank, length, same team on both sides, then already live. Home is checked before away. Matches the update rule: the same bad input always gives the same error. Error handling needs to be clear and intuitive to the end library user.

**Answer (both teams live):**

> Reports each team and the match it's in, so the caller sees the whole problem in one error, in line with your exact-errors rule.

**Selected (whitespace):** `strip()`.

**Answer (exclusions):**

> Yes all stated above are skipped for purposes of this simple library. This should be thoroughly documented and added to assumptions and tradeoffs inside README.md. Adding a support to library to add a custom storage layer by library user via interface can be a future improvement. It is not explicitly stated in the requirements and the library is meant to be simple.,Persistence, restart recovery,Match events, phases,Shootouts, tournament rules,Cancelling a match

### 13. Public API

**Answer (API shape):**

> I am leaning towards interface plus factory with a public Scoreboard interface. The implementation stays private, so its concurrency approach can change without touching users. But without the builder pattern since a constructor contains only one configuration (max finished matches) for now. Builder can be added later if multiple parameters are introduced.

**Answer (ID type):**

> MatchId wrapping UUID. For type safety and clearer domain API modelling. A small typed record that callers treat as opaque. IDs can't be confused with scores, and an ID from another board gives match not found.

**Answer (match data):**

> Immutable Match record. One public record used by both lists. The caller formats it however they like, and the record can't alter the board. A readable toString can print 'Mexico 0 - Canada 5'.

**Answer (update return):**

> Void. The no-op is purely internal, and the caller can't see it.

**Answer (finish return):**

> Void. The caller gets the result from the finished list. Eviction in here happens on FIFO approach. So recently finished matches should always be present in history only the oldest one gets removed based on the finished cap size.

**Answer (exceptions):**

> Invalid input is IllegalArgumentException, which Java developers already expect. State failures get specific unchecked types under one base class, so callers can catch exactly what they need.

**Answer (null input):**

> IllegalArgumentException, all invalid input gets one exception type, with a message such as 'Home team name must not be null'. Matches our rule that null is invalid input.

**Answer (default cap):**

> 1000, holds nearly ten full tournaments at about 104 matches each, and memory stays well under a few megabytes with normal names.

**Answer (valid caps):**

> 1 or more. 0 or negative throws IllegalArgumentException. Guarantees also the rule that the newest finished match is always kept because currently the finishMatch operation does not return the currently finished match and the user of the library needs to call finished matches separately.

**Answer (name cap):**

> No a fixed constant. We already researched the expected length per team name and 1000 is generous enough for this library. All other name lengths are garbage input.

### 14. Concurrency

**Model:** Recommended a single private lock.

**Answer:**

> Lets discuss this in more depth. Ask me follow up questions to clear up all the assumption regarding what usage requirements does the library need. The outcome when we go through all the questions points to the right thread-safety synchronization approach. For example: what is the read/write ration based on that this is a scoreboard library for keeping scores of live football matches? What state is shared? What must change together? How long is the lock held? We also need to cover the constraints. So how many write will typically occur in a live football match? How many football matches can be live at the same time? How frequent are fetch scoreboard summary calls? Lets make a proper research and add follow up questions.

**Model:** Researched goals per match, simultaneous matches, feed rates and lock behaviour, analysed shared state, and asked about usage.

**Answer (read load):**

> A backend calls it a few times a second at most and serves viewers from its own cache or push layer. Assumes roughly 1-100 reads per second.

**Answer (write source):**

> Several threads, for example, one consumer thread per match or partition, so writes can arrive at the same moment, as in six simultaneous kick-offs. The library must handle real write contention, though volumes stay small.

**Answer (freshness):**

> Yes always. Read-your-writes across threads: a completed write is visible to any summary that starts afterwards.

**Answer (scale):**

> One tournament, 24 live max. One instance per tournament within one JVM, so at most 24 live matches and typically about 6. Matches the brief's World Cup scope. We can add a buffer to the cap to have some space left for example 100 matches. Lets not forget to document everything in assumptions and trade offs and research the research we made.

**Selected (buffer):** Sizing target only.

**Answer (lock choice):**

> Before we chose. Where does the sorting and copying snapshot happen. As to my knowledge ReentrantLock blocks consecutive reads. Think about concurrent writes, it should not be blocking because someone may use virtual threads on Java 21. You can make a proper additional research if needed.

**Answer (Java version):**

> Java 21 LTS to support greater compatibility. Builds fine on JDK 25.

**Model:** Compared where copying and sorting happen in each option, and changed its recommendation to a writer lock with a published snapshot.

**Answer:**

> Ok I am leaning towards Lock + published snapshot. But before we finalize and decide, what about the CAS approach?

**Model:** Compared CAS with immutable state, and said it might be better.

**Answer:**

> Lets go with Lock + published snapshot because no retries and no wasted work to keep it sound and safe. Also it offers a better balance of simplicity, predictability and maintainability for this workload. Document every alternative we discussed thoroughly. This goes under trade offs/assumptions/decisions in README.md.

**Selected:** default non-fair lock, `com.sportradar.scoreboard`, JUnit 5 and AssertJ, and `getFinishedMatches` only in the feature commit.

### 15. Writing the spec

**Answer (design check):**

> Yes write the full spec with every decision we agreed on. Documenting each operations behavior with test scenarios and covered edge cases, documenting library design public API, error handling, concurrency. Documenting a list of all decisions and assumptions agreed upon. Testing, Concurrency test plan. So everything we thoroughly discussed goes into the specs.

**Answer (two-list view):**

> No document the limit. GetSummary and getFinishedMatches stay separate calls, and the README notes that a finish can land between them.

**Answer (spec commit):**

> First leave uncommitted so I can first revise it if anything is missing. Commit once I say it is ready to commit and we proceed with the next step after specs.

**Model:** Wrote the spec, and flagged two errors of its own and four unapproved details.

**Answer (step order):**

> All null or blank checks run first, for home then away, so this gives the away null error. Written in the spec this way.

**Answer (both negative):**

> Name both scores. 'Scores must not be negative: home -1, away -2'. Shows the whole problem in one error, like the both-teams-live case. Written in the spec this way.

**Answer (null in ID):**

> Reject when constructing. MatchId's constructor throws IllegalArgumentException, with a small compact constructor. An invalid ID can't exist. The spec doesn't have this yet, so I'd add it and a test.

**Selected (messages):** Use the spec's wording.

### 16. First spec review

**Prompt:**

> Spawn a separate reviewer agent in a separate thread to review the spec if it finds any discrepancies before we start the implementation for a second opinion.

**Model:** Ran an independent review, which found no blockers but found test and wording defects.

**Answer (update order):**

> Assume in-order delivery for this simple library. Add to the future improvements section with mention how to approach it in the future (with update carrying a sequence number for stale ones to be rejected). This also goes into the assumptions section and tradeoffs for this library.

**Answer (commit split):**

> Feature adds list and rebuild. Core keeps the finished map, cap, eviction and 'not live' error. The feature commit adds the list to Snapshot, the rebuild, getFinishedMatches and tests 22 (part), 33-36, 43, 45 and create(1). No dead code in core.

**Answer (name gaps):**

> Document as limits. The approved rule stands. Section 10 and the README list case-folding and non-strip spaces such as NBSP as future improvements, naming the Türkiye example, and the 'stops feed noise' reason is narrowed.

**Selected (other fixes):** Apply all.

### 17. JEP 444

**Prompt:**

> Ok research JEP 444.

**Model:** Read JEP 444, confirmed `ReentrantLock` does not pin, and admitted its case against `synchronized` had been too strong.

### 18. Spec changes

**Prompt:**

> I have changes

**Prompt:**

> Regarding the section 5 in spec. The storage of live matches, live teams, finished matches, start counter and snapshot. I am thinking of using appropriate java structure to keep the order of inserted started matches like for finished matches without a separate start counter. Probably LiveMatch separate record is not needed then. Lets discuss which approach is better.

**Model:** Compared `LinkedHashMap` with the counter, and recommended `LinkedHashMap`.

**Answer:**

> Lets go with LinkedHashMap only. Live matches become LinkedHashMap<MatchId, Match>. The start counter and LiveMatch record are removed, and ties rely on insertion order plus a stable sort, explained in a comment. This is also kept thread safe since we have a surrounding ReentrantLock right?

**Prompt:**

> For Public API design should we also add a Team record for team name validation? What is the current design plan?

**Model:** Showed a public `Team` record conflicts with the approved error order, and proposed a package-private `TeamName`.

**Selected:** Package-private `TeamName`.

**Prompt:**

> Did we include everything in the spec written?

**Model:** Read the spec against the conversation and added what was missing, including a table of rejected alternatives.

**Answer (Match validation):**

> No validation. The library never accepts a Match from callers, so a hand-built one can't reach the board. The board only creates valid ones. No extra code or tests.

### 19. Second spec review

**Prompt:**

> Since we slightly changed the specs again spawn the review agent again to see if there are any new discrepancies. Make it review also based on requirements in PDF.

**Model:** Ran a second independent review, which found no blockers and proved one test gap with a mutant.

**Prompt:**

> Commit all in docs/. Saves both review reports to docs/superpowers/reviews/ next to the spec and plan, and AI.md links them. Prompt history covers every session, including implementation, plus context such as tools and models. The AI.md should include a summary how AI tools were used, Artifacts that guided the implementation where initial PDF is also just mentioned. THen prompt history where each my question and what the model outputed is shown. It can be slight summarized for readability. It needs to be shown how I guided the AI to implementation. Then in README.md we include how to build and test library, usage, core operation supported, log of assumptions, decisions, trade offs, design and reasoning behind it, research, separate header for additional feature docuemnted, tests, future improvements.

**Prompt:**

> Apply all

**Prompt (pasted review notes):**

> Fix the retention rationale in section 3.5. A minimum cap of one does not guarantee the caller can retrieve its finished match: another thread can finish a match and evict it before the caller reads history. Suggested wording:
> The minimum cap is one so history retains at least the most recently finished match. History is bounded; a particular result may be evicted before a caller retrieves it.
>
> Avoid claiming repetition guarantees both race outcomes in test 60. Repeated execution may always produce the same ordering. Use coordinated tests for start-before-finish and finish-before-start, plus a concurrent race test that accepts either valid outcome.
>
> Preserve original user prompts in AI.md or a linked transcript. "Each user question and what the model output, lightly summarised" could mean the prompts themselves get paraphrased. The PDF explicitly requests prompt history. Keep user prompts verbatim where available; summarising model responses is reasonable.

**Prompt:**

> Also briefly mention that new exceptions subclasses are implemented in an exception subpackage.

**Prompt:**

> Ready to commit

### 20. Plan

**Model:** Committed the spec and wrote the implementation plan. Git needed an email address.

**Prompt:**

> I have manually set the user email for this project git repository. Proceed.

**Selected (Javadoc):** Public API only.

**Answer (plan commit):**

> Yes commit once I approve it and before you proceed with the next step.

**Prompt:**

> Use name InMemoryScoreboard instead of DefaultScoreboard

**Prompt:**

> The map declarations would prevent compilation — Tasks 3, 5, and 8.
> The plan declares live and finished as Map<MatchId, Match>, then calls sequencedValues() and pollFirstEntry(). Those methods belong to SequencedMap, not Map, regardless of the concrete object assigned. Use LinkedHashMap<MatchId, Match> as the spec specifies, or SequencedMap backed by LinkedHashMap

**Prompt:**

> In README.md we should separately mention additional feature: recently finished matches

**Answer (plan approval):**

> No spawn a subagent with fresh context to review plan against the spec to find any discrepancies if there are any to be resolved before we implement.

**Model:** Ran an independent plan review, which found a hang risk, a contradiction between Tasks 7 and 8, and smaller issues. It also reported that it had run a broad `pkill`.

**Selected:** Apply all fixes, move `create(int)` to Task 5, save the review, and approve the plan for native execution.

### 21. Implementation

**Model:** Implemented the nine plan tasks with test-driven development, one commit per task. It watched each test fail first. Concurrency tests passed at once, so it confirmed them with mutants: removing the lock failed several tests, and leaking the lock on a no-op failed with a timeout rather than hanging the build.

**Model:** A fresh reviewer then checked the whole branch, ran 16 mutants, and found no production defects. It found two test gaps, which were fixed and proved against the same mutants, and six minor issues.

### 22. Finishing

**Selected (AI.md):** Complete, commit it.

**Selected (minor findings):** Fix all six.

**Model:** Fixed all six, and confirmed the test changes with mutants: a lock leaked on rejection, a split publish, and `trim()` in place of `strip()` are now all caught. The suite has 122 tests, passing on JDK 21 and JDK 25.
