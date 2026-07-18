# StockLens AI — System Design Document

**Status:** Approved for MVP implementation  
**Version:** 2.0  
**Date:** July 2026  
**Author:** Licy Li

---

## 1. Executive Summary

StockLens AI is a full-stack, AI-assisted stock research application that lets a user compare two publicly traded companies in one screen.

The user enters two ticker symbols, such as `AAPL` and `MSFT`. The system aggregates company profiles, current market data, historical prices, valuation metrics, profitability metrics, growth metrics, financial-health indicators, and recent news. It then generates a structured, source-grounded AI comparison brief.

The final MVP experience is a single comparison dashboard containing:

1. Two-stock search
2. Side-by-side company summary cards
3. AI comparison brief
4. Historical price-performance chart
5. Grouped financial metrics
6. Recent company developments
7. Data-source, freshness, and disclaimer information

The application is a research and educational tool. It does not execute trades, predict prices, or provide personalized investment advice.

The project is also designed as a portfolio-quality software engineering project that demonstrates:

- Java and Spring Boot backend development
- REST API design
- React and TypeScript frontend development
- External financial and news API integration
- PostgreSQL data modeling
- Redis caching
- Structured OpenAI integration through Spring AI
- Source-grounded AI output and validation
- Unit and integration testing
- Docker-based local development
- Flyway migrations
- OpenAPI documentation
- GitHub Actions CI

---

## 2. Problem Statement

Stock research is fragmented across company-profile pages, financial-data sites, price charts, news providers, and analyst commentary. A user who wants to compare two companies often needs to open several browser tabs and manually reconcile:

- Company descriptions
- Market capitalization
- Valuation multiples
- Profitability
- Growth rates
- Balance-sheet indicators
- Historical price performance
- Recent news and company developments

This creates unnecessary context switching and makes quick, high-level comparison difficult.

StockLens AI solves this problem by providing one comparison workflow that:

1. Retrieves data from external providers
2. Normalizes inconsistent provider formats
3. Stores durable data in PostgreSQL
4. Caches frequently reused results in Redis
5. Presents a side-by-side comparison in a clear frontend
6. Generates an AI brief grounded only in supplied financial data and news

---

## 3. Product Vision

StockLens AI should feel like a modern research assistant rather than a trading terminal.

The product should help a user answer:

- What does each company do?
- How do their valuations differ?
- Which company currently has stronger profitability or growth metrics?
- How have the stocks performed over the selected period?
- What recent developments may matter?
- What are the main strengths, trade-offs, and risks shown by the available data?

The product should not answer:

- Which stock should I buy?
- What price will the stock reach?
- How should I allocate my personal portfolio?

---

## 4. Goals

### 4.1 Product Goals

The MVP must allow a user to:

1. Enter and validate two stock ticker symbols.
2. Retrieve a side-by-side company overview.
3. Compare selected financial metrics by category.
4. Compare historical stock performance over a selected period.
5. Read recent news for each company.
6. Generate a structured AI comparison brief.
7. See the data sources and update timestamps used in the comparison.
8. Refresh stale comparison data.
9. Reuse previously retrieved or generated data when it is still fresh.

### 4.2 Engineering Goals

The project should demonstrate:

- Clear frontend, controller, service, repository, client, and domain boundaries
- A modular monolith rather than unnecessary microservices
- Provider-independent financial and news integrations
- Durable storage in PostgreSQL
- Explicit cache-aside behavior in Redis
- Typed and validated AI output
- Controlled failure handling for third-party services
- Reproducible local development
- Automated testing and CI
- Documented trade-offs

### 4.3 Portfolio Goals

A reviewer should be able to:

- Understand the product in under 30 seconds from the screenshot and README
- Start dependencies with Docker Compose
- Run the backend and frontend locally
- Explore backend endpoints through Swagger UI
- Run automated tests
- Understand why PostgreSQL, Redis, Spring AI, Flyway, and Testcontainers are used
- Review a clean Git history organized by milestone

---

## 5. Non-Goals

The following are outside the MVP:

- Real-time trading
- Brokerage integration
- Portfolio management
- Personalized investment recommendations
- Price prediction
- Technical-indicator analysis
- Intraday or tick-level market data
- Options or derivatives analysis
- Social sentiment analysis
- User accounts and authentication
- Saved watchlists
- Payment processing
- Push notifications
- Native mobile applications
- Multi-agent AI workflows
- Vector databases or RAG pipelines
- Kafka, RabbitMQ, or background-job infrastructure
- Elasticsearch
- Kubernetes
- Microservices

The navigation may visually include a future `Watchlist` item, but it is not required for MVP completion.

---

## 6. Target Users

### 6.1 Primary Users

- Students learning financial analysis
- Retail investors performing lightweight company research
- Software recruiters and interviewers reviewing the project
- Developers interested in a complete Spring Boot and AI integration example

### 6.2 Primary Use Case

A user compares `AAPL` and `MSFT`.

The application returns:

- Both company profiles
- Current prices and daily changes
- Key financial metrics
- Historical return series
- Recent news for each company
- A structured AI comparison brief
- Supporting source references
- Data timestamps

---

## 7. Core User Stories

### US-1: Compare Two Companies

As a user, I want to enter two ticker symbols so that I can compare two companies in one view.

### US-2: View Company Summaries

As a user, I want to see the company name, sector, price, market cap, valuation, revenue, and short description so that I can quickly understand each business.

### US-3: Compare Price Performance

As a user, I want to compare historical performance over `1M`, `6M`, `1Y`, `5Y`, or `MAX` so that I can understand how the stocks performed over the same period.

### US-4: Compare Financial Metrics

As a user, I want key metrics grouped by valuation, profitability, growth, and financial health so that the data is easier to scan.

### US-5: Read Recent Developments

As a user, I want recent company news with source and date information so that I can understand current developments.

### US-6: Read an AI Comparison Brief

As a user, I want a concise AI-generated summary of the companies’ relative strengths and risks so that I do not need to interpret every metric manually.

### US-7: Verify AI Claims

As a user, I want the AI brief to identify its supporting financial data and news sources so that I can verify its claims.

### US-8: Reuse Fresh Results

As a user, I want repeated comparisons to load quickly when the underlying data has not changed.

### US-9: Refresh Data

As a user, I want to refresh stale data so that the comparison reflects the latest data available from configured providers.

---

## 8. Functional Requirements

### FR-1: Ticker Input and Validation

The system must:

- Accept exactly two tickers for a comparison
- Trim whitespace
- Normalize tickers to uppercase
- Reject blank inputs
- Reject unsupported characters and formats
- Reject duplicate tickers
- Return a clear error for unknown companies

Example:

```text
" aapl " -> "AAPL"
```

The initial supported format is:

```regex
^[A-Z][A-Z0-9.-]{0,9}$
```

Provider-specific symbols may require later adjustment.

### FR-2: Company Profile Retrieval

For each ticker, the system must retrieve and normalize:

- Ticker
- Company name
- Exchange
- Sector
- Industry
- Country
- Website
- Company description
- Logo URL, when available
- Provider symbol or identifier
- Retrieval timestamp

### FR-3: Market Snapshot Retrieval

For each ticker, the system should retrieve:

- Current or latest available price
- Absolute price change
- Percentage price change
- Currency
- Market status, when available
- Quote timestamp
- Market capitalization

The UI must clearly display when data is delayed or represents the latest available close rather than a live quote.

### FR-4: Financial Metrics Retrieval

The MVP should support the following metrics when available.

#### Valuation

- P/E (TTM)
- Forward P/E
- PEG ratio
- Price-to-sales ratio

#### Profitability

- Gross margin
- Net margin
- Return on equity

#### Growth

- Revenue growth
- Earnings growth

#### Financial Health / Risk

- Debt-to-equity ratio
- Current ratio
- Beta

#### Summary Metrics

- Market capitalization
- Revenue (TTM)

The exact provider field definitions must be documented because providers may calculate metrics differently.

### FR-5: Historical Price Retrieval

The system must support historical daily price data for:

- 1 month
- 6 months
- 1 year
- 5 years
- Maximum provider-supported range

Each data point should include:

- Ticker
- Trading date
- Adjusted close, preferred
- Raw close, optional
- Currency
- Retrieval timestamp

The comparison chart must support:

- Raw price mode
- Normalized return percentage mode

Normalized return is calculated as:

```text
returnPercent = ((currentValue / firstValue) - 1) * 100
```

Return percentage should be the default comparison mode because raw share prices are not directly comparable.

### FR-6: Recent News Retrieval

For each company, the system must retrieve recent news containing:

- External article ID, when available
- Headline
- Source name
- Canonical URL
- Publication timestamp
- Short description or snippet
- Associated ticker
- Retrieval timestamp
- Optional topic or sentiment label

The system must prevent duplicate articles using a provider ID or canonical URL hash.

The default dashboard should display up to three articles per company. The API may retrieve more for AI context.

### FR-7: AI Comparison Brief

The system must generate a structured comparison containing:

```json
{
  "overallSummary": "string",
  "advantages": {
    "valuation": {
      "winner": "MSFT",
      "explanation": "string"
    },
    "profitability": {
      "winner": "MSFT",
      "explanation": "string"
    },
    "growth": {
      "winner": "MSFT",
      "explanation": "string"
    },
    "financialHealth": {
      "winner": "AAPL",
      "explanation": "string"
    }
  },
  "keyRisks": [
    {
      "ticker": "AAPL",
      "text": "string",
      "sourceIds": [1]
    }
  ],
  "sourceIds": [1, 2]
}
```

A category may return `NEUTRAL` or `INSUFFICIENT_DATA` instead of forcing a winner.

### FR-8: Source Grounding

The AI prompt must assign local source identifiers to:

- Financial metric snapshots
- Company profile facts
- News articles

Example:

```text
[Metric M1] AAPL P/E (TTM): 28.74, as of 2026-07-17
[Metric M2] MSFT P/E (TTM): 33.21, as of 2026-07-17
[News N1] Microsoft announces... Reuters, 2026-07-16
```

The model must return only supplied source identifiers. The backend must validate that every returned source ID exists in the prompt context.

### FR-9: Comparison Persistence

The system must persist durable source data:

- Companies
- Market and financial snapshots
- Historical prices
- News articles
- Generated AI comparison briefs
- Brief-to-source relationships
- Retrieval and generation timestamps

The complete rendered dashboard response does not need to be stored as one large JSON document. It can be assembled from durable source tables and cached response objects.

### FR-10: Caching

Initial cache targets and TTLs:

| Cached data | Initial TTL |
|---|---:|
| Company profile | 24 hours |
| Quote / market snapshot | 15 minutes |
| Financial metrics | 6 hours |
| Historical price series | 6 hours |
| Recent news | 30 minutes |
| Comparison dashboard response | 15 minutes |
| AI comparison brief | 1 hour |

All TTL values must be configurable.

### FR-11: Manual Refresh

The application must support a refresh action that can:

1. Invalidate relevant Redis entries.
2. Retrieve current provider data.
3. Persist new snapshots where appropriate.
4. Rebuild the dashboard response.
5. Optionally regenerate the AI brief.

Refreshing raw market data and regenerating the AI brief should remain separable to avoid unnecessary OpenAI cost.

### FR-12: Data Freshness and Provenance

The dashboard must display:

- Last updated timestamp
- Financial-data provider
- News-data provider
- Whether the response was cached
- Whether values are delayed, if known

### FR-13: API Documentation

All public backend endpoints must be documented through OpenAPI and visible in Swagger UI.

### FR-14: Responsive Frontend

The comparison page must work on:

- Desktop
- Tablet
- Mobile

The desktop experience is the primary MVP target.

---

## 9. Non-Functional Requirements

### 9.1 Reliability

- Third-party failures must not corrupt stored data.
- Refresh writes should be transactional where appropriate.
- Redis failure must not make PostgreSQL-backed reads unavailable.
- Invalid AI output must produce a controlled fallback or API error.
- Partial provider success should be represented explicitly rather than silently fabricating missing fields.

### 9.2 Performance

Target local response times:

| Request type | Target |
|---|---:|
| Cached dashboard response | < 500 ms |
| PostgreSQL-backed dashboard assembly | < 1.5 s |
| Provider refresh without AI | Best effort; typically < 8 s |
| AI brief generation | Best effort; typically < 15 s |

These are development targets, not production SLAs.

### 9.3 Maintainability

The codebase should:

- Use feature-oriented package boundaries
- Keep provider DTOs separate from domain models
- Keep business logic outside controllers
- Centralize cache keys and TTL configuration
- Centralize metric definitions and formatting rules
- Avoid interfaces without a meaningful boundary
- Avoid premature distributed-system abstractions

### 9.4 Security

- API keys must come from environment variables or platform secrets.
- No secret may be committed to Git.
- Logs must not contain credentials.
- Ticker input must be validated.
- External URLs must be treated as untrusted.
- HTML from providers must not be rendered unsanitized.
- AI output must be treated as untrusted data and validated.
- CORS should allow only configured frontend origins outside local development.

### 9.5 Observability

The MVP should include:

- Structured application logs
- Request IDs or correlation IDs
- Provider request duration and failure logging
- Cache hit/miss logging at debug level
- AI generation duration and validation status
- Spring Boot Actuator health endpoint

A full monitoring dashboard is not required for MVP.

### 9.6 Accessibility

The frontend should:

- Use semantic HTML
- Support keyboard navigation
- Maintain visible focus states
- Use text labels in addition to red/green color
- Maintain reasonable color contrast
- Include accessible chart labels or summary text

---

## 10. Final Frontend Design

The approved MVP is a single-page comparison dashboard.

Recommended screenshot location in the repository:

```text
docs/images/stocklens-final-ui.png
```

### 10.1 Page Information Hierarchy

1. Navigation bar
2. Compare Stocks search area
3. Two company summary cards
4. AI Comparison Brief
5. Price Performance chart
6. Key Financial Metrics
7. Recent Developments
8. Data sources, update time, and disclaimer

### 10.2 Navigation Bar

Contents:

- StockLens AI logo and name
- `Compare` navigation item
- Optional disabled or future `Watchlist` item
- Data freshness text on the right

MVP requirement: only the Compare page must function.

### 10.3 Search Area

Contents:

- Page title: `Compare Stocks`
- Subtitle explaining the product
- Left ticker input
- `VS` separator
- Right ticker input
- Primary `Compare` button

States:

- Empty
- Typing
- Loading
- Invalid ticker
- Duplicate ticker
- Successful comparison

### 10.4 Company Summary Cards

Desktop layout: two equal-width cards.

Each card contains:

- Company logo, when available
- Ticker
- Company name
- Exchange or sector badge
- Latest price
- Absolute and percentage daily change
- Market capitalization
- P/E (TTM)
- Revenue (TTM)
- Short company description
- Optional details link for future use

Descriptions should remain short to preserve scanability.

### 10.5 AI Comparison Brief

The AI section appears before the chart and metrics because it is the product’s primary differentiator.

Contents:

- Overall comparison summary
- Valuation advantage card
- Profitability advantage card
- Growth advantage card
- Financial-health or lower-leverage card
- Key risks
- Source count / source link
- Regenerate button

The AI brief must not use language such as `buy`, `sell`, `guaranteed`, or `will outperform`.

### 10.6 Price Performance Chart

Controls:

- `1M`
- `6M`
- `1Y`
- `5Y`
- `MAX`
- `Price`
- `Return %`

Default:

- Time range: `1Y`
- Mode: `Return %`

The section should include text summaries of the selected-period return for accessibility and fast scanning.

### 10.7 Key Financial Metrics

Metrics are grouped into four cards:

1. Valuation
2. Profitability
3. Growth
4. Financial Health

The dashboard shows only the main metrics. A future `View all metrics` interaction may expand a complete table.

Metric highlighting must follow explicit rules, not simply highlight the larger value.

Examples:

| Metric | Comparison rule |
|---|---|
| P/E | Context-dependent; lower may indicate cheaper valuation |
| Forward P/E | Context-dependent; lower may indicate cheaper valuation |
| PEG | Lower is generally preferable when positive |
| Gross margin | Higher is generally preferable |
| Net margin | Higher is generally preferable |
| ROE | Higher may be preferable but can be distorted by leverage |
| Revenue growth | Higher is generally preferable |
| Debt/equity | Lower generally indicates lower leverage |
| Current ratio | Must be interpreted by range, not simple maximum |
| Beta | Descriptive risk measure; no universal winner |

The UI may color a metric only when the comparison rule is defined and defensible. Otherwise, values remain neutral.

### 10.8 Recent Developments

Desktop layout: one column per company.

Each item contains:

- Headline
- Source
- Publication date
- Optional topic or sentiment tag
- Link to the original source

The dashboard displays up to three items per company.

### 10.9 Footer

Contents:

- Market / financial data provider
- News data provider
- Last updated timestamp
- Informational disclaimer

Suggested disclaimer:

> StockLens AI provides automated research summaries for informational and educational purposes only. It does not constitute financial advice.

### 10.10 Responsive Behavior

#### Desktop: >= 1200 px

- Two-column company cards
- Four metric cards in one row or a 2x2 grid
- Two-column news panels
- Full-width chart

#### Tablet: 768–1199 px

- Company cards may remain two-column when space permits
- Metric cards use a 2x2 grid
- News may use two columns or stack

#### Mobile: < 768 px

- Search inputs stack vertically
- Company cards stack
- AI advantage cards stack or wrap
- Metric cards become one column
- News panels stack
- Chart supports horizontal padding and simplified labels

---

## 11. High-Level Architecture

```text
Browser
  |
  v
React + TypeScript Frontend
  |
  | HTTPS / JSON
  v
Spring Boot REST API
  |
  +--> Comparison Orchestrator
  |      |
  |      +--> Company Service
  |      +--> Market Data Service
  |      +--> Financial Metrics Service
  |      +--> Historical Price Service
  |      +--> News Service
  |      +--> AI Comparison Service
  |
  +--> PostgreSQL
  +--> Redis
  +--> Financial Data Provider
  +--> News Provider
  +--> OpenAI through Spring AI
```

### 11.1 Main Dashboard Request Flow

```text
1. User enters two tickers.
2. Frontend validates basic input.
3. Frontend requests a comparison dashboard.
4. Backend normalizes and validates tickers.
5. Backend checks comparison response cache.
6. On cache miss, backend loads fresh-enough source data from PostgreSQL.
7. Missing or stale data is fetched from external providers.
8. Provider data is normalized and persisted.
9. Historical series are aligned by trading date.
10. Metric comparison rules are applied.
11. Existing fresh AI brief is loaded, or a new brief is generated when requested.
12. Backend returns one dashboard DTO.
13. Frontend renders all sections.
```

### 11.2 Refresh Flow

```text
1. User selects refresh.
2. Backend invalidates affected cache entries.
3. Backend retrieves provider data.
4. Backend persists new snapshots.
5. Backend rebuilds the dashboard response.
6. AI is regenerated only when explicitly requested or configured.
```

---

## 12. Technology Stack

| Area | Technology |
|---|---|
| Frontend language | TypeScript |
| Frontend framework | React |
| Frontend build tool | Vite |
| Charting | Recharts |
| HTTP client | Fetch API or Axios |
| Backend language | Java 21 |
| Backend framework | Spring Boot |
| AI integration | Spring AI with OpenAI |
| Persistence | Spring Data JPA |
| Database | PostgreSQL |
| Cache | Redis |
| Database migration | Flyway |
| API documentation | OpenAPI / Swagger UI |
| Unit testing | JUnit 5, Mockito, Vitest |
| Integration testing | Testcontainers |
| Frontend component testing | React Testing Library |
| End-to-end testing | Optional Playwright smoke test |
| Build tools | Maven and npm |
| Local infrastructure | Docker Compose |
| CI | GitHub Actions |
| Deployment | Render or Railway |
| Development assistant | Codex |

Recharts is the selected charting library for the MVP. It integrates naturally with React, supports the required responsive line charts and custom tooltips, and keeps implementation complexity lower than a more feature-heavy charting platform. A different chart library should only be considered later if the product requires substantially more advanced interactions or visualization types.

---

## 13. Key Architecture Decisions

### 13.1 Modular Monolith

The backend will be a modular monolith.

Reasons:

- One developer owns the project.
- Expected traffic is low.
- Deployment should remain simple.
- Transactions and debugging are easier.
- Domain boundaries can still be represented through packages.

A separate Python AI service is unnecessary because the project uses a hosted model through Spring AI and does not require a Python-specific ML pipeline.

### 13.2 Backend-for-Frontend Dashboard Endpoint

The frontend should receive one aggregated dashboard response instead of making many unrelated provider-shaped requests.

Reasons:

- Reduces frontend orchestration
- Avoids inconsistent timestamps across sections
- Centralizes validation and fallback behavior
- Simplifies loading and error states
- Allows caching of the assembled comparison response

Separate resource endpoints remain useful for testing and future detail pages.

### 13.3 PostgreSQL as Source of Truth

PostgreSQL stores durable application data. Redis stores disposable cache entries.

If Redis is unavailable or cleared, the system can reconstruct responses from PostgreSQL and external providers.

### 13.4 Snapshot-Based Financial Data

Financial and market metrics are stored as snapshots rather than overwriting one row forever.

Reasons:

- Preserves retrieval history
- Supports future metric charts
- Makes data-cutoff timestamps explicit
- Improves reproducibility of saved AI briefs

### 13.5 Provider Abstraction

External providers are accessed through interfaces.

```java
public interface FinancialDataClient {
    CompanyProfileData getCompanyProfile(String ticker);
    MarketSnapshotData getMarketSnapshot(String ticker);
    FinancialMetricsData getFinancialMetrics(String ticker);
    List<HistoricalPriceData> getHistoricalPrices(
        String ticker,
        LocalDate from,
        LocalDate to
    );
}
```

This prevents provider response formats from leaking into business services and enables fake clients in tests.

### 13.6 Structured AI Output

AI output is mapped into typed Java records and validated.

```java
public record ComparisonBriefResult(
    String overallSummary,
    Map<MetricCategory, AdvantageResult> advantages,
    List<RiskResult> keyRisks,
    List<String> sourceIds
) {}
```

The system must not treat arbitrary model text as trusted application data.

### 13.7 Cache-Aside Pattern

Read flow:

```text
1. Check Redis.
2. On miss, load or build response.
3. Store response in Redis.
4. Return response.
```

Redis failure should degrade performance, not correctness.

### 13.8 Explicit Metric Semantics

Metric direction and highlighting rules are centralized in a metric definition registry.

Example:

```java
public record MetricDefinition(
    MetricCode code,
    String displayName,
    MetricCategory category,
    ComparisonStrategy comparisonStrategy,
    String unit
) {}
```

This prevents the frontend from making simplistic or inconsistent judgments.

---

## 14. Proposed Repository Structure

```text
stocklens-ai/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/stocklens/
│       │   │   ├── common/
│       │   │   ├── company/
│       │   │   ├── market/
│       │   │   ├── financial/
│       │   │   ├── news/
│       │   │   ├── comparison/
│       │   │   └── research/
│       │   └── resources/
│       │       ├── db/migration/
│       │       └── application.yml
│       └── test/
├── frontend/
│   ├── package.json
│   └── src/
│       ├── api/
│       ├── components/
│       ├── features/comparison/
│       ├── hooks/
│       ├── pages/
│       ├── types/
│       └── utils/
├── docs/
│   ├── design.md
│   └── images/
│       └── stocklens-final-ui.png
├── docker-compose.yml
├── .env.example
└── README.md
```

### 14.1 Backend Package Structure

```text
com.stocklens
├── common
│   ├── config
│   ├── exception
│   ├── response
│   ├── time
│   └── validation
├── company
│   ├── domain
│   ├── repository
│   ├── service
│   └── dto
├── market
│   ├── client
│   ├── domain
│   ├── repository
│   ├── service
│   └── dto
├── financial
│   ├── client
│   ├── domain
│   ├── repository
│   ├── service
│   └── dto
├── news
│   ├── client
│   ├── domain
│   ├── repository
│   ├── service
│   └── dto
├── comparison
│   ├── controller
│   ├── service
│   ├── metric
│   └── dto
├── research
│   ├── ai
│   ├── domain
│   ├── repository
│   ├── service
│   └── dto
└── StockLensApplication.java
```

### 14.2 Frontend Structure

```text
frontend/src
├── api
│   ├── client.ts
│   └── comparisonApi.ts
├── components
│   ├── common
│   └── layout
├── features/comparison
│   ├── components
│   │   ├── StockSearchForm.tsx
│   │   ├── CompanySummaryCard.tsx
│   │   ├── AIComparisonBrief.tsx
│   │   ├── PricePerformanceChart.tsx
│   │   ├── MetricCategoryCard.tsx
│   │   ├── RecentDevelopments.tsx
│   │   └── DataProvenanceFooter.tsx
│   ├── hooks
│   ├── types
│   └── utils
├── pages
│   └── ComparePage.tsx
└── App.tsx
```

---

## 15. Data Model

### 15.1 Company

```text
Company
-------
id
 ticker
 name
 exchange
 sector
 industry
 country
 website_url
 description
 logo_url
 provider_symbol
 created_at
 updated_at
```

Constraints:

- `ticker` is unique.
- `ticker` is uppercase.
- `ticker` is not null.

### 15.2 Market Snapshot

```text
MarketSnapshot
--------------
id
 company_id
 price
 price_change
 price_change_percent
 market_cap
 currency
 quote_timestamp
 retrieved_at
 provider_name
 raw_data_json
```

### 15.3 Financial Metric Snapshot

```text
FinancialMetricSnapshot
-----------------------
id
 company_id
 pe_ttm
 forward_pe
 peg_ratio
 price_to_sales
 revenue_ttm
 gross_margin
 net_margin
 return_on_equity
 revenue_growth
 earnings_growth
 debt_to_equity
 current_ratio
 beta
 currency
 reported_at
 retrieved_at
 provider_name
 raw_data_json
```

A single normalized snapshot row is acceptable for the MVP. A more flexible metric-value schema can be introduced later only if provider variability makes it necessary.

### 15.4 Historical Price

```text
HistoricalPrice
---------------
id
 company_id
 trading_date
 open_price
 high_price
 low_price
 close_price
 adjusted_close
 volume
 currency
 provider_name
 retrieved_at
```

Constraint:

```text
unique(company_id, trading_date, provider_name)
```

### 15.5 News Article

```text
NewsArticle
-----------
id
 external_id
 headline
 source_name
 article_url
 description
 published_at
 retrieved_at
 url_hash
 provider_name
```

### 15.6 News Article Company

```text
NewsArticleCompany
------------------
news_article_id
 company_id
```

A join table supports articles associated with multiple companies.

### 15.7 Comparison Brief

```text
ComparisonBrief
---------------
id
 left_company_id
 right_company_id
 overall_summary
 advantages_json
 key_risks_json
 model_name
 prompt_version
 generated_at
 data_cutoff_at
 input_hash
```

The ticker pair must be stored in canonical order for cache and lookup consistency, while the response may preserve the user-selected display order.

### 15.8 Comparison Brief Source

```text
ComparisonBriefSource
---------------------
comparison_brief_id
 source_type
 source_reference
 news_article_id nullable
 financial_snapshot_id nullable
 market_snapshot_id nullable
```

For MVP simplicity, the application may persist news relations and store metric-source IDs in structured JSON. The schema should not become unnecessarily complex before implementation proves the need.

---

## 16. API Design

Base path:

```text
/api/v1
```

### 16.1 Primary Dashboard Endpoint

```http
GET /api/v1/comparisons?left=AAPL&right=MSFT&period=1Y&mode=RETURN
```

Example response:

```json
{
  "comparisonId": "AAPL:MSFT:1Y:RETURN",
  "left": {
    "ticker": "AAPL",
    "companyName": "Apple Inc.",
    "exchange": "NASDAQ",
    "sector": "Technology",
    "price": 192.62,
    "priceChange": 1.35,
    "priceChangePercent": 0.71,
    "marketCap": 2960000000000,
    "peTtm": 28.74,
    "revenueTtm": 394330000000,
    "description": "Apple designs and markets consumer technology products."
  },
  "right": {
    "ticker": "MSFT",
    "companyName": "Microsoft Corporation",
    "exchange": "NASDAQ",
    "sector": "Technology",
    "price": 417.11,
    "priceChange": -0.92,
    "priceChangePercent": -0.22,
    "marketCap": 3100000000000,
    "peTtm": 33.21,
    "revenueTtm": 245120000000,
    "description": "Microsoft develops software, cloud services, and devices."
  },
  "pricePerformance": {
    "period": "1Y",
    "mode": "RETURN",
    "leftReturnPercent": 18.4,
    "rightReturnPercent": -7.2,
    "series": []
  },
  "metricGroups": [],
  "news": {
    "left": [],
    "right": []
  },
  "aiBrief": null,
  "provenance": {
    "financialProvider": "configured-provider",
    "newsProvider": "configured-provider",
    "lastUpdatedAt": "2026-07-18T22:40:00Z",
    "cached": false
  }
}
```

The dashboard may initially return `aiBrief: null` and load the brief with a separate request to avoid blocking the entire page.

### 16.2 Generate or Retrieve AI Comparison Brief

```http
POST /api/v1/comparisons/research
Content-Type: application/json
```

Request:

```json
{
  "leftTicker": "AAPL",
  "rightTicker": "MSFT",
  "forceRefresh": false
}
```

Response:

```json
{
  "overallSummary": "Microsoft currently shows lower valuation multiples and stronger recent revenue growth, while Apple displays different profitability and leverage characteristics.",
  "advantages": {
    "valuation": {
      "winner": "MSFT",
      "explanation": "MSFT has the lower P/E in the supplied snapshot.",
      "sourceIds": ["M1", "M2"]
    }
  },
  "keyRisks": [],
  "sources": [],
  "generatedAt": "2026-07-18T22:41:00Z",
  "cached": false
}
```

### 16.3 Refresh Comparison Data

```http
POST /api/v1/comparisons/refresh
Content-Type: application/json
```

Request:

```json
{
  "tickers": ["AAPL", "MSFT"],
  "regenerateBrief": false
}
```

### 16.4 Supporting Resource Endpoints

```http
GET /api/v1/stocks/{ticker}
GET /api/v1/stocks/{ticker}/metrics
GET /api/v1/stocks/{ticker}/history?period=1Y
GET /api/v1/stocks/{ticker}/news?limit=10
```

These are useful for testing, future detail pages, and Swagger demonstration.

### 16.5 Health Endpoint

```http
GET /actuator/health
```

---

## 17. Frontend Data Flow and State

### 17.1 URL State

The selected tickers and chart options should be represented in the URL where practical.

Example:

```text
/compare?left=AAPL&right=MSFT&period=1Y&mode=RETURN
```

Benefits:

- Shareable comparisons
- Browser back/forward support
- Refresh persistence

### 17.2 Loading Strategy

Recommended loading sequence:

1. Load dashboard source data.
2. Render company cards, chart, metrics, and news.
3. Load existing or generated AI brief separately.

This prevents OpenAI latency from blocking the entire dashboard.

### 17.3 UI States

The frontend must support:

- Initial empty state
- Dashboard loading state
- Partial section loading
- AI brief loading state
- Invalid input state
- Unknown ticker state
- Provider error state
- Partial-data warning state
- Complete success state

### 17.4 Data Formatting

The backend returns numeric values. The frontend formats them for display.

Examples:

```text
2960000000000 -> $2.96T
394330000000 -> $394.33B
0.246 -> 24.6%
```

Numeric data must not be persisted as formatted strings.

---

## 18. Error Handling

The API returns a consistent structure:

```json
{
  "code": "STOCK_NOT_FOUND",
  "message": "No company was found for ticker INVALID.",
  "timestamp": "2026-07-18T22:45:00Z",
  "path": "/api/v1/comparisons",
  "requestId": "abc-123",
  "details": []
}
```

Initial error codes:

| Code | HTTP status |
|---|---:|
| INVALID_TICKER | 400 |
| DUPLICATE_TICKERS | 400 |
| STOCK_NOT_FOUND | 404 |
| FINANCIAL_PROVIDER_ERROR | 502 |
| NEWS_PROVIDER_ERROR | 502 |
| AI_PROVIDER_ERROR | 502 |
| INVALID_AI_RESPONSE | 502 |
| RATE_LIMITED | 429 |
| DATA_UNAVAILABLE | 503 |
| INTERNAL_ERROR | 500 |

Raw third-party errors must not be returned directly to the browser.

The dashboard may return partial data when one non-critical section fails. Partial data must include warnings such as:

```json
{
  "warnings": [
    {
      "section": "NEWS",
      "message": "Recent news is temporarily unavailable."
    }
  ]
}
```

---

## 19. External API Integration

The MVP should use one financial-data provider and one news provider.

Provider selection criteria:

- Free-tier or low-cost availability
- Request limits
- Availability of fundamentals and historical prices
- Availability of company news
- Stable documentation
- Legal permission to display returned data
- Java integration simplicity

Provider DTOs must remain inside the provider adapter.

Transformation flow:

```text
Provider JSON
    -> Provider DTO
    -> Normalization Mapper
    -> Domain / Persistence Model
    -> API DTO
```

Required client behavior:

- Configurable connection timeout
- Configurable read timeout
- Limited retries for transient failures
- No retry for invalid tickers
- Rate-limit detection
- Sanitized logging
- Response validation

A resilience framework such as Resilience4j should be added only when actual retry/circuit-breaker requirements justify it.

---

## 20. AI Generation Design

### 20.1 Input Context

The model receives:

- Both company names and descriptions
- Selected financial metrics for both companies
- Metric timestamps
- Historical-period return summary
- Recent news for both companies
- Source identifiers
- Explicit output schema and constraints

The model does not need every historical price point. The backend should provide summarized return values and relevant trend facts.

### 20.2 Prompt Rules

The prompt must instruct the model to:

- Use only supplied information
- Compare rather than recommend
- Avoid personalized advice
- Avoid price forecasts
- Distinguish facts from interpretation
- Use only valid source IDs
- Return structured JSON
- Return `INSUFFICIENT_DATA` when evidence is missing
- Avoid claiming that one company is universally better

### 20.3 Prompt Versioning

Every stored brief records a prompt version.

Example:

```text
stock-comparison-v1
```

Material prompt changes require a new version.

### 20.4 Output Limits

Initial limits:

- Overall summary: maximum 180 words
- Explanation per advantage: maximum 80 words
- Key risks: maximum 6 total
- Source IDs: maximum 15

### 20.5 Validation

The backend validates:

- Required fields
- Allowed category names
- Allowed winner values
- Text length limits
- Nonblank content
- Valid source IDs
- No unsupported ticker symbols

One repair retry is allowed. A second invalid response returns `INVALID_AI_RESPONSE` or a controlled no-brief fallback.

### 20.6 Input Hashing

A comparison brief should store a hash based on:

- Canonical ticker pair
- Financial snapshot IDs
- News article IDs
- Prompt version
- Model name

If the input hash has not changed and the cached brief is fresh, the application can reuse the previous result.

---

## 21. Caching Design

### 21.1 Cache Keys

```text
stocklens:company:{ticker}
stocklens:market:{ticker}
stocklens:metrics:{ticker}
stocklens:history:{ticker}:{period}
stocklens:news:{ticker}:{limit}
stocklens:comparison:{left}:{right}:{period}:{mode}
stocklens:brief:{left}:{right}:{inputHash}
```

Ticker pairs must be canonicalized for storage and cache lookup.

### 21.2 Invalidation

Refreshing a ticker invalidates:

- Company profile cache
- Market snapshot cache
- Financial metrics cache
- Historical price cache
- News cache
- Any comparison cache containing that ticker
- Any AI brief cache based on superseded input data

### 21.3 Redis Failure Behavior

On read failure:

- Log the failure.
- Continue using PostgreSQL or providers.

On write failure:

- Log the failure.
- Return the successfully built response.

---

## 22. Database Migration Strategy

Flyway files:

```text
backend/src/main/resources/db/migration
```

Initial migrations:

```text
V1__create_company_table.sql
V2__create_market_snapshot_table.sql
V3__create_financial_metric_snapshot_table.sql
V4__create_historical_price_table.sql
V5__create_news_tables.sql
V6__create_comparison_brief_tables.sql
V7__add_indexes_and_constraints.sql
```

Recommended setting:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Flyway owns schema creation and modification.

---

## 23. Testing Strategy

### 23.1 Backend Unit Tests

Cover:

- Ticker normalization and validation
- Duplicate ticker rejection
- Provider DTO normalization
- Historical return calculation
- Trading-date series alignment
- Metric comparison strategies
- News deduplication
- Cache fallback behavior
- AI context construction
- AI source validation
- Input-hash construction
- Exception mapping

### 23.2 Repository Integration Tests

Use PostgreSQL Testcontainers to verify:

- Flyway migrations
- JPA mappings
- Unique ticker constraint
- Unique historical-price constraint
- Unique URL hash
- Latest snapshot queries
- News-company relationships
- Comparison brief persistence

### 23.3 Redis Integration Tests

Use Redis Testcontainers to verify:

- Serialization
- TTL behavior
- Cache invalidation
- Pair-key canonicalization
- Missing-value fallback

### 23.4 Controller Tests

Verify:

- HTTP statuses
- Query and request validation
- Response structure
- Error structure
- Partial-data warnings

### 23.5 Provider Client Tests

CI must not depend on paid live APIs.

Use mock HTTP responses for:

- Successful data
- Unknown ticker
- Timeout
- Rate limit
- Malformed response
- Missing fields

### 23.6 AI Tests

Use a fake AI client returning:

- Valid structured output
- Invalid JSON
- Invalid source IDs
- Unsupported winner values
- Empty output
- Provider error

A live OpenAI smoke test may be run manually and excluded from default CI.

### 23.7 Frontend Tests

Use Vitest and React Testing Library for:

- Search-form validation
- Loading and error states
- Company-card rendering
- Metric formatting
- Chart mode/range controls
- AI brief rendering
- Partial-data warnings

Optional Playwright smoke test:

```text
Enter AAPL and MSFT -> submit -> dashboard renders expected sections
```

---

## 24. Local Development

Docker Compose initially starts:

- PostgreSQL
- Redis

Backend:

```bash
docker compose up -d
cd backend
./mvnw spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Required environment variables:

```text
OPENAI_API_KEY
FINANCIAL_API_KEY
NEWS_API_KEY
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
SPRING_DATASOURCE_URL
SPRING_DATA_REDIS_HOST
VITE_API_BASE_URL
```

A `.env.example` file documents required values without real secrets.

---

## 25. CI Pipeline

GitHub Actions runs on pull requests and pushes to `main`.

Recommended jobs:

### Backend Job

1. Checkout
2. Set up Java 21
3. Cache Maven dependencies
4. Run formatting or static checks
5. Run `./mvnw verify`
6. Run unit and Testcontainers integration tests
7. Build JAR

### Frontend Job

1. Checkout
2. Set up Node.js
3. Cache npm dependencies
4. Run `npm ci`
5. Run lint
6. Run type checking
7. Run tests
8. Run production build

Deployment should be added only after both jobs are stable.

---

## 26. Deployment

The first deployment prioritizes simplicity.

Recommended options:

- Render
- Railway

Possible deployment layout:

```text
React static frontend
Spring Boot web service
Managed PostgreSQL
Managed Redis
```

The frontend and backend may be deployed separately.

Requirements:

- Environment-based secrets
- Configured CORS origin
- HTTPS
- Health check
- No production secrets in repository files

AWS is not part of the MVP because it would add infrastructure complexity without improving the core portfolio value.

---

## 27. Development Workflow with Codex

Codex is an implementation assistant, not the source of architectural decisions.

Each task should reference this document and remain narrowly scoped.

Good task:

```text
Implement the HistoricalPrice entity, Flyway migration, repository, and PostgreSQL Testcontainers tests according to Sections 15.4, 22, and 23. Do not implement controllers or external provider calls.
```

Poor task:

```text
Build StockLens AI.
```

For every generated change:

1. Review the diff.
2. Compare it with this design.
3. Run relevant tests.
4. Remove unnecessary abstractions.
5. Check error handling.
6. Check secrets and logs.
7. Commit one coherent feature.

Repository path:

```text
docs/design.md
```

---

## 28. MVP Milestones

### Milestone 1: Repository and Infrastructure

Deliverables:

- Monorepo structure
- Spring Boot project
- React/Vite project
- Docker Compose
- PostgreSQL connection
- Redis connection
- Flyway configuration
- Actuator health endpoint
- Basic CI skeleton

### Milestone 2: Company and Market Data

Deliverables:

- Company entity and migration
- Market snapshot entity and migration
- Ticker validation
- Financial provider interface
- Company and quote provider implementation
- Supporting stock endpoints
- Unit and integration tests

### Milestone 3: Financial Metrics and Historical Prices

Deliverables:

- Financial snapshot entity
- Historical price entity
- Provider normalization
- Return-percentage calculation
- Metric definition registry
- Metrics and history endpoints
- Tests

### Milestone 4: News Integration

Deliverables:

- News provider interface
- News provider implementation
- Article persistence
- Deduplication
- Company relationships
- News endpoint
- Tests

### Milestone 5: Aggregated Comparison API

Deliverables:

- Comparison orchestrator
- Dashboard DTO
- Series alignment
- Metric grouping and comparison rules
- Partial-data warnings
- Primary comparison endpoint
- Tests

### Milestone 6: Frontend Dashboard

Deliverables:

- Search form
- Company summary cards
- Price chart
- Metric category cards
- Recent news panels
- Responsive layout
- Loading and error states
- Frontend tests

### Milestone 7: AI Comparison Brief

Deliverables:

- AI context builder
- Spring AI integration
- Structured output schema
- Prompt template and version
- Source grounding
- Validation and repair retry
- Persistence
- AI brief frontend section
- Mocked AI tests

### Milestone 8: Caching and Refresh

Deliverables:

- Cache configuration
- Cache-aside behavior
- TTL configuration
- Comparison cache
- AI input-hash reuse
- Refresh endpoint
- Cache invalidation tests

### Milestone 9: Documentation and Polish

Deliverables:

- OpenAPI and Swagger UI
- Final README
- Architecture diagram
- Final screenshot
- Data-source disclosure
- Accessibility check
- Full CI

### Milestone 10: Deployment and Demo

Deliverables:

- Deployed frontend
- Deployed backend
- Managed PostgreSQL
- Managed Redis
- Public demo URL
- Demo video or GIF
- Resume-ready project bullets

---

## 29. Main Risks and Mitigations

### Risk 1: Financial Provider Limitations

Free APIs may have strict request limits, delayed data, or missing metrics.

Mitigation:

- Keep providers behind interfaces.
- Cache responses.
- Store snapshots.
- Keep metric set focused.
- Show missing data explicitly.
- Document provider definitions.

### Risk 2: Inconsistent Metric Definitions

Different providers may calculate P/E, growth, or margins differently.

Mitigation:

- Use one primary provider per metric category in MVP.
- Store provider name and timestamps.
- Document definitions.
- Avoid mixing provider values without disclosure.

### Risk 3: AI Hallucination

The model may generate unsupported claims.

Mitigation:

- Supply a controlled context.
- Use explicit source IDs.
- Require structured output.
- Validate IDs and fields.
- Allow `INSUFFICIENT_DATA`.
- Avoid recommendation language.

### Risk 4: Misleading Metric Highlighting

Simple `higher is better` logic may be financially incorrect.

Mitigation:

- Centralize comparison rules.
- Highlight only metrics with defensible semantics.
- Keep context-dependent metrics neutral.
- Use explanatory tooltips later if needed.

### Risk 5: Scope Expansion

Authentication, watchlists, RAG, agents, and cloud infrastructure could delay completion.

Mitigation:

- Treat non-goals as binding.
- Complete the comparison vertical slice first.
- Add no technology without a concrete problem.

### Risk 6: External Latency

Several provider calls plus OpenAI may make the page slow.

Mitigation:

- Cache aggressively within provider terms.
- Load AI separately.
- Fetch independent data concurrently.
- Persist provider data.
- Display section-level loading states.

### Risk 7: Generated Frontend Complexity

AI-generated UI code can become oversized or hard to maintain.

Mitigation:

- Keep components feature-oriented.
- Define typed API models.
- Remove duplicate formatting logic.
- Add frontend tests.
- Do not couple the application to a proprietary app-builder runtime.

---

## 30. Success Criteria

The MVP is complete when:

- A user can enter two valid tickers.
- Invalid and duplicate tickers are handled clearly.
- Both company summary cards render.
- Market and financial data are persisted.
- Historical prices are retrieved and compared.
- Price and return modes work.
- Metrics are grouped into four categories.
- Recent news is retrieved and deduplicated.
- A structured AI comparison brief is generated.
- AI source IDs are valid.
- Data sources and timestamps are visible.
- Repeated requests use cached results where appropriate.
- Redis failure does not break core reads.
- PostgreSQL and Redis run through Docker Compose.
- Flyway creates the schema.
- Backend and frontend tests pass.
- GitHub Actions verifies pull requests.
- Swagger UI documents the API.
- The application can be started from documented instructions.
- The deployed or local UI closely matches the approved final mockup.

---

## 31. Future Enhancements

After the MVP:

- Single-company detail page
- Functional watchlists
- Saved comparisons
- User authentication
- Scheduled refresh
- Earnings-calendar integration
- Historical financial metric charts
- SEC filing or earnings transcript ingestion
- Inline AI citations per sentence
- Export comparison to PDF
- Shareable comparison links
- Additional model providers
- AI response evaluation
- Rate limiting
- Background refresh jobs
- Cloud monitoring
- pgvector-based document retrieval

---

## 32. Final Design Principle

StockLens AI should remain a small but complete system.

The goal is not to maximize the number of technologies. Each selected technology must solve a clear engineering problem:

- React and TypeScript provide a maintainable interactive dashboard.
- Spring Boot provides the application and API layer.
- PostgreSQL provides durable relational storage and historical snapshots.
- Redis avoids repeated external calls and AI generation.
- Spring AI provides typed model integration.
- Flyway makes schema changes reproducible.
- Testcontainers verifies behavior against real infrastructure.
- Docker Compose makes local setup repeatable.
- GitHub Actions keeps the project buildable.
- Codex accelerates implementation while tests and human review preserve correctness.

The final product should communicate its purpose within 30 seconds:

> StockLens AI compares two companies, aggregates financial data and news, and generates a source-grounded AI research brief.
