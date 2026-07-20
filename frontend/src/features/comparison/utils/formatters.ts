import type { MetricUnit } from '../types/comparison.ts'

const DASH = '—'

function isFiniteNumber(value: number | null | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

export function formatDecimal(value: number | null | undefined, digits = 2): string {
  if (!isFiniteNumber(value)) return DASH
  return new Intl.NumberFormat('en-US', {
    maximumFractionDigits: digits,
    minimumFractionDigits: Math.min(digits, 2),
  }).format(value)
}

export function formatCurrency(
  value: number | null | undefined,
  currency = 'USD',
): string {
  if (!isFiniteNumber(value)) return DASH
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
    }).format(value)
  } catch {
    return `${formatDecimal(value)} ${currency}`
  }
}

export function formatCompactCurrency(
  value: number | null | undefined,
  currency = 'USD',
): string {
  if (!isFiniteNumber(value)) return DASH
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      notation: 'compact',
      maximumFractionDigits: 2,
    }).format(value)
  } catch {
    return `${new Intl.NumberFormat('en-US', {
      notation: 'compact',
      maximumFractionDigits: 2,
    }).format(value)} ${currency}`
  }
}

export function formatPercentagePoints(
  value: number | null | undefined,
  options: { signed?: boolean; digits?: number } = {},
): string {
  if (!isFiniteNumber(value)) return DASH
  const digits = options.digits ?? 2
  const sign = options.signed && value > 0 ? '+' : ''
  return `${sign}${new Intl.NumberFormat('en-US', {
    maximumFractionDigits: digits,
    minimumFractionDigits: 0,
  }).format(value)}%`
}

export function formatMetricValue(
  value: number | null | undefined,
  unit: MetricUnit,
  currency = 'USD',
): string {
  if (!isFiniteNumber(value)) return DASH
  if (unit === 'DECIMAL_FRACTION_PERCENT') return formatPercentagePoints(value * 100)
  if (unit === 'CURRENCY_AMOUNT') return formatCompactCurrency(value, currency)
  return formatDecimal(value)
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return DASH
  const date = new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00` : value)
  if (Number.isNaN(date.getTime())) return DASH
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(date)
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return DASH
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return DASH
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export function humanizeProvider(value: string | null | undefined): string {
  if (!value) return DASH
  return value
    .split(',')
    .map((provider) => {
      const normalized = provider.trim().toUpperCase()
      if (normalized === 'FMP') return 'Financial Modeling Prep'
      return provider
        .trim()
        .split('_')
        .map((part) =>
          part.length <= 4 ? part.toUpperCase() : `${part[0]}${part.slice(1).toLowerCase()}`,
        )
        .join(' ')
    })
    .join(', ')
}

export function safeExternalUrl(value: string | null | undefined): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null
  } catch {
    return null
  }
}
