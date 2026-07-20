import type { ComparisonNewsArticle } from '../types/comparison.ts'
import { NewsArticleCard } from './NewsArticleCard.tsx'

interface RecentDevelopmentsProps {
  leftTicker: string
  rightTicker: string
  leftArticles: ComparisonNewsArticle[]
  rightArticles: ComparisonNewsArticle[]
}

function NewsPanel({ ticker, articles, side }: {
  ticker: string
  articles: ComparisonNewsArticle[]
  side: 'left' | 'right'
}) {
  return (
    <section className={`news-panel news-panel--${side}`} aria-labelledby={`${side}-news-title`}>
      <h3 id={`${side}-news-title`}><span aria-hidden="true">●</span> {ticker}</h3>
      <div className="news-panel__items">
        {articles.length > 0 ? articles.slice(0, 3).map((article) => (
          <NewsArticleCard key={`${article.id}-${article.url}`} article={article} />
        )) : <p className="empty-copy">No recent developments are available.</p>}
      </div>
    </section>
  )
}

export function RecentDevelopments(props: RecentDevelopmentsProps) {
  return (
    <div className="news-grid">
      <NewsPanel ticker={props.leftTicker} articles={props.leftArticles} side="left" />
      <NewsPanel ticker={props.rightTicker} articles={props.rightArticles} side="right" />
    </div>
  )
}
