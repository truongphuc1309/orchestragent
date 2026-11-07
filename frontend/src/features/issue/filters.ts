import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { z } from 'zod'
import { prioritySchema, statusSchema, type Priority, type Status } from './schemas'

/** Which issues to show. An empty list means "any" for that field. */
export interface IssueFilters {
  statuses: Status[]
  priorities: Priority[]
  /** User IDs of members. */
  assignees: string[]
  /** Also show issues nobody is assigned to (combined with {@link assignees} by "or"). */
  unassigned: boolean
}

export const NO_FILTERS: IssueFilters = { statuses: [], priorities: [], assignees: [], unassigned: false }

export function hasFilters(filters: IssueFilters): boolean {
  return (
    filters.statuses.length > 0 || filters.priorities.length > 0 || filters.assignees.length > 0 || filters.unassigned
  )
}

export type IssueView = 'board' | 'list'

const filtersSchema = z.object({
  statuses: z.array(statusSchema),
  priorities: z.array(prioritySchema),
  assignees: z.array(z.string()),
  unassigned: z.boolean(),
})

const persistedSchema = z.object({
  view: z.enum(['board', 'list']),
  filtersByWorkspace: z.record(z.string(), filtersSchema),
})

interface IssueViewState {
  view: IssueView
  /** Filters are remembered per workspace, so switching workspaces does not carry them over. */
  filtersByWorkspace: Record<string, IssueFilters>
  setView: (view: IssueView) => void
  setFilters: (workspaceId: string, filters: IssueFilters) => void
  clearFilters: (workspaceId: string) => void
}

export const useIssueViewStore = create<IssueViewState>()(
  persist(
    (set) => ({
      view: 'board',
      filtersByWorkspace: {},
      setView: (view) => set({ view }),
      setFilters: (workspaceId, filters) =>
        set((state) => ({ filtersByWorkspace: { ...state.filtersByWorkspace, [workspaceId]: filters } })),
      clearFilters: (workspaceId) =>
        set((state) => {
          const { [workspaceId]: _removed, ...rest } = state.filtersByWorkspace
          return { filtersByWorkspace: rest }
        }),
    }),
    {
      name: 'orchestragent.issueView',
      partialize: (state) => ({ view: state.view, filtersByWorkspace: state.filtersByWorkspace }),
      // Whatever is in local storage may be old or hand-edited: only accept it when it has the expected shape.
      merge: (persisted, current) => {
        const parsed = persistedSchema.safeParse(persisted)
        return parsed.success ? { ...current, ...parsed.data } : current
      },
    },
  ),
)

export function useIssueFilters(workspaceId: string): IssueFilters {
  return useIssueViewStore((state) => state.filtersByWorkspace[workspaceId] ?? NO_FILTERS)
}
