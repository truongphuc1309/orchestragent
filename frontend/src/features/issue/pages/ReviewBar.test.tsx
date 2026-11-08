import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACM_PROJECT, ACME, ADA, comment, issue, issueId, task, taskId } from '@/test/fixtures'
import { fakeServer, json, problem, type RecordedRequest } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import type { Comment, Issue } from '../schemas'

const BASE = `/api/workspaces/${ACME.id}`
const IN_REVIEW: Issue = issue({ id: issueId(7), number: 7, title: 'Fix the login', status: 'in_review' })
const APPROVE = `POST ${BASE}/issues/${IN_REVIEW.id}/approve`
const REQUEST_CHANGES = `POST ${BASE}/issues/${IN_REVIEW.id}/request-changes`

type Handler = (request: RecordedRequest) => Response | Promise<Response>

function handlers(current: () => Issue, extra: Record<string, Handler> = {}) {
  return {
    'GET /api/me': () => json(200, ADA),
    'GET /api/workspaces': () => json(200, [ACME]),
    [`GET ${BASE}/members`]: () => json(200, []),
    [`GET ${BASE}/agents`]: () => json(200, []),
    [`GET ${BASE}/issues/key/ACM-7`]: () => json(200, current()),
    [`GET ${BASE}/issues/${IN_REVIEW.id}/comments`]: () => json(200, []),
    [`GET ${BASE}/tasks?issueId=${IN_REVIEW.id}`]: () => json(200, []),
    [`GET /api/workspaces/${ACME.id}/projects?includeArchived=true`]: () => json(200, [ACM_PROJECT]),
    ...extra,
  }
}

async function openIssue() {
  const view = renderRoutes(routes, ['/w/acme/issues/ACM-7'])
  await screen.findByRole('heading', { name: 'Fix the login' })
  return view
}

describe('review bar', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  describe('when it is shown', () => {
    it('is there for an issue in review, with both choices', async () => {
      fakeServer(handlers(() => IN_REVIEW))
      await openIssue()

      const bar = await screen.findByRole('region', { name: 'Waiting for your review' })
      expect(within(bar).getByRole('button', { name: 'Approve' })).toBeInTheDocument()
      expect(within(bar).getByRole('button', { name: 'Request changes' })).toBeInTheDocument()
    })

    it.each(['backlog', 'todo', 'in_progress', 'done', 'blocked', 'cancelled'] as const)(
      'is not there for an issue that is %s',
      async (status) => {
        fakeServer(handlers(() => ({ ...IN_REVIEW, status })))
        await openIssue()

        await waitFor(() => expect(screen.queryByText('Loading the agent runs…')).not.toBeInTheDocument())
        expect(screen.queryByRole('region', { name: 'Waiting for your review' })).not.toBeInTheDocument()
      },
    )
  })

  describe('approving', () => {
    it('approves with one click, and the bar goes away once the issue is done', async () => {
      let current: Issue = IN_REVIEW
      const server = fakeServer(
        handlers(() => current, {
          [APPROVE]: () => {
            current = { ...IN_REVIEW, status: 'done' }
            return json(200, current)
          },
        }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Approve' }))

      await waitFor(() => expect(server.calls(APPROVE)).toHaveLength(1))
      expect(await screen.findByText('Approved: the issue is done')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('region', { name: 'Waiting for your review' })).not.toBeInTheDocument())
      expect(screen.getByRole('combobox', { name: 'Status' })).toHaveTextContent('Done')
    })

    it('shows why it was refused, for example when someone else decided first', async () => {
      fakeServer(
        handlers(() => IN_REVIEW, {
          [APPROVE]: () => problem(409, { detail: 'Only an issue that is in review can be approved or sent back', code: 'issue.not_in_review' }),
        }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Approve' }))

      expect(await screen.findByText('Only an issue that is in review can be approved or sent back')).toBeInTheDocument()
    })
  })

  describe('requesting changes', () => {
    it('asks for a comment, sends it, and the bar goes away once the issue is back in to do', async () => {
      let current: Issue = IN_REVIEW
      const added = comment({ id: '0198f1c0-0000-7000-8000-0000000006a1', issueId: IN_REVIEW.id, body: 'Please add a test.' })
      let comments: Comment[] = []
      const server = fakeServer(
        handlers(() => current, {
          [`GET ${BASE}/issues/${IN_REVIEW.id}/comments`]: () => json(200, comments),
          [REQUEST_CHANGES]: () => {
            current = { ...IN_REVIEW, status: 'todo' }
            comments = [added]
            return json(200, current)
          },
        }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Request changes' }))
      await user.type(screen.getByLabelText('What should change?'), '  Please add a test.  ')
      await user.click(screen.getByRole('button', { name: 'Send back' }))

      await waitFor(() => expect(server.calls(REQUEST_CHANGES)).toHaveLength(1))
      expect(server.calls(REQUEST_CHANGES)[0]?.body).toEqual({ comment: 'Please add a test.' })
      expect(await screen.findByText('Sent back with your comment')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('region', { name: 'Waiting for your review' })).not.toBeInTheDocument())
      expect(screen.getByRole('combobox', { name: 'Status' })).toHaveTextContent('To do')
      expect(await screen.findByText('Please add a test.', { selector: 'p' })).toBeInTheDocument()
    })

    it('does not send anything without a comment', async () => {
      const server = fakeServer(handlers(() => IN_REVIEW))
      await openIssue()
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Request changes' }))
      await user.click(screen.getByRole('button', { name: 'Send back' }))
      expect(await screen.findByText('Say what should change')).toBeInTheDocument()
      await user.type(screen.getByLabelText('What should change?'), '    ')
      await user.click(screen.getByRole('button', { name: 'Send back' }))

      expect(await screen.findByText('Say what should change')).toBeInTheDocument()
      expect(server.calls(REQUEST_CHANGES)).toHaveLength(0)
    })

    it('can be cancelled, going back to the two choices without a request', async () => {
      const server = fakeServer(handlers(() => IN_REVIEW))
      await openIssue()
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Request changes' }))
      await user.type(screen.getByLabelText('What should change?'), 'Never mind')
      await user.click(screen.getByRole('button', { name: 'Cancel' }))

      expect(screen.queryByLabelText('What should change?')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Approve' })).toBeInTheDocument()
      expect(server.calls(REQUEST_CHANGES)).toHaveLength(0)
    })

    it('shows the reason when the server refuses, and keeps what was typed', async () => {
      fakeServer(
        handlers(() => IN_REVIEW, {
          [REQUEST_CHANGES]: () => problem(409, { detail: 'Only an issue that is in review can be approved or sent back' }),
        }),
      )
      await openIssue()
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Request changes' }))
      await user.type(screen.getByLabelText('What should change?'), 'Too late')
      await user.click(screen.getByRole('button', { name: 'Send back' }))

      expect(await screen.findByText('Only an issue that is in review can be approved or sent back')).toBeInTheDocument()
      expect(screen.getByLabelText('What should change?')).toHaveValue('Too late')
    })

    it('keeps what was typed when the form is closed and opened again', async () => {
      fakeServer(handlers(() => IN_REVIEW))
      await openIssue()
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Request changes' }))
      await user.type(screen.getByLabelText('What should change?'), 'Half written')
      await user.click(screen.getByRole('button', { name: 'Cancel' }))
      await user.click(screen.getByRole('button', { name: 'Request changes' }))

      // The draft is kept while the page is open; cancelling only closes the form.
      expect(screen.getByLabelText('What should change?')).toHaveValue('Half written')
    })
  })

  it('appears after an agent finishes, without a reload', async () => {
    let current: Issue = { ...IN_REVIEW, status: 'in_progress' }
    const runs = task({ id: taskId(1), issueId: IN_REVIEW.id, status: 'running', startedAt: '2026-01-08T10:00:00Z' })
    fakeServer(
      handlers(() => current, {
        [`GET ${BASE}/tasks?issueId=${IN_REVIEW.id}`]: () => json(200, [runs]),
        [`GET ${BASE}/tasks/${runs.id}/messages?after=-1`]: () => json(200, []),
      }),
    )
    const { queryClient } = await openIssue()
    await screen.findByText('Waiting for output…')
    expect(screen.queryByRole('region', { name: 'Waiting for your review' })).not.toBeInTheDocument()

    current = IN_REVIEW
    await queryClient.invalidateQueries({ queryKey: ['workspaces', ACME.id, 'issues'] })

    expect(await screen.findByRole('region', { name: 'Waiting for your review' })).toBeInTheDocument()
  })
})
