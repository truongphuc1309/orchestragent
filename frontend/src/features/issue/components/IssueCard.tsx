import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { FolderKanban } from 'lucide-react'
import { Link } from 'react-router'
import { cn } from '@/shared/lib/utils'
import { assigneeRef } from '../assignee'
import type { IssueEditing } from '../editing'
import type { Issue, Status } from '../schemas'
import { ActorAvatar } from './ActorAvatar'
import { EditableTitleLink } from './EditableTitleLink'
import { AssigneeSelect, PrioritySelect } from './IssueFields'
import { PriorityIcon } from './PriorityIcon'

interface CardProps {
  issue: Issue
  workspaceSlug: string
  /** Shown under the title when the board mixes issues of several projects. */
  projectName?: string
  /** Makes the title, priority and assignee editable. Without it the card only shows them (the drag copy). */
  editing?: IssueEditing
  dragging?: boolean
}

const TITLE_CLASS = 'line-clamp-3 text-sm'

/** What a card shows. Also used for the copy that follows the pointer while dragging. */
export function IssueCardView({ issue, workspaceSlug, projectName, editing, dragging }: CardProps) {
  const href = `/w/${workspaceSlug}/issues/${issue.key}`

  return (
    <div
      className={cn(
        'flex flex-col gap-2 rounded-md bg-card p-2 shadow-raised hover:bg-accent',
        dragging && 'shadow-overlay',
      )}
    >
      {editing ? (
        <EditableTitleLink
          title={issue.title}
          to={href}
          linkClassName={TITLE_CLASS}
          onSave={(title) => editing.setTitle(issue, title)}
        />
      ) : (
        <Link to={href} className={cn(TITLE_CLASS, 'text-foreground no-underline hover:no-underline')} draggable={false}>
          {issue.title}
        </Link>
      )}
      {projectName !== undefined && (
        <span className="flex items-center gap-1 text-xs text-muted-foreground" title={`Project: ${projectName}`}>
          <FolderKanban className="size-3 shrink-0" aria-hidden />
          <span className="truncate">{projectName}</span>
        </span>
      )}
      {editing ? (
        // The menus open on a press, so a press here must not start dragging the card.
        <div className="flex items-center gap-1" onPointerDown={(event) => event.stopPropagation()}>
          <PrioritySelect
            compact
            label="Priority"
            value={issue.priority}
            onChange={(priority) => editing.setPriority(issue, priority)}
          />
          <span className="shrink-0 text-xs whitespace-nowrap text-muted-foreground">{issue.key}</span>
          <span className="flex-1" />
          <AssigneeSelect
            compact
            label="Assignee"
            value={assigneeRef(issue.assignee)}
            members={editing.members}
            agents={editing.agents}
            currentName={issue.assignee?.name}
            currentActor={issue.assignee}
            onChange={(assignee) => editing.setAssignee(issue, assignee)}
          />
        </div>
      ) : (
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
      )}
    </div>
  )
}

/**
 * A card that can be dragged inside its column and into other columns. {@code status} is the column the card is
 * shown in: while it is dragged that may already be another column than the issue's status.
 */
export function SortableIssueCard({
  status,
  ...card
}: Omit<CardProps, 'dragging'> & { status: Status }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: card.issue.id,
    data: { status },
  })
  const { onKeyDown, ...pointerListeners } = listeners ?? {}

  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn('list-none touch-manipulation', isDragging && 'opacity-40')}
      {...attributes}
      {...pointerListeners}
      // Space and Enter start a keyboard drag only on the card itself. Pressed in the title field or on a menu
      // button inside the card they bubble up here, and would start a drag instead of typing or opening the menu.
      onKeyDown={(event) => {
        if (event.target === event.currentTarget) {
          onKeyDown?.(event)
        }
      }}
    >
      <IssueCardView {...card} />
    </li>
  )
}
