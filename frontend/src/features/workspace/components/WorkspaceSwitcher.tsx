import { Check, ChevronsUpDown, Plus } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { initials } from '@/shared/lib/format'
import { Button } from '@/shared/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'
import type { Workspace } from '../schemas'
import { CreateWorkspaceDialog } from './CreateWorkspaceDialog'

/** Shows the current workspace and lets the user jump to another one or create a new one. */
export function WorkspaceSwitcher({ current, workspaces }: { current: Workspace; workspaces: Workspace[] }) {
  const navigate = useNavigate()
  const [creating, setCreating] = useState(false)

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" className="h-10 w-full justify-between px-2" aria-label="Switch workspace">
            <span className="flex min-w-0 items-center gap-2">
              <span
                aria-hidden="true"
                className="flex size-6 shrink-0 items-center justify-center rounded-md bg-primary text-xs font-semibold text-primary-foreground"
              >
                {initials(current.name)}
              </span>
              <span className="truncate">{current.name}</span>
            </span>
            <ChevronsUpDown className="text-muted-foreground" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-60">
          <DropdownMenuLabel>Workspaces</DropdownMenuLabel>
          {workspaces.map((workspace) => (
            <DropdownMenuItem key={workspace.id} onSelect={() => void navigate(`/w/${workspace.slug}`)}>
              <span className="min-w-0 flex-1 truncate">{workspace.name}</span>
              {workspace.id === current.id && <Check aria-label="Current workspace" />}
            </DropdownMenuItem>
          ))}
          <DropdownMenuSeparator />
          <DropdownMenuItem onSelect={() => setCreating(true)}>
            <Plus />
            Create workspace
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <CreateWorkspaceDialog open={creating} onOpenChange={setCreating} />
    </>
  )
}
