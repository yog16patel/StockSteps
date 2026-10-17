# Licensing and provenance — Phase 9B.8

- Alpha Vantage remains **personal-licence data**: the gate's personal mode now applies whenever Alpha Vantage serves any dataset
  (fundamentals-only included); commercial mode fails closed for it and for every unlicensed input. Derived metrics inherit their inputs'
  rights (`LicenseRegistry.derivedDisplayAllowed`).
- **AI processing:** Alpha Vantage `aiProcessing = PENDING_CONFIRMATION` → with Alpha Vantage in use, Gemini receives no market data
  (comparison, brief, earnings, movement AI use deterministic fallbacks).
- **Provenance:** every fact carries `source` (PROVIDER_DIRECT / BACKEND_CALCULATED), `basis` (period, date, fiscal year, currency) and a
  note; bundle `warnings[0]` names the source; `datasets` lists per-dataset availability; `retrievedAt` is set. No new contract field was
  needed. Proposal (optional, additive): `CompanyFundamentals.source: String?` ("alphavantage" / "fmp") for the apps' "Source" line —
  owner decision D-9B8-5.
- Keys server-side only; no secrets in exceptions or logs (9B.7 client); user data untouched; caches are symbol/period keyed only.
- Open questions for Alpha Vantage (Q13, 9B.7) plus: may fundamentals and computed ratios be shown to testers or the public; may they be
  sent to an AI model; may statements be stored beyond 24 h?
