import { z } from 'zod'
import { apiRequest } from '@/shared/api/client'
import { apiTokenSchema, createdApiTokenSchema, type ApiToken, type CreatedApiToken } from './schemas'

export function listTokens(signal?: AbortSignal): Promise<ApiToken[]> {
  return apiRequest('/api/me/tokens', { schema: z.array(apiTokenSchema), signal })
}

export function createToken(name: string): Promise<CreatedApiToken> {
  return apiRequest('/api/me/tokens', { method: 'POST', body: { name }, schema: createdApiTokenSchema })
}

export function revokeToken(tokenId: string): Promise<void> {
  return apiRequest(`/api/me/tokens/${tokenId}`, { method: 'DELETE', schema: z.undefined() })
}
