import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import type { Workspace } from '@/features/workspace/schemas'
import { messageOf } from '@/shared/api/errors'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'
import { useCreateProject, useUpdateProject } from '../hooks'
import {
  MAX_PROJECT_KEY,
  projectFormSchema,
  suggestKey,
  type Project,
  type ProjectForm,
  type ProjectFormInput,
} from '../schemas'

interface Props {
  workspace: Workspace
  /** The project to edit, or null to create a new one. */
  project: Project | null
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Called with the new project after it was created. */
  onCreated?: (project: Project) => void
}

export function ProjectFormDialog({ workspace, project, open, onOpenChange, onCreated }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{project ? 'Edit project' : 'Create project'}</DialogTitle>
          <DialogDescription>
            A project groups issues. Its key starts the key of every issue in it, for example WEB-12.
          </DialogDescription>
        </DialogHeader>
        {/* The content unmounts when the dialog closes, so every opening starts from the project as it is now. */}
        <ProjectFormView
          workspace={workspace}
          project={project}
          onClose={() => onOpenChange(false)}
          onCreated={onCreated}
        />
      </DialogContent>
    </Dialog>
  )
}

function ProjectFormView({
  workspace,
  project,
  onClose,
  onCreated,
}: {
  workspace: Workspace
  project: Project | null
  onClose: () => void
  onCreated?: (project: Project) => void
}) {
  const create = useCreateProject(workspace.id)
  const update = useUpdateProject(workspace.id)
  const mutation = project ? update : create
  // While the person has not typed a key themselves, it follows the name.
  const [keyTouched, setKeyTouched] = useState(false)

  const form = useForm<ProjectFormInput, unknown, ProjectForm>({
    resolver: zodResolver(projectFormSchema),
    defaultValues: { key: project?.key ?? '', name: project?.name ?? '', description: project?.description ?? '' },
  })
  const keyError = form.formState.errors.key
  const nameError = form.formState.errors.name
  const descriptionError = form.formState.errors.description

  // A server error (a key that is taken) is about what was sent: it goes away as soon as the person edits again.
  const forgetServerError = () => {
    if (mutation.error) {
      mutation.reset()
    }
  }
  const name = form.register('name', {
    onChange: (event: { target: { value: string } }) => {
      forgetServerError()
      if (project === null && !keyTouched) {
        form.setValue('key', suggestKey(event.target.value))
      }
    },
  })
  const key = form.register('key', {
    onChange: () => {
      forgetServerError()
      setKeyTouched(true)
    },
  })

  const submit = form.handleSubmit((values) => {
    if (project) {
      update.mutate(
        { projectId: project.id, changes: { name: values.name, description: values.description } },
        {
          onSuccess: (saved) => {
            toast.success(`${saved.name} was saved`)
            onClose()
          },
        },
      )
    } else {
      create.mutate(values, {
        onSuccess: (created) => {
          toast.success(`${created.name} (${created.key}) was created`)
          onClose()
          onCreated?.(created)
        },
      })
    }
  })

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="project-name">Name</Label>
        <Input id="project-name" autoComplete="off" autoFocus aria-invalid={nameError !== undefined} {...name} />
        {nameError && (
          <p role="alert" className="text-sm text-destructive">
            {nameError.message}
          </p>
        )}
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="project-key">Key</Label>
        <Input
          id="project-key"
          autoComplete="off"
          className="w-40 uppercase"
          maxLength={MAX_PROJECT_KEY}
          readOnly={project !== null}
          aria-readonly={project !== null}
          aria-invalid={keyError !== undefined}
          aria-describedby="project-key-hint"
          {...key}
        />
        <p id="project-key-hint" className="text-sm text-muted-foreground">
          {project
            ? 'The key cannot be changed: issue keys such as ' + project.key + '-1 stay the same forever.'
            : '2 to 10 letters and digits, starting with a letter. It cannot be changed later.'}
        </p>
        {keyError && (
          <p role="alert" className="text-sm text-destructive">
            {keyError.message}
          </p>
        )}
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="project-description">Description (optional)</Label>
        <Textarea
          id="project-description"
          rows={3}
          aria-invalid={descriptionError !== undefined}
          {...form.register('description')}
        />
        {descriptionError && (
          <p role="alert" className="text-sm text-destructive">
            {descriptionError.message}
          </p>
        )}
      </div>

      {mutation.error && (
        <p role="alert" className="text-sm text-destructive">
          {messageOf(mutation.error, 'Could not save the project. Please try again.')}
        </p>
      )}
      <DialogFooter>
        <Button type="button" variant="ghost" onClick={onClose}>
          Cancel
        </Button>
        <Button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Saving…' : project ? 'Save' : 'Create'}
        </Button>
      </DialogFooter>
    </form>
  )
}
