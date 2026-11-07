import { Link } from 'react-router'
import { Button } from '@/shared/ui/button'
import { RoleBadge } from '../components/RoleBadge'
import { useWorkspace } from '../workspaceContext'

/** Start page of a workspace. Agents and the inbox will add their own sections in later steps. */
export function WorkspaceHomePage() {
  const workspace = useWorkspace()

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-3">
        <h1 className="text-2xl leading-7 font-medium">{workspace.name}</h1>
        <RoleBadge role={workspace.role} />
      </div>
      <p className="text-muted-foreground">Track the work of your team on the board, or open the list of all issues.</p>
      <div>
        <Button asChild>
          <Link to={`/w/${workspace.slug}/issues`}>Go to issues</Link>
        </Button>
      </div>
    </div>
  )
}
