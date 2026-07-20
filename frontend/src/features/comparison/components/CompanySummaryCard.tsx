import { useState } from 'react'
import type { CompanySummary } from '../types/comparison.ts'
import {
  formatCompactCurrency,
  formatCurrency,
  formatDecimal,
  formatDateTime,
  formatPercentagePoints,
  safeExternalUrl,
} from '../utils/formatters.ts'

interface CompanySummaryCardProps {
  company: CompanySummary
  side: 'left' | 'right'
}

function CompanyLogo({ company }: { company: CompanySummary }) {
  const source = safeExternalUrl(company.logoUrl)
  const [failedSource, setFailedSource] = useState<string | null>(null)

  if (!source || failedSource === source) {
    return <span className="company-logo company-logo--fallback" aria-hidden="true">{company.ticker.slice(0, 2)}</span>
  }

  return (
    <img
      className="company-logo"
      src={source}
      alt=""
      onError={() => setFailedSource(source)}
    />
  )
}

export function CompanySummaryCard({ company, side }: CompanySummaryCardProps) {
  const currency = company.currency ?? 'USD'
  const change = company.priceChange
  const changeClass = change === null ? 'neutral' : change > 0 ? 'positive' : change < 0 ? 'negative' : 'neutral'
  const website = safeExternalUrl(company.website)

  return (
    <article className={`company-card company-card--${side}`} aria-labelledby={`${side}-company-title`}>
      <div className="company-card__heading">
        <CompanyLogo company={company} />
        <div>
          <h2 id={`${side}-company-title`}>{company.ticker}</h2>
          <p>{company.companyName}</p>
        </div>
        {company.sector ? <span className="pill">{company.sector}</span> : null}
      </div>

      <div className="company-card__quote">
        <strong>{formatCurrency(company.price, currency)}</strong>
        <span className={`market-change market-change--${changeClass}`}>
          {formatCurrency(company.priceChange, currency)} ({formatPercentagePoints(company.priceChangePercent, { signed: true })})
          <span className="market-change__label"> {changeClass === 'positive' ? 'up' : changeClass === 'negative' ? 'down' : 'unchanged'}</span>
        </span>
      </div>
      <p className="quote-context">Latest available{company.quoteTimestamp ? ` · ${formatDateTime(company.quoteTimestamp)}` : ''}</p>

      <dl className="company-stats">
        <div><dt>Market cap</dt><dd>{formatCompactCurrency(company.marketCap, currency)}</dd></div>
        <div><dt>P/E (TTM)</dt><dd>{formatDecimal(company.peTtm)}</dd></div>
        <div><dt>Revenue (TTM)</dt><dd>{formatCompactCurrency(company.revenueTtm, currency)}</dd></div>
      </dl>

      <p className="company-description" title={company.description ?? undefined}>{company.description || 'Company description is unavailable.'}</p>
      <div className="company-card__meta">
        <span>{[company.exchange, company.country].filter(Boolean).join(' · ') || 'Company profile'}</span>
        {website ? (
          <a href={website} target="_blank" rel="noopener noreferrer">
            Company website <span aria-hidden="true">↗</span>
          </a>
        ) : null}
      </div>
    </article>
  )
}
