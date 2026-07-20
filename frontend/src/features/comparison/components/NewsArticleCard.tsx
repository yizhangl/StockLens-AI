import type { ComparisonNewsArticle } from '../types/comparison.ts'
import { formatDate, safeExternalUrl } from '../utils/formatters.ts'

interface NewsArticleCardProps {
  article: ComparisonNewsArticle
}

export function NewsArticleCard({ article }: NewsArticleCardProps) {
  const articleUrl = safeExternalUrl(article.url)
  return (
    <article className="news-article">
      <h4>
        {articleUrl ? (
          <a href={articleUrl} target="_blank" rel="noopener noreferrer">
            {article.headline}<span className="sr-only"> (opens in a new tab)</span>
          </a>
        ) : article.headline}
      </h4>
      <p className="news-article__meta">{article.sourceName} · {formatDate(article.publishedAt)}</p>
      {article.description ? <p className="news-article__description">{article.description}</p> : null}
    </article>
  )
}
