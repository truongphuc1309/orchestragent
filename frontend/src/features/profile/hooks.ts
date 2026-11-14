import { useMutation, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { meQueryKey } from '@/features/auth/hooks'
import type { User } from '@/features/auth/schemas'
import { removeAvatar, updateProfile, uploadAvatar } from './api'
import type { ProfileForm } from './schemas'

/**
 * The new profile replaces the cached user at once (menu, profile page). Workspace data shows names and pictures
 * too (members, issues, comments), so it is fetched again.
 */
function profileChanged(queryClient: QueryClient, user: User) {
  queryClient.setQueryData(meQueryKey, user)
  void queryClient.invalidateQueries({ queryKey: ['workspaces'] })
}

export function useUpdateProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (changes: ProfileForm) => updateProfile(changes),
    onSuccess: (user) => profileChanged(queryClient, user),
  })
}

export function useUploadAvatar() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => uploadAvatar(file),
    onSuccess: (user) => profileChanged(queryClient, user),
  })
}

export function useRemoveAvatar() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: removeAvatar,
    onSuccess: (user) => profileChanged(queryClient, user),
  })
}
