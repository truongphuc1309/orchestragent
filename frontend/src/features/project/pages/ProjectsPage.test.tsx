import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACME, ADA, MIA, project, projectId } from '@/test/fixtures'
import { fakeServer, json, problem, type RecordedRequest } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import type { Project } from '../schemas'

const BASE = `/api/workspaces/${ACME.id}/projects`
const ACTIVE = `GET ${BASE}`
const ARCHIVED_TOO = `GET ${BASE}?includeArchived=true`

const WEB = project({ id: projectId(1), key: 'WEB', name: 'Web app', description: 'The pages people click' })
const API = project({ id: projectId(2), key: 'API', name: 'Backend', createdById: MIA.userId, createdByName: 'Mia' })
const OLD = project({ id: projectId(3), key: 'OLD', name: 'Old site', archived: true, archivedAt: '2026-01-09T10:00:00Z' })

type Handler = (request: RecordedRequest) => Response | Promise<Response>

function handlers(
  options: { projects?: Project[]; workspace?: typeof ACME } = {},
  extra: Record<string, Handler> = {},
): Record<string, Handler> {
  const { projects = [API, WEB, OLD], workspace = ACME } = options
  return {
    'GET /api/me': () => json(200, ADA),
    'GET /api/workspaces': () => json(200, [workspace]),
    [ACTIVE]: () => json(200, projects.filter((candidate) => !candidate.archived)),
    [ARCHIVED_TOO]: () => json(200, projects),
    ...extra,
  }
}

async function openProjects() {
  const view = renderRoutes(routes, ['/w/acme/projects'])
  await screen.findByRole('heading', { name: 'Projects' })
  return view
}

/** The table row of a project (the navigation links to projects too, so look in the table only). */
function rowOf(name: string): HTMLElement {
  const row = within(screen.getByRole('table', { name: 'Projects' })).getByRole('link', { name }).closest('tr')
  if (row === null) {
    throw new Error(`No row for ${name}`)
  }
  return row
}

describe('projects page', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('lists the active projects with their key and creator, linked to their page', async () => {
    fakeServer(handlers())
    await openProjects()

    const table = await screen.findByRole('table', { name: 'Projects' })
    expect(within(table).getByRole('columnheader', { name: 'Key' })).toBeInTheDocument()
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows.map((row) => within(row).getAllByRole('cell')[0]?.textContent)).toEqual(['API', 'WEB'])
    expect(within(rowOf('Web app')).getByText('The pages people click')).toBeInTheDocument()
    expect(within(rowOf('Backend')).getByText('Mia')).toBeInTheDocument()
    expect(within(table).getByRole('link', { name: 'Web app' })).toHaveAttribute('href', '/w/acme/projects/WEB')
    expect(screen.queryByText('Old site')).not.toBeInTheDocument()
  })

  it('lists the active projects in the navigation', async () => {
    fakeServer(handlers())
    await openProjects()

    const nav = screen.getByRole('navigation', { name: 'Workspace' })
    expect(await within(nav).findByRole('link', { name: 'Web app' })).toHaveAttribute('href', '/w/acme/projects/WEB')
    expect(within(nav).getByRole('link', { name: 'Backend' })).toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: 'Old site' })).not.toBeInTheDocument()
  })

  it('shows archived projects on request, marked as such', async () => {
    const server = fakeServer(handlers())
    await openProjects()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Show archived' }))

    await waitFor(() => expect(server.calls(ARCHIVED_TOO)).toHaveLength(1))
    expect(await screen.findByRole('link', { name: 'Old site' })).toBeInTheDocument()
    expect(within(rowOf('Old site')).getByText('Archived')).toBeInTheDocument()
  })

  it('says so when there is no project yet', async () => {
    fakeServer(handlers({ projects: [] }))
    await openProjects()

    expect(await screen.findByText('No projects yet. Create the first one.')).toBeInTheDocument()
  })

  it('offers a retry when the projects cannot be loaded', async () => {
    let healthy = false
    fakeServer(handlers({}, { [ACTIVE]: () => (healthy ? json(200, [WEB]) : problem(500, {})) }))
    await openProjects()
    const user = userEvent.setup()

    expect(await screen.findByText('Could not load the projects.')).toBeInTheDocument()
    healthy = true
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('table', { name: 'Projects' }, { timeout: 3000 })).toBeInTheDocument()
  })

  describe('creating', () => {
    it('suggests a key from the name, sends the form and opens the new project', async () => {
      const created = project({ id: projectId(9), key: 'MA', name: 'Mobile app' })
      const server = fakeServer(
        handlers({}, {
          [`POST ${BASE}`]: () => json(201, created),
          [`GET ${BASE}/key/MA`]: () => json(200, created),
          [`GET /api/workspaces/${ACME.id}/issues?project=${created.id}&limit=500`]: () => json(200, { items: [], total: 0 }),
          [`GET /api/workspaces/${ACME.id}/members`]: () => json(200, []),
          [`GET /api/workspaces/${ACME.id}/agents`]: () => json(200, []),
        }),
      )
      const { router } = await openProjects()
      const user = userEvent.setup()

      await user.click(screen.getByRole('button', { name: 'Create project' }))
      const dialog = await screen.findByRole('dialog', { name: 'Create project' })
      await user.type(within(dialog).getByLabelText('Name'), 'Mobile app')
      expect(within(dialog).getByLabelText('Key')).toHaveValue('MA')
      await user.type(within(dialog).getByLabelText('Description (optional)'), 'Phones')
      await user.click(within(dialog).getByRole('button', { name: 'Create' }))

      await waitFor(() => expect(server.calls(`POST ${BASE}`)).toHaveLength(1))
      expect(server.calls(`POST ${BASE}`)[0]?.body).toEqual({ key: 'MA', name: 'Mobile app', description: 'Phones' })
      expect(await screen.findByText('Mobile app (MA) was created')).toBeInTheDocument()
      await waitFor(() => expect(router.state.location.pathname).toBe('/w/acme/projects/MA'))
    })

    it('keeps a key the person typed, upper-cased, when the name changes afterwards', async () => {
      const server = fakeServer(handlers({}, { [`POST ${BASE}`]: () => json(201, WEB) }))
      await openProjects()
      const user = userEvent.setup()

      await user.click(screen.getByRole('button', { name: 'Create project' }))
      const dialog = await screen.findByRole('dialog', { name: 'Create project' })
      await user.type(within(dialog).getByLabelText('Key'), 'web2')
      await user.type(within(dialog).getByLabelText('Name'), 'Website')
      await user.click(within(dialog).getByRole('button', { name: 'Create' }))

      await waitFor(() => expect(server.calls(`POST ${BASE}`)).toHaveLength(1))
      expect(server.calls(`POST ${BASE}`)[0]?.body).toMatchObject({ key: 'WEB2', name: 'Website' })
    })

    it('refuses a key that is not well formed without asking the server', async () => {
      const server = fakeServer(handlers())
      await openProjects()
      const user = userEvent.setup()

      await user.click(screen.getByRole('button', { name: 'Create project' }))
      const dialog = await screen.findByRole('dialog', { name: 'Create project' })
      await user.type(within(dialog).getByLabelText('Key'), '1WEB')
      await user.click(within(dialog).getByRole('button', { name: 'Create' }))

      expect(await within(dialog).findByText('Use 2 to 10 letters and digits, starting with a letter')).toBeInTheDocument()
      expect(await within(dialog).findByText('Enter a name for the project')).toBeInTheDocument()
      expect(server.calls(`POST ${BASE}`)).toHaveLength(0)
    })

    it('shows the server error, for example a key that is taken, and keeps the dialog open', async () => {
      fakeServer(
        handlers({}, {
          [`POST ${BASE}`]: () =>
            problem(409, { code: 'project.key_taken', detail: 'Another project of this workspace already uses the key WEB' }),
        }),
      )
      await openProjects()
      const user = userEvent.setup()

      await user.click(screen.getByRole('button', { name: 'Create project' }))
      const dialog = await screen.findByRole('dialog', { name: 'Create project' })
      await user.type(within(dialog).getByLabelText('Name'), 'Web')
      await user.clear(within(dialog).getByLabelText('Key'))
      await user.type(within(dialog).getByLabelText('Key'), 'WEB')
      await user.click(within(dialog).getByRole('button', { name: 'Create' }))

      expect(await within(dialog).findByText('Another project of this workspace already uses the key WEB')).toBeInTheDocument()
      expect(screen.getByRole('dialog', { name: 'Create project' })).toBeInTheDocument()

      // The message was about the key that was sent: typing another one takes it away.
      await user.type(within(dialog).getByLabelText('Key'), '2')
      expect(within(dialog).queryByText('Another project of this workspace already uses the key WEB')).not.toBeInTheDocument()
    })
  })

  describe('changing', () => {
    it('edits the name and description but never the key', async () => {
      const server = fakeServer(
        handlers({}, { [`PATCH ${BASE}/${WEB.id}`]: () => json(200, { ...WEB, name: 'Website' }) }),
      )
      await openProjects()
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Edit Web app' }))
      const dialog = await screen.findByRole('dialog', { name: 'Edit project' })
      const key = within(dialog).getByLabelText('Key')
      expect(key).toHaveValue('WEB')
      expect(key).toHaveAttribute('readonly')
      await user.clear(within(dialog).getByLabelText('Name'))
      await user.type(within(dialog).getByLabelText('Name'), 'Website')
      await user.click(within(dialog).getByRole('button', { name: 'Save' }))

      await waitFor(() => expect(server.calls(`PATCH ${BASE}/${WEB.id}`)).toHaveLength(1))
      expect(server.calls(`PATCH ${BASE}/${WEB.id}`)[0]?.body).toEqual({
        name: 'Website',
        description: 'The pages people click',
      })
      expect(await screen.findByText('Website was saved')).toBeInTheDocument()
    })

    it('archives after asking, and restores from the archived view', async () => {
      let projects = [API, WEB, OLD]
      const server = fakeServer(
        handlers({}, {
          [ACTIVE]: () => json(200, projects.filter((candidate) => !candidate.archived)),
          [ARCHIVED_TOO]: () => json(200, projects),
          [`POST ${BASE}/${WEB.id}/archive`]: () => {
            projects = projects.map((candidate) => (candidate.id === WEB.id ? { ...WEB, archived: true } : candidate))
            return json(200, { ...WEB, archived: true })
          },
          [`POST ${BASE}/${OLD.id}/restore`]: () => json(200, { ...OLD, archived: false }),
        }),
      )
      await openProjects()
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Archive Web app' }))
      const confirm = await screen.findByRole('dialog', { name: 'Archive Web app?' })
      await user.click(within(confirm).getByRole('button', { name: 'Archive' }))
      await waitFor(() => expect(server.calls(`POST ${BASE}/${WEB.id}/archive`)).toHaveLength(1))
      expect(await screen.findByText('Web app was archived')).toBeInTheDocument()
      await waitFor(() =>
        expect(within(screen.getByRole('table', { name: 'Projects' })).queryByRole('link', { name: 'Web app' })).toBeNull(),
      )

      await user.click(screen.getByRole('button', { name: 'Show archived' }))
      await user.click(await screen.findByRole('button', { name: 'Restore Old site' }))
      await waitFor(() => expect(server.calls(`POST ${BASE}/${OLD.id}/restore`)).toHaveLength(1))
      expect(await screen.findByText('Old site was restored')).toBeInTheDocument()
    })

    it('offers no changes to a member on a project someone else made', async () => {
      fakeServer(handlers({ workspace: { ...ACME, role: 'member' } }))
      await openProjects()

      await screen.findByRole('table', { name: 'Projects' })
      // Ada made WEB herself, so she may change it; Mia made API.
      expect(within(rowOf('Web app')).getByRole('button', { name: 'Edit Web app' })).toBeInTheDocument()
      expect(within(rowOf('Backend')).queryByRole('button')).toBeNull()
    })

    it('lets an admin change any project', async () => {
      fakeServer(handlers({ workspace: { ...ACME, role: 'admin' } }))
      await openProjects()

      expect(await screen.findByRole('button', { name: 'Edit Backend' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Archive Backend' })).toBeInTheDocument()
    })
  })
})
