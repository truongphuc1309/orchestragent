import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { InvitationsList } from '../components/InvitationsList'
import { InviteFormView } from '../components/InviteForm'
import { MembersTable } from '../components/MembersTable'
import { useMembers } from '../hooks'
import { canManageInvitations } from '../permissions'
import { useWorkspace } from '../workspaceContext'

/** Settings → Members: who is in the workspace, their roles, and (for admins and owners) invitations. */
export function MembersPage() {
  const workspace = useWorkspace()
  const members = useMembers(workspace.id)
  const canManage = canManageInvitations(workspace.role)

  return (
    <div className="flex max-w-3xl flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl leading-7 font-medium">Members</h1>
        <p className="text-muted-foreground">People who can work in {workspace.name}.</p>
      </div>

      {canManage && (
        <section className="flex flex-col gap-3" aria-labelledby="invite-heading">
          <h2 id="invite-heading" className="text-base font-semibold">
            Invite people
          </h2>
          <InviteFormView workspace={workspace} />
        </section>
      )}

      <section className="flex flex-col gap-3" aria-labelledby="members-heading">
        <h2 id="members-heading" className="text-base font-semibold">
          Members{members.data ? ` (${members.data.length})` : ''}
        </h2>
        {members.isPending ? (
          <div className="flex flex-col gap-2" aria-busy="true">
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
            <span className="sr-only">Loading members…</span>
          </div>
        ) : members.isError ? (
          <div role="alert" className="flex items-center gap-3 text-sm text-destructive">
            Could not load the members.
            <Button variant="secondary" size="sm" onClick={() => void members.refetch()}>
              Try again
            </Button>
          </div>
        ) : (
          <MembersTable workspace={workspace} members={members.data} />
        )}
      </section>

      {canManage && (
        <section className="flex flex-col gap-3" aria-labelledby="pending-heading">
          <h2 id="pending-heading" className="text-base font-semibold">
            Pending invitations
          </h2>
          <InvitationsList workspace={workspace} />
        </section>
      )}
    </div>
  )
}
