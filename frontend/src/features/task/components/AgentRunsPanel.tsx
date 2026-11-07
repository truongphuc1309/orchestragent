import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { useTasks } from '../hooks'
import { isActive } from '../schemas'
import { TaskRunCard } from './TaskRunCard'

/**
 * "Agent runs" on the issue page: every run of an agent on this issue, newest first, with its log. It shows
 * nothing for an issue no agent has worked on.
 */
export function AgentRunsPanel({ workspaceId, issueId }: { workspaceId: string; issueId: string }) {
  const tasks = useTasks(workspaceId, issueId)

  if (tasks.isPending) {
    return (
      <div aria-busy="true">
        <Skeleton className="h-16 w-full" />
        <span className="sr-only">Loading the agent runs…</span>
      </div>
    )
  }
  if (tasks.isError) {
    return (
      <div role="alert" className="flex items-center gap-3 text-sm text-destructive">
        Could not load the agent runs.
        <Button variant="secondary" size="sm" onClick={() => void tasks.refetch()}>
          Try again
        </Button>
      </div>
    )
  }
  if (tasks.data.length === 0) {
    return null
  }
  return (
    <section aria-labelledby="runs-heading" className="flex flex-col gap-3">
      <h2 id="runs-heading" className="text-base font-semibold">
        Agent runs
      </h2>
      <ul aria-label="Agent runs" className="m-0 flex flex-col gap-2 p-0">
        {tasks.data.map((task, index) => (
          <TaskRunCard
            key={task.id}
            workspaceId={workspaceId}
            task={task}
            // The list is newest first; the oldest run is run 1.
            number={tasks.data.length - index}
            defaultOpen={index === 0 && isActive(task.status)}
            canRetry={index === 0}
          />
        ))}
      </ul>
    </section>
  )
}
