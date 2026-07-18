# StockLens AI Execution Plan Standard

This document defines how implementation plans are created, reviewed, updated,
and completed for StockLens AI.

An execution plan is a living implementation document for one milestone or one
bounded cross-cutting feature. It translates `docs/design.md` into a sequence of
reviewable code changes without replacing the design document.

---

## 1. Sources of Truth

Every execution plan must follow these sources in order:

1. The current user task
2. `AGENTS.md`
3. `docs/design.md`
4. Existing code and tests
5. The execution plan itself

An execution plan may clarify implementation details, but it must not silently
change product scope, architecture, API contracts, data models, or non-goals.

If these sources conflict, stop implementation and document the conflict under
`Open Questions / Blockers`.

---

## 2. When an Execution Plan Is Required

Create or update a plan when a task:

- affects more than one application layer;
- is expected to modify more than five files;
- is expected to take more than one hour;
- introduces or changes a database migration;
- changes a public API contract;
- integrates an external provider;
- changes caching or invalidation behavior;
- changes AI prompts, output schemas, grounding, or validation;
- introduces a new production dependency;
- spans both frontend and backend;
- implements a milestone from `docs/design.md`.

A plan is optional for a small, isolated fix when the behavior, files, and test
strategy are already obvious.

---

## 3. Plan Location and Naming

Store plans under:

```text
.agent/plans/
```

Use names in this format:

```text
NN-short-kebab-case-title.md
```

Examples:

```text
00-design-alignment.md
01-repository-and-infrastructure.md
02-company-and-market-data.md
03-financial-metrics-and-history.md
04-news-integration.md
05-comparison-api.md
06-frontend-dashboard.md
07-ai-comparison-brief.md
08-caching-and-refresh.md
09-documentation-and-polish.md
10-deployment-and-demo.md
```

One plan should cover one coherent milestone or feature. Do not create one plan
for the entire application.

---

## 4. Plan Status

Use one of these values:

- `Draft` — still being investigated or reviewed;
- `Ready` — scope and acceptance criteria are clear;
- `In Progress` — implementation has started;
- `Blocked` — work cannot continue without a decision or dependency;
- `Completed` — acceptance criteria are satisfied and validation has passed;
- `Superseded` — replaced by a newer plan.

Do not begin implementation while the plan is `Draft` unless the user explicitly
requests exploratory work.

---

## 5. Required Plan Qualities

Every plan must be:

### Self-contained

A developer should understand the intended change without relying on hidden chat
context. Reference exact sections of `docs/design.md`, but summarize the relevant
requirements inside the plan.

### Specific

Name concrete files, classes, endpoints, tables, commands, and observable
behaviors where they can be known from the current repository.

Avoid vague steps such as:

```text
Implement the backend.
Add tests.
Fix errors.
```

Prefer:

```text
Create `Company` and `MarketSnapshot` JPA entities, add Flyway migrations,
implement latest-snapshot repository queries, and verify them with PostgreSQL
Testcontainers tests.
```

### Bounded

State both goals and non-goals. Do not pull later milestones into the current
plan merely because they are related.

### Testable

Acceptance criteria must describe observable behavior. Every implementation
phase must identify how it will be validated.

### Maintainable

The plan is a living document. Update progress, decisions, blockers, deviations,
and validation evidence as work proceeds.

---

## 6. Required Workflow

### Before Planning

1. Read `AGENTS.md`.
2. Read the relevant sections of `docs/design.md`.
3. Inspect the current repository, implementation, tests, and configuration.
4. Identify existing patterns that should be followed.
5. Record unresolved decisions rather than guessing.

### Before Coding

1. Create or update the execution plan.
2. Define goal, scope, non-goals, and acceptance criteria.
3. List expected files to create or modify.
4. Describe schema, API, dependency, configuration, and environment changes.
5. Define tests and validation commands.
6. Mark the plan `Ready` only when the approach is coherent.

### During Coding

1. Mark the plan `In Progress`.
2. Implement the smallest complete phase.
3. Add or update tests with production code.
4. Update the progress checklist after each completed phase.
5. Record material decisions and deviations immediately.
6. Do not introduce unrelated refactors.
7. Do not silently expand scope.

### After Coding

1. Run all validation commands listed in the plan.
2. Review the complete diff against `AGENTS.md` and `docs/design.md`.
3. Check for secrets, generated files, dead code, and unrelated changes.
4. Record actual command results under `Validation Evidence`.
5. Confirm every acceptance criterion.
6. Record known limitations and remaining work.
7. Mark the plan `Completed` only when required checks pass.

---

## 7. Implementation Phase Rules

Each phase should produce a reviewable result and normally end with passing
relevant tests.

A good phase:

- has one clear purpose;
- modifies a limited, understandable set of files;
- can be validated independently;
- does not depend on unfinished later phases for correctness;
- leaves the repository buildable whenever practical.

For a large milestone, prefer several phases such as:

1. Domain model and migration
2. Repository and persistence tests
3. Service behavior and unit tests
4. Controller and API tests
5. Frontend integration
6. Documentation and final verification

Do not implement multiple future milestones in one phase.

---

## 8. Testing and Validation Requirements

Plans must list exact commands expected to run.

Typical backend commands:

```bash
cd backend && ./mvnw test
cd backend && ./mvnw verify
```

Typical frontend commands:

```bash
cd frontend && npm run lint
cd frontend && npm test
cd frontend && npm run build
```

Typical infrastructure commands:

```bash
docker compose config
docker compose up -d
docker compose ps
docker compose down
```

Use only commands that exist in the repository. If a script is not yet defined,
state that it must be added as part of the plan.

Automated tests must not call paid or live third-party services. Use mock HTTP
responses, fake AI clients, and Testcontainers as required by `AGENTS.md` and
`docs/design.md`.

Do not write `tests pass` without recording the command that was run and its
result.

---

## 9. Database and API Change Rules

When a plan changes persistence, include:

- new or modified tables and columns;
- constraints and indexes;
- Flyway migration filenames;
- entity and repository changes;
- compatibility or data-migration considerations;
- Testcontainers coverage.

When a plan changes an API, include:

- HTTP method and path;
- request parameters or body;
- response shape;
- validation behavior;
- error codes and partial-data behavior;
- frontend impact;
- OpenAPI and controller-test updates.

Do not change an approved public contract without documenting the reason and
receiving approval.

---

## 10. Dependency and Configuration Rules

For every new production dependency, record:

- the problem it solves;
- why existing dependencies are insufficient;
- the selected version or version-management source;
- runtime and testing impact;
- alternatives considered.

For configuration changes, record:

- new environment variables;
- safe defaults;
- `.env.example` changes;
- local, test, and production differences;
- secret-handling requirements.

Never place real credentials in an execution plan, test fixture, source file, or
committed configuration.

---

## 11. Decision and Deviation Rules

Use `Decision Log` for implementation choices that are not already fixed by the
design document.

Examples:

- selecting Recharts rather than Apache ECharts;
- choosing a provider adapter mapping strategy;
- deciding whether a dashboard section may return partial data;
- selecting a serialization format for Redis.

Use `Deviations from Design` when implementation cannot follow the approved
design exactly.

A deviation entry must include:

- the design requirement;
- the discovered constraint;
- the proposed change;
- its trade-offs;
- approval status.

Do not edit `docs/design.md` as part of normal implementation unless explicitly
requested.

---

## 12. Progress Tracking

Use checkboxes for implementation progress:

```md
- [x] Completed item
- [ ] Pending item
```

The checklist must reflect actual repository state, not intended work.

When blocked, include:

- what is blocked;
- why it is blocked;
- what decision or input is needed;
- what work can continue independently.

---

## 13. Completion Standard

A plan may be marked `Completed` only when:

- all required acceptance criteria are satisfied;
- required migrations and API contracts match the design;
- relevant automated tests pass;
- build and lint checks pass where applicable;
- the complete diff has been reviewed;
- no secrets are present;
- known limitations are documented;
- plan progress and validation evidence are current;
- remaining work belongs to a later plan rather than unfinished current scope.

A partially implemented feature must remain `In Progress` or `Blocked`.

---

## 14. Execution Plan Template

Copy this template into `.agent/plans/NN-plan-name.md`.

```md
# <Plan Title>

**Status:** Draft  
**Milestone:** <Design milestone number and name>  
**Created:** YYYY-MM-DD  
**Last Updated:** YYYY-MM-DD  
**Design References:** `docs/design.md` Sections <X, Y, Z>

## 1. Goal

Describe the user-visible or engineering outcome in concrete terms.

## 2. Background and Current State

Summarize the relevant design requirements and what currently exists in the
repository. Include exact paths and existing behavior discovered during
inspection.

## 3. Scope

### In Scope

- ...

### Out of Scope

- ...

## 4. Assumptions

- ...

## 5. Open Questions / Blockers

- None, or list unresolved items that require a decision.

Do not guess when an unresolved item changes architecture, public APIs,
persistence, security, cost, or scope.

## 6. Acceptance Criteria

- [ ] ...
- [ ] ...
- [ ] Relevant automated tests pass.
- [ ] No secrets or unrelated changes are introduced.

## 7. Expected Files

### Create

- `path/to/new-file`

### Modify

- `path/to/existing-file`

### Delete

- None.

This list is an estimate and must be updated when implementation changes it.

## 8. API / Schema / Configuration Changes

### API

- None, or document method, path, request, response, validation, and errors.

### Database

- None, or document tables, columns, constraints, indexes, and Flyway files.

### Configuration and Environment

- None, or document properties and environment variables.

### Dependencies

- None, or justify each new production dependency.

## 9. Implementation Plan

### Phase 1: <Name>

**Purpose**

- ...

**Steps**

1. ...
2. ...

**Validation**

- `exact command`
- Expected observable result

### Phase 2: <Name>

**Purpose**

- ...

**Steps**

1. ...
2. ...

**Validation**

- `exact command`
- Expected observable result

## 10. Testing Strategy

### Unit Tests

- ...

### Integration Tests

- ...

### Frontend / Controller Tests

- ...

### Manual Verification

- ...

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| ... | ... | ... |

## 12. Rollback / Recovery

Describe how to revert or recover from migration, configuration, provider, or
API changes. Write `Not applicable` only when the change is genuinely trivial.

## 13. Progress

- [ ] Plan reviewed and marked Ready
- [ ] Phase 1 complete
- [ ] Phase 2 complete
- [ ] Tests and validation complete
- [ ] Diff reviewed
- [ ] Acceptance criteria confirmed

## 14. Decision Log

| Date | Decision | Reason | Alternatives |
|---|---|---|---|
| YYYY-MM-DD | ... | ... | ... |

## 15. Deviations from Design

- None.

If present, document the requirement, constraint, proposed deviation,
trade-offs, and approval status.

## 16. Validation Evidence

Record commands after they are actually run.

| Command | Result | Notes |
|---|---|---|
| `...` | PASS / FAIL / NOT RUN | ... |

## 17. Completion Summary

Complete this section at the end of implementation.

### Implemented

- ...

### Files Changed

- ...

### Tests Added or Updated

- ...

### Known Limitations

- ...

### Remaining Work

- ...
```

---

## 15. StockLens AI Initial Plan Sequence

Unless repository inspection reveals a dependency conflict, use this order:

1. `00-design-alignment.md` — resolve document, path, provider, and library
   decisions required before implementation.
2. `01-repository-and-infrastructure.md` — monorepo, Spring Boot, React/Vite,
   PostgreSQL, Redis, Flyway, health check, and CI skeleton.
3. `02-company-and-market-data.md` — company, quotes, validation, provider
   adapter, persistence, and supporting endpoints.
4. `03-financial-metrics-and-history.md` — financial snapshots, historical
   prices, returns, metric definitions, and tests.
5. `04-news-integration.md` — provider adapter, persistence, deduplication, and
   company relationships.
6. `05-comparison-api.md` — dashboard orchestration, grouped metrics, aligned
   series, provenance, warnings, and tests.
7. `06-frontend-dashboard.md` — final approved dashboard using real backend
   contracts, responsive states, and component tests.
8. `07-ai-comparison-brief.md` — Spring AI, prompt versioning, structured output,
   source grounding, repair, persistence, and UI integration.
9. `08-caching-and-refresh.md` — Redis cache-aside behavior, TTLs, input hashing,
   refresh, and invalidation.
10. `09-documentation-and-polish.md` — OpenAPI, README, accessibility, final
    screenshot, architecture diagram, and full CI.
11. `10-deployment-and-demo.md` — hosted services, configuration, smoke tests,
    public demo, and resume material.

Later plans may be refined after earlier implementation reveals concrete
constraints, but milestones must not be silently reordered or merged in a way
that expands scope.
