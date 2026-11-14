import { userSchema, type User } from '@/features/auth/schemas'
import { apiRequest } from '@/shared/api/client'
import type { ProfileForm } from './schemas'

export function updateProfile(changes: ProfileForm): Promise<User> {
  return apiRequest('/api/me', { method: 'PATCH', body: changes, schema: userSchema })
}

export function uploadAvatar(file: File): Promise<User> {
  const form = new FormData()
  form.append('file', file)
  return apiRequest('/api/me/avatar', { method: 'PUT', body: form, schema: userSchema })
}

export function removeAvatar(): Promise<User> {
  return apiRequest('/api/me/avatar', { method: 'DELETE', schema: userSchema })
}
