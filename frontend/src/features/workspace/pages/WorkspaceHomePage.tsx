import { RoleBadge } from '../components/RoleBadge'
import { useWorkspace } from '../workspaceContext'

/** Placeholder start page of a workspace until issues arrive in step 5. */
export function WorkspaceHomePage() {
  const workspace = useWorkspace()

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-3">
        <h1 className="text-2xl leading-7 font-medium">{workspace.name}</h1>
        <RoleBadge role={workspace.role} />
      </div>
      <p className="text-muted-foreground">Issues, the board and agents are coming in the next steps.</p>
    </div>
  )
}
