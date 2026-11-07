import { formatDateTime } from '@/shared/lib/format'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { useComments } from '../hooks'
import { ActorAvatar } from './ActorAvatar'

/** The comments of an issue, oldest first. */
export function CommentTimeline({ workspaceId, issueId }: { workspaceId: string; issueId: string }) {
  const comments = useComments(workspaceId, issueId)

  if (comments.isPending) {
    return (
      <div className="flex flex-col gap-3" aria-busy="true">
        <Skeleton className="h-12 w-full" />
        <span className="sr-only">Loading comments…</span>
      </div>
    )
  }
  if (comments.isError) {
    return (
      <div role="alert" className="flex items-center gap-3 text-sm text-destructive">
        Could not load the comments.
        <Button variant="secondary" size="sm" onClick={() => void comments.refetch()}>
          Try again
        </Button>
      </div>
    )
  }
  if (comments.data.length === 0) {
    return <p className="text-muted-foreground">No comments yet.</p>
  }
  return (
    <ol aria-label="Comments" className="flex flex-col gap-4 p-0">
      {comments.data.map((comment) => (
        <li key={comment.id} className="flex list-none gap-3">
          <ActorAvatar actor={comment.author} />
          <div className="min-w-0 flex-1">
            <p className="flex items-baseline gap-2">
              <span className="font-medium">{comment.author.name}</span>
              <time dateTime={comment.createdAt} className="text-xs text-muted-foreground">
                {formatDateTime(comment.createdAt)}
              </time>
            </p>
            <p className="whitespace-pre-wrap break-words">{comment.body}</p>
          </div>
        </li>
      ))}
    </ol>
  )
}
