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

- `GEMINI_API_KEY`: optional. Without it, original news still works.
- `GEMINI_NEWS_MODEL`: defaults to `gemini-3.5-flash-lite`.
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
Duplicate articles in a response are removed. SQLite shares results across all
users and persists through backend restarts. Concurrent work is coalesced locally,
with database leases as an additional protection for processes sharing the file.

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

SQLite files are ignored by Git. This implementation targets the current single
backend. Cloud Run's local disk is ephemeral and not shared between instances:
replace NewsSimplificationStore with a durable shared database and distributed
claim/budget strategy before scaling. The current queue limits are not an account-
wide daily spending cap. Monitor provider usage and configure account quotas.

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
