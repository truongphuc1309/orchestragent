import { act, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACM_PROJECT, ACME, ADA, comment, GLOBEX, issue, issueId, project, projectId } from '@/test/fixtures'
import { FakeEventSource } from '@/test/fakeEventSource'
import { fakeServer, json, problem } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import { useIssueViewStore } from '@/features/issue/filters'
import type { Comment } from '@/features/issue/schemas'
import type { Project } from '@/features/project/schemas'

const ISSUES = `/api/workspaces/${ACME.id}/issues`
const STREAM = `/api/workspaces/${ACME.id}/events`
const FIRST = issue({ id: issueId(1), number: 1, title: 'First issue' })
const SECOND = issue({ id: issueId(2), number: 2, title: 'Second issue' })

/** The page of a workspace whose data changes between requests, like the real server's. */
function liveServer() {
  const comments: Comment[] = []
  const projects: Project[] = []
  const state = { issues: [FIRST], comments, detail: FIRST, detailGone: false, projects }
  const server = fakeServer({
    'GET /api/me': () => json(200, ADA),
    'GET /api/workspaces': () => json(200, [ACME, GLOBEX]),
    [`GET /api/workspaces/${ACME.id}/members`]: () => json(200, []),
    [`GET /api/workspaces/${ACME.id}/agents`]: () => json(200, []),
    [`GET /api/workspaces/${ACME.id}/tasks?issueId=${FIRST.id}`]: () => json(200, []),
    [`GET ${ISSUES}?limit=500`]: () => json(200, { items: state.issues, total: state.issues.length }),
    [`GET ${ISSUES}/key/ACM-1`]: () =>
      state.detailGone ? problem(404, { code: 'issue.not_found' }) : json(200, state.detail),
    [`GET ${ISSUES}/${FIRST.id}/comments`]: () => json(200, state.comments),
    [`GET /api/workspaces/${ACME.id}/projects?includeArchived=true`]: () => json(200, [ACM_PROJECT]),
    [`GET /api/workspaces/${ACME.id}/projects`]: () => json(200, state.projects),
  })
  return { state, server }
}

function stream(): FakeEventSource {
  const source = FakeEventSource.latest(STREAM)
  if (source === undefined) {
    throw new Error('The workspace has not opened its event stream')
  }
  return source
}

describe('live updates of a workspace', () => {
  beforeEach(() => {
    useIssueViewStore.setState({ view: 'board', filtersByWorkspace: {} })
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('opens the stream of the workspace on screen, and only then', async () => {
    liveServer()
    const { router } = renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('First issue')

    expect(FakeEventSource.instances.map((source) => source.url)).toEqual([STREAM])

    await act(() => router.navigate('/w/globex/issues'))
    await waitFor(() => expect(FakeEventSource.latest(`/api/workspaces/${GLOBEX.id}/events`)).toBeDefined())
    expect(FakeEventSource.instances.find((source) => source.url === STREAM)?.closed).toBe(true)
  })

  it('does not open a stream on the login page', async () => {
    fakeServer({ 'GET /api/me': () => problem(401, {}) })
    renderRoutes(routes, ['/login'])

    await screen.findByText('Sign in to Orchestragent')

    expect(FakeEventSource.instances).toEqual([])
  })

  it('closes the stream when the workspace is left', async () => {
    liveServer()
    const view = renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('First issue')

    view.unmount()

    expect(stream().closed).toBe(true)
  })

  it('shows an issue created by someone else on the board without a reload', async () => {
    const { state } = liveServer()
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('First issue')
    stream().open()

    state.issues = [FIRST, SECOND]
    act(() => stream().emit('issue.created', { issueId: SECOND.id, key: 'ACM-2' }))

    expect(await screen.findByText('Second issue')).toBeInTheDocument()
  })

  it('shows a project created by someone else in the navigation without a reload', async () => {
    const { state } = liveServer()
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('First issue')
    const nav = screen.getByRole('navigation', { name: 'Workspace' })
    expect(within(nav).queryByRole('link', { name: 'Mobile app' })).toBeNull()

    state.projects = [project({ id: projectId(5), key: 'MOB', name: 'Mobile app' })]
    act(() => stream().emit('project.updated', { projectId: projectId(5) }))

    expect(await within(nav).findByRole('link', { name: 'Mobile app' })).toHaveAttribute('href', '/w/acme/projects/MOB')
  })

  it('removes an issue deleted by someone else from the board', async () => {
    const { state } = liveServer()
    state.issues = [FIRST, SECOND]
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('Second issue')

    state.issues = [FIRST]
    act(() => stream().emit('issue.deleted', { issueId: SECOND.id, key: 'ACM-2' }))

    await waitFor(() => expect(screen.queryByText('Second issue')).not.toBeInTheDocument())
  })

  it('updates an open issue page when someone edits the issue', async () => {
    const { state } = liveServer()
    renderRoutes(routes, ['/w/acme/issues/ACM-1'])
    await screen.findByRole('heading', { name: 'First issue' })

    state.detail = { ...FIRST, title: 'Renamed by a teammate' }
    act(() => stream().emit('issue.updated', { issueId: FIRST.id, changedFields: ['title'] }))

    expect(await screen.findByRole('heading', { name: 'Renamed by a teammate' })).toBeInTheDocument()
  })

  it('tells the person looking at an issue that it was deleted', async () => {
    const { state } = liveServer()
    renderRoutes(routes, ['/w/acme/issues/ACM-1'])
    await screen.findByRole('heading', { name: 'First issue' })

    state.detailGone = true
    act(() => stream().emit('issue.deleted', { issueId: FIRST.id, number: 1 }))

    expect(await screen.findByRole('heading', { name: 'Issue not found' })).toBeInTheDocument()
  })

  it('shows a new comment on the open issue', async () => {
    const { state } = liveServer()
    renderRoutes(routes, ['/w/acme/issues/ACM-1'])
    await screen.findByText('No comments yet.')

    const added = comment({ id: '0198f1c0-0000-7000-8000-0000000002b1', issueId: FIRST.id, body: 'From the other tab' })
    state.comments = [added]
    act(() => stream().emit('comment.created', { issueId: FIRST.id, commentId: added.id }))

    expect(await screen.findByText('From the other tab')).toBeInTheDocument()
  })

  it('ignores a comment on an issue that is not open', async () => {
    const { server } = liveServer()
    renderRoutes(routes, ['/w/acme/issues/ACM-1'])
    await screen.findByText('No comments yet.')
    const before = server.calls(`GET ${ISSUES}/${FIRST.id}/comments`).length

    act(() => stream().emit('comment.created', { issueId: SECOND.id, commentId: '0198f1c0-0000-7000-8000-0000000002b2' }))

    await new Promise((resolve) => setTimeout(resolve, 30))
    expect(server.calls(`GET ${ISSUES}/${FIRST.id}/comments`)).toHaveLength(before)
  })

  it('ignores an event it does not understand', async () => {
    const { server } = liveServer()
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('First issue')
    const before = server.calls(`GET ${ISSUES}?limit=500`).length

    act(() => {
      stream().emit('issue.updated', { unexpected: true })
      stream().emit('issue.updated', 'not json')
    })

    await new Promise((resolve) => setTimeout(resolve, 30))
    expect(server.calls(`GET ${ISSUES}?limit=500`)).toHaveLength(before)
  })

  it('fetches everything again when the stream comes back after a break', async () => {
    const { state, server } = liveServer()
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('First issue')
    stream().open()
    const before = server.calls(`GET ${ISSUES}?limit=500`).length

    // Events were missed while the connection was down.
    state.issues = [FIRST, SECOND]
    act(() => stream().fail(false))
    act(() => stream().open())

    expect(await screen.findByText('Second issue')).toBeInTheDocument()
    expect(server.calls(`GET ${ISSUES}?limit=500`).length).toBeGreaterThan(before)
  })
})
