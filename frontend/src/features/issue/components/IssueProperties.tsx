import type { ReactNode } from 'react'
import { Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { useMe } from '@/features/auth/hooks'
import { useMembers } from '@/features/workspace/hooks'
import type { Workspace } from '@/features/workspace/schemas'
import { messageOf } from '@/shared/api/errors'
import { ConfirmDialog } from '@/shared/layout/ConfirmDialog'
import { formatDateTime } from '@/shared/lib/format'
import { Button } from '@/shared/ui/button'
import { useAssignIssue, useDeleteIssue, useUpdateIssue } from '../hooks'
import { issueKey } from '../key'
import { canDeleteIssue } from '../permissions'
import type { Issue } from '../schemas'
import { ActorAvatar } from './ActorAvatar'
import { AssigneeSelect, PrioritySelect, StatusSelect } from './IssueFields'

function Property({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="text-xs font-semibold text-muted-foreground uppercase">{label}</dt>
      <dd className="m-0">{children}</dd>
    </div>
  )
}

/** The right-hand panel of the issue page: status, priority, assignee, who made it, and delete. */
export function IssueProperties({ workspace, issue }: { workspace: Workspace; issue: Issue }) {
  const me = useMe()
  const members = useMembers(workspace.id)
  const navigate = useNavigate()
  const update = useUpdateIssue(workspace.id)
  const assign = useAssignIssue(workspace.id)
  const remove = useDeleteIssue(workspace.id)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const mayDelete = canDeleteIssue(workspace.role, me.data?.id === issue.creator.id)

  const onError = (fallback: string) => ({ onError: (error: unknown) => toast.error(messageOf(error, fallback)) })

  return (
    <aside aria-label="Details" className="flex w-72 shrink-0 flex-col gap-4">
      <dl className="m-0 flex flex-col gap-4">
        <Property label="Status">
          <StatusSelect
            label="Status"
            value={issue.status}
            onChange={(status) =>
              update.mutate({ issueId: issue.id, changes: { status } }, onError('Could not change the status.'))
            }
          />
        </Property>
        <Property label="Priority">
          <PrioritySelect
            label="Priority"
            value={issue.priority}
            onChange={(priority) =>
              update.mutate({ issueId: issue.id, changes: { priority } }, onError('Could not change the priority.'))
            }
          />
        </Property>
        <Property label="Assignee">
          <AssigneeSelect
            label="Assignee"
            value={issue.assignee?.id ?? null}
            members={members.data ?? []}
            currentName={issue.assignee?.name}
            onChange={(assigneeId) =>
              assign.mutate({ issueId: issue.id, assigneeId }, onError('Could not change the assignee.'))
            }
          />
        </Property>
        <Property label="Reporter">
          <span className="flex items-center gap-2">
            <ActorAvatar actor={issue.creator} />
            {issue.creator.name}
          </span>
        </Property>
        <Property label="Created">{formatDateTime(issue.createdAt)}</Property>
        <Property label="Updated">{formatDateTime(issue.updatedAt)}</Property>
      </dl>

      {mayDelete && (
        <>
          <Button variant="ghost" className="justify-start text-destructive" onClick={() => setConfirmingDelete(true)}>
            <Trash2 />
            Delete issue
          </Button>
          <ConfirmDialog
            open={confirmingDelete}
            onOpenChange={setConfirmingDelete}
            title={`Delete ${issueKey(workspace.slug, issue.number)}?`}
            description="The issue and all its comments will be deleted. This cannot be undone."
            confirmLabel="Delete"
            destructive
            pending={remove.isPending}
            onConfirm={() =>
              remove.mutate(issue.id, {
                onSuccess: () => {
                  toast.success(`${issueKey(workspace.slug, issue.number)} was deleted`)
                  void navigate(`/w/${workspace.slug}/issues`)
                },
                onError: (error) => {
                  setConfirmingDelete(false)
                  toast.error(messageOf(error, 'Could not delete the issue.'))
                },
              })
            }
          />
        </>
      )}
    </aside>
  )
}
