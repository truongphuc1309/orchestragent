import { z } from 'zod'

export const runtimeStatusSchema = z.enum(['online', 'offline'])
export type RuntimeStatus = z.infer<typeof runtimeStatusSchema>

/** One AI tool on one computer that has registered with the workspace. */
export const runtimeSchema = z.object({
  id: z.uuid(),
  ownerId: z.uuid(),
  ownerName: z.string(),
  deviceName: z.string(),
  provider: z.string(),
  providerVersion: z.string(),
  status: runtimeStatusSchema,
  lastHeartbeatAt: z.string(),
})
export type Runtime = z.infer<typeof runtimeSchema>

/** The part of a runtime an agent shows. */
export const runtimeSummarySchema = z.object({
  id: z.uuid(),
  deviceName: z.string(),
  provider: z.string(),
  status: runtimeStatusSchema,
})
export type RuntimeSummary = z.infer<typeof runtimeSummarySchema>

export const agentSchema = z.object({
  id: z.uuid(),
  name: z.string(),
  instructions: z.string(),
  model: z.string().nullable(),
  runtime: runtimeSummarySchema.nullable(),
  createdById: z.uuid(),
  createdByName: z.string(),
  archived: z.boolean(),
  archivedAt: z.string().nullable(),
  createdAt: z.string(),
})
export type Agent = z.infer<typeof agentSchema>

export const MAX_AGENT_NAME = 60
export const MAX_INSTRUCTIONS = 20_000
export const MAX_MODEL = 100

export const agentFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Enter a name for the agent')
    .max(MAX_AGENT_NAME, `Use at most ${MAX_AGENT_NAME} characters`),
  instructions: z.string().max(MAX_INSTRUCTIONS, `Use at most ${MAX_INSTRUCTIONS} characters`),
  runtimeId: z.string().min(1, 'Choose where the agent runs'),
  model: z.string().trim().max(MAX_MODEL, `Use at most ${MAX_MODEL} characters`),
})
export type AgentForm = z.infer<typeof agentFormSchema>
