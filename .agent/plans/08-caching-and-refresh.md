# Milestone 8: Redis caching, freshness, AI brief reuse, and manual refresh

## Status

In progress (2026-07-21)

## Current state

- PostgreSQL is the durable store and Redis is already available through Docker Compose and Spring Data Redis.
- Company, quote, metrics, history, and news reads currently persist provider data but invoke providers for normal reads.
- The comparison endpoint assembles every response afresh. The research endpoint persists a brief but does not reuse an equivalent persisted brief.
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

## Manual refresh

`POST /api/v1/comparisons/refresh` accepts one or two normalized distinct tickers and optional `regenerateBrief` (false by default). It evicts affected feature entries, invalidates history for later normal fetching, clears comparison/brief namespaces with SCAN, force-refreshes company/market/metrics/news, and returns sanitized per-section warnings for partial failures. AI is called only when `regenerateBrief=true`.

## Acceptance criteria

- Normal reads use Redis then fresh PostgreSQL before provider calls and remain functional with Redis unavailable.
- A complete comparison is cached canonically; reversed requests preserve requested left/right display orientation and mark provenance as cached.
- Brief reuse is keyed by canonical pair and input hash; `forceRefresh` bypasses both cache and persisted reuse and creates a new brief.
- Public API contracts remain compatible except for documented optional request fields and the new refresh endpoint.
- No Milestone 9 work, scheduled work, cache warming, authentication, deployment work, provider calls in automated tests, or changes to `docs/design.md`.

## Validation

- `cd backend && ./mvnw clean verify`
- `cd frontend && npm run lint && npm test -- --run && npm run build`
- Review `git diff --check`, complete diff, and tracked/untracked files for secrets, generated output, and local environment files.

## Risks and decisions

- No provider calls are allowed in tests; all provider and AI behavior will be mocked.
- Cache invalidation uses Redis SCAN, never `KEYS`. A Redis outage can leave an old cache entry until expiry but cannot prevent durable-data reads.
- Existing persisted brief source links do not reconstruct all original source labels independently; Redis reuse is the preferred fast path, while database reuse will reconstruct the response from the current grounded context with the same input hash.

## Progress

- [x] Inspect design, current implementation, tests, prior plan, and working diff.
- [x] Implement Redis JSON cache infrastructure, TTL properties, cache keys, comparison caching, AI Redis reuse, and manual refresh endpoint.
- [x] Implement frontend refresh and cached-status behavior.
- [ ] Complete PostgreSQL freshness fallback for every feature read and persisted-brief reuse; add the required dedicated cache/freshness/refresh test matrix.
- [ ] Run full milestone validation after the remaining cache-aside work is complete.
