import { AppHeader } from '../components/layout/AppHeader.tsx'
import { ErrorState } from '../components/common/ErrorState.tsx'
import { LoadingSkeleton } from '../components/common/LoadingSkeleton.tsx'
import { AiComparisonBrief } from '../features/comparison/components/AiComparisonBrief.tsx'
import { CompanySummaryCard } from '../features/comparison/components/CompanySummaryCard.tsx'
import { ComparisonWarnings } from '../features/comparison/components/ComparisonWarnings.tsx'
import { DataProvenanceFooter } from '../features/comparison/components/DataProvenanceFooter.tsx'
import { MetricCategoryCard } from '../features/comparison/components/MetricCategoryCard.tsx'
import { PricePerformanceChart } from '../features/comparison/components/PricePerformanceChart.tsx'
import { RecentDevelopments } from '../features/comparison/components/RecentDevelopments.tsx'
import { StockSearchForm } from '../features/comparison/components/StockSearchForm.tsx'
import { useComparison } from '../features/comparison/hooks/useComparison.ts'

export function ComparePage() {
  const comparison = useComparison()
  const { data } = comparison

  return (
    <div className="app-frame">
      <AppHeader lastUpdatedAt={data?.provenance.lastUpdatedAt} />
      <main className="page-shell">
        <section className="hero" aria-labelledby="page-title">
          <p className="section-eyebrow">Side-by-side equity research</p>
          <h1 id="page-title">Compare stocks</h1>
          <p>Research fundamentals, valuation, performance, and recent developments in one focused view.</p>
          <StockSearchForm
            key={`${comparison.query.left}:${comparison.query.right}`}
            query={comparison.query}
            isBusy={comparison.isInitialLoading || comparison.isRefreshing}
            onSubmit={comparison.submit}
          />
        </section>

        {comparison.isInitialLoading ? <LoadingSkeleton /> : null}

        {!data && comparison.error ? (
          <ErrorState
            code={comparison.error.code}
            message={comparison.error.message}
            requestId={comparison.error.requestId}
            onRetry={comparison.retry}
          />
        ) : null}

        {data ? (
          <div className="dashboard" aria-live="polite">
            {comparison.error ? (
              <ErrorState
                compact
                code={comparison.error.code}
                message={comparison.error.message}
                requestId={comparison.error.requestId}
                onRetry={comparison.retry}
              />
            ) : null}

            <section className="company-grid" aria-label="Company summaries">
              <CompanySummaryCard company={data.left} side="left" />
              <CompanySummaryCard company={data.right} side="right" />
            </section>
            <ComparisonWarnings warnings={data.warnings} section="MARKET" />

            <AiComparisonBrief key={`${data.left.ticker}:${data.right.ticker}`} leftTicker={data.left.ticker} rightTicker={data.right.ticker} />

            <ComparisonWarnings warnings={data.warnings} section="HISTORY" />
            <PricePerformanceChart
              performance={data.pricePerformance}
              leftTicker={data.left.ticker}
              rightTicker={data.right.ticker}
              selectedPeriod={comparison.query.period}
              selectedMode={comparison.query.mode}
              isBusy={comparison.isRefreshing}
              onPeriodChange={comparison.selectPeriod}
              onModeChange={comparison.selectMode}
            />

            <section className="panel metrics-panel" aria-labelledby="financial-metrics-title">
              <div className="section-heading">
                <div>
                  <p className="section-eyebrow">Fundamental comparison</p>
                  <h2 id="financial-metrics-title">Key financial metrics</h2>
                </div>
                <span className="section-note">Backend-defined comparison rules</span>
              </div>
              <ComparisonWarnings warnings={data.warnings} section="METRICS" />
              {data.metricGroups.length > 0 ? (
                <div className="metrics-grid">
                  {data.metricGroups.map((group) => (
                    <MetricCategoryCard
                      key={group.category}
                      group={group}
                      leftTicker={data.left.ticker}
                      rightTicker={data.right.ticker}
                      currency={data.left.currency}
                    />
                  ))}
                </div>
              ) : <p className="empty-copy">Financial metrics are unavailable.</p>}
            </section>

            <section className="panel developments-panel" aria-labelledby="recent-developments-title">
              <div className="section-heading">
                <div>
                  <p className="section-eyebrow">Company-specific coverage</p>
                  <h2 id="recent-developments-title">Recent developments</h2>
                </div>
              </div>
              <ComparisonWarnings warnings={data.warnings} section="NEWS" />
              <RecentDevelopments
                leftTicker={data.left.ticker}
                rightTicker={data.right.ticker}
                leftArticles={data.news.left}
                rightArticles={data.news.right}
              />
            </section>
          </div>
        ) : null}
      </main>
      {data ? <DataProvenanceFooter provenance={data.provenance} /> : null}
    </div>
  )
}
