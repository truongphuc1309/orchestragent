import { formatDate } from '@/shared/lib/format'
import { assigneeRef } from '../assignee'
import { useIssueEditing } from '../editing'
import type { Issue } from '../schemas'
import { EditableTitleLink } from './EditableTitleLink'
import { AssigneeSelect, PrioritySelect, StatusSelect } from './IssueFields'

/**
 * All issues as a table, newest first (the order the server returns). The title, status, priority and assignee
 * can be changed in the row. The project column shows when {@code projectNames} is given (the issues come from
 * several projects).
 */
export function IssueList({
  workspaceId,
  issues,
  workspaceSlug,
  projectNames,
}: {
  workspaceId: string
  issues: Issue[]
  workspaceSlug: string
  /** Project names by project ID. */
  projectNames?: ReadonlyMap<string, string>
}) {
  const editing = useIssueEditing(workspaceId)

  return (
    <div className="relative overflow-x-auto rounded-md border">
      <table className="w-full text-left text-sm">
        <thead className="border-b bg-muted text-xs text-muted-foreground uppercase">
          <tr>
            <th scope="col" className="w-16 px-3 py-2 font-semibold">
              Priority
            </th>
            <th scope="col" className="w-24 px-3 py-2 font-semibold">
              Key
            </th>
            <th scope="col" className="min-w-72 px-3 py-2 font-semibold">
              Title
            </th>
            {projectNames && (
              <th scope="col" className="w-40 px-3 py-2 font-semibold">
                Project
              </th>
            )}
            <th scope="col" className="w-36 px-3 py-2 font-semibold">
              Status
            </th>
            <th scope="col" className="w-48 px-3 py-2 font-semibold">
              Assignee
            </th>
            <th scope="col" className="w-32 px-3 py-2 font-semibold">
              Updated
            </th>
          </tr>
        </thead>
        <tbody className="divide-y">
          {issues.map((issue) => (
            <tr key={issue.id} className="hover:bg-accent">
              <td className="px-1 py-1">
                <PrioritySelect
                  compact
                  label="Priority"
                  value={issue.priority}
                  onChange={(priority) => editing.setPriority(issue, priority)}
                />
              </td>
              <td className="px-3 py-2 whitespace-nowrap text-muted-foreground">{issue.key}</td>
              <td className="px-3 py-2">
                <EditableTitleLink
                  title={issue.title}
                  to={`/w/${workspaceSlug}/issues/${issue.key}`}
                  onSave={(title) => editing.setTitle(issue, title)}
                />
              </td>
              {projectNames && (
                <td className="px-3 py-2 text-muted-foreground">
                  <span className="line-clamp-1">{projectNames.get(issue.projectId) ?? 'Unknown project'}</span>
                </td>
              )}
              <td className="px-1 py-1">
                <StatusSelect
                  compact
                  label="Status"
                  value={issue.status}
                  onChange={(status) => editing.setStatus(issue, status)}
                />
              </td>
              <td className="px-1 py-1">
                <AssigneeSelect
                  compact
                  label="Assignee"
                  value={assigneeRef(issue.assignee)}
                  members={editing.members}
                  agents={editing.agents}
                  currentName={issue.assignee?.name}
                  currentActor={issue.assignee}
                  onChange={(assignee) => editing.setAssignee(issue, assignee)}
                />
              </td>
              <td className="px-3 py-2 whitespace-nowrap text-muted-foreground">{formatDate(issue.updatedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
