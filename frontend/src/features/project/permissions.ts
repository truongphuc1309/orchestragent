import type { Role } from '@/features/workspace/schemas'

/**
 * Mirrors the server (ProjectPolicy): any member creates projects; the person who made one, and admins and
 * owners, may rename, archive and restore it.
 */
export function canManageProject(role: Role, isCreator: boolean): boolean {
  return isCreator || role === 'owner' || role === 'admin'
}
