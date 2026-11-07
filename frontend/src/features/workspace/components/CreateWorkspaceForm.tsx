import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { messageOf } from '@/shared/api/errors'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { useCreateWorkspace } from '../hooks'
import { createWorkspaceFormSchema, MAX_WORKSPACE_NAME, type CreateWorkspaceForm, type Workspace } from '../schemas'

/** Name field plus button; used on the onboarding page and in the "create workspace" dialog. */
export function CreateWorkspaceFormView({ onCreated }: { onCreated: (workspace: Workspace) => void }) {
  const create = useCreateWorkspace()
  const form = useForm<CreateWorkspaceForm>({
    resolver: zodResolver(createWorkspaceFormSchema),
    defaultValues: { name: '' },
  })
  const nameError = form.formState.errors.name

  const submit = form.handleSubmit(({ name }) => create.mutate(name, { onSuccess: onCreated }))

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <Label htmlFor="workspace-name">Workspace name</Label>
        <Input
          id="workspace-name"
          autoFocus
          autoComplete="off"
          placeholder="Acme Team"
          maxLength={MAX_WORKSPACE_NAME}
          aria-invalid={nameError !== undefined}
          {...form.register('name')}
        />
        {nameError && (
          <p role="alert" className="text-sm text-destructive">
            {nameError.message}
          </p>
        )}
      </div>
      {create.error && (
        <p role="alert" className="text-sm text-destructive">
          {messageOf(create.error, 'Could not create the workspace. Please try again.')}
        </p>
      )}
      <Button type="submit" disabled={create.isPending}>
        {create.isPending ? 'Creating…' : 'Create workspace'}
      </Button>
    </form>
  )
}
