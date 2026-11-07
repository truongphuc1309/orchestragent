import { BarChart3, Bot, CircleDot, Home, Inbox, KeyRound, Server, Users, type LucideIcon } from 'lucide-react'
import { NavLink } from 'react-router'
import { useUnreadCount } from '@/features/inbox/hooks'
import { cn } from '@/shared/lib/utils'
import type { Workspace } from '../schemas'
import { WorkspaceSwitcher } from './WorkspaceSwitcher'

function SidebarLink({
  to,
  icon: Icon,
  end,
  badge,
  children,
}: {
  to: string
  icon: LucideIcon
  end?: boolean
  badge?: string
  children: string
}) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        cn(
          'flex h-8 items-center gap-2 rounded-md px-2 text-sm no-underline hover:no-underline',
          isActive ? 'bg-selected font-medium text-selected-foreground' : 'text-foreground hover:bg-accent',
        )
      }
    >
      <Icon className="size-4 shrink-0" />
      <span className="flex-1">{children}</span>
      {badge !== undefined && (
        <span className="rounded-full bg-primary px-1.5 text-xs leading-5 font-semibold text-primary-foreground">
          {badge}
        </span>
      )}
    </NavLink>
  )
}

/** "3", or "99+" for many; nothing when there is nothing unread. */
function unreadBadge(count: number | undefined): string | undefined {
  if (count === undefined || count <= 0) {
    return undefined
  }
  return count > 99 ? '99+' : String(count)
}

/** Left navigation of a workspace: switcher on top, then the pages of the workspace. */
export function WorkspaceSidebar({ current, workspaces }: { current: Workspace; workspaces: Workspace[] }) {
  const base = `/w/${current.slug}`
  const unread = useUnreadCount(current.id)
  const badge = unreadBadge(unread.data)

  return (
    <aside className="hidden w-60 shrink-0 flex-col gap-4 border-r bg-sidebar p-2 md:flex">
      <WorkspaceSwitcher current={current} workspaces={workspaces} />
      <nav aria-label="Workspace" className="flex flex-col gap-0.5">
        <SidebarLink to={base} icon={Home} end>
          Home
        </SidebarLink>
        <SidebarLink to={`${base}/inbox`} icon={Inbox} badge={badge}>
          Inbox
        </SidebarLink>
        <SidebarLink to={`${base}/issues`} icon={CircleDot}>
          Issues
        </SidebarLink>
        <SidebarLink to={`${base}/agents`} icon={Bot}>
          Agents
        </SidebarLink>
        <SidebarLink to={`${base}/runtimes`} icon={Server}>
          Runtimes
        </SidebarLink>
        <SidebarLink to={`${base}/usage`} icon={BarChart3}>
          Usage
        </SidebarLink>
        <p className="mt-4 mb-1 px-2 text-xs font-semibold text-muted-foreground uppercase">Settings</p>
        <SidebarLink to={`${base}/settings/members`} icon={Users}>
          Members
        </SidebarLink>
        <SidebarLink to={`${base}/settings/tokens`} icon={KeyRound}>
          API tokens
        </SidebarLink>
      </nav>
    </aside>
  )
}
