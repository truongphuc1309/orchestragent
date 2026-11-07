import { z } from 'zod'

export const notificationTypeSchema = z.enum(['issue_assigned', 'agent_completed', 'agent_failed'])
export type NotificationType = z.infer<typeof notificationTypeSchema>

/**
 * Something the signed-in person should know about an issue. It carries a copy of the issue number and title and
 * of who did it, so it still reads right after they changed. {@code detail} is the failure reason of a failed run.
 */
export const notificationSchema = z.object({
  id: z.uuid(),
  type: notificationTypeSchema,
  issueId: z.uuid(),
  issueNumber: z.number().int(),
  issueTitle: z.string(),
  actorName: z.string(),
  detail: z.string().nullable(),
  createdAt: z.string(),
  readAt: z.string().nullable(),
})
export type Notification = z.infer<typeof notificationSchema>

export const inboxSchema = z.object({
  notifications: z.array(notificationSchema),
  unreadCount: z.number().int().nonnegative(),
})
export type Inbox = z.infer<typeof inboxSchema>

export const unreadCountSchema = z.object({ unreadCount: z.number().int().nonnegative() })
