import { getJson, postJson } from './client.ts'
import type {
  ComparisonDashboard,
  ComparisonQuery,
} from '../features/comparison/types/comparison.ts'

export function buildComparisonPath(query: ComparisonQuery): string {
  const parameters = new URLSearchParams({
    left: query.left,
    right: query.right,
    period: query.period,
    mode: query.mode,
  })
  return `/api/v1/comparisons?${parameters.toString()}`
}

export function fetchComparison(
  query: ComparisonQuery,
  signal?: AbortSignal,
): Promise<ComparisonDashboard> {
  return getJson<ComparisonDashboard>(buildComparisonPath(query), { signal })
}

export function generateComparisonBrief(
  request: { leftTicker: string; rightTicker: string },
  signal?: AbortSignal,
): Promise<import('../features/comparison/types/comparison.ts').ComparisonResearchBrief> {
  return postJson('/api/v1/comparisons/research', request, { signal })
}
