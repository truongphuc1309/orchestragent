import { CircleCheck, CircleX, UserPlus, type LucideIcon } from 'lucide-react'
import { Link } from 'react-router'
import { issueKey } from '@/features/issue/key'
import { formatAgo, formatDateTime } from '@/shared/lib/format'
import { cn } from '@/shared/lib/utils'
import { notificationMessage } from '../message'
import type { Notification, NotificationType } from '../schemas'

const ICON: Record<NotificationType, LucideIcon> = {
  issue_assigned: UserPlus,
  agent_completed: CircleCheck,
  agent_failed: CircleX,
}

/** Agents always use the purple "discovery" tone; a person's action stays neutral. */
const ICON_TONE: Record<NotificationType, string> = {
  issue_assigned: 'text-muted-foreground',
  agent_completed: 'text-lozenge-discovery-foreground',
  agent_failed: 'text-destructive',
}

/**
 * One notification: what happened, the issue, when. Unread ones are bold with a dot. Opening one marks it read
 * and goes to the issue.
 */
export function NotificationItem({
  notification,
  workspaceSlug,
  onOpen,
}: {
  notification: Notification
  workspaceSlug: string
  onOpen: (notification: Notification) => void
}) {
  const Icon = ICON[notification.type]
  const unread = notification.readAt === null
  const key = issueKey(workspaceSlug, notification.issueNumber)

  return (
    <li>
      <Link
        to={`/w/${workspaceSlug}/issues/${notification.issueNumber}`}
        onClick={() => onOpen(notification)}
        className={cn(
          'flex items-start gap-3 rounded-md px-3 py-2 text-foreground no-underline hover:bg-accent hover:no-underline',
          unread && 'bg-selected',
        )}
      >
        <Icon aria-hidden="true" className={cn('mt-0.5 size-4 shrink-0', ICON_TONE[notification.type])} />
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className={cn('text-sm', unread && 'font-semibold')}>
            {unread && <span className="sr-only">Unread: </span>}
            {notificationMessage(notification, key)}
          </span>
          <span className="truncate text-sm text-muted-foreground">{notification.issueTitle}</span>
        </span>
        <time
          dateTime={notification.createdAt}
          title={formatDateTime(notification.createdAt)}
          className="shrink-0 text-xs text-muted-foreground"
        >
          {formatAgo(notification.createdAt)}
        </time>
        {unread && <span aria-hidden="true" className="mt-1.5 size-2 shrink-0 rounded-full bg-primary" />}
      </Link>
    </li>
  )
}
