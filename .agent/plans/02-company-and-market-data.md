# Company and Market Data

**Status:** Completed
**Milestone:** 2 — Company and Market Data
**Created:** 2026-07-18
**Last Updated:** 2026-07-18
**Design References:** `docs/design.md` Sections 4.1, 8 (FR-1 through FR-3),
9.1, 9.3–9.5, 11, 13.3–13.5, 14, 15.1–15.2, 16.4, 18–19,
22–25, 28 (Milestone 2), and 29

## 1. Goal

Implement the first provider-backed stock-data slice of StockLens AI: normalize
and validate one ticker, load or persist its company profile and latest market
snapshot in PostgreSQL, and expose that normalized data through the supporting
`GET /api/v1/stocks/{ticker}` endpoint with consistent errors.

The milestone must preserve the modular-monolith boundaries established by the
design. Provider response objects stay inside one selected provider adapter;
services consume provider-neutral records; JPA entities remain persistence
models; controllers return application API DTOs. Automated tests must use
PostgreSQL Testcontainers and mock HTTP responses, never a live or paid API.

Financial Modeling Prep (FMP) is the selected provider. Milestone 2 uses only
the current `https://financialmodelingprep.com/stable/profile` and
`https://financialmodelingprep.com/stable/quote` endpoints. The provider gate
has been resolved and the milestone implementation is complete.

## 2. Background and Current State

Milestone 1 is complete. Repository inspection on 2026-07-18 found a clean
worktree and the following relevant implementation:

- `backend/pom.xml` uses Java 21 and Spring Boot `4.1.0`. It already includes
  WebMVC/Jackson, JPA, PostgreSQL, Redis, Flyway, Actuator, Spring test support,
  Mockito through the test starters, and PostgreSQL Testcontainers.
- `backend/src/main/java/com/stocklens/StockLensApplication.java` is the only
  production Java class. No feature packages, entities, repositories, services,
  controllers, provider adapters, or API error types exist.
- `backend/src/main/resources/application.yml` points Flyway at
  `classpath:db/migration`, uses PostgreSQL-compatible local defaults, and sets
  `spring.jpa.hibernate.ddl-auto=validate`.
- `V0__baseline.sql` is the only migration. It contains comments only and
  deliberately reserves `V1__create_company_table.sql` for this milestone.
- `IntegrationTestContainers` supplies pinned PostgreSQL 18.4 and Redis 8.8
  service connections. `StockLensApplicationTests` verifies context startup.
- `InfrastructureIntegrationTest` currently asserts that Flyway version `0` is
  the only migration and that no domain tables exist. That assertion must be
  updated when `company` and `market_snapshot` are intentionally introduced;
  its PostgreSQL, Redis, and Actuator checks must remain intact.
- Before this milestone, `.env.example` contained a blank generic provider-key
  placeholder but no chosen provider, base URL, timeout, or provider-specific
  configuration.
- `.github/workflows/ci.yml` already runs `./mvnw verify` with Java 21 and a
  Docker-capable hosted runner. It supplies no provider credentials, which must
  remain true.
- The frontend is a Milestone 1 placeholder and is not changed by this plan.

The design fixes the normalized Company and MarketSnapshot concepts, migration
sequence, ticker regex, provider abstraction, supporting stock path, error
envelope, and testing approach. It does not select the financial provider or
fully specify the response body of `GET /api/v1/stocks/{ticker}`. This plan
proposes the missing API detail without changing the primary comparison API.

## 3. Scope

### In Scope

- Normalize single ticker inputs by trimming and uppercasing with
  `Locale.ROOT`, then validate them against
  `^[A-Z][A-Z0-9.-]{0,9}$`.
- Add the Company JPA entity, repository, service, and
  `V1__create_company_table.sql`.
- Add the MarketSnapshot JPA entity, repository, latest-snapshot query, service,
  and `V2__create_market_snapshot_table.sql`.
- Define the provider-neutral `FinancialDataClient` boundary for company profile
  and market snapshot retrieval only.
- Add an FMP adapter with provider-specific
  profile/quote DTOs, HTTP client, configuration, response mapper, validation,
  sanitized error translation, bounded retry behavior, and mock HTTP tests.
- Normalize provider values into provider-neutral records before any service or
  entity sees them.
- For every valid supporting-stock request, fetch the current FMP profile and
  quote, upsert the durable Company row, and append one MarketSnapshot. This is
  direct resource loading, not Redis caching, staleness policy, a refresh
  endpoint, or background refresh orchestration.
- Expose `GET /api/v1/stocks/{ticker}` returning normalized company data and the
  latest stored market snapshot.
- Add request IDs and a consistent error envelope for ticker, not-found,
  provider, rate-limit, unavailable-data, and unexpected-error cases.
- Add focused unit, controller, PostgreSQL Testcontainers, service integration,
  and mock HTTP provider-client tests.
- Document the selected provider's required environment variables and optional
  manual smoke-test setup after selection, without committing credentials.

### Out of Scope

- `FinancialMetricSnapshot`, its migration, normalization, service, or endpoint.
- `HistoricalPrice`, historical provider calls, series alignment, or return
  calculations.
- News providers, articles, company-news relationships, or news endpoints.
- `GET /api/v1/comparisons`, comparison orchestration, dashboard DTOs, metric
  groups, partial dashboard warnings, or comparison caching.
- AI, Spring AI, OpenAI calls, prompts, schemas, persistence, or tests.
- Redis business caching, cache keys/TTLs, invalidation, fallback policy, or any
  cache-aside implementation. Existing Redis connectivity remains unchanged.
- Refresh endpoints, staleness thresholds, background jobs, or any refresh
  orchestration.
- Frontend API integration, search form, company cards, dashboard layout, or any
  other frontend implementation.
- Authentication, users, watchlists, CORS production policy, or authorization.
- OpenAPI/Swagger dependencies or documentation; the design assigns full API
  documentation to Milestone 9.
- Deployment configuration, cloud services, publish/release jobs, or secrets in
  GitHub Actions.
- Migrations `V3` through `V7`; their design-defined purposes remain reserved.

## 4. Assumptions

- FMP is the selected provider. Stable profile and quote responses use a JSON
  array root; an empty array represents no usable symbol result.
- FMP terms require an appropriate display/licensing agreement for data shown
  in a multi-user application. Implementation and local automated verification
  may proceed, but public deployment remains subject to the user's applicable
  FMP subscription and license.
- `raw_data_json` remains nullable for design compatibility and is not populated
  in Milestone 2. Only normalized fields are persisted.
- The existing Spring WebMVC stack supplies `RestClient`, Jackson, JDK HTTP
  facilities, and Spring mock HTTP test support. The default approach adds no
  new production dependency or provider SDK.
- If the provider requires an SDK, resilience library, HTML sanitizer, or other
  production dependency, implementation pauses for approval as required by
  `AGENTS.md`; the plan must be updated with purpose, version source, and impact.
- `GET /api/v1/stocks/{ticker}` is the only new public resource endpoint in this
  milestone. It combines profile and latest-market data because the design lists
  that path but does not approve a separate quote path.
- PostgreSQL remains the durable source of truth for accepted data. Each valid
  request obtains both FMP responses before persistence, updates the single
  Company profile row, and creates a new MarketSnapshot. Provider failures do
  not write a partial profile/snapshot pair.
- Company `updated_at` represents the most recent persisted profile retrieval in
  this single-row profile model. Both timestamps are set from an injected UTC
  `Clock`, not trusted provider time.
- Monetary and market numeric fields use `BigDecimal` end to end and are
  serialized as JSON numbers, never formatted strings.
- Provider keys are optional for application context startup and CI. A
  provider-backed request without configuration returns a controlled
  `DATA_UNAVAILABLE` response. Persisted rows remain durable, but this
  milestone intentionally adds no stale-read fallback endpoint.
- No live-provider smoke test is required for default CI or plan completion. It
  may be run manually only after the user supplies credentials through the
  environment.

## 5. Open Questions / Blockers

- None for local Milestone 2 implementation.
- Public display or deployment remains conditional on an FMP subscription and
  data-display license that permits the intended audience and persistence.
- Market status is deferred because the approved MarketSnapshot model omits it
  and the selected FMP profile/quote endpoints do not supply a directly mapped
  status field. Currency is nullable because the stable quote schema does not
  guarantee it; it is copied from the profile only when FMP supplies one.

## 6. Acceptance Criteria

- [x] FMP selection, stable endpoints, nullable currency, deferred market
  status, and no-raw-response policy are documented.
- [x] `V0__baseline.sql` remains unchanged; Flyway applies
  `V1__create_company_table.sql` and `V2__create_market_snapshot_table.sql` in
  order, and no `V3` or later migration is added.
- [x] Company fields, JPA mapping, database checks, unique ticker constraint,
  and repository lookup match Section 8 of this plan.
- [x] MarketSnapshot fields use `BigDecimal`/`Instant`, preserve provider and
  retrieval provenance, and reference Company without cascading company
  deletion.
- [x] The repository deterministically returns the latest snapshot by quote
  timestamp, retrieval timestamp, and ID.
- [x] Ticker input is trimmed, normalized with `Locale.ROOT`, and validated
  against the exact design regex before repository or provider access.
- [x] Provider-specific DTOs and raw responses remain inside the selected
  adapter package; controllers, services, entities, and API DTOs never expose
  them.
- [x] The provider adapter validates required fields, maps optional values to
  `null`, uses exact decimal parsing, configures timeouts, retries only bounded
  transient idempotent failures, detects rate limits, and sanitizes errors/logs.
- [x] Each valid request fetches both FMP resources, upserts Company, appends a
  MarketSnapshot, and implements no refresh endpoint or Redis caching policy.
- [x] `GET /api/v1/stocks/{ticker}` returns the approved normalized contract and
  no metrics, history, news, comparison, AI, or cache data.
- [x] Every API failure uses the consistent error envelope, UTC timestamp,
  request path, request ID, stable code, and sanitized message defined here.
- [x] Unit tests cover ticker edge cases, provider normalization, service
  repository/provider branches, malformed/empty data, and exception mapping.
- [x] PostgreSQL Testcontainers tests verify both migrations, JPA mappings,
  unique/check/foreign-key constraints, and the latest-snapshot query.
- [x] Mock HTTP tests cover successful profile/quote responses, unknown ticker,
  rate limit, transient failure/retry, timeout, malformed JSON, mismatched
  symbol, missing required fields, and optional missing fields without a live
  provider or credentials.
- [x] Existing context, Redis connectivity, Actuator, frontend, and CI behavior
  remain valid; backend startup and tests require no provider key.
- [x] The Maven test phase and `./mvnw clean verify` pass, the complete diff is
  reviewed, and
  no secrets, generated output, unrelated changes, or later-milestone work is
  introduced.

## 7. Expected Files

This list records the actual files created or modified for the completed
implementation.

### Create

Common validation, time, and API errors:

- `backend/src/main/java/com/stocklens/common/validation/TickerNormalizer.java`
- `backend/src/main/java/com/stocklens/common/time/TimeConfiguration.java`
- `backend/src/main/java/com/stocklens/common/exception/InvalidTickerException.java`
- `backend/src/main/java/com/stocklens/common/exception/StockNotFoundException.java`
- `backend/src/main/java/com/stocklens/common/exception/FinancialProviderException.java`
- `backend/src/main/java/com/stocklens/common/exception/FinancialProviderRateLimitedException.java`
- `backend/src/main/java/com/stocklens/common/exception/DataUnavailableException.java`
- `backend/src/main/java/com/stocklens/common/response/ApiErrorDetail.java`
- `backend/src/main/java/com/stocklens/common/response/ApiErrorResponse.java`
- `backend/src/main/java/com/stocklens/common/web/RequestIdFilter.java`
- `backend/src/main/java/com/stocklens/common/web/GlobalExceptionHandler.java`

Company feature:

- `backend/src/main/java/com/stocklens/company/domain/Company.java`
- `backend/src/main/java/com/stocklens/company/repository/CompanyRepository.java`
- `backend/src/main/java/com/stocklens/company/service/CompanyService.java`
- `backend/src/main/java/com/stocklens/company/service/StockQueryService.java`
- `backend/src/main/java/com/stocklens/company/controller/StockController.java`
- `backend/src/main/java/com/stocklens/company/dto/CompanyResponse.java`
- `backend/src/main/java/com/stocklens/company/dto/StockResponse.java`
- `backend/src/main/java/com/stocklens/company/dto/StockResponseMapper.java`

Market feature and provider-neutral boundary:

- `backend/src/main/java/com/stocklens/market/domain/MarketSnapshot.java`
- `backend/src/main/java/com/stocklens/market/repository/MarketSnapshotRepository.java`
- `backend/src/main/java/com/stocklens/market/service/MarketSnapshotService.java`
- `backend/src/main/java/com/stocklens/market/dto/MarketSnapshotResponse.java`
- `backend/src/main/java/com/stocklens/market/client/FinancialDataClient.java`
- `backend/src/main/java/com/stocklens/market/client/model/CompanyProfileData.java`
- `backend/src/main/java/com/stocklens/market/client/model/MarketSnapshotData.java`

FMP provider adapter:

- `backend/src/main/java/com/stocklens/market/client/fmp/FmpFinancialDataClient.java`
- `backend/src/main/java/com/stocklens/market/client/fmp/FmpProperties.java`
- `backend/src/main/java/com/stocklens/market/client/fmp/FmpClientConfiguration.java`
- `backend/src/main/java/com/stocklens/market/client/fmp/FmpResponseMapper.java`
- `backend/src/main/java/com/stocklens/market/client/fmp/dto/FmpCompanyProfileResponse.java`
- `backend/src/main/java/com/stocklens/market/client/fmp/dto/FmpQuoteResponse.java`

Migrations:

- `backend/src/main/resources/db/migration/V1__create_company_table.sql`
- `backend/src/main/resources/db/migration/V2__create_market_snapshot_table.sql`

Tests and fixtures:

- `backend/src/test/java/com/stocklens/common/validation/TickerNormalizerTest.java`
- `backend/src/test/java/com/stocklens/company/repository/CompanyRepositoryIntegrationTest.java`
- `backend/src/test/java/com/stocklens/company/service/CompanyServiceTest.java`
- `backend/src/test/java/com/stocklens/company/service/StockQueryServiceIntegrationTest.java`
- `backend/src/test/java/com/stocklens/company/service/StockQueryServiceTest.java`
- `backend/src/test/java/com/stocklens/company/controller/StockControllerTest.java`
- `backend/src/test/java/com/stocklens/market/repository/MarketSnapshotRepositoryIntegrationTest.java`
- `backend/src/test/java/com/stocklens/market/service/MarketSnapshotServiceTest.java`
- `backend/src/test/java/com/stocklens/market/client/fmp/FmpResponseMapperTest.java`
- `backend/src/test/java/com/stocklens/market/client/fmp/FmpFinancialDataClientTest.java`
- `backend/src/test/resources/fixtures/fmp/company-profile-success.json`
- `backend/src/test/resources/fixtures/fmp/quote-success.json`
- `backend/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`

### Modify

- `backend/src/main/resources/application.yml` — selected provider properties,
  safe timeouts, and blank-key startup behavior.
- `.env.example` — document selected provider configuration with blank secrets.
- `README.md` — document provider setup and the supporting endpoint after the
  provider is selected; do not turn this into Milestone 9 documentation work.
- `backend/src/test/java/com/stocklens/InfrastructureIntegrationTest.java` —
  replace the obsolete no-domain-table assertion with exact V0/V1/V2 migration
  and expected-table assertions while retaining infrastructure checks.
- `.agent/plans/02-company-and-market-data.md` — maintain progress, decisions,
  deviations, actual files, and validation evidence throughout implementation.
- `backend/pom.xml` only if a new dependency becomes demonstrably necessary and
  the user approves it; the planned default is no change.

### Delete

- None.

Do not modify `docs/design.md`, frontend source, Docker Compose services, or
deployment files in this milestone.

## 8. API / Schema / Configuration Changes

### API

#### Get normalized stock data

```http
GET /api/v1/stocks/{ticker}
```

Request rules:

- No body or query parameters.
- Trim the raw path value, uppercase with `Locale.ROOT`, then validate the exact
  regex `^[A-Z][A-Z0-9.-]{0,9}$`.
- Reject null/blank, overlength, leading digits, spaces inside the symbol, and
  unsupported characters before repository/provider access.

Proposed HTTP 200 response:

```json
{
  "company": {
    "ticker": "AAPL",
    "name": "Apple Inc.",
    "exchange": "NASDAQ",
    "sector": "Technology",
    "industry": "Consumer Electronics",
    "country": "US",
    "websiteUrl": "https://www.apple.com",
    "description": "Apple designs and markets consumer technology products.",
    "logoUrl": null,
    "profileUpdatedAt": "2026-07-18T22:40:00Z"
  },
  "latestMarketSnapshot": {
    "price": 192.62,
    "priceChange": 1.35,
    "priceChangePercent": 0.71,
    "marketCap": 2960000000000.00,
    "currency": "USD",
    "quoteTimestamp": "2026-07-18T20:00:00Z",
    "retrievedAt": "2026-07-18T22:40:00Z",
    "providerName": "FMP"
  }
}
```

Contract rules:

- Optional profile fields and optional quote change/market-cap values serialize
  as JSON `null`; do not fabricate zero, empty text, currency, or timestamps.
- `price`, `quoteTimestamp`, `retrievedAt`, and `providerName` are required for
  a successful response. `currency` is nullable and never inferred.
- `marketStatus` is deferred and is not persisted or exposed in Milestone 2.
- Do not expose database IDs, `provider_symbol`, raw JSON, provider response
  objects, cache flags, metrics, history, news, or AI fields.
- Numeric values remain JSON numbers. Display formatting belongs to the
  frontend.
- This endpoint has no partial-success warning contract. If no usable current
  snapshot can be loaded or filled, return the appropriate controlled error.

No `/metrics`, `/history`, `/news`, `/comparisons`, or `/refresh` endpoint is
created by this plan.

#### Error contract

Every error response uses:

```json
{
  "code": "INVALID_TICKER",
  "message": "Ticker must match ^[A-Z][A-Z0-9.-]{0,9}$.",
  "timestamp": "2026-07-18T22:45:00Z",
  "path": "/api/v1/stocks/INVALID_TICKER",
  "requestId": "abc-123",
  "details": []
}
```

The request filter accepts a safe inbound request ID or generates a UUID,
places it in the response header and request context, and removes it after the
request. It must not log credentials or raw provider bodies.

| Condition | Code | HTTP | Behavior |
|---|---|---:|---|
| Ticker invalid after normalization | `INVALID_TICKER` | 400 | No repository/provider call |
| Provider explicitly reports unknown symbol | `STOCK_NOT_FOUND` | 404 | No raw provider text returned |
| Provider authentication, timeout, malformed response, or terminal upstream failure | `FINANCIAL_PROVIDER_ERROR` | 502 | Sanitized stable message; cause logged without secret/body |
| Provider rate limit | `RATE_LIMITED` | 429 | Preserve safe `Retry-After` only when available |
| Provider is unconfigured or a successful response has no usable required data | `DATA_UNAVAILABLE` | 503 | No stale-read fallback is introduced in this milestone |
| Unexpected application failure | `INTERNAL_ERROR` | 500 | Generic client message, request ID retained |

`DUPLICATE_TICKERS` is not used by a single-stock endpoint and remains for the
comparison milestone. News and AI error codes remain unimplemented.

### Database

Migration order:

1. Keep `V0__baseline.sql` unchanged.
2. Add `V1__create_company_table.sql`.
3. Add `V2__create_market_snapshot_table.sql`.
4. Reserve `V3__create_financial_metric_snapshot_table.sql` through
   `V7__add_indexes_and_constraints.sql` for their design milestones.

An existing Milestone 1 database upgrades from version 0 to 2. A clean database
applies all three migrations. Flyway owns DDL; Hibernate continues to validate.

#### `company`

| Column | SQL type | Null | Constraint / meaning |
|---|---|---:|---|
| `id` | `BIGINT GENERATED BY DEFAULT AS IDENTITY` | No | Primary key |
| `ticker` | `VARCHAR(10)` | No | Unique; uppercase design regex check |
| `name` | `VARCHAR(255)` | No | Trimmed, nonblank check |
| `exchange` | `VARCHAR(64)` | Yes | Blank provider values normalized to null |
| `sector` | `VARCHAR(128)` | Yes | Blank normalized to null |
| `industry` | `VARCHAR(128)` | Yes | Blank normalized to null |
| `country` | `VARCHAR(128)` | Yes | Blank normalized to null |
| `website_url` | `VARCHAR(2048)` | Yes | Only accepted HTTP(S) URI or null |
| `description` | `TEXT` | Yes | Plain text; provider HTML policy resolved with provider |
| `logo_url` | `VARCHAR(2048)` | Yes | Only accepted HTTP(S) URI or null |
| `provider_symbol` | `VARCHAR(64)` | No | Selected provider's normalized symbol/identifier |
| `created_at` | `TIMESTAMPTZ` | No | UTC application timestamp |
| `updated_at` | `TIMESTAMPTZ` | No | UTC profile retrieval/update timestamp; not before creation |

Required named checks/constraints:

- primary key on `id`;
- unique constraint on `ticker`;
- PostgreSQL regex check equivalent to `^[A-Z][A-Z0-9.-]{0,9}$`;
- nonblank checks for `name` and `provider_symbol`;
- `updated_at >= created_at`.

The entity uses `Long`, `String`, and `Instant`, maps exact column lengths, and
does not cascade persistence to snapshots.

#### `market_snapshot`

| Column | SQL type | Null | Constraint / meaning |
|---|---|---:|---|
| `id` | `BIGINT GENERATED BY DEFAULT AS IDENTITY` | No | Primary key |
| `company_id` | `BIGINT` | No | Foreign key to `company(id)`, delete restricted |
| `price` | `NUMERIC(30,8)` | No | `BigDecimal`, strictly positive |
| `price_change` | `NUMERIC(30,8)` | Yes | Signed exact decimal |
| `price_change_percent` | `NUMERIC(19,6)` | Yes | Signed percent value as supplied/normalized |
| `market_cap` | `NUMERIC(30,2)` | Yes | Nonnegative exact decimal |
| `currency` | `VARCHAR(3)` | Yes | Uppercase three-letter check when present |
| `quote_timestamp` | `TIMESTAMPTZ` | No | Provider quote/latest-close time |
| `retrieved_at` | `TIMESTAMPTZ` | No | UTC application receipt time |
| `provider_name` | `VARCHAR(64)` | No | Nonblank selected provider name |
| `raw_data_json` | `JSONB` | Yes | Sanitized raw response only when storage terms permit |

Repeated retrievals may legitimately share a quote timestamp, so this plan does
not impose a uniqueness constraint that would erase snapshot history. The
repository method is:

```text
findFirstByCompany_IdOrderByQuoteTimestampDescRetrievedAtDescIdDesc
```

The deterministic three-part ordering is covered by PostgreSQL integration
tests. Performance indexes are reserved for design migration `V7` unless
measured behavior requires an earlier approved change; do not consume `V7` now.

### Configuration and Environment

Define the typed prefix `stocklens.providers.fmp` with:

| Setting | Environment source | Rule |
|---|---|---|
| API key | `FMP_API_KEY` | Blank in `.env.example`; never logged |
| Base URL | `FMP_BASE_URL` | Default `https://financialmodelingprep.com/stable` |
| Connect timeout | `FMP_CONNECT_TIMEOUT` | Default `2s` |
| Read timeout | `FMP_READ_TIMEOUT` | Default `5s` |
| Max attempts | `FMP_MAX_ATTEMPTS` | Default two total attempts; no unbounded retry |

Retry only idempotent profile/quote GETs on transient I/O and selected 5xx
responses. Do not retry invalid symbols, authentication failures, malformed
payloads, or rate limits unless the provider explicitly supplies an allowed
retry contract. Tests must avoid real sleeping by injecting or bypassing delay.

Provider configuration must not prevent application context startup when the
key is blank. It is validated at the provider-call boundary so CI remains
credential-free.

### Dependencies

No new production dependency is planned:

- Spring `RestClient` and Jackson come from the existing WebMVC starter.
- JPA/PostgreSQL/Flyway are already present.
- Spring test/mock HTTP, JUnit, AssertJ, and Mockito support are already present
  through the current test starters.
- PostgreSQL Testcontainers is already configured.

Do not add a provider SDK, Resilience4j, Spring Retry, Bean Validation starter,
HTML sanitizer, Lombok, MapStruct, H2, WireMock, or MockWebServer without a
demonstrated need and explicit production-dependency approval where applicable.

## 9. Implementation Plan

### Phase 1: Resolve and freeze the FMP provider contract

**Purpose**

- Remove the only external integration blocker before implementation begins.

**Steps**

1. Record FMP as the selected provider using official documentation.
2. Record endpoint/authentication/error/rate-limit/terms/field decisions in
   Section 5 and the Decision Log.
3. Defer market status, keep currency nullable, and do not store raw JSON.
4. Replace provider placeholders with FMP names and configuration.
5. Confirm the existing RestClient stack is enough; pause for approval if a new
   production dependency is proposed.
6. Review the API response and schema with the resolved provider field set, then
   mark the plan `Ready` before code is written.

**Validation**

- Plan review only.
- Expected: no unresolved provider/schema placeholder remains and the plan can
  be marked `Ready` without changing `docs/design.md`.

### Phase 2: Add ticker normalization and the common error foundation

**Purpose**

- Establish one reusable validation rule and stable exception vocabulary before
  repository, provider, or controller code depends on it.

**Steps**

1. Implement trim, `Locale.ROOT` uppercase, blank rejection, and exact regex
   validation in `TickerNormalizer`.
2. Add typed exceptions for invalid ticker, stock not found, financial-provider
   failure/rate limit, and unavailable data without HTTP logic in services.
3. Add a UTC `Clock` bean for deterministic retrieval and error timestamps.
4. Unit-test valid dot/dash/numeric suffixes and invalid null, blank, whitespace,
   leading digit, overlength, lowercase-after-normalization, and special cases.

**Validation**

- `cd backend && ./mvnw -Dtest=TickerNormalizerTest test`
- Expected: normalization examples such as `" aapl " -> "AAPL"` pass and every
  invalid form produces `INVALID_TICKER` before downstream calls.

### Phase 3: Add Company migration, entity, repository, and persistence tests

**Purpose**

- Introduce the durable normalized company profile independently of provider
  transport details.

**Steps**

1. Add immutable `V1__create_company_table.sql` with the exact table and
   constraints from Section 8.
2. Map `Company` to the Flyway-owned schema using explicit column names,
   lengths, nullability, and `Instant` timestamps; add no provider DTO fields.
3. Add `CompanyRepository.findByTicker`.
4. Add PostgreSQL Testcontainers coverage for Flyway V1, JPA save/load, unique
   and regex constraints, optional values, and timestamp preservation.
5. Do not add company update/refresh behavior yet.

**Validation**

- `cd backend && ./mvnw -Dtest=CompanyRepositoryIntegrationTest test`
- Expected: V0/V1 apply, Hibernate validates, normalized companies round-trip,
  and PostgreSQL rejects duplicate/invalid tickers.

### Phase 4: Add MarketSnapshot migration, entity, repository, and latest query

**Purpose**

- Persist exact quote snapshots and make latest-snapshot selection explicit and
  deterministic.

**Steps**

1. Add immutable `V2__create_market_snapshot_table.sql` with the resolved
   market-status decision and all other fields/checks from Section 8.
2. Map `MarketSnapshot` with a required lazy Company relation, `BigDecimal`
   columns, `Instant` timestamps, provider provenance, and optional JSONB raw
   data; add no cascade delete.
3. Add the deterministic latest-snapshot repository query.
4. Test V2 application, JPA round trips, precision/scale, FK/check constraints,
   company relationship, nullable optional fields, and latest ordering.
5. Update `InfrastructureIntegrationTest` to expect migrations 0/1/2 and exactly
   `company`, `market_snapshot`, and Flyway history while retaining Redis,
   database, and Actuator assertions.

**Validation**

- `cd backend && ./mvnw -Dtest=MarketSnapshotRepositoryIntegrationTest,InfrastructureIntegrationTest test`
- Expected: V0–V2 apply once, mappings validate, constraints are enforced, and
  latest selection wins by quote time, then retrieval time, then ID.

### Phase 5: Define and implement the selected provider adapter

**Purpose**

- Isolate external HTTP/authentication/JSON semantics behind the approved
  provider-independent interface.

**Steps**

1. Define `FinancialDataClient` with only
   `getCompanyProfile(String normalizedTicker)` and
   `getMarketSnapshot(String normalizedTicker)`; extend it for metrics/history
   in Milestone 3 rather than adding unused methods now.
2. Define provider-neutral `CompanyProfileData` and `MarketSnapshotData` records
   containing normalized values and provenance, not JPA annotations.
3. Add FMP DTOs mirroring only response fields needed to validate
   and normalize profile/quote payloads.
4. Configure RestClient authentication, base URL, JDK-backed connect/read
   timeouts, sanitized logging, and bounded transient retry behavior.
5. Map/validate required names, symbols, price, currency, timestamps, provider
   name, exact decimals, optional fields, safe HTTP(S) URLs, blank-to-null text,
   and requested/returned symbol consistency.
6. Translate provider errors into typed application exceptions; never return or
   log raw third-party messages, keys, auth headers, or bodies.
7. Store raw JSON only if approved and sanitized; never expose it through the
   interface or API.
8. Add mapper unit tests and mock HTTP client tests for every case listed in the
   acceptance criteria. CI must use no live URL or credential.

**Validation**

- `cd backend && ./mvnw -Dtest='*ResponseMapperTest,*FinancialDataClientTest' test`
- Expected: all success/error/empty/malformed/rate-limit/timeout/retry cases are
  deterministic against mock HTTP responses, with no outbound live call.

### Phase 6: Implement Company, MarketSnapshot, and stock query services

**Purpose**

- Coordinate provider loading and short PostgreSQL writes without adding
  caching, stale reads, or refresh orchestration.

**Steps**

1. `CompanyService` upserts a normalized provider profile, preserving the
   original creation timestamp and updating provider-backed fields.
2. Handle simultaneous cold inserts by reloading the unique ticker after a
   confirmed unique-constraint race; do not swallow unrelated persistence
   failures.
3. `MarketSnapshotService` persists one new normalized quote per valid request;
   the repository latest query remains available for supporting reads/tests.
4. Keep network calls outside long database transactions; repository saves own
   the short write transactions.
5. `StockQueryService` normalizes once, obtains both FMP resources, upserts the
   Company, appends the snapshot, and returns application data for API mapping.
   It performs no stale check, refresh endpoint, comparison, or Redis access.
6. Add unit tests for inserts, updates, provider exceptions, unknown ticker,
   missing required data, unique-insert races, and exact snapshot persistence.
7. Add a PostgreSQL service integration test with a fake FinancialDataClient to
   prove repeated requests update Company and append snapshots.

**Validation**

- `cd backend && ./mvnw -Dtest=CompanyServiceTest,MarketSnapshotServiceTest,StockQueryServiceIntegrationTest test`
- Expected: valid reads obtain normalized provider data, persist/update Company,
  append snapshots, and failures create no invalid MarketSnapshot row.

### Phase 7: Add the supporting stock endpoint and consistent errors

**Purpose**

- Expose the Milestone 2 data through one provider-independent HTTP contract.

**Steps**

1. Add API response records and an explicit mapper from Company/MarketSnapshot;
   do not serialize entities directly.
2. Add `StockController` at `/api/v1/stocks` with one GET path-variable method
   delegating immediately to `StockQueryService`.
3. Add `RequestIdFilter`, `ApiErrorResponse`, details record, and centralized
   exception handling for the Section 8 status/code table.
4. Ensure provider exceptions are sanitized and unexpected failures use a
   generic message while logs retain the request ID.
5. Add MockMvc controller tests for normalized success JSON, nullable optional
   fields, all error statuses/envelopes, request ID propagation/generation, and
   absence of provider/internal/raw-data fields.
6. Do not add `/metrics`, `/history`, `/news`, comparison, refresh, Swagger, or
   frontend code.

**Validation**

- `cd backend && ./mvnw -Dtest=StockControllerTest test`
- Expected: success and error JSON match Section 8 exactly, ticker validation
  occurs before repository/provider access, and raw exceptions are never
  exposed.

### Phase 8: Final integration, startup, and scope verification

**Purpose**

- Prove the complete milestone locally and in the existing credential-free CI
  path without broadening scope.

**Steps**

1. Run the full unit/integration/provider/controller suite with Compose stopped
   to prove Testcontainers/mock HTTP isolation.
2. Run Maven verify and confirm the existing CI backend command needs no secret.
3. Start Compose and the backend without a financial API key; verify context and
   Actuator health still succeed.
4. Verify an invalid-ticker curl returns the documented 400 envelope without an
   external call. Treat a live FMP curl as optional/manual only.
5. Review migrations, dependencies, provider package isolation, logs, complete
   diff, secrets, generated files, and forbidden later-milestone symbols.
6. Update this plan's files, decisions, progress, evidence, limitations, and
   acceptance criteria. Mark `Completed` only when all required checks pass.

**Validation**

- `docker compose config`
- `cd backend && ./mvnw test`
- `cd backend && ./mvnw verify`
- `docker compose up -d`
- `docker compose ps`
- `cd backend && ./mvnw spring-boot:run`
- `curl --fail --silent http://localhost:8080/actuator/health`
- `curl --silent --include http://localhost:8080/api/v1/stocks/INVALID_TICKER`
- `docker compose down`
- `git diff --check`
- `git status --short --untracked-files=all`
- Expected: tests/build/startup pass without provider credentials, health is
  `UP`, invalid ticker is HTTP 400 with the stable envelope, and only Milestone 2
  files appear in the diff.

## 10. Testing Strategy

### Unit Tests

- Ticker normalization: null, blank, trimming, locale-safe uppercase, maximum
  length, allowed dot/dash/digits, invalid leading digit, spaces, separators,
  Unicode, and unsupported characters.
- Provider mapper: complete responses, blank optional text, exact decimal
  values, safe/unsafe URLs, missing required name/price/currency/timestamp,
  mismatched symbols, optional missing fields, and market-status decision.
- CompanyService: stored hit, cold provider fill, unknown stock, provider error,
  invalid normalized data, and confirmed unique-insert race.
- MarketSnapshotService: deterministic stored latest, cold quote fill, provider
  error, empty required data, no invalid persistence, and exact BigDecimal use.
- StockQueryService: normalize once, service order, repeated stored read, and no
  refresh/cache behavior.
- Error mapping: stable code/status/message, UTC timestamp, path, details list,
  request ID, and sanitized unexpected/provider errors.

### PostgreSQL Testcontainers Integration Tests

- Apply V0/V1/V2 on a clean PostgreSQL 18.4 container and validate migration
  history/order/checksums.
- Start the JPA context with `ddl-auto=validate`; do not use H2.
- Save/load Company with all and minimal optional fields.
- Enforce ticker unique/regex/name/provider-symbol/timestamp constraints.
- Save/load MarketSnapshot with exact decimals, JSONB when allowed, nullable
  optional values, and Company relationship.
- Enforce positive price, nonnegative market cap, currency, provider-name, and
  foreign-key constraints.
- Verify deterministic latest-snapshot ordering.
- Use a fake provider for cold service integration and prove a second read uses
  PostgreSQL rather than the fake.
- Retain existing Redis connectivity and Actuator tests without adding Redis
  business behavior.

### Mock HTTP Provider-Client Tests

- Assert exact selected endpoint path, normalized symbol, authentication, and
  safe headers without printing secrets.
- Profile and quote success payloads map to provider-neutral records.
- Provider unknown-symbol response becomes `STOCK_NOT_FOUND`.
- Authentication/authorization and terminal upstream errors become sanitized
  `FINANCIAL_PROVIDER_ERROR`.
- Rate limit becomes `RATE_LIMITED` and safely carries retry metadata when
  available.
- Transient I/O/selected 5xx failures retry no more than configured attempts;
  invalid ticker, 4xx, 429, and malformed JSON do not retry.
- Timeout, malformed JSON, incompatible types, empty payload, mismatched symbol,
  and missing required fields are controlled.
- Optional missing fields stay null.
- No test calls a live provider or reads a real key.

### Controller Tests

- HTTP 200 response shape, numeric serialization, null handling, and normalized
  ticker.
- 400 invalid ticker, 404 unknown stock, 429 rate limit, 502 provider failure,
  503 unavailable data, and 500 unexpected failure.
- Required error fields and response/request ID behavior.
- No entity IDs, provider DTOs, raw data, credentials, stack traces, or raw
  provider messages in responses.
- No Milestone 3+ route is introduced.

### Manual Verification

- Start the application without a provider key and confirm context/health.
- Confirm an invalid ticker fails locally before provider access.
- Optionally, after credentials are supplied through the environment, request
  one known symbol and confirm normalized persistence and a provider-free second
  read. Never record the key or full provider payload in evidence/logs.
- Inspect PostgreSQL migration history and table names.
- Review dependency tree and diff for provider leakage and later scope.

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| FMP plan or license is insufficient for public display | Public deployment may not be permitted | Keep local implementation separate; obtain an applicable FMP display license before deployment |
| Provider terms prohibit display or raw storage | Legal/compliance issue and unusable `raw_data_json` | Verify terms before implementation; keep raw JSON nullable and store only permitted sanitized data |
| Provider lacks a design field or uses ambiguous semantics | Persistence/API could fabricate or mislabel data | Document provider definitions; require only essential quote fields; keep optional values null; resolve market status explicitly |
| Provider-specific symbols differ from public ticker rules | Valid tickers may map incorrectly or unknown symbols may be misreported | Validate public ticker first, keep provider symbol separate, and cover dot/dash/suffix mappings with FMP tests |
| External errors or payloads leak secrets/details | Security and API-boundary violation | Sanitize logs and exceptions; never log auth headers/raw bodies; assert response absence in tests |
| Decimal values are parsed through floating point | Financial precision loss | Parse and persist `BigDecimal` directly from textual/decimal JSON values |
| Simultaneous cold requests duplicate Company | Unique violation or redundant provider calls | Keep DB unique constraint; on confirmed race reload the winner; do not mask other persistence failures |
| Snapshot history grows without uniqueness | Storage growth | Milestone 2 creates only the first missing snapshot; refresh frequency/retention belongs to Milestone 8 |
| Latest query lacks its eventual performance index | Large datasets may scan per company | Keep correctness query now; add measured indexes in reserved V7 as designed |
| Existing infrastructure test expects zero domain tables | Test fails for the intended migration change | Update the exact table/migration assertion while preserving connectivity and health coverage |
| Provider key absence breaks context or CI | Default build becomes credential-dependent | Construct config with blank key allowed; validate only at call boundary; mock all HTTP in tests |
| Retry implementation amplifies rate limits or test time | Provider quota and latency worsen | Retry only bounded transient idempotent failures; never retry 429/invalid/malformed; avoid real sleeps in tests |
| New library is added for convenience | Unapproved dependency and larger scope | Use existing Spring/JDK facilities; pause and obtain approval before any new production dependency |
| Later features leak into resource DTO/service | Milestone coupling and rework | Enforce out-of-scope list and final symbol/dependency/diff scans |

## 12. Rollback / Recovery

- Before V1/V2 are shared, the milestone change can be reverted and a disposable
  local database recreated only after confirming the exact local volume and
  data-loss impact.
- Once applied in any shared database, never edit V1 or V2. Correct schema/data
  with an approved forward migration; do not consume design-reserved V3–V7 or
  renumber migrations without recording and approving a deviation.
- Rolling application code back may leave the additive Company/MarketSnapshot
  tables in place; the Milestone 1 application ignores them and Flyway history
  remains valid.
- Disabling/removing provider configuration stops new fetches but does not
  delete durable normalized rows. Serving stale stored data is deferred.
- If persisted raw JSON is later found impermissible, stop writing it and use an
  approved forward data migration to clear it; do not delete rows manually as a
  routine rollback.
- Normal cleanup remains `docker compose down` without `--volumes`. Do not
  remove named volumes or user data without explicit confirmation.
- Preserve unrelated repository changes and never restore/stage them as part of
  milestone recovery.

## 13. Progress

- [x] Provider selected and provider/schema blockers resolved
- [x] Plan reviewed and marked Ready
- [x] Implementation started; status marked In Progress
- [x] Phase 2 complete — ticker validation and common errors
- [x] Phase 3 complete — Company persistence
- [x] Phase 4 complete — MarketSnapshot persistence and latest query
- [x] Phase 5 complete — selected provider adapter and mock HTTP tests
- [x] Phase 6 complete — services and service tests
- [x] Phase 7 complete — stock endpoint and error handling
- [x] Phase 8 complete — full integration and scope verification
- [x] Tests and validation complete
- [x] Complete diff and secret/generated-file review complete
- [x] Acceptance criteria confirmed

Milestone 2 is complete. Milestone 3 was not started.

## 14. Decision Log

| Date | Decision | Reason | Alternatives |
|---|---|---|---|
| 2026-07-18 | Implement only the two currently needed `FinancialDataClient` methods | Company/quote are Milestone 2; unused metrics/history methods would create premature contracts for Milestone 3 | Copy all four design example methods immediately |
| 2026-07-18 | Use a combined `GET /api/v1/stocks/{ticker}` response | This is the approved supporting path and Company/MarketSnapshot are the only current resource data | Add an unapproved `/quote` endpoint; return provider-shaped data; create comparison endpoint early |
| 2026-07-18 | Plan no new production dependency | Existing Spring WebMVC/Jackson/JDK HTTP facilities can implement the adapter and mock HTTP tests | Add provider SDK, resilience library, mapping library, or WireMock |
| 2026-07-18 | Defer performance indexes to reserved V7 unless evidence requires approval sooner | Matches the migration sequence and keeps V1/V2 focused on table correctness | Add speculative indexes to V1/V2; consume V7 now |
| 2026-07-18 | Select Financial Modeling Prep through its stable profile and quote endpoints | The user selected FMP; its documented REST/JSON contract fits the existing Spring RestClient boundary without a new dependency | Yahoo Finance; Alpha Vantage; provider SDK |
| 2026-07-18 | Keep currency nullable and defer market status | FMP stable quote examples do not guarantee currency and neither selected endpoint supplies an approved market-status field | Guess currency from exchange; add an unsupported market-status column |
| 2026-07-18 | Fetch FMP profile and quote on every supporting stock request | This is the explicit implementation workflow supplied by the user and preserves snapshot history without adding caching/refresh orchestration | PostgreSQL-first missing-only fetch from the draft plan |
| 2026-07-18 | Do not persist raw FMP JSON | Normalized values are sufficient for Milestone 2 and avoid unnecessary licensing/security exposure | Populate nullable `raw_data_json` |
| 2026-07-18 | Build the FMP RestClient directly from `RestClient.builder()` | Spring Boot 4.1 did not expose an injectable builder in the minimal application context; the existing Spring/JDK stack still supplies the approved client without a new dependency | Add a custom shared builder bean or new HTTP dependency |

No provider placeholder or schema blocker remains. Exact validation results are
recorded below.

## 15. Deviations from Design

- None approved.
- FR-3 market status is deferred because the selected endpoints do not provide
  a directly supported value and Section 15.2 omits it; no schema deviation is
  introduced.
- Incrementally defining only the Company/MarketSnapshot methods of the design's
  example FinancialDataClient is not a scope deviation; Milestone 3 will extend
  the same provider boundary for metrics/history.

## 16. Validation Evidence

| Command | Result | Notes |
|---|---|---|
| Provider decision and official-contract review | PASS | FMP stable profile/quote endpoints, array roots, authentication, error codes, free-tier/access limitations, and field examples reviewed on 2026-07-18 |
| Focused unit/provider/controller test run | PASS | 23 tests passed after implementation fixes; no live provider call |
| PostgreSQL Testcontainers repository/service tests | PASS | Company, MarketSnapshot, migration, constraint, latest-ordering, and repeated-request persistence coverage passed |
| `cd backend && ./mvnw clean verify` | PASS | 36 tests, 0 failures, 0 errors, 0 skipped; build success in 38.644 seconds |
| `docker compose config` | PASS | Compose configuration remains valid |
| `docker compose up -d` and `docker compose ps` | PASS | Existing PostgreSQL 18.4 and Redis 8.8 services were healthy; they were left running because they pre-existed this task |
| Backend startup without `FMP_API_KEY` on temporary port 18080 | PASS | Flyway validated versions 0–2, Hibernate schema validation passed, and the temporary process shut down cleanly |
| `curl http://localhost:18080/actuator/health` | PASS | HTTP 200 with `status: UP` |
| `curl http://localhost:18080/api/v1/stocks/BAD_SYMBOL` | PASS | HTTP 400 with `INVALID_TICKER`, safe message, path, UTC timestamp, details, and request ID; no provider call |
| `git diff --check` | PASS | No whitespace errors |
| Secret/generated/later-scope scans | PASS | Only blank FMP configuration and explicit `test-api-key` mock expectations were found; no generated/local environment files or later-milestone production symbols were added |
| `git status --short --untracked-files=all` and complete file review | PASS | Every modified/untracked file belongs to Milestone 2; `docs/design.md`, frontend, dependency manifest, CI, Compose, and deployment files are unchanged |

## 17. Completion Summary

### Implemented

- Added exact ticker normalization, typed errors, request IDs, and the consistent
  API error envelope.
- Added Flyway V1/V2, Company and MarketSnapshot JPA models/repositories, exact
  decimal persistence, deterministic latest-snapshot lookup, and services.
- Added the provider-neutral FinancialDataClient boundary and FMP stable
  profile/quote adapter with typed configuration, mapping, timeouts, bounded
  transient retries, and sanitized failures.
- Added `GET /api/v1/stocks/{ticker}` with provider-neutral response DTOs. Each
  valid request loads both FMP resources, updates Company, and appends a market
  snapshot.

### Files Changed

- Created the common, Company, market, and FMP Java packages listed in Section 7.
- Added migrations V1/V2, FMP mock fixtures, and Milestone 2 tests.
- Updated `.env.example`, `README.md`, `application.yml`, the infrastructure
  integration test, and this execution plan.
- `backend/pom.xml` and all frontend, CI, Compose, design, and deployment files
  remain unchanged.

### Tests Added or Updated

- Added ticker, mapper, provider-client, service, controller, repository, and
  end-to-end service integration tests.
- Updated the infrastructure integration test to assert Flyway versions 0–2
  and the exact Milestone 2 tables while retaining PostgreSQL, Redis, and
  Actuator coverage.

### Known Limitations

- Public/multi-user FMP display remains subject to the user's applicable FMP
  subscription and data-display license.
- Live FMP behavior was not exercised because no credential was requested or
  supplied; automated tests use mock HTTP and sanitized fixtures.
- FMP stable quote currency is not guaranteed. Currency is copied from the
  profile when provided; otherwise it remains null. Market status is deferred.
- No stale-read fallback exists when FMP is unavailable or unconfigured.
- Freshness, refresh, and Redis caching are deliberately deferred to Milestone 8.

### Remaining Work

- None for Milestone 2. Milestone 3 and all later milestones remain unstarted.
