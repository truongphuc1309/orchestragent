import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { messageOf } from '@/shared/api/errors'
import { Button } from '@/shared/ui/button'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'
import { useApproveIssue, useRequestChanges } from '../hooks'
import { requestChangesFormSchema, type Issue, type RequestChangesForm } from '../schemas'

/**
 * The review gate. When an issue is in review (an agent finished its work) a person decides: approve it, and the
 * issue is done, or send it back with a comment saying what should change. The comment is required.
 */
export function ReviewBar({ workspaceId, issue }: { workspaceId: string; issue: Issue }) {
  const approve = useApproveIssue(workspaceId)
  const requestChanges = useRequestChanges(workspaceId)
  const [sendingBack, setSendingBack] = useState(false)
  const form = useForm<RequestChangesForm>({
    resolver: zodResolver(requestChangesFormSchema),
    defaultValues: { comment: '' },
  })
  const commentError = form.formState.errors.comment

  if (issue.status !== 'in_review') {
    return null
  }

  const sendBack = form.handleSubmit(({ comment }) =>
    requestChanges.mutate(
      { issueId: issue.id, comment },
      {
        onSuccess: () => {
          toast.success('Sent back with your comment')
          setSendingBack(false)
          form.reset({ comment: '' })
        },
      },
    ),
  )

  return (
    <section
      aria-labelledby="review-heading"
      className="flex flex-col gap-3 rounded-md border border-selected-foreground/30 bg-selected p-3"
    >
      <div>
        <h2 id="review-heading" className="m-0 text-base font-semibold">
          Waiting for your review
        </h2>
        <p className="m-0 text-sm text-muted-foreground">
          Approve the result to finish the issue, or send it back and say what should change.
        </p>
      </div>
      {sendingBack ? (
        <form onSubmit={sendBack} noValidate className="flex flex-col gap-2">
          <Label htmlFor="review-comment">What should change?</Label>
          <Textarea
            id="review-comment"
            rows={4}
            autoFocus
            aria-invalid={commentError !== undefined}
            {...form.register('comment')}
          />
          {commentError && (
            <p role="alert" className="text-sm text-destructive">
              {commentError.message}
            </p>
          )}
          {requestChanges.error && (
            <p role="alert" className="text-sm text-destructive">
              {messageOf(requestChanges.error, 'Could not send it back. Please try again.')}
            </p>
          )}
          <div className="flex gap-2">
            <Button type="submit" disabled={requestChanges.isPending}>
              {requestChanges.isPending ? 'Sending…' : 'Send back'}
            </Button>
            <Button type="button" variant="ghost" onClick={() => setSendingBack(false)}>
              Cancel
            </Button>
          </div>
        </form>
      ) : (
        <div className="flex gap-2">
          <Button
            disabled={approve.isPending}
            onClick={() =>
              approve.mutate(issue.id, {
                onSuccess: () => toast.success('Approved: the issue is done'),
                onError: (error) => toast.error(messageOf(error, 'Could not approve the issue.')),
              })
            }
          >
            Approve
          </Button>
          <Button variant="secondary" onClick={() => setSendingBack(true)}>
            Request changes
          </Button>
        </div>
      )}
    </section>
  )
}
