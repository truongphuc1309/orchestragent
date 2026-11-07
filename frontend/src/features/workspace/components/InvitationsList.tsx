import { useState } from 'react'
import { toast } from 'sonner'
import { messageOf } from '@/shared/api/errors'
import { ConfirmDialog } from '@/shared/layout/ConfirmDialog'
import { formatDate } from '@/shared/lib/format'
import { Button } from '@/shared/ui/button'
import { useInvitations, useRevokeInvitation } from '../hooks'
import type { Invitation, Workspace } from '../schemas'
import { RoleBadge } from './RoleBadge'

/** Pending invitations of a workspace, with a button to withdraw each one. Admins and owners only. */
export function InvitationsList({ workspace }: { workspace: Workspace }) {
  const invitations = useInvitations(workspace.id, true)
  const revoke = useRevokeInvitation(workspace.id)
  const [revoking, setRevoking] = useState<Invitation | null>(null)

  if (invitations.isPending) {
    return <p className="text-sm text-muted-foreground">Loading invitations…</p>
  }
  if (invitations.isError) {
    return (
      <p role="alert" className="text-sm text-destructive">
        Could not load the invitations.
      </p>
    )
  }
  if (invitations.data.length === 0) {
    return <p className="text-sm text-muted-foreground">No pending invitations.</p>
  }

  const confirmRevoke = () => {
    if (revoking === null) {
      return
    }
    const target = revoking
    revoke.mutate(target.id, {
      onSuccess: () => {
        setRevoking(null)
        toast.success(`Invitation to ${target.email} withdrawn`)
      },
      onError: (error) => {
        setRevoking(null)
        toast.error(messageOf(error, 'Could not withdraw the invitation.'))
      },
    })
  }

  return (
    <>
      <ul className="divide-y rounded-md border" aria-label="Pending invitations">
        {invitations.data.map((invitation) => (
          <li key={invitation.id} className="flex items-center gap-3 px-3 py-2">
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium">{invitation.email}</p>
              <p className="text-xs text-muted-foreground">
                Invited by {invitation.invitedByName} on {formatDate(invitation.createdAt)}
              </p>
            </div>
            <RoleBadge role={invitation.role} />
            <Button
              variant="ghost"
              size="sm"
              className="text-destructive"
              aria-label={`Withdraw invitation to ${invitation.email}`}
              onClick={() => setRevoking(invitation)}
            >
              Withdraw
            </Button>
          </li>
        ))}
      </ul>
      <ConfirmDialog
        open={revoking !== null}
        onOpenChange={(open) => !open && setRevoking(null)}
        title="Withdraw this invitation?"
        description={`${revoking?.email ?? 'The invited person'} will no longer be able to join with it.`}
        confirmLabel="Withdraw invitation"
        destructive
        pending={revoke.isPending}
        onConfirm={confirmRevoke}
      />
    </>
  )
}
