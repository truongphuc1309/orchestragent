import { arrayMove } from '@dnd-kit/sortable'
import { STATUSES, type Issue, type MoveTarget, type Status } from './schemas'

/** Gap used when an issue goes above the first or below the last one; the server uses the same number. */
const STEP = 1024

export function byPosition(a: Issue, b: Issue): number {
  return a.position - b.position || a.createdAt.localeCompare(b.createdAt)
}

/** The issues of one status from top to bottom. */
export function column(issues: readonly Issue[], status: Status): Issue[] {
  return issues.filter((issue) => issue.status === status).sort(byPosition)
}

/** Position for an issue dropped between two neighbours (the same arithmetic as the server). */
export function positionBetween(above: number | null, below: number | null): number {
  if (above === null && below === null) {
    return STEP
  }
  if (above === null) {
    return (below ?? 0) - STEP
  }
  if (below === null) {
    return above + STEP
  }
  return above + (below - above) / 2
}

/** The card IDs of every column from top to bottom: the board as it is shown. */
export type BoardColumns = Record<Status, string[]>

export function boardColumns(issues: readonly Issue[]): BoardColumns {
  const columns: BoardColumns = { backlog: [], todo: [], in_progress: [], in_review: [], done: [], blocked: [], cancelled: [] }
  for (const status of STATUSES) {
    columns[status] = column(issues, status).map((issue) => issue.id)
  }
  return columns
}

/** The column a card is shown in, or null when it is not on the board. */
export function columnOf(columns: BoardColumns, id: string): Status | null {
  return STATUSES.find((status) => columns[status].includes(id)) ?? null
}

/**
 * While a card is dragged over another column, it moves there at once, so that column opens a gap where the card
 * will land and its old column closes up. It goes before the card it is over ({@code below}: after it), or to the
 * bottom when it is over the column itself. Moves inside one column are left to the sortable animation.
 */
export function moveAcross(
  columns: BoardColumns,
  activeId: string,
  overStatus: Status,
  overId: string | null,
  below = false,
): BoardColumns {
  const from = columnOf(columns, activeId)
  if (from === null || from === overStatus) {
    return columns
  }
  const target = columns[overStatus]
  const overIndex = overId === null ? -1 : target.indexOf(overId)
  const insertAt = overIndex < 0 ? target.length : overIndex + (below ? 1 : 0)
  return {
    ...columns,
    [from]: columns[from].filter((id) => id !== activeId),
    [overStatus]: [...target.slice(0, insertAt), activeId, ...target.slice(insertAt)],
  }
}

/**
 * Where the card ends up when it is dropped. {@code startStatus} is the column it was picked up from: dropped on the
 * background of that column it goes to the bottom; dropped on the background of a column it was dragged into, it
 * stays where the drag put it.
 */
export function dropInto(
  columns: BoardColumns,
  activeId: string,
  overStatus: Status,
  overId: string | null,
  startStatus: Status,
): BoardColumns {
  const from = columnOf(columns, activeId)
  if (from === null || overId === activeId) {
    return columns
  }
  if (from !== overStatus) {
    return moveAcross(columns, activeId, overStatus, overId)
  }
  const cards = columns[from]
  const oldIndex = cards.indexOf(activeId)
  if (overId === null) {
    return from === startStatus ? { ...columns, [from]: arrayMove(cards, oldIndex, cards.length - 1) } : columns
  }
  const overIndex = cards.indexOf(overId)
  return overIndex < 0 ? columns : { ...columns, [from]: arrayMove(cards, oldIndex, overIndex) }
}

/** What the server is asked to do to get from {@code before} to {@code after}, or null when the card did not move. */
export function targetOf(before: BoardColumns, after: BoardColumns, activeId: string): MoveTarget | null {
  const status = columnOf(after, activeId)
  if (status === null) {
    return null
  }
  const neighbours = (columns: BoardColumns, inColumn: Status) => {
    const cards = columns[inColumn]
    const index = cards.indexOf(activeId)
    return { aboveId: cards[index - 1] ?? null, belowId: cards[index + 1] ?? null }
  }
  const now = neighbours(after, status)
  if (columnOf(before, activeId) === status) {
    const then = neighbours(before, status)
    if (then.aboveId === now.aboveId && then.belowId === now.belowId) {
      return null
    }
  }
  return { status, ...now }
}

/** What a finished drag means: the column and the neighbours, or null when nothing changed. */
export function moveTarget(issues: readonly Issue[], activeId: string, overStatus: Status, overId: string | null): MoveTarget | null {
  const active = issues.find((issue) => issue.id === activeId)
  if (active === undefined) {
    return null
  }
  const before = boardColumns(issues)
  return targetOf(before, dropInto(before, activeId, overStatus, overId, active.status), activeId)
}

/** The issue as it will look after a move, before the server confirms. */
export function applyMove(issues: readonly Issue[], issueId: string, target: MoveTarget): Issue[] {
  const above = issues.find((issue) => issue.id === target.aboveId)?.position ?? null
  const below = issues.find((issue) => issue.id === target.belowId)?.position ?? null
  const position = positionBetween(above, below)
  return issues.map((issue) => (issue.id === issueId ? { ...issue, status: target.status, position } : issue))
}
