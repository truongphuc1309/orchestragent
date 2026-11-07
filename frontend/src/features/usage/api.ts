import { apiRequest } from '@/shared/api/client'
import { usageSchema, type Usage } from './schemas'

/** What the agents of the workspace cost over the last {@code days} days (UTC), today included. */
export function getUsage(workspaceId: string, days: number, signal?: AbortSignal): Promise<Usage> {
  return apiRequest(`/api/workspaces/${workspaceId}/usage?days=${days}`, { schema: usageSchema, signal })
}
