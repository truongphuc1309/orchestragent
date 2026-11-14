import { initials } from '@/shared/lib/format'
import { Avatar, AvatarFallback, AvatarImage } from '@/shared/ui/avatar'

interface UserAvatarProps {
  name: string
  /** The person's picture; null shows their initials. */
  avatarUrl: string | null
  size?: 'sm' | 'default' | 'lg'
  className?: string
}

/**
 * A person: their picture when they have one (cropped to a circle), otherwise their initials. The initials also show
 * while the picture loads and when it cannot be loaded. The picture is decorative: the name is always shown next to it
 * or in the label of the control around it.
 */
export function UserAvatar({ name, avatarUrl, size = 'default', className }: UserAvatarProps) {
  return (
    <Avatar size={size} className={className}>
      {avatarUrl !== null && <AvatarImage src={avatarUrl} alt="" />}
      <AvatarFallback>{initials(name)}</AvatarFallback>
    </Avatar>
  )
}
