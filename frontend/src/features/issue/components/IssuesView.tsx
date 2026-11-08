import { LayoutGrid, List, Plus } from 'lucide-react'
import { useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { useAgents } from '@/features/agent/hooks'
import { useProjects } from '@/features/project/hooks'
import type { Project } from '@/features/project/schemas'
import { useMembers } from '@/features/workspace/hooks'
import { useWorkspace } from '@/features/workspace/workspaceContext'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { hasFilters, useIssueFilters, useIssueViewStore, type IssueFilters } from '../filters'
import { useIssues } from '../hooks'
import type { Status } from '../schemas'
import { CreateIssueDialog } from './CreateIssueDialog'
import { IssueBoard } from './IssueBoard'
import { IssueFiltersBar } from './IssueFiltersBar'
import { IssueList } from './IssueList'

interface Props {
  /** Show only the issues of this project (a project page); without it, the issues of every project. */
  project?: Project
  heading: ReactNode
  /** Under the heading, for example a note that the project is archived. */
  notice?: ReactNode
}

/**
 * Issues as a board or a list, with the filters that are remembered per workspace. On a project page the
 * project filter is fixed to that project and new issues go there.
 */
export function IssuesView({ project, heading, notice }: Props) {
  const workspace = useWorkspace()
  const saved = useIssueFilters(workspace.id)
  const view = useIssueViewStore((state) => state.view)
  const setView = useIssueViewStore((state) => state.setView)
  const setFilters = useIssueViewStore((state) => state.setFilters)
  const filters: IssueFilters = project ? { ...saved, projects: [project.id] } : saved
  const issues = useIssues(workspace.id, filters)
  const members = useMembers(workspace.id)
  const agents = useAgents(workspace.id)
  // Archived projects too: their issues still show, so they need a name and a filter entry.
  const projects = useProjects(workspace.id, true)
  const projectNames = useMemo(
    () => new Map((projects.data ?? []).map((candidate) => [candidate.id, candidate.name])),
    [projects.data],
  )
  const [creating, setCreating] = useState<{ status: Status } | null>(null)

  const canCreate = project === undefined || !project.archived
  const noProjects = project === undefined && projects.isSuccess && projects.data.length === 0
  // A project page keeps the other saved filters but not the project choice, which belongs to the Issues page.
  const changeFilters = (next: IssueFilters) =>
    setFilters(workspace.id, project ? { ...next, projects: saved.projects } : next)
  const shownFilters = project ? { ...filters, projects: [] } : filters

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-3">
        <div className="flex min-w-0 flex-1 flex-col gap-1">{heading}</div>
        {canCreate && (
          <Button onClick={() => setCreating({ status: 'todo' })}>
            <Plus />
            Create issue
          </Button>
        )}
      </div>
      {notice}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <IssueFiltersBar
          filters={shownFilters}
          onChange={changeFilters}
          members={members.data ?? []}
          agents={agents.data ?? []}
          projects={project ? undefined : (projects.data ?? [])}
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

      {noProjects ? (
        <div className="flex flex-col items-center gap-3 rounded-md border border-dashed p-6 text-center">
          <p className="text-muted-foreground">
            Issues live in projects, and {workspace.name} has none yet. Create the first project, then its issues.
          </p>
          <Button asChild>
            <Link to={`/w/${workspace.slug}/projects`}>Create a project first</Link>
          </Button>
        </div>
      ) : issues.isPending ? (
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
              onCreate={canCreate ? (status) => setCreating({ status }) : undefined}
            />
          ) : issues.data.items.length === 0 ? (
            <p className="rounded-md border border-dashed p-6 text-center text-muted-foreground">
              {hasFilters(shownFilters)
                ? 'No issues match these filters.'
                : canCreate
                  ? 'No issues yet. Create the first one.'
                  : 'This project has no issues.'}
            </p>
          ) : (
            <IssueList
              issues={issues.data.items}
              workspaceSlug={workspace.slug}
              projectNames={project ? undefined : projectNames}
            />
          )}
        </>
      )}

      <CreateIssueDialog
        workspace={workspace}
        open={creating !== null}
        onOpenChange={(open) => !open && setCreating(null)}
        initialStatus={creating?.status}
        initialProjectId={project?.id}
      />
    </div>
  )
}
