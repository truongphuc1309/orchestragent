import { z } from 'zod'
import { apiRequest } from '@/shared/api/client'
import { inboxSchema, notificationSchema, unreadCountSchema, type Inbox, type Notification } from './schemas'

const base = (workspaceId: string) => `/api/workspaces/${workspaceId}/inbox`

/** The newest notifications of the signed-in person (at most 100) and how many are unread. */
export function getInbox(workspaceId: string, signal?: AbortSignal): Promise<Inbox> {
  return apiRequest(base(workspaceId), { schema: inboxSchema, signal })
}

export async function getUnreadCount(workspaceId: string, signal?: AbortSignal): Promise<number> {
  const { unreadCount } = await apiRequest(`${base(workspaceId)}/unread-count`, { schema: unreadCountSchema, signal })
  return unreadCount
}

export function markRead(workspaceId: string, notificationId: string): Promise<Notification> {
  return apiRequest(`${base(workspaceId)}/${notificationId}/read`, { method: 'POST', schema: notificationSchema })
}

export function markAllRead(workspaceId: string): Promise<void> {
  return apiRequest(`${base(workspaceId)}/read-all`, { method: 'POST', schema: z.undefined() })
}
