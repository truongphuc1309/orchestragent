import type { Member } from '@/features/workspace/schemas'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import {
  PRIORITIES,
  PRIORITY_LABEL,
  prioritySchema,
  STATUSES,
  STATUS_LABEL,
  statusSchema,
  type Priority,
  type Status,
} from '../schemas'
import { PriorityIcon } from './PriorityIcon'
import { StatusLozenge } from './StatusLozenge'

interface FieldProps<T> {
  value: T
  onChange: (value: T) => void
  label: string
  disabled?: boolean
  className?: string
}

export function StatusSelect({ value, onChange, label, disabled, className }: FieldProps<Status>) {
  return (
    <Select
      value={value}
      disabled={disabled}
      onValueChange={(next) => {
        const parsed = statusSchema.safeParse(next)
        if (parsed.success) {
          onChange(parsed.data)
        }
      }}
    >
      <SelectTrigger aria-label={label} className={className}>
        <SelectValue>
          <StatusLozenge status={value} />
        </SelectValue>
      </SelectTrigger>
      <SelectContent>
        {STATUSES.map((status) => (
          <SelectItem key={status} value={status}>
            {STATUS_LABEL[status]}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

export function PrioritySelect({ value, onChange, label, disabled, className }: FieldProps<Priority>) {
  return (
    <Select
      value={value}
      disabled={disabled}
      onValueChange={(next) => {
        const parsed = prioritySchema.safeParse(next)
        if (parsed.success) {
          onChange(parsed.data)
        }
      }}
    >
      <SelectTrigger aria-label={label} className={className}>
        <SelectValue>
          <PriorityIcon priority={value} decorative />
          {PRIORITY_LABEL[value]}
        </SelectValue>
      </SelectTrigger>
      <SelectContent>
        {PRIORITIES.map((priority) => (
          <SelectItem key={priority} value={priority}>
            <PriorityIcon priority={priority} decorative />
            {PRIORITY_LABEL[priority]}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

/** Select value for "nobody": a member's ID is a UUID, so it can never collide with this. */
const NOBODY = 'unassigned'

/** Picks a member or nobody. {@code value} is a user ID, or null for unassigned. */
export function AssigneeSelect({
  value,
  onChange,
  label,
  members,
  disabled,
  className,
  currentName,
}: FieldProps<string | null> & {
  members: readonly Member[]
  /** Name of the current assignee as the issue reports it; shown while the member list is still loading. */
  currentName?: string
}) {
  const selected = members.find((member) => member.userId === value)
  return (
    <Select
      value={value ?? NOBODY}
      disabled={disabled}
      onValueChange={(next) => onChange(next === NOBODY ? null : next)}
    >
      <SelectTrigger aria-label={label} className={className}>
        <SelectValue>{value === null ? 'Unassigned' : (selected?.name ?? currentName ?? 'Unknown user')}</SelectValue>
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={NOBODY}>Unassigned</SelectItem>
        {members.map((member) => (
          <SelectItem key={member.userId} value={member.userId}>
            {member.name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
