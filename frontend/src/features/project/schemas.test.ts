import { describe, expect, it } from 'vitest'
import { ISSUE_KEY_PATTERN, projectFormSchema, suggestKey } from './schemas'

describe('suggestKey', () => {
  it.each([
    ['Web app', 'WA'],
    ['Customer Web Portal', 'CWP'],
    ['Backend', 'BACK'],
    ['  mobile   app  ', 'MA'],
    ['Ứng dụng di động', 'UDDD'],
    ['2026 Launch plan', 'LP'],
    ['A', 'AX'],
    ['', ''],
    ['123', ''],
    ['one two three four five six seven eight nine ten eleven', 'OTTFFSSENT'],
  ])('%j -> %j', (name, key) => {
    expect(suggestKey(name)).toBe(key)
  })

  it('always suggests a key the server accepts, or nothing', () => {
    for (const name of ['Web app', 'x', 'Ünïcode', 'a-b_c', '9 lives', 'Q']) {
      const key = suggestKey(name)
      expect(key === '' || projectFormSchema.shape.key.safeParse(key).success).toBe(true)
    }
  })
})

describe('the project form', () => {
  it('trims and upper-cases the key', () => {
    expect(projectFormSchema.parse({ key: ' web ', name: 'Web', description: '' }).key).toBe('WEB')
  })

  it.each(['W', '1WEB', 'WE B', 'WEB-1', 'ABCDEFGHIJK'])('refuses the key %j', (key) => {
    expect(projectFormSchema.safeParse({ key, name: 'Web', description: '' }).success).toBe(false)
  })
})

describe('issue keys in URLs', () => {
  it.each([
    ['WEB-1', true],
    ['API2-120', true],
    ['web-1', false],
    ['WEB-0', false],
    ['WEB', false],
    ['12', false],
    ['W-1', false],
  ])('%s -> %s', (key, valid) => {
    expect(ISSUE_KEY_PATTERN.test(key)).toBe(valid)
  })
})
