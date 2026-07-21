import { ErrorState } from '../../../components/common/ErrorState.tsx'
import { useAiBrief } from '../hooks/useAiBrief.ts'
import type { AiAdvantage, ComparisonResearchBrief } from '../types/comparison.ts'

const categories = [
  ['valuation', 'Valuation'],
  ['profitability', 'Profitability'],
  ['growth', 'Growth'],
  ['financialHealth', 'Financial health'],
] as const

function safeNewsUrl(value: string | null): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null
  } catch { return null }
}

function SourceReferences({ ids, brief }: { ids: string[]; brief: ComparisonResearchBrief }) {
  const byId = new Map(brief.sources.map((source) => [source.id, source]))
  return (
    <ul className="ai-sources" aria-label="Supporting sources">
      {ids.map((id) => {
        const source = byId.get(id)
        if (!source) return <li key={id}><span className="source-chip">{id}</span> Source unavailable</li>
        const url = source.type === 'NEWS_ARTICLE' ? safeNewsUrl(source.url) : null
        return (
          <li key={id}>
            <span className="source-chip">{source.id}</span>
            <span>{source.ticker} · {source.label}</span>
            {url ? (
              <a
                href={url}
                target="_blank"
                rel="noopener noreferrer"
                aria-label={`Open cited article ${source.id} for ${source.ticker} in a new tab`}
              >
                Open cited article <span aria-hidden="true">↗</span>
              </a>
            ) : null}
          </li>
        )
      })}
    </ul>
  )
}

function AdvantageCard({ title, value, brief }: { title: string; value: AiAdvantage; brief: ComparisonResearchBrief }) {
  const winner = value.winner === 'INSUFFICIENT_DATA' ? 'Insufficient data' : value.winner
  return (
    <article className="ai-advantage-card">
      <h3>{title}</h3>
      <p className="ai-winner">{winner}</p>
      <p>{value.explanation}</p>
      <SourceReferences ids={value.sourceIds} brief={brief} />
    </article>
  )
}

export function AiComparisonBrief({ leftTicker, rightTicker }: { leftTicker: string; rightTicker: string }) {
  const ai = useAiBrief(leftTicker, rightTicker)
  const { brief } = ai
  return (
    <section className="panel ai-brief" aria-labelledby="ai-brief-title" aria-busy={ai.isGenerating}>
      <div className="section-heading">
        <div>
          <p className="section-eyebrow">Source-grounded research</p>
          <h2 id="ai-brief-title">AI comparison brief</h2>
          <p className="ai-brief__note">Uses the latest persisted company data, metrics, news, and a 1Y performance summary.</p>
        </div>
        <button className="button button--primary" type="button" onClick={() => void ai.generate(Boolean(brief))} disabled={ai.isGenerating}>
          {ai.isGenerating ? 'Generating…' : brief ? 'Regenerate AI brief' : 'Generate AI brief'}
        </button>
      </div>
      {ai.isGenerating ? <p className="ai-brief__loading" role="status">Generating a grounded comparison brief…</p> : null}
      {ai.error ? <ErrorState compact code={ai.error.code} message={ai.error.message} requestId={ai.error.requestId} onRetry={() => void ai.generate()} /> : null}
      {!brief && !ai.isGenerating && !ai.error ? (
        <p className="ai-brief__empty">Generate a brief when you want a researched comparison based only on stored StockLens sources.</p>
      ) : null}
      {brief ? (
        <div className="ai-brief__content">
          <p className="ai-summary">{brief.overallSummary}</p>
          <div className="ai-advantages">
            {categories.map(([key, title]) => <AdvantageCard key={key} title={title} value={brief.advantages[key]} brief={brief} />)}
          </div>
          <section className="ai-risks" aria-labelledby="ai-risks-title">
            <h3 id="ai-risks-title">Key risks</h3>
            {brief.keyRisks.length === 0 ? <p>No key risks were identified from the available source set.</p> : (
              <ul>{brief.keyRisks.map((risk, index) => <li key={`${risk.ticker}-${index}`}><strong>{risk.ticker}</strong> — {risk.text}<SourceReferences ids={risk.sourceIds} brief={brief} /></li>)}</ul>
            )}
          </section>
          <p className="ai-brief__meta">{brief.cached ? 'Reused cached brief' : 'Newly generated brief'} · {new Date(brief.generatedAt).toLocaleString()} · {brief.modelName} · {brief.promptVersion}</p>
        </div>
      ) : null}
    </section>
  )
}
