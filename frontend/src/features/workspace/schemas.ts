import { z } from 'zod'

export const roleSchema = z.enum(['owner', 'admin', 'member'])
export type Role = z.infer<typeof roleSchema>
export const ROLES: readonly Role[] = ['owner', 'admin', 'member']

export const workspaceSchema = z.object({
  id: z.uuid(),
  name: z.string(),
  slug: z.string(),
  /** The signed-in user's own role in this workspace. */
  role: roleSchema,
  createdAt: z.string(),
})
export type Workspace = z.infer<typeof workspaceSchema>

export const memberSchema = z.object({
  userId: z.uuid(),
  name: z.string(),
  email: z.string(),
  role: roleSchema,
  joinedAt: z.string(),
})
export type Member = z.infer<typeof memberSchema>

export const invitationSchema = z.object({
  id: z.uuid(),
  workspaceId: z.uuid(),
  workspaceName: z.string(),
  email: z.string(),
  role: roleSchema,
  invitedByName: z.string(),
  createdAt: z.string(),
})
export type Invitation = z.infer<typeof invitationSchema>

export const MAX_WORKSPACE_NAME = 60

export const createWorkspaceFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Enter a name for the workspace')
    .max(MAX_WORKSPACE_NAME, `Use at most ${MAX_WORKSPACE_NAME} characters`),
})
export type CreateWorkspaceForm = z.infer<typeof createWorkspaceFormSchema>

export const inviteFormSchema = z.object({
  email: z.string().trim().min(1, 'Enter an email address').pipe(z.email('Enter a valid email address')),
  role: roleSchema,
})
export type InviteForm = z.infer<typeof inviteFormSchema>
