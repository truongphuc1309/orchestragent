import { formatCost, formatTokens } from '@/shared/lib/format'
import type { Totals } from './schemas'

/** The measure the daily chart shows; one at a time, never two scales on one chart. */
export type Metric = 'cost' | 'runs' | 'tokens'

export const METRIC_LABEL: Record<Metric, string> = { cost: 'Cost', runs: 'Runs', tokens: 'Tokens' }

export function metricValue(totals: Totals, metric: Metric): number {
  switch (metric) {
    case 'cost':
      return totals.costUsd
    case 'runs':
      return totals.runs
    case 'tokens':
      return totals.inputTokens + totals.outputTokens
  }
}

export function formatMetric(value: number, metric: Metric): string {
  switch (metric) {
    case 'cost':
      return formatCost(value)
    case 'runs':
      return `${value} ${value === 1 ? 'run' : 'runs'}`
    case 'tokens':
      return `${formatTokens(value)} tokens`
  }
}

/** "8 Nov" for an ISO date, read as a UTC day. */
export function formatDay(isoDay: string): string {
  return new Date(`${isoDay}T00:00:00Z`).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' })
}

/** "7 completed, 1 failed, 1 cancelled": how the runs ended (cancelled ones are the rest); cancelled only when some were. */
export function runsNote(totals: Totals): string {
  const cancelled = totals.runs - totals.completed - totals.failed
  const parts = [`${totals.completed} completed`, `${totals.failed} failed`]
  if (cancelled > 0) {
    parts.push(`${cancelled} cancelled`)
  }
  return parts.join(', ')
}
