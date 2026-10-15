# Review package: Phase 5D.5.4A Company Details hierarchy

## 1. Phase and Git baseline
- **Phase:** 5D.5.4A (header, chart, overview hierarchy).
- **Base:** `73a6c23`.
- **Under review:** the local commit "Refine Company Details for Global UI Refinement Phase 5D.5.4A with a borderless chart and day range, one action row, a pinned in-page section bar, a What it does row, a beginner-first section order and borderless sections on Android and iOS" (not pushed).

## 2. Changes
- **Header:** no tags.
- **Chart:** borderless, with Open/Volume as a stacking grid and the Day Range bar; High/Low hidden only while the day range shows.
- **Actions:** one row, Add to portfolio + Compare. Practice Buy (and Earnings when there's no Earnings section) moved to "Research further".
- **Section bar:** Overview · Financials · News · About, pinned. It scrolls within the page, and the selection follows the scroll.
- **"What Apple does":** the first sentence of the existing description plus "Sector · Industry"; tapping it scrolls to About.
- **New order:** Why Did It Move? after Understand; About is its own section.
- **Borders:** every Company Details section is borderless at its call site.
- **Owner fixes:**
  - iOS Understand row alignment.
  - Long Financial Health badges now share the title's line (shared rule 14 → 20 characters; layouts still stack when they don't fit).

## 3. Files
- **shared:** `CompanyDetailsPresentation.kt`, `CompanyDetailsScreen.kt`, `StockChip.kt` (`StockSectionBar`), `StockSemanticStyles.kt`
  (badge limit); tests `CompanyDetailsPresentationTest`, `ResponsiveLayoutRulesTest`.
- **iOS:** `CompanyDetailsScreen.swift`, `DesignSystem/StockDetailComponents.swift`.

## 4. Decisions
- **Section logic** is shared (`sections`, `selectedSection`) so both platforms select the same way.
- **Display only:** no calculation, data or route changes.
- **iOS** uses a plain VStack with an overlay bar so every anchor exists for `scrollTo`.

## 5. Tests
- **PASS:**
  - core 541/0 (JVM and iOS);
  - shared Android 250/0 plus the reruns after the badge change;
  - shared iOS 246/0 (also on final code, rerun after freeing disk space);
  - server 501/0;
  - `assembleDebug`;
  - `xcodebuild`.

## 6. Android
emulator-5554 at 360 dp, 1.0× and 2.0×, light and dark (in-app theme):
- **PASS:** first viewport, pinned bar, tab taps, selection, What → About, Back restores position, badge on the title line.
- Sheet: `CHATGPT_UI_REVIEW_ANDROID.png`.

## 7. iOS
iPhone 16 at Default and AX5, light and dark, guest:
- **PASS:** first viewport, tab taps (inline and pinned), selection, row alignment, badge, AX5 width, stats and title/badge wrapping.
- Sheet: `CHATGPT_UI_REVIEW_IOS.png`.

## 8. Known issues
- Disk: Xcode DerivedData was deleted with owner approval (about 6.9 GB free); the next iOS build is a full rebuild.
- iOS live Dynamic Type refresh (existing limit).
- P/E is still repeated (5D.5.4B).
- The MOCK Market Cap fixture is wrong (data issue).

## 9. Unverified
TalkBack NOT RUN; VoiceOver BLOCKED; iOS signed-in BLOCKED; Android 1.3× and iOS AX XL NOT RUN.

## 10. Deviations
- The badge rule changed (owner).
- The iOS VStack and overlay (LazyVStack couldn't scroll to sections it hadn't created).

## 11. Owner decisions
- Approve 5D.5.4A.
- Proceed to 5D.5.4B.

## 12. Links
Report `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4A.md`; plan `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4_PLAN.md`; focused diff `CHATGPT_FOCUSED_DIFF.md`.
