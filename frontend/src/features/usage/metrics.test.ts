import { describe, expect, it } from 'vitest'
import { runsNote } from './metrics'

describe('runsNote', () => {
  const totals = { inputTokens: 0, outputTokens: 0, costUsd: 0 }

  it('names the cancelled runs only when there are some', () => {
    expect(runsNote({ ...totals, runs: 5, completed: 4, failed: 1 })).toBe('4 completed, 1 failed')
    expect(runsNote({ ...totals, runs: 9, completed: 7, failed: 1 })).toBe('7 completed, 1 failed, 1 cancelled')
  })
})
