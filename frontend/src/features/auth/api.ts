import { z } from 'zod'
import { apiRequest } from '@/shared/api/client'
import { ApiError } from '@/shared/api/errors'
import { userSchema, type User } from './schemas'

export function requestLoginCode(email: string): Promise<void> {
  return apiRequest('/api/auth/code', { method: 'POST', body: { email }, schema: z.undefined() })
}

export function verifyLoginCode(email: string, code: string): Promise<User> {
  return apiRequest('/api/auth/verify', { method: 'POST', body: { email, code }, schema: userSchema })
}

/** The signed-in user, or null when there is no valid session. */
export async function fetchMe(signal?: AbortSignal): Promise<User | null> {
  try {
    return await apiRequest('/api/me', { schema: userSchema, signal })
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

export function logout(): Promise<void> {
  return apiRequest('/api/auth/logout', { method: 'POST', schema: z.undefined() })
}
