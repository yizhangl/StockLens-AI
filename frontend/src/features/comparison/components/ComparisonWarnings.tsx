import { SectionWarning } from '../../../components/common/SectionWarning.tsx'
import type {
  ComparisonWarning,
  WarningSection,
  WarningSide,
} from '../types/comparison.ts'

interface ComparisonWarningsProps {
  warnings: ComparisonWarning[]
  section?: WarningSection
  side?: WarningSide
}

export function ComparisonWarnings({ warnings, section, side }: ComparisonWarningsProps) {
  const matching = warnings.filter(
    (warning) =>
      (!section || warning.section === section) &&
      (!side || warning.side === side || warning.side === 'BOTH' || warning.side === 'GENERAL'),
  ).filter((warning, index, candidates) =>
    candidates.findIndex((candidate) =>
      candidate.section === warning.section &&
      candidate.side === warning.side &&
      candidate.code === warning.code,
    ) === index,
  )

  if (matching.length === 0) return null

  return (
    <div className="warning-list">
      {matching.map((warning) => (
        <SectionWarning
          key={`${warning.section}-${warning.side}-${warning.code}`}
          label={`${warning.side.charAt(0)}${warning.side.slice(1).toLowerCase()} · ${warning.section.charAt(0)}${warning.section.slice(1).toLowerCase()}:`}
          message={warning.message}
        />
      ))}
    </div>
  )
}
