# StockLens AI

StockLens AI is a full-stack application for comparing two public companies.
The current backend foundation includes Spring Boot, PostgreSQL, Redis, Flyway,
and Financial Modeling Prep-backed company profiles and latest market quotes.
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

The integration uses FMP's current `/stable/profile` and `/stable/quote`
endpoints. Public or multi-user display remains subject to the applicable FMP
subscription and data-display license.

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
