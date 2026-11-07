import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { z } from 'zod'
import { fakeServer, json, noContent, problem } from '@/test/fakeServer'
import { apiRequest } from './client'
import { ApiError, UnexpectedResponseError } from './errors'

function clearCookies() {
  for (const part of document.cookie.split('; ')) {
    const name = part.split('=')[0]
    if (name) {
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
    }
  }
}

describe('apiRequest', () => {
  beforeEach(clearCookies)
  afterEach(() => {
    vi.unstubAllGlobals()
    clearCookies()
  })

  it('sends GET requests with the session cookie but without a CSRF header', async () => {
    document.cookie = 'XSRF-TOKEN=abc123; path=/'
    const server = fakeServer({ 'GET /api/thing': () => json(200, { id: 1 }) })

    const result = await apiRequest('/api/thing', { schema: z.object({ id: z.number() }) })

    expect(result).toEqual({ id: 1 })
    const [request] = server.requests
    expect(request?.credentials).toBe('same-origin')
    expect(request?.headers.has('X-XSRF-TOKEN')).toBe(false)
  })

  it('copies the XSRF-TOKEN cookie into the X-XSRF-TOKEN header on writes', async () => {
    document.cookie = 'XSRF-TOKEN=abc123; path=/'
    const server = fakeServer({ 'POST /api/thing': () => noContent() })

    await apiRequest('/api/thing', { method: 'POST', body: { name: 'x' }, schema: z.undefined() })

    const [request] = server.requests
    expect(request?.headers.get('X-XSRF-TOKEN')).toBe('abc123')
    expect(request?.headers.get('Content-Type')).toBe('application/json')
    expect(request?.body).toEqual({ name: 'x' })
  })

  it('fetches a CSRF cookie first when there is none yet', async () => {
    const server = fakeServer({
      'GET /api/health': () => {
        document.cookie = 'XSRF-TOKEN=fresh; path=/'
        return json(200, { status: 'UP' })
      },
      'POST /api/thing': () => noContent(),
    })

    await apiRequest('/api/thing', { method: 'POST', schema: z.undefined() })

    expect(server.requests.map((request) => `${request.method} ${request.path}`)).toEqual([
      'GET /api/health',
      'POST /api/thing',
    ])
    expect(server.requests[1]?.headers.get('X-XSRF-TOKEN')).toBe('fresh')
  })

  it('turns a problem+json response into an ApiError with its details', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    fakeServer({
      'POST /api/auth/code': () =>
        problem(429, { detail: 'A login code was sent recently.', code: 'login.code_rate_limited', retryAfterSeconds: 42 }),
    })

    const error = await apiRequest('/api/auth/code', { method: 'POST', schema: z.undefined() }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    if (!(error instanceof ApiError)) {
      throw new Error('unreachable')
    }
    expect(error.status).toBe(429)
    expect(error.code).toBe('login.code_rate_limited')
    expect(error.retryAfterSeconds).toBe(42)
    expect(error.message).toBe('A login code was sent recently.')
  })

  it('still produces an ApiError when the error body is not JSON', async () => {
    fakeServer({ 'GET /api/thing': () => new Response('<html>Bad gateway</html>', { status: 502 }) })

    const error = await apiRequest('/api/thing', { schema: z.unknown() }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 502 })
  })

  it('rejects a successful response whose body does not match the schema', async () => {
    fakeServer({ 'GET /api/thing': () => json(200, { id: 'not-a-number' }) })

    const error = await apiRequest('/api/thing', { schema: z.object({ id: z.number() }) }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(UnexpectedResponseError)
  })

  it('rejects a successful response that is not JSON', async () => {
    fakeServer({ 'GET /api/thing': () => new Response('plain text', { status: 200 }) })

    const error = await apiRequest('/api/thing', { schema: z.object({}) }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(UnexpectedResponseError)
  })

  it('does not accept a body on a 204 unless the schema allows it', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    fakeServer({ 'POST /api/thing': () => noContent() })

    const error = await apiRequest('/api/thing', { method: 'POST', schema: z.object({ id: z.number() }) }).catch(
      (e: unknown) => e,
    )

    expect(error).toBeInstanceOf(UnexpectedResponseError)
  })
})
