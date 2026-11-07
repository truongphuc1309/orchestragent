import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACME, ADA, MIA, runtime, runtimeId } from '@/test/fixtures'
import { fakeServer, json, problem } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import type { Runtime } from '../schemas'

const RUNTIMES = `GET /api/workspaces/${ACME.id}/runtimes`

const MINE = runtime({ id: runtimeId(1), deviceName: "Ada's laptop", providerVersion: '1.2.3' })
const OFFLINE = runtime({
  id: runtimeId(2),
  ownerId: MIA.userId,
  ownerName: 'Mia',
  deviceName: "Mia's PC",
  provider: 'mock',
  providerVersion: '',
  status: 'offline',
  lastHeartbeatAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
})

function handlers(runtimes: Runtime[] = [MINE, OFFLINE]) {
  return {
    'GET /api/me': () => json(200, ADA),
    'GET /api/workspaces': () => json(200, [ACME]),
    [RUNTIMES]: () => json(200, runtimes),
  }
}

describe('runtimes page', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('lists the computers with provider, version, owner, status and last heartbeat', async () => {
    fakeServer(handlers())
    renderRoutes(routes, ['/w/acme/runtimes'])

    const table = await screen.findByRole('table')
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(3)
    const [, mineRow, theirsRow] = rows
    if (mineRow === undefined || theirsRow === undefined) {
      throw new Error('The table has no rows')
    }

    const mine = within(mineRow)
    expect(mine.getByText("Ada's laptop")).toBeInTheDocument()
    expect(mine.getByText('claude')).toBeInTheDocument()
    expect(mine.getByText('1.2.3')).toBeInTheDocument()
    expect(mine.getByText('(you)')).toBeInTheDocument()
    expect(mine.getByText('Online')).toBeInTheDocument()
    expect(mine.getByText('just now')).toBeInTheDocument()

    const theirs = within(theirsRow)
    expect(theirs.getByText("Mia's PC")).toBeInTheDocument()
    expect(theirs.getByText('Mia')).toBeInTheDocument()
    expect(theirs.queryByText('(you)')).not.toBeInTheDocument()
    expect(theirs.getByText('Offline')).toBeInTheDocument()
    expect(theirs.getByText('5 min ago')).toBeInTheDocument()
  })

  it('shows the exact time of a heartbeat on hover text', async () => {
    fakeServer(handlers([MINE]))
    renderRoutes(routes, ['/w/acme/runtimes'])

    const time = await screen.findByText('just now')

    expect(time).toHaveAttribute('datetime', MINE.lastHeartbeatAt)
    expect(time.getAttribute('title')).not.toBe('')
  })

  it('explains how to connect a computer, with this server and a link to the tokens page', async () => {
    fakeServer(handlers())
    renderRoutes(routes, ['/w/acme/runtimes'])

    const section = (await screen.findByRole('heading', { name: 'Connect a computer' })).closest('section')
    if (section === null) {
      throw new Error('The connect section is missing')
    }
    expect(within(section).getByRole('link', { name: 'API tokens page' })).toHaveAttribute('href', '/w/acme/settings/tokens')
    expect(section).toHaveTextContent(`login --server ${window.location.origin} --token <your token>`)
    expect(section).toHaveTextContent('not released yet')
    expect(section).toHaveTextContent('90 seconds')
  })

  it('says so when no computer is connected, and still explains how to connect one', async () => {
    fakeServer(handlers([]))
    renderRoutes(routes, ['/w/acme/runtimes'])

    expect(await screen.findByText(/No computer is connected yet/)).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Connect a computer' })).toBeInTheDocument()
  })

  it('offers a retry when the runtimes cannot be loaded', async () => {
    let healthy = false
    fakeServer({ ...handlers(), [RUNTIMES]: () => (healthy ? json(200, [MINE]) : problem(500, {})) })
    renderRoutes(routes, ['/w/acme/runtimes'])
    const user = userEvent.setup()

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the runtimes')
    healthy = true
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('table')).toBeInTheDocument()
  })
})
