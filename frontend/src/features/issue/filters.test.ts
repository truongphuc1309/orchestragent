import { beforeEach, describe, expect, it } from 'vitest'
import { hasFilters, NO_FILTERS, useIssueViewStore } from './filters'

const KEY = 'orchestragent.issueView'

describe('issue view store', () => {
  beforeEach(() => {
    useIssueViewStore.setState({ view: 'board', filtersByWorkspace: {}, lastProjectByWorkspace: {} })
  })

  it('knows when a filter is set', () => {
    expect(hasFilters(NO_FILTERS)).toBe(false)
    expect(hasFilters({ ...NO_FILTERS, projects: ['p'] })).toBe(true)
    expect(hasFilters({ ...NO_FILTERS, statuses: ['todo'] })).toBe(true)
    expect(hasFilters({ ...NO_FILTERS, priorities: ['high'] })).toBe(true)
    expect(hasFilters({ ...NO_FILTERS, assignees: ['x'] })).toBe(true)
    expect(hasFilters({ ...NO_FILTERS, unassigned: true })).toBe(true)
  })

  it('keeps filters separately per workspace', () => {
    const { setFilters } = useIssueViewStore.getState()
    setFilters('ws-1', { ...NO_FILTERS, statuses: ['done'] })
    setFilters('ws-2', { ...NO_FILTERS, priorities: ['urgent'] })

    const { filtersByWorkspace } = useIssueViewStore.getState()
    expect(filtersByWorkspace['ws-1']?.statuses).toEqual(['done'])
    expect(filtersByWorkspace['ws-2']?.statuses).toEqual([])
    expect(filtersByWorkspace['ws-2']?.priorities).toEqual(['urgent'])
  })

  it('clears the filters of one workspace only', () => {
    const { setFilters, clearFilters } = useIssueViewStore.getState()
    setFilters('ws-1', { ...NO_FILTERS, statuses: ['done'] })
    setFilters('ws-2', { ...NO_FILTERS, statuses: ['todo'] })

    clearFilters('ws-1')

    expect(Object.keys(useIssueViewStore.getState().filtersByWorkspace)).toEqual(['ws-2'])
  })

  it('writes the view and the filters to local storage', () => {
    const { setView, setFilters } = useIssueViewStore.getState()
    setView('list')
    setFilters('ws-1', { ...NO_FILTERS, unassigned: true })

    const stored: unknown = JSON.parse(localStorage.getItem(KEY) ?? 'null')
    expect(stored).toMatchObject({
      state: { view: 'list', filtersByWorkspace: { 'ws-1': { unassigned: true } } },
    })
  })

  it('restores filters stored before projects existed, as filters over every project', async () => {
    localStorage.setItem(
      KEY,
      JSON.stringify({
        state: {
          view: 'list',
          filtersByWorkspace: { 'ws-1': { statuses: ['blocked'], priorities: [], assignees: [], unassigned: false } },
        },
        version: 0,
      }),
    )

    await useIssueViewStore.persist.rehydrate()

    const state = useIssueViewStore.getState()
    expect(state.view).toBe('list')
    expect(state.filtersByWorkspace['ws-1']).toEqual({ ...NO_FILTERS, statuses: ['blocked'] })
    expect(state.lastProjectByWorkspace).toEqual({})
  })

  it('remembers the project of the last new issue per workspace', () => {
    const { setLastProject } = useIssueViewStore.getState()
    setLastProject('ws-1', 'p-1')
    setLastProject('ws-2', 'p-2')
    setLastProject('ws-1', 'p-3')

    expect(useIssueViewStore.getState().lastProjectByWorkspace).toEqual({ 'ws-1': 'p-3', 'ws-2': 'p-2' })
  })

  it('restores what was stored', async () => {
    localStorage.setItem(
      KEY,
      JSON.stringify({
        state: { view: 'list', filtersByWorkspace: { 'ws-1': { ...NO_FILTERS, statuses: ['blocked'] } } },
        version: 0,
      }),
    )

    await useIssueViewStore.persist.rehydrate()

    expect(useIssueViewStore.getState().view).toBe('list')
    expect(useIssueViewStore.getState().filtersByWorkspace['ws-1']?.statuses).toEqual(['blocked'])
  })

  it.each([
    ['an unknown status', { view: 'board', filtersByWorkspace: { w: { ...NO_FILTERS, statuses: ['finished'] } } }],
    ['an unknown view', { view: 'calendar', filtersByWorkspace: {} }],
    ['the wrong shape', 'nonsense'],
  ])('ignores stored data with %s instead of breaking', async (_name, state) => {
    localStorage.setItem(KEY, JSON.stringify({ state, version: 0 }))

    await useIssueViewStore.persist.rehydrate()

    expect(useIssueViewStore.getState().view).toBe('board')
    expect(useIssueViewStore.getState().filtersByWorkspace).toEqual({})
  })
})
