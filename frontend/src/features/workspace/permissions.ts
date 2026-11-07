import type { Role } from './schemas'

/**
 * Mirrors the rules of the backend (MemberPolicy) so the UI only offers actions that will be accepted. The
 * server enforces the same rules; this file only decides what to show.
 *
 * One rule underlies everything: an owner can manage anyone, an admin can manage members only, and a member
 * cannot manage anyone (except leaving).
 */
const RANK: Record<Role, number> = { owner: 0, admin: 1, member: 2 }

const atLeast = (role: Role, other: Role) => RANK[role] <= RANK[other]
const outranks = (role: Role, other: Role) => RANK[role] < RANK[other]

export function canInvite(actor: Role, invitedRole: Role): boolean {
  return atLeast(actor, 'admin') && (actor === 'owner' || invitedRole !== 'owner')
}

export function canChangeRole(actor: Role, targetCurrent: Role, newRole: Role): boolean {
  if (!atLeast(actor, 'admin')) {
    return false
  }
  return actor === 'owner' || (outranks(actor, targetCurrent) && newRole !== 'owner')
}

export function canRemove(actor: Role, target: Role, removingSelf: boolean): boolean {
  if (removingSelf) {
    return true
  }
  return atLeast(actor, 'admin') && (actor === 'owner' || outranks(actor, target))
}

export function canManageInvitations(actor: Role): boolean {
  return atLeast(actor, 'admin')
}

export const ROLE_LABEL: Record<Role, string> = { owner: 'Owner', admin: 'Admin', member: 'Member' }
