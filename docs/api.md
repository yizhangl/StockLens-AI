# Public API

Base application path: `/api/v1`. All examples use placeholder public tickers
and omit credentials because provider keys are backend-only environment values.

## Error Contract

Controlled failures use this shape:

```json
{
  "code": "INVALID_TICKER",
  "message": "Ticker format is invalid.",
  "timestamp": "2026-07-21T12:00:00Z",
  "path": "/api/v1/comparisons",
  "requestId": "request-id",
  "details": []
}
```

Common codes include `INVALID_TICKER`, `INVALID_PERIOD`, `INVALID_MODE`,
`INVALID_LIMIT`, `DUPLICATE_TICKERS`, `STOCK_NOT_FOUND`, `RATE_LIMITED`,
`FINANCIAL_PROVIDER_ERROR`, `NEWS_PROVIDER_ERROR`, `AI_PROVIDER_ERROR`,
`INVALID_AI_RESPONSE`, `DATA_UNAVAILABLE`, and `INTERNAL_ERROR`. Raw provider,
database, Redis, and model payloads are not returned.

## Aggregated Comparison

```http
GET /api/v1/comparisons?left=AAPL&right=MSFT&period=1Y&mode=RETURN
```

Query parameters:

| Name | Required | Values / default |
|---|---|---|
| `left` | Yes | Valid ticker |
| `right` | Yes | Different valid ticker |
| `period` | No | `1M`, `6M`, `1Y`, `5Y`, `MAX`; default `1Y` |
| `mode` | No | `PRICE`, `RETURN`; default `RETURN` |

Concise response shape:

```json
{
  "comparisonId": "AAPL:MSFT:1Y:RETURN",
  "left": { "ticker": "AAPL", "companyName": "Apple Inc.", "price": 200.00 },
  "right": { "ticker": "MSFT", "companyName": "Microsoft Corporation", "price": 500.00 },
  "pricePerformance": {
    "period": "1Y",
    "mode": "RETURN",
    "pointCount": 250,
    "leftReturnPercent": 10.2,
    "rightReturnPercent": 8.4,
    "series": [{ "date": "2026-07-20", "leftValue": 10.2, "rightValue": 8.4 }]
  },
  "metricGroups": [{ "category": "VALUATION", "metrics": [] }],
  "news": { "left": [], "right": [] },
  "aiBrief": null,
  "provenance": { "financialProvider": "FMP", "newsProvider": "YAHOO_FINANCE", "cached": false },
  "warnings": []
}
```

The endpoint uses a canonical pair cache internally but preserves requested
left/right orientation. A cache hit sets `provenance.cached=true`. Non-critical
market, metrics, history, or news failures can appear as typed warnings while
the rest of the dashboard remains available.

## AI Comparison Research

```http
POST /api/v1/comparisons/research
Content-Type: application/json
```

```json
{
  "leftTicker": "AAPL",
  "rightTicker": "MSFT",
  "forceRefresh": false
}
```

`forceRefresh` defaults to false. With false, the service may reuse a matching
Redis or fresh persisted brief. With true, both reuse layers are bypassed and a
new historical brief row is generated and persisted.

```json
{
  "id": 42,
  "leftTicker": "AAPL",
  "rightTicker": "MSFT",
  "overallSummary": "Grounded comparison summary.",
  "advantages": {
    "valuation": {
      "winner": "NEUTRAL",
      "explanation": "The supplied values require context.",
      "sourceIds": ["M1", "M2"]
    },
    "profitability": {},
    "growth": {},
    "financialHealth": {}
  },
  "keyRisks": [{ "ticker": "AAPL", "text": "Grounded risk.", "sourceIds": ["N1"] }],
  "sources": [{
    "id": "M1",
    "type": "FINANCIAL_METRIC",
    "ticker": "AAPL",
    "label": "P/E (TTM): 20",
    "sourceName": "FMP",
    "url": null,
    "asOf": "2026-07-21T12:00:00Z"
  }],
  "modelName": "configured-model",
  "promptVersion": "stock-comparison-v1",
  "generatedAt": "2026-07-21T12:00:00Z",
  "dataCutoffAt": "2026-07-21T11:55:00Z",
  "cached": false
}
```

The model never supplies public source metadata; IDs are resolved against the
backend's persisted context. AI failures use `DATA_UNAVAILABLE`, `RATE_LIMITED`,
`AI_PROVIDER_ERROR`, or `INVALID_AI_RESPONSE` as appropriate.

## Manual Refresh

```http
POST /api/v1/comparisons/refresh
Content-Type: application/json
```

```json
{
  "tickers": ["AAPL", "MSFT"],
  "regenerateBrief": false
}
```

One or two distinct tickers are accepted. The response reports normalized
tickers, whether AI regeneration was requested, and safe partial warnings:

```json
{
  "tickers": ["AAPL", "MSFT"],
  "regenerateBrief": false,
  "warnings": ["Some data could not be refreshed for MSFT."]
}
```

Successful sections are persisted even when another ticker/section produces a
warning. The frontend then reloads the dashboard. It does not request automatic
AI regeneration.

## Supporting Stock Endpoints

### Company and latest market snapshot

```http
GET /api/v1/stocks/AAPL
```

Returns `{ "company": { ... }, "latestMarketSnapshot": { ... } }` with
normalized profile and latest quote fields.

### Financial metrics

```http
GET /api/v1/stocks/AAPL/metrics
```

Returns the ticker, provider/retrieval metadata, normalized metric values, and
safe warnings. Unavailable values remain `null` rather than being fabricated.

### Historical prices

```http
GET /api/v1/stocks/AAPL/history?period=1Y
```

Periods are `1M`, `6M`, `1Y`, `5Y`, and `MAX`. The response contains a date
range, provider/retrieval metadata, and daily numeric points.

### Recent news

```http
GET /api/v1/stocks/AAPL/news?limit=3
```

`limit` must be from 1 through 20. The response contains relevant, newest-first
articles, provider metadata, retrieval time, and warnings. A successful empty
response is distinct from provider failure.

## Health

```http
GET /actuator/health
```

Only the health actuator endpoint is exposed, and component details are hidden.

