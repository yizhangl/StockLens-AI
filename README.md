# StockLens AI

StockLens AI is a full-stack application for comparing two public companies.
Milestone 1 establishes the Spring Boot, React/Vite, PostgreSQL, Redis, Flyway,
and CI foundation. Company comparison behavior is implemented in later
milestones.

## Prerequisites

- Java 21
- Docker with Docker Compose
- Node.js 24.14.0 and npm (see `.nvmrc`)

Maven is provided by the wrapper in `backend/`; a global Maven installation is
not required.

## Environment

The committed `.env.example` contains local development defaults and blank
future provider placeholders. Docker Compose and the backend use compatible safe
defaults, so a local `.env` is optional for the initial startup.

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
