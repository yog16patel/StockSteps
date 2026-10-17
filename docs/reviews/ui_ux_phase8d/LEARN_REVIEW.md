# Phase 8D — Learn review

## Architecture (inspected, unchanged)

| Part | Source |
|---|---|
| Copy, rules, topic icons and tones, search tiers | core `learning/LearnDisplay` (5D.5.2) |
| Topics and terms ("lessons") | the existing beginner catalogue (`BeginnerEducation`). 8 topics: Business, Growth, Profit, Financial health, Valuation, Income, Earnings, Portfolio |
| Lesson (term) page | title, one-sentence lede, "In this lesson" outline, Why it matters / How to read it / Example / Keep in mind, then "Next: …", "Explore all topics", "Research any company" |
| Guided research ("Understand a stock in five steps") | `GuidedResearch`. Five questions per company; progress is saved research progress (`LearningProgressRepository`): local, synced when signed in, "sync failed" note |
| Quizzes | Practice challenges (Practice tab). Learn itself has no quiz |
| Reading time / difficulty | not in the catalogue, so not shown (never invented) |
| Guest vs signed in | Learn works for guests; progress sync needs an account |
| Disclaimers | research footer: "…This guide explains them; it isn't a recommendation." |

## Questions the landing page answers

| Question | Where | Result |
|---|---|---|
| What can I learn? | "Explore topics" (4 featured + See all) | PASS |
| Where should I start? | "Start here · Understand a stock in five steps" + Start learning | PASS |
| What have I completed? | "Continue" / "Completed" sections, shown only with real progress | PASS (none on this account) |
| What next? | Start learning resumes the latest research, else the first quick pick not started; the lesson page's "Next: …" | PASS |

## 8D changes

| # | Change | Platforms |
|---|---|---|
| 1 | At very large text (iOS accessibility sizes; Android ≥ 1.75×, Learn's existing single-column threshold) the general intro sentence ("Build your investing knowledge…") is left out, so "Start here" and Start learning stay on the first screen. Before, iOS AX5 showed only the intro | both |
| 2 | Guided-research step descriptions wrap in full (they were cut at 2 lines at 2.0× / AX, e.g. "…before looking at the stock p…", and the description isn't in the spoken label) | both |

## Content review

**Content changed:** none. The lessons read as accurate beginner explanations:
- P/E: "A $50 share with $2.50 of earnings per share has a P/E of 20";
- market capitalization: "the total market value of a company's shares".

**Content issues found:** none, so nothing is escalated.

## Not changed (reasons)

- **Category hierarchy and density:** the topic tiles already follow `StockCardGridLayout` (two columns, then compact rows at large
  text).
- **Lesson pages:** the structure is already clear, and their illustration already hides at large text.
- **Progress, quizzes and personalization:** none to add without a new persistence decision.
