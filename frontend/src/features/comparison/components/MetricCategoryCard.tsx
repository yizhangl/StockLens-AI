import type { MetricGroup } from '../types/comparison.ts'
import { formatMetricValue } from '../utils/formatters.ts'

interface MetricCategoryCardProps {
  group: MetricGroup
  leftTicker: string
  rightTicker: string
  currency?: string | null
}

const categoryLabels = {
  VALUATION: 'Valuation',
  PROFITABILITY: 'Profitability',
  GROWTH: 'Growth',
  FINANCIAL_HEALTH: 'Financial health',
} as const

const categoryIcons = {
  VALUATION: '◇',
  PROFITABILITY: '↗',
  GROWTH: '⌁',
  FINANCIAL_HEALTH: '⬡',
} as const

function outcomeText(outcome: string, side: 'LEFT' | 'RIGHT'): string | null {
  if (outcome === side) return 'Supported advantage'
  if (outcome === 'EQUAL') return 'Equal'
  if (outcome === 'INSUFFICIENT_DATA') return 'Insufficient data'
  return null
}

export function MetricCategoryCard({
  group,
  leftTicker,
  rightTicker,
  currency,
}: MetricCategoryCardProps) {
  return (
    <article className="metric-card">
      <div className="metric-card__heading">
        <span className="metric-card__icon" aria-hidden="true">{categoryIcons[group.category]}</span>
        <h3>{categoryLabels[group.category]}</h3>
      </div>
      <div className="metric-table" role="table" aria-label={`${categoryLabels[group.category]} comparison`}>
        <div className="metric-row metric-row--header" role="row">
          <span role="columnheader">Metric</span>
          <span role="columnheader">{leftTicker}</span>
          <span role="columnheader">{rightTicker}</span>
        </div>
        {group.metrics.map((metric) => {
          const leftStatus = outcomeText(metric.outcome, 'LEFT')
          const rightStatus = outcomeText(metric.outcome, 'RIGHT')
          return (
            <div className="metric-row" role="row" key={metric.code} title={metric.explanation}>
              <span role="rowheader">{metric.displayName}</span>
              <span className={metric.outcome === 'LEFT' ? 'metric-value metric-value--advantage' : 'metric-value'} role="cell">
                {formatMetricValue(metric.leftValue, metric.unit, currency ?? 'USD')}
                {leftStatus ? <small>{leftStatus}</small> : null}
              </span>
              <span className={metric.outcome === 'RIGHT' ? 'metric-value metric-value--advantage' : 'metric-value'} role="cell">
                {formatMetricValue(metric.rightValue, metric.unit, currency ?? 'USD')}
                {rightStatus ? <small>{rightStatus}</small> : null}
              </span>
            </div>
          )
        })}
      </div>
    </article>
  )
}
