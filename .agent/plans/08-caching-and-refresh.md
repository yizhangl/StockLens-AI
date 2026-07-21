# Milestone 8: Redis caching, freshness, AI brief reuse, and manual refresh

## Status

Complete (2026-07-21)

## Current state

- PostgreSQL is the durable store and Redis is already available through Docker Compose and Spring Data Redis.
- The implementation supplies a best-effort JSON Redis facade, typed TTL configuration, central keys, comparison cache orientation adaptation, Redis brief reuse, the manual refresh endpoint, and frontend refresh controls.
- Company, market, metrics, history, and news now use Redis first and fresh, usable PostgreSQL data second before invoking their configured providers.
- The research endpoint reuses both Redis briefs and fresh matching persisted `ComparisonBrief` rows before generating a new brief.
- Milestones 1–7 are present in the working tree and are deliberately treated as the starting point for this milestone.

## Scope and phases

1. Add an explicit JSON Redis cache facade, typed TTL properties, central key construction, and safe Redis-failure degradation.
2. Make company, market, metrics, history, and news reads cache-aside with PostgreSQL freshness checks and provider refresh only for stale or absent data.
3. Cache complete canonical comparison responses, adapt reversed-pair cache hits to the requested orientation, and invalidate comparison namespaces without Redis `KEYS`.
4. Reuse AI briefs through Redis and newest matching persisted briefs; add `forceRefresh` while preserving the requested pair orientation.
5. Add `POST /api/v1/comparisons/refresh`, invalidation, forced source refresh, and partial-success warnings.
6. Add frontend data refresh and AI generate/regenerate behavior without changing the public dashboard contract.
7. Add focused unit/controller tests plus Redis/PostgreSQL integration coverage, then run backend and frontend validation.

## Cache policy

- Keys are centralized under `stocklens:`: company, market, metrics, history by period, news by limit, canonical comparison by pair/period/mode, and canonical brief by pair/input hash.
- Values are JSON DTOs/records only; no JPA entities or provider DTOs enter Redis.
- Default TTLs: company 24h, market 15m, metrics/history 6h, news/comparison 15m/30m respectively, and brief 1h. `stocklens.cache.*-ttl` properties and matching `STOCKLENS_CACHE_*` environment variables override them.
- Redis is best-effort. Serialization/connection failures are logged without secrets and fall back to the normal PostgreSQL/provider path.
- Database freshness uses the relevant persisted timestamp and the same boundary: exactly at expiry is stale; future timestamps are accepted as fresh with a warning log.
- Bounded persisted history is complete only when it has at least two distinct dates with positive close values, its first point is no more than seven calendar days after the requested start, and its last point is no more than seven calendar days before the requested end. The tolerance covers weekends, market holidays, and provider boundary timing while rejecting visibly truncated series. `MAX` requires two distinct usable dates and has no bounded-start requirement. The oldest retrieval timestamp in the selected series controls freshness so one newly updated point cannot hide stale coverage.
- V7 adds `news_retrieval`, an append-only successful-retrieval marker keyed to a company with provider, retrieval time, and nonnegative result count. A successful empty provider result persists `result_count=0`; a provider failure creates no marker and does not delete existing articles or advance the last-success timestamp.
- Persisted brief reuse builds the current grounded context and authoritative M7 input hash before checking canonical-pair Redis and then the newest matching PostgreSQL brief (pair, hash, prompt version, model). A fresh valid row is reconstructed against current grounded sources, cached, returned with `cached=true`, and does not call OpenAI. Corrupt or incompatible stored data is logged as a sanitized warning and generated normally. `forceRefresh=true` bypasses both reuse layers and preserves prior rows.
- Canonical comparison-cache values are reoriented on reversed requests: left/right summaries, history values and series, metric values/outcomes, news, provenance timestamps, and warning sides all follow the request order. Cache hits set the public cached provenance flag.

## Manual refresh

`POST /api/v1/comparisons/refresh` accepts one or two normalized distinct tickers and optional `regenerateBrief` (false by default). It evicts affected feature entries, invalidates history for later normal fetching, clears comparison/brief namespaces with SCAN, force-refreshes company/market/metrics/news, and returns sanitized per-section warnings for partial failures. AI is called only when `regenerateBrief=true`.

## Acceptance criteria

- Normal reads use Redis then fresh PostgreSQL before provider calls and remain functional with Redis unavailable.
- A complete comparison is cached canonically; reversed requests preserve requested left/right display orientation and mark provenance as cached.
- Brief reuse is keyed by canonical pair and input hash; `forceRefresh` bypasses both cache and persisted reuse and creates a new brief.
- Public API contracts remain compatible except for documented optional request fields and the new refresh endpoint.
- No Milestone 9 work, scheduled work, cache warming, authentication, deployment work, provider calls in automated tests, or changes to `docs/design.md`.

## Validation

- `cd backend && env -u FMP_API_KEY -u OPENAI_API_KEY ./mvnw clean verify`
- `cd frontend && npm run lint && npm run typecheck && npm test && npm run build`
- Review `git diff --check`, complete diff, and tracked/untracked files for secrets, generated output, and local environment files.

## Risks and decisions

- No provider calls are allowed in tests; all provider and AI behavior will be mocked.
- Cache invalidation uses Redis SCAN, never `KEYS`. A Redis outage can leave an old cache entry until expiry but cannot prevent durable-data reads.
- Existing persisted brief source links do not reconstruct all original source labels independently; Redis reuse is the preferred fast path, while database reuse will reconstruct the response from the current grounded context with the same input hash.

## Progress

- [x] Inspect design, current implementation, tests, prior plan, and working diff.
- [x] Implement Redis JSON cache infrastructure, TTL properties, cache keys, comparison caching, AI Redis reuse, and manual refresh endpoint.
- [x] Implement frontend refresh and cached-status behavior.
- [ ] Run and record the clean-verification baseline.
- [x] Add one `FreshnessPolicy` driven by the injected `Clock` (null/exact-boundary/stale/future behavior).
- [x] Complete durable-first company, market, and financial-metrics reads.
- [x] Add persisted `ComparisonBrief` lookup/reuse by canonical pair, input hash, prompt version, model, newest generation time, and ID. Stored source-link IDs are checked against the current grounded context before response reconstruction.
- [x] Complete durable-first history and news reads using the seven-day trading-boundary tolerance and the V7 successful news-retrieval marker.
- [x] Complete constructor wiring and focused unit/Testcontainers coverage for history freshness/completeness, valid-empty news, latest-marker lookup, and V7 migration.
- [x] Complete the required unit, controller, application-level, orientation, refresh, and Redis/PostgreSQL Testcontainers coverage.
- [x] Run full backend/frontend validation, review the complete diff, and check for generated files, local environment files, and secrets.

## Targeted validation evidence

- Compile-only command: `env -u FMP_API_KEY -u OPENAI_API_KEY ./mvnw -DskipTests test` — `BUILD SUCCESS`.
- Final targeted command: `env -u FMP_API_KEY -u OPENAI_API_KEY ./mvnw -Dtest='StockQueryServiceTest,FinancialMetricsQueryServiceTest,ComparisonResearchControllerTest,FreshnessPolicyTest,HistoricalPriceQueryServiceTest,NewsQueryServiceTest,NewsPersistenceIntegrationTest,NewsQueryServiceIntegrationTest' test`.
- Result: `Tests run: 34, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

## Final validation and completion evidence

- The first full run exposed one confirmed regression: `InfrastructureIntegrationTest` still asserted the pre-M8 V0–V6 migration list. The test now expects V7 and the `news_retrieval` table; no production behavior changed.
- Final backend command: `env -u FMP_API_KEY -u OPENAI_API_KEY ./mvnw clean verify` — `Tests run: 142, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`.
- PostgreSQL Testcontainers: passed. The full suite started PostgreSQL containers, Flyway validated and applied V0–V7, and persistence/integration tests passed.
- Redis Testcontainers: passed. The full suite started Redis containers, including the infrastructure Redis round-trip and cache-backed integration coverage.
- Frontend lint: `npm run lint` — passed.
- Frontend typecheck: `npm run typecheck` — passed.
- Frontend tests: `npm test` — `8` files passed, `28` tests passed.
- Frontend production build: `npm run build` — passed. Vite emitted its non-blocking >500 kB chunk-size advisory.
- Diff check: `git diff --check` — passed. The complete working-tree review found no old Flyway migration edits, provider calls in automated tests, secrets, `.env` files, generated build output, or Milestone 9 work.
- Manual live-provider verification was intentionally skipped: no FMP, Yahoo Finance, OpenAI, or localhost endpoint was called. The automated suite covers the cache/freshness and reuse paths in isolation.

## Known limitations

- Manual live-provider smoke testing remains deferred by design for this validation-only turn.
- The frontend production bundle has Vite's non-blocking chunk-size warning; it is outside Milestone 8 cache/freshness scope.
