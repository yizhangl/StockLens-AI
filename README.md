# StockLens AI

StockLens AI compares two companies in one dashboard. It contains company and market data, 
financial metrics, historical performance, recent news, and a AI brief (using OpenAI API).

The working full-stack MVP was completed in four days through a human-in-the-loop
workflow with **ChatGPT 5.6 Thinking as the product partner** and **Codex as the
engineering partner**.

Tip: StockLens AI is an educational research tool, not financial advice. It does not
recommend trades, predict prices, or provide personalized investment guidance.

## Product Preview

![StockLens AI comparison dashboard](docs/images/stocklens-final-ui1.png)
![StockLens AI comparison dashboard](docs/images/stocklens-final-ui2.png)
![StockLens AI comparison dashboard](docs/images/stocklens-final-ui3.png)
![StockLens AI comparison dashboard](docs/images/stocklens-final-ui4.png)

## Built in Four Days with ChatGPT and Codex

The core application was completed in four days using an AI-assisted,
milestone-based development process. This was not a one-shot prompt that generated
the entire project. Each phase moved through product planning, focused
implementation, automated validation, live testing, and human review.

### ChatGPT 5.6 Thinking — Product Partner

ChatGPT acted as the product and technical planning partner. It helped me:

- Turn the original financial-research idea into a focused MVP
- Decide which features belonged in the first version
- Compare architecture, provider, caching, and persistence trade-offs
- Convert product decisions into the approved [`docs/design.md`](docs/design.md)
- Break the project into small, testable milestones
- Write focused Codex implementation prompts with clear acceptance criteria
- Analyze logs, investigate failures, and narrow bugs into repair tasks

### Codex — Engineering Partner

Codex acted as the repository-level engineering partner. For each milestone, it:

- Read `AGENTS.md`, `docs/design.md`, and the relevant execution plan
- Implemented focused backend and frontend changes
- Added unit, integration, and regression tests
- Ran Maven, npm, lint, type-check, test, and build validation
- Reported completed work, remaining limitations, and validation results
- Applied focused fixes after live testing exposed real integration issues

### Human-in-the-Loop Workflow

I remained responsible for the product direction and final decisions. I tested the
real application, reviewed the UI and behavior, challenged incomplete results,
selected provider and architecture trade-offs, and decided what ChatGPT and Codex
should work on next.

```mermaid
flowchart LR
    Idea[Personal research problem] --> ChatGPT[ChatGPT 5.6 Thinking<br/>Ideation and product planning]
    ChatGPT --> Design[design.md and milestone requirements]
    Design --> Codex[Codex<br/>Repository implementation]
    Codex --> Validation[Automated tests and diff review]
    Validation --> Review[Human product review and live testing]
    Review -->|feedback or bug report| ChatGPT
    Review -->|approved milestone| Product[Working StockLens AI]
```

This workflow made it possible to move quickly while still keeping the project
modular, testable, source-grounded, and reviewable.


## Key Features

- Side-by-side comparison for two normalized stock symbols
- Raw price and normalized return chart modes across `1M`, `6M`, `1Y`, `5Y`, and
  `MAX`
- Grouped valuation, profitability, growth, and financial-health metrics
- Recent ticker-scoped Yahoo Finance news with deterministic relevance filtering
- Explicitly generated, source-grounded AI comparison briefs with cited claims
- Typed AI output validation, a single repair retry, and a 15-source limit
- Redis cache-aside reads with fresh PostgreSQL fallback
- Visible cached/new response status and data provenance
- Manual provider-data refresh without automatic AI regeneration
- Explicit AI regeneration when a new model response is wanted

## Technology Stack

| Area | Implemented stack |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Data JPA, Spring AI 2.0, Maven |
| Data | PostgreSQL 18, Redis 8, Flyway |
| Frontend | React 19, TypeScript 6, Vite 8, Recharts 3.9, standard CSS |
| Testing | JUnit, Mockito, PostgreSQL/Redis Testcontainers, Vitest 4, React Testing Library 16 |
| Providers | Financial Modeling Prep, unofficial Yahoo Finance news adapter, OpenAI |
| Local tooling | Docker Compose, Maven Wrapper, npm |

Exact dependency versions are recorded in [`backend/pom.xml`](backend/pom.xml),
[`frontend/package.json`](frontend/package.json), and the lockfiles.

## Architecture

```mermaid
flowchart LR
    Browser[Browser] --> React[React dashboard]
    React -->|ordinary dashboard load| API[Spring Boot API]
    API --> Services[Comparison and research services]
    Services -->|best-effort cache| Redis[(Redis)]
    Services -->|durable reads and writes| Postgres[(PostgreSQL<br/>source of truth)]
    Services -->|missing or stale data| FMP[Financial Modeling Prep]
    Services -->|missing or stale news| Yahoo[Yahoo Finance news]
    React -->|explicit Generate or Regenerate| Research[AI research action]
    Research --> Services
    Services -->|grounded persisted context; only when reuse misses| OpenAI[OpenAI]
    React -->|explicit Refresh data| Refresh[Refresh action]
    Refresh --> Services
```

OpenAI is not called during ordinary dashboard loading. Redis is optional and
disposable; a Redis outage may reduce performance but does not replace or bypass
the PostgreSQL durability boundary.

See [`docs/architecture.md`](docs/architecture.md) for package boundaries,
failure degradation, cache invalidation, and AI grounding details.

## Data Flow

### Dashboard request

For each feature, the backend reads in this order:

```text
Redis → fresh and usable PostgreSQL → provider only when missing or stale
```

The aggregated comparison is assembled from normalized application DTOs and can
then be cached as a canonical ticker-pair response.

### AI brief request

```text
persisted StockLens data
→ deterministic grounded context and input hash
→ Redis / fresh matching PostgreSQL brief reuse
→ OpenAI only when reuse misses or forceRefresh=true
→ typed validation and at most one repair
→ PostgreSQL persistence and best-effort Redis cache
```

### Manual refresh

```text
invalidate affected caches
→ refresh provider-backed sections
→ persist successful data
→ reload the dashboard
```

The normal dashboard refresh action does not regenerate the AI brief. AI
regeneration remains an explicit, separately costed action.

## Local Development

### Prerequisites

- Java 21
- Node.js 24 and npm (see [`.nvmrc`](.nvmrc))
- Docker Desktop or another Docker Compose-compatible runtime

Maven is provided by the wrapper; no global Maven installation is required.

### 1. Configure local environment

```bash
git clone <your-repository-url>
cd StockLens-AI
cp .env.example .env
```

Add your own `FMP_API_KEY` and `OPENAI_API_KEY` to the untracked `.env` file.
Set `OPENAI_MODEL` to a model available to your API account. Never commit
`.env`; a ChatGPT subscription is not an OpenAI API credential.

### 2. Start PostgreSQL and Redis

```bash
docker compose up -d
docker compose ps
```

The local defaults expose PostgreSQL on `5432` and Redis on `6379`.

### 3. Start the backend

The existing script loads the root `.env` and starts the Maven wrapper:

```bash
./scripts/run-backend.sh
```

Alternatively, export the required variables in your shell and run:

```bash
cd backend
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`; Actuator health is available at
`/actuator/health`.

### 4. Start the frontend

```bash
cd frontend
npm ci
npm run dev
```

Open the URL printed by Vite, normally `http://localhost:5173`. The development
server proxies `/api` and `/actuator` to port `8080`, so no frontend secret is
required. `VITE_API_BASE_URL` is only needed when building or running against a
different backend origin.

To stop local infrastructure without deleting named volumes:

```bash
docker compose down
```

## Environment Variables

| Variable | Requirement / default | Purpose |
|---|---|---|
| `FMP_API_KEY` | Required for FMP-backed refreshes | Financial Modeling Prep credential |
| `FMP_BASE_URL` | Optional; `https://financialmodelingprep.com/stable` | FMP API root |
| `FMP_CONNECT_TIMEOUT` / `FMP_READ_TIMEOUT` | Optional; `2s` / `5s` | FMP network timeouts |
| `FMP_MAX_ATTEMPTS` | Optional; `2` | Bounded transient FMP attempts |
| `YAHOO_FINANCE_BASE_URL` | Optional; `https://finance.yahoo.com` | Unofficial news adapter root |
| `YAHOO_FINANCE_CONNECT_TIMEOUT` / `YAHOO_FINANCE_READ_TIMEOUT` | Optional; `2s` / `5s` | Yahoo adapter timeouts |
| `YAHOO_FINANCE_MAX_ATTEMPTS` | Optional; `2` | Bounded transient Yahoo attempts |
| `OPENAI_API_KEY` | Required only for new AI generation | OpenAI API credential |
| `OPENAI_MODEL` | Required only for new AI generation | Account-accessible model name |
| `OPENAI_BASE_URL` | Optional; `https://api.openai.com/v1` | OpenAI-compatible API root used by Spring AI |
| `AI_COMPARISON_TEMPERATURE` | Optional; `0.2` | Structured comparison temperature |
| `AI_COMPARISON_MAX_TOKENS` | Optional; `1200` | Structured response token ceiling |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Local defaults in `.env.example` | Compose database and backend credentials |
| `SPRING_DATASOURCE_URL` | Optional; local PostgreSQL URL | Backend JDBC connection |
| `SPRING_DATA_REDIS_HOST` | Optional; `localhost` | Redis host; port is currently `6379` |
| `STOCKLENS_CACHE_*_TTL` | Optional; see `.env.example` | Per-category cache freshness durations |
| `VITE_API_BASE_URL` | Optional; empty/same origin | Frontend API origin override; never place secrets here |

Model availability and pricing depend on the OpenAI API account. Provider quotas,
licensing, and field availability likewise depend on the configured provider
accounts.

## API Overview

### Compare two companies

```http
GET /api/v1/comparisons?left=AAPL&right=MSFT&period=1Y&mode=RETURN
```

Returns company summaries, aligned performance, metric groups, recent news,
provenance, cached status, and typed partial-data warnings. Periods are `1M`,
`6M`, `1Y`, `5Y`, and `MAX`; modes are `PRICE` and `RETURN`.

### Generate or reuse a grounded brief

```http
POST /api/v1/comparisons/research
Content-Type: application/json

{"leftTicker":"AAPL","rightTicker":"MSFT","forceRefresh":false}
```

Returns structured advantages, risks, backend-resolved sources, generation
metadata, and `cached`. `forceRefresh: true` bypasses Redis and persisted-brief
reuse and creates a new historical brief row.

### Refresh provider data

```http
POST /api/v1/comparisons/refresh
Content-Type: application/json

{"tickers":["AAPL","MSFT"],"regenerateBrief":false}
```

Returns the normalized tickers and safe partial-refresh warnings. The frontend's
Refresh data action keeps `regenerateBrief` false.

Likely controlled errors include `INVALID_TICKER`, `DUPLICATE_TICKERS`,
`STOCK_NOT_FOUND`, `RATE_LIMITED`, `DATA_UNAVAILABLE`, provider errors,
`AI_PROVIDER_ERROR`, and `INVALID_AI_RESPONSE`. Error bodies include a request ID
without exposing raw provider or infrastructure details.

See [`docs/api.md`](docs/api.md) for concise response shapes and supporting stock
endpoints.

## AI Grounding and Safety

- AI reads persisted StockLens company, market, metric, history, and news data.
- The backend constructs deterministic `C`, `Q`, `M`, `P`, and `N` source IDs.
- The model returns typed structured output and only supplied source IDs.
- IDs are trimmed, blanks removed, deduplicated, and checked against the context.
- Nested claims may use at most 15 unique sources overall.
- One repair retry is allowed; a second invalid result is not persisted or cached.
- Source metadata comes from the backend context, never from model-provided URLs.
- Prompts reject personalized investment advice, guaranteed-return claims, and
  price predictions.

These controls reduce unsupported output but do not guarantee that model
analysis is correct. Users should inspect the cited source data.

## Caching and Freshness

Default TTLs are company `24h`, market `15m`, metrics `6h`, history `6h`, news
`30m`, assembled comparisons `15m`, and AI briefs `1h`. Exactly-at-TTL data is
stale. Successful empty-news retrievals are recorded distinctly from provider
failures, and Redis failures fall through to durable PostgreSQL reads.

See [`docs/architecture.md`](docs/architecture.md#caching-and-freshness) for
history completeness, brief reuse, refresh invalidation, and failure behavior.

## Testing

Backend verification uses mocked provider clients, a fake AI client, and real
PostgreSQL/Redis Testcontainers. Run it with external credentials removed:

```bash
cd backend
env -u FMP_API_KEY -u OPENAI_API_KEY ./mvnw clean verify
```

Frontend tests mock Fetch and require no running backend:

```bash
cd frontend
npm run lint
npm run typecheck
npm test
npm run build
```

Automated tests do not call paid or live providers.


## Known Limitations

- FMP quotas, fields, delays, and display rights depend on the account plan.
- Yahoo Finance news uses an unofficial replaceable adapter and is not a
  guaranteed licensed production contract.
- Quotes are latest-available/delayed data, not exchange-grade real-time data.
- OpenAI model access, latency, and cost depend on the API account.
- There is no authentication, scheduled refresh, or production deployment.
- Exchange and ticker support is constrained by validation and provider coverage.
- The Recharts dependency adds a comparatively large chart chunk, reviewed in
  the production build rather than hidden by raising Vite's warning threshold.
- Runtime OpenAPI/Swagger UI is not currently installed; the implemented public
  contract is documented in [`docs/api.md`](docs/api.md).
- The project is for educational and portfolio demonstration use only.

## Repository Guide

- [`docs/design.md`](docs/design.md) — approved product and system design
- [`docs/architecture.md`](docs/architecture.md) — implemented boundaries and flows
- [`docs/api.md`](docs/api.md) — public REST contracts
- [`.agent/plans/`](.agent/plans/) — milestone execution records
