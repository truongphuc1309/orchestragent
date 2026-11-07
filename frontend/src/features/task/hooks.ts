import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cancelTask, listTaskMessages, listTasks, MESSAGES_PER_CALL, retryTask } from './api'
import type { TaskMessage } from './schemas'

/** Every key contains the workspace ID, so tasks of two workspaces never share a cache entry. */
export const taskKeys = {
  all: (workspaceId: string) => ['workspaces', workspaceId, 'tasks'] as const,
  list: (workspaceId: string, issueId: string) => [...taskKeys.all(workspaceId), 'list', issueId] as const,
  lists: (workspaceId: string) => [...taskKeys.all(workspaceId), 'list'] as const,
  messages: (workspaceId: string, taskId: string) => [...taskKeys.all(workspaceId), 'messages', taskId] as const,
}

/** The runs of an issue, newest first. They refresh when the server announces a change (see useWorkspaceEvents). */
export function useTasks(workspaceId: string, issueId: string) {
  return useQuery({
    queryKey: taskKeys.list(workspaceId, issueId),
    queryFn: ({ signal }) => listTasks(workspaceId, issueId, signal),
  })
}

/** At most this many pages of log lines are read in one go; a runaway log must not freeze the page. */
const MAX_PAGES = 10

/**
 * The log of a task. Each refresh asks only for the lines after the last one it already has and appends them,
 * so following a running task costs a few lines per update, not the whole log every time.
 */
export function useTaskMessages(workspaceId: string, taskId: string, enabled: boolean) {
  const queryClient = useQueryClient()
  const key = taskKeys.messages(workspaceId, taskId)
  return useQuery({
    queryKey: key,
    enabled,
    queryFn: async ({ signal }) => {
      const known = queryClient.getQueryData<TaskMessage[]>(key) ?? []
      const all = [...known]
      for (let page = 0; page < MAX_PAGES; page++) {
        const after = all.length === 0 ? -1 : (all[all.length - 1]?.seq ?? -1)
        const batch = await listTaskMessages(workspaceId, taskId, after, signal)
        all.push(...batch)
        if (batch.length < MESSAGES_PER_CALL) {
          break
        }
      }
      return all
    },
  })
}

/** A cancelled or retried task changes the issue too (its status and the runs list), so both refresh. */
function useRefreshAfterChange(workspaceId: string) {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: taskKeys.lists(workspaceId) }),
      queryClient.invalidateQueries({ queryKey: ['workspaces', workspaceId, 'issues'] }),
    ])
}

export function useCancelTask(workspaceId: string) {
  const refresh = useRefreshAfterChange(workspaceId)
  return useMutation({ mutationFn: (taskId: string) => cancelTask(workspaceId, taskId), onSuccess: refresh })
}

export function useRetryTask(workspaceId: string) {
  const refresh = useRefreshAfterChange(workspaceId)
  return useMutation({ mutationFn: (taskId: string) => retryTask(workspaceId, taskId), onSuccess: refresh })
}
