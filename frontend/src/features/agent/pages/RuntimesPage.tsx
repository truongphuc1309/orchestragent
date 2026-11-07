import { useMe } from '@/features/auth/hooks'
import { useWorkspace } from '@/features/workspace/workspaceContext'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { ConnectMachine } from '../components/ConnectMachine'
import { RuntimesTable } from '../components/RuntimesTable'
import { useRuntimes } from '../hooks'

/** The computers that can run agents, and how to connect another one. */
export function RuntimesPage() {
  const workspace = useWorkspace()
  const me = useMe()
  const runtimes = useRuntimes(workspace.id)

  return (
    <div className="flex max-w-4xl flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl leading-7 font-medium">Runtimes</h1>
        <p className="text-muted-foreground">Computers that run the work of agents in {workspace.name}.</p>
      </div>

      {runtimes.isPending ? (
        <div className="flex flex-col gap-2" aria-busy="true">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <span className="sr-only">Loading runtimes…</span>
        </div>
      ) : runtimes.isError ? (
        <div role="alert" className="flex items-center gap-3 text-sm text-destructive">
          Could not load the runtimes.
          <Button variant="secondary" size="sm" onClick={() => void runtimes.refetch()}>
            Try again
          </Button>
        </div>
      ) : runtimes.data.length === 0 ? (
        <p className="rounded-md border border-dashed p-6 text-center text-muted-foreground">
          No computer is connected yet. Follow the steps below to connect one.
        </p>
      ) : (
        <RuntimesTable runtimes={runtimes.data} myId={me.data?.id} />
      )}

      <ConnectMachine workspaceSlug={workspace.slug} />
    </div>
  )
}
