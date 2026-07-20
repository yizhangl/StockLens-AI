import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { comparisonFixture } from '../../../test/comparisonFixture.ts'
import { useComparison } from './useComparison.ts'

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  })
}

beforeEach(() => window.history.replaceState({}, '', '/'))
afterEach(() => vi.unstubAllGlobals())

describe('useComparison', () => {
  it('aborts the previous request and ignores its stale response', async () => {
    const resolvers: Array<(response: Response) => void> = []
    const fetchMock = vi.fn().mockImplementation(
      () => new Promise<Response>((resolve) => resolvers.push(resolve)),
    )
    vi.stubGlobal('fetch', fetchMock)
    const { result } = renderHook(() => useComparison())

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    act(() => result.current.selectPeriod('6M'))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))

    const firstSignal = fetchMock.mock.calls[0]?.[1]?.signal as AbortSignal
    expect(firstSignal.aborted).toBe(true)

    const newer = comparisonFixture()
    newer.pricePerformance.period = '6M'
    resolvers[1]?.(jsonResponse(newer))
    await waitFor(() => expect(result.current.data?.pricePerformance.period).toBe('6M'))

    resolvers[0]?.(jsonResponse(comparisonFixture()))
    await act(async () => Promise.resolve())
    expect(result.current.data?.pricePerformance.period).toBe('6M')
  })
})
