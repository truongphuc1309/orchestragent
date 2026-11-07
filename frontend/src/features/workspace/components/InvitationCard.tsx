import { toast } from 'sonner'
import { useNavigate } from 'react-router'
import { messageOf } from '@/shared/api/errors'
import { Button } from '@/shared/ui/button'
import { useAcceptInvitation } from '../hooks'
import type { Invitation } from '../schemas'
import { RoleBadge } from './RoleBadge'

/** One invitation addressed to the signed-in user, with a button that accepts it and opens the workspace. */
export function InvitationCard({ invitation }: { invitation: Invitation }) {
  const accept = useAcceptInvitation()
  const navigate = useNavigate()

  const onAccept = () =>
    accept.mutate(invitation.id, {
      onSuccess: (workspace) => {
        toast.success(`You joined ${workspace.name}`)
        void navigate(`/w/${workspace.slug}`)
      },
      onError: (error) => toast.error(messageOf(error, 'Could not accept the invitation.')),
    })

  return (
    <div className="flex items-center justify-between gap-3 rounded-md border p-3">
      <div className="min-w-0">
        <p className="truncate font-medium">{invitation.workspaceName}</p>
        <p className="flex items-center gap-2 text-xs text-muted-foreground">
          Invited by {invitation.invitedByName} <RoleBadge role={invitation.role} />
        </p>
      </div>
      <Button size="sm" disabled={accept.isPending} onClick={onAccept}>
        {accept.isPending ? 'Joining…' : 'Accept'}
      </Button>
    </div>
  )
}
