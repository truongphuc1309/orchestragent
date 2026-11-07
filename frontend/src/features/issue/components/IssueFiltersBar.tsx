import type { ReactNode } from 'react'
import { ChevronDown } from 'lucide-react'
import type { Member } from '@/features/workspace/schemas'
import { Button } from '@/shared/ui/button'
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'
import { hasFilters, type IssueFilters } from '../filters'
import { PRIORITIES, PRIORITY_LABEL, STATUSES, STATUS_LABEL } from '../schemas'

function toggle<T>(list: readonly T[], item: T): T[] {
  return list.includes(item) ? list.filter((candidate) => candidate !== item) : [...list, item]
}

function FilterMenu({ label, count, children }: { label: string; count: number; children: ReactNode }) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="secondary" aria-label={count > 0 ? `${label}: ${count} selected` : label}>
          {label}
          {count > 0 && <span className="rounded-sm bg-selected px-1 text-xs text-selected-foreground">{count}</span>}
          <ChevronDown />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">{children}</DropdownMenuContent>
    </DropdownMenu>
  )
}

interface Props {
  filters: IssueFilters
  onChange: (filters: IssueFilters) => void
  members: readonly Member[]
}

/** Status, priority and assignee filters. The choice is kept per workspace by the caller. */
export function IssueFiltersBar({ filters, onChange, members }: Props) {
  return (
    <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Filters">
      <FilterMenu label="Status" count={filters.statuses.length}>
        {STATUSES.map((status) => (
          <DropdownMenuCheckboxItem
            key={status}
            checked={filters.statuses.includes(status)}
            onSelect={(event) => event.preventDefault()}
            onCheckedChange={() => onChange({ ...filters, statuses: toggle(filters.statuses, status) })}
          >
            {STATUS_LABEL[status]}
          </DropdownMenuCheckboxItem>
        ))}
      </FilterMenu>
      <FilterMenu label="Priority" count={filters.priorities.length}>
        {PRIORITIES.map((priority) => (
          <DropdownMenuCheckboxItem
            key={priority}
            checked={filters.priorities.includes(priority)}
            onSelect={(event) => event.preventDefault()}
            onCheckedChange={() => onChange({ ...filters, priorities: toggle(filters.priorities, priority) })}
          >
            {PRIORITY_LABEL[priority]}
          </DropdownMenuCheckboxItem>
        ))}
      </FilterMenu>
      <FilterMenu label="Assignee" count={filters.assignees.length + (filters.unassigned ? 1 : 0)}>
        <DropdownMenuCheckboxItem
          checked={filters.unassigned}
          onSelect={(event) => event.preventDefault()}
          onCheckedChange={() => onChange({ ...filters, unassigned: !filters.unassigned })}
        >
          Unassigned
        </DropdownMenuCheckboxItem>
        {members.map((member) => (
          <DropdownMenuCheckboxItem
            key={member.userId}
            checked={filters.assignees.includes(member.userId)}
            onSelect={(event) => event.preventDefault()}
            onCheckedChange={() => onChange({ ...filters, assignees: toggle(filters.assignees, member.userId) })}
          >
            {member.name}
          </DropdownMenuCheckboxItem>
        ))}
      </FilterMenu>
      {hasFilters(filters) && (
        <Button
          variant="ghost"
          onClick={() => onChange({ statuses: [], priorities: [], assignees: [], unassigned: false })}
        >
          Clear filters
        </Button>
      )}
    </div>
  )
}
