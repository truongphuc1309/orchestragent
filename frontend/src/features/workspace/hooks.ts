import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  acceptInvitation,
  changeMemberRole,
  createWorkspace,
  inviteMember,
  listInvitations,
  listMembers,
  listMyInvitations,
  listWorkspaces,
  removeMember,
  revokeInvitation,
} from './api'
import type { Role } from './schemas'

/** Every workspace-scoped query key contains the workspace ID, so data of two workspaces never mixes. */
export const queryKeys = {
  workspaces: ['workspaces'] as const,
  members: (workspaceId: string) => ['workspaces', workspaceId, 'members'] as const,
  invitations: (workspaceId: string) => ['workspaces', workspaceId, 'invitations'] as const,
  myInvitations: ['invitations', 'mine'] as const,
}

export function useWorkspaces() {
  return useQuery({ queryKey: queryKeys.workspaces, queryFn: ({ signal }) => listWorkspaces(signal) })
}

export function useCreateWorkspace() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => createWorkspace(name),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.workspaces }),
  })
}

export function useMembers(workspaceId: string) {
  return useQuery({ queryKey: queryKeys.members(workspaceId), queryFn: ({ signal }) => listMembers(workspaceId, signal) })
}

export function useChangeMemberRole(workspaceId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: Role }) => changeMemberRole(workspaceId, userId, role),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.members(workspaceId) }),
  })
}

export function useRemoveMember(workspaceId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => removeMember(workspaceId, userId),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.members(workspaceId) }),
        // Someone who just left is no longer a member of anything here.
        queryClient.invalidateQueries({ queryKey: queryKeys.workspaces }),
      ]),
  })
}

export function useInvitations(workspaceId: string, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.invitations(workspaceId),
    queryFn: ({ signal }) => listInvitations(workspaceId, signal),
    enabled,
  })
}

export function useInviteMember(workspaceId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ email, role }: { email: string; role: Role }) => inviteMember(workspaceId, email, role),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.invitations(workspaceId) }),
  })
}

export function useRevokeInvitation(workspaceId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (invitationId: string) => revokeInvitation(workspaceId, invitationId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.invitations(workspaceId) }),
  })
}

export function useMyInvitations() {
  return useQuery({ queryKey: queryKeys.myInvitations, queryFn: ({ signal }) => listMyInvitations(signal) })
}

export function useAcceptInvitation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (invitationId: string) => acceptInvitation(invitationId),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.workspaces }),
        queryClient.invalidateQueries({ queryKey: queryKeys.myInvitations }),
      ]),
  })
}
