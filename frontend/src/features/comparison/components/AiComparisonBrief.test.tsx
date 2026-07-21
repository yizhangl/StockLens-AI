import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AiComparisonBrief } from './AiComparisonBrief.tsx'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function brief() {
  const advantage = {
    winner: 'AAPL',
    explanation: 'The supplied persisted metric supports this comparison.',
    sourceIds: ['M1'],
  }
  return {
    id: 7,
    leftTicker: 'AAPL',
    rightTicker: 'MSFT',
    overallSummary: 'A grounded comparison based on persisted StockLens sources.',
    advantages: {
      valuation: advantage,
      profitability: advantage,
      growth: advantage,
      financialHealth: advantage,
    },
    keyRisks: [],
    sources: [{
      id: 'M1',
      type: 'NEWS_ARTICLE',
      ticker: 'AAPL',
      label: 'A long but valid cited source label',
      sourceName: 'Example News',
      url: 'https://example.com/article',
      asOf: '2026-07-21T00:00:00Z',
    }],
    modelName: 'configured-model',
    promptVersion: 'stock-comparison-v1',
    generatedAt: '2026-07-21T00:00:00Z',
    dataCutoffAt: '2026-07-20T23:00:00Z',
    cached: false,
  }
}

afterEach(() => vi.unstubAllGlobals())

describe('AiComparisonBrief', () => {
  it('uses descriptive safe citation labels and preserves a brief after regeneration failure', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(brief()))
      .mockResolvedValueOnce(jsonResponse({
        code: 'AI_PROVIDER_ERROR',
        message: 'AI comparison generation is temporarily unavailable.',
        timestamp: '2026-07-21T00:00:00Z',
        path: '/api/v1/comparisons/research',
        requestId: 'ai-request',
        details: [],
      }, 502))
    vi.stubGlobal('fetch', fetchMock)
    render(<AiComparisonBrief leftTicker="AAPL" rightTicker="MSFT" />)

    await user.click(screen.getByRole('button', { name: 'Generate AI brief' }))
    expect(await screen.findByText(/grounded comparison based on persisted/i)).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /open cited article M1 for AAPL in a new tab/i })[0])
      .toHaveAttribute('rel', 'noopener noreferrer')

    await user.click(screen.getByRole('button', { name: 'Regenerate AI brief' }))
    expect(await screen.findByText(/temporarily unavailable/i)).toBeInTheDocument()
    expect(screen.getByText(/grounded comparison based on persisted/i)).toBeInTheDocument()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(fetchMock.mock.calls[1]?.[1]?.body).toContain('"forceRefresh":true')
  })
})
