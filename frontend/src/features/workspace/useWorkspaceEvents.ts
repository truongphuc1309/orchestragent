import { useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { z } from 'zod'
import { connectEventStream } from '@/shared/api/eventStream'
import { issueKeys } from '@/features/issue/hooks'
import { queryKeys } from './hooks'

/** The server events of a workspace (see the issue context's SSE listener) and what they carry. */
const issueEvent = z.object({ issueId: z.uuid() })
const EVENT_NAMES = ['issue.created', 'issue.updated', 'issue.deleted', 'comment.created'] as const

/**
 * Applies one server event to the cache: whatever it touches is marked stale, and TanStack Query fetches it
 * again if some screen is showing it. The events carry no data on purpose, so a repeated or reordered event
 * cannot leave the screen with something older than what the server has.
 */
export function applyWorkspaceEvent(queryClient: QueryClient, workspaceId: string, name: string, data: unknown) {
  const parsed = issueEvent.safeParse(data)
  if (!parsed.success) {
    return
  }
  switch (name) {
    case 'issue.created':
    case 'issue.updated':
    case 'issue.deleted':
      void queryClient.invalidateQueries({ queryKey: issueKeys.lists(workspaceId) })
      void queryClient.invalidateQueries({ queryKey: issueKeys.details(workspaceId) })
      break
    case 'comment.created':
      void queryClient.invalidateQueries({ queryKey: issueKeys.comments(workspaceId, parsed.data.issueId) })
      break
  }
}

/**
 * Keeps the open workspace live: while mounted, listens to the workspace's event stream and refreshes the
 * data that other people's changes touch. After a lost connection everything of the workspace is refreshed,
 * since events may have been missed meanwhile. Query keys contain the workspace ID, so switching workspaces
 * closes the old stream and opens a new one.
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
