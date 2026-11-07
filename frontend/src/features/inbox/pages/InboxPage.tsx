import { CheckCheck } from 'lucide-react'
import { useWorkspace } from '@/features/workspace/workspaceContext'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { NotificationItem } from '../components/NotificationItem'
import { useInbox, useMarkAllRead, useMarkRead } from '../hooks'
import type { Notification } from '../schemas'

/** The signed-in person's notifications in this workspace, newest first. */
export function InboxPage() {
  const workspace = useWorkspace()
  const inbox = useInbox(workspace.id)
  const markRead = useMarkRead(workspace.id)
  const markAllRead = useMarkAllRead(workspace.id)

  const open = (notification: Notification) => {
    if (notification.readAt === null) {
      markRead.mutate(notification.id)
    }
  }

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div className="flex items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl leading-7 font-medium">Inbox</h1>
          <p className="text-muted-foreground">
            Issues assigned to you, and the work of agents on issues you created.
          </p>
        </div>
        <Button
          variant="secondary"
          onClick={() => markAllRead.mutate()}
          disabled={!inbox.isSuccess || inbox.data.unreadCount === 0 || markAllRead.isPending}
        >
          <CheckCheck />
          Mark all as read
        </Button>
      </div>

      {inbox.isPending ? (
        <div className="flex flex-col gap-2" aria-busy="true">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
          <span className="sr-only">Loading the inbox…</span>
        </div>
      ) : inbox.isError ? (
        <div role="alert" className="flex items-center gap-3 text-sm text-destructive">
          Could not load the inbox.
          <Button variant="secondary" size="sm" onClick={() => void inbox.refetch()}>
            Try again
          </Button>
        </div>
      ) : inbox.data.notifications.length === 0 ? (
        <p className="rounded-md border border-dashed p-6 text-center text-muted-foreground">
          Nothing here yet. You will be told when someone assigns you an issue, and when an agent finishes or fails on an
          issue you created.
        </p>
      ) : (
        <ul aria-label="Notifications" className="m-0 flex list-none flex-col gap-1 p-0">
          {inbox.data.notifications.map((notification) => (
            <NotificationItem
              key={notification.id}
              notification={notification}
              workspaceSlug={workspace.slug}
              onOpen={open}
            />
          ))}
        </ul>
      )}
    </div>
  )
}
