import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACM_PROJECT, ACME, ADA, issue, issueId } from '@/test/fixtures'
import { FakeEventSource } from '@/test/fakeEventSource'
import { fakeServer, json, noContent, problem } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import type { Notification } from '../schemas'

const BASE = `/api/workspaces/${ACME.id}/inbox`
const STREAM = `/api/workspaces/${ACME.id}/events`

function notification(n: number, overrides: Partial<Notification>): Notification {
  return {
    id: `0198f1c0-0000-7000-8000-0000000004${String(n).padStart(2, '0')}`,
    type: 'issue_assigned',
    issueId: issueId(n),
    issueKey: `ACM-${n}`,
    issueTitle: `Issue ${n}`,
    actorName: 'Mia',
    detail: null,
    createdAt: new Date(Date.now() - n * 60_000).toISOString(),
    readAt: null,
    ...overrides,
  }
}

const ASSIGNED = notification(1, { issueTitle: 'Write the docs' })
const FINISHED = notification(2, { type: 'agent_completed', actorName: 'Builder', issueTitle: 'Add login' })
const FAILED = notification(3, {
  type: 'agent_failed',
  actorName: 'Builder',
  detail: 'timeout',
  readAt: new Date().toISOString(),
})

/** A server whose inbox changes when notifications are read, like the real one. */
function inboxServer(initial: Notification[] = [ASSIGNED, FINISHED, FAILED]) {
  const state = { notifications: initial }
  const unread = () => state.notifications.filter((n) => n.readAt === null).length
  const server = fakeServer({
    'GET /api/me': () => json(200, ADA),
    'GET /api/workspaces': () => json(200, [ACME]),
    [`GET ${BASE}`]: () => json(200, { notifications: state.notifications, unreadCount: unread() }),
    [`GET ${BASE}/unread-count`]: () => json(200, { unreadCount: unread() }),
    [`POST ${BASE}/read-all`]: () => {
      state.notifications = state.notifications.map((n) => ({ ...n, readAt: n.readAt ?? new Date().toISOString() }))
      return noContent()
    },
    [`POST ${BASE}/${ASSIGNED.id}/read`]: () => {
      state.notifications = state.notifications.map((n) => (n.id === ASSIGNED.id ? { ...n, readAt: new Date().toISOString() } : n))
      return json(200, { ...ASSIGNED, readAt: new Date().toISOString() })
    },
    // The issue page that opening a notification leads to.
    [`GET /api/workspaces/${ACME.id}/issues/key/ACM-1`]: () => json(200, issue({ id: issueId(1), number: 1, title: 'Write the docs' })),
    [`GET /api/workspaces/${ACME.id}/issues/${issueId(1)}/comments`]: () => json(200, []),
    [`GET /api/workspaces/${ACME.id}/members`]: () => json(200, []),
    [`GET /api/workspaces/${ACME.id}/agents`]: () => json(200, []),
    [`GET /api/workspaces/${ACME.id}/tasks?issueId=${issueId(1)}`]: () => json(200, []),
    [`GET /api/workspaces/${ACME.id}/projects?includeArchived=true`]: () => json(200, [ACM_PROJECT]),
  })
  return { state, server }
}

function badge(): string | null {
  const link = screen.getByRole('link', { name: /^Inbox/ })
  return link.textContent?.replace('Inbox', '') ?? null
}

describe('inbox page', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('says what happened on each issue, newest first, with the unread ones marked', async () => {
    inboxServer()
    renderRoutes(routes, ['/w/acme/inbox'])

    const list = await screen.findByRole('list', { name: 'Notifications' })
    const items = within(list).getAllByRole('listitem')
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining('Unread: Mia assigned you ACM-1Write the docs'),
      expect.stringContaining('Unread: Builder finished ACM-2: it is waiting for your review'),
      expect.stringContaining('Builder could not finish ACM-3: it ran out of time'),
    ])
    expect(items[2]?.textContent).not.toContain('Unread')
    expect(within(list).getByRole('link', { name: /assigned you ACM-1/ })).toHaveAttribute('href', '/w/acme/issues/ACM-1')
  })

  it('shows the unread count in the navigation', async () => {
    inboxServer()
    renderRoutes(routes, ['/w/acme/inbox'])

    await waitFor(() => expect(badge()).toBe('2'))
  })

  it('opening an unread notification marks it read and goes to the issue', async () => {
    const { server } = inboxServer()
    const user = userEvent.setup()
    const { router } = renderRoutes(routes, ['/w/acme/inbox'])

    await user.click(await screen.findByRole('link', { name: /assigned you ACM-1/ }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/w/acme/issues/ACM-1'))
    expect(server.calls(`POST ${BASE}/${ASSIGNED.id}/read`)).toHaveLength(1)
    await waitFor(() => expect(badge()).toBe('1'))
  })

  it('opening a notification that is already read sends nothing', async () => {
    const { server } = inboxServer([{ ...ASSIGNED, readAt: new Date().toISOString() }])
    const user = userEvent.setup()
    renderRoutes(routes, ['/w/acme/inbox'])

    await user.click(await screen.findByRole('link', { name: /assigned you ACM-1/ }))

    expect(server.requests.filter((request) => request.method === 'POST')).toHaveLength(0)
  })

  it('marks everything read at once, and the button rests when nothing is unread', async () => {
    const { server } = inboxServer()
    const user = userEvent.setup()
    renderRoutes(routes, ['/w/acme/inbox'])
    const button = await screen.findByRole('button', { name: 'Mark all as read' })
    await waitFor(() => expect(button).toBeEnabled())

    await user.click(button)

    expect(server.calls(`POST ${BASE}/read-all`)).toHaveLength(1)
    await waitFor(() => expect(button).toBeDisabled())
    await waitFor(() => expect(badge()).toBe(''))
    expect(screen.queryByText(/Unread:/)).not.toBeInTheDocument()
  })

  it('refreshes the list and the badge when the server says the inbox changed', async () => {
    const { state } = inboxServer([])
    renderRoutes(routes, ['/w/acme/inbox'])
    expect(await screen.findByText(/Nothing here yet/)).toBeInTheDocument()

    state.notifications = [ASSIGNED]
    act(() => FakeEventSource.latest(STREAM)?.emit('inbox.updated', {}))

    expect(await screen.findByRole('link', { name: /assigned you ACM-1/ })).toBeInTheDocument()
    await waitFor(() => expect(badge()).toBe('1'))
  })

  it('offers to try again when the inbox cannot be loaded', async () => {
    fakeServer({
      'GET /api/me': () => json(200, ADA),
      'GET /api/workspaces': () => json(200, [ACME]),
      [`GET ${BASE}`]: () => problem(500, { title: 'Broken' }),
    })
    renderRoutes(routes, ['/w/acme/inbox'])

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the inbox.')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})
