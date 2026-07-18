# StockLens AI — System Design Document

**Status:** Draft  
**Version:** 1.0  
**Date:** July 2026  
**Author:** Licy Li

## 1. Overview

StockLens AI is a backend-focused stock research platform that combines company fundamentals, valuation metrics, recent news, and AI-generated research summaries.

A user provides a stock ticker such as `AAPL` or `MSFT`. The system retrieves relevant financial and news data, normalizes and stores the results, and generates a structured research brief containing:

- Company summary
- Key financial metrics
- Recent developments
- Potential opportunities
- Potential risks
- Supporting sources

The project is designed as a portfolio-quality backend application rather than a production trading system.

The primary engineering goals are to demonstrate:

- Java and Spring Boot backend development
- REST API design
- Relational data modeling with PostgreSQL
- External API integration
- Redis caching
- Structured LLM integration
- Unit and integration testing
- Database migrations
- CI and containerized development

## 2. Problem Statement

Stock research information is often distributed across several sources.

A user may need to separately review:

- Company descriptions
- Financial ratios
- Valuation metrics
- Recent news
- Earnings developments
- Analyst commentary

This creates unnecessary context switching and makes it difficult to quickly compare companies.

StockLens AI provides a single API that aggregates this information and generates a concise, source-grounded research brief.

The platform is not intended to provide personalized investment advice or predict stock prices.

## 3. Project Goals

### 3.1 Product Goals

The MVP should allow a user to:

1.  Search for a publicly traded company using its ticker.
2.  View basic company and financial information.
3.  View recent news associated with the company.
4.  Generate a structured AI research brief.
5.  Refresh stale company or news data.
6.  Retrieve previously generated reports without repeating expensive external calls.

### 3.2 Engineering Goals

The project should demonstrate:

- Clear controller, service, repository, and client boundaries
- Reliable external API integration
- Persistent relational data storage
- Explicit cache behavior
- Structured and validated AI output
- Reproducible local development
- Automated tests and CI
- Documented technical decisions and trade-offs

### 3.3 Portfolio Goals

The repository should be easy for a recruiter or interviewer to understand.

A reviewer should be able to:

- Read the architecture in the README
- Start dependencies using Docker Compose
- Run the application locally
- Explore endpoints using Swagger UI
- Run automated tests
- Understand the design decisions
- Review a clear Git commit history

## 4. Non-Goals

The following features are intentionally excluded from the MVP:

- Real-time stock trading
- Brokerage account integration
- Portfolio management
- Personalized financial advice
- Price prediction
- Technical chart analysis
- Intraday market data
- Complex recommendation algorithms
- Multi-agent workflows
- Retrieval-augmented generation with a vector database
- Kubernetes deployment
- Microservice architecture
- Kafka or RabbitMQ
- Elasticsearch
- User authentication
- Payment processing
- Mobile application

These features may be considered later but should not block completion of the first working version.

## 5. Target Users

### Primary User

A student, retail investor, or job interviewer who wants to quickly inspect a public company.

### Example Use Case

The user requests:

    GET /api/v1/stocks/AAPL/research

The system returns:

- Company profile
- Selected valuation metrics
- Recent news
- AI-generated summary
- Opportunities
- Risks
- Source references
- Data timestamps

## 6. Core User Stories

### US-1: Retrieve Company Overview

As a user, I want to enter a stock ticker and retrieve basic company information so that I can understand what the company does.

### US-2: View Financial Metrics

As a user, I want to see selected financial and valuation metrics so that I can evaluate the company at a high level.

### US-3: View Recent News

As a user, I want to retrieve recent company news so that I can understand recent developments.

### US-4: Generate Research Brief

As a user, I want the platform to generate a structured research brief so that I can quickly identify important opportunities and risks.

### US-5: Reuse Existing Results

As a user, I want repeated requests to return quickly so that I do not need to wait for the same data to be fetched and generated again.

### US-6: Refresh Stale Data

As a user, I want to explicitly refresh stock data when the stored information is outdated.

## 7. Functional Requirements

### FR-1: Ticker Validation

The system must:

- Normalize tickers to uppercase
- Remove leading and trailing whitespace
- Reject blank tickers
- Reject unsupported ticker formats
- Return a clear error for unknown companies

Example:

    " aapl " → "AAPL"

### FR-2: Company Profile Retrieval

The system must retrieve and store:

- Ticker
- Company name
- Exchange
- Sector
- Industry
- Country
- Company description
- Market capitalization
- External provider identifier
- Last updated timestamp

### FR-3: Financial Metrics Retrieval

The MVP should support a limited set of metrics:

- Current stock price
- Market capitalization
- Price-to-earnings ratio
- Earnings per share
- Revenue
- Revenue growth
- Profit margin
- Debt-to-equity ratio

The exact set may be adjusted based on data-provider availability.

### FR-4: News Retrieval

The system must retrieve recent company-related news.

Each article should contain:

- External article identifier or canonical URL hash
- Headline
- Source name
- URL
- Published timestamp
- Short description or snippet
- Associated company
- Retrieval timestamp

The system must avoid storing duplicate articles.

### FR-5: Research Brief Generation

The system must generate a structured response containing:

    {
      "summary": "string",
      "recentDevelopments": [
        "string"
      ],
      "opportunities": [
        "string"
      ],
      "risks": [
        "string"
      ],
      "sourceIds": [
        1,
        2
      ]
    }

The generated brief must be based only on the company data, financial metrics, and news provided to the model.

### FR-6: Source Grounding

The model prompt must assign a local source identifier to every news article.

Example:

    [Source 1] Apple announces...
    [Source 2] Apple reports...

The model must return source identifiers rather than inventing URLs.

The application must validate that every returned source identifier exists in the provided context.

### FR-7: Persistence

The system must persist:

- Companies
- Financial metric snapshots
- News articles
- Generated research briefs
- Source relationships
- Data retrieval timestamps

### FR-8: Caching

The system should cache selected responses in Redis.

Initial cache targets:

- Company overview
- Recent news response
- Generated research brief

Suggested initial TTL values:

| Cached Data       | Initial TTL |
|-------------------|-------------|
| Company profile   | 24 hours    |
| Financial metrics | 6 hours     |
| Recent news       | 30 minutes  |
| Research brief    | 1 hour      |

TTL values must be configurable.

### FR-9: Manual Refresh

The system should provide a refresh operation that:

1.  Invalidates relevant cache entries.
2.  Retrieves the latest available provider data.
3.  Updates PostgreSQL.
4.  Optionally generates a new research brief.

### FR-10: API Documentation

All public endpoints must be visible through OpenAPI documentation and Swagger UI.

## 8. Non-Functional Requirements

### 8.1 Reliability

- External API failures must not corrupt stored data.
- Database writes for one refresh operation should be transactional where appropriate.
- Redis failure should not make core database-backed reads unavailable.
- Invalid model output must produce a controlled application error or fallback response.

### 8.2 Performance

For cached requests, the target response time is:

    Under 500 ms in the local development environment

Requests involving external APIs or OpenAI may take longer.

The MVP does not require strict production latency guarantees.

### 8.3 Maintainability

The codebase should:

- Use clear package boundaries
- Avoid unnecessary abstractions
- Keep external providers behind interfaces
- Separate domain logic from HTTP concerns
- Use configuration properties rather than hardcoded values

### 8.4 Security

- API keys must be supplied through environment variables.
- Secrets must not be committed to Git.
- Logs must not include API keys.
- External URLs must be treated as untrusted input.
- User-provided ticker values must be validated.
- Model responses must not be trusted without validation.

### 8.5 Observability

The MVP should include:

- Structured application logs
- Request and error logging
- External API failure logging
- Cache hit or miss logging at debug level
- Health endpoint through Spring Boot Actuator

Metrics dashboards are outside the initial scope.

## 9. High-Level Architecture

    Client / Swagger UI
            |
            v
    Stock Controller
            |
            v
    Stock Research Service
       |         |         |
       |         |         |
       v         v         v
    PostgreSQL  Redis   External Providers
                           |
                 ---------------------
                 |                   |
                 v                   v
          Financial Data API     News API

    Stock Research Service
            |
            v
    AI Brief Service
            |
            v
    Spring AI / OpenAI API

### Main Request Flow

    1. Client submits ticker
    2. Controller validates request
    3. Service checks Redis
    4. Service checks PostgreSQL
    5. Stale or missing data is retrieved from external providers
    6. Data is normalized and persisted
    7. Relevant context is selected
    8. AI brief is generated
    9. Model output is validated
    10. Result is persisted
    11. Response is cached
    12. API response is returned

## 10. Technology Stack

| Area                  | Technology             |
|-----------------------|------------------------|
| Language              | Java 21                |
| Backend framework     | Spring Boot            |
| AI integration        | Spring AI with OpenAI  |
| Persistence           | Spring Data JPA        |
| Database              | PostgreSQL             |
| Cache                 | Redis                  |
| Database migration    | Flyway                 |
| API documentation     | OpenAPI and Swagger UI |
| Unit testing          | JUnit 5 and Mockito    |
| Integration testing   | Testcontainers         |
| Local infrastructure  | Docker Compose         |
| Build tool            | Maven                  |
| CI                    | GitHub Actions         |
| Deployment            | Render or Railway      |
| Development assistant | Codex                  |

## 11. Architecture Decisions

### 11.1 Modular Monolith

The system will be implemented as a modular monolith.

Reasons:

- The project is maintained by one developer.
- The expected traffic is low.
- The domain is not large enough to justify distributed services.
- A monolith is easier to test, deploy, and debug.
- Internal boundaries can still be represented through packages and interfaces.

A separate Python AI service will not be created because the project does not use local Python models or a Python-specific data pipeline.

### 11.2 PostgreSQL as Source of Truth

PostgreSQL will store durable application data.

Redis will only store disposable cached results.

If Redis data is lost, the system should still be able to reconstruct responses using PostgreSQL and external providers.

### 11.3 Provider Abstraction

External financial and news providers must be accessed through interfaces.

Example:

    public interface FinancialDataClient {
        CompanyProfileData getCompanyProfile(String ticker);
        FinancialMetricsData getFinancialMetrics(String ticker);
    }

This avoids coupling business services directly to a specific provider response format.

It also allows tests to use fake implementations.

### 11.4 Structured AI Output

The application will not treat model output as arbitrary text.

Spring AI will map the response to a typed Java object.

Example:

    public record ResearchBriefResult(
        String summary,
        List<String> recentDevelopments,
        List<String> opportunities,
        List<String> risks,
        List<Long> sourceIds
    ) {}

The application must validate:

- Required fields are present
- Lists do not exceed configured limits
- Source IDs exist
- Text fields are not blank

### 11.5 Cache-Aside Pattern

The application will use a cache-aside strategy.

    Read:
    1. Check Redis
    2. On miss, load or generate result
    3. Store result in Redis
    4. Return result

PostgreSQL remains the persistent source of truth.

The application should still function when Redis is unavailable, although responses may be slower.

## 12. Proposed Package Structure

    com.stocklens
    ├── common
    │   ├── config
    │   ├── exception
    │   ├── response
    │   └── validation
    │
    ├── company
    │   ├── controller
    │   ├── service
    │   ├── repository
    │   ├── domain
    │   └── dto
    │
    ├── financial
    │   ├── client
    │   ├── service
    │   ├── domain
    │   └── dto
    │
    ├── news
    │   ├── client
    │   ├── service
    │   ├── repository
    │   ├── domain
    │   └── dto
    │
    ├── research
    │   ├── controller
    │   ├── service
    │   ├── ai
    │   ├── repository
    │   ├── domain
    │   └── dto
    │
    └── StockLensApplication.java

The project should avoid creating interfaces for every class.

Interfaces should be introduced where they provide a real boundary, especially:

- External providers
- AI model client
- Cache abstraction, if required
- Components with multiple implementations

## 13. Data Model

### 13.1 Company

    Company
    -------
    id
    ticker
    name
    exchange
    sector
    industry
    country
    description
    provider_symbol
    created_at
    updated_at

Constraints:

- `ticker` must be unique.
- `ticker` must be stored in uppercase.
- `ticker` must not be null.

### 13.2 Financial Metric Snapshot

    FinancialMetricSnapshot
    -----------------------
    id
    company_id
    stock_price
    market_cap
    pe_ratio
    eps
    revenue
    revenue_growth
    profit_margin
    debt_to_equity
    currency
    reported_at
    retrieved_at
    raw_data_json

A snapshot model is used rather than updating one row forever.

This allows the system to preserve historical values and makes future comparison possible.

### 13.3 News Article

    NewsArticle
    -----------
    id
    company_id
    external_id
    headline
    source_name
    article_url
    description
    published_at
    retrieved_at
    url_hash

Constraints:

- `url_hash` should be unique.
- Duplicate provider results should not create duplicate records.

### 13.4 Research Brief

    ResearchBrief
    -------------
    id
    company_id
    summary
    recent_developments_json
    opportunities_json
    risks_json
    model_name
    prompt_version
    generated_at
    data_cutoff_at

### 13.5 Research Brief Source

    ResearchBriefSource
    -------------------
    research_brief_id
    news_article_id

This creates a many-to-many relationship between generated briefs and source articles.

## 14. API Design

Base path:

    /api/v1

### 14.1 Retrieve Company Overview

    GET /api/v1/stocks/{ticker}

Example response:

    {
      "ticker": "AAPL",
      "companyName": "Apple Inc.",
      "exchange": "NASDAQ",
      "sector": "Technology",
      "industry": "Consumer Electronics",
      "description": "Apple designs and sells...",
      "lastUpdatedAt": "2026-07-17T18:30:00Z"
    }

### 14.2 Retrieve Financial Metrics

    GET /api/v1/stocks/{ticker}/metrics

Example response:

    {
      "ticker": "AAPL",
      "stockPrice": 210.15,
      "marketCap": 3200000000000,
      "peRatio": 32.4,
      "eps": 6.49,
      "currency": "USD",
      "reportedAt": "2026-07-17T00:00:00Z",
      "retrievedAt": "2026-07-17T18:30:00Z"
    }

### 14.3 Retrieve Recent News

    GET /api/v1/stocks/{ticker}/news?limit=10

Constraints:

- Default limit: 10
- Maximum limit: 20

### 14.4 Generate or Retrieve Research Brief

    POST /api/v1/stocks/{ticker}/research

Optional request:

    {
      "forceRefresh": false
    }

Example response:

    {
      "ticker": "AAPL",
      "summary": "Apple remains a highly profitable consumer technology company...",
      "recentDevelopments": [
        "The company reported..."
      ],
      "opportunities": [
        "Services revenue continues to expand..."
      ],
      "risks": [
        "The company remains exposed to..."
      ],
      "sources": [
        {
          "id": 12,
          "headline": "Apple reports quarterly results",
          "sourceName": "Example News",
          "url": "https://example.com/article"
        }
      ],
      "generatedAt": "2026-07-17T18:35:00Z",
      "cached": false
    }

### 14.5 Refresh Stock Data

    POST /api/v1/stocks/{ticker}/refresh

This endpoint refreshes company, financial, and news data.

AI brief generation should remain separate so that refreshing raw data does not automatically create an OpenAI cost.

## 15. Error Handling

The API should return a consistent error structure.

    {
      "code": "STOCK_NOT_FOUND",
      "message": "No company was found for ticker INVALID.",
      "timestamp": "2026-07-17T18:40:00Z",
      "path": "/api/v1/stocks/INVALID"
    }

Initial error codes:

| Code                     | HTTP Status |
|--------------------------|-------------|
| INVALID_TICKER           | 400         |
| STOCK_NOT_FOUND          | 404         |
| FINANCIAL_PROVIDER_ERROR | 502         |
| NEWS_PROVIDER_ERROR      | 502         |
| AI_PROVIDER_ERROR        | 502         |
| INVALID_AI_RESPONSE      | 502         |
| RATE_LIMITED             | 429         |
| INTERNAL_ERROR           | 500         |

Raw external provider errors should not be returned directly to the client.

## 16. External API Integration

The MVP may use one financial data provider and one news provider.

The final provider should be selected based on:

- Free-tier availability
- Request limits
- Availability of company fundamentals
- Availability of valuation metrics
- News coverage
- Java integration simplicity
- Terms of use

Provider-specific DTOs must not be used directly as domain entities.

The transformation flow should be:

    Provider Response
          |
          v
    Provider DTO
          |
          v
    Normalization Mapper
          |
          v
    Domain Entity / Application DTO

Required external-client behavior:

- Configurable connection timeout
- Configurable read timeout
- Controlled retries for transient failures
- No retry for invalid ticker responses
- Rate-limit error handling
- Sanitized logging

The MVP should avoid adding a general resilience framework until basic integration is working.

## 17. AI Generation Design

### 17.1 Input Context

The model should receive:

- Company name and description
- Selected financial metrics
- Metric timestamps
- Up to a configured number of recent news articles
- Clear source identifiers
- Explicit output instructions

### 17.2 Prompt Rules

The prompt must instruct the model to:

- Use only the supplied information
- Avoid personalized investment advice
- Avoid price predictions
- Distinguish facts from interpretation
- Return structured output
- Reference only valid source IDs
- State when available evidence is limited
- Avoid presenting missing information as fact

### 17.3 Prompt Versioning

Every saved brief should record a prompt version.

Example:

    stock-research-v1

When the prompt changes materially, the version should be updated.

This makes generated results more reproducible and easier to discuss in interviews.

### 17.4 Output Limits

Initial limits:

- Summary: maximum 250 words
- Recent developments: maximum 5
- Opportunities: maximum 5
- Risks: maximum 5
- Source IDs: maximum 10

### 17.5 Validation

The AI service must reject or repair responses when:

- Required fields are missing
- Source IDs are invalid
- Output cannot be parsed
- Lists exceed limits
- The response is empty

The first version should use one controlled retry with a correction prompt.

If the second response is invalid, the API should return `INVALID_AI_RESPONSE`.

## 18. Caching Design

### 18.1 Cache Keys

Suggested format:

    stocklens:company:{ticker}
    stocklens:metrics:{ticker}
    stocklens:news:{ticker}:{limit}
    stocklens:research:{ticker}:{dataVersion}

The key format must be centralized rather than constructed in multiple services.

### 18.2 Cache Invalidation

A stock refresh should invalidate:

- Company cache
- Metrics cache
- News cache
- Research brief cache

A new research brief should replace the current research cache.

### 18.3 Redis Failure Behavior

Redis is an optimization, not a required source of truth.

On Redis read failure:

- Log the error
- Continue with PostgreSQL or provider retrieval

On Redis write failure:

- Log the error
- Return the successfully generated response

## 19. Database Migration Strategy

Flyway migrations will be stored under:

    src/main/resources/db/migration

Initial migrations:

    V1__create_company_table.sql
    V2__create_financial_metric_snapshot_table.sql
    V3__create_news_article_table.sql
    V4__create_research_brief_tables.sql
    V5__add_indexes_and_constraints.sql

Hibernate schema generation should use validation rather than automatic mutation in normal development.

Recommended setting:

    spring.jpa.hibernate.ddl-auto=validate

Flyway is responsible for creating and changing the schema.

## 20. Testing Strategy

### 20.1 Unit Tests

JUnit and Mockito should cover:

- Ticker normalization
- Ticker validation
- Financial DTO normalization
- News deduplication
- Cache fallback behavior
- Research context construction
- AI source ID validation
- Exception mapping

### 20.2 Repository Integration Tests

Testcontainers with PostgreSQL should verify:

- Flyway migrations execute successfully
- JPA entities map correctly
- Unique ticker constraint
- Unique article hash constraint
- Snapshot queries return the latest record
- Research-source relationships persist correctly

### 20.3 Redis Integration Tests

Testcontainers with Redis should verify:

- Cache serialization
- TTL behavior
- Cache invalidation
- Fallback behavior when values are missing

### 20.4 Controller Tests

Controller tests should verify:

- HTTP status codes
- Request validation
- Response structure
- Error response structure

### 20.5 External Provider Tests

Automated CI tests should not call real paid APIs.

Provider clients should be tested with:

- Mock HTTP responses
- Recorded sample payloads where permitted
- Invalid ticker responses
- Timeout behavior
- Rate-limit responses
- Malformed provider responses

### 20.6 AI Tests

CI should not depend on live OpenAI calls.

Tests should use a fake AI client returning:

- Valid structured output
- Invalid JSON
- Invalid source IDs
- Empty output
- Provider error

A live OpenAI smoke test may be run manually and excluded from the default CI pipeline.

## 21. Local Development

Docker Compose should initially start:

- PostgreSQL
- Redis

The Spring Boot application may run directly from the developer machine.

Example:

    docker compose up -d
    ./mvnw spring-boot:run

Required environment variables:

    OPENAI_API_KEY
    FINANCIAL_API_KEY
    NEWS_API_KEY
    POSTGRES_DB
    POSTGRES_USER
    POSTGRES_PASSWORD

A `.env.example` file should document required values without containing real secrets.

## 22. CI Pipeline

GitHub Actions should run on:

- Pull requests
- Pushes to the main branch

Initial pipeline:

    1. Check out repository
    2. Set up Java 21
    3. Cache Maven dependencies
    4. Run formatting or static checks
    5. Run mvn verify
    6. Run unit and integration tests
    7. Build application artifact

Deployment should not be added until the local application and automated tests are stable.

## 23. Deployment

The initial deployment should prioritize simplicity.

Preferred options:

- Railway
- Render

Possible deployed components:

    Spring Boot web service
    PostgreSQL managed database
    Redis managed service

AWS deployment is not part of the MVP because it would add infrastructure complexity without improving the core product.

The deployment must use environment variables for secrets and provider keys.

## 24. Development Workflow with Codex

Codex will be used as an implementation assistant, not as the owner of architectural decisions.

Each development task should be bounded.

Good task example:

    Implement the Company entity, repository, Flyway migration, and repository integration tests according to Sections 13 and 19 of the design document. Do not implement external API calls or controllers.

Poor task example:

    Build the entire StockLens AI application.

For every Codex-generated change:

1.  Review the diff.
2.  Confirm that it follows the design document.
3.  Run relevant tests.
4.  Remove unnecessary abstractions.
5.  Check error handling.
6.  Check for hardcoded secrets.
7.  Commit the feature independently.

The design document should be included in the repository as:

    docs/design.md

## 25. MVP Milestones

### Milestone 1: Project Foundation

Deliverables:

- Spring Boot project
- Maven configuration
- Docker Compose
- PostgreSQL connection
- Redis connection
- Flyway configuration
- Basic health endpoint
- GitHub repository structure

### Milestone 2: Company Persistence

Deliverables:

- Company entity
- Company repository
- Company service
- Ticker validation
- Company API
- Flyway migration
- Unit and integration tests

### Milestone 3: Financial Data Integration

Deliverables:

- Financial provider interface
- Provider implementation
- Metric normalization
- Financial snapshot persistence
- Metrics API
- Provider error handling
- Tests using mock provider responses

### Milestone 4: News Integration

Deliverables:

- News provider interface
- News provider implementation
- Article persistence
- Duplicate prevention
- Recent news API
- News integration tests

### Milestone 5: AI Research Brief

Deliverables:

- Research context builder
- Spring AI integration
- Structured response model
- Prompt template
- Source grounding
- Response validation
- Research brief persistence
- Mocked AI tests

### Milestone 6: Caching

Deliverables:

- Redis cache configuration
- Cache-aside behavior
- TTL configuration
- Cache invalidation
- Redis integration tests

### Milestone 7: API Documentation and CI

Deliverables:

- OpenAPI documentation
- Swagger UI
- GitHub Actions workflow
- Full automated test execution
- README setup instructions

### Milestone 8: Deployment and Demo

Deliverables:

- Deployed backend
- Managed PostgreSQL
- Managed Redis
- Public Swagger UI or API demo
- Architecture diagram
- Final README
- Resume-ready project bullets

## 26. Main Risks

### Risk 1: External API Limitations

Free financial APIs may have strict request limits or incomplete metrics.

Mitigation:

- Hide the provider behind an interface.
- Cache provider responses.
- Keep the initial metric set small.
- Store sample test payloads.

### Risk 2: AI Hallucination

The model may generate claims not supported by supplied data.

Mitigation:

- Limit input context.
- Use source identifiers.
- Require structured output.
- Validate returned sources.
- Clearly label the result as an automated research summary.
- Avoid investment recommendations.

### Risk 3: Scope Expansion

Adding RAG, agents, authentication, dashboards, and cloud infrastructure may delay completion.

Mitigation:

- Treat the non-goals section as binding for MVP development.
- Complete one vertical slice before adding optional features.
- Do not add a new tool without a concrete problem.

### Risk 4: Excessive Configuration Work

Redis, Docker, Testcontainers, Flyway, and deployment may consume more time than product development.

Mitigation:

- Add infrastructure incrementally.
- Complete PostgreSQL-backed functionality before Redis.
- Add deployment only after CI is passing.

### Risk 5: Unstable Generated Output

AI responses may vary between calls.

Mitigation:

- Use structured output.
- Use a low model temperature.
- Persist generated reports.
- Record model and prompt versions.
- Avoid exact-text assertions in tests.

## 27. Success Criteria

The MVP is complete when:

- A user can submit a valid ticker.
- The system can retrieve and persist company data.
- The system can retrieve and persist selected financial metrics.
- The system can retrieve and deduplicate recent news.
- The system can generate a structured research brief.
- The brief references valid stored sources.
- Repeated requests can use cached results.
- PostgreSQL and Redis run through Docker Compose.
- Flyway creates the database schema.
- Unit and integration tests pass.
- GitHub Actions verifies every pull request.
- Swagger UI documents the API.
- The application is deployed or can be started using documented steps.

## 28. Future Enhancements

Possible post-MVP improvements:

- Compare two or more stocks
- Scheduled data refresh
- Historical metric charts
- Earnings report ingestion
- User watchlists
- Authentication
- Saved research collections
- Additional model providers
- Model response evaluation
- pgvector-based document retrieval
- Frontend dashboard
- Cloud monitoring
- Rate limiting
- Async background jobs

These enhancements should only begin after the MVP success criteria are met.

## 29. Final Design Principle

StockLens AI should remain a small but complete system.

The goal is not to use the largest number of technologies.

The goal is to demonstrate that each selected technology solves a clear engineering problem:

- PostgreSQL provides durable relational storage.
- Redis avoids repeated expensive work.
- Spring AI provides typed model integration.
- Flyway makes schema changes reproducible.
- Testcontainers verifies compatibility with real infrastructure.
- Docker Compose makes local development repeatable.
- GitHub Actions ensures the project remains buildable.
- Codex accelerates implementation while tests and human review maintain correctness.
