import type { ReactNode } from 'react'
import { ChevronDown, Sparkles } from 'lucide-react'
import type { Agent } from '@/features/agent/schemas'
import type { Project } from '@/features/project/schemas'
import type { Member } from '@/features/workspace/schemas'
import { Button } from '@/shared/ui/button'
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'
import { hasFilters, NO_FILTERS, type IssueFilters } from '../filters'
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
  agents: readonly Agent[]
  /** The projects to filter by; without them (a project page) there is no project filter. */
  projects?: readonly Project[]
}

/** Project, status, priority and assignee filters. The choice is kept per workspace by the caller. */
export function IssueFiltersBar({ filters, onChange, members, agents, projects }: Props) {
  return (
    <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Filters">
      {projects && (
        <FilterMenu label="Project" count={filters.projects.length}>
          {projects.length === 0 && <p className="px-2 py-1.5 text-sm text-muted-foreground">No projects yet</p>}
          {projects.map((project) => (
            <DropdownMenuCheckboxItem
              key={project.id}
              checked={filters.projects.includes(project.id)}
              onSelect={(event) => event.preventDefault()}
              onCheckedChange={() => onChange({ ...filters, projects: toggle(filters.projects, project.id) })}
            >
              <span className="text-muted-foreground">{project.key}</span>
              {project.name}
              {project.archived && <span className="text-muted-foreground">(archived)</span>}
            </DropdownMenuCheckboxItem>
          ))}
        </FilterMenu>
      )}
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
        {agents.length > 0 && <DropdownMenuSeparator />}
        {agents.map((agent) => (
          <DropdownMenuCheckboxItem
            key={agent.id}
            checked={filters.assignees.includes(agent.id)}
            onSelect={(event) => event.preventDefault()}
            onCheckedChange={() => onChange({ ...filters, assignees: toggle(filters.assignees, agent.id) })}
          >
            <Sparkles className="text-lozenge-discovery-foreground" aria-hidden />
            {agent.name}
          </DropdownMenuCheckboxItem>
        ))}
      </FilterMenu>
      {hasFilters(filters) && (
        <Button variant="ghost" onClick={() => onChange(NO_FILTERS)}>
          Clear filters
        </Button>
      )}
    </div>
  )
}
