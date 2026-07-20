export function AiBriefPlaceholder() {
  return (
    <section className="panel ai-placeholder" aria-labelledby="ai-brief-title">
      <div className="ai-placeholder__icon" aria-hidden="true">✦</div>
      <div>
        <p className="section-eyebrow">Research brief</p>
        <h2 id="ai-brief-title">AI comparison brief</h2>
        <p>AI comparison insights will be available in the next milestone.</p>
      </div>
      <span className="pill pill--muted">Coming next</span>
    </section>
  )
}
