import { z } from 'zod'

/** Runs (and how they ended), tokens and dollars. The cost arrives as a JSON number. */
export const totalsSchema = z.object({
  runs: z.number().int().nonnegative(),
  completed: z.number().int().nonnegative(),
  failed: z.number().int().nonnegative(),
  inputTokens: z.number().int().nonnegative(),
  outputTokens: z.number().int().nonnegative(),
  costUsd: z.number().nonnegative(),
})
export type Totals = z.infer<typeof totalsSchema>

export const usageSchema = z.object({
  firstDay: z.string(),
  lastDay: z.string(),
  totals: totalsSchema,
  byAgent: z.array(z.object({ agentId: z.uuid(), agentName: z.string(), archived: z.boolean(), totals: totalsSchema })),
  /** The most expensive issues. {@code number} and {@code title} are null for an issue deleted since. */
  topIssues: z.array(
    z.object({ issueId: z.uuid(), number: z.number().int().nullable(), title: z.string().nullable(), totals: totalsSchema }),
  ),
  /** Every day of the period (UTC), oldest first, with zeros on quiet days. */
  byDay: z.array(z.object({ day: z.string(), totals: totalsSchema })),
})
export type Usage = z.infer<typeof usageSchema>

export const PERIODS = [7, 30, 90] as const
export type Period = (typeof PERIODS)[number]
