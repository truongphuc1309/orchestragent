import { ChevronLeft } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { IssuesView } from '@/features/issue/components/IssuesView'
import { useWorkspace } from '@/features/workspace/workspaceContext'
import { ApiError } from '@/shared/api/errors'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { useProjectByKey } from '../hooks'

/** One project: its issues as a board or a list. New issues created here go to this project. */
export function ProjectIssuesPage() {
  const workspace = useWorkspace()
  const { projectKey = '' } = useParams()
  const project = useProjectByKey(workspace.id, projectKey.toUpperCase())
  const back = (
    <Link to={`/w/${workspace.slug}/projects`} className="flex items-center gap-1 text-sm">
      <ChevronLeft className="size-4" />
      Projects
    </Link>
  )

  if (project.isError && project.error instanceof ApiError && project.error.status === 404) {
    return (
      <div className="flex flex-col gap-2">
        {back}
        <h1 className="text-2xl leading-7 font-medium">Project not found</h1>
        <p className="text-muted-foreground">There is no project {projectKey} in {workspace.name}.</p>
      </div>
    )
  }
  if (project.isPending) {
    return (
      <div className="flex flex-col gap-3" aria-busy="true">
        {back}
        <Skeleton className="h-8 w-72" />
        <Skeleton className="h-40 w-full" />
        <span className="sr-only">Loading the project…</span>
      </div>
    )
  }
  if (project.isError) {
    return (
      <div className="flex flex-col gap-3">
        {back}
        <div role="alert" className="flex items-center gap-3 text-sm text-destructive">
          Could not load the project.
          <Button variant="secondary" size="sm" onClick={() => void project.refetch()}>
            Try again
          </Button>
        </div>
      </div>
    )
  }

  const data = project.data
  return (
    <div className="flex flex-col gap-2">
      {back}
      <IssuesView
        // A new project means new filters and a new board: start the view afresh.
        key={data.id}
        project={data}
        heading={
          <>
            <h1 className="flex items-center gap-2 text-2xl leading-7 font-medium">
              {data.name}
              <span className="text-base font-normal text-muted-foreground">{data.key}</span>
              {data.archived && <Badge variant="default">Archived</Badge>}
            </h1>
            {data.description && <p className="text-muted-foreground">{data.description}</p>}
          </>
        }
        notice={
          data.archived ? (
            <p role="status" className="rounded-md border bg-muted p-3 text-sm text-muted-foreground">
              This project is archived: its issues stay as they are, but no new issues can be created in it. Restore it
              on the <Link to={`/w/${workspace.slug}/projects`}>Projects</Link> page to add issues again.
            </p>
          ) : undefined
        }
      />
    </div>
  )
}
