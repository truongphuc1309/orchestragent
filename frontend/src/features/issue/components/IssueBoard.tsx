import {
  closestCorners,
  DndContext,
  DragOverlay,
  KeyboardSensor,
  MeasuringStrategy,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragOverEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import { sortableKeyboardCoordinates } from '@dnd-kit/sortable'
import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { z } from 'zod'
import { messageOf } from '@/shared/api/errors'
import { boardColumns, columnOf, dropInto, moveAcross, targetOf, type BoardColumns } from '../board'
import { useMoveIssue } from '../hooks'
import { BOARD_STATUSES, statusSchema, type Issue, type Status } from '../schemas'
import { BoardColumn, COLUMN_ID_PREFIX } from './BoardColumn'
import { IssueCardView } from './IssueCard'

const dropDataSchema = z.object({ status: statusSchema })

interface Props {
  workspaceId: string
  workspaceSlug: string
  issues: Issue[]
  /** Missing when no issue can be created here (an archived project): the columns then have no "+" button. */
  onCreate?: (status: Status) => void
}

/**
 * The order the board shows instead of the one the issues give, while a card is dragged and right after it is
 * dropped. During a drag the card already sits in the column it is over, so that column opens a gap and the drop
 * animation flies to the right place. After the drop the new order is kept until the issues change (the cache gets
 * the same move a moment later), so the board never shows the old order in between.
 */
interface Arrangement {
  columns: BoardColumns
  /** The issues the arrangement was made from; after the drop it counts only while they are still the same. */
  base: readonly Issue[]
  dragging: boolean
}

/** Rects of the columns change while cards move between them, so dnd-kit measures them all the time. */
const MEASURING = { droppable: { strategy: MeasuringStrategy.Always } }

/** The kanban board: six columns, cards dragged with the mouse, touch or keyboard. */
export function IssueBoard({ workspaceId, workspaceSlug, issues, onCreate }: Props) {
  const move = useMoveIssue(workspaceId)
  const [dragged, setDragged] = useState<Issue | null>(null)
  const [arrangement, setArrangement] = useState<Arrangement | null>(null)
  const sensors = useSensors(
    // A small distance keeps a plain click on a card (opening it) from turning into a drag.
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const fromIssues = useMemo(() => boardColumns(issues), [issues])
  const byId = useMemo(() => new Map(issues.map((issue) => [issue.id, issue])), [issues])
  const shown =
    arrangement !== null && (arrangement.dragging || arrangement.base === issues) ? arrangement.columns : fromIssues

  const onDragStart = (event: DragStartEvent) => {
    setDragged(byId.get(String(event.active.id)) ?? null)
    setArrangement({ columns: fromIssues, base: issues, dragging: true })
  }

  const onDragOver = ({ active, over }: DragOverEvent) => {
    if (over === null) {
      return
    }
    const data = dropDataSchema.safeParse(over.data.current)
    if (!data.success) {
      return
    }
    const overId = String(over.id)
    const overCard = overId.startsWith(COLUMN_ID_PREFIX) ? null : overId
    // Over a card, the dragged card goes after it when its middle is below the middle of that card.
    const translated = active.rect.current.translated
    const below =
      overCard !== null &&
      translated !== null &&
      translated.top + translated.height / 2 > over.rect.top + over.rect.height / 2
    setArrangement((current) => {
      if (current === null || !current.dragging) {
        return current
      }
      const next = moveAcross(current.columns, String(active.id), data.data.status, overCard, below)
      // Returning the same object tells React nothing changed, so hovering inside one column renders nothing.
      return next === current.columns ? current : { ...current, columns: next }
    })
  }

  const onDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    const activeId = String(active.id)
    const columns = arrangement?.dragging ? arrangement.columns : fromIssues
    setDragged(null)
    const data = over === null ? null : dropDataSchema.safeParse(over.data.current)
    const picked = byId.get(activeId)
    if (over === null || data === null || !data.success || picked === undefined) {
      setArrangement(null)
      return
    }
    const overId = String(over.id)
    const overCard = overId.startsWith(COLUMN_ID_PREFIX) ? null : overId
    const final = dropInto(columns, activeId, data.data.status, overCard, picked.status)
    const target = columnOf(final, activeId) === null ? null : targetOf(fromIssues, final, activeId)
    if (target === null) {
      setArrangement(null)
      return
    }
    // Shown in this very render, with the drag ending: the card stays where it was dropped.
    setArrangement({ columns: final, base: issues, dragging: false })
    move.mutate(
      { issueId: activeId, target },
      { onError: (error) => toast.error(messageOf(error, 'Could not move the issue.')) },
    )
  }

  const onDragCancel = () => {
    setDragged(null)
    setArrangement(null)
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCorners}
      measuring={MEASURING}
      onDragStart={onDragStart}
      onDragOver={onDragOver}
      onDragEnd={onDragEnd}
      onDragCancel={onDragCancel}
    >
      <div className="relative flex items-start gap-3 overflow-x-auto pb-4">
        {BOARD_STATUSES.map((status) => (
          <BoardColumn
            key={status}
            status={status}
            issues={shown[status].flatMap((id) => byId.get(id) ?? [])}
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
