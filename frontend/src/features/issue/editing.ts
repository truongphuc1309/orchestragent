import { toast } from 'sonner'
import { useAgents } from '@/features/agent/hooks'
import type { Agent } from '@/features/agent/schemas'
import { useMembers } from '@/features/workspace/hooks'
import type { Member } from '@/features/workspace/schemas'
import { messageOf } from '@/shared/api/errors'
import { useAssignIssue, useUpdateIssue } from './hooks'
import type { AssigneeRef, Issue, Priority, Status } from './schemas'

/**
 * What a card or a list row needs to change an issue in place: the people an issue can go to, and one function
 * per field. Every function reports a failure with a toast; the issue then keeps its old value, because the
 * lists only change when the server confirms.
 */
export interface IssueEditing {
  members: readonly Member[]
  agents: readonly Agent[]
  setTitle: (issue: Issue, title: string) => void
  setStatus: (issue: Issue, status: Status) => void
  setPriority: (issue: Issue, priority: Priority) => void
  setAssignee: (issue: Issue, assignee: AssigneeRef | null) => void
}

export function useIssueEditing(workspaceId: string): IssueEditing {
  const members = useMembers(workspaceId)
  const agents = useAgents(workspaceId)
  const update = useUpdateIssue(workspaceId)
  const assign = useAssignIssue(workspaceId)

  const onError = (fallback: string) => ({ onError: (error: unknown) => toast.error(messageOf(error, fallback)) })

  return {
    members: members.data ?? [],
    agents: agents.data ?? [],
    setTitle: (issue, title) =>
      update.mutate({ issueId: issue.id, changes: { title } }, onError('Could not change the title.')),
    setStatus: (issue, status) =>
      update.mutate({ issueId: issue.id, changes: { status } }, onError('Could not change the status.')),
    setPriority: (issue, priority) =>
      update.mutate({ issueId: issue.id, changes: { priority } }, onError('Could not change the priority.')),
    setAssignee: (issue, assignee) =>
      assign.mutate({ issueId: issue.id, assignee }, onError('Could not change the assignee.')),
  }
}
