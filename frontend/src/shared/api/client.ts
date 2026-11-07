import { z } from 'zod'
import { ApiError, problemSchema, UnexpectedResponseError } from './errors'

const XSRF_COOKIE = 'XSRF-TOKEN'
const XSRF_HEADER = 'X-XSRF-TOKEN'

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export interface RequestOptions<T> {
  /** Every response is parsed with this schema; there are no unchecked casts. */
  schema: z.ZodType<T>
  method?: Method
  body?: unknown
  signal?: AbortSignal
}

export function readCookie(name: string): string | undefined {
  const prefix = `${name}=`
  const entry = document.cookie.split('; ').find((part) => part.startsWith(prefix))
  return entry === undefined ? undefined : decodeURIComponent(entry.slice(prefix.length))
}

/** The server hands out the CSRF cookie on any response; a cheap GET gets it before the first write. */
async function csrfToken(): Promise<string | undefined> {
  const existing = readCookie(XSRF_COOKIE)
  if (existing !== undefined) {
    return existing
  }
  await fetch('/api/health', { credentials: 'same-origin' })
  return readCookie(XSRF_COOKIE)
}

export async function apiRequest<T>(path: string, options: RequestOptions<T>): Promise<T> {
  const method = options.method ?? 'GET'
  const headers = new Headers({ Accept: 'application/json' })
  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  if (method !== 'GET') {
    const token = await csrfToken()
    if (token !== undefined) {
      headers.set(XSRF_HEADER, token)
    }
  }
  const response = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  })
  return parseResponse(path, response, options.schema)
}

export async function parseResponse<T>(path: string, response: Response, schema: z.ZodType<T>): Promise<T> {
  if (!response.ok) {
    throw await toApiError(response)
  }
  const raw: unknown = response.status === 204 ? undefined : await readJson(path, response)
  const result = schema.safeParse(raw)
  if (!result.success) {
    throw new UnexpectedResponseError(path, z.prettifyError(result.error))
  }
  return result.data
}

async function readJson(path: string, response: Response): Promise<unknown> {
  try {
    return await response.json()
  } catch {
    throw new UnexpectedResponseError(path, 'body is not valid JSON')
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  let raw: unknown
  try {
    raw = await response.json()
  } catch {
    raw = undefined
  }
  const parsed = problemSchema.safeParse(raw)
  const problem = parsed.success ? parsed.data : {}
  const message = problem.detail ?? problem.title ?? (response.statusText || `Request failed (${response.status})`)
  return new ApiError(response.status, message, problem)
}
