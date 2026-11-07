import { describe, expect, it } from 'vitest'
import { redirectTarget } from './redirect'

describe('redirectTarget', () => {
  it('keeps a plain app path, including query and hash', () => {
    expect(redirectTarget({ from: '/w/acme/issues?status=todo#top' })).toBe('/w/acme/issues?status=todo#top')
  })

  it.each([
    ['protocol-relative URL', '//evil.example.com/steal'],
    ['absolute URL', 'https://evil.example.com'],
    ['javascript URL', 'javascript:alert(1)'],
    ['backslash trick', '/\\evil.example.com'],
    ['relative path', 'issues'],
    ['empty string', ''],
  ])('falls back to the start page for a %s', (_name, from) => {
    expect(redirectTarget({ from })).toBe('/')
  })

  it.each([['/login'], ['/login?x=1'], ['/login/again']])('never redirects back to the login page (%s)', (from) => {
    expect(redirectTarget({ from })).toBe('/')
  })

  it('does not mistake other paths that merely start with the word login', () => {
    expect(redirectTarget({ from: '/loginhistory' })).toBe('/loginhistory')
  })

  it.each([[undefined], [null], ['/x'], [42], [{}], [{ from: 7 }]])('falls back for unusable state %j', (state) => {
    expect(redirectTarget(state)).toBe('/')
  })
})
