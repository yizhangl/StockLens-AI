# Repository and Infrastructure

**Status:** Completed
**Milestone:** 1 — Repository and Infrastructure
**Created:** 2026-07-18
**Last Updated:** 2026-07-18
**Design References:** `docs/design.md` Sections 9.5, 12, 14, 16.5, 22–25, 28, and 30

## 1. Goal

Establish a reproducible monorepo foundation for StockLens AI with a Java 21
Spring Boot backend, a React/TypeScript/Vite frontend, local PostgreSQL and
Redis services, Flyway-owned schema initialization, an Actuator health
endpoint, smoke-level automated verification, and a two-job GitHub Actions CI
skeleton.

At the end of this milestone, a developer must be able to install dependencies,
start PostgreSQL and Redis with Docker Compose, start both applications with the
documented commands, observe a healthy backend, and run the backend and frontend
checks without any company, provider, AI, dashboard, authentication, or
deployment implementation.

## 2. Background and Current State

`docs/design.md` defines a modular-monolith backend and React frontend in one
repository. Section 12 fixes Java 21, Spring Boot, Maven, React, TypeScript,
Vite, PostgreSQL, Redis, Flyway, Testcontainers, npm, Docker Compose, and GitHub
Actions as the relevant Milestone 1 technologies. Section 14 places application
code under `backend/` and `frontend/`, migrations under
`backend/src/main/resources/db/migration/`, and shared local configuration at
the repository root. Section 22 makes Flyway the schema owner and requires
`spring.jpa.hibernate.ddl-auto=validate`. Sections 24 and 25 define the expected
local startup flow and separate backend/frontend CI checks. Section 28 limits
Milestone 1 to repository and infrastructure foundations.

Current repository inspection on 2026-07-18 found:

- `backend/` and `frontend/` exist but are empty.
- `README.md` contains only the project title.
- `.agent/plans/` contains no existing milestone plan.
- No Maven, npm, Docker Compose, environment-example, or CI configuration exists.
- The tracked project files are planning/design documents and the approved UI
  reference; there is no implementation or test pattern to preserve yet.
- The worktree already contains unrelated user changes: `docs/api-contract.md`
  is deleted and `.idea/` is untracked. They are outside this milestone and must
  not be altered, restored, staged, or included in the milestone diff.

This plan is self-contained for Milestone 1, but it does not replace the broader
architecture, data model, or milestone order in `docs/design.md`.

## 3. Scope

### In Scope

- Create the Maven-based Java 21 Spring Boot application shell under `backend/`.
- Create the npm-based React, TypeScript, and Vite application shell under
  `frontend/`.
- Add root ignore, editor, environment-example, and local-development
  documentation appropriate for a monorepo.
- Define PostgreSQL and Redis Docker Compose services with persistent local data
  and service health checks.
- Configure the backend datasource, JPA validation, Redis connectivity, Flyway,
  and the Actuator health endpoint using environment variables with safe local
  defaults.
- Add a no-domain Flyway baseline migration while preserving the design's
  `V1__create_company_table.sql` filename for Milestone 2.
- Add backend infrastructure integration tests and a minimal frontend component
  smoke test.
- Add a basic GitHub Actions workflow with independent backend and frontend jobs.
- Document and manually verify the intended local startup, health-check, test,
  lint, build, and shutdown commands.

### Out of Scope

- Company or market entities, repositories, services, migrations, or endpoints.
- Financial, market, or news provider interfaces, clients, SDKs, fixtures, or
  live calls.
- Spring AI, OpenAI clients, prompts, schemas, API keys in use, or AI tests.
- Comparison APIs, orchestration, caching behavior, ticker validation, or public
  application controllers.
- The comparison dashboard, charts, financial UI, provider UI, or approved
  screenshot implementation.
- Authentication, users, watchlists, authorization, or session state.
- OpenAPI/Swagger setup; this belongs to a later milestone when public endpoints
  exist.
- Deployment workflows, cloud services, production configuration, or hosted
  environments.
- Recharts and any other dashboard-only frontend dependency.
- Changes to `docs/design.md` or the approved UI reference.
- Changes to unrelated existing worktree entries, including
  `docs/api-contract.md` and `.idea/`.

## 4. Assumptions

- The backend Maven coordinates will be `com.stocklens:stocklens-backend`, with
  the main package `com.stocklens` as specified by Section 14.
- Maven Wrapper and npm with a committed `package-lock.json` will make builds
  reproducible; no globally installed Maven is required.
- PostgreSQL is required for backend startup because it is the durable source of
  truth and Flyway must validate the schema. Redis is an optional cache in the
  final architecture, but this milestone verifies that a configured Redis
  connection works when Redis is available.
- Docker is available both locally for manual Compose checks and on the GitHub
  hosted runner for Testcontainers.
- Local-only example credentials are non-secret, clearly labeled, and may have
  safe defaults. Real credentials and `.env` remain untracked.
- The root `README.md` is the canonical location for startup commands until a
  later documentation milestone expands it.
- The frontend will render only a small StockLens AI placeholder/shell needed to
  prove React mounting and testing; it will not resemble or implement the
  comparison dashboard.
- Provider and OpenAI variable names from Section 24 may appear as blank,
  explicitly unused future placeholders in `.env.example`; no Milestone 1 code
  may read or require them.

## 5. Open Questions / Blockers

### Blocking before implementation begins

- Resolved on 2026-07-18: Spring Boot `4.1.0` and Maven Wrapper `3.3.4` were
  selected from current official stable releases. The user approved the planned
  production dependency set before implementation began.
- Resolved on 2026-07-18: Node.js `24.14.0`, React `19.2.7`, Vite `8.1.x`,
  and TypeScript `6.0.2` are pinned through `.nvmrc`, `package.json`, and
  `package-lock.json`.
- Resolved on 2026-07-18: PostgreSQL `18.4-alpine` and Redis `8.8.0-alpine`
  are pinned in Docker Compose.

### Resolved implementation decisions

- Use conventional local ports `5432`, `6379`, `8080`, and Vite's default
  development port unless repository or machine inspection reveals a conflict.
- Keep Actuator's default aggregate health semantics for this milestone: with
  PostgreSQL and Redis running, `/actuator/health` must be `UP`; Redis-down
  cache fallback behavior and any health-group refinement belong to Milestone 8.
- Do not introduce a backend formatting plugin in the initial CI skeleton unless
  separately approved. Maven compilation/tests and frontend ESLint/typecheck are
  sufficient Milestone 1 quality gates.

These items do not change the approved architecture. Exact versions and the
Milestone 1 production dependencies were approved before implementation.

## 6. Acceptance Criteria

- [x] `backend/` is a Java 21 Maven Wrapper project with a single
  `com.stocklens.StockLensApplication` entry point and no feature/domain code.
- [x] The backend dependency set includes only the infrastructure needed for
  web startup, JPA/PostgreSQL, Redis, Flyway, Actuator, and tests; it excludes
  Spring AI, provider SDKs, security/authentication, and deployment libraries.
- [x] `frontend/` is an npm-locked React/TypeScript/Vite project with lint,
  typecheck, test, build, and development scripts and no dashboard implementation.
- [x] Root repository configuration ignores build products, IDE files, local
  environment files, logs, coverage, and dependency directories without hiding
  source, wrapper, lock, migration, or example-environment files.
- [x] `.env.example` contains only documented local placeholders/defaults and no
  real credentials; `.env` is ignored.
- [x] `docker compose config` succeeds and defines only PostgreSQL and Redis,
  both with pinned images, named volumes, health checks, and ports/configuration
  consistent with backend defaults.
- [x] With the Compose services healthy, the backend starts, connects to
  PostgreSQL and Redis, applies Flyway migrations, and returns HTTP 200 with
  `UP` from `GET /actuator/health`.
- [x] Hibernate is configured with `ddl-auto=validate`; application startup does
  not create or mutate domain tables through Hibernate.
- [x] `V0__baseline.sql` is recorded successfully by Flyway and creates no
  company or other domain tables; `V1` remains available for the company-table
  migration specified by Section 22 and Milestone 2.
- [x] Backend integration tests use PostgreSQL and Redis Testcontainers and
  verify application context startup, Flyway application, database connectivity,
  Redis connectivity, and Actuator health without live external services.
- [x] A Vitest/React Testing Library test proves that the minimal frontend shell
  renders, and frontend lint, typecheck, test, and production build all pass.
- [x] `.github/workflows/ci.yml` runs independent backend and frontend jobs on
  pull requests and pushes to `main`; it performs no deployment.
- [x] `README.md` documents prerequisite tools, environment setup, dependency
  startup, backend/frontend startup, health verification, all automated checks,
  and non-destructive shutdown.
- [x] Every command listed in Sections 9 and 10 is run where the local environment
  supports it, and its actual result is recorded in `Validation Evidence`.
- [x] The complete diff is reviewed against `AGENTS.md` and `docs/design.md`, and
  contains no secrets, generated build output, unrelated changes, or work from a
  later milestone.

## 7. Expected Files

This list is an estimate and must be updated if a generator produces materially
different paths. Generated examples/assets that do not support the minimal shell
should be removed rather than retained as noise.

### Create

Root and infrastructure:

- `.editorconfig`
- `.env.example`
- `.gitignore`
- `.github/workflows/ci.yml`
- `.nvmrc`
- `docker-compose.yml`

Backend:

- `backend/pom.xml`
- `backend/mvnw`
- `backend/mvnw.cmd`
- `backend/.mvn/wrapper/maven-wrapper.properties`
- `backend/.mvn/wrapper/maven-wrapper.jar` only if required by the selected
  Maven Wrapper distribution
- `backend/src/main/java/com/stocklens/StockLensApplication.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/V0__baseline.sql`
- `backend/src/test/java/com/stocklens/StockLensApplicationTests.java`
- `backend/src/test/java/com/stocklens/InfrastructureIntegrationTest.java`
- `backend/src/test/java/com/stocklens/support/IntegrationTestContainers.java`

Frontend:

- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/index.html`
- `frontend/eslint.config.js`
- `frontend/tsconfig.json`
- `frontend/tsconfig.app.json`
- `frontend/tsconfig.node.json`
- `frontend/vite.config.ts`
- `frontend/src/main.tsx`
- `frontend/src/App.tsx`
- `frontend/src/App.test.tsx`
- `frontend/src/setupTests.ts`
- `frontend/src/index.css`
- `frontend/src/vite-env.d.ts`

### Modify

- `README.md`
- `.agent/plans/01-repository-and-infrastructure.md` throughout implementation
  to record status, progress, decisions, deviations, and validation evidence

### Delete

- None from the current tracked repository.
- Any disposable example logo/counter assets created by the Vite scaffolder may
  be omitted before the scaffold is committed; this does not authorize deleting
  pre-existing user files.

## 8. API / Schema / Configuration Changes

### API

- Expose only Spring Boot Actuator's `GET /actuator/health` endpoint.
- Expected success with both dependencies available: HTTP 200 and a response
  whose aggregate status is `UP`.
- Do not add `/api/v1` controllers or application response DTOs in this milestone.
- Expose only the health endpoint from Actuator; do not expose environment,
  configuration-properties, heap-dump, or other sensitive management endpoints.

### Database

- Configure Flyway's migration path as
  `classpath:db/migration`, corresponding to
  `backend/src/main/resources/db/migration/`.
- Add `V0__baseline.sql` as a versioned, no-domain migration. It should contain
  an explanatory SQL comment and make no company, snapshot, news, comparison,
  user, or infrastructure metadata tables. Flyway's own schema-history table is
  the only expected table after Milestone 1.
- Reserve the design's `V1__create_company_table.sql` through
  `V7__add_indexes_and_constraints.sql` sequence for later milestones.
- Configure `spring.jpa.hibernate.ddl-auto=validate`; Flyway alone owns schema
  creation and modification.
- Test against PostgreSQL through Testcontainers rather than H2 so migration and
  connectivity behavior match the selected database.
- There is no production data migration or backward-compatibility concern in an
  empty repository. Once a migration has been applied in a shared environment,
  it must not be edited in place; use a new forward migration.

### Configuration and Environment

`application.yml` will use environment overrides with safe local defaults:

| Setting | Environment variable | Local behavior |
|---|---|---|
| JDBC URL | `SPRING_DATASOURCE_URL` | Defaults to local Compose PostgreSQL |
| Database name | `POSTGRES_DB` | Used by Compose; documented local default |
| Database user | `POSTGRES_USER` | Shared by Compose/backend local config |
| Database password | `POSTGRES_PASSWORD` | Local-only example value, never production |
| Redis host | `SPRING_DATA_REDIS_HOST` | Defaults to `localhost` |
| Frontend API base | `VITE_API_BASE_URL` | Reserved local backend URL |
| OpenAI key | `OPENAI_API_KEY` | Blank and unused until Milestone 7 |
| Financial key | `FINANCIAL_API_KEY` | Blank and unused until provider milestone |
| News key | `NEWS_API_KEY` | Blank and unused until provider milestone |

Additional fixed local ports may use Compose substitution defaults, but new
environment-variable names must be documented here and in `.env.example` before
they are introduced. Test properties must be supplied dynamically by
Testcontainers and must not rely on a developer's `.env`.

### Dependencies

All versions must be pinned or managed by a pinned parent/BOM and lockfile.
Exact version selection is recorded in the Decision Log before implementation.

Backend production dependencies:

| Dependency | Purpose | Version source and impact |
|---|---|---|
| `spring-boot-starter-web` | Run the HTTP application and management endpoint | Managed by the pinned Spring Boot parent; embedded server only |
| `spring-boot-starter-actuator` | Provide `/actuator/health` and standard DB/Redis health contributors | Managed by Spring Boot; expose health only |
| `spring-boot-starter-data-jpa` | Establish the approved persistence stack and `ddl-auto=validate` | Managed by Spring Boot; no entities in Milestone 1 |
| `spring-boot-starter-data-redis` | Establish the approved Redis connection | Managed by Spring Boot; no application caching yet |
| `flyway-core` | Own and validate schema migrations | Managed by Spring Boot |
| Flyway PostgreSQL database support module, if required by the managed Flyway version | Support PostgreSQL migrations | Same managed Flyway release; confirm necessity during Phase 1 |
| PostgreSQL JDBC driver | Connect to PostgreSQL | Managed by Spring Boot and runtime-scoped |

Backend test dependencies:

- `spring-boot-starter-test` for JUnit 5, assertions, and Spring test support.
- Spring Boot Testcontainers integration, Testcontainers JUnit Jupiter, and the
  PostgreSQL module for real infrastructure tests.
- Testcontainers core `GenericContainer` for Redis unless a compatible,
  justified Redis-specific module already exists in the selected dependency set.
- No H2 database, paid API client, Spring AI, or provider test dependency.

Frontend runtime dependencies:

- `react` and `react-dom`, with compatible versions pinned in `package.json` and
  fully locked in `package-lock.json`.

Frontend development dependencies:

- Vite, TypeScript, and the official React Vite plugin for build/tooling.
- ESLint and the generated TypeScript/React lint plugins for static checks.
- Vitest, React Testing Library, `jest-dom`, user-event if the initial test needs
  it, and jsdom for the component smoke test.
- No Recharts, Axios, state library, component library, Playwright, or deployment
  package in this milestone.

Alternatives considered:

- A globally installed Maven was rejected in favor of Maven Wrapper
  reproducibility.
- H2 was rejected because Section 23 requires PostgreSQL Testcontainers and H2
  would not validate PostgreSQL/Flyway behavior.
- Yarn/pnpm were rejected because the design specifies npm and CI specifies
  `npm ci`.
- A Redis mock was rejected for the infrastructure integration test because the
  milestone must verify a real Redis connection.

## 9. Implementation Plan

### Phase 1: Initialize backend Spring Boot project

**Purpose**

- Create the smallest Java 21 modular-monolith application shell and reproducible
  Maven build needed by later infrastructure phases.

**Steps**

1. Confirm and record the approved Spring Boot and Maven Wrapper versions and
   the production/test dependency set before adding dependencies.
2. Generate or create `backend/pom.xml` with group `com.stocklens`, artifact
   `stocklens-backend`, Java 21, Maven Wrapper, and only the dependencies listed
   in Section 8.
3. Create `com.stocklens.StockLensApplication` as the sole production Java class.
4. Do not pre-create empty feature packages, domain classes, controllers,
   provider abstractions, or AI configuration.
5. Confirm the wrapper is executable on Unix and its distribution URL/checksum
   are version-controlled as supported by the chosen wrapper release.

**Validation**

- `cd backend && ./mvnw --version`
- `cd backend && ./mvnw -DskipTests compile`
- Expected: Maven uses Java 21 and compiles the one-class application without
  relying on a global Maven installation.

### Phase 2: Initialize frontend React, TypeScript, and Vite project

**Purpose**

- Create a reproducible, typed frontend shell with the build and test commands
  required by local development and CI.

**Steps**

1. Confirm and record the approved Node.js LTS, React, TypeScript, and Vite
   versions before generating the scaffold.
2. Generate the React/TypeScript Vite scaffold directly into the existing empty
   `frontend/` directory using npm and commit `package-lock.json`.
3. Replace template counter/logo content with a semantic, minimal StockLens AI
   placeholder that proves mounting but does not implement the comparison UI.
4. Configure scripts for `dev`, `lint`, `typecheck`, `test`, and `build`.
5. Configure Vitest with jsdom and React Testing Library setup; do not add
   dashboard-only packages.
6. Remove unused generator sample assets and code before validation.

**Validation**

- `cd frontend && npm ci`
- `cd frontend && npm run lint`
- `cd frontend && npm run typecheck`
- `cd frontend && npm test -- --run`
- `cd frontend && npm run build`
- Expected: dependency installation is lockfile-reproducible and the minimal
  frontend passes lint, type checking, tests, and production build.

### Phase 3: Add root repository configuration

**Purpose**

- Make local development consistent and prevent secrets or generated artifacts
  from entering version control.

**Steps**

1. Add a root `.gitignore` covering `.env`, IDE metadata, OS metadata, Maven
   targets, npm dependencies, Vite output, coverage, logs, and temporary files,
   while explicitly retaining `.env.example`, Maven Wrapper files,
   `package-lock.json`, and source/migration files.
2. Add `.editorconfig` with UTF-8, final newlines, trimmed trailing whitespace,
   and language-appropriate indentation without imposing a new formatter.
3. Add `.nvmrc` using the Node line approved in Phase 2.
4. Add `.env.example` with matching local infrastructure values, blank future
   provider/AI placeholders, explanatory comments, and no real secret.
5. Expand `README.md` with prerequisite and environment instructions, deferring
   final command verification to Phase 9.
6. Use `git status --short` to confirm unrelated user changes remain untouched.

**Validation**

- `git check-ignore -v .env .idea/workspace.xml backend/target/example frontend/node_modules/example frontend/dist/example`
- `git check-ignore -v .env.example backend/mvnw frontend/package-lock.json`
- `git diff --check`
- Expected: generated/local paths are ignored, required reproducibility files are
  not ignored, and repository text files have no whitespace errors.

### Phase 4: Add PostgreSQL and Redis Docker Compose services

**Purpose**

- Provide reproducible local infrastructure without adding application or
  deployment services.

**Steps**

1. Add `docker-compose.yml` with only `postgres` and `redis` services using the
   approved pinned image tags.
2. Configure PostgreSQL database/user/password from environment variables with
   local defaults that match `.env.example` and `application.yml`.
3. Add named volumes for PostgreSQL and Redis local data; do not bind-mount
   developer-specific absolute paths.
4. Add a PostgreSQL `pg_isready` health check and Redis `redis-cli ping` health
   check with bounded intervals, timeouts, and retries.
5. Publish conventional local ports through configurable/default mappings and
   avoid adding application containers, provider simulators, or cloud settings.

**Validation**

- `docker compose config`
- `docker compose up -d`
- `docker compose ps`
- `docker compose exec postgres pg_isready -U stocklens -d stocklens`
- `docker compose exec redis redis-cli ping`
- Expected: Compose configuration resolves without warnings, both services
  become healthy, PostgreSQL accepts a connection, and Redis returns `PONG`.

### Phase 5: Configure Spring Boot database, Redis, Flyway, and Actuator

**Purpose**

- Connect the backend shell to the approved local infrastructure and expose the
  minimum operational health signal.

**Steps**

1. Add `application.yml` datasource properties using environment overrides and
   local Compose-compatible defaults.
2. Enable Flyway at `classpath:db/migration` and set
   `spring.jpa.hibernate.ddl-auto=validate`.
3. Configure Redis host/port with local defaults; do not add cache managers,
   cache keys, TTLs, serialization policy, or application cache behavior.
4. Expose only the Actuator `health` endpoint and avoid sensitive details in the
   default unauthenticated response.
5. Start the backend against healthy Compose services and verify startup logs
   show a successful migration and no schema auto-creation by Hibernate.

**Validation**

- `cd backend && ./mvnw spring-boot:run`
- `curl --fail --silent http://localhost:8080/actuator/health`
- Expected: the process stays running and the health request returns HTTP 200
  with aggregate status `UP` when PostgreSQL and Redis are healthy.

### Phase 6: Add a minimal Flyway baseline migration

**Purpose**

- Prove Flyway ownership and repeatability without pulling Milestone 2's company
  schema into Milestone 1.

**Steps**

1. Add `backend/src/main/resources/db/migration/V0__baseline.sql` containing an
   explanatory comment and no domain DDL.
2. Do not enable Flyway `baseline-on-migrate`; this is a normal versioned
   migration for a new schema, not adoption of an existing unmanaged schema.
3. Verify a clean PostgreSQL database records version `0` in
   `flyway_schema_history` and contains no application/domain tables.
4. Verify a second application/test startup validates rather than reapplies the
   migration.

**Validation**

- `cd backend && ./mvnw test`
- `cd backend && ./mvnw verify`
- Expected: Flyway applies the versioned baseline exactly once on a clean
  Testcontainers database, validation succeeds on reuse where tested, and no
  company table exists.

### Phase 7: Add backend and frontend verification tests

**Purpose**

- Turn the infrastructure expectations into automated checks that work locally
  and in CI without paid or live external APIs.

**Steps**

1. Add shared test support that starts PostgreSQL and Redis Testcontainers and
   supplies connection properties dynamically; pin container images consistently
   with the approved infrastructure versions when practical.
2. Add a Spring context smoke test using the real infrastructure.
3. Add an integration test that verifies Flyway version `0`, successful SQL and
   Redis round trips, no company/domain tables, and an HTTP `UP` response from
   `/actuator/health` on a random port.
4. Add a frontend component test that renders the minimal application shell and
   asserts the accessible StockLens AI heading/content.
5. Ensure all containers are lifecycle-managed by Testcontainers and tests do
   not depend on a developer's running Compose stack or `.env` file.

**Validation**

- `cd backend && ./mvnw test`
- `cd backend && ./mvnw verify`
- `cd frontend && npm run lint`
- `cd frontend && npm run typecheck`
- `cd frontend && npm test -- --run`
- `cd frontend && npm run build`
- Expected: all backend and frontend checks pass with no live provider or OpenAI
  calls and no dependency on manually started Compose services.

### Phase 8: Add a basic GitHub Actions CI skeleton

**Purpose**

- Run the same reproducible backend and frontend verification on pull requests
  and pushes to `main` without adding deployment behavior.

**Steps**

1. Add `.github/workflows/ci.yml` triggered by pull requests and pushes to
   `main`, with least-privilege read-only repository contents permission.
2. Add a backend job that checks out the repository, sets up the approved Java
   21 distribution with Maven caching, and runs `./mvnw verify` from `backend/`.
3. Ensure the backend job can use the runner's Docker daemon for Testcontainers;
   do not call live external APIs or configure API secrets.
4. Add a frontend job that checks out the repository, uses the `.nvmrc` Node
   version with npm caching, runs `npm ci`, lint, typecheck, tests in non-watch
   mode, and the production build from `frontend/`.
5. Add no publish, deployment, release, provider, or OpenAI steps.

**Validation**

- Inspect `.github/workflows/ci.yml` against the exact local commands.
- `cd backend && ./mvnw verify`
- `cd frontend && npm ci`
- `cd frontend && npm run lint`
- `cd frontend && npm run typecheck`
- `cd frontend && npm test -- --run`
- `cd frontend && npm run build`
- Expected: local equivalents pass. The workflow will run both jobs when the
  changes are pushed; remote execution is outside this local implementation task.

### Phase 9: Verify documented local startup commands

**Purpose**

- Prove that a new developer can follow the repository documentation from a
  clean checkout without hidden setup or secrets.

**Steps**

1. Finalize `README.md` with prerequisites for Java 21, Docker with Compose,
   and the pinned Node/npm line; explain that Maven is supplied by the wrapper.
2. Document copying/reviewing `.env.example` without ever committing `.env`, or
   document that defaults allow an initial local start without copying when that
   is the implemented behavior.
3. In separate terminals, execute the documented Compose, backend, and frontend
   startup commands exactly as written.
4. Confirm Compose services are healthy, `/actuator/health` is `UP`, and the Vite
   page renders the minimal shell at the documented URL.
5. Run all documented automated checks, then stop the application processes and
   run `docker compose down` without deleting named volumes.
6. Review the complete diff, run the secret/generated-file checks, update this
   plan's progress and evidence, and confirm every acceptance criterion.

**Validation**

- `docker compose config`
- `docker compose up -d`
- `docker compose ps`
- `cd backend && ./mvnw spring-boot:run`
- `curl --fail --silent http://localhost:8080/actuator/health`
- `cd frontend && npm ci`
- `cd frontend && npm run dev`
- `cd backend && ./mvnw verify`
- `cd frontend && npm run lint`
- `cd frontend && npm run typecheck`
- `cd frontend && npm test -- --run`
- `cd frontend && npm run build`
- `docker compose down`
- `git diff --check`
- `git status --short`
- Expected: documentation is sufficient to reproduce startup and all checks;
  only scoped files plus pre-existing unrelated user changes appear in status.

## 10. Testing Strategy

### Unit Tests

- No domain unit tests are appropriate because this milestone intentionally
  contains no business logic.
- Keep the default backend context check small but run it with real disposable
  infrastructure so it cannot pass due to an embedded database or mocked Redis.
- Treat the frontend `App` rendering test as a component smoke test rather than
  as evidence that any comparison behavior exists.

### Integration Tests

- Use JUnit 5 and Testcontainers for PostgreSQL and Redis.
- Supply datasource and Redis connection properties dynamically from the
  containers, isolating tests from local `.env` and Compose state.
- Verify application-context startup and successful JPA/Flyway initialization.
- Query `flyway_schema_history` to verify the version `0` baseline succeeds.
- Query PostgreSQL metadata to verify no company or other domain tables exist.
- Execute a minimal SQL round trip and Redis write/read/delete round trip.
- Start the backend on a random port and verify `/actuator/health` returns HTTP
  200 and `UP` while both containers are available.
- Run via both `./mvnw test` and `./mvnw verify` so Maven phase/configuration
  mistakes are visible before CI.

### Frontend / Controller Tests

- Use Vitest, jsdom, and React Testing Library to verify the minimal application
  shell mounts and exposes its project heading through an accessible role.
- Run ESLint and TypeScript type checking separately from Vitest.
- Run the Vite production build to catch bundling/configuration errors.
- There are no application controllers to test. The Actuator health endpoint is
  covered as infrastructure in the backend integration test.

### CI Verification

- Backend job: Java 21 setup plus `cd backend && ./mvnw verify`, using
  Testcontainers and no live services or paid APIs.
- Frontend job: pinned Node setup, `npm ci`, lint, typecheck, non-watch tests, and
  production build.
- CI must not receive `OPENAI_API_KEY`, `FINANCIAL_API_KEY`, or `NEWS_API_KEY`.
- The workflow has no deployment credentials or write permissions.

### Manual Verification

- Validate Compose rendering before starting services.
- Confirm both infrastructure containers report healthy and respond to their
  native probes.
- Start the backend with the exact README command and inspect the health response.
- Start the frontend with the exact README command and open the printed local URL
  to confirm the shell renders without browser-console errors.
- Stop processes and use `docker compose down` without `--volumes` for normal
  cleanup.
- Review the final diff and status for secrets, generated artifacts, executable
  wrapper permissions, and unrelated changes.

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Unpinned or incompatible Java/Spring/Flyway/Testcontainers versions | Builds or migrations differ locally and in CI | Approve one supported Spring Boot parent, use its dependency management, pin the wrapper, and record versions before Phase 1 |
| Node/Vite/React drift | `npm ci` or CI behaves differently across machines | Pin Node in `.nvmrc`, commit `package-lock.json`, and use the same Node source in CI |
| Floating PostgreSQL or Redis image tags | Local and CI behavior can change without a code change | Pin supported image tags and use consistent tags for Compose/Testcontainers where practical |
| `V1` consumed by a generic baseline | Milestone 2 migration sequence would conflict with Section 22 | Use a no-domain `V0__baseline.sql`; reserve `V1__create_company_table.sql` |
| A no-op migration gives false confidence | Flyway could run while database connectivity or migration history is not actually asserted | Query `flyway_schema_history`, verify SQL connectivity, and assert absence of domain tables in Testcontainers |
| Hibernate silently creates schema | Database ownership drifts away from Flyway | Set `ddl-auto=validate` and assert only Flyway history exists |
| Redis health makes aggregate health `DOWN` when Redis is unavailable | Operational behavior may appear stricter than the eventual optional-cache architecture | Require `UP` only with both Milestone 1 services running; implement/test graceful cache fallback and refined health semantics in Milestone 8 |
| Local port collision | Documented startup fails on a developer machine | Use configurable Compose mappings/defaults and document overrides without changing container ports |
| Testcontainers cannot access Docker | Backend integration tests fail locally or on custom CI runners | Document Docker as a prerequisite, use GitHub hosted runners initially, and report environment failures rather than replacing real integration tests with mocks |
| Scaffold brings sample code or later-milestone dependencies | Milestone scope and diff become noisy | Remove sample assets/counters and review dependency manifests before completion |
| Example environment file leaks credentials | Security incident or accidental secret commit | Commit placeholders/local-only values, ignore `.env`, scan the diff, and provide no CI secrets |
| Existing dirty worktree is accidentally overwritten | User work in `docs/api-contract.md` or `.idea/` is lost or mixed into the milestone | Record baseline status, restrict edits to expected paths, and compare final status/diff without restoring or staging unrelated files |
| CI skeleton expands into deployment | Violates explicit Milestone 1 scope | Limit workflow permissions and jobs to verification only; add no publish/deploy steps |

## 12. Rollback / Recovery

- Before shared use, the scaffold and configuration can be reverted as one
  milestone change without data transformation because no domain schema exists.
- Stop application processes and run `docker compose down` to remove containers
  and networks while preserving named local volumes.
- Do not use `docker compose down --volumes` as routine recovery. If a disposable
  local database must be recreated, confirm the exact local-only target and data
  loss before removing its named volumes.
- Never edit an already-applied migration in a shared database. If the baseline
  needs correction after sharing, add a forward Flyway migration and record the
  deviation/decision in this plan.
- Maven/npm caches and generated build directories are disposable and ignored;
  they can be regenerated by wrapper/npm commands, but cleanup is not required
  for rollback.
- Preserve and exclude the pre-existing `docs/api-contract.md` deletion and
  `.idea/` directory from any rollback or milestone staging operation.

## 13. Progress

- [x] Plan reviewed; implementation explicitly authorized and status marked In Progress
- [x] Production dependency and version choices approved
- [x] Phase 1 complete — backend initialized
- [x] Phase 2 complete — frontend initialized
- [x] Phase 3 complete — root configuration added
- [x] Phase 4 complete — PostgreSQL and Redis Compose services added
- [x] Phase 5 complete — Spring infrastructure configured
- [x] Phase 6 complete — Flyway baseline added
- [x] Phase 7 complete — verification tests added
- [x] Phase 8 complete — CI skeleton added
- [x] Phase 9 complete — documented startup verified
- [x] Tests and validation complete
- [x] Complete diff and secret/generated-file review complete
- [x] Acceptance criteria confirmed

The checklist reflects the current repository state and is updated as each
phase is implemented and validated.

## 14. Decision Log

| Date | Decision | Reason | Alternatives |
|---|---|---|---|
| 2026-07-18 | Use a versioned no-domain `V0__baseline.sql` for Milestone 1 | Proves Flyway setup while preserving Section 22's `V1__create_company_table.sql` for Milestone 2 and respecting the prohibition on company implementation | Use `V1__baseline.sql` (conflicts with design numbering); create the company table now (out of scope); omit a migration (does not satisfy the task) |
| 2026-07-18 | Keep the frontend to a minimal semantic placeholder | React mounting/build/testing needs observable content, but dashboard work belongs to Milestone 6 | Retain Vite counter demo; begin dashboard layout |
| 2026-07-18 | Use PostgreSQL and Redis Testcontainers rather than mocks or local Compose for automated backend tests | Matches Sections 12 and 23 and makes CI independent of developer services | H2/mocked Redis; CI-managed service containers; manually running Compose |
| 2026-07-18 | Use two independent CI jobs and no deployment job | Matches Section 25 and makes backend/frontend failures clear while respecting the no-deployment constraint | Single sequential job; add deployment skeleton |
| 2026-07-18 | Defer Redis cache-aside behavior and failure fallback to Milestone 8 | Milestone 1 validates connectivity only; adding cache policies now would expand scope | Add cache manager/TTL/fallback implementation now |
| 2026-07-18 | Pin Spring Boot `4.1.0` and Maven Wrapper `3.3.4` for the backend scaffold | Both are current official stable releases; Spring Boot 4.1 supports Java 21 and Maven 3.6.3+ | Use the maintained Spring Boot 3.5 line; use an older wrapper release |
| 2026-07-18 | Pin Node.js `24.14.0`, React `19.2.7`, Vite `8.1.x` (locked to `8.1.5`), and TypeScript `6.0.2` | Node 24 is the active LTS line available in the workspace runtime; the selected React/Vite releases are current stable versions and the npm lockfile fixes exact transitive versions | Use the non-LTS system Node 23; use older maintained React/Vite lines |
| 2026-07-18 | Pin `postgres:18.4-alpine` and `redis:8.8.0-alpine` | These are current supported official-image patch releases and provide multi-architecture images for local development | Use floating major/latest tags; use older maintained majors |

## 15. Deviations from Design

- None. `V0__baseline.sql` precedes rather than replaces the initial
  `V1__create_company_table.sql` through `V7__add_indexes_and_constraints.sql`
  sequence in Section 22.

## 16. Validation Evidence

The commands below were run on 2026-07-18. Generated build output and dependency
directories remain local and ignored.

| Command | Result | Notes |
|---|---|---|
| `cd backend && ./mvnw --version` | PASS | Maven 3.9.16 on temporary Temurin Java 21.0.11; wrapper distribution checksum verification enabled |
| `cd backend && ./mvnw -DskipTests compile` | PASS | Maven 3.9.16 used Temurin Java 21.0.11 and compiled the sole production class with `release 21` |
| `cd backend && ./mvnw test` | PASS | Four JUnit tests passed against PostgreSQL 18.4 and Redis 8.8 Testcontainers: context, Flyway/schema, database, Redis, and Actuator checks |
| `cd backend && ./mvnw verify` | PASS | Four JUnit tests passed and the executable Spring Boot jar was packaged successfully; the final run passed with Compose stopped, proving Testcontainers owns test infrastructure |
| `cd frontend && npm ci` | PASS | Installed 237 locked packages with Node 24.14.0 |
| `cd frontend && npm run lint` | PASS | ESLint completed with no findings |
| `cd frontend && npm run typecheck` | PASS | TypeScript project build completed with no errors |
| `cd frontend && npm test -- --run` | PASS | Vitest: 1 file and 1 test passed |
| `cd frontend && npm run build` | PASS | TypeScript and Vite 8.1.5 production build succeeded |
| `docker compose config` | PASS | Resolved exactly PostgreSQL and Redis with pinned images, health checks, named volumes, and expected ports |
| `docker compose up -d` | PASS | Pulled images and created both services plus named volumes/network |
| `docker compose ps` | PASS | PostgreSQL and Redis both reported `healthy` |
| `docker compose exec postgres pg_isready -U stocklens -d stocklens` | PASS | PostgreSQL reported accepting connections |
| `docker compose exec redis redis-cli ping` | PASS | Redis returned `PONG` |
| `curl --fail --silent http://localhost:8080/actuator/health` | PASS | Running backend returned HTTP 200 with `{"groups":["liveness","readiness"],"status":"UP"}` |
| `cd frontend && npm run dev` | PASS | Vite 8.1.5 started at `http://localhost:5173/` under Node 24.14.0 |
| Browser render check at `http://localhost:5173/` | PASS | Rendered page exposed the `StockLens AI` level-one heading and milestone placeholder with no browser console errors |
| `docker compose exec postgres psql -U stocklens -d stocklens -Atc "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"` | PASS | The clean database recorded exactly version `0`, description `baseline`, with `success=true` |
| `docker compose exec postgres psql -U stocklens -d stocklens -Atc "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;"` | PASS | The only public table was `flyway_schema_history`; no company or domain table was created |
| Second `cd backend && ./mvnw spring-boot:run` startup | PASS | Flyway validated version `0` and reported the schema up to date without reapplying the migration |
| `docker compose down` | PASS | Stopped and removed milestone containers/network without deleting the named volumes |
| `git diff --check` | PASS | No whitespace errors in tracked changes; intended untracked files were also inspected |
| `git check-ignore -v .env .idea/workspace.xml backend/target/example frontend/node_modules/example frontend/dist/example` | PASS | Local secrets, IDE metadata, and generated build/dependency paths are ignored |
| `git check-ignore -q backend/mvnw` and `git check-ignore -q frontend/package-lock.json` | PASS | Both commands returned the expected nonzero result, confirming reproducibility files are not ignored |
| `ruby -e 'require "yaml"; YAML.parse_file(ARGV.fetch(0))' .github/workflows/ci.yml` | PASS | CI workflow YAML parsed successfully; inspection confirmed independent backend/frontend jobs, read-only contents permission, and no deployment or secret-dependent steps |
| Secret/generated-file and later-scope scans | PASS | No credential-shaped values, `.env`, unignored build output, or later-milestone dependency/feature code was found |
| `git status --short --untracked-files=all` | PASS | Only Milestone 1 files plus the preserved pre-existing staged deletion of empty `docs/api-contract.md` appeared; `.idea/` remains untouched and is now ignored |

## 17. Completion Summary

### Implemented

- Java 21/Spring Boot backend scaffold with PostgreSQL, Redis, Flyway, JPA
  validation, and health-only Actuator configuration.
- React/TypeScript/Vite frontend scaffold with a minimal accessible shell.
- Pinned PostgreSQL/Redis Compose services, root environment/ignore/editor
  configuration, local-development documentation, and two-job CI workflow.
- Comment-only Flyway version `0` baseline and real-infrastructure backend plus
  component-level frontend verification tests.

### Files Changed

- Root: `.editorconfig`, `.env.example`, `.gitignore`, `.nvmrc`, `README.md`,
  `docker-compose.yml`, and `.github/workflows/ci.yml`.
- Backend: Maven wrapper/configuration, `pom.xml`, the application entry point,
  `application.yml`, `V0__baseline.sql`, and three test/support classes.
- Frontend: npm manifest/lockfile, Vite/TypeScript/ESLint configuration, HTML,
  minimal React source/styles, and test setup/component test.
- Plan: `.agent/plans/01-repository-and-infrastructure.md`.

### Tests Added or Updated

- Four JUnit tests cover context startup, Flyway/schema state, PostgreSQL and
  Redis round trips, and Actuator health through PostgreSQL/Redis Testcontainers.
- One Vitest/React Testing Library test covers the accessible frontend shell.

### Known Limitations

- CI cannot be proven on GitHub until the workflow is pushed and runs remotely.
- The host defaults to Java 20 and Node 23; local validation used temporary
  Temurin 21.0.11 and the workspace Node 24.14.0 runtime required by `README.md`.
- Redis graceful-degradation behavior is deliberately deferred to Milestone 8.

### Remaining Work

- No Milestone 1 implementation item remains. The CI workflow still needs its
  first remote run after the changes are pushed.
- Company/market data begins in Milestone 2; the comparison UI, providers, AI,
  caching behavior, documentation polish, and deployment remain in their later
  design milestones.
