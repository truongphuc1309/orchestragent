import { describe, expect, it } from 'vitest'
import { formatAgo, formatCost, formatDuration, formatTokens } from './format'

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

describe('formatDuration', () => {
  it.each([
    [0, '0s'],
    [999, '0s'],
    [45_000, '45s'],
    [59_999, '59s'],
    [60_000, '1m 00s'],
    [185_000, '3m 05s'],
    [3_599_000, '59m 59s'],
    [3_600_000, '1h 00m'],
    [3_720_000, '1h 02m'],
    [90_000_000, '25h 00m'],
  ])('%s ms reads "%s"', (milliseconds, expected) => {
    expect(formatDuration(milliseconds)).toBe(expected)
  })

  it('shows a dash for something that is not a duration', () => {
    expect(formatDuration(-1)).toBe('—')
    expect(formatDuration(Number.NaN)).toBe('—')
    expect(formatDuration(Number.POSITIVE_INFINITY)).toBe('—')
  })
})

describe('formatTokens', () => {
  it.each([
    [0, '0'],
    [950, '950'],
    [999, '999'],
    [1000, '1k'],
    [1234, '1.2k'],
    [15_000, '15k'],
    [999_949, '999.9k'],
    [999_950, '1M'],
    [999_999, '1M'],
    [1_000_000, '1M'],
    [3_400_000, '3.4M'],
  ])('%s tokens read "%s"', (count, expected) => {
    expect(formatTokens(count)).toBe(expected)
  })
})

describe('formatCost', () => {
  it.each([
    [0, '$0.00'],
    [0.004, '<$0.01'],
    [0.01, '$0.01'],
    [0.0549, '$0.05'],
    [12.5, '$12.50'],
  ])('%s dollars read "%s"', (usd, expected) => {
    expect(formatCost(usd)).toBe(expected)
  })
})
