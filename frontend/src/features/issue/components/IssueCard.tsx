import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { Link } from 'react-router'
import { cn } from '@/shared/lib/utils'
import type { Issue, Status } from '../schemas'
import { ActorAvatar } from './ActorAvatar'
import { PriorityIcon } from './PriorityIcon'

/** What a card shows. Also used for the copy that follows the pointer while dragging. */
export function IssueCardView({ issue, workspaceSlug, dragging }: { issue: Issue; workspaceSlug: string; dragging?: boolean }) {
  return (
    <div
      className={cn(
        'flex flex-col gap-2 rounded-md bg-card p-2 shadow-raised hover:bg-accent',
        dragging && 'shadow-overlay',
      )}
    >
      <Link
        to={`/w/${workspaceSlug}/issues/${issue.key}`}
        className="line-clamp-3 text-sm text-foreground no-underline hover:no-underline"
        draggable={false}
      >
        {issue.title}
      </Link>
      <div className="flex items-center gap-2">
        <PriorityIcon priority={issue.priority} />
        <span className="text-xs text-muted-foreground">{issue.key}</span>
        <span className="flex-1" />
        {issue.assignee && (
          <span title={issue.assignee.name}>
            <ActorAvatar actor={issue.assignee} />
            <span className="sr-only">Assigned to {issue.assignee.name}</span>
          </span>
        )}
      </div>
    </div>
  )
}

/**
 * A card that can be dragged inside its column and into other columns. {@code status} is the column the card is
 * shown in: while it is dragged that may already be another column than the issue's status.
 */
export function SortableIssueCard({ issue, status, workspaceSlug }: { issue: Issue; status: Status; workspaceSlug: string }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: issue.id,
    data: { status },
  })

  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn('list-none touch-manipulation', isDragging && 'opacity-40')}
      {...attributes}
      {...listeners}
    >
      <IssueCardView issue={issue} workspaceSlug={workspaceSlug} />
    </li>
  )
}
