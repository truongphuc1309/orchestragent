import { z } from 'zod'

/** 2 to 10 upper-case letters and digits, starting with a letter: the server's rule (ProjectKey). */
export const PROJECT_KEY_PATTERN = /^[A-Z][A-Z0-9]{1,9}$/
export const MAX_PROJECT_KEY = 10
export const MAX_PROJECT_NAME = 80
export const MAX_PROJECT_DESCRIPTION = 2_000

/** A group of issues. Its key starts the keys of its issues (WEB in WEB-12) and never changes. */
export const projectSchema = z.object({
  id: z.uuid(),
  key: z.string(),
  name: z.string(),
  description: z.string(),
  createdById: z.uuid(),
  createdByName: z.string(),
  archived: z.boolean(),
  archivedAt: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
})
export type Project = z.infer<typeof projectSchema>

export const projectFormSchema = z.object({
  key: z
    .string()
    .trim()
    .toUpperCase()
    .regex(PROJECT_KEY_PATTERN, 'Use 2 to 10 letters and digits, starting with a letter'),
  name: z
    .string()
    .trim()
    .min(1, 'Enter a name for the project')
    .max(MAX_PROJECT_NAME, `Use at most ${MAX_PROJECT_NAME} characters`),
  description: z.string().max(MAX_PROJECT_DESCRIPTION, `Use at most ${MAX_PROJECT_DESCRIPTION} characters`),
})
export type ProjectFormInput = z.input<typeof projectFormSchema>
export type ProjectForm = z.output<typeof projectFormSchema>

/**
 * A key to start from while someone types the name of a new project: the initials of a name of several words
 * ("Mobile App" → MA... "Customer Web Portal" → CWP), or the first letters of a single word ("Backend" → BACK).
 * People may change it; it only has to be valid.
 */
export function suggestKey(name: string): string {
  const words = name
    // "đ" is a letter of its own, not "d" with a mark, so taking marks off would drop it.
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .normalize('NFKD')
    // Accents become marks of their own after NFKD: drop them so "Ứng" stays one word, "Ung".
    .replace(/\p{M}/gu, '')
    .replace(/[^A-Za-z0-9 ]/g, ' ')
    .split(' ')
    .filter((word) => word.length > 0)
  // A key starts with a letter: words before the first one that does are skipped.
  const firstLetter = words.findIndex((word) => /^[A-Za-z]/.test(word))
  if (firstLetter < 0) {
    return ''
  }
  const usable = words.slice(firstLetter)
  const key =
    usable.length === 1
      ? (usable[0] ?? '').slice(0, 4)
      : usable
          .map((word) => word[0] ?? '')
          .join('')
          .slice(0, MAX_PROJECT_KEY)
  const upper = key.toUpperCase()
  if (upper.length >= 2) {
    return upper
  }
  // One letter is not a key: take more of the first word.
  return (usable[0] ?? '').slice(0, 3).toUpperCase().padEnd(2, 'X')
}

/** An issue key such as WEB-12, as the issue route accepts it. */
export const ISSUE_KEY_PATTERN = /^[A-Z][A-Z0-9]{1,9}-[1-9][0-9]{0,8}$/
