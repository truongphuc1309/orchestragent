import { Home, KeyRound, Users, type LucideIcon } from 'lucide-react'
import { NavLink } from 'react-router'
import { cn } from '@/shared/lib/utils'
import type { Workspace } from '../schemas'
import { WorkspaceSwitcher } from './WorkspaceSwitcher'

function SidebarLink({ to, icon: Icon, end, children }: { to: string; icon: LucideIcon; end?: boolean; children: string }) {
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
      {children}
    </NavLink>
  )
}

/** Left navigation of a workspace: switcher on top, then the pages of the workspace. */
export function WorkspaceSidebar({ current, workspaces }: { current: Workspace; workspaces: Workspace[] }) {
  const base = `/w/${current.slug}`

  return (
    <aside className="hidden w-60 shrink-0 flex-col gap-4 border-r bg-sidebar p-2 md:flex">
      <WorkspaceSwitcher current={current} workspaces={workspaces} />
      <nav aria-label="Workspace" className="flex flex-col gap-0.5">
        <SidebarLink to={base} icon={Home} end>
          Home
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
