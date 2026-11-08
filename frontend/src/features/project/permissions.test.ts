import { describe, expect, it } from 'vitest'
import type { Role } from '@/features/workspace/schemas'
import { canManageProject } from './permissions'

/** The same table as the server's ProjectTest (ProjectPolicy) and ProjectServiceTest, written out by hand. */
describe('canManageProject', () => {
  it.each<[Role, boolean, boolean]>([
    ['owner', true, true],
    ['owner', false, true],
    ['admin', true, true],
    ['admin', false, true],
    ['member', true, true],
    ['member', false, false],
  ])('%s, creator=%s -> %s', (role, isCreator, expected) => {
    expect(canManageProject(role, isCreator)).toBe(expected)
  })
})
