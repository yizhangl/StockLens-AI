# Financial Metrics and Historical Prices

**Status:** Completed  
**Milestone:** 3 — Financial Metrics and Historical Prices  
**Created:** 2026-07-18  
**Last Updated:** 2026-07-18  
**Design References:** `docs/design.md` Sections 4.1, 8 (FR-4 and FR-5),
9.1, 9.3–9.5, 10.6–10.7, 11, 13.3–13.5, 13.8, 14–16,
18–19, 22–25, 28 (Milestone 3), and 29

## 1. Goal

Implement the second durable financial-data slice of StockLens AI. A caller can
request normalized financial metrics or one stock's historical daily prices for
an approved period. The backend loads provider-independent data through the
existing FMP adapter, persists append-only metric snapshots and idempotent daily
prices in PostgreSQL, calculates a single-stock return, and exposes application
DTOs through the approved supporting endpoints.

The milestone preserves the modular monolith: FMP DTOs remain in the adapter,
financial logic lives in services, Flyway owns V3/V4, controllers handle HTTP
only, and automated tests use mock HTTP and PostgreSQL Testcontainers without a
live credential.

## 2. Background and Current State

Milestone 2 is complete and the worktree was clean at planning time.

- Java 21 / Spring Boot 4.1 already supplies WebMVC/Jackson, JPA, PostgreSQL,
  Flyway, Mockito, mock REST support, and Testcontainers. No dependency change
  is required.
- `FinancialDataClient` currently exposes profile and quote methods. The FMP
  adapter already owns typed configuration, timeouts, bounded retry, safe error
  translation, and a `RestClient` that never logs request URLs or keys.
- Company and MarketSnapshot use Flyway V1/V2, JPA entities, repositories,
  services, provider-neutral records, and response DTOs.
- `TickerNormalizer`, UTC `Clock`, request IDs, and the common API error envelope
  are reusable.
- `InfrastructureIntegrationTest` currently asserts Flyway versions 0–2 and the
  `company` / `market_snapshot` tables.
- No financial metric, historical price, period, return, registry, `/metrics`,
  or `/history` implementation exists.

Official FMP documentation and its public API Viewer were inspected on
2026-07-18. All four relevant stable responses use a JSON array root. The
viewer labels the endpoints `Limited Access`, so automated completion relies on
sanitized schema-faithful fixtures and actual-account access remains a manual
verification limitation.

## 3. Scope

### In Scope

- Flyway V3 `financial_metric_snapshot` and V4 `historical_price` tables.
- JPA entities, repositories, deterministic latest metric query, historical
  date-range queries, and transactional idempotent price persistence.
- Extend `FinancialDataClient` with provider-neutral financial metrics and
  historical daily price methods.
- FMP stable integrations:
  - `/key-metrics-ttm`
  - `/ratios-ttm`
  - `/financial-growth?period=annual&limit=1`
  - `/historical-price-eod/full` with `from`/`to` when bounded.
- Exact field normalization documented in Section 8.
- Typed `PricePeriod` for `1M`, `6M`, `1Y`, `5Y`, and `MAX`, with UTC `Clock`
  date-range calculation.
- Single-stock return calculation using adjusted close only when legitimately
  present, otherwise close.
- A central metric definition registry containing code, name, category, unit,
  comparison strategy, and interpretation note.
- `GET /api/v1/stocks/{ticker}/metrics`.
- `GET /api/v1/stocks/{ticker}/history?period=1Y`.
- Unit, mapper, mock HTTP, repository, service integration, and controller
  tests, plus the existing complete backend suite.
- Minimal README smoke-test documentation with no credential value.

### Out of Scope

- News, article persistence, deduplication, or `/news`.
- `/api/v1/comparisons`, dashboard assembly, metric winner selection, grouped
  two-stock output, two-stock trading-date alignment, or partial dashboard data.
- AI, Spring AI, OpenAI, prompts, grounding, or briefs.
- Redis business caching, TTLs, cache keys, fallback policy, invalidation, or
  refresh orchestration.
- Frontend dashboard components or API integration.
- Authentication, users, watchlists, OpenAPI dependency work, deployment, or
  any Milestone 4+ implementation.
- Dividend-adjusted history endpoint, intraday data, technical indicators, or
  derived forward P/E/revenue/beta values.
- Flyway V5 or later.

## 4. Assumptions

- FMP remains the selected provider and the existing typed configuration is
  reused unchanged.
- No live FMP key is available or required. Official public API Viewer samples
  are used as the sanitized fixture source.
- Metric percentage-like values are stored as **decimal fractions** exactly as
  FMP supplies them: `0.246` means `24.6%`. The API exposes the same numeric
  fraction plus registry unit metadata; display conversion belongs to the
  frontend.
- Selected-period return is a different contract: it follows the design
  formula multiplied by 100 and is returned in **percentage points**, rounded
  to four decimal places only at the API/service boundary.
- `reported_at` is a nullable PostgreSQL `DATE` / Java `LocalDate`. FMP's annual
  growth payload supplies a date without a time; inventing a midnight timestamp
  would be less accurate.
- A non-empty growth row must include symbol, date, and a supported annual
  period. Growth currency is optional and falls back to profile currency.
- Metrics/history requests first load the company profile and their full target
  provider data before writing. This allows a fresh database to create the
  Company row and prevents provider failure from partially writing the target
  slice.
- Every metrics request appends a FinancialMetricSnapshot. Historical prices
  are upserted by company/date/provider so repeated retrievals update corrected
  values without duplicates.
- The full EOD sample has `symbol`, `date`, `open`, `high`, `low`, `close`, and
  `volume`, but no adjusted-close field. `adjusted_close` remains null and close
  drives returns. The dividend-adjusted endpoint is not substituted.
- FMP documents a 5000-record maximum for full history. `MAX` omits `from` and
  therefore means the maximum range returned by that endpoint/account, not an
  unlimited lifetime guarantee.
- Raw FMP JSON remains null. Only normalized fields are persisted.

## 5. Open Questions / Blockers

- None for credential-free implementation and automated validation.
- Actual account access to each `Limited Access` endpoint is unknown until the
  user runs a manual smoke test with `FMP_API_KEY`.
- Public/multi-user display and persistence remain subject to the user's FMP
  subscription and data-display license.

## 6. Acceptance Criteria

- [ ] V3/V4 apply after unchanged V0–V2; no V5+ migration is added.
- [ ] FinancialMetricSnapshot matches the approved fields, stores exact
  `BigDecimal` values, appends snapshots, and has a deterministic latest query.
- [ ] HistoricalPrice has the approved unique company/date/provider constraint,
  exact price decimals, integral volume, transactional upsert, and ascending
  queries.
- [ ] Provider-specific DTOs stay inside the FMP package and public responses
  contain no entity IDs, raw JSON, FMP DTOs, or credentials.
- [ ] The exact FMP mapping and unavailable metrics are implemented as Section 8
  defines; no provider values are fabricated.
- [ ] Percentage metrics use decimal fractions consistently; return percentages
  use percentage points and four-decimal API rounding.
- [ ] PricePeriod calculates deterministic UTC ranges for all five codes and
  invalid periods return a consistent HTTP 400 error.
- [ ] Return calculation covers positive, negative, zero, one-point, empty, and
  zero-start series without floating point.
- [ ] Metric definitions centralize semantics and never declare universal
  winners for beta, ROE, P/E, or current ratio.
- [ ] Metrics and history endpoints normalize lowercase tickers, persist valid
  data, return the documented contracts, and reuse existing safe errors.
- [ ] Mock HTTP tests cover success, partial/empty/malformed data, endpoint
  errors, and exact URLs without a live FMP call.
- [ ] PostgreSQL Testcontainers verifies migrations, JPA validation, latest
  metric lookup, historical uniqueness/idempotency/range/order, and existing
  infrastructure behavior.
- [ ] `cd backend && ./mvnw clean verify` passes with no FMP credential.
- [ ] Complete diff/security/scope review finds no secrets, generated files,
  unrelated changes, provider leakage, or Milestone 4+ implementation.

## 7. Expected Files

### Create

- `backend/src/main/resources/db/migration/V3__create_financial_metric_snapshot_table.sql`
- `backend/src/main/resources/db/migration/V4__create_historical_price_table.sql`
- `backend/src/main/java/com/stocklens/common/exception/InvalidPeriodException.java`
- `backend/src/main/java/com/stocklens/financial/domain/FinancialMetricSnapshot.java`
- `backend/src/main/java/com/stocklens/financial/domain/HistoricalPrice.java`
- `backend/src/main/java/com/stocklens/financial/repository/FinancialMetricSnapshotRepository.java`
- `backend/src/main/java/com/stocklens/financial/repository/HistoricalPriceRepository.java`
- `backend/src/main/java/com/stocklens/financial/service/FinancialMetricSnapshotService.java`
- `backend/src/main/java/com/stocklens/financial/service/FinancialMetricsQueryService.java`
- `backend/src/main/java/com/stocklens/financial/service/HistoricalPriceService.java`
- `backend/src/main/java/com/stocklens/financial/service/HistoricalPriceQueryService.java`
- `backend/src/main/java/com/stocklens/financial/service/HistoricalReturnCalculator.java`
- `backend/src/main/java/com/stocklens/financial/period/PricePeriod.java`
- `backend/src/main/java/com/stocklens/financial/period/PriceDateRange.java`
- `backend/src/main/java/com/stocklens/financial/metric/MetricCode.java`
- `backend/src/main/java/com/stocklens/financial/metric/MetricCategory.java`
- `backend/src/main/java/com/stocklens/financial/metric/MetricUnit.java`
- `backend/src/main/java/com/stocklens/financial/metric/ComparisonStrategy.java`
- `backend/src/main/java/com/stocklens/financial/metric/MetricDefinition.java`
- `backend/src/main/java/com/stocklens/financial/metric/MetricDefinitionRegistry.java`
- `backend/src/main/java/com/stocklens/financial/controller/FinancialController.java`
- `backend/src/main/java/com/stocklens/financial/dto/MetricValueResponse.java`
- `backend/src/main/java/com/stocklens/financial/dto/MetricWarningResponse.java`
- `backend/src/main/java/com/stocklens/financial/dto/FinancialMetricsResponse.java`
- `backend/src/main/java/com/stocklens/financial/dto/HistoricalPricePointResponse.java`
- `backend/src/main/java/com/stocklens/financial/dto/HistoricalPriceResponse.java`
- `backend/src/main/java/com/stocklens/market/client/model/FinancialMetricsData.java`
- `backend/src/main/java/com/stocklens/market/client/model/HistoricalPriceData.java`
- `backend/src/main/java/com/stocklens/market/client/fmp/dto/FmpKeyMetricsTtmResponse.java`
- `backend/src/main/java/com/stocklens/market/client/fmp/dto/FmpRatiosTtmResponse.java`
- `backend/src/main/java/com/stocklens/market/client/fmp/dto/FmpFinancialGrowthResponse.java`
- `backend/src/main/java/com/stocklens/market/client/fmp/dto/FmpHistoricalPriceResponse.java`
- Focused tests mirroring the production classes above under `backend/src/test`.
- Sanitized FMP fixtures for key metrics, ratios, growth, and full history under
  `backend/src/test/resources/fixtures/fmp/`.

### Modify

- `backend/src/main/java/com/stocklens/market/client/FinancialDataClient.java`
- `backend/src/main/java/com/stocklens/market/client/fmp/FmpFinancialDataClient.java`
- `backend/src/main/java/com/stocklens/market/client/fmp/FmpResponseMapper.java`
- `backend/src/main/java/com/stocklens/common/web/GlobalExceptionHandler.java`
- `backend/src/test/java/com/stocklens/InfrastructureIntegrationTest.java`
- Existing fake `FinancialDataClient` implementations in tests.
- `README.md`
- `.agent/plans/03-financial-metrics-and-history.md`

### Delete

- None.

No change is expected to `backend/pom.xml`, provider configuration, frontend,
CI, Compose, deployment files, or `docs/design.md`.

## 8. API / Schema / Provider Mapping

### Provider Field Mapping

All sample roots are arrays and all DTO decimals are `BigDecimal`.

| Normalized field | Stable endpoint / exact FMP field | Convention |
|---|---|---|
| `pe_ttm` | `/ratios-ttm` → `priceToEarningsRatioTTM` | Ratio, unchanged |
| `forward_pe` | Not directly supplied by approved endpoints | Null; do not derive from forward PEG |
| `peg_ratio` | `/ratios-ttm` → `priceToEarningsGrowthRatioTTM` | Ratio, unchanged |
| `price_to_sales` | `/ratios-ttm` → `priceToSalesRatioTTM` | Ratio, unchanged |
| `revenue_ttm` | Not directly supplied by approved endpoints | Null; do not derive from per-share data |
| `gross_margin` | `/ratios-ttm` → `grossProfitMarginTTM` | Decimal fraction |
| `net_margin` | `/ratios-ttm` → `netProfitMarginTTM` | Decimal fraction |
| `return_on_equity` | `/key-metrics-ttm` → `returnOnEquityTTM` | Decimal fraction |
| `revenue_growth` | latest annual `/financial-growth` → `revenueGrowth` | Decimal fraction |
| `earnings_growth` | latest annual `/financial-growth` → `netIncomeGrowth` | Decimal fraction |
| `debt_to_equity` | `/ratios-ttm` → `debtToEquityRatioTTM` | Ratio, unchanged |
| `current_ratio` | `/ratios-ttm` → `currentRatioTTM` | Ratio, unchanged |
| `beta` | Not directly supplied by approved metric endpoints | Null; do not add a screener/profile-derived estimate |
| `currency` | growth `reportedCurrency`, then profile currency | Uppercase ISO-like three letters or null |
| `reported_at` | latest annual growth `date` | `LocalDate` or null when growth unavailable |
| metadata | requested/returned `symbol`; application `retrievedAt`; `FMP` | Required except reported date/currency |

Key-metrics and growth empty arrays are treated as unavailable optional metric
groups. Ratios is the core metric group; an empty ratios array is controlled
`DATA_UNAVAILABLE`. Any non-empty response must match the requested symbol.
Malformed payloads and HTTP failures use existing sanitized provider errors.

Historical full EOD mapping:

| Normalized field | Exact FMP field |
|---|---|
| ticker | `symbol` |
| trading date | `date` |
| open/high/low/close | `open` / `high` / `low` / `close` |
| adjusted close | unavailable in this endpoint; null |
| volume | `volume` |
| currency | profile fallback only |
| provider/retrieval | `FMP` / application UTC Clock |

The client sends `symbol`, optional `from`, `to`, then `apikey`. It validates
required date/close, positive available prices, nonnegative volume, ticker
consistency, persistence precision, inclusive bounds, duplicate dates, and
ascending normalized order.

### API: Metrics

```http
GET /api/v1/stocks/{ticker}/metrics
```

```json
{
  "ticker": "AAPL",
  "metrics": [
    {
      "code": "PE_TTM",
      "displayName": "P/E (TTM)",
      "category": "VALUATION",
      "unit": "RATIO",
      "comparisonStrategy": "CONTEXT_DEPENDENT",
      "value": 32.889608822880916,
      "description": "Trailing price relative to trailing earnings."
    }
  ],
  "reportedAt": "2024-09-28",
  "retrievedAt": "2026-07-18T20:00:00Z",
  "providerName": "FMP",
  "warnings": [
    {"metricCode": "FORWARD_PE", "message": "Metric is unavailable from the configured provider response."}
  ]
}
```

All approved registry definitions are returned in stable enum order. A missing
optional value is JSON null and has one warning; no winner is calculated.

### API: History

```http
GET /api/v1/stocks/{ticker}/history?period=1Y
```

`period` defaults to `1Y`. It accepts only `1M`, `6M`, `1Y`, `5Y`, `MAX`
case-insensitively after trimming.

```json
{
  "ticker": "AAPL",
  "period": "1Y",
  "startDate": "2025-07-18",
  "endDate": "2026-07-18",
  "pricePoints": [
    {
      "date": "2026-07-17",
      "open": 210.00,
      "high": 212.00,
      "low": 209.00,
      "close": 211.00,
      "adjustedClose": null,
      "volume": 1000000,
      "currency": "USD"
    }
  ],
  "returnPercent": 12.3457,
  "dataSource": "FMP",
  "retrievedAt": "2026-07-18T20:00:00Z"
}
```

Non-MAX start/end are selected UTC date boundaries. MAX start is the first
returned trading date; its end is the UTC calculation date. Points are ascending.
Empty provider history returns controlled `DATA_UNAVAILABLE`; a one-point series
returns zero; zero starting value returns null defensively.

Invalid period adds `INVALID_PERIOD` / HTTP 400 to the existing envelope. Other
errors retain Milestone 2 codes.

### Database

`V3__create_financial_metric_snapshot_table.sql`:

- identity primary key and required `company_id` FK with restricted delete;
- optional metric columns: ratios/fractions `NUMERIC(30,12)`, revenue
  `NUMERIC(30,2)`;
- nullable `currency VARCHAR(3)`, nullable `reported_at DATE`;
- required `retrieved_at TIMESTAMPTZ`, `provider_name VARCHAR(64)`;
- nullable `raw_data_json JSONB` (not populated);
- currency and nonblank-provider checks.

`V4__create_historical_price_table.sql`:

- identity primary key and required Company FK with restricted delete;
- required `trading_date DATE`, `close_price NUMERIC(30,8)`, provider, retrieval;
- nullable open/high/low/adjusted prices, volume `BIGINT`, and currency;
- positive-price, nonnegative-volume, currency, and provider checks;
- unique `(company_id, trading_date, provider_name)`.

No V7 performance index is consumed early.

### Configuration / Dependencies

- Reuse `FMP_API_KEY`, base URL, timeouts, and max attempts.
- No new environment variable and no new production dependency.
- Missing key continues to permit context, compilation, tests, and CI; provider
  calls return controlled `DATA_UNAVAILABLE`.

## 9. Implementation Plan

### Phase 1: Freeze provider contract and plan

1. Inspect official stable docs/API Viewer and record exact mappings,
   percentage convention, adjusted-price decision, access limits, and nulls.
2. Inspect all existing production/tests and mark this plan Ready.

Validation: plan review; no unresolved architecture/dependency blocker.

### Phase 2: Financial metric persistence

1. Add V3, entity, repository latest query, and snapshot service.
2. Test exact decimals, nullable values, FK/checks, append behavior, and latest
   selection in PostgreSQL Testcontainers.

Validation:
`cd backend && ./mvnw -Dtest=FinancialMetricSnapshotRepositoryIntegrationTest,FinancialMetricSnapshotServiceTest test`

### Phase 3: Historical persistence, periods, and returns

1. Add V4, entity, repository queries, and transactional date/provider upsert.
2. Add PricePeriod/date ranges and BigDecimal return calculator.
3. Test constraints, idempotency, ordering/ranges, all periods, and all return
   edge cases.

Validation:
`cd backend && ./mvnw -Dtest=HistoricalPriceRepositoryIntegrationTest,HistoricalPriceServiceTest,PricePeriodTest,HistoricalReturnCalculatorTest test`

### Phase 4: FMP metrics and history adapter

1. Extend provider-neutral interface/records.
2. Add minimal schema-faithful FMP DTOs and mapper methods.
3. Extend generic FMP GET behavior for metric aggregation and historical date
   parameters without changing credential/error/retry policy.
4. Add sanitized fixtures, mapper tests, and exact mock HTTP tests.

Validation:
`cd backend && ./mvnw -Dtest=FmpResponseMapperTest,FmpFinancialDataClientTest test`

### Phase 5: Query services, registry, and APIs

1. Add financial/history query orchestration using existing ticker/profile and
   Company upsert behavior with network calls outside persistence transactions.
2. Add the registry and map snapshot values into stable metric definitions.
3. Add DTOs/controller and INVALID_PERIOD handling.
4. Unit/integration/controller test normalization, success, optional warnings,
   provider/error branches, invalid period, ascending points, and persistence.

Validation:
`cd backend && ./mvnw -Dtest='*FinancialMetrics*Test,*HistoricalPrice*Test,FinancialControllerTest' test`

### Phase 6: Full validation and completion

1. Update infrastructure migration/table assertions and README smoke commands.
2. Run clean verification with Testcontainers and no FMP credential.
3. Start credential-free backend for health/invalid-input checks when local
   PostgreSQL/Redis are available; live FMP calls remain optional/manual.
4. Review complete diff, secrets, generated files, provider leakage, scope, and
   every acceptance criterion; fix confirmed Milestone 3 issues only.
5. Record evidence and mark Completed only after required checks pass.

Validation:

- `docker compose config`
- `cd backend && ./mvnw clean verify`
- `docker compose up -d`
- `docker compose ps`
- `cd backend && ./mvnw spring-boot:run`
- credential-free health and invalid ticker/period curls
- `git diff --check`
- `git status --short --untracked-files=all`

## 10. Testing Strategy

### Unit / Mapper Tests

- Metric mapping: all exact fields, aggregation, precision, decimal-fraction
  convention, optional nulls, symbol/date/period validation, empty groups, and
  malformed/range-invalid values.
- History mapping: root array, exact prices/volume, null adjusted close,
  ascending order, duplicate conflict, missing date/close, bounds, and empty.
- Periods/returns: five boundaries with fixed Clock; positive, negative, zero,
  one, empty, zero-start, adjusted preference, precision/rounding.
- Registry: complete codes, unique definitions, categories/units/strategies,
  and context-dependent metrics remain neutral.
- Services: fetch-before-write order, profile currency fallback, append metrics,
  history idempotence, errors do not write target data.

### PostgreSQL Testcontainers

- Flyway 0–4 and exact four domain tables; Hibernate `validate`.
- V3/V4 FK/check/unique constraints and exact decimals/dates.
- Latest FinancialMetricSnapshot by retrieval time/ID.
- Historical repeated retrieval updates without duplicates, date-range queries,
  and ascending order.
- Fake-provider integration proves lowercase ticker normalization, Company
  creation, metric snapshot append, history persistence, and return output.

### Mock HTTP / Controller

- Exact stable paths/query parameters/key handling; no live URL.
- Success for all four new endpoints/groups, partial optional groups, empty
  ratios/history, 401/429/5xx/timeout/malformed responses, and no raw body/key.
- Metrics/history response shapes and absence of provider/entity/raw fields.
- Invalid ticker/period, unknown stock, unavailable data, rate limit, provider
  failure, and consistent request ID/error envelope.

### Manual

- Credential-free context and Actuator health.
- Invalid ticker and period fail before external access.
- Optional user-run commands with environment-supplied key:
  `GET /api/v1/stocks/AAPL/metrics` and
  `GET /api/v1/stocks/AAPL/history?period=1Y`; never print the key.
- Inspect Company, metric snapshot, and unique historical price rows.

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Current FMP plan cannot access one endpoint | Live slice may be partial/unavailable | Preserve boundary, null optional key/growth groups, safe errors for core/history, document manual limitation |
| Similar provider fields have different units | Misleading API values | Exact mapping table and decimal-fraction tests; no name-based guesses |
| Forward P/E/revenue/beta are tempting to derive | Fabricated metrics | Persist/return null with explicit warnings |
| Growth date is mistaken for a precise timestamp | False precision | Store LocalDate and document meaning |
| Full history omits adjusted close | Misstated total return | Keep adjusted null and use close; do not switch endpoint silently |
| MAX exceeds 5000 provider rows | Incomplete lifetime range | Define MAX as provider-supported maximum and surface returned start date |
| Re-fetch duplicates historical rows | Unique failures/data growth | Transactional upsert by company/date/provider plus DB unique constraint |
| External decimals exceed schema | 500 or corruption | Validate precision/scale before persistence and return controlled unavailable data |
| New code leaks FMP types | Architecture violation | FMP-only DTO package, provider-neutral records, public DTO tests/scans |
| Milestone 5 logic leaks into registry | Premature dashboard/winner behavior | Registry contains semantics only; no pair comparison or series alignment |

## 12. Rollback / Recovery

- V3/V4 are additive. Before shared use, a disposable local database can be
  recreated only after confirming data-loss scope. Never edit applied migrations.
- Shared corrections use a separately approved forward migration; do not consume
  V5–V7 or renumber files.
- Older application code ignores additive tables but Flyway history remains.
- Historical upsert is repeatable after interrupted retrieval; unique keys
  prevent duplicate dates.
- Removing provider credentials stops new fetches and does not delete stored
  data. No stale-read API fallback is introduced in this milestone.
- Never remove volumes or user data as routine rollback.

## 13. Progress

- [x] Governing documents, Milestone 2 plan, backend, and tests read
- [x] Official FMP docs/API Viewer schema review complete
- [x] Plan reviewed and marked Ready
- [x] Status marked In Progress
- [x] Phase 2 complete — metric persistence
- [x] Phase 3 complete — history persistence, periods, returns
- [x] Phase 4 complete — FMP adapter and mock HTTP tests
- [x] Phase 5 complete — registry, query services, APIs, tests
- [x] Phase 6 complete — full validation and scope review
- [x] Acceptance criteria confirmed

## 14. Decision Log

| Date | Decision | Reason | Alternatives |
|---|---|---|---|
| 2026-07-18 | Store metric percentages as decimal fractions | Matches FMP stable payloads and design formatting example | Percentage points; mixed provider conventions |
| 2026-07-18 | Return selected-period performance in percentage points, rounded to 4 decimals | Matches the approved formula and separates calculation precision from API presentation | Decimal fraction; premature frontend formatting |
| 2026-07-18 | Use latest annual financial-growth row for growth/report date | Provides explicit YoY revenue/net-income growth and a real provider date | Mix quarterly/annual; derive TTM growth |
| 2026-07-18 | Keep forward P/E, revenue TTM, and beta null | Approved metric endpoints expose no reliable direct fields in inspected stable samples | Derive from PEG/per-share fields; add unrequested endpoints |
| 2026-07-18 | Keep adjusted close null and use close | Full stable EOD sample has no adjusted-close field; endpoint change requires an explicit decision | Copy close; silently use dividend-adjusted endpoint |
| 2026-07-18 | Define MAX as provider-supported maximum | FMP documents a 5000-record cap | Claim unlimited lifetime coverage |
| 2026-07-18 | Upsert historical rows and append metric snapshots | Matches approved uniqueness and snapshot models | Append duplicate prices; overwrite metric history |
| 2026-07-18 | Add no dependency/config variable | Existing Spring/JDK/JPA test stack is sufficient | Provider SDK, resilience library, mapping library |
| 2026-07-18 | Round provider metric decimals half-up to scale 12 | Real FMP ratios can exceed the approved NUMERIC(30,12) fractional scale; deterministic boundary normalization preserves usable precision | Reject valid provider values; widen the completed migration |

## 15. Deviations from Design

- None.
- The design leaves SQL types and API body details open; LocalDate reported data,
  precision, warnings, and response shapes are implementation clarifications.
- Missing optional provider fields are an explicit provider limitation, not a
  schema deviation.

## 16. Validation Evidence

| Command | Result | Notes |
|---|---|---|
| Governing/current-state review | PASS | Required documents and complete backend/tests read on 2026-07-18 |
| Official FMP docs/API Viewer review | PASS | Array roots and exact fields confirmed; endpoints marked Limited Access |
| Focused phase tests | PASS | 27 focused tests, then 3 focused PostgreSQL tests after correcting a test identity assertion |
| `cd backend && ./mvnw clean verify` | PASS | 61 tests, 0 failures/errors/skips; JAR packaged; Testcontainers ran PostgreSQL and Redis; includes high-precision FMP metric regression |
| Local startup/curls | PASS | Compose services healthy; Flyway advanced local schema to V4; Actuator UP; invalid ticker/period returned expected 400 envelopes |
| Diff/security/scope review | PASS | `git diff --check`, Compose config, status, secret scan, generated-file scan, provider-boundary and Milestone 4 scope review passed |

## 17. Completion Summary

Milestone 3 implementation and validation completed on 2026-07-18.

### Implemented

- Added Flyway V3/V4, JPA entities, repositories, and PostgreSQL constraints for
  financial metric snapshots and historical daily prices.
- Extended the provider-neutral financial client and FMP adapter with stable
  ratios, key metrics, annual growth, and full EOD history mappings.
- Added metric definitions, safe comparison semantics, period parsing, exact
  return calculation, query services, persistence behavior, and two normalized
  stock endpoints with consistent errors and warnings.
- Documented local metric/history requests and provider limitations.

### Files Changed

- Backend migrations, financial domain/repository/service/controller/DTO classes,
  FMP DTOs/client/mapper, common invalid-period handling, README, and tests/fixtures.
- No frontend, design-document, dependency, configuration-variable, or Milestone
  4 implementation changes.

### Tests Added or Updated

- Added/updated unit tests for periods, returns, metric registry, query service,
  FMP mapping/client, and controller contracts.
- Added PostgreSQL/Redis Testcontainers coverage for migrations, constraints,
  latest snapshots, ordered/ranged history, idempotent upserts, and fake-provider
  end-to-end service behavior.
- Final suite: 61 passing tests.

### Known Limitations

- Endpoint access depends on the user's FMP subscription.
- Forward P/E, revenue TTM, beta, and adjusted close are unavailable from the
  approved inspected response schemas and will remain null.
- MAX is capped by provider support.

### Remaining Work

- Milestone 3 has no remaining plan items. Milestone 4 remains unstarted.
