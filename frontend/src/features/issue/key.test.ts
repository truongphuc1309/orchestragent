import { describe, expect, it } from 'vitest'
import { issueKey } from './key'

describe('issueKey', () => {
  it('uses the first three letters of the slug, upper-cased', () => {
    expect(issueKey('acme', 12)).toBe('ACM-12')
    expect(issueKey('orchestragent', 1)).toBe('ORC-1')
  })

  it('skips hyphens and short slugs are used whole', () => {
    expect(issueKey('a-b-c-d', 5)).toBe('ABC-5')
    expect(issueKey('go', 5)).toBe('GO-5')
  })

  it('has a fallback for a slug without letters or digits', () => {
    expect(issueKey('---', 3)).toBe('ISS-3')
  })
})
