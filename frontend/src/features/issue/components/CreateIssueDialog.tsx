import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { useAgents } from '@/features/agent/hooks'
import { useMembers } from '@/features/workspace/hooks'
import type { Workspace } from '@/features/workspace/schemas'
import { messageOf } from '@/shared/api/errors'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'
import { decodeAssignee, encodeAssignee } from '../assignee'
import { useCreateIssue } from '../hooks'
import { issueKey } from '../key'
import { createIssueFormSchema, type CreateIssueForm, type Status } from '../schemas'
import { AssigneeSelect, PrioritySelect, StatusSelect } from './IssueFields'

interface Props {
  workspace: Workspace
  open: boolean
  onOpenChange: (open: boolean) => void
  /** The column the dialog was opened from; new issues start there. */
  initialStatus?: Status
}

export function CreateIssueDialog({ workspace, open, onOpenChange, initialStatus = 'todo' }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Create issue</DialogTitle>
          <DialogDescription>Describe the work. You can assign it to a teammate now or later.</DialogDescription>
        </DialogHeader>
        {/* The content unmounts when the dialog closes, so every opening starts with an empty form. */}
        <CreateIssueFormView workspace={workspace} initialStatus={initialStatus} onClose={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  )
}

interface FormProps {
  workspace: Workspace
  initialStatus: Status
  onClose: () => void
}

function CreateIssueFormView({ workspace, initialStatus, onClose }: FormProps) {
  const members = useMembers(workspace.id)
  const agents = useAgents(workspace.id)
  const create = useCreateIssue(workspace.id)
  const form = useForm<CreateIssueForm>({
    resolver: zodResolver(createIssueFormSchema),
    defaultValues: { title: '', description: '', status: initialStatus, priority: 'none', assignee: '' },
  })
  const titleError = form.formState.errors.title
  const descriptionError = form.formState.errors.description

  const submit = form.handleSubmit((values) =>
    create.mutate(
      {
        title: values.title,
        description: values.description,
        status: values.status,
        priority: values.priority,
        assignee: decodeAssignee(values.assignee),
      },
      {
        onSuccess: (issue) => {
          toast.success(`${issueKey(workspace.slug, issue.number)} was created`)
          onClose()
        },
      },
    ),
  )

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
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
        <Button type="submit" disabled={create.isPending}>
          {create.isPending ? 'Creating…' : 'Create'}
        </Button>
      </DialogFooter>
    </form>
  )
}
