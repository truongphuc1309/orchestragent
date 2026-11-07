import { z } from 'zod'
import { apiRequest } from '@/shared/api/client'
import {
  commentSchema,
  issueListSchema,
  issueSchema,
  type Comment,
  type Issue,
  type IssueList,
  type MoveTarget,
  type NewIssue,
  type Priority,
  type Status,
} from './schemas'
import type { IssueFilters } from './filters'

const base = (workspaceId: string) => `/api/workspaces/${workspaceId}/issues`

/** The server returns at most this many issues per call; `total` says how many match. */
export const LIST_LIMIT = 500

export function listIssues(workspaceId: string, filters: IssueFilters, signal?: AbortSignal): Promise<IssueList> {
  const params = new URLSearchParams()
  filters.statuses.forEach((status) => params.append('status', status))
  filters.priorities.forEach((priority) => params.append('priority', priority))
  filters.assignees.forEach((id) => params.append('assignee', id))
  if (filters.unassigned) {
    params.set('unassigned', 'true')
  }
  params.set('limit', String(LIST_LIMIT))
  return apiRequest(`${base(workspaceId)}?${params.toString()}`, { schema: issueListSchema, signal })
}

export function getIssueByNumber(workspaceId: string, number: number, signal?: AbortSignal): Promise<Issue> {
  return apiRequest(`${base(workspaceId)}/number/${number}`, { schema: issueSchema, signal })
}

export function createIssue(workspaceId: string, issue: NewIssue): Promise<Issue> {
  return apiRequest(base(workspaceId), {
    method: 'POST',
    body: {
      title: issue.title,
      description: issue.description,
      status: issue.status,
      priority: issue.priority,
      assignee: issue.assigneeId === null ? undefined : { type: 'member', id: issue.assigneeId },
    },
    schema: issueSchema,
  })
}

export interface IssueChanges {
  title?: string
  description?: string
  status?: Status
  priority?: Priority
}

export function updateIssue(workspaceId: string, issueId: string, changes: IssueChanges): Promise<Issue> {
  return apiRequest(`${base(workspaceId)}/${issueId}`, { method: 'PATCH', body: changes, schema: issueSchema })
}

/** A null {@code assigneeId} unassigns. */
export function assignIssue(workspaceId: string, issueId: string, assigneeId: string | null): Promise<Issue> {
  return apiRequest(`${base(workspaceId)}/${issueId}/assignee`, {
    method: 'PUT',
    body: { assignee: assigneeId === null ? null : { type: 'member', id: assigneeId } },
    schema: issueSchema,
  })
}

export function moveIssue(workspaceId: string, issueId: string, target: MoveTarget): Promise<Issue> {
  return apiRequest(`${base(workspaceId)}/${issueId}/move`, {
    method: 'POST',
    body: {
      status: target.status,
      aboveId: target.aboveId ?? undefined,
      belowId: target.belowId ?? undefined,
    },
    schema: issueSchema,
  })
}

export function deleteIssue(workspaceId: string, issueId: string): Promise<void> {
  return apiRequest(`${base(workspaceId)}/${issueId}`, { method: 'DELETE', schema: z.undefined() })
}

export function listComments(workspaceId: string, issueId: string, signal?: AbortSignal): Promise<Comment[]> {
  return apiRequest(`${base(workspaceId)}/${issueId}/comments`, { schema: z.array(commentSchema), signal })
}

export function addComment(workspaceId: string, issueId: string, body: string): Promise<Comment> {
  return apiRequest(`${base(workspaceId)}/${issueId}/comments`, { method: 'POST', body: { body }, schema: commentSchema })
}
