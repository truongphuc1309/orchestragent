import { describe, expect, it } from 'vitest'
import { issue, issueId } from '@/test/fixtures'
import { applyMove, column, moveTarget, positionBetween } from './board'
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

  it('breaks ties by number', () => {
    expect(column([card(9, 'todo', 5), card(7, 'todo', 5)], 'todo').map((i) => i.number)).toEqual([7, 9])
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
