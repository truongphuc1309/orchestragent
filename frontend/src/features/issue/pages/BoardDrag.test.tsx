import type { DndContextProps, DragEndEvent, DragOverEvent, DragStartEvent } from '@dnd-kit/core'
import { act, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACME, ADA, issue, issueId } from '@/test/fixtures'
import { fakeServer, json, problem } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import { useIssueViewStore } from '../filters'
import type { Issue, Status } from '../schemas'

/*
 * jsdom has no layout, so a real drag cannot be simulated. Instead the board's DndContext is wrapped to
 * capture its handlers, and the tests call onDragEnd with the events dnd-kit would produce. This covers
 * everything the app does with a drop: working out the neighbours, the instant move, the request, and the
 * rollback when the server refuses.
 */
let dnd: DndContextProps | undefined
vi.mock('@dnd-kit/core', async (importOriginal) => {
  const original = await importOriginal<typeof import('@dnd-kit/core')>()
  return {
    ...original,
    DndContext: (props: DndContextProps) => {
      dnd = props
      return <original.DndContext {...props} />
    },
  }
})

const A = issue({ id: issueId(1), number: 1, title: 'Alpha', status: 'todo', position: 1024 })
const B = issue({ id: issueId(2), number: 2, title: 'Bravo', status: 'todo', position: 2048 })
const C = issue({ id: issueId(3), number: 3, title: 'Charlie', status: 'todo', position: 3072 })
const D = issue({ id: issueId(4), number: 4, title: 'Delta', status: 'in_progress', position: 1024 })
const ISSUES: Issue[] = [A, B, C, D]

const ISSUES_PATH = `/api/workspaces/${ACME.id}/issues`
const LIST = `GET ${ISSUES_PATH}?limit=500`
const MOVE = (id: string) => `POST ${ISSUES_PATH}/${id}/move`

/** What dnd-kit reports when `activeId` is dropped on a card (`overId`) or on a column. */
function dragEnd(activeId: string, over: { id: string; status: Status } | null): DragEndEvent {
  const rect = { width: 0, height: 0, top: 0, left: 0, bottom: 0, right: 0 }
  return {
    active: { id: activeId, data: { current: undefined }, rect: { current: { initial: null, translated: null } } },
    over: over === null ? null : { id: over.id, rect, disabled: false, data: { current: { status: over.status } } },
    activatorEvent: new Event('pointerup'),
    collisions: null,
    delta: { x: 0, y: 0 },
  }
}

function dragStart(activeId: string): DragStartEvent {
  return {
    active: { id: activeId, data: { current: undefined }, rect: { current: { initial: null, translated: null } } },
    activatorEvent: new Event('pointerdown'),
  }
}

/** The same shape as a drop: dnd-kit reports where the dragged card is while it moves. */
function dragOver(activeId: string, over: { id: string; status: Status }): DragOverEvent {
  return dragEnd(activeId, over)
}

function handlers(): DndContextProps {
  if (dnd === undefined) {
    throw new Error('The board has not rendered a DndContext')
  }
  return dnd
}

function dropped(event: DragEndEvent) {
  if (dnd?.onDragEnd === undefined) {
    throw new Error('The board has not rendered a DndContext')
  }
  return dnd.onDragEnd(event)
}

function titlesIn(columnName: string): string[] {
  return within(screen.getByRole('region', { name: columnName }))
    .queryAllByRole('link')
    .map((link) => link.textContent ?? '')
}

describe('dropping a card on the board', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    useIssueViewStore.setState({ view: 'board', filtersByWorkspace: {} })
    dnd = undefined
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  function baseHandlers(extra: Record<string, () => Response | Promise<Response>> = {}) {
    return {
      'GET /api/me': () => json(200, ADA),
      'GET /api/workspaces': () => json(200, [ACME]),
      [`GET /api/workspaces/${ACME.id}/members`]: () => json(200, []),
      [`GET /api/workspaces/${ACME.id}/agents`]: () => json(200, []),
      [LIST]: () => json(200, { items: ISSUES, total: ISSUES.length }),
      ...extra,
    }
  }

  it('sends the neighbours of the drop spot when a card moves down inside its column', async () => {
    const server = fakeServer(baseHandlers({ [MOVE(A.id)]: () => json(200, { ...A, position: 2560 }) }))
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('Alpha')

    dropped(dragEnd(A.id, { id: B.id, status: 'todo' }))

    await waitFor(() => expect(server.calls(MOVE(A.id))).toHaveLength(1))
    expect(server.calls(MOVE(A.id))[0]?.body).toEqual({ status: 'todo', aboveId: B.id, belowId: C.id })
  })

  it('sends the new column when a card is dropped on another column', async () => {
    const server = fakeServer(baseHandlers({ [MOVE(A.id)]: () => json(200, { ...A, status: 'done' }) }))
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('Alpha')

    dropped(dragEnd(A.id, { id: 'column:done', status: 'done' }))

    await waitFor(() => expect(server.calls(MOVE(A.id))).toHaveLength(1))
    expect(server.calls(MOVE(A.id))[0]?.body).toEqual({ status: 'done' })
  })

  it('moves the card at once, before the server has answered', async () => {
    let answer: (response: Response) => void = () => {}
    fakeServer(
      baseHandlers({
        [MOVE(A.id)]: () => new Promise<Response>((resolve) => (answer = resolve)),
      }),
    )
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('Alpha')
    expect(titlesIn('To do')).toEqual(['Alpha', 'Bravo', 'Charlie'])

    dropped(dragEnd(A.id, { id: 'column:in_progress', status: 'in_progress' }))

    await waitFor(() => expect(titlesIn('In progress')).toEqual(['Delta', 'Alpha']))
    expect(titlesIn('To do')).toEqual(['Bravo', 'Charlie'])
    answer(json(200, { ...A, status: 'in_progress', position: 2048 }))
  })

  it('moves the card into the column it is dragged over, before it is dropped', async () => {
    const server = fakeServer(baseHandlers())
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('Alpha')

    act(() => handlers().onDragStart?.(dragStart(A.id)))
    act(() => handlers().onDragOver?.(dragOver(A.id, { id: D.id, status: 'in_progress' })))

    // The new column opens a gap where the card will land and the old one closes up, so the drop animation flies
    // to the right place instead of back to where the card came from.
    expect(titlesIn('In progress')).toEqual(['Alpha', 'Delta'])
    expect(titlesIn('To do')).toEqual(['Bravo', 'Charlie'])
    expect(server.requests.filter((request) => request.method === 'POST')).toEqual([])
  })

  it('never shows the old order between the drop and the update of the issues', async () => {
    let answer: (response: Response) => void = () => {}
    fakeServer(baseHandlers({ [MOVE(C.id)]: () => new Promise<Response>((resolve) => (answer = resolve)) }))
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('Alpha')

    act(() => handlers().onDragStart?.(dragStart(C.id)))
    // Synchronously, in the same render as the end of the drag: nothing waits for the cache.
    act(() => handlers().onDragEnd?.(dragEnd(C.id, { id: A.id, status: 'todo' })))

    expect(titlesIn('To do')).toEqual(['Charlie', 'Alpha', 'Bravo'])
    answer(json(200, { ...C, position: 512 }))
    await waitFor(() => expect(titlesIn('To do')).toEqual(['Charlie', 'Alpha', 'Bravo']))
  })

  it('keeps the place chosen during the drag when the card is dropped on the background of its new column', async () => {
    const server = fakeServer(baseHandlers({ [MOVE(A.id)]: () => json(200, { ...A, status: 'in_progress' }) }))
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('Alpha')

    act(() => handlers().onDragStart?.(dragStart(A.id)))
    act(() => handlers().onDragOver?.(dragOver(A.id, { id: D.id, status: 'in_progress' })))
    act(() => handlers().onDragEnd?.(dragEnd(A.id, { id: 'column:in_progress', status: 'in_progress' })))

    await waitFor(() => expect(server.calls(MOVE(A.id))).toHaveLength(1))
    expect(server.calls(MOVE(A.id))[0]?.body).toEqual({ status: 'in_progress', belowId: D.id })
  })

  it('puts everything back when the drag is cancelled', async () => {
    const server = fakeServer(baseHandlers())
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('Alpha')

    act(() => handlers().onDragStart?.(dragStart(A.id)))
    act(() => handlers().onDragOver?.(dragOver(A.id, { id: 'column:done', status: 'done' })))
    expect(titlesIn('Done')).toEqual(['Alpha'])
    act(() => handlers().onDragCancel?.({ ...dragEnd(A.id, null), over: null }))

    expect(titlesIn('To do')).toEqual(['Alpha', 'Bravo', 'Charlie'])
    expect(titlesIn('Done')).toEqual([])
    expect(server.requests.filter((request) => request.method === 'POST')).toEqual([])
  })

  it('puts the card back and explains when the server refuses the move', async () => {
    fakeServer(
      baseHandlers({
        [MOVE(A.id)]: () =>
          problem(409, { detail: 'The board changed in the meantime. Reload and try again.', code: 'issue.stale_move' }),
      }),
    )
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('Alpha')

    dropped(dragEnd(A.id, { id: 'column:done', status: 'done' }))

    expect(await screen.findByText('The board changed in the meantime. Reload and try again.')).toBeInTheDocument()
    await waitFor(() => expect(titlesIn('To do')).toEqual(['Alpha', 'Bravo', 'Charlie']))
    expect(titlesIn('Done')).toEqual([])
  })

  it('fetches the issues again after a move, so the board shows what the server decided', async () => {
    let items = ISSUES
    const server = fakeServer(
      baseHandlers({
        [LIST]: () => json(200, { items, total: items.length }),
        [MOVE(A.id)]: () => {
          items = [{ ...A, status: 'done', position: 7 }, B, C, D]
          return json(200, items[0])
        },
      }),
    )
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('Alpha')

    dropped(dragEnd(A.id, { id: 'column:done', status: 'done' }))

    await waitFor(() => expect(server.calls(LIST).length).toBeGreaterThan(1))
    await waitFor(() => expect(titlesIn('Done')).toEqual(['Alpha']))
  })

  it.each([
    ['dropped outside every column', null],
    ['dropped on itself', { id: A.id, status: 'todo' as const }],
    ['dropped below the last card of its own column', { id: 'column:todo', status: 'todo' as const }],
  ])('sends nothing when the card is %s', async (_name, over) => {
    const server = fakeServer(baseHandlers())
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('Alpha')
    // The last card of the column: dropping it below itself changes nothing.
    const moving = over?.id === 'column:todo' ? C.id : A.id

    dropped(dragEnd(moving, over))

    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(server.requests.filter((request) => request.method === 'POST')).toEqual([])
  })

  it('ignores a drop whose target carries no status', async () => {
    const server = fakeServer(baseHandlers())
    renderRoutes(routes, ['/w/acme/issues'])
    await screen.findByText('Alpha')
    const event = dragEnd(A.id, { id: B.id, status: 'todo' })
    if (event.over) {
      event.over.data.current = { unexpected: true }
    }

    dropped(event)

    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(server.requests.filter((request) => request.method === 'POST')).toEqual([])
  })
})
