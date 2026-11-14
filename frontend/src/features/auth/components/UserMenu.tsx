import { Link, useNavigate, useParams } from 'react-router'
import { UserAvatar } from '@/shared/components/UserAvatar'
import { Button } from '@/shared/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'
import { useLogout, useMe } from '../hooks'

/**
 * Avatar button in the top bar that opens the account menu: who is signed in, their profile (inside a workspace,
 * where the profile page lives) and sign out.
 */
export function UserMenu() {
  const me = useMe()
  const logout = useLogout()
  const navigate = useNavigate()
  const { slug } = useParams()

  if (!me.data) {
    return null
  }
  const { name, email, avatarUrl } = me.data

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="rounded-full" aria-label="Account menu">
          <UserAvatar name={name} avatarUrl={avatarUrl} />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-56">
        <DropdownMenuLabel>
          <span className="block text-sm font-medium text-foreground">{name}</span>
          <span className="block text-xs font-normal text-muted-foreground">{email}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {slug !== undefined && (
          <DropdownMenuItem asChild>
            <Link to={`/w/${slug}/settings/profile`}>Profile</Link>
          </DropdownMenuItem>
        )}
        <DropdownMenuItem
          disabled={logout.isPending}
          onSelect={() => logout.mutate(undefined, { onSuccess: () => void navigate('/login', { replace: true }) })}
        >
          Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
