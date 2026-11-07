import { z } from 'zod'
import type { Actor, AssigneeRef } from './schemas'

/** Select value for "nobody". The others look like {@code member:<id>} or {@code agent:<id>}. */
export const UNASSIGNED = 'unassigned'

const encoded = z.object({ type: z.enum(['member', 'agent']), id: z.uuid() })

/** The value of an assignee in a select or a form field. */
export function encodeAssignee(assignee: AssigneeRef | null): string {
  return assignee === null ? UNASSIGNED : `${assignee.type}:${assignee.id}`
}

/** The reverse of {@link encodeAssignee}; anything that is not a member or agent means "nobody". */
export function decodeAssignee(value: string): AssigneeRef | null {
  const [type, id] = value.split(':')
  const parsed = encoded.safeParse({ type, id })
  return parsed.success ? parsed.data : null
}

/** The assignee of an issue as a reference, or null when nobody (or something else) is assigned. */
export function assigneeRef(actor: Actor | null): AssigneeRef | null {
  if (actor === null || actor.id === null || actor.type === 'system') {
    return null
  }
  return { type: actor.type, id: actor.id }
}
