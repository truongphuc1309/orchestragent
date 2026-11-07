import { Link } from 'react-router'
import { formatDate } from '@/shared/lib/format'
import { issueKey } from '../key'
import type { Issue } from '../schemas'
import { ActorAvatar } from './ActorAvatar'
import { PriorityIcon } from './PriorityIcon'
import { StatusLozenge } from './StatusLozenge'

/** All issues as a table, newest first (the order the server returns). */
export function IssueList({ issues, workspaceSlug }: { issues: Issue[]; workspaceSlug: string }) {
  return (
    <div className="overflow-x-auto rounded-md border">
      <table className="w-full text-left text-sm">
        <thead className="border-b bg-muted text-xs text-muted-foreground uppercase">
          <tr>
            <th scope="col" className="w-10 px-3 py-2 font-semibold">
              <span className="sr-only">Priority</span>
            </th>
            <th scope="col" className="w-24 px-3 py-2 font-semibold">
              Key
            </th>
            <th scope="col" className="px-3 py-2 font-semibold">
              Title
            </th>
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
              <td className="px-3 py-2">
                <PriorityIcon priority={issue.priority} />
              </td>
              <td className="px-3 py-2 whitespace-nowrap text-muted-foreground">{issueKey(workspaceSlug, issue.number)}</td>
              <td className="px-3 py-2">
                <Link to={`/w/${workspaceSlug}/issues/${issue.number}`} className="text-foreground">
                  {issue.title}
                </Link>
              </td>
              <td className="px-3 py-2">
                <StatusLozenge status={issue.status} />
              </td>
              <td className="px-3 py-2">
                {issue.assignee ? (
                  <span className="flex items-center gap-2">
                    <ActorAvatar actor={issue.assignee} />
                    <span className="truncate">{issue.assignee.name}</span>
                  </span>
                ) : (
                  <span className="text-muted-foreground">Unassigned</span>
                )}
              </td>
              <td className="px-3 py-2 whitespace-nowrap text-muted-foreground">{formatDate(issue.updatedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
