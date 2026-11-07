import { useEffect } from 'react'
import { Link, Outlet, useParams } from 'react-router'
import { UserMenu } from '@/features/auth/components/UserMenu'
import { TopBar } from '@/shared/layout/TopBar'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { WorkspaceSidebar } from '../components/WorkspaceSidebar'
import { useWorkspaces } from '../hooks'
import { useLastWorkspace } from '../lastWorkspace'
import { WorkspaceContext } from '../workspaceContext'

/** Route layout of `/w/:slug`: finds the workspace, then shows the top bar, sidebar and the nested page. */
export function WorkspaceLayout() {
  const { slug } = useParams()
  const workspaces = useWorkspaces()
  const remember = useLastWorkspace((state) => state.remember)
  const forget = useLastWorkspace((state) => state.forget)
  const lastSlug = useLastWorkspace((state) => state.lastSlug)
  const workspace = workspaces.data?.find((candidate) => candidate.slug === slug)

  useEffect(() => {
    if (workspace) {
      remember(workspace.slug)
    } else if (workspaces.data && slug !== undefined && lastSlug === slug) {
      // The remembered workspace no longer exists for this user (removed, or someone else's account).
      forget()
    }
  }, [workspace, workspaces.data, slug, lastSlug, remember, forget])

  if (workspaces.isPending) {
    return (
      <div className="min-h-svh" aria-busy="true">
        <TopBar />
        <div className="flex flex-col gap-3 p-6">
          <Skeleton className="h-7 w-48" />
          <Skeleton className="h-4 w-72" />
          <span className="sr-only">Loading…</span>
        </div>
      </div>
    )
  }

  if (workspaces.isError) {
    return (
      <div className="min-h-svh">
        <TopBar />
        <div role="alert" className="mx-auto flex max-w-md flex-col items-start gap-3 p-6">
          <h1 className="text-lg font-medium">Could not load your workspaces</h1>
          <Button onClick={() => void workspaces.refetch()}>Try again</Button>
        </div>
      </div>
    )
  }

  if (!workspace) {
    return (
      <div className="min-h-svh">
        <TopBar actions={<UserMenu />} />
        <main className="mx-auto flex max-w-md flex-col gap-2 p-6">
          <h1 className="text-2xl leading-7 font-medium">Workspace not found</h1>
          <p className="text-muted-foreground">It does not exist, or you are not a member of it.</p>
          <Link to="/" className="text-sm">
            Go to your workspaces
          </Link>
        </main>
      </div>
    )
  }

  return (
    <WorkspaceContext.Provider value={workspace}>
      <div className="flex min-h-svh flex-col">
        <TopBar actions={<UserMenu />} />
        <div className="flex flex-1">
          <WorkspaceSidebar current={workspace} workspaces={workspaces.data ?? []} />
          <main className="min-w-0 flex-1 px-8 py-6">
            <Outlet />
          </main>
        </div>
      </div>
    </WorkspaceContext.Provider>
  )
}
