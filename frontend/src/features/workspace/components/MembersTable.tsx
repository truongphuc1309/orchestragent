import { useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { useMe } from '@/features/auth/hooks'
import { messageOf } from '@/shared/api/errors'
import { ConfirmDialog } from '@/shared/layout/ConfirmDialog'
import { initials } from '@/shared/lib/format'
import { Avatar, AvatarFallback } from '@/shared/ui/avatar'
import { Button } from '@/shared/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { useChangeMemberRole, useRemoveMember } from '../hooks'
import { canChangeRole, canRemove, ROLE_LABEL } from '../permissions'
import { roleSchema, ROLES, type Member, type Workspace } from '../schemas'
import { RoleBadge } from './RoleBadge'

export function MembersTable({ workspace, members }: { workspace: Workspace; members: Member[] }) {
  const me = useMe()
  const navigate = useNavigate()
  const changeRole = useChangeMemberRole(workspace.id)
  const remove = useRemoveMember(workspace.id)
  const [removing, setRemoving] = useState<Member | null>(null)
  const myId = me.data?.id
  const leaving = removing !== null && removing.userId === myId

  const onRoleSelected = (member: Member, value: string) => {
    const parsed = roleSchema.safeParse(value)
    if (!parsed.success || parsed.data === member.role) {
      return
    }
    changeRole.mutate(
      { userId: member.userId, role: parsed.data },
      {
        onSuccess: () => toast.success(`${member.name} is now ${ROLE_LABEL[parsed.data].toLowerCase()}`),
        onError: (error) => toast.error(messageOf(error, 'Could not change the role.')),
      },
    )
  }

  const confirmRemoval = () => {
    if (removing === null) {
      return
    }
    const target = removing
    remove.mutate(target.userId, {
      onSuccess: () => {
        setRemoving(null)
        if (target.userId === myId) {
          toast.success(`You left ${workspace.name}`)
          void navigate('/')
        } else {
          toast.success(`${target.name} was removed`)
        }
      },
      onError: (error) => {
        setRemoving(null)
        toast.error(messageOf(error, 'Could not remove the member.'))
      },
    })
  }

  return (
    <>
      <ul className="divide-y rounded-md border" aria-label="Members">
        {members.map((member) => {
          const isSelf = member.userId === myId
          const options = ROLES.filter((role) => canChangeRole(workspace.role, member.role, role))
          return (
            <li key={member.userId} className="flex items-center gap-3 px-3 py-2">
              <Avatar size="sm">
                <AvatarFallback>{initials(member.name)}</AvatarFallback>
              </Avatar>
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">
                  {member.name}
                  {isSelf && <span className="ml-1 font-normal text-muted-foreground">(you)</span>}
                </p>
                <p className="truncate text-xs text-muted-foreground">{member.email}</p>
              </div>
              {options.length > 0 ? (
                <Select value={member.role} onValueChange={(value) => onRoleSelected(member, value)}>
                  <SelectTrigger size="sm" className="w-28" aria-label={`Role of ${member.name}`}>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {options.map((role) => (
                      <SelectItem key={role} value={role}>
                        {ROLE_LABEL[role]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              ) : (
                <RoleBadge role={member.role} />
              )}
              <div className="w-20 text-right">
                {canRemove(workspace.role, member.role, isSelf) && (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-destructive"
                    aria-label={isSelf ? `Leave ${workspace.name}` : `Remove ${member.name}`}
                    onClick={() => setRemoving(member)}
                  >
                    {isSelf ? 'Leave' : 'Remove'}
                  </Button>
                )}
              </div>
            </li>
          )
        })}
      </ul>
      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => !open && setRemoving(null)}
        title={leaving ? `Leave ${workspace.name}?` : `Remove ${removing?.name ?? ''}?`}
        description={
          leaving
            ? 'You will lose access to this workspace until someone invites you again.'
            : 'They lose access to this workspace immediately.'
        }
        confirmLabel={leaving ? 'Leave workspace' : 'Remove member'}
        destructive
        pending={remove.isPending}
        onConfirm={confirmRemoval}
      />
    </>
  )
}
