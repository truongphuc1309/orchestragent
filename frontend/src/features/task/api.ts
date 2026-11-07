import { z } from 'zod'
import { apiRequest } from '@/shared/api/client'
import { taskMessageSchema, taskSchema, type Task, type TaskMessage } from './schemas'

const base = (workspaceId: string) => `/api/workspaces/${workspaceId}/tasks`

/** The runs of an issue, newest first. */
export function listTasks(workspaceId: string, issueId: string, signal?: AbortSignal): Promise<Task[]> {
  return apiRequest(`${base(workspaceId)}?issueId=${issueId}`, { schema: z.array(taskSchema), signal })
}

/** The server returns at most this many log lines per call. */
export const MESSAGES_PER_CALL = 500

/** Log lines after the given number ({@code -1} for from the start). */
export function listTaskMessages(
  workspaceId: string,
  taskId: string,
  after: number,
  signal?: AbortSignal,
): Promise<TaskMessage[]> {
  return apiRequest(`${base(workspaceId)}/${taskId}/messages?after=${after}`, {
    schema: z.array(taskMessageSchema),
    signal,
  })
}

export function cancelTask(workspaceId: string, taskId: string): Promise<Task> {
  return apiRequest(`${base(workspaceId)}/${taskId}/cancel`, { method: 'POST', schema: taskSchema })
}

export function retryTask(workspaceId: string, taskId: string): Promise<Task> {
  return apiRequest(`${base(workspaceId)}/${taskId}/retry`, { method: 'POST', schema: taskSchema })
}
