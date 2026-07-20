# Milestone 4: Yahoo Finance News Integration

**Status:** Completed
**Milestone:** 4 — News Integration
**Created:** 2026-07-19
**Last Updated:** 2026-07-19
**Design References:** `docs/design.md` Sections 4.1, 8 (FR-6), 9, 11,
13.5, 14–16, 18–19, 22–25, 28 (Milestone 4), and 29

## 1. Goal

Replace the inaccessible Financial Modeling Prep stock-news adapter with a
Yahoo Finance news-only adapter while preserving the completed provider-neutral
Milestone 4 schema, persistence, deduplication, service, and public API.

Financial Modeling Prep remains the sole financial-data provider for company
profiles, quotes, metrics, and historical prices. Yahoo Finance is used only
for recent company news through the existing `NewsDataClient` boundary.

Yahoo is an unofficial, replaceable MVP provider suitable for local
development, education, portfolio demonstration, and low-volume use. It is not
an official Yahoo developer API, a guaranteed production-stable contract, or a
commercially licensed long-term source. A future production deployment should
evaluate a licensed news provider.

## 2. Background and Current State

Milestones 1–3 are complete. The repository was clean before this replacement
task began. The current backend is a Java 21 / Spring Boot 4.1 modular monolith
using PostgreSQL, Flyway, JPA validation, Redis connectivity, RestClient,
JUnit/Mockito, MockRestServiceServer, and PostgreSQL Testcontainers.

The first Milestone 4 implementation is also complete and provider-independent
outside its FMP adapter:

- `V5__create_news_tables.sql` creates generic `news_article` and
  `news_article_company` tables with no FMP-specific column or constraint.
- `NewsArticle`, `NewsArticleRepository`, `NewsArticlePersistenceService`,
  `CanonicalArticleUrlService`, `NewsQueryService`, `NewsController`, and public
  DTOs contain no FMP response types.
- `NewsDataClient`, `NewsArticleData`, and `NewsFetchResult` form a meaningful
  provider boundary and retain partial-record warning metadata.
- URL canonicalization, SHA-256 hashing, conflict-safe inserts, multi-company
  associations, newest-first queries, and the public endpoint already work.
- Existing provider-neutral news tests cover persistence, canonicalization,
  service behavior, controller behavior, and PostgreSQL constraints.
- FMP-specific news code consists only of `FmpNewsDataClient`,
  `FmpNewsResponseMapper`, `FmpNewsResponse`, their two tests, and one fixture.
- The configured FMP account returns HTTP 402 for `/stable/news/stock`.

V0–V5 are completed migrations and will not be edited. The generic V5 schema
already supports Yahoo external IDs and provider name `YAHOO_FINANCE`.

## 3. Scope

### In Scope

- Preserve the provider-neutral news interface and durable model.
- Add typed Yahoo configuration with an overridable base URL and bounded
  connection/read timeouts and attempts.
- Implement one Yahoo Finance `POST /xhr/ncp` news adapter.
- Add DTOs and a mapper for only the verified Yahoo response fields.
- Filter advertisements and non-story content, validate required fields, and
  preserve partial valid results.
- Request more ticker-scoped candidates than the public display limit.
- Preserve URL canonicalization, hashing, database deduplication, company
  relationships, and newest-first query behavior.
- Preserve `GET /api/v1/stocks/{ticker}/news` and its existing default `10`,
  minimum `1`, and maximum `20` public limit contract.
- Replace only FMP-news-specific tests and fixtures with Yahoo tests/fixtures.
- Update the design and local configuration documentation with the explicit
  unofficial-provider trade-off.
- Run credential-free automated verification and a limited live Yahoo smoke
  test.

### Out of Scope

- `/api/v1/comparisons` or any two-stock comparison orchestration.
- FMP profile, quote, metric, or historical-price changes.
- AI, Spring AI, summaries, sentiment, topic classification, or web search.
- Redis business caching, refresh endpoints, scheduling, or ingestion jobs.
- Frontend dashboard work.
- Authentication or deployment.
- Article-page fetching, content scraping, redirect following, previews, or
  server-side publisher requests.
- Browser automation, Selenium, Playwright, Python, yfinance runtime code, or a
  sidecar service.
- Hidden fallback endpoints or Milestone 5+ behavior.

## 4. Assumptions

- The verified cookie-free Yahoo ticker-stream response is sufficient for the
  low-volume MVP path.
- The endpoint itself is ticker-scoped. Missing related-symbol metadata does
  not make an otherwise valid article irrelevant; provider-neutral
  `relatedSymbols` remains empty until durable company associations are loaded.
- Yahoo `content.canonicalUrl.url` is the preferred article URL because the
  live response supplies publisher URLs there. `clickThroughUrl.url` is used
  only when the canonical URL is absent.
- `content.summary` is the preferred optional description; the verified
  `content.description` was blank in the live sample and is only a fallback.
- `content.id` is the preferred optional external ID; top-level `id` is a
  fallback. The live sample supplied equal UUID-like values in both locations.
- Only `contentType=STORY` is accepted. Videos, slideshows, and promotional
  cards are not part of the approved `NewsArticle` contract.
- A truthy top-level `ad` marker means the record is an advertisement. This
  matches the inspected current upstream yfinance filtering behavior.
- The existing public limit range `1..20` is retained because it is already an
  implemented and documented contract. Dashboard display remains three later.
- No new production or test dependency is needed.

## 5. Open Questions / Blockers

- None. The candidate endpoint returned HTTP 200 JSON without cookies,
  authentication, a crumb, browser automation, or CAPTCHA.
- Yahoo may change or restrict this unofficial endpoint without notice. That is
  a known runtime risk rather than an implementation blocker.

## 6. Verified Yahoo Contract

### Upstream implementation evidence

The current upstream yfinance `TickerBase.get_news` implementation was
inspected on 2026-07-19. It selects `latestNews`, posts a JSON service
configuration to Yahoo's `/xhr/ncp` endpoint, reads
`data.tickerStream.stream`, and removes records with a truthy top-level `ad`
field. yfinance is not added to StockLens; it is contract evidence only.

Reference:
`https://github.com/ranaroussi/yfinance/blob/main/yfinance/base.py`

### Verified live request

```http
POST https://finance.yahoo.com/xhr/ncp
    ?queryRef=latestNews
    &serviceKey=ncp_fin
Accept: application/json
Content-Type: application/json
Content-Length: 50
User-Agent: StockLens-AI/1.0 (educational project)

{
  "serviceConfig": {
    "snippetCount": 10,
    "s": ["AAPL"]
  }
}
```

The read-only probe returned HTTP `200`, content type `application/json`, no
redirect, and ten stream records. Live application testing additionally showed
that Yahoo returns HTTP `502` for a chunked body, so the adapter serializes the
small typed request to bytes and sends the exact fixed-length payload. No
cookie, authorization header, crumb, browser identifier, or secret was sent.

### Verified response shape

```text
root: object
data: object
data.tickerStream: object
data.tickerStream.stream: array
stream item:
  id: string
  ad: optional untyped marker
  content:
    id: string
    contentType: string
    title: string
    description: string
    summary: string
    pubDate: ISO-8601 string
    provider:
      displayName: string
    canonicalUrl:
      url: string
    clickThroughUrl:
      url: string
```

Other observed fields such as thumbnails, display time, hosted flags, provider
URLs/IDs, URL locale metadata, premium flags, and editor metadata are ignored.
The live response did not provide related-symbol metadata. All ten live records
were `STORY`; the first probe contained no advertisement record.

### Exact field mapping

| Yahoo field | Application field | Rule |
|---|---|---|
| `content.id`, then item `id` | `externalId` | Optional trimmed value, maximum 255 characters. |
| `content.title` | `headline` | Required normalized plain text, maximum 1000 characters. |
| `content.provider.displayName` | `sourceName` | Optional normalized plain text, maximum 255 characters. |
| `content.canonicalUrl.url`, then `content.clickThroughUrl.url` | `articleUrl` | Required raw URL passed to centralized HTTP(S) canonicalization. |
| `content.summary`, then `content.description` | `description` | Optional normalized plain text; never generated from the headline. |
| `content.pubDate` | `publishedAt` | Required ISO-8601 instant; never replaced with retrieval time. |
| none | `relatedSymbols` | Empty at provider boundary; the ticker-scoped company relationship supplies public related tickers after persistence. |
| application `Clock` | `retrievedAt` | One deterministic timestamp per provider response. |
| constant | `providerName` | `YAHOO_FINANCE`. |

Filtering rules:

- Skip a record when top-level `ad` is truthy.
- Skip a record whose `contentType` is not `STORY`.
- Skip a record with missing content, headline, URL, or publication timestamp.
- Keep valid records when other records are invalid and report only the skipped
  count.
- A present array containing no valid records returns controlled
  `NEWS_PROVIDER_ERROR`.
- A valid empty stream returns HTTP 200 with an empty application list.
- Missing `data`, missing/null `tickerStream`, or missing/null `stream` is an
  unusable contract and returns controlled `NEWS_PROVIDER_ERROR`.

## 7. Acceptance Criteria

- [x] FMP remains unchanged for financial data and contains no news method.
- [x] All obsolete FMP news client, DTO, mapper, fixture, tests, and bean wiring
  are removed.
- [x] `NewsDataClient` remains provider-independent and is implemented by the
  Yahoo adapter only.
- [x] Yahoo configuration uses typed properties and an overridable base URL;
  no key, cookie, crumb, browser state, or personal header is introduced.
- [x] The adapter sends the verified POST/query/body/headers and validates JSON
  content type without following redirects.
- [x] Yahoo mapping implements only the exact verified fields and filters ads
  and non-story content.
- [x] Valid empty, malformed contract, partial invalid, and all-invalid results
  remain distinct.
- [x] The V5 schema and all V0–V5 migrations remain unchanged.
- [x] Repeated requests remain idempotent and one article can relate to multiple
  companies.
- [x] Public API remains provider-independent, defaults to 10, accepts 1..20,
  returns newest-first results, and reports `YAHOO_FINANCE`.
- [x] Yahoo 429 returns `RATE_LIMITED`; auth/other 4xx, 5xx, timeout, reset,
  redirect, HTML, malformed JSON, and contract failures return safe
  `NEWS_PROVIDER_ERROR`.
- [x] Unit, mock HTTP, MVC, service, persistence, Flyway, and Hibernate
  validation tests pass without live Yahoo access or FMP credentials.
- [x] `cd backend && ./mvnw clean verify` passes.
- [x] A safe live AAPL/MSFT smoke test is recorded honestly.
- [x] Design disclosure, complete diff, secrets/generated-file, scraping,
  endpoint-host, DTO-leakage, and Milestone 5 scope reviews pass.

## 8. Expected Files

### Create

- `backend/src/main/java/com/stocklens/news/client/yahoo/YahooFinanceNewsClientConfiguration.java`
- `backend/src/main/java/com/stocklens/news/client/yahoo/YahooFinanceNewsProperties.java`
- `backend/src/main/java/com/stocklens/news/client/yahoo/YahooFinanceNewsDataClient.java`
- `backend/src/main/java/com/stocklens/news/client/yahoo/YahooFinanceNewsResponseMapper.java`
- `backend/src/main/java/com/stocklens/news/client/yahoo/dto/YahooFinanceNewsRequest.java`
- `backend/src/main/java/com/stocklens/news/client/yahoo/dto/YahooFinanceNewsResponse.java`
- `backend/src/test/java/com/stocklens/news/client/yahoo/YahooFinanceNewsDataClientTest.java`
- `backend/src/test/java/com/stocklens/news/client/yahoo/YahooFinanceNewsResponseMapperTest.java`
- `backend/src/test/resources/fixtures/yahoo/news-success.json`

### Modify

- `.agent/plans/04-news-integration.md`
- `docs/design.md` — only provider/disclosure and local environment text.
- `backend/src/main/resources/application.yml`
- `.env.example`
- `README.md`
- `backend/src/main/java/com/stocklens/common/web/GlobalExceptionHandler.java`
- `backend/src/main/java/com/stocklens/news/service/NewsArticlePersistenceService.java`
- Existing provider-neutral news tests whose fixture provider value is `FMP`.

### Delete

- `backend/src/main/java/com/stocklens/news/client/fmp/FmpNewsDataClient.java`
- `backend/src/main/java/com/stocklens/news/client/fmp/FmpNewsResponseMapper.java`
- `backend/src/main/java/com/stocklens/news/client/fmp/dto/FmpNewsResponse.java`
- `backend/src/test/java/com/stocklens/news/client/fmp/FmpNewsDataClientTest.java`
- `backend/src/test/java/com/stocklens/news/client/fmp/FmpNewsResponseMapperTest.java`
- `backend/src/test/resources/fixtures/fmp/news-success.json`

The list will be updated if implementation proves an item unnecessary. No
migration, dependency manifest, frontend, CI, Compose, or Milestone 1–3
production file is expected to change.

## 9. API / Schema / Configuration Changes

### API

Keep:

```http
GET /api/v1/stocks/{ticker}/news?limit=10
```

- Default `10`, accepted `1..20`.
- Success contract remains `ticker`, `limit`, `providerName`, `retrievedAt`,
  `articles`, and `warnings`.
- `providerName` becomes `YAHOO_FINANCE`.
- A valid empty Yahoo stream is HTTP 200 with empty arrays.
- Public DTOs expose no Yahoo wrapper, raw metadata, hashes, cookies, or
  provider transport fields.

### Database

- No migration change. V5 is already generic and must remain byte-for-byte
  unchanged.
- Existing URL hash and provider/external-ID uniqueness remain authoritative.
- The generic provider value for new rows is `YAHOO_FINANCE`; no provider-name
  CHECK constraint is added.

### Configuration

Add prefix `stocklens.providers.yahoo-finance-news`:

| Property | Environment variable | Default |
|---|---|---|
| base URL | `YAHOO_FINANCE_BASE_URL` | `https://finance.yahoo.com` |
| connect timeout | `YAHOO_FINANCE_CONNECT_TIMEOUT` | `2s` |
| read timeout | `YAHOO_FINANCE_READ_TIMEOUT` | `5s` |
| maximum attempts | `YAHOO_FINANCE_MAX_ATTEMPTS` | `2` total attempts |

No Yahoo key exists. The JDK client explicitly does not follow redirects. The
base URL is overridden by mock HTTP tests.

### Dependencies

None. Existing Spring RestClient/Jackson/JDK HTTP and Spring mock HTTP support
are sufficient.

## 10. Implementation Plan

### Phase 1: Freeze the verified Yahoo contract and replacement plan

1. Inspect current upstream yfinance news code and the live Yahoo response.
2. Record method, host, path, query, body, headers, root, exact mapping, ad
   behavior, risks, and limitations.
3. Confirm the existing generic schema and service boundary can be preserved.

Validation: plan and source review; one safe read-only contract probe.

### Phase 2: Add Yahoo configuration and HTTP adapter

1. Add typed properties and a qualified RestClient using the existing JDK HTTP
   stack, timeouts, redirect control, stable User-Agent, and JSON Accept header.
2. Add verified request/response DTOs.
3. Implement POST body/query construction, candidate count, content-type
   validation, safe status mapping, and at most one transient retry.
4. Do not send credentials, cookies, crumbs, or browser headers.

Validation:
`cd backend && ./mvnw -Dtest=YahooFinanceNewsDataClientTest test`

### Phase 3: Add Yahoo mapping and remove FMP news transport

1. Map only verified fields, using the application Clock for retrieval time.
2. Filter ads and non-story records, normalize text, validate timestamps, and
   implement valid-empty/partial/all-invalid behavior.
3. Delete only obsolete FMP news classes/tests/fixture.
4. Preserve every FMP financial-data class and test.

Validation:
`cd backend && ./mvnw -Dtest=YahooFinanceNewsResponseMapperTest,YahooFinanceNewsDataClientTest test`

### Phase 4: Align provider-neutral behavior and documentation

1. Change all-invalid normalized provider data to news-provider error behavior.
2. Update provider-name expectations without changing API shape, persistence,
   limit validation, URL rules, or deduplication behavior.
3. Update only necessary design, environment, application, and README text.

Validation:
`cd backend && ./mvnw -Dtest='com.stocklens.news.**' test`

### Phase 5: Full automated validation

1. Run all tests with no Yahoo/FMP credential and no live Yahoo dependency.
2. Verify Flyway V0–V5, Hibernate validation, PostgreSQL Testcontainers, and
   all completed FMP financial behavior.
3. Scan for dead FMP-news code, provider DTO leakage, live-test endpoints,
   scraping/browser automation, secrets, and Milestone 5 code.

Validation:

```bash
cd backend && ./mvnw clean verify
git diff --check
```

### Phase 6: Live smoke and final review

1. Start existing Compose dependencies and the backend normally.
2. Call AAPL twice with limit 3 and MSFT once.
3. Verify HTTP/public fields/order/provider and database idempotency queries.
4. If Yahoo refuses or changes the contract, preserve strict tests and record
   the controlled runtime limitation.
5. Review the complete diff and finalize this plan.

## 11. Testing Strategy

### Provider contract and mapping

- Exact POST path/query/body/base URL and stable headers.
- Successful multi-story response and newest-first mapping.
- Exact external ID, headline, source, canonical URL, summary, ISO instant,
  provider name, empty related symbols, and fixed retrieved time.
- Ad and non-story filtering.
- Optional ID/source/summary and click-through URL fallback.
- Valid empty stream; missing/null stream; malformed JSON; HTML content type.
- Mixed malformed records; all invalid; missing content/headline/URL/pubDate;
  invalid URL/timestamp.
- 400/401/403/404/429/5xx, unexpected redirect, timeout, and connection failure.
- No test contacts Yahoo or requires any credential.

### Existing provider-neutral tests

- Preserve URL canonicalization equivalence, fragment removal, query
  preservation, unsafe scheme/user-info/relative rejection, and deterministic
  SHA-256 behavior.
- Preserve PostgreSQL uniqueness, FKs, newest-first query/limit, idempotent
  repeated persistence, race-safe inserts, and multi-company association.
- Preserve ticker/limit/service/controller behavior and public DTO boundaries.

### Manual verification

- AAPL `limit=3`, repeated once, then MSFT `limit=3`.
- Confirm no more than three newest-first stories, required fields, optional
  source, `YAHOO_FINANCE`, no ads/wrappers/cookies/internal details.
- Confirm no duplicate URL hashes or company/article relationships.

## 12. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Yahoo changes an unofficial response | News becomes unavailable | Strict contract validation, controlled provider errors, isolated replaceable adapter, documented risk. |
| Yahoo rate limits low-volume requests | Temporary 429 | No evasion, no long sleep, safe RATE_LIMITED response, no cache added early. |
| Yahoo returns HTML/redirect/challenge | Unsafe parsing or scraping temptation | Disable redirects, require JSON content type, return controlled error, never scrape HTML. |
| Yahoo rejects chunked POST bodies | Live request returns HTTP 502 despite a valid payload | Serialize the typed request to a bounded byte array, send a fixed `Content-Length`, and assert the exact 50-byte AAPL contract in the mock HTTP test. |
| Ads/promotional/video records enter persistence | Misleading public news | Filter truthy `ad`, accept only `STORY`, validate required article fields. |
| Yahoo wrapper and publisher canonical URLs differ | Duplicate or undesirable links | Prefer the supplied canonical publisher URL, fall back deterministically, never follow redirects. |
| Missing related symbols | Over-rejection of ticker-scoped results | Trust only the ticker-scoped stream and associate the requested resolved Company. |
| Existing FMP news rows remain locally | Mixed provider historical data | Generic schema supports both; new calls write Yahoo values. No destructive data migration is justified. |

## 13. Rollback / Recovery

- The Yahoo adapter/configuration can be reverted without a schema rollback.
- V5 remains unchanged and provider-independent.
- Existing stored FMP news rows, if any, remain valid generic historical rows;
  this milestone does not delete durable data.
- If Yahoo becomes unusable, disable calls or replace only the
  `NewsDataClient` adapter in a later approved decision.
- Never edit an applied migration or delete local volumes as routine recovery.

## 14. Progress

- [x] Read governing documents and completed Milestone 1–3 plans.
- [x] Inspected current backend, migrations, tests, and clean diff.
- [x] Verified current upstream yfinance request logic.
- [x] Verified a live cookie-free Yahoo JSON response and exact field shape.
- [x] Replaced the execution plan and marked it In Progress.
- [x] Phase 2 complete — Yahoo configuration and HTTP adapter.
- [x] Phase 3 complete — Yahoo mapping and obsolete FMP-news removal.
- [x] Phase 4 complete — provider-neutral behavior and documentation aligned.
- [x] Phase 5 complete — automated validation.
- [x] Phase 6 complete — live smoke and final review.
- [x] Acceptance criteria confirmed.

## 15. Decision Log

| Date | Decision | Reason | Alternatives |
|---|---|---|---|
| 2026-07-19 | Use Yahoo only for recent news and retain FMP for all financial data | FMP stock news returns HTTP 402; Yahoo ticker stream returned usable JSON | Upgrade FMP plan; licensed provider; search endpoint fallback |
| 2026-07-19 | Select only POST `/xhr/ncp?queryRef=latestNews&serviceKey=ncp_fin` | It matches current yfinance and succeeded without authentication/browser automation | Yahoo search endpoint; implement both as hidden fallbacks |
| 2026-07-19 | Preserve `NewsFetchResult` rather than reduce the boundary to a bare list | Existing provider-neutral skipped-record metadata supports the required partial-success warning | Lose warnings or create a second side channel |
| 2026-07-19 | Preserve public default 10 and range 1..20 | It is an already implemented/documented contract and design shows `limit=10` | Reduce maximum/default to dashboard display size 3/10 |
| 2026-07-19 | Request `max(10, limit*2)` candidates, capped at 40 | Allows ad/invalid filtering while keeping a low bounded request; max public limit 20 | Request exactly limit; unbounded candidate count |
| 2026-07-19 | Prefer Yahoo canonical publisher URL over click-through URL | Live records supply direct publisher URLs in `canonicalUrl` and Yahoo wrapper URLs in `clickThroughUrl` | Persist wrapper only; follow redirects |
| 2026-07-19 | Accept only `STORY` and filter truthy top-level `ad` | Matches the verified content type and current upstream yfinance ad behavior | Persist videos/slideshows/promotions |
| 2026-07-19 | Add no dependency, credential, cookie, or fallback endpoint | Existing stack is sufficient and unofficial access must remain low-risk and replaceable | yfinance runtime, browser automation, new HTTP stack |
| 2026-07-19 | Send the typed Yahoo request as fixed-length JSON bytes | Live testing proved Yahoo returned 502 for chunked transfer encoding but 200 for the identical fixed-length payload | Keep Spring's chunked converter; add a different HTTP dependency |

## 16. Deviations from Design

- Approved by the user in this task: Yahoo Finance is the news provider while
  FMP remains the financial provider. The previous implementation assumption
  that FMP also supplied news is replaced.
- No schema, public API, architecture, or milestone-order deviation exists.

## 17. Validation Evidence

| Command / check | Result | Notes |
|---|---|---|
| Required document/current repository review | PASS | AGENTS, full design, PLANS, Milestone 1–4 plans, backend, migrations, tests, and clean diff inspected. |
| Current upstream yfinance inspection | PASS | `get_news` uses POST `/xhr/ncp`, `latestNews`, the JSON service body, stream root, and top-level ad filtering. |
| Read-only Yahoo contract probe | PASS | HTTP 200, `application/json`, no redirect, ten ticker-stream records, no credentials/cookies/browser automation. |
| `cd backend && ./mvnw -DskipTests compile` | PASS | 84 production source files compile on Java 21 with no dependency change. |
| Focused Yahoo contract/mapper tests | PASS | 13 tests, 0 failures/errors; exact request, mapping, status, content-type, malformed/partial/empty, retry, and filter behavior covered. |
| `cd backend && ./mvnw -Dtest='com.stocklens.news.**' test` | PASS | 35 tests, 0 failures/errors; includes PostgreSQL 18.4 Testcontainers, Flyway V0–V5, Hibernate validation, persistence, service, and MVC coverage. |
| `cd backend && ./mvnw clean verify` | PASS | 96 tests across 26 reports, 0 failures, 0 errors; clean package created. PostgreSQL 18.4 and Redis 8.8 Testcontainers passed; Flyway applied V0–V5 and Hibernate validated the model. |
| Live application smoke | PASS | Temporary port 18081: AAPL twice and MSFT once with `limit=3` each returned HTTP 200, three newest-first articles, required public fields, and `YAHOO_FINANCE`. The initial chunked-body 502 was diagnosed and fixed with fixed-length request serialization. |
| Live database idempotency/order | PASS | 0 duplicate URL hashes, 0 duplicate provider/external IDs, 0 duplicate company/article links; AAPL and MSFT each had ten Yahoo article links after bounded candidate persistence. A newest-first SQL query returned ten descending timestamps with generic `YAHOO_FINANCE` values. |
| Diff/security/scope review | PASS | No migration, dependency manifest, frontend, CI, Compose, deployment, or Milestone 5 change. No dead FMP-news code, key, cookie, crumb, `.env`, generated file, scraping/browser automation, or provider DTO leakage. `git diff --check` passed. |

## 18. Completion Summary

Milestone 4 now uses a typed, isolated Yahoo Finance news adapter behind the
existing provider-neutral `NewsDataClient`. The adapter sends the verified
fixed-length POST contract, validates transport and response structure, maps
only the approved fields, filters advertisements and non-story content, and
preserves partial valid results. FMP remains unchanged for all financial data.

The generic V5 news schema, persistence, URL canonicalization and hashing,
deduplication, company relationships, service orchestration, controller, and
public DTOs were preserved. Obsolete FMP-news-only code and fixtures were
removed. Design, README, application configuration, and the environment example
now disclose the unofficial and replaceable Yahoo provider boundary.

Automated validation finished with 96 tests and no failures or errors. Live
AAPL/MSFT requests passed after a confirmed chunked-transfer incompatibility
was fixed and covered by a fixed-content-length regression assertion. No
Milestone 5 behavior, new dependency, schema migration, credential, scraping,
browser automation, frontend work, authentication, or deployment work was
added. The remaining limitation is the documented instability and licensing
risk of Yahoo's unofficial endpoint; a future production deployment should
replace it with a licensed provider without changing the public or domain
contracts.
