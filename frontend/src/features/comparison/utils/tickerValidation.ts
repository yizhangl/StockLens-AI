import type {
  ComparisonMode,
  ComparisonPeriod,
  ComparisonQuery,
} from '../types/comparison.ts'
import {
  COMPARISON_MODES,
  COMPARISON_PERIODS,
} from '../types/comparison.ts'

export const TICKER_PATTERN = /^[A-Z][A-Z0-9.-]{0,9}$/

export interface TickerErrors {
  left?: string
  right?: string
}

export function normalizeTicker(value: string): string {
  return value.trim().toUpperCase()
}

export function validateTickers(leftValue: string, rightValue: string): {
  left: string
  right: string
  errors: TickerErrors
} {
  const left = normalizeTicker(leftValue)
  const right = normalizeTicker(rightValue)
  const errors: TickerErrors = {}

  if (!left) errors.left = 'Enter the first ticker.'
  else if (!TICKER_PATTERN.test(left)) errors.left = 'Use 1–10 letters, numbers, dots, or hyphens.'

  if (!right) errors.right = 'Enter the second ticker.'
  else if (!TICKER_PATTERN.test(right)) errors.right = 'Use 1–10 letters, numbers, dots, or hyphens.'

  if (!errors.left && !errors.right && left === right) {
    errors.right = 'Choose two different tickers.'
  }

  return { left, right, errors }
}

export function isComparisonPeriod(value: string | null): value is ComparisonPeriod {
  return COMPARISON_PERIODS.some((period) => period === value)
}

export function isComparisonMode(value: string | null): value is ComparisonMode {
  return COMPARISON_MODES.some((mode) => mode === value)
}

export function readComparisonQuery(search: string): ComparisonQuery {
  const parameters = new URLSearchParams(search)
  const leftCandidate = normalizeTicker(parameters.get('left') ?? '')
  const rightCandidate = normalizeTicker(parameters.get('right') ?? '')
  const periodCandidate = parameters.get('period')?.toUpperCase() ?? null
  const modeCandidate = parameters.get('mode')?.toUpperCase() ?? null
  const tickerResult = validateTickers(leftCandidate, rightCandidate)

  const left = tickerResult.errors.left ? 'AAPL' : tickerResult.left || 'AAPL'
  let right = tickerResult.errors.right ? 'MSFT' : tickerResult.right || 'MSFT'
  if (left === right) right = left === 'MSFT' ? 'AAPL' : 'MSFT'

  return {
    left,
    right,
    period: isComparisonPeriod(periodCandidate) ? periodCandidate : '1Y',
    mode: isComparisonMode(modeCandidate) ? modeCandidate : 'RETURN',
  }
}
