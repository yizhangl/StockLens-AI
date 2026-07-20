interface ErrorStateProps {
  code: string
  message: string
  requestId?: string | null
  onRetry: () => void
  compact?: boolean
}

export function ErrorState({ code, message, requestId, onRetry, compact = false }: ErrorStateProps) {
  return (
    <section className={`error-state${compact ? ' error-state--compact' : ''}`} role="alert">
      <div>
        <p className="error-state__eyebrow">{code.replaceAll('_', ' ')}</p>
        <h2>{compact ? 'Update failed' : 'We could not load this comparison'}</h2>
        <p>{message}</p>
        {requestId ? <p className="error-state__request">Request ID: {requestId}</p> : null}
      </div>
      <button className="button button--secondary" type="button" onClick={onRetry}>
        Try again
      </button>
    </section>
  )
}
