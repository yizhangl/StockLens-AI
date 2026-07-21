export const COMPARISON_PERIODS = ['1M', '6M', '1Y', '5Y', 'MAX'] as const
export type ComparisonPeriod = (typeof COMPARISON_PERIODS)[number]

export const COMPARISON_MODES = ['PRICE', 'RETURN'] as const
export type ComparisonMode = (typeof COMPARISON_MODES)[number]

export type MetricCategory =
  | 'VALUATION'
  | 'PROFITABILITY'
  | 'GROWTH'
  | 'FINANCIAL_HEALTH'

export type MetricUnit =
  | 'RATIO'
  | 'DECIMAL_FRACTION_PERCENT'
  | 'CURRENCY_AMOUNT'

export type ComparisonStrategy =
  | 'HIGHER_IS_GENERALLY_BETTER'
  | 'LOWER_IS_GENERALLY_BETTER'
  | 'RANGE_DEPENDENT'
  | 'CONTEXT_DEPENDENT'
  | 'DESCRIPTIVE_ONLY'

export type ComparisonOutcome =
  | 'LEFT'
  | 'RIGHT'
  | 'NEUTRAL'
  | 'EQUAL'
  | 'INSUFFICIENT_DATA'

export type WarningSection = 'MARKET' | 'METRICS' | 'HISTORY' | 'NEWS'
export type WarningSide = 'LEFT' | 'RIGHT' | 'BOTH' | 'GENERAL'

export interface CompanySummary {
  ticker: string
  companyName: string
  exchange: string | null
  sector: string | null
  industry: string | null
  country: string | null
  website: string | null
  logoUrl: string | null
  description: string | null
  price: number | null
  priceChange: number | null
  priceChangePercent: number | null
  marketCap: number | null
  currency: string | null
  quoteTimestamp: string | null
  peTtm: number | null
  revenueTtm: number | null
  companyUpdatedAt: string | null
  marketRetrievedAt: string | null
  metricsRetrievedAt: string | null
}

export interface PricePerformancePoint {
  date: string
  leftValue: number
  rightValue: number
}

export interface PricePerformance {
  period: ComparisonPeriod
  mode: ComparisonMode
  startDate: string | null
  endDate: string | null
  pointCount: number
  leftReturnPercent: number | null
  rightReturnPercent: number | null
  leftCurrency: string | null
  rightCurrency: string | null
  series: PricePerformancePoint[]
}

export interface MetricComparison {
  code: string
  displayName: string
  category: MetricCategory
  unit: MetricUnit
  leftValue: number | null
  rightValue: number | null
  comparisonStrategy: ComparisonStrategy
  outcome: ComparisonOutcome
  explanation: string
}

export interface MetricGroup {
  category: MetricCategory
  metrics: MetricComparison[]
}

export interface ComparisonNewsArticle {
  id: number
  headline: string
  sourceName: string
  url: string
  publishedAt: string
  description: string | null
  relatedSymbols: string[]
}

export interface ComparisonNews {
  left: ComparisonNewsArticle[]
  right: ComparisonNewsArticle[]
}

export interface ComparisonProvenance {
  financialProvider: string | null
  newsProvider: string | null
  leftCompanyUpdatedAt: string | null
  rightCompanyUpdatedAt: string | null
  leftMarketRetrievedAt: string | null
  rightMarketRetrievedAt: string | null
  leftMetricsRetrievedAt: string | null
  rightMetricsRetrievedAt: string | null
  leftHistoryRetrievedAt: string | null
  rightHistoryRetrievedAt: string | null
  leftNewsRetrievedAt: string | null
  rightNewsRetrievedAt: string | null
  historyStartDate: string | null
  historyEndDate: string | null
  lastUpdatedAt: string | null
  cached: boolean
}

export interface ComparisonWarning {
  section: WarningSection
  side: WarningSide
  code: string
  message: string
}

export interface ComparisonDashboard {
  comparisonId: string
  left: CompanySummary
  right: CompanySummary
  pricePerformance: PricePerformance
  metricGroups: MetricGroup[]
  news: ComparisonNews
  aiBrief: unknown | null
  provenance: ComparisonProvenance
  warnings: ComparisonWarning[]
}

export type AiWinner = string | 'NEUTRAL' | 'INSUFFICIENT_DATA'
export type GroundedSourceType =
  | 'COMPANY_PROFILE'
  | 'MARKET_SNAPSHOT'
  | 'FINANCIAL_METRIC'
  | 'HISTORICAL_PERFORMANCE'
  | 'NEWS_ARTICLE'

export interface AiAdvantage {
  winner: AiWinner
  explanation: string
  sourceIds: string[]
}

export interface ComparisonResearchBrief {
  id: number
  leftTicker: string
  rightTicker: string
  overallSummary: string
  advantages: {
    valuation: AiAdvantage
    profitability: AiAdvantage
    growth: AiAdvantage
    financialHealth: AiAdvantage
  }
  keyRisks: Array<{ ticker: string; text: string; sourceIds: string[] }>
  sources: Array<{
    id: string
    type: GroundedSourceType
    ticker: string
    label: string
    sourceName: string
    url: string | null
    asOf: string | null
  }>
  modelName: string
  promptVersion: string
  generatedAt: string
  dataCutoffAt: string
  cached: false
}

export interface ComparisonQuery {
  left: string
  right: string
  period: ComparisonPeriod
  mode: ComparisonMode
}

export interface ApiErrorDetail {
  field: string
  message: string
}

export interface ApiErrorResponse {
  code: string
  message: string
  timestamp: string
  path: string
  requestId: string
  details: ApiErrorDetail[]
}
