# Milestone 4: News Integration

## Status

Completed on 2026-07-19, with the live FMP account limitation documented
below. All automated acceptance criteria pass; the configured account returns
HTTP 402 for the stock-news dataset, and the application returns the planned
controlled `NEWS_PROVIDER_ERROR` without partial writes.

This plan covers Milestone 4 only. It was created after reading `AGENTS.md`,
`docs/design.md`, `.agent/PLANS.md`, the completed Milestone 3 plan, and the
complete current backend implementation and tests. The official Financial
Modeling Prep (FMP) stable documentation and API Viewer were inspected before
the provider DTOs were finalized.

## Goal

Add a provider-independent, durable recent-news slice for one normalized stock
ticker. The slice retrieves company-specific news from FMP, validates and
normalizes untrusted provider records, canonicalizes article URLs, prevents
duplicate articles and duplicate company relationships, persists news in
PostgreSQL, and exposes newest-first application DTOs at:

```http
GET /api/v1/stocks/{ticker}/news?limit=10
```

The milestone is complete when the schema, provider adapter, persistence,
service, endpoint, error behavior, and automated tests pass the full backend
verification without live provider calls or committed credentials.

## Current Repository State

- Milestones 1 through 3 are complete.
- The backend is a Java 21 / Spring Boot 4.1 modular monolith.
- PostgreSQL, Redis connectivity, Flyway, Actuator, consistent API errors,
  request IDs, and Testcontainers are configured.
- Existing migrations are `V0` through `V4`; `V4` creates historical prices.
- `spring.jpa.hibernate.ddl-auto=validate` is active.
- `Company`, `MarketSnapshot`, `FinancialMetricSnapshot`, and
  `HistoricalPrice` are persisted.
- `FinancialDataClient` and the FMP adapter provide profiles, quotes, metrics,
  and history through provider-neutral models.
- FMP already uses `FMP_API_KEY`, `FMP_BASE_URL`, bounded timeouts, and bounded
  retries. News will reuse this configuration and the qualified FMP
  `RestClient`.
- Supporting stock, metrics, and history endpoints exist. No news package,
  news migration, or news endpoint exists yet.
- The worktree was clean before this plan was created.

## Scope

### Included

- `NewsArticle` persistence.
- The `news_article_company` many-to-many join table.
- Provider-neutral `NewsDataClient`, news result, and article data contracts.
- FMP Search Stock News adapter using the stable company-specific endpoint.
- FMP response DTO, validation, sanitization, and mapping.
- Canonical HTTP(S) URL normalization and SHA-256 hashing.
- Response-level and database-backed duplicate prevention.
- Company resolution and recent-news application service.
- `GET /api/v1/stocks/{ticker}/news?limit=10`.
- Consistent news-specific validation, provider, rate-limit, and data errors.
- Unit, mock HTTP, PostgreSQL Testcontainers, service, integration, and MVC
  tests.
- Safe optional local FMP smoke verification.

### Excluded

- Aggregated comparison APIs or two-company orchestration.
- Financial provider, metrics, or history changes unrelated to news.
- AI, Spring AI, sentiment generation, or generated summaries.
- Redis business caching or refresh orchestration.
- Frontend work.
- Authentication, deployment, or any Milestone 5+ functionality.
- General-news or press-release fallback feeds.
- External URL fetching, previews, or article-body downloads.
- Changes to `docs/design.md`.

## Source-of-Truth and Design Alignment

- `AGENTS.md` governs workflow, security, architecture, and completion.
- `docs/design.md` defines the approved product and data model. Relevant
  sections are FR-6, 9, 11, 13.5, 14, 15.5-15.6, 16.4, 18, 19, 22, 23, 24,
  27, 28 Milestone 4, and 29.
- `.agent/PLANS.md` governs the content and maintenance of this plan.
- The implementation remains a feature-oriented modular monolith, keeps FMP
  DTOs within the FMP adapter, keeps HTTP concerns in the controller, and uses
  services for validation and persistence orchestration.
- PostgreSQL remains the durable source of truth. Redis is not used by this
  milestone.
- Flyway owns both news tables; JPA validates rather than creates the schema.

No design conflict was found.

## Verified FMP Contract and Provider Decision

The official FMP stable documentation page and its API Viewer were inspected
on 2026-07-18 for **Search Stock News API**.

Verified request:

```http
GET https://financialmodelingprep.com/stable/news/stock
    ?symbols=AAPL
    &page=0
    &limit=20
    &apikey={apiKey}
```

Verified query parameters:

- `symbols` is required and documented as a string.
- `from` and `to` are optional dates.
- `page` is optional; the documented first page is `0`, with pages capped at
  `100`.
- `limit` is optional; the documentation states at most `250` records per
  request.
- This milestone sends one normalized ticker, `page=0`, and the already
  validated application limit of `1..20`. It does not paginate beyond the first
  page because the public endpoint contract returns at most 20 records.
- No date bounds are sent for the recent-news endpoint.

Verified response root and fields:

```json
[
  {
    "symbol": "AAPL",
    "publishedDate": "2026-06-06 14:13:36",
    "publisher": "TechCrunch",
    "title": "Article headline",
    "image": "https://example.invalid/image.jpg",
    "site": "techcrunch.com",
    "text": "Article snippet",
    "url": "https://techcrunch.com/article"
  }
]
```

Mapping decision:

| FMP field | Application field | Rule |
|---|---|---|
| `symbol` | `relatedSymbols` | Required to match the requested ticker; mapped as a singleton because the verified row contains one string. |
| `publishedDate` | `publishedAt` | Required; parse `yyyy-MM-dd HH:mm:ss`. FMP does not document an offset, so interpret as UTC and record this assumption. |
| `publisher` | `sourceName` | Trimmed plain text; fall back to `site` when publisher is absent. |
| `title` | `headline` | Required, trimmed, HTML-unescaped and tag-stripped plain text. |
| `text` | `description` | Optional, trimmed, HTML-unescaped and tag-stripped plain text. Never replace it with the headline. |
| `url` | `articleUrl` | Required; canonicalization and HTTP(S) validation occur in the application service. |
| `site` | source fallback | Used only when `publisher` is blank. |
| `image` | none | Not persisted or exposed in Milestone 4. |
| none | `externalId` | `null`; the verified FMP response supplies no stable article ID. |

The verified response does not contain sentiment, topics, an external article
ID, or an array of related symbols. No such fields will be invented. The
provider-neutral model keeps `externalId` nullable and represents related
symbols as a set so a future adapter can supply them. Multi-company persistence
is achieved when the same canonical article is returned by separate
single-ticker requests.

## Application Contracts

### Provider boundary

```java
public interface NewsDataClient {
    NewsFetchResult getRecentNews(String ticker, int limit);
}
```

`NewsArticleData` contains only application fields: nullable external ID,
headline, nullable source name, raw article URL, nullable description,
publication timestamp, related symbols, retrieval timestamp, and provider
name. `NewsFetchResult` also carries the provider name, retrieval timestamp,
and a sanitized skipped-record count so partial provider success can be
represented without leaking FMP DTOs.

### Public endpoint

```http
GET /api/v1/stocks/{ticker}/news?limit=10
```

Successful response shape:

```json
{
  "ticker": "AAPL",
  "limit": 10,
  "providerName": "FMP",
  "retrievedAt": "2026-07-18T20:00:00Z",
  "articles": [
    {
      "id": 42,
      "headline": "Article headline",
      "sourceName": "TechCrunch",
      "url": "https://techcrunch.com/article",
      "publishedAt": "2026-06-06T14:13:36Z",
      "description": "Article snippet",
      "relatedSymbols": ["AAPL"]
    }
  ],
  "warnings": []
}
```

- The default limit is `10`; accepted values are `1..20` inclusive.
- `limit` reports the applied/requested value.
- Articles are ordered by `publishedAt DESC, id DESC`.
- `id` is the durable application article ID and can become a local source
  reference in a later milestone.
- Public DTOs contain no entities, join objects, FMP DTOs, raw JSON, provider
  image field, or credentials.
- A valid empty provider list is a successful response with `articles: []` and
  no warning. The company must still be resolvable.
- A mixed valid/invalid result returns valid articles plus one general warning
  containing only the number of skipped records.

## Error Behavior

| Condition | HTTP/code | Behavior |
|---|---|---|
| Invalid ticker | `400 INVALID_TICKER` | Existing ticker validation. |
| Limit outside `1..20` or non-numeric | `400 INVALID_LIMIT` | Safe application message. |
| Unknown company, when profile lookup can determine it | `404 STOCK_NOT_FOUND` | Existing behavior. |
| Missing FMP key | `503 DATA_UNAVAILABLE` | No HTTP request; safe message. |
| FMP 401/403 | `502 NEWS_PROVIDER_ERROR` | No raw body or credential leakage. |
| FMP 429 | `429 RATE_LIMITED` | Preserve a valid numeric `Retry-After` header. |
| FMP 5xx or timeout after bounded retry | `502 NEWS_PROVIDER_ERROR` | Bounded by existing FMP attempt configuration. |
| Malformed JSON | `502 NEWS_PROVIDER_ERROR` | No retry for unreadable data. |
| FMP 404 | `404 STOCK_NOT_FOUND` | Where the provider explicitly supplies it. |
| Mixed valid/invalid rows | `200` plus warning | Preserve valid rows and log only counts/reasons without content. |
| All non-empty rows invalid | `503 DATA_UNAVAILABLE` | Controlled provider-data error. |
| Valid empty list | `200` with empty articles | Empty news is not the same as malformed data. |
| Malformed/unsafe article URL | skip row, then apply mixed/all-invalid policy | Only absolute HTTP(S) article URLs are accepted. |

Spring's query-parameter conversion error for non-numeric `limit` will be
mapped to the same `INVALID_LIMIT` response. Unexpected persistence errors
remain safe `INTERNAL_ERROR` responses; normal uniqueness races are prevented
with PostgreSQL `ON CONFLICT DO NOTHING` writes and database constraints.

## Database Migration

Add one migration matching the sequence reserved by `docs/design.md`:

```text
V5__create_news_tables.sql
```

It creates both tables; it does not split the join table into `V6`, because
the design reserves `V6` for comparison briefs.

### `news_article`

| Column | Type/nullability | Constraint |
|---|---|---|
| `id` | identity bigint, not null | primary key |
| `external_id` | varchar(255), nullable | provider ID when available |
| `headline` | varchar(1000), not null | trimmed value must be nonblank |
| `source_name` | varchar(255), nullable | trimmed value nonblank when present |
| `article_url` | varchar(2048), not null | trimmed value must be nonblank |
| `description` | text, nullable | normalized plain text |
| `published_at` | timestamptz, not null | verified provider field |
| `retrieved_at` | timestamptz, not null | application `Clock` |
| `url_hash` | varchar(64), not null | unique lowercase SHA-256 hex |
| `provider_name` | varchar(64), not null | nonblank, `FMP` for this adapter |

Constraints and indexes:

- Unique `url_hash` constraint.
- Partial unique index on `(provider_name, external_id)` when
  `external_id IS NOT NULL`.
- Check constraints for required nonblank fields and 64-character lowercase
  hexadecimal hash format.
- Index on `(published_at DESC, id DESC)`.
- The unique URL constraint itself supports URL-hash lookup; no duplicate
  index is added.

### `news_article_company`

| Column | Constraint |
|---|---|
| `news_article_id` | FK to `news_article(id)` with `ON DELETE CASCADE` |
| `company_id` | FK to `company(id)` with `ON DELETE CASCADE` |

- Composite primary key `(news_article_id, company_id)` prevents duplicate
  relationships.
- Add `(company_id, news_article_id)` for company-first recent-news lookup.
- One news row may be associated with multiple companies.

## URL Canonicalization and Duplicate Prevention

Central canonicalization rules:

1. Trim surrounding whitespace.
2. Parse as an absolute URI.
3. Require `http` or `https`, a host, and no user-info component.
4. Lowercase the scheme and host using locale-independent rules.
5. Preserve port, raw path, and raw query exactly.
6. Remove the fragment.
7. Do not remove or reorder query parameters.
8. Compute lowercase SHA-256 hex from the UTF-8 canonical URL.

Malformed or unsafe URLs become invalid provider records. Equivalent URLs
differing only in scheme/host case, fragment, or surrounding whitespace share
one hash. Query-string differences remain distinct because no provider-backed
tracking-parameter rule has been verified.

Deduplication order:

1. `(providerName, externalId)` when a nonblank stable external ID exists.
2. Canonical URL hash.

The service collapses duplicate provider rows before writing. PostgreSQL
uniqueness is still authoritative. Article and join inserts use
`ON CONFLICT DO NOTHING`, then load the authoritative row, so repeated and
concurrent requests do not depend solely on in-memory checks. Existing article
content may be refreshed without changing its durable identity fields. The
join insert is separately idempotent, enabling one article to acquire another
company association.

## Security and Content Handling

- Reuse `FMP_API_KEY` and `FMP_BASE_URL`; add no key or configuration namespace.
- Never log a request URL or query string containing `apikey`.
- Log only ticker-independent event type, attempt/count, and exception type;
  never raw provider bodies, full article content, or credentials.
- Treat provider JSON and article URLs as untrusted.
- Convert `title`, `publisher`/`site`, and `text` to plain text using existing
  Spring HTML utilities plus tag stripping and whitespace normalization. This
  adds no dependency.
- Do not fetch, render, or follow returned article/image URLs.
- Test fixtures use only `test-api-key` and sanitized/example content.

## Expected Files

### Create

- `.agent/plans/04-news-integration.md`
- `backend/src/main/resources/db/migration/V5__create_news_tables.sql`
- `backend/src/main/java/com/stocklens/common/exception/InvalidNewsLimitException.java`
- `backend/src/main/java/com/stocklens/common/exception/NewsProviderException.java`
- `backend/src/main/java/com/stocklens/common/exception/NewsProviderRateLimitedException.java`
- `backend/src/main/java/com/stocklens/news/client/NewsDataClient.java`
- `backend/src/main/java/com/stocklens/news/client/model/NewsArticleData.java`
- `backend/src/main/java/com/stocklens/news/client/model/NewsFetchResult.java`
- `backend/src/main/java/com/stocklens/news/client/fmp/FmpNewsDataClient.java`
- `backend/src/main/java/com/stocklens/news/client/fmp/FmpNewsResponseMapper.java`
- `backend/src/main/java/com/stocklens/news/client/fmp/dto/FmpNewsResponse.java`
- `backend/src/main/java/com/stocklens/news/domain/NewsArticle.java`
- `backend/src/main/java/com/stocklens/news/repository/NewsArticleRepository.java`
- `backend/src/main/java/com/stocklens/news/service/CanonicalArticleUrlService.java`
- `backend/src/main/java/com/stocklens/news/service/NewsArticlePersistenceService.java`
- `backend/src/main/java/com/stocklens/news/service/NewsQueryService.java`
- `backend/src/main/java/com/stocklens/news/controller/NewsController.java`
- `backend/src/main/java/com/stocklens/news/dto/NewsArticleResponse.java`
- `backend/src/main/java/com/stocklens/news/dto/NewsResponse.java`
- `backend/src/main/java/com/stocklens/news/dto/NewsWarningResponse.java`
- `backend/src/test/resources/fixtures/fmp/news-success.json`
- Focused tests under `backend/src/test/java/com/stocklens/news/...` for the
  mapper, client, URL canonicalization, repository/persistence, query service,
  full service integration, and controller.

### Modify

- `backend/src/main/java/com/stocklens/common/web/GlobalExceptionHandler.java`
- `backend/src/test/java/com/stocklens/InfrastructureIntegrationTest.java`
- The execution plan progress, decision, evidence, and completion sections as
  work proceeds.

No production dependency, application configuration, existing entity, or
existing provider interface change is expected.

## Dependencies and Configuration

- No new production or test dependency is required.
- Reuse Spring Web MVC/Jackson/JPA, PostgreSQL, JUnit, Mockito, MockRestService,
  and Testcontainers already present.
- Reuse `stocklens.providers.fmp` properties and the qualified `fmpRestClient`.
- No Redis, AI, or new environment variable is introduced.

## Implementation Phases

### Phase 1: Schema and domain mapping

1. Add `V5__create_news_tables.sql` with both tables, constraints, foreign
   keys, and indexes.
2. Add the `NewsArticle` JPA entity and many-to-many company mapping.
3. Add repository lookups, conflict-safe native insert/association operations,
   and a two-step limited recent-query that avoids collection-fetch pagination.
4. Update the infrastructure migration assertion to expect V5 and both tables.
5. Add PostgreSQL integration tests for JPA/schema validation, URL and provider
   ID uniqueness, foreign keys, relationship uniqueness, ordering, limits,
   idempotency, and multi-company association.

### Phase 2: Provider-neutral boundary and URL normalization

1. Add `NewsDataClient`, `NewsArticleData`, and `NewsFetchResult`.
2. Add centralized canonical URL construction and SHA-256 hashing.
3. Test whitespace, case normalization, fragments, deterministic/equivalent
   hashes, distinct query strings, malformed URIs, unsafe schemes, and user
   info.

### Phase 3: FMP news adapter

1. Add the exact verified FMP DTO.
2. Add per-record validation, UTC publication-time parsing, provider symbol
   validation, plain-text normalization, optional-value handling, ordering,
   and skipped-record accounting.
3. Add the FMP HTTP adapter using `/news/stock`, `symbols`, `page=0`, `limit`,
   and `apikey`, reusing FMP configuration/timeouts/retries.
4. Translate auth, 404, 429, 4xx, 5xx, timeout, missing key, malformed JSON,
   valid empty, partial invalid, and all-invalid cases without exposing raw data.
5. Add sanitized fixtures and mock HTTP/mapper tests. Automated tests make no
   live request.

### Phase 4: Persistence and recent-news services

1. Resolve a normalized company from PostgreSQL; if absent, use the existing
   provider-neutral profile client and `CompanyService` so unknown tickers can
   be determined.
2. Validate limit before provider or persistence work.
3. Fetch provider data, canonicalize and hash each URL, add URL failures to the
   sanitized skipped count, and fail only when a non-empty response has no
   valid record.
4. Collapse duplicates by external ID then URL hash.
5. Upsert articles and company relationships transactionally with DB-backed
   conflict handling.
6. Reload at most the requested number of associated articles newest first and
   map entities to public DTOs.
7. Preserve a valid empty list and emit a general warning for partial success.

### Phase 5: Controller and API errors

1. Add `NewsController` under the existing stock base path.
2. Add news response records.
3. Add `INVALID_LIMIT`, `NEWS_PROVIDER_ERROR`, and news rate-limit handling to
   the existing global error contract.
4. Cover default/explicit/invalid limit, ticker, not-found, empty, provider
   errors, request IDs, ordering, and public field boundaries with MVC tests.

### Phase 6: End-to-end automated verification

1. Add a Spring Boot/Testcontainers service integration test with fake
   financial and news providers.
2. Verify lowercase/whitespace ticker normalization, company resolution,
   default/custom limits, empty results, repeat idempotency, partial records,
   newest-first ordering, and multi-company association.
3. Run focused tests while implementing, then the required full command.

### Phase 7: Safe manual verification and completion

1. If a local `FMP_API_KEY` is already available, start dependencies/backend
   without printing the key and call `/api/v1/stocks/AAPL/news?limit=3`.
2. Verify up to three newest-first articles, required public fields, no key in
   response/logs, and idempotent repeated persistence.
3. If account access or credentials prevent the smoke test, record the exact
   limitation; never fabricate live output.
4. Review the complete diff for scope, secrets, generated files, DTO leakage,
   HTML/URL safety, uniqueness, ordering, controller logic, and Milestone 5+
   work.
5. Update this plan with results and completion status.

## Testing Strategy

### Unit tests

- Exact FMP field mapping and UTC timestamp parsing.
- Required headline, URL, symbol, and publication timestamp validation.
- Optional source/description and absent external ID.
- Plain-text handling of actual and encoded HTML.
- Mixed valid/invalid and all-invalid provider records.
- URL canonicalization/hash equivalence and non-equivalence.
- Limit/ticker validation and query-service orchestration.
- Duplicate input collapse and warning counts.
- Safe provider-error translation.

### Mock HTTP provider tests

- Successful multi-row list response and request URI parameters.
- Empty list.
- Malformed JSON.
- Authentication, 404, 429 with/without valid `Retry-After`, other 4xx.
- Bounded 5xx and timeout retry.
- Missing key causes no request.
- Partial and all malformed row behavior using sanitized fixtures/data.

### PostgreSQL Testcontainers tests

- V5 is applied and Hibernate validates the exact schema.
- Unique URL hash and partial provider/external-ID uniqueness.
- Article/company foreign keys and composite relationship uniqueness.
- Recent IDs obey `publishedAt DESC, id DESC` and requested page size.
- Repeated persistence remains one article/one relationship.
- One article can be associated with two companies.

### MVC tests

- Response fields and newest-first article contract.
- Default and explicit limit delegation.
- `INVALID_TICKER`, `INVALID_LIMIT`, `STOCK_NOT_FOUND`, `DATA_UNAVAILABLE`,
  `RATE_LIMITED`, `NEWS_PROVIDER_ERROR`, and safe unexpected errors.
- Empty list and partial warning response.
- No persistence/provider internals in JSON.

## Validation Commands

Focused commands may be used during implementation:

```bash
cd backend && ./mvnw -Dtest='com.stocklens.news.**' test
cd backend && ./mvnw test
```

Required milestone validation:

```bash
cd backend && ./mvnw clean verify
git status --short
git diff --check
git diff --stat
git diff
```

Security/scope review uses tracked-file searches without printing environment
values:

```bash
rg -n "apikey=|FMP_API_KEY|api-key" backend .agent/plans/04-news-integration.md
git status --short --ignored
```

## Acceptance Criteria

- [x] V5 reproducibly creates both news tables with required constraints and
  indexes, and Hibernate validation passes against PostgreSQL.
- [x] `NewsArticle` stores only normalized application fields and supports
  multiple associated companies.
- [x] The official FMP response contract is represented exactly inside the FMP
  adapter; no unverified field is invented.
- [x] `/news/stock` uses one normalized ticker, `page=0`, the validated limit,
  and the existing FMP key/configuration.
- [x] Provider failures, rate limits, malformed data, missing configuration,
  empty data, and partial invalid records have explicit safe behavior.
- [x] URL canonicalization follows the documented minimal rules and SHA-256
  hash output is deterministic.
- [x] Repeated retrievals do not duplicate article or join rows, including
  under database uniqueness constraints.
- [x] One canonical article can relate to more than one company.
- [x] The public endpoint defaults to 10, accepts 1..20, rejects other limits,
  returns newest-first DTOs, and exposes no FMP/JPA/secret fields.
- [x] Mixed valid/invalid responses preserve valid articles with a sanitized
  warning; all-invalid non-empty responses fail in a controlled way.
- [x] Automated tests make no live provider calls and PostgreSQL integration
  tests use Testcontainers rather than H2.
- [x] `cd backend && ./mvnw clean verify` passes.
- [x] Complete diff, scope, generated-file, and credential reviews pass.
- [x] The plan records manual smoke-test success or the honest access/credential
  limitation.
- [x] No Milestone 5 or later work is implemented.

## Assumptions and Unresolved Decisions

### Assumptions

- FMP's documented timezone-less `publishedDate` represents UTC for normalized
  storage. This is necessary to map to `Instant`; the provider documentation
  does not publish a timezone on the inspected page.
- FMP's single `symbol` string identifies one related ticker per returned row.
  The docs do not verify an array or comma-separated per-row symbol contract.
- A valid empty FMP list means the company has no currently returned articles,
  not malformed provider data. Unknown tickers are determined by the existing
  company/profile resolution when possible.
- `publisher` is the preferred source label; `site` is only a fallback.
- The canonical URL, not the provider-returned raw URL, is persisted/exposed.
- Existing FMP timeout and retry configuration is appropriate for its news
  endpoint.

### Resolved during validation

- The configured local FMP account does not currently have access to
  `/stable/news/stock`: the safe direct smoke request returned HTTP 402.
- Because access was denied before a news list was returned, no sanitized live
  article fixture could be captured. The automated fixture remains based on
  the verified official API Viewer schema.
- The application endpoint returned `502 NEWS_PROVIDER_ERROR` with the standard
  safe error body. Local PostgreSQL contained `0` `news_article` rows and `0`
  join rows after the failed request, confirming no partial write.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| FMP plan does not permit stock news | Preserve the interface/adapter, return a controlled error, and record the live limitation. |
| Timezone-less publication strings are ambiguous | Parse the exact format as UTC and document the provider assumption. |
| Provider HTML reaches future UI | Normalize stored text to plain text and never fetch/render provider HTML here. |
| URL over-normalization merges distinct articles | Limit canonicalization to verified safe operations and preserve query strings. |
| Provider duplicates race across requests | Use in-memory collapse plus database uniqueness and conflict-safe inserts. |
| Collection-fetch pagination produces incorrect limits | Page article IDs first, then fetch relationships and restore ID order. |
| One bad row loses all good news | Skip invalid rows with count-only warnings; fail only if non-empty input yields no valid rows. |
| Raw errors or keys leak | Never log full FMP URLs/bodies; central handlers return fixed public messages. |
| Scope expands into comparison/caching/AI | Keep all work behind the supporting one-ticker endpoint and stop at Milestone 4 acceptance. |

## Rollback Strategy

- Code rollback removes only the new `news` package, news exceptions/handler
  branches, news tests/fixtures, and the infrastructure assertion update.
- Database rollback in a development environment removes the join table before
  the article table. Applied Flyway migrations are never edited; a production
  rollback would require a new forward migration.
- No existing source table is altered, so Milestones 1-3 remain independently
  usable if the news slice is disabled.
- No cache, AI, or frontend state requires rollback.

## Progress

- [x] Read all governing documents and the completed Milestone 3 plan.
- [x] Inspected the complete existing backend implementation and tests.
- [x] Verified the official FMP Search Stock News request parameters, list root,
  and exact sample response fields in the stable documentation/API Viewer.
- [x] Recorded scope, architecture, schema, API, mapping, error, partial-record,
  deduplication, security, testing, and completion decisions in this plan.
- [x] Phase 1: schema and domain mapping.
- [x] Phase 2: provider-neutral boundary and URL normalization.
- [x] Phase 3: FMP news adapter.
- [x] Phase 4: persistence and recent-news services.
- [x] Phase 5: controller and API errors.
- [x] Phase 6: end-to-end automated verification.
- [x] Phase 7: safe manual verification and completion review. The account
  returned HTTP 402, so controlled failure and zero partial writes were
  verified instead of a live article success payload.

## Decision Log

- 2026-07-18: Reuse FMP for news and use only the company-specific stable
  `/news/stock` endpoint. No general-news or press-release fallback.
- 2026-07-18: Use `V5__create_news_tables.sql` for both news tables because
  Section 22 reserves V6 for comparison briefs.
- 2026-07-18: Use `page=0` and the API limit `1..20`; no additional pages or
  date bounds are needed for the supporting recent-news endpoint.
- 2026-07-18: Treat FMP's verified list as a list root with fields `symbol`,
  `publishedDate`, `publisher`, `title`, `image`, `site`, `text`, and `url`.
- 2026-07-18: Keep `externalId` null for FMP because no stable ID is supplied.
- 2026-07-18: Interpret timezone-less FMP publication timestamps as UTC.
- 2026-07-18: Return a valid empty provider result as HTTP 200 with an empty
  article list; distinguish it from an all-invalid non-empty response.
- 2026-07-18: Preserve partial success and expose only a skipped-record count.
- 2026-07-18: Preserve all URL query parameters; normalize only whitespace,
  scheme/host case, and fragment removal before SHA-256 hashing.
- 2026-07-18: Use database conflict-safe article and relationship inserts in
  addition to response-level deduplication.
- 2026-07-19: Use `VARCHAR(64)` for `url_hash`; PostgreSQL's `CHAR(64)` reports
  a different JDBC type and does not satisfy Hibernate schema validation under
  `ddl-auto=validate`.
- 2026-07-19: Record the configured account's HTTP 402 as an FMP plan-access
  limitation. Do not add or silently switch to another news feed.

## Deviations

None. The provider boundary returns `NewsFetchResult` rather than a bare list
so the required partial-record warning metadata can remain provider-neutral.
This is a deliberate refinement of the request's illustrative “such as”
interface, not an architecture or scope deviation.

## Evidence

- Official FMP stable documentation: `Search Stock News API`.
- Official API Viewer endpoint: `/stable/news/stock?symbols=AAPL`.
- Verified documented parameters: `symbols`, `from`, `to`, `page`, `limit`;
  maximum 250 records and page maximum 100.
- Verified official sample fields and list root are recorded above.
- `./mvnw -DskipTests compile`: passed after the production slice was added.
- Focused non-container news tests: 23 passed, 0 failed.
- Focused PostgreSQL news integration tests: passed after validating the V5
  schema and aligning `url_hash` with Hibernate's expected JDBC type.
- `cd backend && ./mvnw clean verify`: passed on 2026-07-19 with 94 tests,
  0 failures, 0 errors, and a successfully repackaged JAR.
- The full run applied migrations V0-V5 to PostgreSQL 18.4 and validated the
  mappings with `ddl-auto=validate`.
- `git diff --check`: passed.
- Secret scan found no committed real API key; only configuration names,
  placeholders, and the explicit `test-api-key` fixture value are present.
- Generated/local files remain ignored: `.env`, IDE metadata, Maven `target`,
  frontend `dist`, and `node_modules` are not part of the change.
- Safe live application request:
  `/api/v1/stocks/AAPL/news?limit=3` returned HTTP 502 with code
  `NEWS_PROVIDER_ERROR` and no raw provider content.
- Safe direct contract/access check returned HTTP 402 from FMP. The response
  body was not printed or persisted in the repository.
- Post-request database check returned `0|0` for article and relationship
  counts. The locally started backend was then stopped cleanly.

## Completion Summary

Milestone 4 is implemented without beginning Milestone 5. V5 creates durable
news and company-association tables with URL/external-ID uniqueness, foreign
keys, ordering indexes, and idempotent join constraints. The provider-neutral
news boundary and FMP adapter use only the verified stable list schema and
documented first-page/limit parameters. Provider text is converted to plain
text; URLs are minimally canonicalized and SHA-256 hashed; mixed malformed
records are skipped with count-only warnings; all-invalid data fails safely.

The single-ticker news service resolves companies, validates limits, performs
conflict-safe article/relation upserts, and returns newest-first public DTOs at
`GET /api/v1/stocks/{ticker}/news?limit=10`. News-specific validation,
rate-limit, and provider errors use the existing API error envelope. All 94
backend tests pass. Live success could not be demonstrated because the current
FMP account returns HTTP 402 for this dataset; the abstraction, mock coverage,
controlled runtime error, and zero-partial-write behavior are verified.
