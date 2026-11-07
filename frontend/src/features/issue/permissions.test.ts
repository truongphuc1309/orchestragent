import { describe, expect, it } from 'vitest'
import type { Role } from '@/features/workspace/schemas'
import { canDeleteIssue } from './permissions'

/** The same table as the server's IssueServiceTest, written out by hand. */
describe('canDeleteIssue', () => {
  it.each<[Role, boolean, boolean]>([
    ['owner', true, true],
    ['owner', false, true],
    ['admin', true, true],
    ['admin', false, true],
    ['member', true, true],
    ['member', false, false],
  ])('%s, creator=%s -> %s', (role, isCreator, expected) => {
    expect(canDeleteIssue(role, isCreator)).toBe(expected)
  })
})
