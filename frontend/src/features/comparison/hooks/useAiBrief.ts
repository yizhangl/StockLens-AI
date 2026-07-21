import { useCallback, useEffect, useRef, useState } from 'react'
import { generateComparisonBrief } from '../../../api/comparisonApi.ts'
import { ApiError } from '../../../api/client.ts'
import type { ComparisonResearchBrief } from '../types/comparison.ts'

function displayError(error: unknown): ApiError {
  return error instanceof ApiError
    ? error
    : new ApiError('An unexpected error prevented AI brief generation.', { status: 0, code: 'UNEXPECTED_ERROR' })
}

export function useAiBrief(leftTicker: string, rightTicker: string) {
  const [brief, setBrief] = useState<ComparisonResearchBrief | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [isGenerating, setIsGenerating] = useState(false)
  const controller = useRef<AbortController | null>(null)
  const sequence = useRef(0)

  useEffect(() => () => controller.current?.abort(), [])

  const generate = useCallback(async (forceRefresh = false) => {
    if (isGenerating) return
    controller.current?.abort()
    const next = new AbortController()
    controller.current = next
    const request = ++sequence.current
    setError(null)
    setIsGenerating(true)
    try {
      const response = await generateComparisonBrief({ leftTicker, rightTicker, forceRefresh }, next.signal)
      if (request === sequence.current && !next.signal.aborted) setBrief(response)
    } catch (caught) {
      if (request !== sequence.current || next.signal.aborted) return
      setError(displayError(caught))
    } finally {
      if (request === sequence.current) setIsGenerating(false)
    }
  }, [isGenerating, leftTicker, rightTicker])

  const clear = useCallback(() => { controller.current?.abort(); setBrief(null); setError(null) }, [])
  return { brief, error, isGenerating, generate, clear }
}
