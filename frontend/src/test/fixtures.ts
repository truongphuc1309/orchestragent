import type { Agent, Runtime } from '@/features/agent/schemas'
import type { Comment, Issue } from '@/features/issue/schemas'
import type { Invitation, Member, Workspace } from '@/features/workspace/schemas'

/** Sample API data shared by the tests. IDs are fixed so assertions stay readable. */
export const ADA = { id: '0198f1c0-0000-7000-8000-000000000001', email: 'ada@example.com', name: 'ada' }

export const ACME: Workspace = {
  id: '0198f1c0-0000-7000-8000-0000000000a1',
  name: 'Acme',
  slug: 'acme',
  role: 'owner',
  createdAt: '2026-01-01T10:00:00Z',
}

export const GLOBEX: Workspace = {
  id: '0198f1c0-0000-7000-8000-0000000000a2',
  name: 'Globex',
  slug: 'globex',
  role: 'member',
  createdAt: '2026-01-02T10:00:00Z',
}

export function member(overrides: Partial<Member> & Pick<Member, 'userId' | 'name'>): Member {
  return {
    email: `${overrides.name.toLowerCase()}@example.com`,
    role: 'member',
    joinedAt: '2026-01-01T10:00:00Z',
    ...overrides,
  }
}

export function invitation(overrides: Partial<Invitation> & Pick<Invitation, 'id'>): Invitation {
  return {
    workspaceId: ACME.id,
    workspaceName: ACME.name,
    email: 'ivy@example.com',
    role: 'member',
    invitedByName: 'ada',
    createdAt: '2026-01-03T10:00:00Z',
    ...overrides,
  }
}

export const MIA = { userId: '0198f1c0-0000-7000-8000-000000000002', name: 'Mia' }

export function issue(overrides: Partial<Issue> & Pick<Issue, 'id' | 'number' | 'title'>): Issue {
  return {
    description: '',
    status: 'todo',
    priority: 'none',
    assignee: null,
    creator: { type: 'member', id: ADA.id, name: ADA.name },
    position: 1024,
    createdAt: '2026-01-04T10:00:00Z',
    updatedAt: '2026-01-04T10:00:00Z',
    ...overrides,
  }
}

export function issueId(n: number): string {
  return `0198f1c0-0000-7000-8000-0000000001${String(n).padStart(2, '0')}`
}

export function comment(overrides: Partial<Comment> & Pick<Comment, 'id' | 'issueId' | 'body'>): Comment {
  return {
    author: { type: 'member', id: ADA.id, name: ADA.name },
    createdAt: '2026-01-05T10:00:00Z',
    ...overrides,
  }
}

export function runtimeId(n: number): string {
  return `0198f1c0-0000-7000-8000-0000000003${String(n).padStart(2, '0')}`
}

export function agentId(n: number): string {
  return `0198f1c0-0000-7000-8000-0000000004${String(n).padStart(2, '0')}`
}

export function runtime(overrides: Partial<Runtime> & Pick<Runtime, 'id'>): Runtime {
  return {
    ownerId: ADA.id,
    ownerName: ADA.name,
    deviceName: "Ada's laptop",
    provider: 'claude',
    providerVersion: '1.0.0',
    status: 'online',
    lastHeartbeatAt: new Date().toISOString(),
    ...overrides,
  }
}

export function agent(overrides: Partial<Agent> & Pick<Agent, 'id' | 'name'>): Agent {
  return {
    instructions: 'Write clean code.',
    model: null,
    runtime: { id: runtimeId(1), deviceName: "Ada's laptop", provider: 'claude', status: 'online' },
    createdById: ADA.id,
    createdByName: ADA.name,
    archived: false,
    archivedAt: null,
    createdAt: '2026-01-06T10:00:00Z',
    ...overrides,
  }
}
