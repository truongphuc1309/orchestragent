import { describe, expect, it } from 'vitest'

/**
 * Every element that scrolls its content (a board, a wide table) must also be positioned (`relative`). Otherwise
 * absolutely positioned children, such as the `sr-only` texts on issue cards, take the whole page as their
 * containing block, escape the scroll area and make the entire page scroll sideways.
 */
const sources = import.meta.glob<string>('/src/**/*.tsx', { query: '?raw', import: 'default', eager: true })

/** A class list with a scrolling overflow on an element that can hold positioned children (not a <pre>). */
const SCROLLING_DIV = /<(?!pre\b)[a-z]+\b[^>]*className="([^"]*\boverflow-(?:x-)?auto\b[^"]*)"/g

describe('scroll containers', () => {
  const files = Object.entries(sources).filter(([path]) => !path.endsWith('.test.tsx') && !path.includes('/shared/ui/'))

  it('finds scrolling elements to check', () => {
    const found = files.flatMap(([, text]) => [...text.matchAll(SCROLLING_DIV)])
    expect(found.length).toBeGreaterThan(3)
  })

  it('makes every scrolling element the containing block of its content', () => {
    const offenders = files.flatMap(([path, text]) =>
      [...text.matchAll(SCROLLING_DIV)]
        .filter((match) => !/\b(?:relative|absolute|fixed|sticky)\b/.test(match[1] ?? ''))
        .map((match) => `${path}: ${match[1]}`),
    )
    expect(offenders).toEqual([])
  })
})
