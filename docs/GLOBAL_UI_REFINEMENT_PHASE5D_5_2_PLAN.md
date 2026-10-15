# Global UI Refinement Phase 5D.5.2 — Learn: audit and plan

Status: plan written 2026-10-14. On the owner's instruction ("Don't ask me, continue with what you recommend") the **recommended decisions in
§J are adopted** and implementation follows; the implementation report is `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_2.md`.
- Base: `main` at `bab5a46`. Phase 5D.5.1c is complete and committed (`9e88678`); the owner closed Daily Brief.
- Reference: the owner's illustrative "Learn UI/UX Reference Screens" image, stored as `docs/design/phase5d_5_2/REFERENCE-learn.webp`.
- Workflow: `docs/AI_REVIEW_WORKFLOW.md`.

## A. Current implementation inventory (verified in code; Learn code unchanged since `863d756`)
| Area | Android (Compose) | iOS (SwiftUI) | Shared / core |
|---|---|---|---|
| Learn tab root | `presentation/learn/LearnScreen.kt` (in-page "Learn" heading, no top bar), `LearnScene.kt`, `LearnRoute` | `LearnScreen` inside `GuidedResearchScenes.swift`, `LearnScene.swift`; title from the shared top bar ("Learn", inline) | — |
| Sections today | Intro "Research a company one simple question at a time. Every lesson is free." → Continue learning (if any) → Start learning card (5 steps + 4 quick picks) → Research a company (button) → Completed research → Practice card → **Financial terms (14 flat rows)** | same order | `BeginnerEducation` (14 `EducationEntry`: id, topic, title, short, why, interpret, limitations, example?, detailed?) |
| Topics | The `topic` field exists (Business, Growth, Profit, Financial health, Valuation, Income, Earnings, Portfolio) but **is not shown as categories** | same | catalogue order |
| Lessons / articles | **No articles.** The "lesson" is the 5-step Guided Research for a company (`GuidedResearchScreens.kt` / `GuidedResearchScreen`, with figures, charts, a quiz and Ask) and the term **explanation sheet** (`BeginnerExplanationSheet`: what / why / how to read / example / more detail / keep in mind) | same | `GuidedResearch.kt`, `LearningPresentation.kt` |
| Progress | Research progress per company (`LearningProgressRepository`; account-synced, ephemeral for guests) → Continue / Completed cards | same | no term progress |
| Search / discovery | None in Learn (the "Search companies" button opens the global stock search) | same | — |
| Concept of the day | Only in the Daily Brief (`BriefContent.concept`) | same | rotation in `BriefContent` |
| Contextual links | "Open Learn" / "Continue learning" from Home, the Brief, Earnings, Practice and Guided Research open the **tab root**; metric ⓘ buttons open the sheet in place | same | — |
| States | The landing page is local content (works offline); research needs the network inside Guided Research; a sync-failure note | same | — |
| Guest / signed in | Same content; progress is local for guests | same | — |
| StockSteps+ | Learn is free (Guided Research Ask has its own rules, unchanged) | same | — |

**Correction to the 5D.5 audit:** LN-01 ("iOS Learn has no title") was wrong. iOS shows the shared inline top-bar title "Learn"
(`docs/design/ubuntu_mobile/ios-11-learn.jpg`). The real finding is a parity difference: Android has an in-page large title, iOS a small bar title.

## B. UX findings (current state; captures `docs/design/ubuntu_mobile/android-18-learn.jpg`, `ios-11-learn.jpg`)
| ID | P | Finding |
|---|---|---|
| L-01 | **P0** | Where to start is ambiguous. The first card is "Understand a stock in five steps" with four company links that all look like the primary action, and there is no single "Start" button. |
| L-02 | **P0** | The only general learning content (14 terms) is the last section, a flat list below Practice. A beginner can't find "What is a P/E ratio?" without scrolling the whole page. |
| L-03 | P1 | No topic grouping on screen, although every term has a topic. |
| L-04 | P1 | No way to find a term (no filter or search inside Learn). |
| L-05 | P1 | Term content opens only as a bottom sheet with six equal-weight sections. There is no reading page, no outline and no "next" link. |
| L-06 | P1 | The intro copy ("Every lesson is free") promises lessons that don't exist as such. |
| L-07 | P1 | Parity: Android has an in-page large title, iOS a bar title; iOS uses 19 system fonts and `.buttonStyle(.bordered)` in `GuidedResearchScenes.swift`; the iOS step count is hard-coded `5`. |
| L-08 | P1 | Android term rows have two tap targets for one action (the row and the ⓘ button). |
| L-09 | P2 | Bordered cards everywhere (Start card, Practice card, journey cards); the "Research a company" section is a heading, a sentence and a full-width outlined button. |
| L-10 | P2 | No "concept" entry point in Learn, although the Brief has one. |

## C. Proposed hierarchy (follows the reference, using only existing content)
```
LEARN HOME (before)                          LEARN HOME (after)
Learn                                        Learn                                 🔍  (in-page on both platforms)
Research a company one simple question…      Build your investing knowledge with simple explanations
[Continue learning cards]                    and real-world examples.
Start learning                               ┌ START HERE (learn tint, illustration) ┐
┌ Understand a stock in five steps ─┐        │ Understand a stock in five steps       │
│ Research Apple / Coca-Cola / …    │        │ What it does, whether it's growing, …  │
└───────────────────────────────────┘        │ [ Start learning ] / [ Continue … ]    │
Research a company  [Search companies]       └────────────────────────────────────────┘
Completed research                           Explore topics                       See all
Practice what you learn [card]               [Business][Growth]                (2 columns; 1 at large text)
Financial terms                              [Profit][Financial health]        icon tile, topic, "N terms"
  What is revenue? (Business) ⓘ             Today's concept
  … 14 rows                                   💡 What is the P/E ratio?  How much investors pay…  ›
                                             Continue learning (only with saved progress)
                                             More ways to learn
                                               Research any company ›  Practice with virtual money ›
                                             Completed research (only when there is any)

TOPICS ("See all" / 🔍)                       TOPIC (a tile)              TERM PAGE ("lesson")
← Learn                                       ← Learn                     ← Learn
Explore topics                                Valuation                   [illustration band — hidden at large text]
Browse all investing topics and find …        4 terms                     VALUATION
[ Search terms… ]                             What is EPS?            ›   What is the P/E ratio?
Business       Revenue                     ›  What is the P/E ratio?  ›   How much investors pay for each dollar…
Growth         Revenue growth              ›  …                           ┌ In this lesson ───────────┐
Profit         Net income, profit margin…  ›                              │ • Why it matters           │
…              (topic name + term names)                                  │ • How to read it • Example │
(search: matching terms; empty message)                                   └────────────────────────────┘
                                                                          Why it matters / How to read it /
                                                                          Example / More detail / Keep in mind
                                                                          Next: What is market capitalization? ›
                                                                          Research a company ›
```

## D. Component reuse
| Need | Reuse | New? |
|---|---|---|
| Start card | `StockCard(bordered = false, containerColor = learn tint)`, the existing `ill_learn_basics` / `LearnBasics` illustration, `StockButton` / `.stockPrimary` | No |
| Topic tiles | `StockIconTile`, `StockCard(bordered = false)` surface, `StockCardGridLayout` (2→1 columns, word-safe) | A small private tile composition per platform |
| Rows | `StockNavigationRow` (topics, terms, more ways, next) | No |
| Search field | Compose `StockTextField`; iOS `TextField` with the theme font | No |
| Section headers | `StockSectionHeader` (with "See all") | No |
| Term page | Plain text sections, a `StockCard` learn-tint outline box | One new screen per platform |
| Copy and rules | New core `learning/LearnDisplay.kt` (topics, counts, term names, filter, outline, next, today's concept) + tests | Yes (core, display only) |

## E. Files
- **core:** new `learning/LearnDisplay.kt` + `commonTest/.../learning/LearnDisplayTest.kt`.
- **shared:**
  - `presentation/learn/LearnScreen.kt` (landing rewrite);
  - new `LearnTopicsScreen.kt`, `LearnTermScreen.kt`;
  - `LearnRoute.kt` (+ `LearnTopicsRoute(topic?)`, `LearnTermRoute(id)`);
  - `LearnScene.kt`;
  - `presentation/AppNavigation.kt` (two destinations; Learn tab selected for them; back button; title "Learn").
- **iOS:**
  - `GuidedResearchScenes.swift` (`LearnScreen` landing);
  - new `LearnTopicViews.swift` (topics, term page);
  - `LearnScene.swift`;
  - `AppScene.swift` (Learn destinations; Learn tab uses its own in-page header like Home);
  - `IosLearningClient.kt` (expose `LearnDisplay` where needed).
- **Unchanged:** `BeginnerEducation` text, Guided Research, progress, the server, entitlements, the global search, Brief, Home and other tabs.

## F. Accessibility and responsive behaviour
- **Topic grid:** 2 columns while every topic word fits (`StockCardGridLayout`), otherwise and at accessibility sizes 1 column.
- **Term page:** the illustration band is hidden at large text (Android ≥ 1.3×, iOS accessibility sizes); nothing is truncated.
- **Headings and taps:** headings are marked; rows have 48 dp/pt targets and one tap target per row (fixes L-08).
- **Live type changes:** on iOS, `dynamicTypeSize` is read in the body.
- **Search:** the field is labelled; the result count is spoken only on change.

## G. Scope
- **Polish of existing features:** landing, research cards, Practice and search entry points.
- **Small, no-backend additions (adopted):**
  - topics grouping;
  - Topics and Topic screens;
  - a term reading page built from the existing sections;
  - local term filter;
  - "Today's concept" from the existing rotation.
- **Deferred (needs separate approval):**
  - new lessons and articles (the reference's "What is a stock?", ETFs, Personal finance, Risk);
  - levels;
  - term progress, bookmarks and share;
  - per-term illustrations and diagrams;
  - deep links from other screens to a term (D-8);
  - quizzes beyond Guided Research.

## H. Risks
| Area | Risk | Mitigation |
|---|---|---|
| Navigation | New destinations break back or tab selection | The Learn tab stays selected on Topics and Term; back returns along the stack; tested on device |
| Progress | Start button picks the wrong company | Resume the in-progress journey; else the first quick pick not started; else search. Progress logic unchanged |
| Content accuracy | Derived term names or summaries misstate the catalogue | Names only strip "What is/are", "the/a/an" and "?" from existing titles (tests); no new educational text |
| Today's concept | Differs from the Brief's concept on earnings or closed-market days | Same rotation as `BriefContent` for ordinary days; labelled "Today's concept", not "Concept of the day" |
| Offline | Landing must keep working | The catalogue is local; only research needs the network (unchanged) |
| Auth / guest | Practice requires an account | Same `onPractice` handling |

## I. Phases
| Phase | Scope |
|---|---|
| 5D.5.2A | `LearnDisplay` + tests; landing hierarchy (Start here, topics grid, today's concept, more ways) on both platforms |
| 5D.5.2B | Topics / Topic screens and the term reading page; navigation on both platforms |
| 5D.5.2C | Local term filter (in Topics); contextual deep links deferred (D-8) |
| 5D.5.2D | Suites, builds, device matrix (local MOCK), report and review package |

## J. Owner decisions (adopted as recommended on 2026-10-14)
| ID | Decision | Adopted | Why |
|---|---|---|---|
| D-L1 | Landing order as in §C | Yes | One clear start; topics before secondary tools |
| D-L2 | "Start learning" → resume, else first quick pick not started | Yes | One action; no invented progress |
| D-L3 | Topics from the existing `topic` field; no new topics | Yes | No new content |
| D-L4 | Term reading page replaces the sheet **inside Learn**; the sheet stays everywhere else | Yes | Reading space where people browse; in-context help stays quick |
| D-L5 | Illustration: reuse `ill_learn_basics` / `LearnBasics` on the Start card and the term page band; hidden at large text | Yes | Closest to the reference without new assets |
| D-L6 | Local term filter in Topics (title, topic, short text) | Yes | Answers "what does this term mean?" without backend work |
| D-L7 | Today's concept from the `BriefContent` rotation | Yes | Same source as the Brief; no new content |
| D-L8 | In-page "Learn" title + search icon on iOS too (top bar hidden on the Learn root, as on Home) | Yes | Matches the reference and Android |
| D-8 | Deep links from other screens to a term | Deferred | A navigation change outside Learn |
