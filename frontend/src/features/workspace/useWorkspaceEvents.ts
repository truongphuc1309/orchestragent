import { useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { z } from 'zod'
import { connectEventStream } from '@/shared/api/eventStream'
import { inboxKeys } from '@/features/inbox/hooks'
import { issueKeys } from '@/features/issue/hooks'
import { taskKeys } from '@/features/task/hooks'
import { usageKeys } from '@/features/usage/hooks'
import { queryKeys } from './hooks'

/**
 * The server events of a workspace and what they carry. Issue events come from the issue context's SSE
 * listener, task events from the agent context's, inbox events (sent only to the person whose inbox changed) from
 * the inbox context's. All of them only say what changed.
 */
const issueEvent = z.object({ issueId: z.uuid() })
const taskEvent = z.object({ issueId: z.uuid(), taskId: z.uuid() })
const EVENT_NAMES = [
  'issue.created',
  'issue.updated',
  'issue.deleted',
  'comment.created',
  'task.updated',
  'task.message',
  'inbox.updated',
] as const

/**
 * Applies one server event to the cache: whatever it touches is marked stale, and TanStack Query fetches it
 * again if some screen is showing it. The events carry no data on purpose, so a repeated or reordered event
 * cannot leave the screen with something older than what the server has.
 */
export function applyWorkspaceEvent(queryClient: QueryClient, workspaceId: string, name: string, data: unknown) {
  switch (name) {
    case 'issue.created':
    case 'issue.updated':
    case 'issue.deleted':
      if (issueEvent.safeParse(data).success) {
        void queryClient.invalidateQueries({ queryKey: issueKeys.lists(workspaceId) })
        void queryClient.invalidateQueries({ queryKey: issueKeys.details(workspaceId) })
      }
      break
    case 'comment.created': {
      const parsed = issueEvent.safeParse(data)
      if (parsed.success) {
        void queryClient.invalidateQueries({ queryKey: issueKeys.comments(workspaceId, parsed.data.issueId) })
      }
      break
    }
    case 'task.updated': {
      // A run changed state: the list of runs of that issue. (The issue itself follows with issue.updated.)
      const parsed = taskEvent.safeParse(data)
      if (parsed.success) {
        void queryClient.invalidateQueries({ queryKey: taskKeys.list(workspaceId, parsed.data.issueId) })
        // A run that ended changes what the agents cost.
        void queryClient.invalidateQueries({ queryKey: usageKeys.all(workspaceId) })
      }
      break
    }
    case 'task.message': {
      // A running task has new log lines: only the log of that run.
      const parsed = taskEvent.safeParse(data)
      if (parsed.success) {
        void queryClient.invalidateQueries({ queryKey: taskKeys.messages(workspaceId, parsed.data.taskId) })
      }
      break
    }
    case 'inbox.updated':
      // My inbox changed: the list and the badge.
      void queryClient.invalidateQueries({ queryKey: inboxKeys.all(workspaceId) })
      break
  }
}

/**
 * Keeps the open workspace live: while mounted, listens to the workspace's event stream and refreshes the
 * data that other people's changes (and the work of agents) touch. After a lost connection everything of the
 * workspace is refreshed, since events may have been missed meanwhile. Query keys contain the workspace ID, so
 * switching workspaces closes the old stream and opens a new one.
 */
export function useWorkspaceEvents(workspaceId: string) {
  const queryClient = useQueryClient()

  useEffect(
    () =>
      connectEventStream({
        url: `/api/workspaces/${workspaceId}/events`,
        eventNames: EVENT_NAMES,
        onEvent: (name, data) => applyWorkspaceEvent(queryClient, workspaceId, name, data),
        onResync: () => {
          void queryClient.invalidateQueries({ queryKey: ['workspaces', workspaceId] })
          void queryClient.invalidateQueries({ queryKey: queryKeys.workspaces, exact: true })
        },
      }),
    [queryClient, workspaceId],
  )
}
