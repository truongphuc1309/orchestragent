import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { messageOf } from '@/shared/api/errors'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { useInviteMember } from '../hooks'
import { canInvite, ROLE_LABEL } from '../permissions'
import { inviteFormSchema, roleSchema, ROLES, type InviteForm, type Workspace } from '../schemas'

/** Email and role fields that invite someone. Only offered to admins and owners. */
export function InviteFormView({ workspace }: { workspace: Workspace }) {
  const invite = useInviteMember(workspace.id)
  const form = useForm<InviteForm>({
    resolver: zodResolver(inviteFormSchema),
    defaultValues: { email: '', role: 'member' },
  })
  const emailError = form.formState.errors.email
  const allowedRoles = ROLES.filter((role) => canInvite(workspace.role, role))

  const submit = form.handleSubmit(({ email, role }) =>
    invite.mutate(
      { email, role },
      {
        onSuccess: (invitation) => {
          toast.success(`Invitation sent to ${invitation.email}`)
          form.reset({ email: '', role })
        },
      },
    ),
  )

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-2">
      <div className="flex items-start gap-2">
        <div className="flex flex-1 flex-col gap-1">
          <Label htmlFor="invite-email" className="sr-only">
            Email address
          </Label>
          <Input
            id="invite-email"
            type="email"
            autoComplete="off"
            placeholder="colleague@example.com"
            aria-invalid={emailError !== undefined}
            {...form.register('email')}
          />
        </div>
        <Controller
          control={form.control}
          name="role"
          render={({ field }) => (
            <Select
              value={field.value}
              onValueChange={(value) => {
                const parsed = roleSchema.safeParse(value)
                if (parsed.success) {
                  field.onChange(parsed.data)
                }
              }}
            >
              <SelectTrigger className="h-8 w-28" aria-label="Role for the invitation">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {allowedRoles.map((role) => (
                  <SelectItem key={role} value={role}>
                    {ROLE_LABEL[role]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
        <Button type="submit" disabled={invite.isPending}>
          {invite.isPending ? 'Inviting…' : 'Invite'}
        </Button>
      </div>
      {emailError && (
        <p role="alert" className="text-sm text-destructive">
          {emailError.message}
        </p>
      )}
      {invite.error && (
        <p role="alert" className="text-sm text-destructive">
          {messageOf(invite.error, 'Could not send the invitation. Please try again.')}
        </p>
      )}
    </form>
  )
}
