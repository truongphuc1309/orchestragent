import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACM_PROJECT, ACME, ADA, agent, agentId, comment, issue, issueId, member, MIA } from '@/test/fixtures'
import { fakeServer, json, noContent, problem, type RecordedRequest } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import type { Actor, Issue } from '../schemas'

const MEMBERS = [member({ userId: ADA.id, name: 'ada', role: 'owner' }), member({ userId: MIA.userId, name: 'Mia' })]
const BASE = `/api/workspaces/${ACME.id}/issues`
const AGENTS = `GET /api/workspaces/${ACME.id}/agents`
const BUILDER = agent({ id: agentId(1), name: 'Builder' })
/** The active agents of the workspace; tests that need none override the handler. */
const AGENT_LIST = [BUILDER]

const ISSUE: Issue = issue({
  id: issueId(7),
  number: 7,
  title: 'Fix the login',
  description: 'Users cannot sign in.\nSecond line.',
  status: 'in_progress',
  priority: 'high',
  assignee: { type: 'member', id: MIA.userId, name: 'Mia' },
})
const COMMENTS = [
  comment({ id: '0198f1c0-0000-7000-8000-0000000002a1', issueId: ISSUE.id, body: 'First thoughts' }),
  comment({
    id: '0198f1c0-0000-7000-8000-0000000002a2',
    issueId: ISSUE.id,
    body: 'The agent finished',
    author: { type: 'system', id: null, name: 'System' },
  }),
]

/** True for the body of a request that unassigns the issue: {"assignee": null}. */
function clearsAssignee(body: unknown): boolean {
  return typeof body === 'object' && body !== null && 'assignee' in body && body.assignee === null
}

function handlers(overrides: Record<string, (request: RecordedRequest) => Response | Promise<Response>> = {}, workspace = ACME) {
  return {
    'GET /api/me': () => json(200, ADA),
    'GET /api/workspaces': () => json(200, [workspace]),
    [`GET /api/workspaces/${ACME.id}/members`]: () => json(200, MEMBERS),
    [AGENTS]: () => json(200, AGENT_LIST),
    [`GET /api/workspaces/${ACME.id}/tasks?issueId=${ISSUE.id}`]: () => json(200, []),
    [`GET ${BASE}/key/ACM-7`]: () => json(200, ISSUE),
    [`GET ${BASE}/${ISSUE.id}/comments`]: () => json(200, COMMENTS),
    [`GET /api/workspaces/${ACME.id}/projects?includeArchived=true`]: () => json(200, [ACM_PROJECT]),
    ...overrides,
  }
}

async function openIssue(path = '/w/acme/issues/ACM-7') {
  const view = renderRoutes(routes, [path])
  await screen.findByRole('heading', { name: 'Fix the login' })
  return view
}

describe('issue detail page', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  describe('showing the issue', () => {
    it('shows the key, title, description, properties and comments', async () => {
      fakeServer(handlers())
      await openIssue()

      expect(screen.getByText('ACM-7')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Edit description' })).toHaveTextContent('Users cannot sign in.')
      const details = screen.getByRole('complementary', { name: 'Details' })
      expect(within(details).getByRole('combobox', { name: 'Status' })).toHaveTextContent('In progress')
      expect(within(details).getByRole('combobox', { name: 'Priority' })).toHaveTextContent('High')
      expect(within(details).getByRole('combobox', { name: 'Assignee' })).toHaveTextContent('Mia')
      expect(within(details).getByText('ada')).toBeInTheDocument()
      const comments = await screen.findByRole('list', { name: 'Comments' })
      expect(within(comments).getAllByRole('listitem')).toHaveLength(2)
      expect(within(comments).getByText('First thoughts')).toBeInTheDocument()
      expect(within(comments).getByText('System')).toBeInTheDocument()
    })

    it('links back to the list of issues', async () => {
      fakeServer(handlers())
      await openIssue()

      expect(within(screen.getByRole('main')).getByRole('link', { name: 'Issues' })).toHaveAttribute('href', '/w/acme/issues')
    })

    it('shows "No comments yet." for an issue without comments', async () => {
      fakeServer(handlers({ [`GET ${BASE}/${ISSUE.id}/comments`]: () => json(200, []) }))
      await openIssue()

      expect(await screen.findByText('No comments yet.')).toBeInTheDocument()
    })

    it.each([
      ['an issue that does not exist', '/w/acme/issues/ACM-99'],
      ['an issue key that is not well formed', '/w/acme/issues/acm-7'],
      ['a bare number, as issue links were before projects', '/w/acme/issues/7'],
    ])('says so for %s', async (_name, path) => {
      const server = fakeServer(handlers({ [`GET ${BASE}/key/ACM-99`]: () => problem(404, { code: 'issue.not_found' }) }))
      renderRoutes(routes, [path])

      expect(await screen.findByRole('heading', { name: 'Issue not found' })).toBeInTheDocument()
      expect(server.requests.filter((request) => request.path.includes('/key/') && !request.path.endsWith('/key/ACM-99'))).toEqual([])
    })

    it('offers a retry when loading fails for another reason', async () => {
      let healthy = false
      fakeServer(handlers({ [`GET ${BASE}/key/ACM-7`]: () => (healthy ? json(200, ISSUE) : problem(500, {})) }))
      renderRoutes(routes, ['/w/acme/issues/ACM-7'])
      const user = userEvent.setup()

      expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the issue')
      healthy = true
      await user.click(screen.getByRole('button', { name: 'Try again' }))

      expect(await screen.findByRole('heading', { name: 'Fix the login' })).toBeInTheDocument()
    })
  })

  describe('editing', () => {
    it('saves a new title with Enter, once', async () => {
      const server = fakeServer(
        handlers({ [`PATCH ${BASE}/${ISSUE.id}`]: () => json(200, { ...ISSUE, title: 'Fix the sign-in' }) }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.click(screen.getByRole('button', { name: 'Edit title' }))
      const input = screen.getByRole('textbox', { name: 'Title' })
      await user.clear(input)
      await user.type(input, '  Fix the sign-in  {Enter}')

      expect(await screen.findByRole('heading', { name: 'Fix the sign-in' })).toBeInTheDocument()
      expect(server.calls(`PATCH ${BASE}/${ISSUE.id}`)).toHaveLength(1)
      expect(server.calls(`PATCH ${BASE}/${ISSUE.id}`)[0]?.body).toEqual({ title: 'Fix the sign-in' })
    })

    it('saves the title when the field loses focus', async () => {
      const server = fakeServer(handlers({ [`PATCH ${BASE}/${ISSUE.id}`]: () => json(200, { ...ISSUE, title: 'Other' }) }))
      await openIssue()
      const user = userEvent.setup()

      await user.click(screen.getByRole('button', { name: 'Edit title' }))
      await user.clear(screen.getByRole('textbox', { name: 'Title' }))
      await user.type(screen.getByRole('textbox', { name: 'Title' }), 'Other')
      await user.click(screen.getByText('Comments'))

      await waitFor(() => expect(server.calls(`PATCH ${BASE}/${ISSUE.id}`)).toHaveLength(1))
    })

    it('throws the edit away on Escape', async () => {
      const server = fakeServer(handlers())
      await openIssue()
      const user = userEvent.setup()

      await user.click(screen.getByRole('button', { name: 'Edit title' }))
      await user.type(screen.getByRole('textbox', { name: 'Title' }), ' extra{Escape}')

      expect(screen.getByRole('heading', { name: 'Fix the login' })).toBeInTheDocument()
      expect(server.requests.filter((request) => request.method === 'PATCH')).toEqual([])
    })

    it('does not send an empty title or an unchanged one', async () => {
      const server = fakeServer(handlers())
      await openIssue()
      const user = userEvent.setup()

      await user.click(screen.getByRole('button', { name: 'Edit title' }))
      await user.clear(screen.getByRole('textbox', { name: 'Title' }))
      expect(await screen.findByRole('alert')).toHaveTextContent('Enter a title')
      await user.type(screen.getByRole('textbox', { name: 'Title' }), '{Enter}')
      expect(screen.getByRole('heading', { name: 'Fix the login' })).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Edit title' }))
      await user.type(screen.getByRole('textbox', { name: 'Title' }), '{Enter}')

      expect(server.requests.filter((request) => request.method === 'PATCH')).toEqual([])
    })

    it('saves a new description and keeps the line breaks', async () => {
      const server = fakeServer(
        handlers({ [`PATCH ${BASE}/${ISSUE.id}`]: () => json(200, { ...ISSUE, description: 'New\ntext' }) }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.click(screen.getByRole('button', { name: 'Edit description' }))
      const box = screen.getByRole('textbox', { name: 'Description' })
      await user.clear(box)
      await user.type(box, 'New{Enter}text')
      await user.click(screen.getByRole('button', { name: 'Save' }))

      await waitFor(() => expect(server.calls(`PATCH ${BASE}/${ISSUE.id}`)).toHaveLength(1))
      expect(server.calls(`PATCH ${BASE}/${ISSUE.id}`)[0]?.body).toEqual({ description: 'New\ntext' })
      expect(await screen.findByRole('button', { name: 'Edit description' })).toHaveTextContent('New')
    })

    it('cancels a description edit without a request', async () => {
      const server = fakeServer(handlers())
      await openIssue()
      const user = userEvent.setup()

      await user.click(screen.getByRole('button', { name: 'Edit description' }))
      await user.type(screen.getByRole('textbox', { name: 'Description' }), ' more')
      await user.click(screen.getByRole('button', { name: 'Cancel' }))

      expect(screen.getByRole('button', { name: 'Edit description' })).toHaveTextContent('Users cannot sign in.')
      expect(server.requests.filter((request) => request.method === 'PATCH')).toEqual([])
    })

    it('tells the user when a save fails', async () => {
      fakeServer(
        handlers({ [`PATCH ${BASE}/${ISSUE.id}`]: () => problem(400, { detail: 'Title must be a single line of text' }) }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.click(screen.getByRole('button', { name: 'Edit title' }))
      await user.type(screen.getByRole('textbox', { name: 'Title' }), ' again{Enter}')

      expect(await screen.findByText('Title must be a single line of text')).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: 'Fix the login' })).toBeInTheDocument()
    })
  })

  describe('properties', () => {
    it('changes the status', async () => {
      const server = fakeServer(handlers({ [`PATCH ${BASE}/${ISSUE.id}`]: () => json(200, { ...ISSUE, status: 'done' }) }))
      await openIssue()
      const user = userEvent.setup()

      await user.click(screen.getByRole('combobox', { name: 'Status' }))
      await user.click(await screen.findByRole('option', { name: 'Done' }))

      await waitFor(() => expect(server.calls(`PATCH ${BASE}/${ISSUE.id}`)).toHaveLength(1))
      expect(server.calls(`PATCH ${BASE}/${ISSUE.id}`)[0]?.body).toEqual({ status: 'done' })
      await waitFor(() => expect(screen.getByRole('combobox', { name: 'Status' })).toHaveTextContent('Done'))
    })

    it('changes the priority', async () => {
      const server = fakeServer(handlers({ [`PATCH ${BASE}/${ISSUE.id}`]: () => json(200, { ...ISSUE, priority: 'urgent' }) }))
      await openIssue()
      const user = userEvent.setup()

      await user.click(screen.getByRole('combobox', { name: 'Priority' }))
      await user.click(await screen.findByRole('option', { name: 'Urgent' }))

      await waitFor(() => expect(server.calls(`PATCH ${BASE}/${ISSUE.id}`)[0]?.body).toEqual({ priority: 'urgent' }))
    })

    it('offers members and agents in separate groups', async () => {
      fakeServer(handlers())
      await openIssue()
      const user = userEvent.setup()

      await user.click(screen.getByRole('combobox', { name: 'Assignee' }))

      const listbox = await screen.findByRole('listbox')
      expect(within(listbox).getByText('Members')).toBeInTheDocument()
      expect(within(listbox).getByText('Agents')).toBeInTheDocument()
      expect(within(listbox).getAllByRole('option').map((option) => option.textContent)).toEqual([
        'Unassigned',
        'ada',
        'Mia',
        'Builder',
      ])
    })

    it('assigns the issue to an agent', async () => {
      const server = fakeServer(
        handlers({
          [`PUT ${BASE}/${ISSUE.id}/assignee`]: () =>
            json(200, { ...ISSUE, assignee: { type: 'agent', id: BUILDER.id, name: 'Builder' } }),
        }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.click(screen.getByRole('combobox', { name: 'Assignee' }))
      await user.click(await screen.findByRole('option', { name: 'Builder' }))

      await waitFor(() => expect(server.calls(`PUT ${BASE}/${ISSUE.id}/assignee`)).toHaveLength(1))
      expect(server.calls(`PUT ${BASE}/${ISSUE.id}/assignee`)[0]?.body).toEqual({
        assignee: { type: 'agent', id: BUILDER.id },
      })
      await waitFor(() => expect(screen.getByRole('combobox', { name: 'Assignee' })).toHaveTextContent('Builder'))
    })

    it('shows an agent that is already assigned, even one that has since been archived', async () => {
      const archivedBot: Actor = { type: 'agent', id: agentId(7), name: 'Old bot' }
      fakeServer(handlers({ [`GET ${BASE}/key/ACM-7`]: () => json(200, { ...ISSUE, assignee: archivedBot }) }))
      await openIssue()

      // The archived agent is not among the active agents the picker offers, but its name is still shown.
      expect(screen.getByRole('combobox', { name: 'Assignee' })).toHaveTextContent('Old bot')
    })

    it('assigns to another member and unassigns', async () => {
      const server = fakeServer(
        handlers({
          [`PUT ${BASE}/${ISSUE.id}/assignee`]: (request) =>
            json(200, { ...ISSUE, assignee: clearsAssignee(request.body) ? null : { type: 'member', id: ADA.id, name: 'ada' } }),
        }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.click(screen.getByRole('combobox', { name: 'Assignee' }))
      await user.click(await screen.findByRole('option', { name: 'ada' }))
      await waitFor(() => expect(server.calls(`PUT ${BASE}/${ISSUE.id}/assignee`)).toHaveLength(1))
      expect(server.calls(`PUT ${BASE}/${ISSUE.id}/assignee`)[0]?.body).toEqual({
        assignee: { type: 'member', id: ADA.id },
      })

      await user.click(screen.getByRole('combobox', { name: 'Assignee' }))
      await user.click(await screen.findByRole('option', { name: 'Unassigned' }))
      await waitFor(() => expect(server.calls(`PUT ${BASE}/${ISSUE.id}/assignee`)).toHaveLength(2))
      expect(server.calls(`PUT ${BASE}/${ISSUE.id}/assignee`)[1]?.body).toEqual({ assignee: null })
    })
  })

  describe('comments', () => {
    it('adds a comment and shows it', async () => {
      let comments = COMMENTS
      const added = comment({ id: '0198f1c0-0000-7000-8000-0000000002a3', issueId: ISSUE.id, body: 'Looks good' })
      const server = fakeServer(
        handlers({
          [`GET ${BASE}/${ISSUE.id}/comments`]: () => json(200, comments),
          [`POST ${BASE}/${ISSUE.id}/comments`]: () => {
            comments = [...COMMENTS, added]
            return json(201, added)
          },
        }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.type(screen.getByLabelText('Add a comment'), '  Looks good ')
      await user.click(screen.getByRole('button', { name: 'Comment' }))

      expect(await screen.findByText('Looks good', { selector: 'p' })).toBeInTheDocument()
      expect(server.calls(`POST ${BASE}/${ISSUE.id}/comments`)[0]?.body).toEqual({ body: 'Looks good' })
      expect(screen.getByLabelText('Add a comment')).toHaveValue('')
    })

    it('sends with Ctrl+Enter', async () => {
      const server = fakeServer(
        handlers({
          [`POST ${BASE}/${ISSUE.id}/comments`]: () =>
            json(201, comment({ id: '0198f1c0-0000-7000-8000-0000000002a3', issueId: ISSUE.id, body: 'Quick' })),
        }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.type(screen.getByLabelText('Add a comment'), 'Quick{Control>}{Enter}{/Control}')

      await waitFor(() => expect(server.calls(`POST ${BASE}/${ISSUE.id}/comments`)).toHaveLength(1))
    })

    it('does not send a blank comment', async () => {
      const server = fakeServer(handlers())
      await openIssue()
      const user = userEvent.setup()

      await user.type(screen.getByLabelText('Add a comment'), '   ')
      await user.click(screen.getByRole('button', { name: 'Comment' }))

      expect(await screen.findByText('Write something first')).toBeInTheDocument()
      expect(server.requests.filter((request) => request.method === 'POST')).toEqual([])
    })

    it('shows the server error and keeps the text', async () => {
      fakeServer(
        handlers({ [`POST ${BASE}/${ISSUE.id}/comments`]: () => problem(500, { detail: 'Something broke' }) }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.type(screen.getByLabelText('Add a comment'), 'Keep me')
      await user.click(screen.getByRole('button', { name: 'Comment' }))

      expect(await screen.findByText('Something broke')).toBeInTheDocument()
      expect(screen.getByLabelText('Add a comment')).toHaveValue('Keep me')
    })
  })

  describe('deleting', () => {
    const OTHERS_ISSUE: Issue = { ...ISSUE, creator: { type: 'member', id: MIA.userId, name: 'Mia' } }
    const asMember = { ...ACME, role: 'member' as const }
    const asAdmin = { ...ACME, role: 'admin' as const }

    it('lets the creator delete after confirming, then returns to the list', async () => {
      const server = fakeServer(
        handlers({
          [`DELETE ${BASE}/${ISSUE.id}`]: () => noContent(),
          [`GET ${BASE}?limit=500`]: () => json(200, { items: [], total: 0 }),
        }),
      )
      const { router } = await openIssue()
      const user = userEvent.setup()

      await user.click(screen.getByRole('button', { name: 'Delete issue' }))
      const dialog = await screen.findByRole('dialog', { name: 'Delete ACM-7?' })
      expect(server.calls(`DELETE ${BASE}/${ISSUE.id}`)).toHaveLength(0)
      await user.click(within(dialog).getByRole('button', { name: 'Delete' }))

      await waitFor(() => expect(router.state.location.pathname).toBe('/w/acme/issues'))
      expect(server.calls(`DELETE ${BASE}/${ISSUE.id}`)).toHaveLength(1)
    })

    it('does nothing when the confirmation is cancelled', async () => {
      const server = fakeServer(handlers())
      await openIssue()
      const user = userEvent.setup()

      await user.click(screen.getByRole('button', { name: 'Delete issue' }))
      await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }))

      expect(server.requests.filter((request) => request.method === 'DELETE')).toEqual([])
    })

    it('hides delete from a plain member who did not create the issue', async () => {
      fakeServer(handlers({ [`GET ${BASE}/key/ACM-7`]: () => json(200, OTHERS_ISSUE) }, asMember))
      await openIssue()

      expect(screen.queryByRole('button', { name: 'Delete issue' })).not.toBeInTheDocument()
    })

    it('offers delete to an admin on any issue', async () => {
      fakeServer(handlers({ [`GET ${BASE}/key/ACM-7`]: () => json(200, OTHERS_ISSUE) }, asAdmin))
      await openIssue()

      expect(screen.getByRole('button', { name: 'Delete issue' })).toBeInTheDocument()
    })

    it('offers delete to a plain member on their own issue', async () => {
      fakeServer(handlers({}, asMember))
      await openIssue()

      expect(screen.getByRole('button', { name: 'Delete issue' })).toBeInTheDocument()
    })

    it('explains when the server refuses', async () => {
      fakeServer(
        handlers({
          [`DELETE ${BASE}/${ISSUE.id}`]: () => problem(403, { detail: 'Only the creator, an admin or an owner can delete this issue' }),
        }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.click(screen.getByRole('button', { name: 'Delete issue' }))
      await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Delete' }))

      expect(await screen.findByText('Only the creator, an admin or an owner can delete this issue')).toBeInTheDocument()
    })
  })
})
