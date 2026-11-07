import { z } from 'zod'

/** RFC 7807 problem details as produced by the backend. Every field is optional on purpose. */
export const problemSchema = z.object({
  title: z.string().optional(),
  detail: z.string().optional(),
  status: z.number().optional(),
  code: z.string().optional(),
  retryAfterSeconds: z.number().optional(),
})

export type Problem = z.infer<typeof problemSchema>

/** The server answered with an error status. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined
  readonly retryAfterSeconds: number | undefined

  constructor(status: number, message: string, problem: Problem = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = problem.code
    this.retryAfterSeconds = problem.retryAfterSeconds
  }
}

/** The server answered successfully, but the body did not match the expected schema. */
export class UnexpectedResponseError extends Error {
  readonly path: string

  constructor(path: string, issues: string) {
    super(`Unexpected response from ${path}: ${issues}`)
    this.name = 'UnexpectedResponseError'
    this.path = path
  }
}

/** A message that is safe to show to the user: the server's explanation for API errors, else the fallback. */
export function messageOf(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}
