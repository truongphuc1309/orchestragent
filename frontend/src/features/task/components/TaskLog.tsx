import { useEffect, useRef } from 'react'
import { Button } from '@/shared/ui/button'
import { cn } from '@/shared/lib/utils'
import { useTaskMessages } from '../hooks'
import { isActive, type Task, type TaskMessage } from '../schemas'

/** Long tool output is shown cut; the full text is stored and the log is for reading, not for archiving. */
const SHOWN_LENGTH = 4000

function Shown({ text }: { text: string }) {
  return (
    <pre className="m-0 max-h-80 overflow-auto rounded-md border bg-muted p-2 text-xs whitespace-pre-wrap">
      {text.length > SHOWN_LENGTH ? `${text.slice(0, SHOWN_LENGTH)}\n… (${text.length - SHOWN_LENGTH} more characters)` : text}
    </pre>
  )
}

/** One line of the log. Tool calls and their results are folded away, text is shown as it is. */
function Line({ message }: { message: TaskMessage }) {
  switch (message.type) {
    case 'text':
      return <p className="m-0 whitespace-pre-wrap break-words">{message.content}</p>
    case 'tool_use':
      return (
        <details className="text-sm">
          <summary className="cursor-pointer text-muted-foreground">
            Tool: {message.content.split('\n')[0]?.slice(0, 100) || 'call'}
          </summary>
          <Shown text={message.content} />
        </details>
      )
    case 'tool_result':
      return (
        <details className="text-sm">
          <summary className="cursor-pointer text-muted-foreground">Result</summary>
          <Shown text={message.content} />
        </details>
      )
    case 'error':
      return <p className="m-0 whitespace-pre-wrap break-words text-destructive">{message.content}</p>
    case 'system':
      return <p className="m-0 whitespace-pre-wrap break-words text-muted-foreground italic">{message.content}</p>
  }
}

/** The log of one run, following it live while it runs (the page refreshes it when the server says there is more). */
export function TaskLog({ workspaceId, task, number }: { workspaceId: string; task: Task; number: number }) {
  const messages = useTaskMessages(workspaceId, task.id, true)
  const active = isActive(task.status)
  const end = useRef<HTMLDivElement>(null)
  const count = messages.data?.length ?? 0

  useEffect(() => {
    if (active && count > 0) {
      end.current?.scrollIntoView({ block: 'nearest' })
    }
  }, [active, count])

  if (messages.isPending) {
    return <p className="m-0 text-sm text-muted-foreground">Loading the log…</p>
  }
  if (messages.isError) {
    return (
      <div role="alert" className="flex items-center gap-3 text-sm text-destructive">
        Could not load the log.
        <Button variant="secondary" size="sm" onClick={() => void messages.refetch()}>
          Try again
        </Button>
      </div>
    )
  }
  if (messages.data.length === 0) {
    return (
      <p className="m-0 text-sm text-muted-foreground">{active ? 'Waiting for output…' : 'This run left no output.'}</p>
    )
  }
  return (
    <ol aria-label={`Log of run ${number}`} className={cn('m-0 flex max-h-96 flex-col gap-2 overflow-y-auto p-0')}>
      {messages.data.map((message) => (
        <li key={message.seq} className="list-none">
          <Line message={message} />
        </li>
      ))}
      <div ref={end} />
    </ol>
  )
}
