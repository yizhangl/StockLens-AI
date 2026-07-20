# StockLens AI

StockLens AI is a full-stack application for comparing two public companies.
The current backend foundation includes Spring Boot, PostgreSQL, Redis, Flyway,
and Financial Modeling Prep-backed company profiles, quotes, financial metrics,
and historical daily prices. Recent stock news comes from an unofficial,
replaceable Yahoo Finance ticker-news endpoint.
The comparison dashboard remains a later milestone.

## Prerequisites

- Java 21
- Docker with Docker Compose
- Node.js 24.14.0 and npm (see `.nvmrc`)

Maven is provided by the wrapper in `backend/`; a global Maven installation is
not required.

## Environment

The committed `.env.example` contains local development defaults and a blank
`FMP_API_KEY`. Docker Compose and backend health can start without an FMP key;
provider-backed stock requests require one.

If you create `.env`, keep it local. It is ignored by Git and must never contain
committed credentials.

## Start Local Infrastructure

From the repository root:

```bash
docker compose up -d
docker compose ps
```

The Compose stack starts PostgreSQL on port `5432` and Redis on port `6379`.

## Start the Backend

In a separate terminal:

```bash
cd backend
./mvnw spring-boot:run
```

Verify backend health:

```bash
curl --fail --silent http://localhost:8080/actuator/health
```

With `FMP_API_KEY` available in the backend process environment, retrieve and
persist a company profile and latest quote without printing the key:

```bash
curl --fail --silent http://localhost:8080/api/v1/stocks/AAPL
```

Retrieve normalized financial metrics and a one-year daily price series:

```bash
curl --fail --silent http://localhost:8080/api/v1/stocks/AAPL/metrics
curl --fail --silent 'http://localhost:8080/api/v1/stocks/AAPL/history?period=1Y'
```

Supported history periods are `1M`, `6M`, `1Y`, `5Y`, and `MAX`. The integration
uses FMP stable profile, quote, TTM ratio/key-metric, annual financial-growth,
and full EOD history endpoints. FMP's inspected full EOD response does not
provide adjusted close, so historical returns use close. Forward P/E, revenue
TTM, and beta remain null rather than being derived from unrelated fields.
Endpoint access and public or multi-user display remain subject to the applicable
FMP subscription and data-display license.

Retrieve recent ticker-scoped news from Yahoo Finance:

```bash
curl --fail --silent 'http://localhost:8080/api/v1/stocks/AAPL/news?limit=3'
```

Yahoo Finance is used only for recent news and requires no application API key.
This is an unofficial endpoint intended for local development, education,
portfolio demonstration, and low-volume MVP usage. It is not guaranteed to be
stable or commercially licensed for long-term production use. The backend does
not scrape publisher pages, store browser state, or use cookies/crumb tokens;
future production use should evaluate a licensed news provider.

## Start the Frontend

In another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open the local URL printed by Vite, normally `http://localhost:5173`.

## Run Checks

Backend:

```bash
cd backend
./mvnw test
./mvnw verify
```

Frontend:

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm test -- --run
npm run build
```

## Stop Local Infrastructure

After stopping the backend and frontend processes:

```bash
docker compose down
```

This keeps the named PostgreSQL and Redis volumes for the next local session.
