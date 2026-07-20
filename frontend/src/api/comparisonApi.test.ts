import { afterEach, describe, expect, it, vi } from 'vitest'
import { buildComparisonPath, fetchComparison } from './comparisonApi.ts'
import type { ComparisonDashboard } from '../features/comparison/types/comparison.ts'

const query = { left: 'AAPL', right: 'MSFT', period: '1Y', mode: 'RETURN' } as const

afterEach(() => vi.unstubAllGlobals())

describe('comparison API', () => {
  it('builds the one aggregated endpoint URL', () => {
    expect(buildComparisonPath(query)).toBe(
      '/api/v1/comparisons?left=AAPL&right=MSFT&period=1Y&mode=RETURN',
    )
  })

  it('returns JSON and forwards the abort signal', async () => {
    const dashboard = { comparisonId: 'AAPL:MSFT:1Y:RETURN' } as ComparisonDashboard
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(dashboard), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const controller = new AbortController()

    await expect(fetchComparison(query, controller.signal)).resolves.toEqual(dashboard)
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/v1/comparisons?'), {
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    })
  })

  it('maps structured backend errors', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: 'STOCK_NOT_FOUND',
            message: 'No company was found.',
            timestamp: '2026-07-20T00:00:00Z',
            path: '/api/v1/comparisons',
            requestId: 'request-1',
            details: [],
          }),
          { status: 404, headers: { 'content-type': 'application/json' } },
        ),
      ),
    )

    await expect(fetchComparison(query)).rejects.toMatchObject({
      name: 'ApiError',
      code: 'STOCK_NOT_FOUND',
      status: 404,
      requestId: 'request-1',
    })
  })

  it('handles non-JSON and network failures safely', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('bad gateway', { status: 502 })))
    await expect(fetchComparison(query)).rejects.toMatchObject({ code: 'REQUEST_FAILED' })

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
    await expect(fetchComparison(query)).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
  })
})
