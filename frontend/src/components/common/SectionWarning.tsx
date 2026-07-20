interface SectionWarningProps {
  label: string
  message: string
}

export function SectionWarning({ label, message }: SectionWarningProps) {
  return (
    <div className="section-warning" role="status">
      <span aria-hidden="true">!</span>
      <p><strong>{label}</strong> {message}</p>
    </div>
  )
}
