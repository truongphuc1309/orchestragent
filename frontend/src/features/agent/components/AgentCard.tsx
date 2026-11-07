import { Sparkles } from 'lucide-react'
import { Avatar, AvatarFallback } from '@/shared/ui/avatar'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { cn } from '@/shared/lib/utils'
import type { Agent } from '../schemas'
import { RuntimeStatusLozenge } from './RuntimeStatusLozenge'

interface Props {
  agent: Agent
  /** Whether the signed-in person may edit, archive and restore this agent. */
  canManage: boolean
  onEdit: () => void
  onArchive: () => void
  onRestore: () => void
}

/** One agent in the list: who it is, where it runs, and (for those who may) what can be done with it. */
export function AgentCard({ agent, canManage, onEdit, onArchive, onRestore }: Props) {
  const preview = agent.instructions.replace(/\s+/g, ' ').trim()
  return (
    <li className={cn('flex gap-3 rounded-md border bg-card p-3 shadow-raised', agent.archived && 'bg-muted')}>
      <Avatar>
        <AvatarFallback className="bg-lozenge-discovery text-lozenge-discovery-foreground">
          <Sparkles className="size-4" aria-hidden />
        </AvatarFallback>
      </Avatar>
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className={cn('text-base font-semibold', agent.archived && 'text-muted-foreground')}>{agent.name}</h2>
          {agent.archived && <Badge variant="neutral">Archived</Badge>}
          {agent.runtime && (
            <span className="flex items-center gap-1.5 text-sm text-muted-foreground">
              on {agent.runtime.deviceName} · {agent.runtime.provider}
              <RuntimeStatusLozenge status={agent.runtime.status} />
            </span>
          )}
        </div>
        <p className="m-0 line-clamp-2 text-sm text-muted-foreground">{preview === '' ? 'No instructions.' : preview}</p>
        <p className="m-0 text-xs text-muted-foreground">
          Created by {agent.createdByName}
          {agent.model !== null && ` · model ${agent.model}`}
        </p>
      </div>
      {canManage && (
        <div className="flex shrink-0 items-start gap-2">
          {agent.archived ? (
            <Button variant="secondary" onClick={onRestore} aria-label={`Restore ${agent.name}`}>
              Restore
            </Button>
          ) : (
            <>
              <Button variant="secondary" onClick={onEdit} aria-label={`Edit ${agent.name}`}>
                Edit
              </Button>
              <Button variant="ghost" onClick={onArchive} aria-label={`Archive ${agent.name}`}>
                Archive
              </Button>
            </>
          )}
        </div>
      )}
    </li>
  )
}
