import { Navigate } from 'react-router'
import { TopBar } from '@/shared/layout/TopBar'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { useWorkspaces } from '../hooks'
import { useLastWorkspace } from '../lastWorkspace'

/** `/`: sends the user to their last workspace, their first one, or to onboarding when they have none. */
export function RootRedirectPage() {
  const workspaces = useWorkspaces()
  const lastSlug = useLastWorkspace((state) => state.lastSlug)

  if (workspaces.isError) {
    return (
      <div role="alert" className="mx-auto flex min-h-svh max-w-md flex-col items-start justify-center gap-3 p-6">
        <h1 className="text-lg font-medium">Could not load your workspaces</h1>
        <Button onClick={() => void workspaces.refetch()}>Try again</Button>
      </div>
    )
  }

  if (workspaces.isPending) {
    return (
      <div className="min-h-svh" aria-busy="true">
        <TopBar />
        <div className="flex flex-col gap-3 p-6">
          <Skeleton className="h-7 w-48" />
          <span className="sr-only">Loading…</span>
        </div>
      </div>
    )
  }

  const target = workspaces.data.find((workspace) => workspace.slug === lastSlug) ?? workspaces.data[0]
  return <Navigate to={target ? `/w/${target.slug}` : '/onboarding'} replace />
}
