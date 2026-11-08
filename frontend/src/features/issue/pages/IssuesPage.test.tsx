import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACM_PROJECT, ACME, ADA, agent, agentId, issue, issueId, member, MIA, project, projectId } from '@/test/fixtures'
import type { Agent } from '@/features/agent/schemas'
import { fakeServer, json, problem } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import { NO_FILTERS, useIssueViewStore } from '../filters'
import type { Issue } from '../schemas'

const MEMBERS = [member({ userId: ADA.id, name: 'ada', role: 'owner' }), member({ userId: MIA.userId, name: 'Mia' })]

const ISSUES: Issue[] = [
  issue({ id: issueId(1), number: 1, title: 'Write the plan', status: 'todo', position: 1024, priority: 'high' }),
  issue({
    id: issueId(2),
    number: 2,
    title: 'Fix the login',
    status: 'todo',
    position: 512,
    assignee: { type: 'member', id: MIA.userId, name: 'Mia' },
  }),
  issue({ id: issueId(3), number: 3, title: 'Ship it', status: 'done', position: 1024, priority: 'urgent' }),
  issue({ id: issueId(4), number: 4, title: 'Dropped idea', status: 'cancelled', position: 1024 }),
]

const ISSUES_PATH = `/api/workspaces/${ACME.id}/issues`
const LIST = `GET ${ISSUES_PATH}?limit=500`
const AGENTS = `GET /api/workspaces/${ACME.id}/agents`
/** No agents unless a test puts some here. */
let AGENTS_LIST: Agent[] = []

const ACTIVE_PROJECTS = `GET /api/workspaces/${ACME.id}/projects`

function baseHandlers(items: Issue[] = ISSUES, total = items.length) {
  return {
    'GET /api/me': () => json(200, ADA),
    'GET /api/workspaces': () => json(200, [ACME]),
    [`GET /api/workspaces/${ACME.id}/members`]: () => json(200, MEMBERS),
    [AGENTS]: () => json(200, AGENTS_LIST),
    [LIST]: () => json(200, { items, total }),
    [`GET /api/workspaces/${ACME.id}/projects?includeArchived=true`]: () => json(200, [ACM_PROJECT]),
    [ACTIVE_PROJECTS]: () => json(200, [ACM_PROJECT]),
  }
}

/** The card (list item) that holds the link with this title. */
function cardOf(title: string): HTMLElement {
  const card = screen.getByRole('link', { name: title }).closest('li')
  if (card === null) {
    throw new Error(`No card for ${title}`)
  }
  return card
}

function cardsIn(columnName: string): string[] {
  const column = screen.getByRole('region', { name: columnName })
  return within(column)
    .queryAllByRole('link')
    .map((link) => link.textContent ?? '')
}

describe('issues page', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    useIssueViewStore.setState({ view: 'board', filtersByWorkspace: {}, lastProjectByWorkspace: {} })
    AGENTS_LIST = []
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  describe('board', () => {
    it('shows the six columns with each issue in its column, ordered by position', async () => {
      fakeServer(baseHandlers())
      renderRoutes(routes, ['/w/acme/issues'])

      await screen.findByText('Write the plan')
      for (const name of ['Backlog', 'To do', 'In progress', 'In review', 'Blocked', 'Done']) {
        expect(screen.getByRole('region', { name })).toBeInTheDocument()
      }
      expect(cardsIn('To do')).toEqual(['Fix the login', 'Write the plan'])
      expect(cardsIn('Done')).toEqual(['Ship it'])
      expect(cardsIn('Backlog')).toEqual([])
      expect(screen.getByRole('region', { name: 'To do' })).toHaveTextContent('2')
    })

    it('leaves cancelled issues off the board', async () => {
      fakeServer(baseHandlers())
      renderRoutes(routes, ['/w/acme/issues'])

      await screen.findByText('Write the plan')

      expect(screen.queryByText('Dropped idea')).not.toBeInTheDocument()
      expect(screen.queryByRole('region', { name: 'Cancelled' })).not.toBeInTheDocument()
    })

    it('shows the key, priority and assignee on a card and links to the issue', async () => {
      fakeServer(baseHandlers())
      renderRoutes(routes, ['/w/acme/issues'])

      const link = await screen.findByRole('link', { name: 'Fix the login' })
      const card = cardOf('Fix the login')
      expect(link).toHaveAttribute('href', '/w/acme/issues/ACM-2')
      expect(card).toHaveTextContent('ACM-2')
      expect(within(card).getByText('Assigned to Mia')).toBeInTheDocument()
      expect(within(cardOf('Write the plan')).getByRole('img', { name: 'Priority: High' })).toBeInTheDocument()
    })

    it('shows a message when the issues cannot be loaded and retries', async () => {
      let healthy = false
      fakeServer({
        ...baseHandlers(),
        [LIST]: () => (healthy ? json(200, { items: ISSUES, total: 4 }) : problem(500, {})),
      })
      renderRoutes(routes, ['/w/acme/issues'])
      const user = userEvent.setup()

      expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the issues')
      healthy = true
      await user.click(screen.getByRole('button', { name: 'Try again' }))

      expect(await screen.findByText('Write the plan')).toBeInTheDocument()
    })

    it('says when the list was cut short', async () => {
      fakeServer(baseHandlers(ISSUES, 1203))
      renderRoutes(routes, ['/w/acme/issues'])

      expect(await screen.findByRole('status')).toHaveTextContent('Showing the newest 4 of 1203 issues')
    })
  })

  describe('list view', () => {
    it('switches to a table that includes cancelled issues, and remembers the choice', async () => {
      fakeServer(baseHandlers())
      renderRoutes(routes, ['/w/acme/issues'])
      const user = userEvent.setup()
      await screen.findByText('Write the plan')

      await user.click(screen.getByRole('tab', { name: 'List' }))

      const table = await screen.findByRole('table')
      expect(within(table).getAllByRole('row')).toHaveLength(5)
      expect(within(table).getByText('Dropped idea')).toBeInTheDocument()
      expect(within(table).getByText('ACM-4')).toBeInTheDocument()
      expect(within(table).getByText('Mia')).toBeInTheDocument()
      expect(within(table).getAllByText('Unassigned')).toHaveLength(3)
      expect(useIssueViewStore.getState().view).toBe('list')
      expect(within(table).getByRole('link', { name: 'Fix the login' })).toHaveAttribute('href', '/w/acme/issues/ACM-2')
    })

    it('opens in the remembered view', async () => {
      useIssueViewStore.setState({ view: 'list' })
      fakeServer(baseHandlers())
      renderRoutes(routes, ['/w/acme/issues'])

      expect(await screen.findByRole('table')).toBeInTheDocument()
    })

    it('invites the user to create the first issue when there are none', async () => {
      useIssueViewStore.setState({ view: 'list' })
      fakeServer(baseHandlers([], 0))
      renderRoutes(routes, ['/w/acme/issues'])

      expect(await screen.findByText('No issues yet. Create the first one.')).toBeInTheDocument()
    })
  })

  describe('filters', () => {
    it('asks the server for the chosen statuses and remembers them for this workspace', async () => {
      const server = fakeServer({
        ...baseHandlers(),
        [`GET ${ISSUES_PATH}?status=todo&limit=500`]: () => json(200, { items: [ISSUES[0], ISSUES[1]], total: 2 }),
      })
      renderRoutes(routes, ['/w/acme/issues'])
      const user = userEvent.setup()
      await screen.findByText('Ship it')

      await user.click(screen.getByRole('button', { name: 'Status' }))
      await user.click(await screen.findByRole('menuitemcheckbox', { name: 'To do' }))
      await user.keyboard('{Escape}')

      await waitFor(() => expect(screen.queryByText('Ship it')).not.toBeInTheDocument())
      expect(server.calls(`GET ${ISSUES_PATH}?status=todo&limit=500`)).toHaveLength(1)
      expect(useIssueViewStore.getState().filtersByWorkspace[ACME.id]?.statuses).toEqual(['todo'])
      expect(screen.getByRole('button', { name: 'Status: 1 selected' })).toBeInTheDocument()
    })

    it('combines status, priority and assignee in one request', async () => {
      useIssueViewStore.setState({
        filtersByWorkspace: {
          [ACME.id]: {
            projects: [],
            statuses: ['todo', 'done'],
            priorities: ['high'],
            assignees: [MIA.userId],
            unassigned: true,
          },
        },
      })
      const path = `GET ${ISSUES_PATH}?status=todo&status=done&priority=high&assignee=${MIA.userId}&unassigned=true&limit=500`
      const server = fakeServer({ ...baseHandlers(), [path]: () => json(200, { items: [], total: 0 }) })
      renderRoutes(routes, ['/w/acme/issues'])

      await waitFor(() => expect(server.calls(path)).toHaveLength(1))
    })

    it('clears the filters', async () => {
      useIssueViewStore.setState({ filtersByWorkspace: { [ACME.id]: { ...NO_FILTERS, priorities: ['high'] } } })
      fakeServer({
        ...baseHandlers(),
        [`GET ${ISSUES_PATH}?priority=high&limit=500`]: () => json(200, { items: [ISSUES[0]], total: 1 }),
      })
      renderRoutes(routes, ['/w/acme/issues'])
      const user = userEvent.setup()
      await screen.findByText('Write the plan')
      expect(screen.queryByText('Ship it')).not.toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Clear filters' }))

      expect(await screen.findByText('Ship it')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Clear filters' })).not.toBeInTheDocument()
    })

    it('says when nothing matches the filters', async () => {
      useIssueViewStore.setState({
        view: 'list',
        filtersByWorkspace: { [ACME.id]: { ...NO_FILTERS, statuses: ['blocked'] } },
      })
      fakeServer({
        ...baseHandlers(),
        [`GET ${ISSUES_PATH}?status=blocked&limit=500`]: () => json(200, { items: [], total: 0 }),
      })
      renderRoutes(routes, ['/w/acme/issues'])

      expect(await screen.findByText('No issues match these filters.')).toBeInTheDocument()
    })

    it('offers the agents of the workspace in the assignee filter and filters by their ID', async () => {
      AGENTS_LIST = [agent({ id: agentId(1), name: 'Builder' })]
      const filtered = `GET ${ISSUES_PATH}?assignee=${agentId(1)}&limit=500`
      const server = fakeServer({
        ...baseHandlers(),
        [filtered]: () => json(200, { items: [], total: 0 }),
      })
      renderRoutes(routes, ['/w/acme/issues'])
      const user = userEvent.setup()
      await screen.findByText('Write the plan')

      await user.click(screen.getByRole('button', { name: 'Assignee' }))
      await user.click(await screen.findByRole('menuitemcheckbox', { name: 'Builder' }))

      await waitFor(() => expect(server.calls(filtered)).toHaveLength(1))
      expect(useIssueViewStore.getState().filtersByWorkspace[ACME.id]?.assignees).toEqual([agentId(1)])
    })

    it('does not carry filters over to another workspace', async () => {
      useIssueViewStore.setState({ filtersByWorkspace: { 'some-other-workspace': { ...NO_FILTERS, statuses: ['done'] } } })
      fakeServer(baseHandlers())
      renderRoutes(routes, ['/w/acme/issues'])

      await screen.findByText('Ship it')
      expect(screen.queryByRole('button', { name: 'Clear filters' })).not.toBeInTheDocument()
    })
  })

  describe('projects', () => {
    const API = project({ id: projectId(2), key: 'API', name: 'Backend' })
    const OLD = project({ id: projectId(3), key: 'OLD', name: 'Old site', archived: true })
    const API_ISSUE = issue({ id: issueId(5), number: 1, title: 'Add an endpoint', projectId: API.id, key: 'API-1' })

    it('filters by project, archived ones included, and remembers the choice', async () => {
      const path = `GET ${ISSUES_PATH}?project=${API.id}&limit=500`
      const server = fakeServer({
        ...baseHandlers(),
        [`GET /api/workspaces/${ACME.id}/projects?includeArchived=true`]: () => json(200, [API, ACM_PROJECT, OLD]),
        [path]: () => json(200, { items: [API_ISSUE], total: 1 }),
      })
      renderRoutes(routes, ['/w/acme/issues'])
      const user = userEvent.setup()
      await screen.findByText('Write the plan')

      await user.click(screen.getByRole('button', { name: 'Project' }))
      expect(await screen.findByRole('menuitemcheckbox', { name: /Old site.*archived/ })).toBeInTheDocument()
      await user.click(screen.getByRole('menuitemcheckbox', { name: /Backend/ }))
      await user.keyboard('{Escape}')

      expect(await screen.findByText('Add an endpoint')).toBeInTheDocument()
      expect(server.calls(path)).toHaveLength(1)
      expect(useIssueViewStore.getState().filtersByWorkspace[ACME.id]?.projects).toEqual([API.id])
      expect(screen.getByRole('button', { name: 'Project: 1 selected' })).toBeInTheDocument()
    })

    it('shows the issues of every project together, with the project of each in the list', async () => {
      useIssueViewStore.setState({ view: 'list' })
      fakeServer({
        ...baseHandlers([API_ISSUE, ...ISSUES]),
        [`GET /api/workspaces/${ACME.id}/projects?includeArchived=true`]: () => json(200, [API, ACM_PROJECT]),
      })
      renderRoutes(routes, ['/w/acme/issues'])

      const table = await screen.findByRole('table')
      const endpoint = within(table).getByRole('link', { name: 'Add an endpoint' })
      expect(endpoint).toHaveAttribute('href', '/w/acme/issues/API-1')
      const row = endpoint.closest('tr')
      expect(row).not.toBeNull()
      expect(within(row ?? table).getByText('API-1')).toBeInTheDocument()
      expect(within(row ?? table).getByText('Backend')).toBeInTheDocument()
      const plan = within(table).getByRole('link', { name: 'Write the plan' }).closest('tr')
      expect(within(plan ?? table).getByText('Acme app')).toBeInTheDocument()
    })

    it('asks for a project first when the workspace has none', async () => {
      fakeServer({
        ...baseHandlers([]),
        [`GET /api/workspaces/${ACME.id}/projects?includeArchived=true`]: () => json(200, []),
        [ACTIVE_PROJECTS]: () => json(200, []),
      })
      renderRoutes(routes, ['/w/acme/issues'])
      const user = userEvent.setup()

      const link = await screen.findByRole('link', { name: 'Create a project first' })
      expect(link).toHaveAttribute('href', '/w/acme/projects')
      expect(screen.queryByRole('region', { name: 'To do' })).not.toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Create issue' }))
      const dialog = await screen.findByRole('dialog', { name: 'Create issue' })
      expect(await within(dialog).findByRole('link', { name: 'Create a project' })).toHaveAttribute('href', '/w/acme/projects')
      expect(within(dialog).getByRole('button', { name: 'Create' })).toBeDisabled()
    })

    it('starts the create dialog in the project used last, and lets the person pick another', async () => {
      useIssueViewStore.setState({ lastProjectByWorkspace: { [ACME.id]: API.id } })
      const created = issue({ id: issueId(9), number: 10, title: 'Elsewhere' })
      const server = fakeServer({
        ...baseHandlers(),
        [ACTIVE_PROJECTS]: () => json(200, [API, ACM_PROJECT]),
        [`POST ${ISSUES_PATH}`]: () => json(201, created),
      })
      renderRoutes(routes, ['/w/acme/issues'])
      const user = userEvent.setup()
      await screen.findByText('Write the plan')

      await user.click(screen.getByRole('button', { name: 'Create issue' }))
      const dialog = await screen.findByRole('dialog', { name: 'Create issue' })
      const picker = within(dialog).getByRole('combobox', { name: 'Project' })
      await waitFor(() => expect(picker).toHaveTextContent('Backend'))
      await user.click(picker)
      await user.click(await screen.findByRole('option', { name: /Acme app/ }))
      await user.type(within(dialog).getByLabelText('Title'), 'Elsewhere')
      await user.click(within(dialog).getByRole('button', { name: 'Create' }))

      await waitFor(() => expect(server.calls(`POST ${ISSUES_PATH}`)).toHaveLength(1))
      expect(server.calls(`POST ${ISSUES_PATH}`)[0]?.body).toMatchObject({ projectId: ACM_PROJECT.id })
      expect(await screen.findByText('ACM-10 was created')).toBeInTheDocument()
      expect(useIssueViewStore.getState().lastProjectByWorkspace[ACME.id]).toBe(ACM_PROJECT.id)
    })
  })

  describe('creating an issue', () => {
    it('checks the title and creates the issue with the chosen details', async () => {
      const created = issue({
        id: issueId(9),
        number: 9,
        title: 'New work',
        priority: 'high',
        assignee: { type: 'member', id: MIA.userId, name: 'Mia' },
      })
      let items = ISSUES
      const server = fakeServer({
        ...baseHandlers(),
        [LIST]: () => json(200, { items, total: items.length }),
        [`POST ${ISSUES_PATH}`]: () => {
          items = [...ISSUES, created]
          return json(201, created)
        },
      })
      renderRoutes(routes, ['/w/acme/issues'])
      const user = userEvent.setup()
      await screen.findByText('Write the plan')

      await user.click(screen.getByRole('button', { name: 'Create issue' }))
      const dialog = await screen.findByRole('dialog', { name: 'Create issue' })
      await user.click(within(dialog).getByRole('button', { name: 'Create' }))
      expect(await within(dialog).findByText('Enter a title')).toBeInTheDocument()
      expect(server.calls(`POST ${ISSUES_PATH}`)).toHaveLength(0)

      await user.type(within(dialog).getByLabelText('Title'), '  New work  ')
      await user.type(within(dialog).getByLabelText('Description'), 'Some details')
      await user.click(within(dialog).getByRole('combobox', { name: 'Priority' }))
      await user.click(await screen.findByRole('option', { name: 'High' }))
      await user.click(within(dialog).getByRole('combobox', { name: 'Assignee' }))
      await user.click(await screen.findByRole('option', { name: 'Mia' }))
      await user.click(within(dialog).getByRole('button', { name: 'Create' }))

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(server.calls(`POST ${ISSUES_PATH}`)[0]?.body).toEqual({
        projectId: ACM_PROJECT.id,
        title: 'New work',
        description: 'Some details',
        status: 'todo',
        priority: 'high',
        assignee: { type: 'member', id: MIA.userId },
      })
      expect(await screen.findByText('ACM-9 was created')).toBeInTheDocument()
      expect(await screen.findByRole('link', { name: 'New work' })).toBeInTheDocument()
    })

    it('starts in the column whose plus button was used and sends no assignee when none is chosen', async () => {
      const created = issue({ id: issueId(9), number: 9, title: 'Started', status: 'in_progress' })
      const server = fakeServer({ ...baseHandlers(), [`POST ${ISSUES_PATH}`]: () => json(201, created) })
      renderRoutes(routes, ['/w/acme/issues'])
      const user = userEvent.setup()
      await screen.findByText('Write the plan')

      await user.click(screen.getByRole('button', { name: 'Create issue in In progress' }))
      const dialog = await screen.findByRole('dialog', { name: 'Create issue' })
      expect(within(dialog).getByRole('combobox', { name: 'Status' })).toHaveTextContent('In progress')
      await user.type(within(dialog).getByLabelText('Title'), 'Started')
      await user.click(within(dialog).getByRole('button', { name: 'Create' }))

      await waitFor(() => expect(server.calls(`POST ${ISSUES_PATH}`)).toHaveLength(1))
      const body = server.calls(`POST ${ISSUES_PATH}`)[0]?.body
      expect(body).toMatchObject({ title: 'Started', status: 'in_progress', priority: 'none' })
      expect(body).not.toHaveProperty('assignee')
    })

    it('assigns a new issue to an agent', async () => {
      AGENTS_LIST = [agent({ id: agentId(1), name: 'Builder' })]
      const created = issue({
        id: issueId(9),
        number: 9,
        title: 'For the bot',
        assignee: { type: 'agent', id: agentId(1), name: 'Builder' },
      })
      const server = fakeServer({ ...baseHandlers(), [`POST ${ISSUES_PATH}`]: () => json(201, created) })
      renderRoutes(routes, ['/w/acme/issues'])
      const user = userEvent.setup()
      await screen.findByText('Write the plan')

      await user.click(screen.getByRole('button', { name: 'Create issue' }))
      const dialog = await screen.findByRole('dialog', { name: 'Create issue' })
      await user.type(within(dialog).getByLabelText('Title'), 'For the bot')
      await user.click(within(dialog).getByRole('combobox', { name: 'Assignee' }))
      await user.click(await screen.findByRole('option', { name: 'Builder' }))
      await user.click(within(dialog).getByRole('button', { name: 'Create' }))

      await waitFor(() => expect(server.calls(`POST ${ISSUES_PATH}`)).toHaveLength(1))
      expect(server.calls(`POST ${ISSUES_PATH}`)[0]?.body).toMatchObject({
        title: 'For the bot',
        assignee: { type: 'agent', id: agentId(1) },
      })
    })

    it('shows the server error and keeps the dialog open', async () => {
      fakeServer({
        ...baseHandlers(),
        [`POST ${ISSUES_PATH}`]: () => problem(400, { detail: 'Title must be a single line of text', code: 'issue.invalid_title' }),
      })
      renderRoutes(routes, ['/w/acme/issues'])
      const user = userEvent.setup()
      await screen.findByText('Write the plan')

      await user.click(screen.getByRole('button', { name: 'Create issue' }))
      const dialog = await screen.findByRole('dialog', { name: 'Create issue' })
      await user.type(within(dialog).getByLabelText('Title'), 'Bad')
      await user.click(within(dialog).getByRole('button', { name: 'Create' }))

      expect(await within(dialog).findByText('Title must be a single line of text')).toBeInTheDocument()
      expect(screen.getByRole('dialog')).toBeInTheDocument()
    })

    it('opens empty every time', async () => {
      fakeServer(baseHandlers())
      renderRoutes(routes, ['/w/acme/issues'])
      const user = userEvent.setup()
      await screen.findByText('Write the plan')

      await user.click(screen.getByRole('button', { name: 'Create issue' }))
      await user.type(await screen.findByLabelText('Title'), 'Half written')
      await user.click(screen.getByRole('button', { name: 'Cancel' }))
      await user.click(screen.getByRole('button', { name: 'Create issue' }))

      expect(await screen.findByLabelText('Title')).toHaveValue('')
    })
  })
})
