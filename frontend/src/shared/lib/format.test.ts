import { describe, expect, it } from 'vitest'
import { formatAgo } from './format'

const NOW = Date.parse('2026-01-05T12:00:00Z')
const ago = (seconds: number) => new Date(NOW - seconds * 1000).toISOString()

describe('formatAgo', () => {
  it.each([
    [0, 'just now'],
    [44, 'just now'],
    [45, '1 min ago'],
    [90, '2 min ago'],
    [59 * 60, '59 min ago'],
    [60 * 60, '1 h ago'],
    [23 * 3600 + 59 * 60, '23 h ago'],
    [24 * 3600, '1 d ago'],
    [10 * 24 * 3600, '10 d ago'],
  ])('%s seconds ago reads "%s"', (seconds, expected) => {
    expect(formatAgo(ago(seconds), NOW)).toBe(expected)
  })

  it('treats a time slightly in the future as just now', () => {
    expect(formatAgo(new Date(NOW + 5000).toISOString(), NOW)).toBe('just now')
  })

  it('shows a dash for something that is not a date', () => {
    expect(formatAgo('yesterday', NOW)).toBe('—')
  })
})
