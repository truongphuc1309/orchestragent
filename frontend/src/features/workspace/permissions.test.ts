import { describe, expect, it } from 'vitest'
import { canChangeRole, canInvite, canManageInvitations, canRemove } from './permissions'
import { ROLES, type Role } from './schemas'

/**
 * Same hand-written truth tables as the backend's MemberPolicyTest, so the buttons the UI shows can never
 * drift from what the server accepts.
 */
describe('permissions', () => {
  it.each<[Role, Role, boolean]>([
    ['owner', 'owner', true],
    ['owner', 'admin', true],
    ['owner', 'member', true],
    ['admin', 'owner', false],
    ['admin', 'admin', true],
    ['admin', 'member', true],
    ['member', 'owner', false],
    ['member', 'admin', false],
    ['member', 'member', false],
  ])('%s inviting as %s -> %s', (actor, invitedRole, allowed) => {
    expect(canInvite(actor, invitedRole)).toBe(allowed)
  })

  it.each<[Role, Role, Role, boolean]>([
    ['owner', 'owner', 'owner', true],
    ['owner', 'owner', 'admin', true],
    ['owner', 'owner', 'member', true],
    ['owner', 'admin', 'owner', true],
    ['owner', 'admin', 'admin', true],
    ['owner', 'admin', 'member', true],
    ['owner', 'member', 'owner', true],
    ['owner', 'member', 'admin', true],
    ['owner', 'member', 'member', true],
    ['admin', 'owner', 'owner', false],
    ['admin', 'owner', 'admin', false],
    ['admin', 'owner', 'member', false],
    ['admin', 'admin', 'owner', false],
    ['admin', 'admin', 'admin', false],
    ['admin', 'admin', 'member', false],
    ['admin', 'member', 'owner', false],
    ['admin', 'member', 'admin', true],
    ['admin', 'member', 'member', true],
    ['member', 'owner', 'member', false],
    ['member', 'admin', 'member', false],
    ['member', 'member', 'admin', false],
    ['member', 'member', 'owner', false],
  ])('%s changing %s to %s -> %s', (actor, targetCurrent, newRole, allowed) => {
    expect(canChangeRole(actor, targetCurrent, newRole)).toBe(allowed)
  })

  it.each<[Role, Role, boolean]>([
    ['owner', 'owner', true],
    ['owner', 'admin', true],
    ['owner', 'member', true],
    ['admin', 'owner', false],
    ['admin', 'admin', false],
    ['admin', 'member', true],
    ['member', 'owner', false],
    ['member', 'admin', false],
    ['member', 'member', false],
  ])('%s removing %s -> %s', (actor, target, allowed) => {
    expect(canRemove(actor, target, false)).toBe(allowed)
  })

  it('everyone may leave', () => {
    for (const role of ROLES) {
      expect(canRemove(role, role, true)).toBe(true)
    }
  })

  it('only admins and owners manage invitations', () => {
    expect(canManageInvitations('owner')).toBe(true)
    expect(canManageInvitations('admin')).toBe(true)
    expect(canManageInvitations('member')).toBe(false)
  })
})
