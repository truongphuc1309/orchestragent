import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { FakeEventSource } from '@/test/fakeEventSource'
import { ACM_PROJECT, ACME, ADA, issue, issueId, task, taskId, taskMessage } from '@/test/fixtures'
import { fakeServer, json, problem, type RecordedRequest } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import type { Task, TaskMessage } from '../schemas'

const BASE = `/api/workspaces/${ACME.id}`
const ISSUE = issue({ id: issueId(7), number: 7, title: 'Fix the login', status: 'in_progress' })
const TASKS = `GET ${BASE}/tasks?issueId=${ISSUE.id}`
const STREAM = `${BASE}/events`

const WAITING = task({ id: taskId(3), issueId: ISSUE.id, status: 'queued', trigger: 'comment', attempt: 3 })
const FAILED = task({
  id: taskId(2),
  issueId: ISSUE.id,
  status: 'failed',
  attempt: 2,
  trigger: 'retry',
  error: 'The tool did not answer for 30 minutes.',
  failureReason: 'timeout',
  startedAt: '2026-01-08T10:00:00Z',
  finishedAt: '2026-01-08T10:30:00Z',
})
const DONE = task({
  id: taskId(1),
  issueId: ISSUE.id,
  status: 'completed',
  attempt: 1,
  startedAt: '2026-01-08T09:00:00Z',
  finishedAt: '2026-01-08T09:03:05Z',
  inputTokens: 1234,
  outputTokens: 340,
  costUsd: 0.0549,
})

type Handler = (request: RecordedRequest) => Response | Promise<Response>

function handlers(tasks: Task[], extra: Record<string, Handler> = {}) {
  return {
    'GET /api/me': () => json(200, ADA),
    'GET /api/workspaces': () => json(200, [ACME]),
    [`GET ${BASE}/members`]: () => json(200, []),
    [`GET ${BASE}/agents`]: () => json(200, []),
    [`GET ${BASE}/issues/key/ACM-7`]: () => json(200, ISSUE),
    [`GET ${BASE}/issues/${ISSUE.id}/comments`]: () => json(200, []),
    [TASKS]: () => json(200, tasks),
    [`GET /api/workspaces/${ACME.id}/projects?includeArchived=true`]: () => json(200, [ACM_PROJECT]),
    ...extra,
  }
}

function logPath(id: string, after = -1) {
  return `GET ${BASE}/tasks/${id}/messages?after=${after}`
}

async function openIssue() {
  const view = renderRoutes(routes, ['/w/acme/issues/ACM-7'])
  await screen.findByRole('heading', { name: 'Fix the login' })
  return view
}

function runCard(attempt: number): HTMLElement {
  const card = screen.getByText(`Run ${attempt}`).closest('li')
  if (card === null) {
    throw new Error(`No card for run ${attempt}`)
  }
  return card
}

describe('agent runs on the issue page', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  describe('the list of runs', () => {
    it('shows nothing for an issue no agent has worked on', async () => {
      fakeServer(handlers([]))
      await openIssue()

      await waitFor(() => expect(screen.queryByText('Loading the agent runs…')).not.toBeInTheDocument())
      expect(screen.queryByRole('heading', { name: 'Agent runs' })).not.toBeInTheDocument()
    })

    it('lists the runs newest first with state, trigger, agent and computer', async () => {
      fakeServer(handlers([WAITING, FAILED, DONE]))
      await openIssue()

      const list = await screen.findByRole('list', { name: 'Agent runs' })
      const cards = within(list).getAllByRole('listitem')
      expect(cards).toHaveLength(3)
      expect(cards[0]).toHaveTextContent('Run 3')
      expect(cards[1]).toHaveTextContent('Run 2')
      expect(cards[2]).toHaveTextContent('Run 1')
      expect(cards[0]).toHaveTextContent('Queued')
      expect(cards[0]).toHaveTextContent("Comment · Builder on Ada's laptop")
      expect(cards[1]).toHaveTextContent('Failed')
      expect(cards[1]).toHaveTextContent('Retry · Builder')
      expect(cards[2]).toHaveTextContent('Completed')
      expect(cards[2]).toHaveTextContent('Assigned · Builder')
    })

    it('numbers the runs in time order, and shows a retry as an attempt of its own', async () => {
      const first = task({ id: taskId(1), issueId: ISSUE.id, status: 'completed', attempt: 1, trigger: 'assigned' })
      const second = task({ id: taskId(2), issueId: ISSUE.id, status: 'running', attempt: 1, trigger: 'changes_requested' })
      const third = task({ id: taskId(3), issueId: ISSUE.id, status: 'queued', attempt: 2, trigger: 'retry' })
      fakeServer(handlers([third, second, first], { [logPath(second.id)]: () => json(200, []) }))
      await openIssue()

      const list = await screen.findByRole('list', { name: 'Agent runs' })
      const cards = within(list).getAllByRole('listitem')
      // Two runs both have attempt 1, but they are different runs: no two cards carry the same name.
      expect(cards.map((card) => card.textContent?.match(/Run \d+/)?.[0])).toEqual(['Run 3', 'Run 2', 'Run 1'])
      expect(cards[0]).toHaveTextContent('Retry · Builder on Ada\'s laptop · attempt 2')
      expect(cards[1]).not.toHaveTextContent('attempt')
      expect(cards[2]).not.toHaveTextContent('attempt')
    })

    it('shows how long each run took and what it cost', async () => {
      fakeServer(handlers([WAITING, FAILED, DONE]))
      await openIssue()

      await screen.findByRole('list', { name: 'Agent runs' })
      expect(runCard(1)).toHaveTextContent('3m 05s')
      expect(runCard(1)).toHaveTextContent('1.2k in / 340 out tokens')
      expect(runCard(1)).toHaveTextContent('$0.05')
      expect(runCard(2)).toHaveTextContent('30m 00s')
      expect(runCard(3)).toHaveTextContent('Waiting for a computer to pick it up')
    })

    it('says why a failed run failed', async () => {
      fakeServer(handlers([FAILED]))
      await openIssue()

      await screen.findByRole('list', { name: 'Agent runs' })
      expect(within(runCard(1)).getByRole('note')).toHaveTextContent('The tool did not answer for 30 minutes.')
    })

    it('shows a run that was handed to a computer but has not started as starting', async () => {
      fakeServer(handlers([task({ id: taskId(1), issueId: ISSUE.id, status: 'dispatched' })]))
      await openIssue()

      await screen.findByRole('list', { name: 'Agent runs' })
      expect(runCard(1)).toHaveTextContent('Starting')
    })

    it('offers a retry when the runs cannot be loaded', async () => {
      let healthy = false
      fakeServer(handlers([DONE], { [TASKS]: () => (healthy ? json(200, [DONE]) : problem(500, {})) }))
      await openIssue()
      const user = userEvent.setup()

      expect(await screen.findByText('Could not load the agent runs.')).toBeInTheDocument()
      healthy = true
      await user.click(screen.getByRole('button', { name: 'Try again' }))

      expect(await screen.findByRole('list', { name: 'Agent runs' })).toBeInTheDocument()
    })
  })

  describe('the log', () => {
    const RUNNING = task({
      id: taskId(1),
      issueId: ISSUE.id,
      status: 'running',
      startedAt: new Date(Date.now() - 65_000).toISOString(),
    })

    it('opens by itself for the run in progress and shows text, tool calls and results', async () => {
      const lines: TaskMessage[] = [
        taskMessage(0, 'text', 'Looking at the login code'),
        taskMessage(1, 'tool_use', 'grep -rn "login" src\nsecond line'),
        taskMessage(2, 'tool_result', '3 matches in 2 files'),
        taskMessage(3, 'error', 'Permission denied'),
        taskMessage(4, 'system', 'Session resumed'),
      ]
      fakeServer(handlers([RUNNING], { [logPath(RUNNING.id)]: () => json(200, lines) }))
      await openIssue()

      const log = await screen.findByRole('list', { name: 'Log of run 1' })
      expect(within(log).getByText('Looking at the login code')).toBeInTheDocument()
      expect(within(log).getByText('Tool: grep -rn "login" src')).toBeInTheDocument()
      expect(within(log).getByText('Result')).toBeInTheDocument()
      expect(within(log).getByText('Permission denied')).toHaveClass('text-destructive')
      expect(within(log).getByText('Session resumed')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Hide log' })).toHaveAttribute('aria-expanded', 'true')
    })

    it('folds the tool output away until it is opened', async () => {
      fakeServer(
        handlers([RUNNING], {
          [logPath(RUNNING.id)]: () => json(200, [taskMessage(0, 'tool_result', 'the full result text')]),
        }),
      )
      await openIssue()
      const user = userEvent.setup()

      const log = await screen.findByRole('list', { name: 'Log of run 1' })
      const details = within(log).getByText('Result').closest('details')
      expect(details).not.toHaveAttribute('open')
      await user.click(within(log).getByText('Result'))

      expect(details).toHaveAttribute('open')
      expect(within(log).getByText('the full result text')).toBeInTheDocument()
    })

    it('cuts a very long tool result when showing it', async () => {
      const long = 'x'.repeat(6000)
      fakeServer(handlers([RUNNING], { [logPath(RUNNING.id)]: () => json(200, [taskMessage(0, 'tool_result', long)]) }))
      await openIssue()

      const log = await screen.findByRole('list', { name: 'Log of run 1' })
      expect(within(log).getByText(/\(2000 more characters\)/)).toBeInTheDocument()
    })

    it('says it is waiting for output while the run has none yet', async () => {
      fakeServer(handlers([RUNNING], { [logPath(RUNNING.id)]: () => json(200, []) }))
      await openIssue()

      expect(await screen.findByText('Waiting for output…')).toBeInTheDocument()
    })

    it('keeps the log of a finished run closed until asked, then loads it', async () => {
      const server = fakeServer(
        handlers([DONE], { [logPath(DONE.id)]: () => json(200, [taskMessage(0, 'text', 'All done')]) }),
      )
      await openIssue()
      const user = userEvent.setup()
      await screen.findByRole('list', { name: 'Agent runs' })
      expect(server.calls(logPath(DONE.id))).toHaveLength(0)

      await user.click(screen.getByRole('button', { name: 'Show log' }))

      expect(await screen.findByText('All done')).toBeInTheDocument()
      expect(server.calls(logPath(DONE.id))).toHaveLength(1)
      await user.click(screen.getByRole('button', { name: 'Hide log' }))
      expect(screen.queryByText('All done')).not.toBeInTheDocument()
    })

    it('says so when a finished run left no output', async () => {
      fakeServer(handlers([DONE], { [logPath(DONE.id)]: () => json(200, []) }))
      await openIssue()
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Show log' }))

      expect(await screen.findByText('This run left no output.')).toBeInTheDocument()
    })

    it('offers a retry when the log cannot be loaded', async () => {
      let healthy = false
      fakeServer(
        handlers([RUNNING], {
          [logPath(RUNNING.id)]: () => (healthy ? json(200, [taskMessage(0, 'text', 'Back again')]) : problem(500, {})),
        }),
      )
      await openIssue()
      const user = userEvent.setup()

      expect(await screen.findByText('Could not load the log.')).toBeInTheDocument()
      healthy = true
      await user.click(screen.getByRole('button', { name: 'Try again' }))

      expect(await screen.findByText('Back again')).toBeInTheDocument()
    })

    it('reads a very long log page by page', async () => {
      const page = (from: number, count: number) =>
        Array.from({ length: count }, (_, i) => taskMessage(from + i, 'text', `line ${from + i}`))
      const server = fakeServer(
        handlers([RUNNING], {
          [logPath(RUNNING.id)]: () => json(200, page(0, 500)),
          [logPath(RUNNING.id, 499)]: () => json(200, page(500, 20)),
        }),
      )
      await openIssue()

      const log = await screen.findByRole('list', { name: 'Log of run 1' })
      await waitFor(() => expect(within(log).getAllByRole('listitem')).toHaveLength(520))
      expect(server.calls(logPath(RUNNING.id, 499))).toHaveLength(1)
    })
  })

  describe('following a run live', () => {
    it('adds new log lines as the server announces them, asking only for the lines it does not have', async () => {
      const running = task({ id: taskId(1), issueId: ISSUE.id, status: 'running', startedAt: '2026-01-08T10:00:00Z' })
      let lines: TaskMessage[] = [taskMessage(0, 'text', 'First thought')]
      const server = fakeServer(
        handlers([running], {
          [logPath(running.id)]: () => json(200, lines),
          [logPath(running.id, 0)]: () => json(200, [taskMessage(1, 'text', 'Second thought')]),
        }),
      )
      await openIssue()
      expect(await screen.findByText('First thought')).toBeInTheDocument()

      lines = [taskMessage(0, 'text', 'First thought'), taskMessage(1, 'text', 'Second thought')]
      act(() =>
        FakeEventSource.latest(STREAM)?.emit('task.message', { issueId: ISSUE.id, taskId: running.id, lastSeq: 1 }),
      )

      expect(await screen.findByText('Second thought')).toBeInTheDocument()
      expect(screen.getByText('First thought')).toBeInTheDocument()
      expect(server.calls(logPath(running.id))).toHaveLength(1)
      expect(server.calls(logPath(running.id, 0))).toHaveLength(1)
    })

    it('shows a run changing state without a reload', async () => {
      let tasks: Task[] = [task({ id: taskId(1), issueId: ISSUE.id, status: 'queued' })]
      fakeServer(handlers([], { [TASKS]: () => json(200, tasks), [logPath(taskId(1))]: () => json(200, []) }))
      await openIssue()
      await screen.findByText('Waiting for a computer to pick it up')

      tasks = [task({ id: taskId(1), issueId: ISSUE.id, status: 'running', startedAt: new Date().toISOString() })]
      act(() =>
        FakeEventSource.latest(STREAM)?.emit('task.updated', { issueId: ISSUE.id, taskId: taskId(1), status: 'running' }),
      )

      await waitFor(() => expect(runCard(1)).toHaveTextContent('Running'))
      expect(within(runCard(1)).getByRole('button', { name: 'Cancel run 1' })).toBeInTheDocument()
    })

    it('shows a new run that appears after the issue was opened', async () => {
      let tasks: Task[] = []
      fakeServer(handlers([], { [TASKS]: () => json(200, tasks) }))
      await openIssue()
      await waitFor(() => expect(screen.queryByText('Loading the agent runs…')).not.toBeInTheDocument())

      tasks = [WAITING]
      act(() =>
        FakeEventSource.latest(STREAM)?.emit('task.updated', { issueId: ISSUE.id, taskId: WAITING.id, status: 'queued' }),
      )

      expect(await screen.findByRole('heading', { name: 'Agent runs' })).toBeInTheDocument()
    })

    it('ignores task events it does not understand', async () => {
      const server = fakeServer(handlers([DONE]))
      await openIssue()
      await screen.findByRole('list', { name: 'Agent runs' })
      const before = server.calls(TASKS).length

      act(() => {
        FakeEventSource.latest(STREAM)?.emit('task.updated', { unexpected: true })
        FakeEventSource.latest(STREAM)?.emit('task.message', 'not json')
      })

      await new Promise((resolve) => setTimeout(resolve, 30))
      expect(server.calls(TASKS)).toHaveLength(before)
    })
  })

  describe('cancelling and retrying', () => {
    it('cancels a run that is waiting or going on', async () => {
      const running = task({ id: taskId(1), issueId: ISSUE.id, status: 'running', startedAt: '2026-01-08T10:00:00Z' })
      let tasks: Task[] = [WAITING, running]
      const server = fakeServer(
        handlers([], {
          [TASKS]: () => json(200, tasks),
          [logPath(running.id)]: () => json(200, []),
          [`POST ${BASE}/tasks/${WAITING.id}/cancel`]: () => {
            tasks = [{ ...WAITING, status: 'cancelled' }, running]
            return json(200, { ...WAITING, status: 'cancelled' })
          },
        }),
      )
      await openIssue()
      const user = userEvent.setup()
      await screen.findByRole('list', { name: 'Agent runs' })

      await user.click(within(runCard(2)).getByRole('button', { name: 'Cancel run 2' }))

      await waitFor(() => expect(server.calls(`POST ${BASE}/tasks/${WAITING.id}/cancel`)).toHaveLength(1))
      await waitFor(() => expect(runCard(2)).toHaveTextContent('Cancelled'))
      expect(within(runCard(2)).queryByRole('button', { name: 'Cancel run 2' })).not.toBeInTheDocument()
      expect(within(runCard(1)).getByRole('button', { name: 'Cancel run 1' })).toBeInTheDocument()
    })

    it('has no cancel button for a finished run', async () => {
      fakeServer(handlers([FAILED, DONE]))
      await openIssue()

      await screen.findByRole('list', { name: 'Agent runs' })
      expect(screen.queryByRole('button', { name: /Cancel run/ })).not.toBeInTheDocument()
    })

    it('tells the user when cancelling fails', async () => {
      fakeServer(
        handlers([WAITING], {
          [`POST ${BASE}/tasks/${WAITING.id}/cancel`]: () => problem(409, { detail: 'Someone else changed this at the same time. Please try again.' }),
        }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Cancel run 1' }))

      expect(await screen.findByText('Someone else changed this at the same time. Please try again.')).toBeInTheDocument()
    })

    it('retries the newest failed run', async () => {
      const queued = task({ id: taskId(9), issueId: ISSUE.id, status: 'queued', attempt: 3, trigger: 'retry' })
      let tasks: Task[] = [FAILED]
      const server = fakeServer(
        handlers([], {
          [TASKS]: () => json(200, tasks),
          [`POST ${BASE}/tasks/${FAILED.id}/retry`]: () => {
            tasks = [queued, FAILED]
            return json(201, queued)
          },
        }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Retry run 1' }))

      await waitFor(() => expect(server.calls(`POST ${BASE}/tasks/${FAILED.id}/retry`)).toHaveLength(1))
      expect(await screen.findByText('The run is queued again')).toBeInTheDocument()
      await waitFor(() => expect(screen.getByText('Run 2')).toBeInTheDocument())
      expect(screen.queryByRole('button', { name: 'Retry run 1' })).not.toBeInTheDocument()
    })

    it('offers a retry only on the newest run, not on older failures', async () => {
      const older = { ...FAILED, id: taskId(8), attempt: 1 }
      fakeServer(handlers([FAILED, older]))
      await openIssue()

      await screen.findByRole('list', { name: 'Agent runs' })
      expect(screen.getByRole('button', { name: 'Retry run 2' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Retry run 1' })).not.toBeInTheDocument()
    })

    it('tells the user why a retry was refused', async () => {
      fakeServer(
        handlers([FAILED], {
          [`POST ${BASE}/tasks/${FAILED.id}/retry`]: () => problem(409, { detail: 'Another run of this issue is already waiting', code: 'task.already_queued' }),
        }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Retry run 1' }))

      expect(await screen.findByText('Another run of this issue is already waiting')).toBeInTheDocument()
    })
  })
})
