import { Plus } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { useMe } from '@/features/auth/hooks'
import { useWorkspace } from '@/features/workspace/workspaceContext'
import { messageOf } from '@/shared/api/errors'
import { ConfirmDialog } from '@/shared/layout/ConfirmDialog'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { AgentCard } from '../components/AgentCard'
import { AgentFormDialog } from '../components/AgentFormDialog'
import { useAgents, useArchiveAgent, useRestoreAgent } from '../hooks'
import { canManageAgent } from '../permissions'
import type { Agent } from '../schemas'

/** The agents of a workspace: create, edit, archive and restore them. */
export function AgentsPage() {
  const workspace = useWorkspace()
  const me = useMe()
  const [showArchived, setShowArchived] = useState(false)
  const agents = useAgents(workspace.id, showArchived)
  const archive = useArchiveAgent(workspace.id)
  const restore = useRestoreAgent(workspace.id)
  // `null` while closed; a new agent is `{ agent: null }`.
  const [editing, setEditing] = useState<{ agent: Agent | null } | null>(null)
  const [archiving, setArchiving] = useState<Agent | null>(null)

  const confirmArchive = () => {
    if (archiving === null) {
      return
    }
    const target = archiving
    archive.mutate(target.id, {
      onSuccess: () => {
        setArchiving(null)
        toast.success(`${target.name} was archived`)
      },
      onError: (error) => {
        setArchiving(null)
        toast.error(messageOf(error, 'Could not archive the agent.'))
      },
    })
  }

  return (
    <div className="flex max-w-4xl flex-col gap-4">
      <div className="flex items-center gap-3">
        <div className="flex flex-1 flex-col gap-1">
          <h1 className="text-2xl leading-7 font-medium">Agents</h1>
          <p className="text-muted-foreground">AI teammates you can assign issues to in {workspace.name}.</p>
        </div>
        <Button variant="secondary" aria-pressed={showArchived} onClick={() => setShowArchived((shown) => !shown)}>
          {showArchived ? 'Hide archived' : 'Show archived'}
        </Button>
        <Button onClick={() => setEditing({ agent: null })}>
          <Plus />
          Create agent
        </Button>
      </div>

      {agents.isPending ? (
        <div className="flex flex-col gap-2" aria-busy="true">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
          <span className="sr-only">Loading agents…</span>
        </div>
      ) : agents.isError ? (
        <div role="alert" className="flex items-center gap-3 text-sm text-destructive">
          Could not load the agents.
          <Button variant="secondary" size="sm" onClick={() => void agents.refetch()}>
            Try again
          </Button>
        </div>
      ) : agents.data.length === 0 ? (
        <p className="rounded-md border border-dashed p-6 text-center text-muted-foreground">
          {showArchived ? 'There are no agents yet.' : 'No agents yet. Create the first one.'}
        </p>
      ) : (
        <ul aria-label="Agents" className="m-0 flex flex-col gap-2 p-0">
          {agents.data.map((agent) => (
            <AgentCard
              key={agent.id}
              agent={agent}
              canManage={canManageAgent(workspace.role, me.data?.id === agent.createdById)}
              onEdit={() => setEditing({ agent })}
              onArchive={() => setArchiving(agent)}
              onRestore={() =>
                restore.mutate(agent.id, {
                  onSuccess: () => toast.success(`${agent.name} was restored`),
                  onError: (error) => toast.error(messageOf(error, 'Could not restore the agent.')),
                })
              }
            />
          ))}
        </ul>
      )}

      <AgentFormDialog
        workspace={workspace}
        agent={editing?.agent ?? null}
        open={editing !== null}
        onOpenChange={(open) => !open && setEditing(null)}
      />
      <ConfirmDialog
        open={archiving !== null}
        onOpenChange={(open) => !open && setArchiving(null)}
        title={`Archive ${archiving?.name ?? 'this agent'}?`}
        description="It can no longer be assigned issues, but the issues it already has keep its name. You can restore it later."
        confirmLabel="Archive"
        pending={archive.isPending}
        onConfirm={confirmArchive}
      />
    </div>
  )
}
