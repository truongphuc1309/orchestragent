import {
  closestCorners,
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import { sortableKeyboardCoordinates } from '@dnd-kit/sortable'
import { useState } from 'react'
import { toast } from 'sonner'
import { z } from 'zod'
import { messageOf } from '@/shared/api/errors'
import { column, moveTarget } from '../board'
import { useMoveIssue } from '../hooks'
import { BOARD_STATUSES, statusSchema, type Issue, type Status } from '../schemas'
import { BoardColumn, COLUMN_ID_PREFIX } from './BoardColumn'
import { IssueCardView } from './IssueCard'

const dropDataSchema = z.object({ status: statusSchema })

interface Props {
  workspaceId: string
  workspaceSlug: string
  issues: Issue[]
  onCreate: (status: Status) => void
}

/** The kanban board: six columns, cards dragged with the mouse, touch or keyboard. */
export function IssueBoard({ workspaceId, workspaceSlug, issues, onCreate }: Props) {
  const move = useMoveIssue(workspaceId)
  const [dragged, setDragged] = useState<Issue | null>(null)
  const sensors = useSensors(
    // A small distance keeps a plain click on a card (opening it) from turning into a drag.
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const onDragStart = (event: DragStartEvent) => {
    setDragged(issues.find((issue) => issue.id === event.active.id) ?? null)
  }

  const onDragEnd = (event: DragEndEvent) => {
    setDragged(null)
    const { active, over } = event
    if (over === null) {
      return
    }
    const data = dropDataSchema.safeParse(over.data.current)
    if (!data.success) {
      return
    }
    const overId = String(over.id)
    const target = moveTarget(
      issues,
      String(active.id),
      data.data.status,
      overId.startsWith(COLUMN_ID_PREFIX) ? null : overId,
    )
    if (target === null) {
      return
    }
    move.mutate(
      { issueId: String(active.id), target },
      { onError: (error) => toast.error(messageOf(error, 'Could not move the issue.')) },
    )
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCorners}
      onDragStart={onDragStart}
      onDragEnd={onDragEnd}
      onDragCancel={() => setDragged(null)}
    >
      <div className="flex items-start gap-3 overflow-x-auto pb-4">
        {BOARD_STATUSES.map((status) => (
          <BoardColumn
            key={status}
            status={status}
            issues={column(issues, status)}
            workspaceSlug={workspaceSlug}
            onCreate={onCreate}
          />
        ))}
      </div>
      <DragOverlay>
        {dragged && <IssueCardView issue={dragged} workspaceSlug={workspaceSlug} dragging />}
      </DragOverlay>
    </DndContext>
  )
}
