import { LayoutGrid, List, Plus } from 'lucide-react'
import { useState } from 'react'
import { useMembers } from '@/features/workspace/hooks'
import { useWorkspace } from '@/features/workspace/workspaceContext'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { CreateIssueDialog } from '../components/CreateIssueDialog'
import { IssueBoard } from '../components/IssueBoard'
import { IssueFiltersBar } from '../components/IssueFiltersBar'
import { IssueList } from '../components/IssueList'
import { hasFilters, useIssueFilters, useIssueViewStore } from '../filters'
import { useIssues } from '../hooks'
import type { Status } from '../schemas'

/** The issues of a workspace as a board or a list, with filters that are remembered per workspace. */
export function IssuesPage() {
  const workspace = useWorkspace()
  const filters = useIssueFilters(workspace.id)
  const view = useIssueViewStore((state) => state.view)
  const setView = useIssueViewStore((state) => state.setView)
  const setFilters = useIssueViewStore((state) => state.setFilters)
  const issues = useIssues(workspace.id, filters)
  const members = useMembers(workspace.id)
  const [creating, setCreating] = useState<{ status: Status } | null>(null)

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-3">
        <h1 className="text-2xl leading-7 font-medium">Issues</h1>
        <span className="flex-1" />
        <Button onClick={() => setCreating({ status: 'todo' })}>
          <Plus />
          Create issue
        </Button>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <IssueFiltersBar
          filters={filters}
          onChange={(next) => setFilters(workspace.id, next)}
          members={members.data ?? []}
        />
        <Tabs value={view} onValueChange={(value) => setView(value === 'list' ? 'list' : 'board')}>
          <TabsList variant="line" aria-label="View">
            <TabsTrigger value="board">
              <LayoutGrid />
              Board
            </TabsTrigger>
            <TabsTrigger value="list">
              <List />
              List
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      {issues.isPending ? (
        <div className="flex flex-col gap-2" aria-busy="true">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <span className="sr-only">Loading issues…</span>
        </div>
      ) : issues.isError ? (
        <div role="alert" className="flex items-center gap-3 text-sm text-destructive">
          Could not load the issues.
          <Button variant="secondary" size="sm" onClick={() => void issues.refetch()}>
            Try again
          </Button>
        </div>
      ) : (
        <>
          {issues.data.total > issues.data.items.length && (
            <p role="status" className="text-sm text-muted-foreground">
              Showing the newest {issues.data.items.length} of {issues.data.total} issues. Use filters to narrow them down.
            </p>
          )}
          {view === 'board' ? (
            <IssueBoard
              workspaceId={workspace.id}
              workspaceSlug={workspace.slug}
              issues={issues.data.items}
              onCreate={(status) => setCreating({ status })}
            />
          ) : issues.data.items.length === 0 ? (
            <p className="rounded-md border border-dashed p-6 text-center text-muted-foreground">
              {hasFilters(filters) ? 'No issues match these filters.' : 'No issues yet. Create the first one.'}
            </p>
          ) : (
            <IssueList issues={issues.data.items} workspaceSlug={workspace.slug} />
          )}
        </>
      )}

      <CreateIssueDialog
        workspace={workspace}
        open={creating !== null}
        onOpenChange={(open) => !open && setCreating(null)}
        initialStatus={creating?.status}
      />
    </div>
  )
}
