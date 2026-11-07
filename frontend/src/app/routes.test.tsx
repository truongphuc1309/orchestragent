import { describe, expect, it } from 'vitest'
import { routes } from './routes'

describe('routes', () => {
  it('every page route has the error page, so a crash never leaves a blank screen', () => {
    const pageRoutes = routes.filter((route) => route.path !== '*')

    expect(pageRoutes.length).toBeGreaterThan(0)
    for (const route of pageRoutes) {
      expect(route.errorElement, `route ${route.path ?? '(layout)'}`).toBeDefined()
    }
  })
})
