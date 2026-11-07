import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { messageOf } from '@/shared/api/errors'
import { Button } from '@/shared/ui/button'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'
import { useAddComment } from '../hooks'
import { commentFormSchema, type CommentForm as CommentFormValues } from '../schemas'

/** The box at the bottom of the timeline. Ctrl or Cmd + Enter sends. */
export function CommentFormView({ workspaceId, issueId }: { workspaceId: string; issueId: string }) {
  const add = useAddComment(workspaceId, issueId)
  const form = useForm<CommentFormValues>({ resolver: zodResolver(commentFormSchema), defaultValues: { body: '' } })
  const error = form.formState.errors.body

  const submit = form.handleSubmit(({ body }) => add.mutate(body, { onSuccess: () => form.reset({ body: '' }) }))

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-2">
      <Label htmlFor="comment-body">Add a comment</Label>
      <Textarea
        id="comment-body"
        rows={3}
        aria-invalid={error !== undefined}
        placeholder="Write a comment…"
        {...form.register('body')}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
            event.preventDefault()
            void submit()
          }
        }}
      />
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error.message}
        </p>
      )}
      {add.error && (
        <p role="alert" className="text-sm text-destructive">
          {messageOf(add.error, 'Could not send the comment. Please try again.')}
        </p>
      )}
      <div>
        <Button type="submit" disabled={add.isPending}>
          {add.isPending ? 'Sending…' : 'Comment'}
        </Button>
      </div>
    </form>
  )
}
