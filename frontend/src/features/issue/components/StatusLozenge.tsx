import { Badge } from '@/shared/ui/badge'
import { STATUS_LABEL, type Status } from '../schemas'

const VARIANT = {
  backlog: 'neutral',
  todo: 'neutral',
  cancelled: 'neutral',
  in_progress: 'info',
  in_review: 'discovery',
  done: 'success',
  blocked: 'danger',
} as const

/** The status as a Jira-style lozenge (see "Giao diện (UI)" in PLAN.md for the colours). */
export function StatusLozenge({ status }: { status: Status }) {
  return <Badge variant={VARIANT[status]}>{STATUS_LABEL[status]}</Badge>
}
