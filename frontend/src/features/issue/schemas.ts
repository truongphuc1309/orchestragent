import { z } from 'zod'

export const statusSchema = z.enum(['backlog', 'todo', 'in_progress', 'in_review', 'done', 'blocked', 'cancelled'])
export type Status = z.infer<typeof statusSchema>
export const STATUSES: readonly Status[] = statusSchema.options

export const STATUS_LABEL: Record<Status, string> = {
  backlog: 'Backlog',
  todo: 'To do',
  in_progress: 'In progress',
  in_review: 'In review',
  done: 'Done',
  blocked: 'Blocked',
  cancelled: 'Cancelled',
}

/** The board has six columns; cancelled issues only show in the list. */
export const BOARD_STATUSES: readonly Status[] = ['backlog', 'todo', 'in_progress', 'in_review', 'blocked', 'done']

export const prioritySchema = z.enum(['none', 'low', 'medium', 'high', 'urgent'])
export type Priority = z.infer<typeof prioritySchema>
/** From most to least urgent, the order people expect in a menu. */
export const PRIORITIES: readonly Priority[] = ['urgent', 'high', 'medium', 'low', 'none']

export const PRIORITY_LABEL: Record<Priority, string> = {
  none: 'No priority',
  low: 'Low',
  medium: 'Medium',
  high: 'High',
  urgent: 'Urgent',
}

export const actorTypeSchema = z.enum(['member', 'agent', 'system'])
export type ActorType = z.infer<typeof actorTypeSchema>

/** A member, an agent or the system. The ID is null for the system. */
export const actorSchema = z.object({
  type: actorTypeSchema,
  id: z.uuid().nullable(),
  name: z.string(),
})
export type Actor = z.infer<typeof actorSchema>

export const issueSchema = z.object({
  id: z.uuid(),
  number: z.number().int(),
  title: z.string(),
  description: z.string(),
  status: statusSchema,
  priority: prioritySchema,
  assignee: actorSchema.nullable(),
  creator: actorSchema,
  position: z.number(),
  createdAt: z.string(),
  updatedAt: z.string(),
})
export type Issue = z.infer<typeof issueSchema>

export const issueListSchema = z.object({
  items: z.array(issueSchema),
  total: z.number().int(),
})
export type IssueList = z.infer<typeof issueListSchema>

export const commentSchema = z.object({
  id: z.uuid(),
  issueId: z.uuid(),
  author: actorSchema,
  body: z.string(),
  createdAt: z.string(),
})
export type Comment = z.infer<typeof commentSchema>

export const MAX_TITLE = 200
export const MAX_DESCRIPTION = 20_000
export const MAX_COMMENT = 10_000

export const titleSchema = z
  .string()
  .trim()
  .min(1, 'Enter a title')
  .max(MAX_TITLE, `Use at most ${MAX_TITLE} characters`)

export const createIssueFormSchema = z.object({
  title: titleSchema,
  description: z.string().max(MAX_DESCRIPTION, `Use at most ${MAX_DESCRIPTION} characters`),
  status: statusSchema,
  priority: prioritySchema,
  /** The assignee as encoded by assignee.ts (member:<id>, agent:<id>), or the empty string for nobody. */
  assignee: z.string(),
})
export type CreateIssueForm = z.infer<typeof createIssueFormSchema>

export const commentFormSchema = z.object({
  body: z.string().trim().min(1, 'Write something first').max(MAX_COMMENT, `Use at most ${MAX_COMMENT} characters`),
})
export type CommentForm = z.infer<typeof commentFormSchema>

/** A member (user ID) or an agent (agent ID) an issue is assigned to. */
export interface AssigneeRef {
  type: 'member' | 'agent'
  id: string
}

/** What the create dialog sends; it mirrors the server's request. */
export interface NewIssue {
  title: string
  description: string
  status: Status
  priority: Priority
  assignee: AssigneeRef | null
}

/** Where a dragged issue lands: a column and the issues directly above and below the drop spot. */
export interface MoveTarget {
  status: Status
  aboveId: string | null
  belowId: string | null
}
