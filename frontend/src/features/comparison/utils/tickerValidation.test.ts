import { describe, expect, it } from 'vitest'
import {
  normalizeTicker,
  readComparisonQuery,
  validateTickers,
} from './tickerValidation.ts'

describe('ticker validation', () => {
  it('normalizes valid tickers', () => {
    expect(normalizeTicker(' brk.b ')).toBe('BRK.B')
    expect(validateTickers(' aapl ', ' msft ')).toEqual({
      left: 'AAPL',
      right: 'MSFT',
      errors: {},
    })
  })

  it('rejects blanks, unsupported characters, and duplicates', () => {
    expect(validateTickers('', 'MSFT').errors.left).toMatch(/first ticker/i)
    expect(validateTickers('AAPL!', 'MSFT').errors.left).toMatch(/letters/i)
    expect(validateTickers('aapl', 'AAPL').errors.right).toMatch(/different/i)
  })

  it('reads valid URL state and falls back from invalid values', () => {
    expect(
      readComparisonQuery('?left=googl&right=amzn&period=6m&mode=price'),
    ).toEqual({ left: 'GOOGL', right: 'AMZN', period: '6M', mode: 'PRICE' })

    expect(readComparisonQuery('?left=AAPL!&right=AAPL&period=2Y')).toEqual({
      left: 'AAPL',
      right: 'MSFT',
      period: '1Y',
      mode: 'RETURN',
    })
  })
})
