import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchMe, logout, requestLoginCode, verifyLoginCode } from './api'

export const meQueryKey = ['me'] as const

/** `data` is the user, `null` when signed out, `undefined` while loading. */
export function useMe() {
  return useQuery({
    queryKey: meQueryKey,
    queryFn: ({ signal }) => fetchMe(signal),
    staleTime: 5 * 60 * 1000,
  })
}

export function useRequestLoginCode() {
  return useMutation({ mutationFn: (email: string) => requestLoginCode(email) })
}

export function useVerifyLoginCode() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ email, code }: { email: string; code: string }) => verifyLoginCode(email, code),
    onSuccess: (user) => queryClient.setQueryData(meQueryKey, user),
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      // Nothing cached may outlive the session: it could belong to another user's data.
      queryClient.clear()
      queryClient.setQueryData(meQueryKey, null)
    },
  })
}
