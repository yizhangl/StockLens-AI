import type { ComparisonProvenance } from '../types/comparison.ts'
import { formatDate, formatDateTime, humanizeProvider } from '../utils/formatters.ts'

interface DataProvenanceFooterProps {
  provenance: ComparisonProvenance
}

export function DataProvenanceFooter({ provenance }: DataProvenanceFooterProps) {
  return (
    <footer className="data-footer" aria-label="Data sources and disclaimer">
      <dl className="data-footer__facts">
        <div><dt>Market &amp; financial data</dt><dd>{humanizeProvider(provenance.financialProvider)}</dd></div>
        <div><dt>News data</dt><dd>{humanizeProvider(provenance.newsProvider)}</dd></div>
        <div><dt>Last updated</dt><dd>{formatDateTime(provenance.lastUpdatedAt)}</dd></div>
        <div><dt>History range</dt><dd>{formatDate(provenance.historyStartDate)} – {formatDate(provenance.historyEndDate)}</dd></div>
        <div><dt>Response</dt><dd>{provenance.cached ? 'Cached' : 'Freshly assembled'}</dd></div>
      </dl>
      <p className="disclaimer">
        StockLens AI provides automated research summaries for informational and educational purposes only. It does not constitute financial advice.
      </p>
    </footer>
  )
}
