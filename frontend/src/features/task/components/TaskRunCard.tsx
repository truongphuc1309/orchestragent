import { useState } from 'react'
import { toast } from 'sonner'
import { messageOf } from '@/shared/api/errors'
import { formatCost, formatDuration, formatTokens } from '@/shared/lib/format'
import { useNow } from '@/shared/lib/useNow'
import { Button } from '@/shared/ui/button'
import { useCancelTask, useRetryTask } from '../hooks'
import { isActive, TRIGGER_LABEL, type Task } from '../schemas'
import { TaskLog } from './TaskLog'
import { TaskStatusLozenge } from './TaskStatusLozenge'

interface Props {
  workspaceId: string
  task: Task
  /** Which run of the issue this is in time order: 1 for the first, 2 for the next, and so on. */
  number: number
  /** Open the log from the start (the newest run, while it is going on). */
  defaultOpen: boolean
  /** Only the newest run offers a retry: an older failure has been superseded. */
  canRetry: boolean
}

/** How long the run has taken (counting up while it runs), or what it is waiting for. */
function progress(task: Task, now: number): string {
  if (task.status === 'queued') {
    return 'Waiting for a computer to pick it up'
  }
  if (task.status === 'dispatched' || task.startedAt === null) {
    return task.status === 'dispatched' ? 'Starting' : '—'
  }
  const end = task.finishedAt === null ? now : new Date(task.finishedAt).getTime()
  return formatDuration(end - new Date(task.startedAt).getTime())
}

function usage(task: Task): string | null {
  const parts: string[] = []
  if (task.inputTokens !== null || task.outputTokens !== null) {
    parts.push(`${formatTokens(task.inputTokens ?? 0)} in / ${formatTokens(task.outputTokens ?? 0)} out tokens`)
  }
  if (task.costUsd !== null) {
    parts.push(formatCost(task.costUsd))
  }
  return parts.length === 0 ? null : parts.join(' · ')
}

/** One run of an agent: its state, how long and how much, the log, and what can be done with it. */
export function TaskRunCard({ workspaceId, task, number, defaultOpen, canRetry }: Props) {
  const [open, setOpen] = useState(defaultOpen)
  const cancel = useCancelTask(workspaceId)
  const retry = useRetryTask(workspaceId)
  const active = isActive(task.status)
  const now = useNow(task.status === 'running')
  const cost = usage(task)

  return (
    <li className="flex list-none flex-col gap-2 rounded-md border bg-card p-3">
      <div className="flex flex-wrap items-center gap-2">
        <TaskStatusLozenge status={task.status} />
        <span className="font-medium">Run {number}</span>
        <span className="text-sm text-muted-foreground">
          {TRIGGER_LABEL[task.trigger]} · {task.agentName}
          {task.runtime && ` on ${task.runtime.deviceName}`}
          {task.attempt > 1 && ` · attempt ${task.attempt}`}
        </span>
        <span className="flex-1" />
        {active && (
          <Button
            variant="secondary"
            aria-label={`Cancel run ${number}`}
            disabled={cancel.isPending}
            onClick={() =>
              cancel.mutate(task.id, { onError: (error) => toast.error(messageOf(error, 'Could not cancel the run.')) })
            }
          >
            Cancel
          </Button>
        )}
        {task.status === 'failed' && canRetry && (
          <Button
            variant="secondary"
            aria-label={`Retry run ${number}`}
            disabled={retry.isPending}
            onClick={() =>
              retry.mutate(task.id, {
                onSuccess: () => toast.success('The run is queued again'),
                onError: (error) => toast.error(messageOf(error, 'Could not retry the run.')),
              })
            }
          >
            Retry
          </Button>
        )}
      </div>

      <p className="m-0 text-sm text-muted-foreground">
        {progress(task, now)}
        {cost && ` · ${cost}`}
      </p>

      {task.status === 'failed' && task.error && (
        <p role="note" className="m-0 text-sm whitespace-pre-wrap break-words text-destructive">
          {task.error}
        </p>
      )}

      <div>
        <Button variant="ghost" size="sm" aria-expanded={open} onClick={() => setOpen((shown) => !shown)}>
          {open ? 'Hide log' : 'Show log'}
        </Button>
      </div>
      {open && <TaskLog workspaceId={workspaceId} task={task} number={number} />}
    </li>
  )
}
