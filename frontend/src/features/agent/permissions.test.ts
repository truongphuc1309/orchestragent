import { describe, expect, it } from 'vitest'
import type { Role } from '@/features/workspace/schemas'
import { canManageAgent } from './permissions'

/** The same table as the server's AgentPolicyTest and AgentServiceTest, written out by hand. */
describe('canManageAgent', () => {
  it.each<[Role, boolean, boolean]>([
    ['owner', true, true],
    ['owner', false, true],
    ['admin', true, true],
    ['admin', false, true],
    ['member', true, true],
    ['member', false, false],
  ])('%s, creator=%s -> %s', (role, isCreator, expected) => {
    expect(canManageAgent(role, isCreator)).toBe(expected)
  })
})
