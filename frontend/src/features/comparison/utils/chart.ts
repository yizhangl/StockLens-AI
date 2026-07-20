import type {
  ComparisonMode,
  PricePerformancePoint,
} from '../types/comparison.ts'
import { formatCurrency, formatPercentagePoints } from './formatters.ts'

export interface ChartDatum {
  date: string
  timestamp: number
  left: number
  right: number
}

export function toChartData(series: PricePerformancePoint[]): ChartDatum[] {
  return series
    .map((point) => ({
      date: point.date,
      timestamp: new Date(`${point.date}T00:00:00Z`).getTime(),
      left: point.leftValue,
      right: point.rightValue,
    }))
    .filter(
      (point) =>
        Number.isFinite(point.timestamp) &&
        Number.isFinite(point.left) &&
        Number.isFinite(point.right),
    )
}

export function formatChartValue(
  value: number,
  mode: ComparisonMode,
  currency: string | null,
): string {
  return mode === 'RETURN'
    ? formatPercentagePoints(value)
    : formatCurrency(value, currency ?? 'USD')
}
