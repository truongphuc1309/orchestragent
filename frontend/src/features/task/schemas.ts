import { z } from 'zod'
import { runtimeStatusSchema } from '@/features/agent/schemas'

export const taskStatusSchema = z.enum(['queued', 'dispatched', 'running', 'completed', 'failed', 'cancelled'])
export type TaskStatus = z.infer<typeof taskStatusSchema>

export const triggerSchema = z.enum(['assigned', 'comment', 'changes_requested', 'retry'])
export type Trigger = z.infer<typeof triggerSchema>

export const TRIGGER_LABEL: Record<Trigger, string> = {
  assigned: 'Assigned',
  comment: 'Comment',
  changes_requested: 'Changes requested',
  retry: 'Retry',
}

export const STATUS_LABEL: Record<TaskStatus, string> = {
  queued: 'Queued',
  dispatched: 'Starting',
  running: 'Running',
  completed: 'Completed',
  failed: 'Failed',
  cancelled: 'Cancelled',
}

/** A task that still needs attention: it can be cancelled, and its log may still grow. */
export function isActive(status: TaskStatus): boolean {
  return status === 'queued' || status === 'dispatched' || status === 'running'
}

/** One run of an agent on an issue. */
export const taskSchema = z.object({
  id: z.uuid(),
  issueId: z.uuid(),
  agentId: z.uuid(),
  agentName: z.string(),
  runtime: z
    .object({ id: z.uuid(), deviceName: z.string(), provider: z.string(), status: runtimeStatusSchema })
    .nullable(),
  trigger: triggerSchema,
  status: taskStatusSchema,
  attempt: z.number().int(),
  error: z.string().nullable(),
  failureReason: z.string().nullable(),
  inputTokens: z.number().nullable(),
  outputTokens: z.number().nullable(),
  costUsd: z.number().nullable(),
  createdAt: z.string(),
  startedAt: z.string().nullable(),
  finishedAt: z.string().nullable(),
})
export type Task = z.infer<typeof taskSchema>

export const messageTypeSchema = z.enum(['text', 'tool_use', 'tool_result', 'error', 'system'])
export type MessageType = z.infer<typeof messageTypeSchema>

/** One line of the log of a task. */
export const taskMessageSchema = z.object({
  seq: z.number().int(),
  type: messageTypeSchema,
  content: z.string(),
  createdAt: z.string(),
})
export type TaskMessage = z.infer<typeof taskMessageSchema>
