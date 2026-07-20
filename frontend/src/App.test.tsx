import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App.tsx'
import { comparisonFixture } from './test/comparisonFixture.ts'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

beforeEach(() => {
  window.history.replaceState({}, '', '/')
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('App', () => {
  it('shows an initial skeleton and then renders the complete default dashboard', async () => {
    let resolveRequest: ((response: Response) => void) | undefined
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise<Response>((resolve) => {
      resolveRequest = resolve
    })))

    render(<App />)
    expect(screen.getByRole('heading', { level: 1, name: /compare stocks/i })).toBeInTheDocument()
    expect(await screen.findByRole('status', { name: /loading comparison/i })).toBeInTheDocument()

    resolveRequest?.(jsonResponse(comparisonFixture()))
    expect(await screen.findByRole('heading', { name: /price performance/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /key financial metrics/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /recent developments/i })).toBeInTheDocument()
    expect(window.location.search).toBe('?left=AAPL&right=MSFT&period=1Y&mode=RETURN')
  })

  it('loads valid URL state and updates controls without calling an active control', async () => {
    window.history.replaceState({}, '', '/?left=GOOGL&right=AMZN&period=6M&mode=PRICE')
    const first = comparisonFixture()
    first.left.ticker = 'GOOGL'
    first.left.companyName = 'Alphabet Inc.'
    first.right.ticker = 'AMZN'
    first.right.companyName = 'Amazon.com, Inc.'
    first.pricePerformance.period = '6M'
    first.pricePerformance.mode = 'PRICE'
    const second = structuredClone(first)
    second.pricePerformance.period = '1Y'
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(first))
      .mockResolvedValueOnce(jsonResponse(second))
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)
    expect(await screen.findByRole('heading', { level: 2, name: 'GOOGL' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '6M' })).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(screen.getByRole('button', { name: '6M' }))
    expect(fetchMock).toHaveBeenCalledTimes(1)

    await userEvent.click(screen.getByRole('button', { name: '1Y' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(window.location.search).toContain('period=1Y'))
  })

  it('normalizes a new search, preserves form editability, and updates the URL on success', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn().mockImplementation(() =>
      Promise.resolve(jsonResponse(comparisonFixture())),
    )
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByRole('heading', { level: 2, name: 'AAPL' })

    const left = screen.getByLabelText(/first company ticker/i)
    const right = screen.getByLabelText(/second company ticker/i)
    await user.clear(left)
    await user.type(left, ' googl ')
    await user.clear(right)
    await user.type(right, 'amzn')
    await user.click(screen.getByRole('button', { name: 'Compare' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(String(fetchMock.mock.calls[1]?.[0])).toContain('left=GOOGL&right=AMZN')
    await waitFor(() => expect(window.location.search).toContain('left=GOOGL&right=AMZN'))
    expect(left).not.toBeDisabled()
  })

  it('renders structured whole-page errors safely and retries', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        code: 'STOCK_NOT_FOUND',
        message: 'No company was found for ticker INVALID.',
        timestamp: '2026-07-20T00:00:00Z',
        path: '/api/v1/comparisons',
        requestId: 'request-404',
        details: [],
      }, 404))
      .mockResolvedValueOnce(jsonResponse(comparisonFixture()))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    expect(await screen.findByRole('heading', { name: /could not load this comparison/i })).toBeInTheDocument()
    expect(screen.getByText(/request-404/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /try again/i }))
    expect(await screen.findByRole('heading', { level: 2, name: 'AAPL' })).toBeInTheDocument()
  })

  it('keeps partial data visible and places typed section warnings', async () => {
    const fixture = comparisonFixture()
    fixture.warnings = [{
      section: 'NEWS',
      side: 'RIGHT',
      code: 'NEWS_PROVIDER_ERROR',
      message: 'Recent news is temporarily unavailable for MSFT.',
    }, {
      section: 'NEWS',
      side: 'RIGHT',
      code: 'NEWS_PROVIDER_ERROR',
      message: 'Recent news is temporarily unavailable for MSFT.',
    }]
    fixture.news.right = []
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(fixture)))
    render(<App />)

    expect(await screen.findByRole('heading', { level: 2, name: 'AAPL' })).toBeInTheDocument()
    expect(screen.getAllByText(/temporarily unavailable for MSFT/i)).toHaveLength(1)
    expect(screen.getByText('Right · News:')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /key financial metrics/i })).toBeInTheDocument()
  })
})
