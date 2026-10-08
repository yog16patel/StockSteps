# Portfolio tracker implementation

Implemented on Android Compose, native SwiftUI and the KMP/Ktor layers. This work is
committed as **Add shared portfolio tracker on Android and iOS**, after
`Add personalized Home dashboard on Android and iOS` (`a6b3e2f`). No production
credentials, provider purchases or Firestore deployment were performed.

## Architecture and navigation

The five tabs are Home, Markets, Portfolio, Watchlist and Learn. Settings remains
available from Home's profile button, including theme, authentication, biometric
lock, development backend selection and sign-out. Android uses serialized route
objects; iOS uses native navigation destinations and sheets. Holding Details opens
the exact saved symbol in the existing Company Details destination. Settings and
form/detail pages keep back/close controls. Home explicitly opens its root when
its tab is selected, avoiding restoration of an old Settings child destination.

`core/.../portfolio` owns independent serializable ledger models, exact decimal
arithmetic, deterministic calculations, fixtures, `PortfolioRepository`, and an
app-owned `PortfolioPresenter`. `AccountDependencies` supplies these with Koin.
Scenes acquire ViewModels, collect state and wire navigation; Screens receive
immutable state and callbacks. Entry files remain light. Native SwiftUI delegates
calculations to the same shared presenter through `IosAccountClient`.

The Home card observes this presenter's StateFlow; it has no separate portfolio
engine or holdings store. The watchlist card continues observing watchlists.
Portfolio/watchlist membership and deletion are independent. Company Details and
watchlist row menus can start a prefilled portfolio entry. Holding Details uses
the existing watchlist chooser and existing symbol-scoped alerts destination.
There is no second alert registry for owned stocks.

## Data model and ledger

- `PortfolioAccount`: stable UUID, unique display name, category (TFSA/RRSP/FHSA/
  Non-Registered/Personal/Other), CAD/USD reporting currency, archive state and
  server timestamps.
- `PortfolioTransaction`: stable UUID/idempotency key, account, qualified symbol
  and exchange metadata, type, explicit trade/effective date, optional settlement
  date, fractional quantity, unit price, trade and cash currencies, gross amount,
  fees, net amount, optional actual trade-to-cash FX rate, notes, server timestamps
  and server-assigned sequence.
- Types: BUY, SELL, CASH_DEPOSIT, CASH_WITHDRAWAL, DIVIDEND,
  DIVIDEND_REINVESTMENT, FEE, OPENING_POSITION, CASH_ADJUSTMENT,
  TRANSFER_IN, TRANSFER_OUT and STOCK_SPLIT.
- Sequence preserves submission order within a date, including submissions sharing
  a millisecond. Editing preserves the original sequence. Replay sorts by date,
  sequence, timestamp and ID.
- Opening positions import shares and cost as of the date entered. They create no
  cash and no chart history before that date. No acquisition dates are invented.
- Buy, sell, cash, dividend, reinvestment, transfer, adjustment and split entry,
  transaction editing/deletion, account editing/archiving, account selection,
  account deletion confirmation and account creation from an entry are supported.
- A stock split records a new/old ratio. Automated corporate-action ingestion and
  linked two-account transfer wizards are not included: manual TRANSFER_IN/OUT
  entries must use consistent carried basis. Transfers are distinct from deposits.

## Calculations and precision

`Decimal` implements base-ten integer-string arithmetic in common Kotlin, avoiding
Double financial calculations and platform BigDecimal differences. It accepts up
to 18 integer/eight fractional digits, rejects malformed/excess precision input,
uses half-up rounding for multiplication/division, and rejects out-of-range
results. Provider Doubles are converted only at the market-data boundary;
Double conversion in charts is for drawing. Money/percent display rounds to two
places without changing the stored ledger.

Moving-average book-cost policy (not a jurisdiction-specific legal tax ACB):

- Buy/opening fees increase cost basis. Sell fees reduce proceeds. A full sale
  removes all remaining basis, avoiding rounding residue.
- Realized gain = actual net sale proceeds minus allocated moving-average basis.
- Dividends increase cash and dividend totals without reducing basis.
- Reinvestment adds shares and basis with no net cash movement; the implied gross
  dividend funds the shares and fees. Do not record the same dividend twice.
- Standalone fees reduce cash. Deposits add cash net of fees; withdrawals remove
  cash plus fees. Gross external contributions remain distinct from expenses.
- Transfer-out removes proportional basis with no realized gain; transfer-in uses
  the carried average cost entered. Transfer fees affect cash/basis as appropriate.
- Splits multiply shares without changing basis. Related fees are separate entries.
- Negative shares and historical overselling are rejected on every write, edit and
  delete. Negative cash is allowed and explicitly labelled as unfunded/borrowed
  cash; no margin lending or brokerage execution is implied.
- Account value includes holdings plus each currency's cash. Remaining invested
  cost, unrealized/realized gains, native-currency dividend totals and holding/
  currency allocations are separate. Allocations include cash; missing data never
  renormalizes the available subset to 100%. Borrowed cash can yield >100% exposure.
- All-account total uses the active accounts and selected reporting currency when
  all needed values/current FX are available. Otherwise it is unavailable.
- Daily monetary gain removes actual external deposits/withdrawals. Percentage
  uses prior value plus deposits (a documented dated-flow convention, not intraday
  TWR). Opening imports, transfers, corrections and splits without measured
  boundary valuations make daily return unavailable. Stale quote sessions do too.
- TWR, XIRR, tax reporting, broker connections and automatic corporate actions are
  future enhancements; they are not approximated by price-change percentages.

## FX, quotes, historical values and caching

REAL uses the free, keyless [Bank of Canada Valet API](https://www.bankofcanada.ca/valet-api-how-to/)
for dated FXUSDCAD observations. The server caches results for six hours. The most
recent publication on or before a valuation date may be carried forward for at
most seven days (weekends/holidays), with the publication date shown. Missing FX
never becomes 1:1 or today's FX substituted into a past valuation.

Reporting cost and realized gains use acquisition/disposal-date FX. Current market
values use current dated FX. Trade currency, actual converted cash currency, and
security quote currency remain distinct. A quote in a different currency is
converted explicitly; unknown/unsupported quote currency makes it unavailable.

Quotes and metadata share the existing StockService/WatchMarketData caches. Shared
WatchDataRepository batches requests, coalesces concurrent loads and reuses covered
fresh responses for 60 seconds; explicit refresh can bypass this cache. Offline
fallback requires coverage for every requested symbol. Company Details and
Portfolio share the daily PriceChartService cache. Portfolio history loads only
when a range is requested; it is not fetched as years of prices on initial open.
At most four history provider loads run concurrently.

1D/1W/1M/3M/1Y/ALL chart requests replay the holdings and cash owned on each
observation date, use that date's prices/FX, and exclude dates before the first
ledger entry. They show account value, not investment return. Missing observations
are gaps. Up to approximately 600 actual observation dates are sampled, preserving
the last date. A 1D end-of-day dataset can have one point; intraday valuation is not
invented. Charts offer date/value inspection and accessibility descriptions.

Provider close adjustment conventions are not normalized into an automated
corporate-action dataset. Record splits explicitly and verify the source history;
this implementation does not manufacture split/dividend-adjusted total returns.
Canadian live quote availability still depends on the existing providers; a .TO
security never falls back silently to a US ticker.

## Persistence, endpoints and isolation

REAL: one transactional, server-only Firestore document at
`users/{verifiedUid}/portfolio/ledger`, storing the normalized shared ledger and
revision. Existing default-deny client rules cover this path. MOCK: separate
in-memory UserDataStore. Missing REAL credentials still fail visibly rather than
silently using in-memory storage.

All portfolio routes require the existing verified Firebase bearer authentication:

| Method | Path under `/api/v1/me/portfolio` | Purpose |
| --- | --- | --- |
| GET | root | Full normalized accounts + transactions ledger |
| PUT | `/accounts` | Create/update/archive an account by stable ID |
| DELETE | `/accounts/{id}` | Delete that account and its ledger entries |
| POST | `/transactions` | Idempotent creation by stable transaction ID |
| PUT | `/transactions` | Edit an existing entry, then validate full replay |
| DELETE | `/transactions/{id}` | Delete only if subsequent replay remains valid |
| GET | `/accounts/{id}/summary` | Shared-engine valuation, daily result, allocation |
| GET | `/accounts/{id}/history?range=1M` | Dated values and valuation notices |

The uid is never taken from a request body/path. Firestore read-modify-write is
atomic and retry-safe; retries cannot duplicate a transaction, and reusing an ID
with different content is rejected. Client-computed gross/net values and timestamps
are normalized by the server. A failed edit/delete leaves the previous ledger
unchanged. Operational limits: 20 accounts, 100 distinct securities, 1,000
transactions, and <800 KB encoded ledger. Pagination/subcollection migration is a
future scalability step, not an unbounded Firestore-document promise.

Device SQLDelight caches use the independent portfolio key and account+backend
namespace. Cached market reports must match ledger revision. Sign-out clears all
account/environment cache namespaces. Requests and 401 retries abort if identity
or backend changes during token restoration; results for old owners are discarded.
Derived positions are reused until ledger/account changes. No offline financial
write queue silently claims an unsaved trade was accepted.

## Mock scenarios

The development-only picker is shown only in MOCK. All 18 cases are deterministic,
read-only, and generated outside Composables/SwiftUI. They do not seed Firestore or
change saved mock accounts, watchlists, alerts or authentication:

no-portfolios, one-cad, multiple-accounts, mixed-currencies, fractional-shares,
multiple-buys, partial-sale, complete-sale, dividend, dividend-reinvestment,
cash-flows, missing-history, missing-fx, stale-quote, partial-failure, empty-account,
watched-and-owned and account-switch. Scenario account switching is also read-only.

MOCK server quotes/history stay fixture-based, and FX uses deterministic 1.35 CAD
per USD. User-entered mock portfolio dates use the real local server clock instead
of the old fixture capture date; dated previews have their own fixed observation
dates. REAL never instantiates the sample FX source.

## Validation and known limits

See the latest Portfolio section in PROJECT_HANDOFF.md for final commands/results.
Automated tests cover decimal precision/rounding, quantities, fees, partial/full
sales, dividends/DRIP, cash flows, transfers, splits, historical replay, FX basis,
daily flow adjustment, 18 fixtures, account/environment isolation, sign-out cache
cleanup, authentication races, backend ownership, atomic invalid edits/deletes,
idempotency, sequence ordering, dated Bank of Canada mapping/cache and history.

Android emulator smoke test: create CAD account; search/select exact AAPL NASDAQ
USD listing; save 0.5 shares at USD 100 on 2026-10-08; observe CAD 227.25 value,
CAD 67.50 basis and CAD 159.75 unrealized gain with mock FX. Home immediately
matched; watchlist remained empty. The temporary account was deleted with
confirmation. Partial-sale preview showed USD 984 total, 560 remaining basis,
140 unrealized and 44 realized. Native iOS is built with Xcode's simulator SDK;
live REAL Firestore/provider validation and device UI checks remain distinct from
these mock/build checks.

Next work: live Firebase/FX/provider acceptance test with configured server,
corporate-action-normalized history, linked transfer entry, scalable paged ledger,
and a native device accessibility/tablet/fold-posture acceptance pass.

TWR/XIRR, benchmarks, sector/asset-class allocation and attribution are now in
Portfolio Intelligence: see [PORTFOLIO_INTELLIGENCE.md](PORTFOLIO_INTELLIGENCE.md).
