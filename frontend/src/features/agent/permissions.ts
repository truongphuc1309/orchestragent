import type { Role } from '@/features/workspace/schemas'

/**
 * Mirrors the server (AgentPolicy): the person who made an agent, and admins and owners, may edit, archive and
 * restore it. Everyone else can only use it, by assigning issues to it.
 */
export function canManageAgent(role: Role, isCreator: boolean): boolean {
  return isCreator || role === 'owner' || role === 'admin'
}
