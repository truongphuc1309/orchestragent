import { z } from 'zod'

export const MAX_NAME = 100
export const MAX_BIO = 500
export const MAX_AVATAR_BYTES = 2 * 1024 * 1024
/** What the server accepts. It reads the format from the file itself, so this check only saves a round trip. */
export const AVATAR_TYPES = ['image/png', 'image/jpeg', 'image/webp']

/** The same rules as the server (DisplayName and Bio in the identity domain). */
export const profileFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Enter your name')
    .max(MAX_NAME, `Name must be at most ${MAX_NAME} characters`)
    .refine((name) => !/[\n\r\t]/.test(name), 'Name must be on one line'),
  bio: z.string().trim().max(MAX_BIO, `About you must be at most ${MAX_BIO} characters`),
})

export type ProfileForm = z.infer<typeof profileFormSchema>

/** Why the file cannot be a profile photo, or null when it can. */
export function avatarFileProblem(file: File): string | null {
  if (!AVATAR_TYPES.includes(file.type)) {
    return 'Choose a PNG, JPEG or WebP image.'
  }
  if (file.size > MAX_AVATAR_BYTES) {
    return 'The photo must be at most 2 MB.'
  }
  return null
}
