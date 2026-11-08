import { z } from 'zod'
import { apiRequest } from '@/shared/api/client'
import {
  commentSchema,
  issueListSchema,
  issueSchema,
  type AssigneeRef,
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
  filters.projects.forEach((id) => params.append('project', id))
  filters.statuses.forEach((status) => params.append('status', status))
  filters.priorities.forEach((priority) => params.append('priority', priority))
  filters.assignees.forEach((id) => params.append('assignee', id))
  if (filters.unassigned) {
    params.set('unassigned', 'true')
  }
  params.set('limit', String(LIST_LIMIT))
  return apiRequest(`${base(workspaceId)}?${params.toString()}`, { schema: issueListSchema, signal })
}

/** By the key people use, WEB-12. */
export function getIssueByKey(workspaceId: string, key: string, signal?: AbortSignal): Promise<Issue> {
  return apiRequest(`${base(workspaceId)}/key/${encodeURIComponent(key)}`, { schema: issueSchema, signal })
}

export function createIssue(workspaceId: string, issue: NewIssue): Promise<Issue> {
  return apiRequest(base(workspaceId), {
    method: 'POST',
    body: {
      projectId: issue.projectId,
      title: issue.title,
      description: issue.description,
      status: issue.status,
      priority: issue.priority,
      assignee: issue.assignee ?? undefined,
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

/** A null {@code assignee} unassigns. */
export function assignIssue(workspaceId: string, issueId: string, assignee: AssigneeRef | null): Promise<Issue> {
  return apiRequest(`${base(workspaceId)}/${issueId}/assignee`, {
    method: 'PUT',
    body: { assignee },
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

/** The review gate: accepts the agent's work (the issue was in review) and the issue is done. */
export function approveIssue(workspaceId: string, issueId: string): Promise<Issue> {
  return apiRequest(`${base(workspaceId)}/${issueId}/approve`, { method: 'POST', schema: issueSchema })
}

/** The review gate: sends the work back with a comment (required); the issue returns to to-do. */
export function requestChanges(workspaceId: string, issueId: string, comment: string): Promise<Issue> {
  return apiRequest(`${base(workspaceId)}/${issueId}/request-changes`, {
    method: 'POST',
    body: { comment },
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
