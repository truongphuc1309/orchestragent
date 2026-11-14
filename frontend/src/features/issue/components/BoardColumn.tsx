import { useDroppable } from '@dnd-kit/core'
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { Plus } from 'lucide-react'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'
import type { IssueEditing } from '../editing'
import { STATUS_LABEL, type Issue, type Status } from '../schemas'
import { StatusLozenge } from './StatusLozenge'
import { SortableIssueCard } from './IssueCard'

export const COLUMN_ID_PREFIX = 'column:'

interface Props {
  status: Status
  issues: Issue[]
  workspaceSlug: string
  /** Project names by project ID; given when the board mixes projects, so each card names its own. */
  projectNames?: ReadonlyMap<string, string>
  editing: IssueEditing
  onCreate?: (status: Status) => void
}

/**
 * One status of the board: a heading with the count, and the cards from top to bottom. The column is as tall as
 * the board; when its cards do not fit, the list of cards scrolls and the heading stays.
 */
export function BoardColumn({ status, issues, workspaceSlug, projectNames, editing, onCreate }: Props) {
  const { setNodeRef, isOver } = useDroppable({ id: `${COLUMN_ID_PREFIX}${status}`, data: { status } })

  return (
    <section
      aria-label={STATUS_LABEL[status]}
      className={cn('flex w-72 shrink-0 flex-col gap-2 rounded-md bg-muted p-2', isOver && 'bg-selected')}
    >
      <header className="flex items-center gap-2 px-1">
        <StatusLozenge status={status} />
        <span className="text-xs text-muted-foreground" aria-label={`${issues.length} issues`}>
          {issues.length}
        </span>
        <span className="flex-1" />
        {onCreate && (
          <Button
            variant="ghost"
            size="icon"
            aria-label={`Create issue in ${STATUS_LABEL[status]}`}
            onClick={() => onCreate(status)}
          >
            <Plus />
          </Button>
        )}
      </header>
      <SortableContext items={issues.map((issue) => issue.id)} strategy={verticalListSortingStrategy}>
        <ul
          ref={setNodeRef}
          // Room around the cards so the scroll area does not cut their shadows.
          className="relative -mx-1 flex min-h-16 flex-1 flex-col gap-2 overflow-y-auto px-1 py-0.5"
        >
          {issues.map((issue) => (
            <SortableIssueCard
              key={issue.id}
              issue={issue}
              status={status}
              workspaceSlug={workspaceSlug}
              projectName={projectNames?.get(issue.projectId)}
              editing={editing}
            />
          ))}
        </ul>
      </SortableContext>
    </section>
  )
}
