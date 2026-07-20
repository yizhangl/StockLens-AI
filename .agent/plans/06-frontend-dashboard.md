# Milestone 6: Frontend Comparison Dashboard

**Status:** Completed  
**Milestone:** 6 — Frontend Dashboard  
**Created:** 2026-07-20  
**Last Updated:** 2026-07-20  
**Design References:** `docs/design.md` Sections 4, 8–10, 12–14, 16–18,
23–25, 28 (Milestone 6), 29, and 30

## 1. Goal

Replace the initial React scaffold with the approved responsive StockLens AI
comparison dashboard. The page will consume only the completed Milestone 5
`GET /api/v1/comparisons` response, preserve the backend's provider-independent
contract and comparison decisions, and render useful loading, error, partial,
empty, and successful states on desktop, tablet, and mobile.

## 2. Background and Current State

Milestones 1–5 are complete. The repository is clean before Milestone 6 begins.
The backend endpoint accepts `left`, `right`, `period`, and `mode`, defaults to
`1Y` and `RETURN`, and returns:

- requested-order company summaries;
- aligned price or return series;
- four registry-ordered metric groups and authoritative outcomes;
- at most three recent articles per side;
- an explicit nullable `aiBrief`;
- typed partial-data warnings;
- provider and freshness provenance.

The frontend is a minimal React 19 / TypeScript 6 / Vite 8 scaffold with strict
TypeScript, ESLint, Vitest, React Testing Library, user-event, and jsdom already
installed. It contains only an application-shell placeholder and one smoke
test. There is no router, API client, state library, chart library, dashboard,
or feature folder.

The approved visual reference is
`docs/images/stocklens-final-ui.png` (1122 x 1402). Its information hierarchy,
light research-dashboard appearance, blue/orange comparison accents, bordered
cards, compact financial tables, and responsive stacking guide the frontend.
The mock's fabricated AI content is not implemented because Milestone 7 has not
begun and the live response currently has `aiBrief: null`.

## 3. Scope

### In Scope

- A typed native-Fetch comparison API client.
- Configurable API base URL and Vite development proxy.
- URL-backed ticker, period, and mode state without a router.
- Search normalization, validation, keyboard submission, and focus behavior.
- Request cancellation and stale-response protection.
- Initial skeleton, control-level loading, empty/whole-page error, retry, and
  partial-success warning states.
- Company summaries, aligned Recharts performance visualization, metric cards,
  news panels, provenance, exact disclaimer, and a neutral null-AI placeholder.
- Consistent null-safe number, percentage, currency, date, and provider
  formatting.
- Responsive and accessible desktop/tablet/mobile presentation.
- Unit and component tests with mocked Fetch; no live backend/provider calls.
- Production build and local browser smoke verification.

### Out of Scope

- Any backend endpoint, DTO, provider, database, migration, test, or behavior
  change.
- AI generation, Spring AI/OpenAI, prompts, grounding, persistence, regenerate
  actions, fake summaries, winners, risks, or claims.
- Redis business caching, refresh behavior, manual refresh, or cache status
  changes.
- Authentication, functional watchlists, saved comparisons, routing libraries,
  global state libraries, deployment, analytics, predictions, recommendations,
  sentiment generation, scraping, and all Milestone 7+ work.

## 4. Assumptions

- The page defaults to `AAPL`, `MSFT`, `1Y`, and `RETURN`, writes those values to
  the query string after a successful request, and automatically loads them on
  first render. Valid URL parameters override the defaults.
- Missing or invalid URL parameters fall back safely to defaults rather than
  blocking initial rendering; user-submitted invalid values remain visible and
  receive inline errors.
- Recharts is the only new production dependency. It is selected explicitly by
  `docs/design.md` and the user task, so no additional dependency decision is
  needed.
- Native Fetch, local React state, `URLSearchParams`, and `history.replaceState`
  are sufficient; no Axios, router, query cache, or global state dependency is
  justified.
- The relative API base is the default. `VITE_API_BASE_URL` may provide an
  absolute backend origin in other environments. Vite proxies `/api` and
  `/actuator` to `http://localhost:8080` for local development.
- Backend percentage conventions are contractual: daily change and historical
  returns already use percentage points; `DECIMAL_FRACTION_PERCENT` metric
  values are decimal fractions and are multiplied by 100 only when formatted.
- Backend metric `outcome` is authoritative. The frontend never recomputes a
  winner from numeric values.
- Provider URLs are untrusted. Only parseable `http:` and `https:` links are
  clickable, opened with safe external-link attributes; no provider HTML is
  rendered.
- `aiBrief: null` is represented by a short, neutral Milestone 7 availability
  note, not fabricated analysis.

## 5. Open Questions / Blockers

- None. The current backend contract, approved image, and frontend scaffold are
  compatible with the design and requested milestone.

## 6. Acceptance Criteria

- [x] The page uses only `GET /api/v1/comparisons` for dashboard data.
- [x] API request/response/error models match every current backend field and
  preserve nullable values.
- [x] API requests support a configurable base URL, AbortSignal, non-JSON and
  network errors, structured backend errors, and cancellation.
- [x] Vite proxies `/api` and `/actuator` for local development without a
  permissive backend CORS change or hardcoded component origin.
- [x] Default `AAPL/MSFT/1Y/RETURN` state auto-loads and valid URL state is read.
- [x] Successful comparisons update the URL; changing period or mode retains
  tickers; active controls do not create duplicate requests.
- [x] Search trims and uppercases values, validates the approved ticker regex,
  rejects blanks and duplicates inline, submits with Enter, and remains
  editable after errors.
- [x] New requests abort old requests and stale results cannot replace current
  state.
- [x] The initial request shows a dashboard skeleton; subsequent control
  requests preserve current content and show an explicit busy state.
- [x] Company cards render identity, safe logo/site behavior, raw market data,
  concise descriptions, and missing values as an em dash.
- [x] Daily change percentage and historical return percentage are not
  multiplied by 100.
- [x] The responsive Recharts chart uses only aligned backend series, includes
  accessible text summaries, supports all five periods and both modes, and
  formats axes/tooltips appropriately.
- [x] Metric cards retain backend group/row order and style only the backend's
  authoritative LEFT/RIGHT/EQUAL/NEUTRAL/INSUFFICIENT_DATA outcome.
- [x] Metric decimal-fraction percentages are multiplied by 100 only at display
  time; ratios and currency values use their proper conventions.
- [x] News renders up to three articles per side, dates and sources safely,
  without raw HTML, and handles empty data.
- [x] Typed warnings are visible near the affected section and preserve the
  successful remainder of the dashboard.
- [x] Whole-page failures are safe, actionable, and retryable without losing
  editable form values.
- [x] Provenance humanizes providers, shows freshness and cached state, and
  includes the exact approved informational disclaimer.
- [x] `aiBrief: null` produces no fake AI content or functional regenerate UI.
- [x] Layout is usable at approximately 1440, 1024, 768, and 390 px with no
  horizontal page overflow.
- [x] Semantic regions, labels, visible focus, status announcements, non-color
  outcome text, accessible controls, and chart summary text are present.
- [x] Frontend tests cover the client, validation, formatting, major sections,
  controls, loading, errors, retry, warnings, URL state, and stale requests.
- [x] Frontend automated tests call no live backend, database, FMP, Yahoo, or
  OpenAI service.
- [x] Lint, strict typecheck, tests, production build, and backend regression
  verification pass.
- [x] Manual browser smoke checks pass for default and GOOGL/AMZN comparisons,
  controls, invalid requests, retry/error behavior where practical, and mobile.
- [x] Complete diff, secrets, generated-file, dependency, and scope reviews
  pass; Milestone 7 remains untouched.

## 7. Expected Files

### Create

- `.agent/plans/06-frontend-dashboard.md`
- `frontend/src/api/client.ts`
- `frontend/src/api/comparisonApi.ts`
- `frontend/src/components/common/ErrorState.tsx`
- `frontend/src/components/common/LoadingSkeleton.tsx`
- `frontend/src/components/common/SectionWarning.tsx`
- `frontend/src/components/layout/AppHeader.tsx`
- `frontend/src/features/comparison/components/AiBriefPlaceholder.tsx`
- `frontend/src/features/comparison/components/CompanySummaryCard.tsx`
- `frontend/src/features/comparison/components/ComparisonWarnings.tsx`
- `frontend/src/features/comparison/components/DataProvenanceFooter.tsx`
- `frontend/src/features/comparison/components/MetricCategoryCard.tsx`
- `frontend/src/features/comparison/components/NewsArticleCard.tsx`
- `frontend/src/features/comparison/components/PricePerformanceChart.tsx`
- `frontend/src/features/comparison/components/RecentDevelopments.tsx`
- `frontend/src/features/comparison/components/StockSearchForm.tsx`
- `frontend/src/features/comparison/hooks/useComparison.ts`
- `frontend/src/features/comparison/types/comparison.ts`
- `frontend/src/features/comparison/utils/chart.ts`
- `frontend/src/features/comparison/utils/formatters.ts`
- `frontend/src/features/comparison/utils/tickerValidation.ts`
- `frontend/src/pages/ComparePage.tsx`
- focused `.test.ts` / `.test.tsx` files beside the code they cover

### Modify

- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/vite.config.ts`
- `frontend/src/App.tsx`
- `frontend/src/App.test.tsx`
- `frontend/src/index.css`
- this plan as progress and evidence are recorded

### Delete

- None. Existing placeholder behavior is replaced in place.

This estimate may be consolidated where a proposed component has no meaningful
boundary. The final list will reflect the actual implementation.

## 8. API / Schema / Configuration Changes

### API

No backend API change. The frontend consumes:

```http
GET /api/v1/comparisons?left={ticker}&right={ticker}&period={period}&mode={mode}
```

Supported periods are `1M`, `6M`, `1Y`, `5Y`, and `MAX`; modes are `PRICE` and
`RETURN`. Structured API errors expose `code`, `message`, `timestamp`, `path`,
`requestId`, and `details`.

### Database

None. No entity, table, migration, cache record, or persisted frontend state is
added.

### Configuration and Environment

- Optional `VITE_API_BASE_URL` controls the frontend API origin. Empty means
  same-origin relative requests.
- Vite development proxy targets local backend port 8080 for `/api` and
  `/actuator`.
- No real `.env`, key, secret, or production CORS rule is added.

The repository-level `.env.example` already documents `VITE_API_BASE_URL`; a
second frontend environment example is unnecessary unless inspection during
implementation proves otherwise.

### Dependencies

- Add `recharts` 3.9.2 as a production dependency. It solves the required responsive
  aligned line chart and is the design-selected library. Native SVG or another
  chart package would either recreate accessible interaction primitives or
  conflict with the approved stack.
- Existing Vitest, React Testing Library, user-event, and jsdom dependencies are
  reused; no new test dependency is expected.

## 9. Implementation Plan

### Phase 1: Typed API and Formatting Foundation

**Purpose**

- Establish one trustworthy boundary for backend data and frontend display
  semantics before rendering the page.

**Steps**

1. Add strict provider-independent TypeScript models matching all comparison
   and error fields, enum strings, lists, and nullable values.
2. Implement a native-Fetch client with base URL normalization, query encoding,
   JSON/content-type handling, structured errors, abort propagation, and safe
   fallback errors.
3. Add ticker/query validation, formatting, safe-link, and chart-data helpers.
4. Add focused client, validation, formatter, and chart-helper tests.

**Validation**

- `cd frontend && npm run typecheck`
- `cd frontend && npm test -- --run`

### Phase 2: Search, URL State, and Request Lifecycle

**Purpose**

- Make comparison state deterministic, shareable, editable, and resistant to
  request races.

**Steps**

1. Build the accessible two-ticker search form with normalization, validation,
   duplicate detection, Enter submission, inline errors, disabled/busy state,
   and invalid-field focus.
2. Implement `useComparison` with initial URL parsing/defaults, automatic load,
   AbortController cancellation, request identity guards, retained successful
   data during control changes, retry, and successful URL replacement.
3. Add hook/page tests for defaults, URL overrides, search errors, loading,
   retry, structured failures, cancellation, stale requests, and duplicate
   active-control suppression.

**Validation**

- `cd frontend && npm run typecheck`
- `cd frontend && npm test -- --run`

### Phase 3: Dashboard Sections and Approved Visual Hierarchy

**Purpose**

- Render every Milestone 6 data section without leaking provider shapes or
  fabricating unavailable content.

**Steps**

1. Add the application header, initial skeleton, whole-page error, warning, and
   neutral null-AI components.
2. Add company cards with safe images/links, concise descriptions, market
   change state, summary values, and null handling.
3. Add the responsive Recharts performance section with period/mode controls,
   backend-aligned points, mode-aware axes/tooltips, and accessible summaries.
4. Add registry-ordered metric category cards with unit formatting and
   backend-outcome styling/text.
5. Add recent-development panels and safe article links.
6. Add provenance/freshness/footer metadata and the exact disclaimer.
7. Add focused component tests for all major data, null, empty, warning, and
   interaction paths.

**Validation**

- `cd frontend && npm run lint`
- `cd frontend && npm run typecheck`
- `cd frontend && npm test -- --run`

### Phase 4: Responsive Styling and Application Integration

**Purpose**

- Assemble a polished single-page dashboard that follows the approved image
  while remaining usable and accessible across target widths.

**Steps**

1. Replace the scaffold in `App` with `ComparePage` and assemble all sections.
2. Implement design tokens and standard CSS for the desktop-first hierarchy,
   blue/orange comparison identity, cards, tables, controls, focus, skeletons,
   responsive grids, wrapping, chart sizing, and mobile stacking.
3. Verify semantic landmarks, headings, live/status announcements, label
   associations, keyboard behavior, non-color status labels, and overflow.
4. Update the app-level test to cover the integrated initial/default flow.

**Validation**

- `cd frontend && npm run lint`
- `cd frontend && npm run typecheck`
- `cd frontend && npm test -- --run`
- `cd frontend && npm run build`

### Phase 5: Full Validation, Browser Smoke, and Review

**Purpose**

- Prove the milestone against the repository lifecycle and a real local browser.

**Steps**

1. Run the complete frontend install/lint/typecheck/test/build sequence.
2. Run the complete backend regression lifecycle without changing backend code.
3. Start the existing local infrastructure/backend/frontend as needed and use
   the in-app browser to verify default AAPL/MSFT, GOOGL/AMZN, all controls,
   invalid input, backend error rendering, and approximately 390 px layout.
4. Inspect the complete Git status/diff/stat/check for unrelated changes,
   secrets, generated files, broad types, duplicated contracts/formatters,
   unsafe links/HTML, fake AI, frontend winner calculations, hardcoded origins,
   console logs, and Milestone 7 scope.
5. Fix confirmed Milestone 6 issues, record evidence, confirm every acceptance
   criterion, and mark the plan completed only if all required checks pass.

**Validation**

- `cd frontend && npm install`
- `cd frontend && npm run lint`
- `cd frontend && npm run typecheck`
- `cd frontend && npm test -- --run`
- `cd frontend && npm run build`
- `cd backend && ./mvnw clean verify`
- `git status --short`
- `git diff --stat`
- `git diff --check`
- `git diff`

## 10. Testing Strategy

### Unit Tests

- Ticker trim/uppercase/regex/blank/duplicate behavior.
- API URL/base/query construction, success parsing, structured error parsing,
  non-JSON error fallback, network failure, and abort propagation.
- Number/currency/compact/percentage/date/provider/safe-link formatting,
  including null/non-finite values and the two distinct percent conventions.
- Chart point adaptation and mode-aware label formatting.

### Component and Hook Tests

- Search keyboard and button submission, validation, focus, editable failures,
  busy state, and unchanged active controls.
- Company data, safe links/logo fallback, positive/negative/zero change, and
  null values.
- Price/return chart controls, summaries, empty series, and period/mode events.
- Metric units/outcomes, neutral/insufficient labels, backend ordering, and
  absence of frontend winner calculation.
- News ordering/cap, safe article links, dates, descriptions, and empty state.
- Section warning placement, neutral AI placeholder, provenance, and disclaimer.
- Page initial skeleton, success, retained-data loading, structured/unknown
  errors, retry, partial success, URL state, cancellation, and stale guards.

### Integration Boundaries

- Fetch is mocked in every frontend test. Tests require no running backend,
  PostgreSQL, Redis, FMP, Yahoo, or OpenAI.
- The backend regression suite verifies Milestones 1–5 remain intact.

### Manual Verification

- Desktop default AAPL/MSFT load and all dashboard sections.
- GOOGL/AMZN submit, visible loading, URL update, and side identity.
- All periods/modes, active states, URL and chart/summary changes, and no active
  control duplicate request.
- Blank, duplicate, invalid-character, and unknown tickers.
- Approximately 1440, 1024, 768, and 390 px layouts; keyboard navigation,
  visible focus, safe external links, and absence of horizontal page overflow.

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Large aligned series makes chart/tests slow | Poor interaction | Pass backend-aligned points directly, avoid frontend joins, and use compact fixtures in tests. |
| Request races overwrite newer controls | Wrong dashboard/URL | Abort previous requests and compare request identity before committing state. |
| Percentage conventions are mixed | Values off by 100x | Centralize formatters and test daily/history percentage points separately from metric fractions. |
| Null provider values break formatting | Runtime errors or misleading zeros | Model nullability explicitly and render em dash/empty states. |
| Metric color implies an invented winner | Financially misleading UI | Style only the backend outcome and pair color with explicit outcome text. |
| Untrusted provider URLs/content create risk | Unsafe navigation/rendering | Permit only HTTP(S), use safe rel attributes, and render text only. |
| Chart is inaccessible | Information unavailable without vision | Add textual return/range summaries, labels, and semantic controls. |
| Mock image cannot be copied exactly without fake AI | Visual gap | Preserve hierarchy and use a clearly labeled neutral placeholder only. |
| Live providers are slow or unavailable during smoke | Incomplete manual evidence | Automated tests mock Fetch; document honest manual limitations without weakening required build checks. |
| CSS overflows at mobile widths | Broken target layout | Use min-width-safe grids, overflow-aware chart container, wrapping, and verify 390 px browser dimensions. |

## 12. Rollback / Recovery

No database or backend rollback is required. Revert the frontend feature files,
restore the original scaffold files, and remove Recharts from `package.json` and
the lockfile. The completed backend and all durable data remain unchanged.

## 13. Progress

- [x] Read `AGENTS.md`, `.agent/PLANS.md`, full `docs/design.md`, completed
  Milestone 5 plan, comparison DTOs/controller, current frontend source and
  configuration, package-lock root, and complete clean Git state.
- [x] Inspected the approved UI image at its authoritative path.
- [x] Reconciled scope, backend contract, formatting semantics, state behavior,
  dependency choice, warning behavior, and AI placeholder without a conflict.
- [x] Created a coherent Ready execution plan before implementation.
- [x] Phase 1 complete — typed API and formatting foundation.
- [x] Phase 2 complete — search, URL state, and request lifecycle.
- [x] Phase 3 complete — dashboard sections and tests.
- [x] Phase 4 complete — responsive integration and full frontend validation.
- [x] Phase 5 complete — backend regression, browser smoke, and final review.
- [x] Acceptance criteria confirmed.

## 14. Decision Log

| Date | Decision | Reason | Alternative |
|---|---|---|---|
| 2026-07-20 | Use Recharts | Explicitly selected in design and suited to responsive dual-line charts | Native SVG or another library |
| 2026-07-20 | Use native Fetch and local hook state | Existing platform capabilities cover one page without global orchestration | Axios/query/state/router dependency |
| 2026-07-20 | Use relative API paths plus Vite proxy by default | Same-origin production compatibility and simple local development | Hardcoded backend origin or permissive CORS |
| 2026-07-20 | Auto-load AAPL/MSFT defaults and read valid query state | Satisfies immediate demo value, refresh persistence, and shareability | Empty initial dashboard |
| 2026-07-20 | Replace null AI with a neutral availability note | Preserves approved hierarchy without fabricating Milestone 7 content | Omit the section entirely |
| 2026-07-20 | Retain the current dashboard during control reloads | Prevents disruptive blanking while still exposing busy state | Full skeleton on every control click |
| 2026-07-20 | Use 1200 and 768 px primary CSS breakpoints | Matches approved desktop/tablet/mobile design | Device-specific breakpoints |

## 15. Validation Evidence

| Command / check | Result | Notes |
|---|---|---|
| Initial Git status/diff/diff-check | PASS | Repository was clean before Milestone 6 changes. |
| Approved UI image inspection | PASS | 1122 x 1402 PNG present; not moved or modified. |
| Frontend scaffold/API contract review | PASS | React/Vite scaffold and completed comparison response are compatible. |
| `npm install recharts` | PASS | Added design-approved Recharts 3.9.2; 0 reported vulnerabilities. Sandbox DNS required the normal approved network retry. |
| `npm run lint` | PASS | ESLint completed with no warnings or errors. |
| `npm run typecheck` | PASS | Strict TypeScript project references completed successfully. |
| Final `npm install` | PASS | Lockfile was current and audit reported 0 vulnerabilities. The local Node 23 runtime emitted engine warnings because the toolchain supports Node 20/22/24 rather than odd-numbered Node 23. |
| Final `npm test` | PASS | 8 files, 27 tests, 0 failures; all Fetch behavior is mocked and no service is required. |
| Final `npm run build` | PASS | Vite production build completed; emitted an informational 574.76 kB entry-chunk warning. |
| `cd backend && ./mvnw clean verify` | PASS | 127 tests, 0 failures/errors; PostgreSQL/Redis Testcontainers and the full Milestone 1–5 regression suite passed after the sandbox-only Docker socket denial was rerun with Docker access. |
| Live default browser smoke | PASS | AAPL/MSFT auto-loaded at 1Y/RETURN with two company cards, two chart lines, four metric groups, two news panels, provenance, exact disclaimer, neutral AI placeholder, and no console errors. |
| Live alternate/control smoke | PASS | GOOGL/AMZN preserved left/right order; 1M, 6M, 1Y, 5Y, MAX, PRICE, and RETURN updated active state, URL, and summaries. Repeated live calls eventually produced expected provider warnings/rate limiting, which the partial/error UI displayed safely. |
| Live validation/responsive smoke | PASS | Invalid character, blank, and duplicate cases stayed client-side; a valid unknown submission received a safe rate-limit response while preserving editable values and the prior dashboard. 1440/1024/768/390 px had no page overflow; mobile stacked search/cards/metrics/news and retained a visible chart. |
| Complete diff/security/scope audit | PASS | Only the plan and frontend changed; no backend/design/image/CORS/migration/provider/AI/cache/deployment file changed. No new secret, local env, generated artifact, raw HTML, broad `any`, console logging, or unsafe link was added. |

## 16. Known Limitations and Remaining Work

- `aiBrief` remains null and displays only a neutral availability note until
  Milestone 7.
- No client or Redis cache, refresh action, or manual regeneration exists;
  those are later milestones.
- URL state uses the current single page and query string without a router.
- Recharts exposes the backend's aligned daily data but does not invent missing
  dates, interpolate, zoom, or perform financial calculations.
- The production bundle is 574.76 kB before gzip (170.86 kB gzip) and triggers
  Vite's informational 500 kB warning. Route-level splitting is deferred because
  the MVP intentionally has one page.
- Recharts 3.9.2 is the sole direct production dependency added. It uses Redux
  packages internally, but StockLens does not declare, import, or use Redux as
  application state. An attempted switch to the older compatible Recharts 2
  line could not be downloaded after the environment's network-approval quota
  was exhausted; no workaround or lockfile fabrication was used.
- Use an even-numbered supported Node release (22 or 24) to avoid the local
  Node 23 engine warnings. Validation still completed successfully on Node 23.
