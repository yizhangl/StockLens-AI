# Milestone 7: Source-Grounded AI Comparison Brief

**Status:** Completed  
**Milestone:** 7 — AI Comparison Brief  
**Created:** 2026-07-21  
**Last Updated:** 2026-07-21  
**Design References:** `docs/design.md` Sections 8, 9, 13–16, 20, 22–25, and 28

## 1. Goal

Add a separately generated, source-grounded AI comparison brief for a persisted
pair of companies. `POST /api/v1/comparisons/research` must load only durable
PostgreSQL records, call OpenAI through Spring AI only after source sufficiency
is established, validate the typed response, persist the validated brief and
its cited source links, and return a provider-neutral public contract. The
dashboard remains independently loaded and only requests an AI brief after an
explicit user action.

## 2. Background and Current State

Milestones 1–6 are implemented. The existing dashboard endpoint is intentionally
not reusable here: `ComparisonService` refreshes profiles, market data, metrics,
history, and news through FMP/Yahoo. The research path will instead use
repositories for `Company`, latest `MarketSnapshot`, latest
`FinancialMetricSnapshot`, `HistoricalPrice`, and `NewsArticle` rows. The FMP
account is rate-limited, but persisted AAPL/MSFT data is available.

Spring Boot is 4.1.0. Spring AI 2.0.x officially supports Spring Boot 4.0.x and
4.1.x; use its managed `spring-ai-starter-model-openai` starter with `ChatClient`.
No Spring Boot upgrade, OpenAI SDK, second AI provider, or live service is used
by automated tests.

## 3. Scope

### In Scope

- Read-only persisted source loader and deterministic bounded context builder.
- Stable context-local `C`, `Q`, `M`, `P`, and `N` source identifiers.
- `stock-comparison-v1` centralized prompt template and SHA-256 input hash.
- Provider-neutral AI client, Spring AI OpenAI adapter, typed output, semantic
  validation, and exactly one repair retry.
- Comparison brief/source Flyway schema, JPA persistence, and research API.
- Explicit frontend Generate/Regenerate AI section with safe citations.
- Backend fake-client and PostgreSQL Testcontainers coverage; frontend mocked
  Fetch coverage.

### Out of Scope

- Redis/cache-aside, input-hash reuse, stored-brief reuse, refresh, TTLs,
  background work, streaming, chat memory, RAG, tools, embeddings, web search,
  recommendations, forecasts, authentication, deployment, and Milestone 8+.

## 4. Assumptions

- A brief requires both companies, current metrics for both, one non-null metric
  shared by both, and at least one market, common 1Y history, or news category.
- Missing optional categories become explicit context limitations; insufficient
  source sets return `DATA_UNAVAILABLE` before calling the AI client.
- `forceRefresh` is deliberately omitted: design section 16.2 shows it as an
  example, while this milestone assigns refresh/reuse to Milestone 8.
- `OPENAI_API_KEY` and `OPENAI_MODEL` are optional at startup; production calls
  without them return a controlled `DATA_UNAVAILABLE` response.

## 5. Open Questions / Blockers

- None. Docker-backed validation passed after Docker Desktop was started.
- The optional live OpenAI smoke test is intentionally skipped: no real key was
  supplied, and every successful M7 request creates a billable model call.

## 6. Acceptance Criteria

- [x] Research loads PostgreSQL only and never calls FMP/Yahoo/ComparisonService.
- [x] Context/source IDs/order/limits/hash are deterministic and grounded.
- [x] Structured response is validated, repaired once at most, and unsafe or
  invalid output is never persisted or exposed.
- [x] Valid briefs and cited source relationships persist in canonical ticker order.
- [x] POST response preserves requested display order and resolves source metadata
  from server context; it is never taken from the model.
- [x] Dashboard retains independent loading and supports explicit generation,
  errors, retry, reset, and safe citations.
- [x] Backend/frontend validation passes without API keys or external network.
- [x] No secret, generated file, provider refresh, or Milestone 8 behavior is added.

## 7. Expected Files

### Create

- `backend/src/main/java/com/stocklens/research/**`
- `backend/src/main/resources/db/migration/V6__create_comparison_brief_tables.sql`
- focused research unit/controller/repository/integration tests
- frontend AI API, hook, component, and tests

### Modify

- `backend/pom.xml`, `backend/src/main/resources/application.yml`, `.env.example`
- repository interfaces needed for read-only source queries
- global exception handling
- comparison page/types/styles and this plan

### Delete

- None. The Milestone 6 placeholder is replaced in place.

## 8. API / Schema / Configuration Changes

### API

`POST /api/v1/comparisons/research` accepts `{ leftTicker, rightTicker }` and
normalizes both tickers. It returns a newly generated brief every successful
call, with request-order tickers, four category results, risks, server-resolved
sources, `modelName`, `promptVersion`, `generatedAt`, `dataCutoffAt`, and
`cached: false`. Invalid/duplicate/unknown tickers retain current error behavior.
Missing configuration or source data returns `503 DATA_UNAVAILABLE`; AI rate
limits return `429 RATE_LIMITED`; provider failures return `502 AI_PROVIDER_ERROR`;
twice-invalid model output returns `502 INVALID_AI_RESPONSE`.

### Database

`V6__create_comparison_brief_tables.sql` adds `comparison_brief` (canonical
company pair, JSONB advantages/risks, model/prompt/timestamps/hash) and
`comparison_brief_source` (one unique local source reference per brief, nullable
links to cited news/financial/market snapshots). It has pair/generated/hash and
source-link indexes; it intentionally permits multiple briefs for one hash.

### Configuration and Dependencies

`OPENAI_API_KEY`, `OPENAI_MODEL`, optional `OPENAI_BASE_URL`,
`AI_COMPARISON_TEMPERATURE`, and `AI_COMPARISON_MAX_TOKENS` are environment
backed. `spring-ai-starter-model-openai` is the sole new production dependency,
managed by Spring AI BOM 2.0.0 (compatible with Boot 4.1). It provides the
approved `ChatClient` adapter; no OpenAI SDK is needed.

## 9. Implementation Plan

### Phase 1: Research persistence and read-only sources

Add V6/JPA/repositories and a database-only source loader. Load latest snapshots,
all persisted history needed for a deterministic 1Y common-return summary, and
at most five newest news articles per company. Enforce source sufficiency before
AI invocation.

### Phase 2: Context, prompt, AI boundary, validation

Build ordered grounded sources with sanitized bounded text; calculate data cutoff
and hash. Add centralized `stock-comparison-v1` prompt, neutral client boundary,
conditional Spring AI adapter, typed output validation, and a single repair call.

### Phase 3: Service, persistence, REST contract

Generate outside a write transaction; validate then persist brief and used source
links in a bounded transaction. Add thin controller and sanitized error mapping.

### Phase 4: Frontend explicit AI experience

Replace the placeholder with an explicit section/hook/client. It must not load on
dashboard/period/mode changes, must abort/reset when the ticker pair changes,
preserve an old successful brief after regeneration failure, and safely render
source metadata.

### Phase 5: Validation and audit

Run `cd backend && ./mvnw clean verify`, then frontend lint/typecheck/test/build;
review the complete diff and secrets/generated-file status. Run a live smoke only
when a real OpenAI key is supplied.

## 10. Testing Strategy

- Unit: loader sufficiency, stable context/IDs/limits/injection delimiters/hash,
  validator edge cases/advice language, repair paths, and service ordering/persistence.
- Integration: PostgreSQL Testcontainers migration/JPA JSONB/source-link/FK and
  HTTP endpoint with a fake `ComparisonAiClient`; mocks verify no provider use.
- Frontend: mocked fetch for explicit action/loading/errors/regeneration/reset,
  source mapping, and unchanged dashboard behavior.

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| FMP is rate limited | Cannot refresh data | Research reads persisted rows only. |
| Model produces invalid/unsafe claims | Incorrect output | Typed schema, semantic validation, one repair, no persistence on failure. |
| Prompt injection in source text | Model instruction hijack | Delimited normalized data, explicit untrusted-data instruction, bounded fields. |
| Missing credentials | Runtime generation unavailable | Startup remains healthy; endpoint returns controlled 503; fake client tests. |

## 12. Rollback / Recovery

Revert the application change and deploy against a database retaining V6; Flyway
migrations are forward-only. Invalid briefs are never persisted. Later migration
work would be required only if physical table removal became necessary.

## 13. Progress

- [x] Current implementation, migrations, contracts, frontend, and diff inspected
- [x] Plan reviewed and marked In Progress
- [x] Phase 1 complete
- [x] Phase 2 complete
- [x] Phase 3 complete
- [x] Phase 4 complete
- [x] Tests and validation complete
- [x] Diff reviewed and acceptance criteria confirmed

## 14. Decision Log

| Date | Decision | Reason | Alternatives |
|---|---|---|---|
| 2026-07-21 | Use Spring AI BOM/starter 2.0.0 | Officially supports Boot 4.1 and supplies ChatClient/OpenAI integration. | Boot upgrade, OpenAI SDK, manual HTTP. |
| 2026-07-21 | Separate persisted loader | Existing ComparisonService performs provider refreshes. | Modifying/reusing it (rejected). |
| 2026-07-21 | Omit forceRefresh/reuse | M8 owns refresh, caching, and reuse. | Adding premature API/cache behavior. |

## 15. Deviations from Design

- None. `forceRefresh` is not implemented because the milestone-specific task
  explicitly defers its behavior; the design presents it as an example request.

## 16. Validation Evidence

| Command | Result | Notes |
|---|---|---|
| `cd backend && ./mvnw clean verify` | PASS | 130 tests passed, including PostgreSQL/Redis Testcontainers and Flyway V6 validation. |
| `cd backend && ./mvnw test -Dtest='AiComparisonValidatorTest,ComparisonResearchControllerTest,ComparisonServiceTest,ComparisonControllerTest'` | PASS | Focused new and existing non-container regressions pass. |
| `cd frontend && npm run lint` | PASS | No lint errors. |
| `cd frontend && npm run typecheck` | PASS | Strict TypeScript passes. |
| `cd frontend && npm test` | PASS | 8 files, 28 tests. |
| `cd frontend && npm run build` | PASS | Production build succeeds; existing Vite bundle-size advisory remains. |
| `git diff --check` | PASS | No whitespace errors; provider/cache/secrets scope review passed. |

## 17. Completion Summary

### Implemented

- PostgreSQL-only source loading, ordered grounded context, source references,
  1Y common-date return summary, bounded untrusted text handling, and input hash.
- `stock-comparison-v1`, provider-neutral client, Spring AI OpenAI adapter,
  typed output, semantic validation, and one repair attempt.
- V6 brief/source persistence, canonical storage ordering, public research POST,
  and controlled errors.
- Explicit frontend Generate/Regenerate section with cancellation, safe sources,
  section-level errors, and dashboard-preserving behavior.

### Known Limitations

- Live OpenAI behavior is not exercised automatically or manually without a key.
- The existing Vite build reports its informational >500 kB bundle advisory.

### Remaining Work

- Milestone 8 may add cache/reuse/refresh behavior. It was not started.
