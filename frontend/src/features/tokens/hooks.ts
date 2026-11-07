import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createToken, listTokens, revokeToken } from './api'

const tokensKey = ['tokens'] as const

export function useTokens() {
  return useQuery({ queryKey: tokensKey, queryFn: ({ signal }) => listTokens(signal) })
}

export function useCreateToken() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => createToken(name),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: tokensKey }),
  })
}

export function useRevokeToken() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (tokenId: string) => revokeToken(tokenId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: tokensKey }),
  })
}
