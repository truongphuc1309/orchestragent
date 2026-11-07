import type { Member } from '@/features/workspace/schemas'
import { Sparkles } from 'lucide-react'
import type { Agent } from '@/features/agent/schemas'
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { decodeAssignee, encodeAssignee, UNASSIGNED } from '../assignee'
import {
  PRIORITIES,
  PRIORITY_LABEL,
  prioritySchema,
  STATUSES,
  STATUS_LABEL,
  statusSchema,
  type AssigneeRef,
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

/**
 * Picks who an issue goes to: nobody, a member or an agent. Agents are marked with the purple spark so they
 * are told apart from people at a glance. {@code value} is null for unassigned.
 */
export function AssigneeSelect({
  value,
  onChange,
  label,
  members,
  agents,
  disabled,
  className,
  currentName,
}: FieldProps<AssigneeRef | null> & {
  members: readonly Member[]
  /** Active agents; archived ones cannot be given work, so they are not offered. */
  agents: readonly Agent[]
  /** Name of the current assignee as the issue reports it; shown while the lists are still loading. */
  currentName?: string
}) {
  const shown = (() => {
    if (value === null) {
      return 'Unassigned'
    }
    const known =
      value.type === 'agent'
        ? agents.find((agent) => agent.id === value.id)?.name
        : members.find((member) => member.userId === value.id)?.name
    return known ?? currentName ?? (value.type === 'agent' ? 'Unknown agent' : 'Unknown user')
  })()

  return (
    <Select
      value={encodeAssignee(value)}
      disabled={disabled}
      onValueChange={(next) => onChange(decodeAssignee(next))}
    >
      <SelectTrigger aria-label={label} className={className}>
        <SelectValue>
          {value?.type === 'agent' && <Sparkles className="text-lozenge-discovery-foreground" aria-hidden />}
          {shown}
        </SelectValue>
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={UNASSIGNED}>Unassigned</SelectItem>
        <SelectGroup>
          <SelectLabel>Members</SelectLabel>
          {members.map((member) => (
            <SelectItem key={member.userId} value={encodeAssignee({ type: 'member', id: member.userId })}>
              {member.name}
            </SelectItem>
          ))}
        </SelectGroup>
        {agents.length > 0 && (
          <SelectGroup>
            <SelectLabel>Agents</SelectLabel>
            {agents.map((agent) => (
              <SelectItem key={agent.id} value={encodeAssignee({ type: 'agent', id: agent.id })}>
                <Sparkles className="text-lozenge-discovery-foreground" aria-hidden />
                {agent.name}
              </SelectItem>
            ))}
          </SelectGroup>
        )}
      </SelectContent>
    </Select>
  )
}
