# StockLens AI Agent Instructions

## 1. Source of Truth

- Read this file before making changes.
- Read `docs/design.md` before planning or implementing features.
- The approved UI reference is stored in `docs/images/stocklens-final-ui.png`.
- `docs/design.md` defines product scope, architecture, APIs, data models,
  caching behavior, AI grounding rules, and non-goals.
- Do not change `docs/design.md` unless explicitly requested.
- If code, task instructions, and the design document conflict, stop and
  report the conflict before implementing.

## 2. MVP Scope

StockLens AI is a full-stack application for comparing two public companies.

The MVP includes:

- Two-stock symbol search
- Company overview cards
- Financial and valuation metrics
- Historical price and return comparison
- Recent company news
- Source-grounded AI comparison brief
- PostgreSQL persistence
- Redis caching
- Automated tests
- Docker-based local development
- CI and deployment

Do not add authentication, trading, portfolio management, price prediction,
RAG, vector databases, Kafka, microservices, or Kubernetes unless explicitly
requested.

## 3. Technology Stack

Backend:

- Java 21
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Redis
- Flyway
- Spring AI
- Maven
- JUnit 5
- Mockito
- Testcontainers

Frontend:

- React
- TypeScript
- Vite
- A charting library approved in the design
- Standard CSS or the approved component library

Infrastructure:

- Docker Compose
- GitHub Actions

## 4. Architecture Rules

- Keep the backend as a modular monolith.
- PostgreSQL is the durable source of truth.
- Redis is an optional cache and must not be required for core reads.
- Keep provider-specific DTOs outside domain entities.
- Access external providers through interfaces.
- Do not expose provider response objects from controllers.
- Controllers handle HTTP concerns only.
- Business logic belongs in services.
- Use Flyway for schema changes.
- Use `spring.jpa.hibernate.ddl-auto=validate`.
- Use `BigDecimal` for monetary and financial values.
- Do not store formatted values such as `$2.9T` in the database.
- AI output must be structured and validated.
- AI source IDs must reference supplied source records.
- Do not generate personalized investment advice or price predictions.

## 5. Development Workflow

For changes affecting multiple layers, more than five files, or expected to
take more than one hour, create or update an execution plan under:

`.agent/plans/`

Before coding:

1. Read the relevant design sections.
2. Inspect the existing implementation and tests.
3. State assumptions and unresolved questions.
4. List files expected to change.
5. Create or update the execution plan.
6. Do not write code until the plan is coherent.

During implementation:

1. Implement the smallest complete slice.
2. Stay within the requested milestone.
3. Do not introduce unrelated refactors.
4. Add tests with production code.
5. Keep the execution plan updated.

After implementation:

1. Run relevant tests.
2. Review the complete diff.
3. Check for secrets and generated files.
4. Confirm acceptance criteria.
5. Report changed files, test results, and remaining work.

## 6. Commands

Backend:

- `cd backend && ./mvnw test`
- `cd backend && ./mvnw verify`
- `cd backend && ./mvnw spring-boot:run`

Frontend:

- `cd frontend && npm install`
- `cd frontend && npm run lint`
- `cd frontend && npm test`
- `cd frontend && npm run build`
- `cd frontend && npm run dev`

Infrastructure:

- `docker compose up -d`
- `docker compose ps`
- `docker compose down`

## 7. Testing Rules

- Do not call paid external APIs in automated tests.
- Use mock HTTP responses for provider clients.
- Use Testcontainers for PostgreSQL and Redis integration tests.
- Use a fake AI client in CI.
- Test invalid symbols, provider failures, malformed responses, and empty data.
- Do not rely only on happy-path tests.
- Do not claim completion while tests are failing.

## 8. Security Rules

- Never commit API keys or `.env` files.
- Do not log secrets.
- Keep secrets in environment variables.
- Treat external API and model responses as untrusted.
- Validate all ticker inputs.
- Ask before adding a new production dependency.

## 9. Completion Report

At the end of each task, report:

- Summary of behavior implemented
- Files changed
- Tests added
- Commands run and results
- Known limitations
- Remaining plan items