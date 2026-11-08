import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { useAgents } from '@/features/agent/hooks'
import { useProjects } from '@/features/project/hooks'
import { useMembers } from '@/features/workspace/hooks'
import type { Workspace } from '@/features/workspace/schemas'
import { messageOf } from '@/shared/api/errors'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'
import { decodeAssignee, encodeAssignee } from '../assignee'
import { useCreateIssue } from '../hooks'
import { useIssueViewStore } from '../filters'
import { createIssueFormSchema, type CreateIssueForm, type Status } from '../schemas'
import { AssigneeSelect, PrioritySelect, StatusSelect } from './IssueFields'

interface Props {
  workspace: Workspace
  open: boolean
  onOpenChange: (open: boolean) => void
  /** The column the dialog was opened from; new issues start there. */
  initialStatus?: Status
  /** The project page the dialog was opened from; otherwise the project of the last issue created here. */
  initialProjectId?: string
}

export function CreateIssueDialog({ workspace, open, onOpenChange, initialStatus = 'todo', initialProjectId }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Create issue</DialogTitle>
          <DialogDescription>Describe the work. You can assign it to a teammate now or later.</DialogDescription>
        </DialogHeader>
        {/* The content unmounts when the dialog closes, so every opening starts with an empty form. */}
        <CreateIssueFormView
          workspace={workspace}
          initialStatus={initialStatus}
          initialProjectId={initialProjectId}
          onClose={() => onOpenChange(false)}
        />
      </DialogContent>
    </Dialog>
  )
}

interface FormProps {
  workspace: Workspace
  initialStatus: Status
  initialProjectId: string | undefined
  onClose: () => void
}

function CreateIssueFormView({ workspace, initialStatus, initialProjectId, onClose }: FormProps) {
  const members = useMembers(workspace.id)
  const agents = useAgents(workspace.id)
  // Active projects only: an archived project takes no new issues.
  const projects = useProjects(workspace.id)
  const lastProjectId = useIssueViewStore((state) => state.lastProjectByWorkspace[workspace.id])
  const setLastProject = useIssueViewStore((state) => state.setLastProject)
  const create = useCreateIssue(workspace.id)

  const active = projects.data ?? []
  const preferred = [initialProjectId, lastProjectId].find(
    (id) => id !== undefined && active.some((project) => project.id === id),
  )
  const defaultProjectId = preferred ?? active[0]?.id
  const noProject = projects.isSuccess && active.length === 0

  const form = useForm<CreateIssueForm>({
    resolver: zodResolver(createIssueFormSchema),
    defaultValues: {
      projectId: defaultProjectId ?? '',
      title: '',
      description: '',
      status: initialStatus,
      priority: 'none',
      assignee: '',
    },
  })
  const titleError = form.formState.errors.title
  const descriptionError = form.formState.errors.description
  const projectError = form.formState.errors.projectId

  // The projects may arrive after the form opened: choose the default then, if nothing is chosen yet.
  useEffect(() => {
    if (defaultProjectId !== undefined && form.getValues('projectId') === '') {
      form.setValue('projectId', defaultProjectId)
    }
  }, [defaultProjectId, form])

  const submit = form.handleSubmit((values) =>
    create.mutate(
      {
        projectId: values.projectId,
        title: values.title,
        description: values.description,
        status: values.status,
        priority: values.priority,
        assignee: decodeAssignee(values.assignee),
      },
      {
        onSuccess: (issue) => {
          setLastProject(workspace.id, issue.projectId)
          toast.success(`${issue.key} was created`)
          onClose()
        },
      },
    ),
  )

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <Controller
        control={form.control}
        name="projectId"
        render={({ field }) => (
          <div className="flex flex-col gap-1.5">
            <Label>Project</Label>
            <Select value={field.value === '' ? undefined : field.value} onValueChange={field.onChange}>
              <SelectTrigger aria-label="Project" aria-invalid={projectError !== undefined} className="w-full sm:w-72">
                <SelectValue placeholder={projects.isPending ? 'Loading projects…' : 'Choose a project'} />
              </SelectTrigger>
              <SelectContent>
                {active.map((project) => (
                  <SelectItem key={project.id} value={project.id}>
                    <span className="text-muted-foreground">{project.key}</span>
                    {project.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {projectError && (
              <p role="alert" className="text-sm text-destructive">
                {projectError.message}
              </p>
            )}
            {noProject && (
              <p role="status" className="text-sm text-muted-foreground">
                Issues live in projects, and {workspace.name} has none yet.{' '}
                <Link to={`/w/${workspace.slug}/projects`} onClick={onClose}>
                  Create a project
                </Link>{' '}
                first.
              </p>
            )}
          </div>
        )}
      />
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="issue-title">Title</Label>
        <Input
          id="issue-title"
          autoComplete="off"
          autoFocus
          aria-invalid={titleError !== undefined}
          {...form.register('title')}
        />
        {titleError && (
          <p role="alert" className="text-sm text-destructive">
            {titleError.message}
          </p>
        )}
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="issue-description">Description</Label>
        <Textarea
          id="issue-description"
          rows={4}
          aria-invalid={descriptionError !== undefined}
          {...form.register('description')}
        />
        {descriptionError && (
          <p role="alert" className="text-sm text-destructive">
            {descriptionError.message}
          </p>
        )}
      </div>
      <div className="grid grid-cols-3 gap-3">
        <Controller
          control={form.control}
          name="status"
          render={({ field }) => (
            <div className="flex flex-col gap-1.5">
              <Label>Status</Label>
              <StatusSelect value={field.value} onChange={field.onChange} label="Status" className="w-full" />
            </div>
          )}
        />
        <Controller
          control={form.control}
          name="priority"
          render={({ field }) => (
            <div className="flex flex-col gap-1.5">
              <Label>Priority</Label>
              <PrioritySelect value={field.value} onChange={field.onChange} label="Priority" className="w-full" />
            </div>
          )}
        />
        <Controller
          control={form.control}
          name="assignee"
          render={({ field }) => (
            <div className="flex flex-col gap-1.5">
              <Label>Assignee</Label>
              <AssigneeSelect
                value={decodeAssignee(field.value)}
                onChange={(ref) => field.onChange(ref === null ? '' : encodeAssignee(ref))}
                label="Assignee"
                members={members.data ?? []}
                agents={agents.data ?? []}
                className="w-full"
              />
            </div>
          )}
        />
      </div>
      {create.error && (
        <p role="alert" className="text-sm text-destructive">
          {messageOf(create.error, 'Could not create the issue. Please try again.')}
        </p>
      )}
      <DialogFooter>
        <Button type="button" variant="ghost" onClick={onClose}>
          Cancel
        </Button>
        <Button type="submit" disabled={create.isPending || noProject}>
          {create.isPending ? 'Creating…' : 'Create'}
        </Button>
      </DialogFooter>
    </form>
  )
}
