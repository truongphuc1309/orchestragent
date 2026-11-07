import { Badge } from '@/shared/ui/badge'
import { STATUS_LABEL, type TaskStatus } from '../schemas'

const VARIANT = {
  queued: 'neutral',
  dispatched: 'info',
  running: 'info',
  completed: 'success',
  failed: 'danger',
  cancelled: 'neutral',
} as const

export function TaskStatusLozenge({ status }: { status: TaskStatus }) {
  return <Badge variant={VARIANT[status]}>{STATUS_LABEL[status]}</Badge>
}
