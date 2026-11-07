import { z } from 'zod'

export const userSchema = z.object({
  id: z.uuid(),
  email: z.string(),
  name: z.string(),
})

export type User = z.infer<typeof userSchema>

export const CODE_LENGTH = 6

/** Form schema of the first login step. */
export const emailFormSchema = z.object({
  email: z.string().trim().min(1, 'Enter your email address').pipe(z.email('Enter a valid email address')),
})

export type EmailForm = z.infer<typeof emailFormSchema>
