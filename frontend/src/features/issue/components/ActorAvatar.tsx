import { Sparkles } from 'lucide-react'
import { UserAvatar } from '@/shared/components/UserAvatar'
import { Avatar, AvatarFallback } from '@/shared/ui/avatar'
import type { Actor } from '../schemas'

/**
 * A member shows their picture, or their initials without one; an agent always shows the purple "discovery" tone
 * with a spark; the system shows an S.
 */
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
  if (actor.type === 'system') {
    return (
      <Avatar size={size}>
        <AvatarFallback>S</AvatarFallback>
      </Avatar>
    )
  }
  return <UserAvatar name={actor.name} avatarUrl={actor.avatarUrl} size={size} />
}
