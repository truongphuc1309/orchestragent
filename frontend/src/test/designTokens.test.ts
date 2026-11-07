import { describe, expect, it } from 'vitest'

/**
 * Enforces the UI rule from AGENTS.md: colours live only in the tokens of index.css. Components and pages use
 * token classes (bg-primary, text-muted-foreground, ...), never raw colours or the default Tailwind palette.
 */
const sources = import.meta.glob<string>('/src/**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true })

const RAW_COLOR = /#[0-9a-fA-F]{3,8}\b|\b(?:rgb|rgba|hsl|hsla|oklch|oklab|lab|lch)\(/
const DEFAULT_PALETTE =
  /\b(?:bg|text|border|ring|fill|stroke|from|to|via|outline|divide|shadow|decoration|accent|caret)-(?:red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose|slate|gray|zinc|neutral|stone)-\d{2,3}\b/
const PLAIN_BLACK_WHITE = /\b(?:bg|text|border|ring|fill|stroke)-(?:white|black)\b/

describe('design tokens', () => {
  const files = Object.entries(sources).filter(([path]) => !path.endsWith('designTokens.test.ts'))

  it('finds the source files it is supposed to check', () => {
    expect(files.length).toBeGreaterThan(20)
    expect(files.some(([path]) => path.endsWith('/shared/ui/button.tsx'))).toBe(true)
  })

  it.each([
    ['raw colour values', RAW_COLOR],
    ['default Tailwind palette classes', DEFAULT_PALETTE],
    ['plain black/white classes', PLAIN_BLACK_WHITE],
  ])('no source file uses %s', (_name, pattern) => {
    const offenders = files.filter(([, text]) => pattern.test(text)).map(([path]) => path)
    expect(offenders).toEqual([])
  })
})
