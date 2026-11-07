import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACME, ADA, agentId, issueId } from '@/test/fixtures'
import { fakeServer, json } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import type { Totals, Usage } from '../schemas'

const USAGE = `/api/workspaces/${ACME.id}/usage`

function totals(runs: number, cost: number, overrides: Partial<Totals> = {}): Totals {
  return { runs, completed: runs, failed: 0, inputTokens: runs * 1000, outputTokens: runs * 100, costUsd: cost, ...overrides }
}

function report(days: string[], perDay: Totals[]): Usage {
  return {
    firstDay: days[0] ?? '',
    lastDay: days[days.length - 1] ?? '',
    totals: totals(5, 1.5, { completed: 4, failed: 1, inputTokens: 52_300, outputTokens: 1_200 }),
    byAgent: [
      { agentId: agentId(1), agentName: 'Builder', archived: false, totals: totals(4, 1.2) },
      { agentId: agentId(2), agentName: 'Old helper', archived: true, totals: totals(1, 0.3, { failed: 1, completed: 0 }) },
    ],
    topIssues: [
      { issueId: issueId(7), number: 7, title: 'Add login', totals: totals(3, 1.0) },
      { issueId: issueId(8), number: null, title: null, totals: totals(2, 0.5) },
    ],
    byDay: days.map((day, i) => ({ day, totals: perDay[i] ?? totals(0, 0) })),
  }
}

const THIRTY = report(['2026-11-06', '2026-11-07', '2026-11-08'], [totals(1, 0.25), totals(0, 0), totals(4, 1.25)])
const SEVEN = report(['2026-11-07', '2026-11-08'], [totals(0, 0), totals(2, 0.5)])

function server() {
  return fakeServer({
    'GET /api/me': () => json(200, ADA),
    'GET /api/workspaces': () => json(200, [ACME]),
    [`GET ${USAGE}?days=30`]: () => json(200, THIRTY),
    [`GET ${USAGE}?days=7`]: () => json(200, SEVEN),
  })
}

function barHeights(): string[] {
  return screen.getAllByTestId('bar').map((bar) => bar.style.height)
}

describe('usage page', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the totals of the last 30 days', async () => {
    server()
    renderRoutes(routes, ['/w/acme/usage'])

    expect(await screen.findByText('$1.50')).toBeInTheDocument()
    expect(screen.getByText('4 completed, 1 failed')).toBeInTheDocument()
    expect(screen.getByText('52.3k')).toBeInTheDocument()
    expect(screen.getByText(/from 6 Nov to 8 Nov/)).toBeInTheDocument()
  })

  it('draws one bar per day, as tall as its cost compared with the most expensive day', async () => {
    server()
    renderRoutes(routes, ['/w/acme/usage'])
    await screen.findByText('Cost per day (UTC)')

    expect(barHeights()).toEqual(['20%', '0%', '100%'])
    const days = within(screen.getByRole('list', { name: 'Cost per day' })).getAllByRole('button')
    expect(days[0]).toHaveAccessibleName('6 Nov: 1 run, 1.1k tokens, $0.25')
  })

  it('switches the chart to another measure, never two at once', async () => {
    server()
    const user = userEvent.setup()
    renderRoutes(routes, ['/w/acme/usage'])
    await screen.findByText('Cost per day (UTC)')

    await user.click(screen.getByRole('tab', { name: 'Runs' }))

    expect(screen.getByText('Runs per day (UTC)')).toBeInTheDocument()
    expect(screen.queryByText('Cost per day (UTC)')).not.toBeInTheDocument()
    expect(barHeights()).toEqual(['25%', '0%', '100%'])
  })

  it('lists the agents and the most expensive issues, a deleted one included', async () => {
    server()
    renderRoutes(routes, ['/w/acme/usage'])

    const agents = await screen.findByRole('table', { name: 'Usage per agent' })
    expect(within(agents).getAllByRole('row').map((row) => row.textContent)).toEqual([
      'AgentRunsFailedInput tokensOutput tokensCost',
      'Builder404k400$1.20',
      'Old helperArchived111k100$0.30',
    ])
    const issues = screen.getByRole('table', { name: 'Usage per issue' })
    expect(within(issues).getByRole('link', { name: 'ACM-7 Add login' })).toHaveAttribute('href', '/w/acme/issues/7')
    expect(within(issues).getByText('Deleted issue')).toBeInTheDocument()
  })

  it('asks for another period', async () => {
    const fake = server()
    const user = userEvent.setup()
    renderRoutes(routes, ['/w/acme/usage'])
    await screen.findByText('$1.50')

    await user.click(screen.getByRole('tab', { name: '7 days' }))

    await waitFor(() => expect(barHeights()).toEqual(['0%', '100%']))
    expect(fake.calls(`GET ${USAGE}?days=7`)).toHaveLength(1)
  })

  it('has the same numbers as a table', async () => {
    server()
    renderRoutes(routes, ['/w/acme/usage'])
    await screen.findByText('Show as a table')

    const tables = screen.getAllByRole('table', { hidden: true })
    const daily = tables.find((table) => within(table).queryByText('Day') !== null)
    if (daily === undefined) {
      throw new Error('The daily table is missing')
    }
    expect(within(daily).getAllByRole('row', { hidden: true }).map((row) => row.textContent)).toEqual([
      'DayRunsTokensCost',
      '6 Nov11.1k$0.25',
      '7 Nov00$0.00',
      '8 Nov44.4k$1.25',
    ])
  })
})
