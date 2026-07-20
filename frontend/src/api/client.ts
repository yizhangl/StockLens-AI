import type { ApiErrorResponse } from '../features/comparison/types/comparison.ts'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly requestId: string | null
  readonly details: ApiErrorResponse['details']

  constructor(
    message: string,
    options: {
      status: number
      code: string
      requestId?: string | null
      details?: ApiErrorResponse['details']
    },
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = options.status
    this.code = options.code
    this.requestId = options.requestId ?? null
    this.details = options.details ?? []
  }
}

function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<ApiErrorResponse>
  return typeof candidate.code === 'string' && typeof candidate.message === 'string'
}

export async function getJson<T>(
  path: string,
  options: { signal?: AbortSignal } = {},
): Promise<T> {
  let response: Response

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      headers: { Accept: 'application/json' },
      signal: options.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError('Unable to connect to StockLens AI. Please try again.', {
      status: 0,
      code: 'NETWORK_ERROR',
    })
  }

  const contentType = response.headers.get('content-type') ?? ''
  let body: unknown = null

  if (contentType.includes('application/json')) {
    try {
      body = await response.json()
    } catch {
      if (response.ok) {
        throw new ApiError('StockLens AI returned an unreadable response.', {
          status: response.status,
          code: 'INVALID_RESPONSE',
        })
      }
    }
  }

  if (!response.ok) {
    if (isApiErrorResponse(body)) {
      throw new ApiError(body.message, {
        status: response.status,
        code: body.code,
        requestId: body.requestId,
        details: body.details,
      })
    }
    throw new ApiError('StockLens AI could not complete this comparison.', {
      status: response.status,
      code: 'REQUEST_FAILED',
    })
  }

  if (!contentType.includes('application/json') || body === null) {
    throw new ApiError('StockLens AI returned an unreadable response.', {
      status: response.status,
      code: 'INVALID_RESPONSE',
    })
  }

  return body as T
}
