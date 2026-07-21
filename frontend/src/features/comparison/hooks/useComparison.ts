import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchComparison, refreshComparison } from '../../../api/comparisonApi.ts'
import { ApiError } from '../../../api/client.ts'
import type {
  ComparisonDashboard,
  ComparisonMode,
  ComparisonPeriod,
  ComparisonQuery,
} from '../types/comparison.ts'
import { readComparisonQuery } from '../utils/tickerValidation.ts'

type HistoryMode = 'push' | 'replace' | 'none'

function querySearch(query: ComparisonQuery): string {
  return new URLSearchParams({
    left: query.left,
    right: query.right,
    period: query.period,
    mode: query.mode,
  }).toString()
}

function toDisplayError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  return new ApiError('An unexpected error prevented this comparison.', {
    status: 0,
    code: 'UNEXPECTED_ERROR',
  })
}

export interface ComparisonState {
  data: ComparisonDashboard | null
  query: ComparisonQuery
  error: ApiError | null
  isInitialLoading: boolean
  isRefreshing: boolean
  submit: (left: string, right: string) => void
  selectPeriod: (period: ComparisonPeriod) => void
  selectMode: (mode: ComparisonMode) => void
  retry: () => void
  refreshData: () => Promise<void>
  isManualRefreshing: boolean
}

export function useComparison(): ComparisonState {
  const [initialQuery] = useState<ComparisonQuery>(() => readComparisonQuery(window.location.search))
  const [data, setData] = useState<ComparisonDashboard | null>(null)
  const dataRef = useRef<ComparisonDashboard | null>(null)
  const [query, setQuery] = useState<ComparisonQuery>(initialQuery)
  const queryRef = useRef<ComparisonQuery>(initialQuery)
  const [error, setError] = useState<ApiError | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [isManualRefreshing, setIsManualRefreshing] = useState(false)
  const requestSequence = useRef(0)
  const abortController = useRef<AbortController | null>(null)

  const load = useCallback(async (nextQuery: ComparisonQuery, historyMode: HistoryMode) => {
    abortController.current?.abort()
    const controller = new AbortController()
    abortController.current = controller
    const requestId = ++requestSequence.current
    queryRef.current = nextQuery
    setQuery(nextQuery)
    setError(null)
    setIsLoading(true)

    try {
      const response = await fetchComparison(nextQuery, controller.signal)
      if (requestId !== requestSequence.current || controller.signal.aborted) return

      dataRef.current = response
      setData(response)
      if (historyMode !== 'none') {
        const nextUrl = `${window.location.pathname}?${querySearch(nextQuery)}${window.location.hash}`
        window.history[historyMode === 'push' ? 'pushState' : 'replaceState']({}, '', nextUrl)
      }
    } catch (caught) {
      if (controller.signal.aborted || requestId !== requestSequence.current) return
      setError(toDisplayError(caught))
    } finally {
      if (requestId === requestSequence.current) setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    const initialLoad = window.setTimeout(() => void load(initialQuery, 'replace'), 0)
    const handlePopState = () => void load(readComparisonQuery(window.location.search), 'none')
    window.addEventListener('popstate', handlePopState)
    return () => {
      window.clearTimeout(initialLoad)
      window.removeEventListener('popstate', handlePopState)
      abortController.current?.abort()
    }
  }, [initialQuery, load])

  const submit = useCallback(
    (left: string, right: string) => {
      const nextQuery = { ...queryRef.current, left, right }
      if (querySearch(nextQuery) === querySearch(queryRef.current) && dataRef.current && !error) return
      void load(nextQuery, 'push')
    },
    [error, load],
  )

  const selectPeriod = useCallback(
    (period: ComparisonPeriod) => {
      if (period === queryRef.current.period) return
      void load({ ...queryRef.current, period }, 'push')
    },
    [load],
  )

  const selectMode = useCallback(
    (mode: ComparisonMode) => {
      if (mode === queryRef.current.mode) return
      void load({ ...queryRef.current, mode }, 'push')
    },
    [load],
  )

  const retry = useCallback(() => void load(queryRef.current, 'replace'), [load])
  const refreshData = useCallback(async () => {
    if (isManualRefreshing) return
    setIsManualRefreshing(true)
    setError(null)
    try {
      await refreshComparison({ tickers: [queryRef.current.left, queryRef.current.right] })
      await load(queryRef.current, 'replace')
    } catch (caught) { setError(toDisplayError(caught)) }
    finally { setIsManualRefreshing(false) }
  }, [isManualRefreshing, load])

  return {
    data,
    query,
    error,
    isInitialLoading: isLoading && !data,
    isRefreshing: isLoading && Boolean(data),
    submit,
    selectPeriod,
    selectMode,
    retry,
    refreshData,
    isManualRefreshing,
  }
}
