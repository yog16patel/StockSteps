# Phase 8D — Dynamic Type and font scale

## iOS (iPhone 16, guest, MOCK)

| Screen | Default | AX3 | AX5 BEFORE | AX5 AFTER |
|---|---|---|---|---|
| Discover | PASS | AFTER PASS: search and sectors on the first screen | search below the fold (title, purpose and 6-line scope) | PASS: title, purpose, "160 companies · not the whole market", "About this list", search and sectors visible; the disclosure opens with the full text |
| Practice (guest) | PASS | AFTER PASS | Sign in below the fold | PASS: Portfolio bar, nested tabs, "Practice Portfolio" (section heading role), SIMULATED, tagline and "Sign in to use Practice" visible |
| Learn | PASS | — | intro sentence filled the first screen | PASS: "Start here · Understand a stock in five steps", What's covered and Start learning visible |

## Android (emulator-5554, 412 dp)

**Sweep at 1.5×** (8C build): no clipping, overlap, unreadable truncation or unreachable action on any screen checked.

| Screen | What was checked |
|---|---|
| Markets | movers, sectors |
| Discover | landing, filters, results |
| Search | results |
| Company Details | metrics |
| My Portfolio | summary (missing FX) |
| Practice | tabs scroll |
| Watchlist | rows |
| Learn | cards, research |
| Bottom navigation | labels fit |

**At 2.0×:**
- **Learn:** the research step descriptions were cut at 2 lines; now they wrap.
- **Learn:** the intro is left out from 1.75×.
- **Practice:** the heading uses the section-heading role inside the Portfolio landing from 1.3×.
- **Discover:** the short scope label and "About this list".

Frames: `captures/{before,after}/android/*fs2.0*`, `captures/sweep-1.5/`.

No fixed heights were added. Every change either leaves text out (repeated or decorative intro only), moves it behind an existing
disclosure, or lets it wrap.
