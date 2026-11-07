import { ChevronLeft } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { useWorkspace } from '@/features/workspace/workspaceContext'
import { ApiError, messageOf } from '@/shared/api/errors'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { AgentRunsPanel } from '@/features/task/components/AgentRunsPanel'
import { CommentFormView } from '../components/CommentForm'
import { CommentTimeline } from '../components/CommentTimeline'
import { InlineDescription } from '../components/InlineDescription'
import { InlineTitle } from '../components/InlineTitle'
import { IssueProperties } from '../components/IssueProperties'
import { ReviewBar } from '../components/ReviewBar'
import { useIssue, useUpdateIssue } from '../hooks'
import { issueKey } from '../key'

/** One issue: editable title and description, the properties panel and the comment timeline. */
export function IssueDetailPage() {
  const workspace = useWorkspace()
  const { number: rawNumber } = useParams()
  const number = /^\d+$/.test(rawNumber ?? '') ? Number(rawNumber) : null
  const issue = useIssue(workspace.id, number)
  const update = useUpdateIssue(workspace.id)
  const back = (
    <Link to={`/w/${workspace.slug}/issues`} className="flex items-center gap-1 text-sm">
      <ChevronLeft className="size-4" />
      Issues
    </Link>
  )

  if (number === null || (issue.isError && issue.error instanceof ApiError && issue.error.status === 404)) {
    return (
      <div className="flex flex-col gap-2">
        {back}
        <h1 className="text-2xl leading-7 font-medium">Issue not found</h1>
        <p className="text-muted-foreground">It does not exist in {workspace.name}, or it was deleted.</p>
      </div>
    )
  }
  if (issue.isPending) {
    return (
      <div className="flex flex-col gap-3" aria-busy="true">
        {back}
        <Skeleton className="h-8 w-96" />
        <Skeleton className="h-24 w-full" />
        <span className="sr-only">Loading the issue…</span>
      </div>
    )
  }
  if (issue.isError) {
    return (
      <div className="flex flex-col gap-3">
        {back}
        <div role="alert" className="flex items-center gap-3 text-sm text-destructive">
          Could not load the issue.
          <Button variant="secondary" size="sm" onClick={() => void issue.refetch()}>
            Try again
          </Button>
        </div>
      </div>
    )
  }

  const data = issue.data
  const save = (changes: { title?: string; description?: string }, fallback: string) =>
    update.mutate({ issueId: data.id, changes }, { onError: (error) => toast.error(messageOf(error, fallback)) })

  return (
    <div className="flex flex-col gap-4">
      {back}
      <div className="flex gap-8">
        <div className="flex min-w-0 flex-1 flex-col gap-6">
          <div className="flex flex-col gap-1">
            <p className="px-2 text-sm text-muted-foreground">{issueKey(workspace.slug, data.number)}</p>
            <InlineTitle title={data.title} onSave={(title) => save({ title }, 'Could not save the title.')} />
          </div>
          <section aria-labelledby="description-heading" className="flex flex-col gap-1">
            <h2 id="description-heading" className="px-2 text-base font-semibold">
              Description
            </h2>
            <InlineDescription
              description={data.description}
              saving={update.isPending}
              onSave={(description) => save({ description }, 'Could not save the description.')}
            />
          </section>
          <ReviewBar workspaceId={workspace.id} issue={data} />
          <AgentRunsPanel workspaceId={workspace.id} issueId={data.id} />
          <section aria-labelledby="comments-heading" className="flex flex-col gap-4">
            <h2 id="comments-heading" className="text-base font-semibold">
              Comments
            </h2>
            <CommentTimeline workspaceId={workspace.id} issueId={data.id} />
            <CommentFormView workspaceId={workspace.id} issueId={data.id} />
          </section>
        </div>
        <IssueProperties workspace={workspace} issue={data} />
      </div>
    </div>
  )
}
