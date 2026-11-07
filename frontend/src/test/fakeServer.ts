import { vi } from 'vitest'

export interface RecordedRequest {
  method: string
  path: string
  headers: Headers
  body: unknown
  credentials: RequestCredentials | undefined
}

type Handler = (request: RecordedRequest) => Response | Promise<Response>

export function json(status: number, body: unknown, contentType = 'application/json'): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } })
}

export function problem(status: number, body: Record<string, unknown>): Response {
  return json(status, { status, ...body }, 'application/problem+json')
}

export function noContent(): Response {
  return new Response(null, { status: 204 })
}

/**
 * The one request every workspace page makes in the background: the unread count of the inbox badge in the
 * navigation. Unless a test handles it itself, it is answered with zero, so page tests do not all have to list it.
 * Nothing else has a default.
 */
const UNREAD_COUNT = /^\/api\/workspaces\/[^/]+\/inbox\/unread-count$/

function ambientHandler(method: string, path: string): Handler | undefined {
  return method === 'GET' && UNREAD_COUNT.test(path) ? () => json(200, { unreadCount: 0 }) : undefined
}

/**
 * Replaces `fetch` with a table of handlers keyed by "METHOD /path". A request nobody handles fails the
 * test loudly, so tests can never silently talk to a real server (the inbox badge's unread count is the only
 * exception, see {@link ambientHandler}).
 */
export function fakeServer(handlers: Record<string, Handler>) {
  const requests: RecordedRequest[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = typeof input === 'string' ? input : input instanceof URL ? input.pathname : input.url
    const method = (init?.method ?? 'GET').toUpperCase()
    const rawBody = typeof init?.body === 'string' ? init.body : undefined
    const request: RecordedRequest = {
      method,
      path,
      headers: new Headers(init?.headers),
      body: rawBody === undefined ? undefined : JSON.parse(rawBody),
      credentials: init?.credentials,
    }
    requests.push(request)
    const handler = handlers[`${method} ${path}`] ?? ambientHandler(method, path)
    if (handler === undefined) {
      throw new Error(`Unhandled request in test: ${method} ${path}`)
    }
    return handler(request)
  })
  vi.stubGlobal('fetch', fetchMock)
  return {
    requests,
    calls: (key: string) => requests.filter((request) => `${request.method} ${request.path}` === key),
  }
}
