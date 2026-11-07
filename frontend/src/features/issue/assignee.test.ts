import { describe, expect, it } from 'vitest'
import { assigneeRef, decodeAssignee, encodeAssignee, UNASSIGNED } from './assignee'

const ID = '0198f1c0-0000-7000-8000-000000000001'

describe('assignee values', () => {
  it('round-trips members and agents', () => {
    for (const type of ['member', 'agent'] as const) {
      expect(decodeAssignee(encodeAssignee({ type, id: ID }))).toEqual({ type, id: ID })
    }
  })

  it('writes nobody as "unassigned" and reads it back as nobody', () => {
    expect(encodeAssignee(null)).toBe(UNASSIGNED)
    expect(decodeAssignee(UNASSIGNED)).toBeNull()
    expect(decodeAssignee('')).toBeNull()
  })

  it.each(['member', 'member:', 'robot:' + ID, 'agent:not-a-uuid', ':' + ID])('reads %s as nobody', (value) => {
    expect(decodeAssignee(value)).toBeNull()
  })

  it('keeps a member and an agent with the same ID apart', () => {
    expect(encodeAssignee({ type: 'member', id: ID })).not.toBe(encodeAssignee({ type: 'agent', id: ID }))
  })
})

describe('assigneeRef', () => {
  it('turns the assignee of an issue into a reference', () => {
    expect(assigneeRef({ type: 'agent', id: ID, name: 'Builder' })).toEqual({ type: 'agent', id: ID })
    expect(assigneeRef({ type: 'member', id: ID, name: 'Mia' })).toEqual({ type: 'member', id: ID })
  })

  it('gives nothing for nobody and for the system', () => {
    expect(assigneeRef(null)).toBeNull()
    expect(assigneeRef({ type: 'system', id: null, name: 'System' })).toBeNull()
  })
})
