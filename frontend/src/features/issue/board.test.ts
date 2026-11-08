import { describe, expect, it } from 'vitest'
import { issue, issueId } from '@/test/fixtures'
import { applyMove, boardColumns, column, dropInto, moveAcross, moveTarget, positionBetween, targetOf } from './board'
import type { Issue, Status } from './schemas'

function card(n: number, status: Status, position: number): Issue {
  return issue({ id: issueId(n), number: n, title: `Issue ${n}`, status, position })
}

const A = card(1, 'todo', 1024)
const B = card(2, 'todo', 2048)
const C = card(3, 'todo', 3072)
const X = card(4, 'in_progress', 1024)
const Y = card(5, 'in_progress', 2048)
const ALL = [A, B, C, X, Y]

describe('column', () => {
  it('lists one status from top to bottom, whatever order the server sent', () => {
    expect(column([C, X, A, Y, B], 'todo').map((i) => i.number)).toEqual([1, 2, 3])
    expect(column([C, X, A, Y, B], 'in_progress').map((i) => i.number)).toEqual([4, 5])
    expect(column(ALL, 'done')).toEqual([])
  })

  // Numbers count per project since step 13, so they cannot order issues of two projects: the older issue wins.
  it('breaks ties by creation time', () => {
    const older = { ...card(9, 'todo', 5), createdAt: '2026-01-01T10:00:00Z' }
    const newer = { ...card(7, 'todo', 5), createdAt: '2026-01-02T10:00:00Z' }
    expect(column([newer, older], 'todo').map((i) => i.number)).toEqual([9, 7])
  })
})

describe('positionBetween', () => {
  it('matches the server arithmetic', () => {
    expect(positionBetween(null, null)).toBe(1024)
    expect(positionBetween(1024, 2048)).toBe(1536)
    expect(positionBetween(null, 1024)).toBe(0)
    expect(positionBetween(2048, null)).toBe(3072)
  })
})

describe('moveTarget', () => {
  it('moves a card down inside its column: it lands after the card it is dropped on', () => {
    expect(moveTarget(ALL, A.id, 'todo', B.id)).toEqual({ status: 'todo', aboveId: B.id, belowId: C.id })
    expect(moveTarget(ALL, A.id, 'todo', C.id)).toEqual({ status: 'todo', aboveId: C.id, belowId: null })
  })

  it('moves a card up inside its column: it lands before the card it is dropped on', () => {
    expect(moveTarget(ALL, C.id, 'todo', A.id)).toEqual({ status: 'todo', aboveId: null, belowId: A.id })
    expect(moveTarget(ALL, C.id, 'todo', B.id)).toEqual({ status: 'todo', aboveId: A.id, belowId: B.id })
  })

  it('moves a card into another column before the card it is dropped on', () => {
    expect(moveTarget(ALL, A.id, 'in_progress', Y.id)).toEqual({ status: 'in_progress', aboveId: X.id, belowId: Y.id })
    expect(moveTarget(ALL, A.id, 'in_progress', X.id)).toEqual({ status: 'in_progress', aboveId: null, belowId: X.id })
  })

  it('puts a card dropped on the column itself at the bottom', () => {
    expect(moveTarget(ALL, A.id, 'in_progress', null)).toEqual({ status: 'in_progress', aboveId: Y.id, belowId: null })
    expect(moveTarget(ALL, A.id, 'todo', null)).toEqual({ status: 'todo', aboveId: C.id, belowId: null })
  })

  it('puts a card dropped on an empty column alone in it', () => {
    expect(moveTarget(ALL, A.id, 'done', null)).toEqual({ status: 'done', aboveId: null, belowId: null })
  })

  it('reports nothing to do when the card stays where it is', () => {
    expect(moveTarget(ALL, B.id, 'todo', B.id)).toBeNull()
    // Dropped below the last card when it already is last, or on the card directly below it.
    expect(moveTarget(ALL, C.id, 'todo', null)).toBeNull()
    // Dropping B on A is a real move (B goes above A); it is not the same as staying put.
    expect(moveTarget(ALL, B.id, 'todo', A.id)).toEqual({ status: 'todo', aboveId: null, belowId: A.id })
  })

  it('ignores a card that is not on the board', () => {
    expect(moveTarget(ALL, issueId(99), 'todo', A.id)).toBeNull()
  })

  it('never names the dragged card as its own neighbour', () => {
    for (const target of [A, B, C, X, Y]) {
      for (const overStatus of ['todo', 'in_progress', 'done'] as const) {
        for (const over of [null, ...ALL.map((i) => i.id)]) {
          const result = moveTarget(ALL, target.id, overStatus, over)
          expect(result?.aboveId).not.toBe(target.id)
          expect(result?.belowId).not.toBe(target.id)
        }
      }
    }
  })
})

describe('the order shown while a card is dragged', () => {
  const START = boardColumns(ALL)

  it('lists every column from top to bottom', () => {
    expect(START.todo).toEqual([A.id, B.id, C.id])
    expect(START.in_progress).toEqual([X.id, Y.id])
    expect(START.done).toEqual([])
  })

  it('moves a card dragged over another column into it at once, before or after the card it is over', () => {
    const before = moveAcross(START, A.id, 'in_progress', Y.id)
    expect(before.in_progress).toEqual([X.id, A.id, Y.id])
    expect(before.todo).toEqual([B.id, C.id])

    expect(moveAcross(START, A.id, 'in_progress', Y.id, true).in_progress).toEqual([X.id, Y.id, A.id])
    expect(moveAcross(START, A.id, 'done', null).done).toEqual([A.id])
  })

  it('leaves a drag inside one column to the sortable animation, and says so by returning the same object', () => {
    expect(moveAcross(START, A.id, 'todo', C.id)).toBe(START)
    expect(moveAcross(START, issueId(99), 'done', null)).toBe(START)
  })

  it('keeps the place the drag chose when the card is dropped on the background of its new column', () => {
    const dragged = moveAcross(START, A.id, 'in_progress', Y.id)

    const dropped = dropInto(dragged, A.id, 'in_progress', null, 'todo')

    expect(dropped.in_progress).toEqual([X.id, A.id, Y.id])
    expect(targetOf(START, dropped, A.id)).toEqual({ status: 'in_progress', aboveId: X.id, belowId: Y.id })
  })

  it('reorders inside the new column when the card is dropped on one of its cards', () => {
    const dragged = moveAcross(START, A.id, 'in_progress', X.id)

    expect(dropInto(dragged, A.id, 'in_progress', Y.id, 'todo').in_progress).toEqual([X.id, Y.id, A.id])
    expect(dropInto(dragged, A.id, 'in_progress', A.id, 'todo')).toBe(dragged)
  })

  it('reports nothing to do when a card dragged away comes back to its old place', () => {
    const away = moveAcross(START, B.id, 'in_progress', null)
    const back = moveAcross(away, B.id, 'todo', C.id)

    expect(back.todo).toEqual([A.id, B.id, C.id])
    expect(targetOf(START, back, B.id)).toBeNull()
  })
})

describe('applyMove', () => {
  it('shows the card in its new column and place straight away', () => {
    const moved = applyMove(ALL, C.id, { status: 'todo', aboveId: A.id, belowId: B.id })

    expect(column(moved, 'todo').map((i) => i.number)).toEqual([1, 3, 2])
  })

  it('changes the column of a card moved elsewhere and leaves the others alone', () => {
    const moved = applyMove(ALL, A.id, { status: 'in_progress', aboveId: X.id, belowId: Y.id })

    expect(column(moved, 'in_progress').map((i) => i.number)).toEqual([4, 1, 5])
    expect(column(moved, 'todo').map((i) => i.number)).toEqual([2, 3])
    expect(moved.find((i) => i.id === B.id)).toBe(B)
  })

  it('does not change the list it was given', () => {
    const before = JSON.stringify(ALL)
    applyMove(ALL, A.id, { status: 'done', aboveId: null, belowId: null })
    expect(JSON.stringify(ALL)).toBe(before)
  })
})
