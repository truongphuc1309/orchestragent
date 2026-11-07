import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getInbox, getUnreadCount, markAllRead, markRead } from './api'

/** Every key contains the workspace ID, so two workspaces never share an inbox in the cache. */
export const inboxKeys = {
  all: (workspaceId: string) => ['workspaces', workspaceId, 'inbox'] as const,
  list: (workspaceId: string) => [...inboxKeys.all(workspaceId), 'list'] as const,
  unread: (workspaceId: string) => [...inboxKeys.all(workspaceId), 'unread'] as const,
}

/** The inbox. It refreshes when the server says it changed (inbox.updated, see useWorkspaceEvents). */
export function useInbox(workspaceId: string) {
  return useQuery({
    queryKey: inboxKeys.list(workspaceId),
    queryFn: ({ signal }) => getInbox(workspaceId, signal),
  })
}

/** The number on the badge in the navigation. */
export function useUnreadCount(workspaceId: string) {
  return useQuery({
    queryKey: inboxKeys.unread(workspaceId),
    queryFn: ({ signal }) => getUnreadCount(workspaceId, signal),
  })
}

function useRefreshInbox(workspaceId: string) {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: inboxKeys.all(workspaceId) })
}

export function useMarkRead(workspaceId: string) {
  const refresh = useRefreshInbox(workspaceId)
  return useMutation({ mutationFn: (notificationId: string) => markRead(workspaceId, notificationId), onSuccess: refresh })
}

export function useMarkAllRead(workspaceId: string) {
  const refresh = useRefreshInbox(workspaceId)
  return useMutation({ mutationFn: () => markAllRead(workspaceId), onSuccess: refresh })
}
