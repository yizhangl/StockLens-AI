# Milestone 5: Aggregated Comparison API

**Status:** Completed  
**Milestone:** 5 — Aggregated Comparison API  
**Created:** 2026-07-19  
**Last Updated:** 2026-07-19  
**Design References:** `docs/design.md` Sections 4, 8–11, 13–16, 18–19,
22–25, 28 (Milestone 5), and 29

## 1. Goal

Add the provider-independent `GET /api/v1/comparisons` backend endpoint that
assembles the completed company, market, financial-metric, historical-price,
and news capabilities into one deterministic two-stock dashboard response.

The endpoint preserves requested left/right display order, uses a canonical
identifier for the unordered ticker pair, aligns histories on common trading
dates, supports `PRICE` and `RETURN` display modes, applies safe registry-driven
metric comparisons, and degrades known non-critical section failures into typed
warnings. Company identity remains critical.

## 2. Current Repository State

Milestones 1–4 are complete. The repository was clean before this milestone
began. The backend is a Java 21 / Spring Boot 4.1 modular monolith with:

- PostgreSQL persistence, Flyway V0–V5, and Hibernate schema validation.
- Redis connectivity only; no business cache.
- FMP behind `FinancialDataClient` for profiles, quotes, metrics, and history.
- Yahoo Finance behind `NewsDataClient` for recent news.
- Provider-neutral query responses for stock, metrics, history, and news.
- A central `MetricDefinitionRegistry` containing code, display name, category,
  unit, comparison strategy, description, and stable enum order.
- `PricePeriod` parsing for `1M`, `6M`, `1Y`, `5Y`, and `MAX`.
- `TickerNormalizer` as the single ticker validation component.
- Existing unit, MVC, mock-HTTP, PostgreSQL Testcontainers, and Redis
  Testcontainers coverage. Automated tests do not call live providers.

The initial Git status, diff, diff stat, and whitespace check were clean. No
comparison package, comparison persistence, cache, or AI code exists.

## 3. Scope

### In scope

- Normalize and validate two ticker inputs, period, mode, and duplicates.
- Resolve both Company identities as critical data.
- Refresh and assemble optional market, metric, history, and news sections.
- Preserve requested left/right order and generate a canonical sorted ID.
- Align daily histories by the intersection of valid trading dates.
- Return raw prices in `PRICE` mode and shared-baseline percentage returns in
  `RETURN` mode.
- Group metrics in the approved four-category order and compare values using
  the registry's strategies.
- Return up to three newest news articles per side.
- Return typed, sanitized, de-duplicated partial-data warnings.
- Return deterministic provenance and `cached=false`.
- Serialize `aiBrief` explicitly as `null`.
- Add unit, MVC, and full application-level fake-provider tests.
- Run automated verification and the requested local live smoke checks.

### Out of scope

- Frontend or React dashboard work.
- AI, Spring AI, OpenAI, prompts, grounding, or comparison-brief persistence.
- Redis business caching, cache keys, refresh, invalidation, or scheduling.
- New providers or changes to FMP/Yahoo adapters.
- Authentication, deployment, watchlists, forecasts, or advice.
- Database tables or migrations.
- Milestone 6 or later work.

## 4. Assumptions and Decisions

- `StockQueryService.getStock` must preserve its current fetch-before-write
  behavior. Additive company-resolution and market-refresh operations will let
  the comparison orchestrator separate critical identity from optional market
  data without changing the existing stock endpoint.
- The comparison service may invoke the existing metrics and history query
  services after resolving a Company. Their additional profile calls are
  accepted for this uncached milestone; provider-call consolidation belongs to
  a later refresh/caching design.
- A valid identity provider response is persisted before optional sections are
  requested. Unknown profiles remain `STOCK_NOT_FOUND`; provider/configuration
  failures during identity resolution remain whole-request errors under the
  existing contracts.
- After identity succeeds, only known feature failures are converted to
  section warnings. Database failures and programming errors propagate.
- No stale-data fallback is added. A failed optional refresh returns null/empty
  section data plus a warning rather than silently substituting an older row.
- Duplicate historical dates retain the first valid provider-neutral point in
  input order, then dates are sorted ascending. Existing provider and database
  boundaries already reject/prevent duplicates; this rule makes synthetic or
  future inputs deterministic.
- A positive adjusted close is preferred. Otherwise a positive close is used.
  A point with no positive usable value or no date is discarded.
- Histories use only the intersection of valid trading dates. There is no
  interpolation, forward fill, weekend generation, or independent baseline.
- `RETURN` values use a 24-digit `HALF_UP` calculation context and API scale 4,
  matching `HistoricalReturnCalculator`.
- A one-point `PRICE` series is valid. A one-point `RETURN` series contains the
  shared zero baseline but has null summary returns and an insufficient-history
  warning.
- No common history produces an empty series and a `HISTORY` warning.
- Currency mismatch is warned only for raw `PRICE` comparison; no FX conversion
  is attempted.
- Registry enum order is the stable metric order. `SUMMARY` metrics are omitted
  from groups; `REVENUE_TTM` is surfaced in company summaries.
- Missing values always produce `INSUFFICIENT_DATA`. Range-, context-, and
  descriptive strategies remain `NEUTRAL` when both values exist. A non-positive
  PEG ratio is also neutral rather than becoming an unsafe lower-value winner.
- Financial/news provider provenance is the sorted distinct set of successful
  source names joined by commas. A single provider remains its ordinary name.
- `lastUpdatedAt` is the maximum actual successful source timestamp represented
  in the response; missing timestamps are not replaced with generation time.

## 5. API Contract

```http
GET /api/v1/comparisons?left=AAPL&right=MSFT&period=1Y&mode=RETURN
```

- `left` and `right` are required logically and normalized by
  `TickerNormalizer`.
- `period` defaults to `1Y` and is parsed by `PricePeriod` case-insensitively.
- `mode` defaults to `RETURN`; supported values are `PRICE` and `RETURN`, parsed
  case-insensitively.
- Duplicate normalized tickers return `400 DUPLICATE_TICKERS`.
- Invalid/missing/blank tickers return `400 INVALID_TICKER`.
- Invalid period returns `400 INVALID_PERIOD`.
- Invalid mode returns `400 INVALID_MODE`.
- Unknown Company identity returns `404 STOCK_NOT_FOUND`.

Canonical ID:

```text
min(left,right):max(left,right):period:mode
```

The ID is canonical; the `left` and `right` response sections are never
reordered.

Top-level response fields:

- `comparisonId`
- `left`, `right` company summaries
- `pricePerformance`
- `metricGroups`
- `news.left`, `news.right`
- `aiBrief` (null)
- `provenance`
- `warnings`

Company summaries expose raw provider-independent values only: ticker, company
name, exchange, sector, industry, country, website, logo, description, quote
values, market cap/currency/timestamps, P/E TTM, revenue TTM, and company/market/
metrics retrieval timestamps.

Price performance exposes period, mode, common start/end dates, point count,
summary returns, currencies, and one aligned left/right value per common date.
`PRICE` points contain raw prices and no winner. `RETURN` points contain
percentage points from the first common date, whose values are both zero.

Metric rows expose registry metadata, both raw `BigDecimal` values, strategy,
outcome, and a short deterministic explanation. Groups are ordered:

1. `VALUATION`
2. `PROFITABILITY`
3. `GROWTH`
4. `FINANCIAL_HEALTH`

Warnings expose `section`, `side`, `code`, and sanitized `message`. Sections
are `MARKET`, `METRICS`, `HISTORY`, and `NEWS`; sides are `LEFT`, `RIGHT`,
`BOTH`, and `GENERAL`.

## 6. Critical and Partial-Failure Policy

1. Normalize and resolve both Company identities before optional work.
2. Let identity `STOCK_NOT_FOUND`, provider, configuration, database, and
   unexpected failures retain existing whole-request behavior.
3. For each optional section and side, convert only its known provider,
   rate-limit, unavailable-data, or post-resolution stock failure to one typed
   warning.
4. Never catch generic `RuntimeException`/`Exception` for partial success.
5. Ignore per-field metric warnings when a snapshot exists; null values remain
   visible without one warning per field.
6. Convert news skipped-record warnings into at most one section warning per
   side.
7. De-duplicate warnings by section, side, and code while preserving assembly
   order.

## 7. Historical Alignment Algorithm

1. Convert each response point to `(date, effectiveValue)` if its date exists
   and adjusted-close-or-close is positive.
2. Retain the first valid point for duplicate dates and sort by date ascending.
3. Intersect the two date sets; this implicitly trims to the overlapping range.
4. If empty, return an empty performance section and `NO_COMMON_HISTORY`.
5. In `PRICE`, emit the two effective prices for each common date.
6. In `RETURN`, use the first common date's two values as the shared baseline
   and calculate each point with `((value / baseline) - 1) * 100` using
   `BigDecimal`, calculation context 24, `HALF_UP`, and output scale 4.
7. For one common `RETURN` point, emit the zero point but set both summary
   returns null and report `INSUFFICIENT_HISTORY`.
8. Derive summary start/end/count and final returns only from the aligned set.

## 8. Metric Comparison Rules

- Missing either value: `INSUFFICIENT_DATA`.
- `HIGHER_IS_GENERALLY_BETTER`: numeric greater side, or `EQUAL`.
- `LOWER_IS_GENERALLY_BETTER`: numeric lower side, or `EQUAL`.
- Non-positive PEG on either side: `NEUTRAL` even though its registry strategy
  is lower-is-generally-better.
- `RANGE_DEPENDENT`, `CONTEXT_DEPENDENT`, and `DESCRIPTIVE_ONLY`: `NEUTRAL`.
- P/E, forward P/E, ROE, current ratio, and beta therefore never receive an
  unsafe universal winner under the current registry.

## 9. Provenance

The response includes successful source names and timestamps for company,
market, metrics, history, and news on both sides; the aligned history range;
`lastUpdatedAt`; and `cached=false`. Provider names come from feature responses
or persisted source records, never controller constants. Missing sections keep
their provenance fields null.

## 10. Expected Files

### Create

- `.agent/plans/05-comparison-api.md`
- `backend/src/main/java/com/stocklens/common/exception/DuplicateTickersException.java`
- `backend/src/main/java/com/stocklens/common/exception/InvalidComparisonModeException.java`
- `backend/src/main/java/com/stocklens/comparison/controller/ComparisonController.java`
- `backend/src/main/java/com/stocklens/comparison/service/ComparisonService.java`
- `backend/src/main/java/com/stocklens/comparison/service/HistoricalSeriesAligner.java`
- `backend/src/main/java/com/stocklens/comparison/service/MetricComparisonService.java`
- comparison DTO records under `backend/src/main/java/com/stocklens/comparison/dto/`
- comparison enums under `backend/src/main/java/com/stocklens/comparison/model/`
- `backend/src/test/java/com/stocklens/comparison/controller/ComparisonControllerTest.java`
- `backend/src/test/java/com/stocklens/comparison/service/ComparisonServiceTest.java`
- `backend/src/test/java/com/stocklens/comparison/service/HistoricalSeriesAlignerTest.java`
- `backend/src/test/java/com/stocklens/comparison/service/MetricComparisonServiceTest.java`
- `backend/src/test/java/com/stocklens/comparison/ComparisonApiIntegrationTest.java`

### Modify

- `backend/src/main/java/com/stocklens/company/service/StockQueryService.java`
- `backend/src/main/java/com/stocklens/common/web/GlobalExceptionHandler.java`
- `backend/src/test/java/com/stocklens/company/service/StockQueryServiceTest.java`
- this execution plan as progress and validation evidence are recorded

The list may be narrowed if a proposed type is unnecessary. No existing DTO,
entity, migration, provider adapter, dependency manifest, configuration,
frontend, Compose, CI, or design file is expected to change.

## 11. Implementation Phases

### Phase 1 — Request and feature-service boundary

1. Add duplicate-ticker and comparison-mode validation/error mappings.
2. Add mode and warning/outcome enums.
3. Add additive company-resolution and market-refresh operations while
   preserving `getStock` behavior.

Validation: focused validator/service tests and compilation.

### Phase 2 — Deterministic domain assembly helpers

1. Add provider-independent dashboard DTO records.
2. Implement historical intersection, price/return mapping, and statuses.
3. Implement registry-driven metric grouping and comparison outcomes.

Validation: focused aligner and metric-comparison tests.

### Phase 3 — Orchestrator and HTTP endpoint

1. Implement sequential ComparisonService orchestration without a long
   transaction or direct provider-client use.
2. Add critical/optional failure handling, warnings, company/news mapping,
   provenance, canonical ID, logging, and explicit null AI field.
3. Add the thin comparison controller with documented defaults.

Validation: service and MVC tests.

### Phase 4 — Integration-oriented coverage

1. Add an application-level controller-to-service test with fake financial and
   news provider boundaries and real PostgreSQL persistence.
2. Assert complete assembly, provider isolation, defaults, alignment, null AI,
   no cache, and preserved existing tables/migrations.

Validation: focused application-level test; no live network.

### Phase 5 — Full validation and smoke review

1. Run `cd backend && ./mvnw clean verify`.
2. Start existing Compose services and run the requested default, price,
   reversed-order, duplicate, invalid-period, and invalid-mode smoke calls when
   local credentials are available.
3. Review status, complete diff, whitespace, migrations, secrets, generated
   files, dependencies, provider boundaries, numeric types, warning catches,
   and Milestone 6 scope.
4. Fix confirmed Milestone 5 issues only and finalize this plan.

## 12. Testing Strategy

- Validation: valid/lowercase/whitespace/missing/blank/invalid/duplicate inputs;
  defaults and invalid values; canonical ID and display order.
- Aligner: all supported periods, identical/different/unsorted/missing/duplicate
  dates, empty/no-common/one-point histories, adjusted-close fallback, invalid
  values/baselines, price mode, shared-baseline return mode, precision.
- Metrics: higher/lower outcomes, equality, every missing-value case, neutral
  strategies, unsafe PEG/P-E/beta/current-ratio/ROE behavior, group/order/raw
  numeric preservation.
- Service: complete dashboard, summaries, aligned history, metric groups, news
  cap/order, explicit null AI, false cache, provider/timestamp provenance,
  warning conversion/de-duplication, critical identity failure, and unexpected
  exception propagation.
- MVC: status/defaults/modes/normalization/missing/invalid/duplicate/not-found/
  partial responses, field names, null AI, no provider DTO/entity leakage.
- Integration: actual HTTP controller, application services, persistence, and
  fake provider interfaces with no external requests.
- Regression: all existing Milestone 1–4 tests in the Maven lifecycle.

## 13. Acceptance Criteria

- [x] Endpoint accepts exactly two normalized distinct tickers.
- [x] Defaults are `period=1Y` and `mode=RETURN`; invalid explicit values fail.
- [x] Requested side order is preserved and ID uses the sorted pair.
- [x] Company identity is critical; known optional failures become warnings.
- [x] ComparisonService calls no FMP/Yahoo implementation or provider client.
- [x] Company summaries contain only real raw numeric/source fields.
- [x] Histories use valid common dates with no fill/interpolation.
- [x] PRICE has raw values and no winner; currency mismatch warns.
- [x] RETURN uses the shared first common date, BigDecimal, and scale 4.
- [x] Metrics reuse the registry and unsafe strategies remain neutral.
- [x] Metric groups and rows have stable approved ordering.
- [x] News is provider-independent, newest-first, and at most three per side.
- [x] Warnings are typed, sanitized, deterministic, and de-duplicated.
- [x] Provenance uses actual successful source names/timestamps and false cache.
- [x] `aiBrief` is explicitly null with no AI implementation.
- [x] No table, migration, dependency, frontend, cache, or Milestone 6 work.
- [x] Automated tests make no live provider calls.
- [x] `cd backend && ./mvnw clean verify` passes with Milestone 1–4 tests.
- [x] Complete diff, secrets, generated-file, and scope reviews pass.
- [x] Requested live smoke results are recorded honestly.

## 14. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Multiple sequential provider calls increase latency | Slow dashboard response | Keep deterministic sequential orchestration now; caching/refresh is explicitly later. |
| Optional query service repeats profile resolution | Extra FMP traffic | Preserve existing service contracts; document and defer consolidation to approved refresh work. |
| Optional provider errors hide real bugs | Incorrect HTTP 200 | Catch only enumerated feature exceptions; propagate all unexpected exceptions. |
| Histories have mismatched calendars | Biased normalized returns | Use intersection and one shared baseline; never fill or interpolate. |
| Duplicate synthetic dates disagree | Ambiguous point | First valid input point wins deterministically; current provider/schema prevent production duplicates. |
| Raw prices use different currencies | Misleading comparison | Preserve both currencies, warn in PRICE, and do no FX conversion. |
| Registry strategy is overly simplistic | Unsafe winner | Respect context/range/descriptive neutrality and guard non-positive PEG. |
| Partial timestamps/providers are missing | Fabricated provenance | Keep missing fields null and derive aggregates only from successful source data. |

## 15. Migration and Dependency Decision

No migration or dependency is required. The comparison is an assembled view of
existing durable source records and provider-neutral services. V0–V5 remain
unchanged and no comparison, cache, or AI table is added.

## 16. Progress

- [x] Read AGENTS, PLANS, full design, Milestone 2–4 plans, current code/DTOs/
  tests, and complete clean Git diff.
- [x] Reconciled service boundaries, alignment inputs, registry semantics, and
  critical/non-critical failure behavior without a design conflict.
- [x] Created the coherent Milestone 5 execution plan before implementation.
- [x] Phase 1 complete — request and feature-service boundary.
- [x] Phase 2 complete — DTOs, historical alignment, and metric comparison.
- [x] Phase 3 complete — orchestration and controller.
- [x] Phase 4 complete — integration-oriented coverage.
- [x] Phase 5 complete — full validation, live smoke, and final review.
- [x] Acceptance criteria confirmed.

## 17. Decision Log

| Date | Decision | Reason | Alternative |
|---|---|---|---|
| 2026-07-19 | Keep `getStock` behavior and add reusable resolution/market operations | Separates critical identity from optional market without breaking M2 | Treat all stock data as critical; duplicate provider calls in comparison |
| 2026-07-19 | Use sorted ID but requested display order | Meets future canonical identity and current UI semantics | Reorder dashboard sides |
| 2026-07-19 | Intersect common trading dates with one baseline | Fair, deterministic comparison without invented data | Forward-fill, interpolate, or independent starts |
| 2026-07-19 | Use registry enum order and omit SUMMARY group | Registry is source of truth; revenue belongs in summary | Duplicate metric lists in comparison |
| 2026-07-19 | Catch only known optional feature failures | Preserves partial utility without swallowing bugs | Catch every runtime exception |
| 2026-07-19 | Join sorted distinct provider names | Honest deterministic provenance if sources differ | Hide mismatch behind a hardcoded provider |
| 2026-07-19 | Add no persistence/cache/AI behavior | Design defines Milestone 5 as response orchestration | Prebuild later milestones |

## 18. Validation Evidence

| Command / check | Result | Notes |
|---|---|---|
| Initial Git status/diff/diff-check | PASS | Repository was clean before Milestone 5 changes. |
| `cd backend && ./mvnw -DskipTests compile` | PASS | 105 production source files compiled without a dependency change. |
| Focused comparison and StockQueryService tests | PASS | Unit and MVC coverage passed; initial sandbox-only Docker socket denial was rerun with Docker access. |
| First `cd backend && ./mvnw clean verify` | PASS | 126 tests, 0 failures/errors; PostgreSQL/Redis Testcontainers, Flyway V0–V5, Hibernate validation, M1–M4 regression suite, integration HTTP assembly, and package succeeded. |
| Initial live smoke | ISSUE FOUND | Default/PRICE/reversed requests exposed a null `REVENUE_TTM` summary lookup bug; validation errors already returned the expected 400 codes. |
| Null summary-metric regression | PASS | Focused ComparisonService suite passed with unavailable revenue preserved as null and no section failure. |
| Final `cd backend && ./mvnw clean verify` | PASS | 127 tests, 0 failures/errors; full post-fix lifecycle and package succeeded. No automated test contacted FMP or Yahoo. |
| Final default live smoke | PASS | Temporary port 18083 returned 200; AAPL/MSFT, 1Y RETURN, 250 aligned common points, first values zero, four metric groups, three news items per side, null AI, FMP/Yahoo provenance, false cache, no warnings. |
| Final PRICE live smoke | PASS | 200; 6M PRICE, 124 aligned points, raw numeric prices, no winner field, matching USD currencies, no warnings. |
| Final reversed-order smoke | PASS | 200; left remained MSFT, right remained AAPL, while ID remained `AAPL:MSFT:1Y:RETURN`. |
| Final invalid-request smoke | PASS | Duplicate returned `400 DUPLICATE_TICKERS`; `2Y` returned `400 INVALID_PERIOD`; `GAIN` returned `400 INVALID_MODE`. |
| Complete diff/whitespace review | PASS | `git diff --check` passed; all modified tracked files and every new comparison file were reviewed. |
| Migration/dependency/scope review | PASS | No change to V0–V5, `pom.xml`, design, frontend, provider adapters, Compose, CI, deployment, Redis business logic, or AI. |
| Boundary/numeric/error review | PASS | No production comparison import of provider clients/adapters, no direct FMP/Yahoo calls, no `double`, no async/parallel execution, no long transaction, and no catch-all partial warning logic. |
| Secrets/generated-file review | PASS | No `.env`, credential, local environment file, or generated artifact is staged/untracked; Maven `target` remains ignored. |

## 19. Completion Summary

Milestone 5 is complete. `GET /api/v1/comparisons` now validates and normalizes
two distinct tickers, defaults to `1Y`/`RETURN`, preserves requested side order,
and uses a sorted canonical comparison ID. Company resolution is critical;
market, metrics, history, and news use existing provider-neutral application
services and convert only known failures into typed partial warnings.

The response contains raw company/market/summary values, four registry-ordered
metric groups with safe deterministic outcomes, intersection-aligned price or
shared-baseline return points, at most three newest news articles per side,
actual source provenance, `cached=false`, and explicit `aiBrief=null`. Missing
individual metrics remain null. PRICE has no performance winner or FX
conversion. RETURN uses `BigDecimal` and scale 4.

No migration or dependency was added, and no existing migration was edited.
No provider adapter, public Milestone 1–4 endpoint, frontend, AI, cache, refresh,
authentication, or deployment work changed. Final verification passed 127
tests with no failures or errors, including the complete earlier-milestone
suite and a fake-provider HTTP integration test. Live AAPL/MSFT smoke requests
passed after a null-metric issue found by the first run was fixed and covered.

Known limitations are deliberate: the uncached orchestrator performs sequential
provider calls and existing metric/history query services repeat profile
resolution; Yahoo remains an unofficial provider; different PRICE currencies
are warned rather than converted; and partial failures do not fall back to
stale stored rows. These belong to later approved refresh/cache/provider work.
No Milestone 5 plan item remains, and Milestone 6 was not started.
