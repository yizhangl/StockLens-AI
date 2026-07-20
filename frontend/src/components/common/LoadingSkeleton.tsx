export function LoadingSkeleton() {
  return (
    <div className="dashboard-skeleton" aria-label="Loading comparison" role="status">
      <span className="sr-only">Loading comparison dashboard</span>
      <div className="skeleton-grid">
        <div className="skeleton-card" />
        <div className="skeleton-card" />
      </div>
      <div className="skeleton-card skeleton-card--brief" />
      <div className="skeleton-card skeleton-card--chart" />
      <div className="skeleton-grid skeleton-grid--four">
        <div className="skeleton-card skeleton-card--metric" />
        <div className="skeleton-card skeleton-card--metric" />
        <div className="skeleton-card skeleton-card--metric" />
        <div className="skeleton-card skeleton-card--metric" />
      </div>
    </div>
  )
}
