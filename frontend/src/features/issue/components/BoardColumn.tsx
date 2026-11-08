import { useDroppable } from '@dnd-kit/core'
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { Plus } from 'lucide-react'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'
import { STATUS_LABEL, type Issue, type Status } from '../schemas'
import { StatusLozenge } from './StatusLozenge'
import { SortableIssueCard } from './IssueCard'

export const COLUMN_ID_PREFIX = 'column:'

interface Props {
  status: Status
  issues: Issue[]
  workspaceSlug: string
  onCreate?: (status: Status) => void
}

/** One status of the board: a heading with the count, and the cards from top to bottom. */
export function BoardColumn({ status, issues, workspaceSlug, onCreate }: Props) {
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
        <ul ref={setNodeRef} className="flex min-h-16 flex-1 flex-col gap-2 p-0">
          {issues.map((issue) => (
            <SortableIssueCard key={issue.id} issue={issue} status={status} workspaceSlug={workspaceSlug} />
          ))}
        </ul>
      </SortableContext>
    </section>
  )
}
