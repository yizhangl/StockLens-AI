import { describe, expect, it } from 'vitest'
import {
  formatCompactCurrency,
  formatMetricValue,
  formatPercentagePoints,
  humanizeProvider,
  safeExternalUrl,
} from './formatters.ts'

describe('comparison formatters', () => {
  it('does not multiply percentage-point values', () => {
    expect(formatPercentagePoints(0.71, { signed: true })).toBe('+0.71%')
    expect(formatPercentagePoints(-7.2)).toBe('-7.2%')
  })

  it('multiplies only decimal-fraction metric percentages', () => {
    expect(formatMetricValue(0.246, 'DECIMAL_FRACTION_PERCENT')).toBe('24.6%')
    expect(formatMetricValue(28.74, 'RATIO')).toBe('28.74')
    expect(formatMetricValue(null, 'RATIO')).toBe('—')
  })

  it('formats compact currency and provider names', () => {
    expect(formatCompactCurrency(2_960_000_000_000)).toMatch(/\$2\.96T/)
    expect(humanizeProvider('FMP,YAHOO_FINANCE')).toBe('Financial Modeling Prep, Yahoo Finance')
  })

  it('permits only HTTP external URLs', () => {
    expect(safeExternalUrl('https://example.com/news')).toBe('https://example.com/news')
    expect(safeExternalUrl('javascript:alert(1)')).toBeNull()
    expect(safeExternalUrl('not-a-url')).toBeNull()
  })
})
