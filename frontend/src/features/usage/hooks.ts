import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { getUsage } from './api'

/** Every key contains the workspace ID, so two workspaces never share usage numbers in the cache. */
export const usageKeys = {
  all: (workspaceId: string) => ['workspaces', workspaceId, 'usage'] as const,
  period: (workspaceId: string, days: number) => [...usageKeys.all(workspaceId), days] as const,
}

/** Usage of a period; switching periods keeps the old numbers on screen until the new ones arrive. */
export function useUsage(workspaceId: string, days: number) {
  return useQuery({
    queryKey: usageKeys.period(workspaceId, days),
    queryFn: ({ signal }) => getUsage(workspaceId, days, signal),
    placeholderData: keepPreviousData,
  })
}
