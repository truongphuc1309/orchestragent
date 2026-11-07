import { z } from 'zod'
import { apiRequest } from '@/shared/api/client'
import {
  invitationSchema,
  memberSchema,
  workspaceSchema,
  type Invitation,
  type Member,
  type Role,
  type Workspace,
} from './schemas'

const workspacesPath = '/api/workspaces'

export function listWorkspaces(signal?: AbortSignal): Promise<Workspace[]> {
  return apiRequest(workspacesPath, { schema: z.array(workspaceSchema), signal })
}

export function createWorkspace(name: string): Promise<Workspace> {
  return apiRequest(workspacesPath, { method: 'POST', body: { name }, schema: workspaceSchema })
}

export function listMembers(workspaceId: string, signal?: AbortSignal): Promise<Member[]> {
  return apiRequest(`${workspacesPath}/${workspaceId}/members`, { schema: z.array(memberSchema), signal })
}

export function changeMemberRole(workspaceId: string, userId: string, role: Role): Promise<void> {
  return apiRequest(`${workspacesPath}/${workspaceId}/members/${userId}`, {
    method: 'PATCH',
    body: { role },
    schema: z.undefined(),
  })
}

export function removeMember(workspaceId: string, userId: string): Promise<void> {
  return apiRequest(`${workspacesPath}/${workspaceId}/members/${userId}`, { method: 'DELETE', schema: z.undefined() })
}

export function listInvitations(workspaceId: string, signal?: AbortSignal): Promise<Invitation[]> {
  return apiRequest(`${workspacesPath}/${workspaceId}/invitations`, { schema: z.array(invitationSchema), signal })
}

export function inviteMember(workspaceId: string, email: string, role: Role): Promise<Invitation> {
  return apiRequest(`${workspacesPath}/${workspaceId}/invitations`, {
    method: 'POST',
    body: { email, role },
    schema: invitationSchema,
  })
}

export function revokeInvitation(workspaceId: string, invitationId: string): Promise<void> {
  return apiRequest(`${workspacesPath}/${workspaceId}/invitations/${invitationId}`, {
    method: 'DELETE',
    schema: z.undefined(),
  })
}

/** Invitations addressed to the signed-in user's email. */
export function listMyInvitations(signal?: AbortSignal): Promise<Invitation[]> {
  return apiRequest('/api/invitations', { schema: z.array(invitationSchema), signal })
}

export function acceptInvitation(invitationId: string): Promise<Workspace> {
  return apiRequest(`/api/invitations/${invitationId}/accept`, { method: 'POST', schema: workspaceSchema })
}
