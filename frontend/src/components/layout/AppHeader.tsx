import { formatDateTime } from '../../features/comparison/utils/formatters.ts'

interface AppHeaderProps {
  lastUpdatedAt?: string | null
}

export function AppHeader({ lastUpdatedAt }: AppHeaderProps) {
  return (
    <header className="app-header">
      <div className="app-header__inner">
        <a className="brand" href="/" aria-label="StockLens AI comparison home">
          <span className="brand__mark" aria-hidden="true">↗</span>
          <span>StockLens <strong>AI</strong></span>
        </a>
        <nav aria-label="Primary navigation">
          <a className="nav-link nav-link--active" href="/">Compare</a>
          <span className="nav-link nav-link--disabled" aria-disabled="true">Watchlist</span>
        </nav>
        <p className="app-header__freshness">
          <span aria-hidden="true">◷</span>
          {lastUpdatedAt ? `Updated ${formatDateTime(lastUpdatedAt)}` : 'Comparison research'}
        </p>
      </div>
    </header>
  )
}
