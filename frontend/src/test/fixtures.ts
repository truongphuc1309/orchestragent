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
