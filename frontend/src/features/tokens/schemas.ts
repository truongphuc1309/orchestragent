import { z } from 'zod'

export const apiTokenSchema = z.object({
  id: z.uuid(),
  name: z.string(),
  /** The first characters of the token, enough to recognise it in the list. */
  tokenPrefix: z.string(),
  lastUsedAt: z.string().nullable(),
  revokedAt: z.string().nullable(),
  createdAt: z.string(),
})
export type ApiToken = z.infer<typeof apiTokenSchema>

/** The only response that contains the full token; the server never shows it again. */
export const createdApiTokenSchema = z.object({
  token: apiTokenSchema,
  secret: z.string(),
})
export type CreatedApiToken = z.infer<typeof createdApiTokenSchema>

export const MAX_TOKEN_NAME = 100

export const createTokenFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Enter a name for the token')
    .max(MAX_TOKEN_NAME, `Use at most ${MAX_TOKEN_NAME} characters`),
})
export type CreateTokenForm = z.infer<typeof createTokenFormSchema>
