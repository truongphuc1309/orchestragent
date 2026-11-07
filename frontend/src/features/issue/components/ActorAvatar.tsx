import { Sparkles } from 'lucide-react'
import { initials } from '@/shared/lib/format'
import { Avatar, AvatarFallback } from '@/shared/ui/avatar'
import type { Actor } from '../schemas'

/** A member shows their initials; an agent always shows the purple "discovery" tone with a spark. */
export function ActorAvatar({ actor, size = 'sm' }: { actor: Actor; size?: 'sm' | 'default' }) {
  if (actor.type === 'agent') {
    return (
      <Avatar size={size}>
        <AvatarFallback className="bg-lozenge-discovery text-lozenge-discovery-foreground">
          <Sparkles className="size-3" aria-hidden />
        </AvatarFallback>
      </Avatar>
    )
  }
  return (
    <Avatar size={size}>
      <AvatarFallback>{actor.type === 'system' ? 'S' : initials(actor.name)}</AvatarFallback>
    </Avatar>
  )
}
