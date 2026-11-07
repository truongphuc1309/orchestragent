import type { Issue, MoveTarget, Status } from './schemas'

/** Gap used when an issue goes above the first or below the last one; the server uses the same number. */
const STEP = 1024

export function byPosition(a: Issue, b: Issue): number {
  return a.position - b.position || a.number - b.number
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

/** What a finished drag means: the column and the neighbours, or null when nothing changed. */
export function moveTarget(issues: readonly Issue[], activeId: string, overStatus: Status, overId: string | null): MoveTarget | null {
  const active = issues.find((issue) => issue.id === activeId)
  if (active === undefined) {
    return null
  }
  const target = column(issues, overStatus)
  const oldIndex = target.findIndex((issue) => issue.id === activeId)
  const without = target.filter((issue) => issue.id !== activeId)

  // Dropped on the column itself (an empty column, or the space below the cards): goes to the bottom.
  let insertAt = without.length
  if (overId !== null && overId !== activeId) {
    const overIndex = without.findIndex((issue) => issue.id === overId)
    if (overIndex >= 0) {
      // Moving down inside a column lands after the card it is dropped on; every other move lands before it.
      insertAt = oldIndex >= 0 && oldIndex <= overIndex ? overIndex + 1 : overIndex
    }
  } else if (overId === activeId) {
    insertAt = oldIndex >= 0 ? oldIndex : without.length
  }

  const above = without[insertAt - 1] ?? null
  const below = without[insertAt] ?? null
  if (active.status === overStatus && oldIndex === insertAt) {
    return null
  }
  return { status: overStatus, aboveId: above?.id ?? null, belowId: below?.id ?? null }
}

/** The issue as it will look after a move, before the server confirms. */
export function applyMove(issues: readonly Issue[], issueId: string, target: MoveTarget): Issue[] {
  const above = issues.find((issue) => issue.id === target.aboveId)?.position ?? null
  const below = issues.find((issue) => issue.id === target.belowId)?.position ?? null
  const position = positionBetween(above, below)
  return issues.map((issue) => (issue.id === issueId ? { ...issue, status: target.status, position } : issue))
}
