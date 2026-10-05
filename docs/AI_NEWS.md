# AI news simplification

The existing `GET /api/v1/news?page=0&limit=20` contract still returns articles.
Each now preserves a provider `id`, `description` and nullable `explanation`:

```json
{
  "title": "Original headline",
  "url": "https://publisher.example/article",
  "source": "Original publisher",
  "publishedAt": "2026-10-04T12:00:00Z",
  "id": "finnhub:123",
  "description": "Provider-supplied description",
  "explanation": {
    "simpleHeadline": "A beginner-friendly headline",
    "summary": "One or two short sentences.",
    "whyItMatters": "Possible significance, with uncertainty preserved.",
    "sentiment": "NEUTRAL",
    "confidence": null
  }
}
```

## Backend setup

Set these in the **backend process**, using the same environment/run configuration
as FMP_API_KEY and FINNHUB_API_KEY:

- `GEMINI_API_KEY`: optional. Without it, stored summaries can still be read; no new AI work is queued.
- `GEMINI_NEWS_MODEL`: defaults to `gemini-3.5-flash-lite`.
- `NEWS_STORE`: defaults to `firestore`; use `sqlite` only for local development.
- `NEWS_FIRESTORE_PROJECT_ID`: defaults to `GOOGLE_CLOUD_PROJECT`, then `stocksteps`.
- `NEWS_FIRESTORE_DATABASE_ID`: defaults to `(default)`.
- `NEWS_DB_PATH`: defaults to `server/data/news.db`, relative to process working
  directory. Prefer an absolute path for predictable local persistence.

Keys are sent in headers, never to mobile clients or committed resources. A Secret
Manager environment binding is compatible with this configuration. Restart the
backend after changing configuration. Rebuild mobile apps for the new news UI.
Do not paste keys into chat or documentation.

The model identifier and structured output support were checked against
[official model documentation](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite)
on October 4, 2026. The model is configurable to accommodate availability changes.

## Processing and cost

NewsService fetches Finnhub general news, returns stored explanations immediately,
and queues eligible missing explanations without waiting for AI. Refresh later to
retrieve completed explanations. Android/iOS Home load news independently from
snapshot and watchlist, and the older discovery UI reuses the news card components.
No ticker-specific endpoint or invented company metadata is added in this V1.

Identity uses a positive Finnhub article ID, otherwise a SHA-256 hash of normalized
URL (fragment/tracking parameters removed and query sorted) and headline. The cache key also includes content, publisher,
symbol, model and prompt version. Changed content/version gets a new explanation.
Duplicate articles in a response are removed. Firestore shares results across all
users/backend instances and persists through restarts and Cloud Run replacements.
SQLite is an explicit local-development option. If an earlier local database exists
at NEWS_DB_PATH, matching summaries are copied to Firestore on first lookup, without
another AI call. This is lazy migration, not a bulk import of all older entries.
Concurrent work is coalesced locally; Firestore transactions acquire shared leases,
track unique ownership and fence stale workers from overwriting a newer result.

One worker, a 20-item bounded queue and at most five enqueues per response bound
work. No automatic HTTP retry: failures wait 15 minutes before becoming eligible.
Interrupted work leases expire after 60 seconds. AI HTTP timeout is 8 seconds;
the overall job timeout is 10 seconds. The queued work itself is not durable: a
subsequent news request queues it again after restart.

Only the headline (300 characters), description (2,000), ticker (30) and publisher
(120) are sent; no article scraping, grounding/search, user data or full article
fetch is performed. Output is capped at 512 tokens. Relevance is deterministic:
a description is required; ticker-tagged articles must mention that ticker, and
general articles must contain finance/market terms. This deliberately simple rule
can miss relevant stories or accept ambiguous matches; it is not an AI classifier.

SQLite files are ignored by Git. Firestore is now the durable store for Cloud Run.
The current queue limits are per instance, not an account-wide daily spending cap.
Monitor provider usage and configure account quotas. No silent SQLite fallback is
used when cloud configuration fails; original news continues to work.

## Connecting Firestore

The server uses Google's official Firestore SDK with Application Default Credentials
(ADC), not Android's google-services.json, the iOS plist, or Firebase CLI login.
Install the [Google Cloud CLI](https://cloud.google.com/sdk/docs/install) if it
is not available on your Mac, then run:

```sh
gcloud auth application-default login
gcloud auth application-default set-quota-project stocksteps
```

Set these in the backend environment and restart:

```text
NEWS_STORE=firestore
NEWS_FIRESTORE_PROJECT_ID=stocksteps
NEWS_FIRESTORE_DATABASE_ID=(default)
```

The authenticated principal must have Firestore data access (typically
`roles/datastore.user`). On Cloud Run, attach a service account with that role;
the SDK obtains credentials through the runtime. No service-account key download
is required. Ensure the existing default Firestore database is available.

Summaries are stored under `newsSimplifications/{cacheKey}` with `result` JSON,
`retryAt`, `updatedAt` and temporary lease `owner`. These documents contain
explanations/coordination metadata, not the entire provider news feed. Existing
rules deny all mobile reads/writes to this collection; the server uses IAM.
No rules deployment is needed for the current deny-by-default rules.

For local emulator testing use `FIRESTORE_EMULATOR_HOST=127.0.0.1:8085` and a
`demo-*` project. Emulator tests never use stocksteps credentials or data.

Firestore reads have a 1.5-second total response-path budget; individual operations
have a five-second cancellable deadline. Slow/unavailable cloud storage returns
original news while retaining explanations already loaded. Background jobs have the existing AI/cooldown limits.

References: [ADC setup](https://cloud.google.com/docs/authentication/provide-credentials-adc),
[Firestore server setup](https://firebase.google.com/docs/firestore/quickstart-server),
[Firestore IAM](https://cloud.google.com/firestore/native/docs/security/iam).


## Output and failures

The backend interface `AiNewsSimplifier` is provider-neutral; Gemini DTO/request
handling is isolated. The prompt distinguishes facts from implications, forbids
invented facts, advice and predictions, and treats article text as untrusted data.
Structured output and local validation reject malformed JSON, missing/blank fields,
unknown sentiment, invalid confidence and overlong text. Only completed candidates
are accepted. Prompt/schema validation cannot prove factual accuracy; review real
outputs against source descriptions before release. Confidence, if ever supplied,
is an unverified model estimate and is not shown in the UI.

AI/provider/storage failures leave `explanation: null` and do not fail the news
response. Storage initialization failure disables enrichment for that process,
with a sanitized warning. Provider-news failures retain existing API error behavior.
Cards display publisher/time, simplified headline/summary, Why it matters,
sentiment and AI-simplified attribution, with Read original. Unsimplified cards
show the original headline, up to three lines of the provider description when
available, and attribution/link.

Tests use mock providers, never real Gemini calls. They cover parsing/errors,
missing fields/sentiment, timeouts, persistence/claims, duplicate/cached/uncached
articles, relevance, source mapping and original-news fallback.
