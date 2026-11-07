import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACME, ADA, agent, agentId, MIA, runtime, runtimeId } from '@/test/fixtures'
import { fakeServer, json, problem, type RecordedRequest } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import type { Agent, Runtime } from '../schemas'

const BASE = `/api/workspaces/${ACME.id}`
const AGENTS = `GET ${BASE}/agents`
const ARCHIVED_TOO = `GET ${BASE}/agents?includeArchived=true`

const MY_LAPTOP = runtime({ id: runtimeId(1), deviceName: "Ada's laptop" })
const MY_SERVER = runtime({ id: runtimeId(3), deviceName: "Ada's server", provider: 'mock', status: 'offline' })
const MIAS_PC = runtime({ id: runtimeId(2), ownerId: MIA.userId, ownerName: 'Mia', deviceName: "Mia's PC" })

const BUILDER = agent({
  id: agentId(1),
  name: 'Builder',
  instructions: '# Rules\n\nWrite   clean code.',
  model: 'sonnet',
})
const REVIEWER = agent({
  id: agentId(2),
  name: 'Reviewer',
  runtime: { id: runtimeId(2), deviceName: "Mia's PC", provider: 'claude', status: 'online' },
  createdById: MIA.userId,
  createdByName: 'Mia',
})
const RETIRED = agent({ id: agentId(3), name: 'Retired', archived: true, archivedAt: '2026-01-07T10:00:00Z' })

type Handler = (request: RecordedRequest) => Response | Promise<Response>

function handlers(
  options: { agents?: Agent[]; runtimes?: Runtime[]; workspace?: typeof ACME } & Record<string, unknown> = {},
  extra: Record<string, Handler> = {},
) {
  const { agents = [BUILDER, REVIEWER], runtimes = [MY_LAPTOP, MY_SERVER, MIAS_PC], workspace = ACME } = options
  return {
    'GET /api/me': () => json(200, ADA),
    'GET /api/workspaces': () => json(200, [workspace]),
    [AGENTS]: () => json(200, agents.filter((candidate) => !candidate.archived)),
    [ARCHIVED_TOO]: () => json(200, agents),
    [`GET ${BASE}/runtimes`]: () => json(200, runtimes),
    ...extra,
  }
}

async function openAgents() {
  const view = renderRoutes(routes, ['/w/acme/agents'])
  await screen.findByRole('heading', { name: 'Agents' })
  return view
}

function cardOf(name: string): HTMLElement {
  const card = screen.getByRole('heading', { name }).closest('li')
  if (card === null) {
    throw new Error(`No card for ${name}`)
  }
  return card
}

describe('agents page', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  describe('the list', () => {
    it('shows each agent with where it runs, who made it and what it was told', async () => {
      fakeServer(handlers())
      await openAgents()

      const builder = await screen.findByRole('heading', { name: 'Builder' }).then(() => cardOf('Builder'))
      expect(builder).toHaveTextContent("on Ada's laptop · claude")
      expect(within(builder).getByText('Online')).toBeInTheDocument()
      expect(builder).toHaveTextContent('# Rules Write clean code.')
      expect(builder).toHaveTextContent('Created by ada')
      expect(builder).toHaveTextContent('model sonnet')
      expect(cardOf('Reviewer')).toHaveTextContent('Created by Mia')
      expect(cardOf('Reviewer')).not.toHaveTextContent('model')
    })

    it('shows when the computer of an agent is offline', async () => {
      const offline = agent({
        id: agentId(4),
        name: 'Sleepy',
        runtime: { id: runtimeId(3), deviceName: "Ada's server", provider: 'mock', status: 'offline' },
      })
      fakeServer(handlers({ agents: [offline] }))
      await openAgents()

      await screen.findByRole('heading', { name: 'Sleepy' })
      expect(within(cardOf('Sleepy')).getByText('Offline')).toBeInTheDocument()
    })

    it('says so when an agent has no instructions', async () => {
      fakeServer(handlers({ agents: [agent({ id: agentId(4), name: 'Blank', instructions: '' })] }))
      await openAgents()

      await screen.findByRole('heading', { name: 'Blank' })
      expect(cardOf('Blank')).toHaveTextContent('No instructions.')
    })

    it('invites the user to create the first agent', async () => {
      fakeServer(handlers({ agents: [] }))
      await openAgents()

      expect(await screen.findByText('No agents yet. Create the first one.')).toBeInTheDocument()
    })

    it('offers a retry when the agents cannot be loaded', async () => {
      let healthy = false
      fakeServer(handlers({}, { [AGENTS]: () => (healthy ? json(200, [BUILDER]) : problem(500, {})) }))
      await openAgents()
      const user = userEvent.setup()

      expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the agents')
      healthy = true
      await user.click(screen.getByRole('button', { name: 'Try again' }))

      expect(await screen.findByRole('heading', { name: 'Builder' })).toBeInTheDocument()
    })

    it('shows archived agents only on request, marked as archived', async () => {
      const server = fakeServer(handlers({ agents: [BUILDER, RETIRED] }))
      await openAgents()
      const user = userEvent.setup()
      await screen.findByRole('heading', { name: 'Builder' })
      expect(screen.queryByRole('heading', { name: 'Retired' })).not.toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Show archived' }))

      expect(await screen.findByRole('heading', { name: 'Retired' })).toBeInTheDocument()
      expect(server.calls(ARCHIVED_TOO)).toHaveLength(1)
      expect(within(cardOf('Retired')).getByText('Archived')).toBeInTheDocument()
      expect(within(cardOf('Retired')).getByRole('button', { name: 'Restore Retired' })).toBeInTheDocument()
      expect(within(cardOf('Retired')).queryByRole('button', { name: 'Edit Retired' })).not.toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Hide archived' }))
      await waitFor(() => expect(screen.queryByRole('heading', { name: 'Retired' })).not.toBeInTheDocument())
    })
  })

  describe('who may change an agent', () => {
    it('lets an owner change any agent', async () => {
      fakeServer(handlers())
      await openAgents()

      await screen.findByRole('heading', { name: 'Reviewer' })
      expect(within(cardOf('Reviewer')).getByRole('button', { name: 'Edit Reviewer' })).toBeInTheDocument()
      expect(within(cardOf('Builder')).getByRole('button', { name: 'Archive Builder' })).toBeInTheDocument()
    })

    it('lets a plain member change their own agent only', async () => {
      fakeServer(handlers({ workspace: { ...ACME, role: 'member' } }))
      await openAgents()

      await screen.findByRole('heading', { name: 'Reviewer' })
      // Builder was made by the signed-in user (ada), Reviewer by Mia.
      expect(within(cardOf('Builder')).getByRole('button', { name: 'Edit Builder' })).toBeInTheDocument()
      expect(within(cardOf('Reviewer')).queryByRole('button')).not.toBeInTheDocument()
    })

    it('lets an admin change any agent', async () => {
      fakeServer(handlers({ workspace: { ...ACME, role: 'admin' } }))
      await openAgents()

      await screen.findByRole('heading', { name: 'Reviewer' })
      expect(within(cardOf('Reviewer')).getByRole('button', { name: 'Edit Reviewer' })).toBeInTheDocument()
    })
  })

  describe('creating an agent', () => {
    it('offers only the user\'s own computers, preselects an online one, and creates the agent', async () => {
      let agents: Agent[] = []
      const created = agent({ id: agentId(9), name: 'Helper', model: 'opus' })
      const server = fakeServer(
        handlers(
          {},
          {
            [AGENTS]: () => json(200, agents),
            [`POST ${BASE}/agents`]: () => {
              agents = [created]
              return json(201, created)
            },
          },
        ),
      )
      await openAgents()
      const user = userEvent.setup()
      await screen.findByText('No agents yet. Create the first one.')

      await user.click(screen.getByRole('button', { name: 'Create agent' }))
      const dialog = await screen.findByRole('dialog', { name: 'Create agent' })
      const runtimeSelect = within(dialog).getByRole('combobox', { name: 'Runtime' })
      await waitFor(() => expect(runtimeSelect).toHaveTextContent("Ada's laptop · claude"))
      await user.click(runtimeSelect)
      const options = (await screen.findAllByRole('option')).map((option) => option.textContent)
      expect(options).toEqual(["Ada's laptop · claude", "Ada's server · mock (offline)"])
      await user.keyboard('{Escape}')

      await user.type(within(dialog).getByLabelText('Name'), '  Helper ')
      await user.type(within(dialog).getByLabelText('Model (optional)'), 'opus')
      await user.type(within(dialog).getByLabelText('Instructions'), 'Be helpful.')
      await user.click(within(dialog).getByRole('button', { name: 'Create' }))

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(server.calls(`POST ${BASE}/agents`)[0]?.body).toEqual({
        name: 'Helper',
        instructions: 'Be helpful.',
        runtimeId: runtimeId(1),
        model: 'opus',
      })
      expect(await screen.findByText('Helper was created')).toBeInTheDocument()
      expect(await screen.findByRole('heading', { name: 'Helper' })).toBeInTheDocument()
    })

    it('lets the user pick another of their computers', async () => {
      const server = fakeServer(handlers({}, { [`POST ${BASE}/agents`]: () => json(201, BUILDER) }))
      await openAgents()
      const user = userEvent.setup()
      await screen.findByRole('heading', { name: 'Builder' })

      await user.click(screen.getByRole('button', { name: 'Create agent' }))
      const dialog = await screen.findByRole('dialog', { name: 'Create agent' })
      await user.type(within(dialog).getByLabelText('Name'), 'Server bot')
      await user.click(within(dialog).getByRole('combobox', { name: 'Runtime' }))
      await user.click(await screen.findByRole('option', { name: "Ada's server · mock (offline)" }))
      await user.click(within(dialog).getByRole('button', { name: 'Create' }))

      await waitFor(() => expect(server.calls(`POST ${BASE}/agents`)).toHaveLength(1))
      expect(server.calls(`POST ${BASE}/agents`)[0]?.body).toMatchObject({ runtimeId: runtimeId(3), model: '' })
    })

    it('asks for a name and sends nothing without one', async () => {
      const server = fakeServer(handlers())
      await openAgents()
      const user = userEvent.setup()
      await screen.findByRole('heading', { name: 'Builder' })

      await user.click(screen.getByRole('button', { name: 'Create agent' }))
      const dialog = await screen.findByRole('dialog', { name: 'Create agent' })
      await user.click(within(dialog).getByRole('button', { name: 'Create' }))

      expect(await within(dialog).findByText('Enter a name for the agent')).toBeInTheDocument()
      expect(server.calls(`POST ${BASE}/agents`)).toHaveLength(0)
    })

    it('explains how to get a runtime when the user has none, and blocks creating', async () => {
      const server = fakeServer(handlers({ runtimes: [MIAS_PC] }))
      await openAgents()
      const user = userEvent.setup()
      await screen.findByRole('heading', { name: 'Builder' })

      await user.click(screen.getByRole('button', { name: 'Create agent' }))
      const dialog = await screen.findByRole('dialog', { name: 'Create agent' })

      expect(await within(dialog).findByRole('status')).toHaveTextContent('You have no runtime yet')
      expect(within(dialog).getByRole('link', { name: 'Connect a computer' })).toHaveAttribute('href', '/w/acme/runtimes')
      expect(within(dialog).getByRole('button', { name: 'Create' })).toBeDisabled()
      expect(server.calls(`POST ${BASE}/agents`)).toHaveLength(0)
    })

    it('shows the reason when the server refuses, and keeps the dialog open', async () => {
      fakeServer(
        handlers(
          {},
          { [`POST ${BASE}/agents`]: () => problem(409, { detail: 'Another agent already has this name', code: 'agent.name_taken' }) },
        ),
      )
      await openAgents()
      const user = userEvent.setup()
      await screen.findByRole('heading', { name: 'Builder' })

      await user.click(screen.getByRole('button', { name: 'Create agent' }))
      const dialog = await screen.findByRole('dialog', { name: 'Create agent' })
      await user.type(within(dialog).getByLabelText('Name'), 'Builder')
      await user.click(within(dialog).getByRole('button', { name: 'Create' }))

      expect(await within(dialog).findByText('Another agent already has this name')).toBeInTheDocument()
      expect(screen.getByRole('dialog')).toBeInTheDocument()
    })

    it('opens empty every time', async () => {
      fakeServer(handlers())
      await openAgents()
      const user = userEvent.setup()
      await screen.findByRole('heading', { name: 'Builder' })

      await user.click(screen.getByRole('button', { name: 'Create agent' }))
      await user.type(await screen.findByLabelText('Name'), 'Half written')
      await user.click(screen.getByRole('button', { name: 'Cancel' }))
      await user.click(screen.getByRole('button', { name: 'Create agent' }))

      expect(await screen.findByLabelText('Name')).toHaveValue('')
    })
  })

  describe('editing an agent', () => {
    it('starts from the agent as it is and saves the changes', async () => {
      const server = fakeServer(
        handlers({}, { [`PUT ${BASE}/agents/${BUILDER.id}`]: () => json(200, { ...BUILDER, name: 'Master builder' }) }),
      )
      await openAgents()
      const user = userEvent.setup()
      await screen.findByRole('heading', { name: 'Builder' })

      await user.click(screen.getByRole('button', { name: 'Edit Builder' }))
      const dialog = await screen.findByRole('dialog', { name: 'Edit agent' })
      expect(within(dialog).getByLabelText('Name')).toHaveValue('Builder')
      expect(within(dialog).getByLabelText('Model (optional)')).toHaveValue('sonnet')
      expect(within(dialog).getByLabelText('Instructions')).toHaveValue('# Rules\n\nWrite   clean code.')
      expect(within(dialog).getByRole('combobox', { name: 'Runtime' })).toHaveTextContent("Ada's laptop · claude")

      await user.clear(within(dialog).getByLabelText('Name'))
      await user.type(within(dialog).getByLabelText('Name'), 'Master builder')
      await user.click(within(dialog).getByRole('button', { name: 'Save' }))

      await waitFor(() => expect(server.calls(`PUT ${BASE}/agents/${BUILDER.id}`)).toHaveLength(1))
      expect(server.calls(`PUT ${BASE}/agents/${BUILDER.id}`)[0]?.body).toEqual({
        name: 'Master builder',
        instructions: '# Rules\n\nWrite   clean code.',
        runtimeId: runtimeId(1),
        model: 'sonnet',
      })
      expect(await screen.findByText('Master builder was saved')).toBeInTheDocument()
    })

    it('keeps the runtime of somebody else\'s agent when an admin saves without changing it', async () => {
      const server = fakeServer(
        handlers({}, { [`PUT ${BASE}/agents/${REVIEWER.id}`]: () => json(200, REVIEWER) }),
      )
      await openAgents()
      const user = userEvent.setup()
      await screen.findByRole('heading', { name: 'Reviewer' })

      await user.click(screen.getByRole('button', { name: 'Edit Reviewer' }))
      const dialog = await screen.findByRole('dialog', { name: 'Edit agent' })
      // Mia's computer is not the admin's, but it is the agent's current one, so it stays selectable.
      expect(within(dialog).getByRole('combobox', { name: 'Runtime' })).toHaveTextContent("Mia's PC · claude")
      await user.click(within(dialog).getByRole('combobox', { name: 'Runtime' }))
      expect((await screen.findAllByRole('option')).map((option) => option.textContent)).toEqual([
        "Mia's PC · claude",
        "Ada's laptop · claude",
        "Ada's server · mock (offline)",
      ])
      await user.keyboard('{Escape}')
      await user.click(within(dialog).getByRole('button', { name: 'Save' }))

      await waitFor(() => expect(server.calls(`PUT ${BASE}/agents/${REVIEWER.id}`)).toHaveLength(1))
      expect(server.calls(`PUT ${BASE}/agents/${REVIEWER.id}`)[0]?.body).toMatchObject({ runtimeId: runtimeId(2) })
    })

    it('shows the reason when the server refuses the change', async () => {
      fakeServer(
        handlers(
          {},
          { [`PUT ${BASE}/agents/${BUILDER.id}`]: () => problem(403, { detail: 'Only the creator of an agent, an admin or an owner can change it' }) },
        ),
      )
      await openAgents()
      const user = userEvent.setup()
      await screen.findByRole('heading', { name: 'Builder' })

      await user.click(screen.getByRole('button', { name: 'Edit Builder' }))
      const dialog = await screen.findByRole('dialog', { name: 'Edit agent' })
      await user.click(within(dialog).getByRole('button', { name: 'Save' }))

      expect(
        await within(dialog).findByText('Only the creator of an agent, an admin or an owner can change it'),
      ).toBeInTheDocument()
    })
  })

  describe('archiving and restoring', () => {
    it('asks before archiving, then archives', async () => {
      const server = fakeServer(
        handlers({}, { [`POST ${BASE}/agents/${BUILDER.id}/archive`]: () => json(200, { ...BUILDER, archived: true }) }),
      )
      await openAgents()
      const user = userEvent.setup()
      await screen.findByRole('heading', { name: 'Builder' })

      await user.click(screen.getByRole('button', { name: 'Archive Builder' }))
      const dialog = await screen.findByRole('dialog', { name: 'Archive Builder?' })
      expect(server.calls(`POST ${BASE}/agents/${BUILDER.id}/archive`)).toHaveLength(0)
      await user.click(within(dialog).getByRole('button', { name: 'Archive' }))

      await waitFor(() => expect(server.calls(`POST ${BASE}/agents/${BUILDER.id}/archive`)).toHaveLength(1))
      expect(await screen.findByText('Builder was archived')).toBeInTheDocument()
    })

    it('does nothing when the confirmation is cancelled', async () => {
      const server = fakeServer(handlers())
      await openAgents()
      const user = userEvent.setup()
      await screen.findByRole('heading', { name: 'Builder' })

      await user.click(screen.getByRole('button', { name: 'Archive Builder' }))
      await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }))

      expect(server.requests.filter((request) => request.method === 'POST')).toEqual([])
    })

    it('restores an archived agent', async () => {
      const server = fakeServer(
        handlers(
          { agents: [BUILDER, RETIRED] },
          { [`POST ${BASE}/agents/${RETIRED.id}/restore`]: () => json(200, { ...RETIRED, archived: false }) },
        ),
      )
      await openAgents()
      const user = userEvent.setup()
      await user.click(await screen.findByRole('button', { name: 'Show archived' }))

      await user.click(await screen.findByRole('button', { name: 'Restore Retired' }))

      await waitFor(() => expect(server.calls(`POST ${BASE}/agents/${RETIRED.id}/restore`)).toHaveLength(1))
      expect(await screen.findByText('Retired was restored')).toBeInTheDocument()
    })

    it('tells the user when the server refuses to restore', async () => {
      fakeServer(
        handlers(
          { agents: [BUILDER, RETIRED] },
          { [`POST ${BASE}/agents/${RETIRED.id}/restore`]: () => problem(409, { detail: 'Another agent already has this name' }) },
        ),
      )
      await openAgents()
      const user = userEvent.setup()
      await user.click(await screen.findByRole('button', { name: 'Show archived' }))

      await user.click(await screen.findByRole('button', { name: 'Restore Retired' }))

      expect(await screen.findByText('Another agent already has this name')).toBeInTheDocument()
    })
  })
})
