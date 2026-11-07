import type { Role } from '@/features/workspace/schemas'

/**
 * Mirrors the server (IssueService.delete): the creator of an issue, and admins and owners, may delete it.
 * Everything else about issues is open to every member.
 */
export function canDeleteIssue(role: Role, isCreator: boolean): boolean {
  return isCreator || role === 'owner' || role === 'admin'
}
