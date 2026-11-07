import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addComment,
  assignIssue,
  createIssue,
  deleteIssue,
  getIssueByNumber,
  listComments,
  listIssues,
  moveIssue,
  updateIssue,
  type IssueChanges,
} from './api'
import { applyMove } from './board'
import type { IssueFilters } from './filters'
import type { AssigneeRef, Issue, IssueList, MoveTarget, NewIssue } from './schemas'

/** Every key contains the workspace ID, so issues of two workspaces never share cache entries. */
export const issueKeys = {
  all: (workspaceId: string) => ['workspaces', workspaceId, 'issues'] as const,
  list: (workspaceId: string, filters: IssueFilters) => [...issueKeys.all(workspaceId), 'list', filters] as const,
  details: (workspaceId: string) => [...issueKeys.all(workspaceId), 'detail'] as const,
  lists: (workspaceId: string) => [...issueKeys.all(workspaceId), 'list'] as const,
  detail: (workspaceId: string, number: number | null) => [...issueKeys.all(workspaceId), 'detail', number] as const,
  comments: (workspaceId: string, issueId: string) => [...issueKeys.all(workspaceId), 'comments', issueId] as const,
}

export function useIssues(workspaceId: string, filters: IssueFilters) {
  return useQuery({
    queryKey: issueKeys.list(workspaceId, filters),
    queryFn: ({ signal }) => listIssues(workspaceId, filters, signal),
  })
}

/** {@code number} is null when the URL does not hold a valid issue number; nothing is requested then. */
export function useIssue(workspaceId: string, number: number | null) {
  return useQuery({
    queryKey: issueKeys.detail(workspaceId, number),
    queryFn: ({ signal }) => getIssueByNumber(workspaceId, number ?? 0, signal),
    enabled: number !== null,
    // A missing issue will not appear by asking again.
    retry: false,
  })
}

export function useCreateIssue(workspaceId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (issue: NewIssue) => createIssue(workspaceId, issue),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: issueKeys.lists(workspaceId) }),
  })
}

/** After the server confirms an edit: show the fresh issue on its page and refresh the lists. */
function useSyncIssue(workspaceId: string) {
  const queryClient = useQueryClient()
  return (issue: Issue) => {
    queryClient.setQueryData(issueKeys.detail(workspaceId, issue.number), issue)
    return queryClient.invalidateQueries({ queryKey: issueKeys.lists(workspaceId) })
  }
}

export function useUpdateIssue(workspaceId: string) {
  const sync = useSyncIssue(workspaceId)
  return useMutation({
    mutationFn: ({ issueId, changes }: { issueId: string; changes: IssueChanges }) =>
      updateIssue(workspaceId, issueId, changes),
    onSuccess: sync,
  })
}

export function useAssignIssue(workspaceId: string) {
  const sync = useSyncIssue(workspaceId)
  return useMutation({
    mutationFn: ({ issueId, assignee }: { issueId: string; assignee: AssigneeRef | null }) =>
      assignIssue(workspaceId, issueId, assignee),
    onSuccess: sync,
  })
}

/**
 * Dragging on the board feels instant: the card moves in every cached list at once, and the server's answer
 * (or an error, which puts everything back) arrives afterwards.
 */
export function useMoveIssue(workspaceId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ issueId, target }: { issueId: string; target: MoveTarget }) => moveIssue(workspaceId, issueId, target),
    onMutate: async ({ issueId, target }) => {
      await queryClient.cancelQueries({ queryKey: issueKeys.lists(workspaceId) })
      const before = queryClient.getQueriesData<IssueList>({ queryKey: issueKeys.lists(workspaceId) })
      queryClient.setQueriesData<IssueList>({ queryKey: issueKeys.lists(workspaceId) }, (list) =>
        list === undefined ? list : { ...list, items: applyMove(list.items, issueId, target) },
      )
      return { before }
    },
    onError: (_error, _variables, context) => {
      context?.before.forEach(([key, data]) => queryClient.setQueryData(key, data))
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: issueKeys.all(workspaceId) }),
  })
}

export function useDeleteIssue(workspaceId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (issueId: string) => deleteIssue(workspaceId, issueId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: issueKeys.lists(workspaceId) }),
  })
}

export function useComments(workspaceId: string, issueId: string) {
  return useQuery({
    queryKey: issueKeys.comments(workspaceId, issueId),
    queryFn: ({ signal }) => listComments(workspaceId, issueId, signal),
  })
}

export function useAddComment(workspaceId: string, issueId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: string) => addComment(workspaceId, issueId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: issueKeys.comments(workspaceId, issueId) }),
  })
}
