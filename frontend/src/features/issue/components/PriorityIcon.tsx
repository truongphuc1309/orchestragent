import { ChevronDown, ChevronUp, ChevronsUp, Equal, Minus, type LucideIcon } from 'lucide-react'
import { cn } from '@/shared/lib/utils'
import { PRIORITY_LABEL, type Priority } from '../schemas'

const ICON: Record<Priority, { icon: LucideIcon; color: string }> = {
  urgent: { icon: ChevronsUp, color: 'text-priority-urgent' },
  high: { icon: ChevronUp, color: 'text-priority-high' },
  medium: { icon: Equal, color: 'text-priority-medium' },
  low: { icon: ChevronDown, color: 'text-priority-low' },
  none: { icon: Minus, color: 'text-priority-none' },
}

/** {@code decorative} hides the icon from screen readers, for places where the priority is also written out. */
export function PriorityIcon({ priority, className, decorative }: { priority: Priority; className?: string; decorative?: boolean }) {
  const { icon: Icon, color } = ICON[priority]
  const classes = cn('size-4 shrink-0', color, className)
  return decorative ? (
    <Icon aria-hidden className={classes} />
  ) : (
    <Icon role="img" aria-label={`Priority: ${PRIORITY_LABEL[priority]}`} className={classes} />
  )
}
