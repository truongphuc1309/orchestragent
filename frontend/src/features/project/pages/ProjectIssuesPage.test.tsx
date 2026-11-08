import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACME, ADA, issue, issueId, project, projectId } from '@/test/fixtures'
import { fakeServer, json, problem, type RecordedRequest } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import { NO_FILTERS, useIssueViewStore } from '@/features/issue/filters'
import type { Project } from '../schemas'

const WS = `/api/workspaces/${ACME.id}`
const WEB = project({ id: projectId(1), key: 'WEB', name: 'Web app', description: 'The pages people click' })
const API = project({ id: projectId(2), key: 'API', name: 'Backend' })
const OLD = project({ id: projectId(3), key: 'OLD', name: 'Old site', archived: true, archivedAt: '2026-01-09T10:00:00Z' })

const WEB_ISSUE = issue({ id: issueId(1), number: 3, title: 'Fix the login', projectId: WEB.id, key: 'WEB-3' })
const OLD_ISSUE = issue({ id: issueId(2), number: 1, title: 'Old page', projectId: OLD.id, key: 'OLD-1' })

type Handler = (request: RecordedRequest) => Response | Promise<Response>

function handlers(shown: Project, extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /api/me': () => json(200, ADA),
    'GET /api/workspaces': () => json(200, [ACME]),
    [`GET ${WS}/members`]: () => json(200, []),
    [`GET ${WS}/agents`]: () => json(200, []),
    [`GET ${WS}/projects`]: () => json(200, [API, WEB]),
    [`GET ${WS}/projects?includeArchived=true`]: () => json(200, [API, OLD, WEB]),
    [`GET ${WS}/projects/key/${shown.key}`]: () => json(200, shown),
    [`GET ${WS}/issues?project=${WEB.id}&limit=500`]: () => json(200, { items: [WEB_ISSUE], total: 1 }),
    [`GET ${WS}/issues?project=${OLD.id}&limit=500`]: () => json(200, { items: [OLD_ISSUE], total: 1 }),
    ...extra,
  }
}

describe('project page', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    useIssueViewStore.setState({ view: 'board', filtersByWorkspace: {}, lastProjectByWorkspace: {} })
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('shows the project and only its issues, with links by issue key', async () => {
    const server = fakeServer(handlers(WEB))
    renderRoutes(routes, ['/w/acme/projects/WEB'])

    expect(await screen.findByRole('heading', { name: /Web app/ })).toHaveTextContent('WEB')
    expect(screen.getByText('The pages people click')).toBeInTheDocument()
    const card = await screen.findByRole('link', { name: 'Fix the login' })
    expect(card).toHaveAttribute('href', '/w/acme/issues/WEB-3')
    expect(screen.getByText('WEB-3')).toBeInTheDocument()
    expect(server.calls(`GET ${WS}/issues?project=${WEB.id}&limit=500`)).toHaveLength(1)
    // The project is fixed here, so there is no project filter to choose.
    expect(screen.queryByRole('button', { name: 'Project' })).not.toBeInTheDocument()
  })

  it('keeps the other saved filters, and leaves the Issues page project choice alone', async () => {
    useIssueViewStore.setState({
      filtersByWorkspace: { [ACME.id]: { ...NO_FILTERS, projects: [API.id], statuses: ['todo'] } },
    })
    const path = `GET ${WS}/issues?project=${WEB.id}&status=todo&limit=500`
    const server = fakeServer(handlers(WEB, { [path]: () => json(200, { items: [WEB_ISSUE], total: 1 }) }))
    renderRoutes(routes, ['/w/acme/projects/WEB'])
    const user = userEvent.setup()

    await waitFor(() => expect(server.calls(path)).toHaveLength(1))
    await user.click(screen.getByRole('button', { name: 'Clear filters' }))

    await waitFor(() =>
      expect(useIssueViewStore.getState().filtersByWorkspace[ACME.id]).toEqual({ ...NO_FILTERS, projects: [API.id] }),
    )
  })

  it('creates new issues in this project, whatever was used last', async () => {
    useIssueViewStore.setState({ lastProjectByWorkspace: { [ACME.id]: API.id } })
    const created = issue({ id: issueId(9), number: 4, title: 'New page', projectId: WEB.id, key: 'WEB-4' })
    const server = fakeServer(handlers(WEB, { [`POST ${WS}/issues`]: () => json(201, created) }))
    renderRoutes(routes, ['/w/acme/projects/WEB'])
    const user = userEvent.setup()
    await screen.findByRole('link', { name: 'Fix the login' })

    await user.click(screen.getByRole('button', { name: 'Create issue' }))
    const dialog = await screen.findByRole('dialog', { name: 'Create issue' })
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: 'Project' })).toHaveTextContent('Web app'))
    await user.type(within(dialog).getByLabelText('Title'), 'New page')
    await user.click(within(dialog).getByRole('button', { name: 'Create' }))

    await waitFor(() => expect(server.calls(`POST ${WS}/issues`)).toHaveLength(1))
    expect(server.calls(`POST ${WS}/issues`)[0]?.body).toMatchObject({ projectId: WEB.id, title: 'New page' })
    expect(await screen.findByText('WEB-4 was created')).toBeInTheDocument()
    expect(useIssueViewStore.getState().lastProjectByWorkspace[ACME.id]).toBe(WEB.id)
  })

  it('shows an archived project read-only: its issues stay, but nothing new can be added', async () => {
    fakeServer(handlers(OLD))
    renderRoutes(routes, ['/w/acme/projects/OLD'])

    expect(await screen.findByRole('link', { name: 'Old page' })).toBeInTheDocument()
    expect(screen.getByText(/This project is archived/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create issue' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Create issue in/ })).not.toBeInTheDocument()
  })

  it('finds the project whatever the case of the key in the address', async () => {
    fakeServer(handlers(WEB))
    renderRoutes(routes, ['/w/acme/projects/web'])

    expect(await screen.findByRole('link', { name: 'Fix the login' })).toBeInTheDocument()
  })

  it('says so for a project that does not exist', async () => {
    fakeServer(handlers(WEB, { [`GET ${WS}/projects/key/NOPE`]: () => problem(404, { code: 'project.not_found' }) }))
    renderRoutes(routes, ['/w/acme/projects/NOPE'])

    expect(await screen.findByRole('heading', { name: 'Project not found' })).toBeInTheDocument()
    expect(screen.getByText(`There is no project NOPE in ${ACME.name}.`)).toBeInTheDocument()
  })

  it('offers a retry when the project cannot be loaded', async () => {
    let healthy = false
    fakeServer(handlers(WEB, { [`GET ${WS}/projects/key/WEB`]: () => (healthy ? json(200, WEB) : problem(500, {})) }))
    renderRoutes(routes, ['/w/acme/projects/WEB'])
    const user = userEvent.setup()

    expect(await screen.findByText('Could not load the project.')).toBeInTheDocument()
    healthy = true
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('link', { name: 'Fix the login' })).toBeInTheDocument()
  })
})
