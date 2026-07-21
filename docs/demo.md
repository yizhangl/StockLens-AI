# Three-Minute Demo

This script demonstrates implemented behavior without presenting StockLens AI
as a trading or recommendation product.

## Before the Demo

- Use your own untracked `.env` values.
- Start PostgreSQL and Redis, then the backend and frontend using the root README.
- Confirm the page contains no credential, raw provider payload, or private data.
- Prefer a pair with already persisted data to reduce quota and latency risk.

## Script

### 0:00–0:30 — Product and comparison

Open the comparison page with `AAPL` and `MSFT`. Explain that StockLens AI is an
educational research dashboard backed by persisted financial data and news, not
financial advice.

Point out the two company cards, latest-available quote disclosure, provenance,
and the missing-value convention.

### 0:30–1:00 — Performance and metrics

Switch from `RETURN` to `PRICE`, then back to `RETURN`, and select another period.
Explain that return mode aligns common trading dates and is more comparable than
raw share prices.

Show the four metric categories. Emphasize that comparison outcomes come from
backend metric definitions; the frontend does not assume every larger number is
better.

### 1:00–1:25 — Recent developments

Review the two news columns and open one safe original-article link. Mention that
Yahoo Finance is an unofficial replaceable news adapter and that deterministic
relevance filtering rejects articles supported only by a ticker tag.

### 1:25–2:15 — Grounded AI brief

Select **Generate AI brief**. Show the structured summary, four categories,
risks, local source IDs, and backend-resolved source metadata.

Explain that the model sees only persisted StockLens context; output is typed,
validated, limited to 15 unique citations, and repaired once at most. Invalid
repaired output is not persisted or cached.

Generate again without changing source data to demonstrate cached or persisted
brief reuse and its visible status.

### 2:15–2:45 — Refresh and durability

Select **Refresh data**. Explain the cache-aside order: Redis, fresh PostgreSQL,
then providers only when needed. PostgreSQL remains durable and Redis is
best-effort.

Show that data refresh reloads the dashboard but does not automatically spend an
OpenAI request. AI regeneration is a separate explicit action.

### 2:45–3:00 — Engineering close

Summarize the modular Spring Boot backend, React/TypeScript frontend, Flyway,
PostgreSQL/Redis Testcontainers, mocked providers, fake AI testing, and isolated
CI-style validation.

## Screenshot Checklist

The approved screenshot is already tracked at
`docs/images/stocklens-final-ui.png`. Before replacing it in a future milestone,
confirm that a proposed capture:

- uses representative public tickers and no personal data;
- exposes no keys, `.env` contents, prompts, request IDs, or raw payloads;
- shows the complete dashboard at a readable desktop width;
- includes cached/new status, provenance, and the educational disclaimer;
- has no loading spinner, error banner, clipped text, or browser developer UI;
- is a clear quality improvement over the approved image.

