import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACME } from '@/test/fixtures'
import { fakeServer, json, noContent, problem } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'

const ADA = { id: '0198f1c0-0000-7000-8000-000000000001', email: 'ada@example.com', name: 'ada' }

describe('UserMenu', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('shows who is signed in and signs out from the account menu', async () => {
    let signedIn = true
    const server = fakeServer({
      'GET /api/me': () => (signedIn ? json(200, ADA) : problem(401, {})),
      'GET /api/workspaces': () => json(200, [ACME]),
      'POST /api/auth/logout': () => {
        signedIn = false
        return noContent()
      },
    })
    const { router } = renderRoutes(routes, ['/'])
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Account menu' }))
    expect(await screen.findByText('ada@example.com', { selector: 'span' })).toBeInTheDocument()
    await user.click(await screen.findByRole('menuitem', { name: 'Sign out' }))

    expect(await screen.findByText('Sign in to Orchestragent')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(server.calls('POST /api/auth/logout')).toHaveLength(1)
    expect(server.calls('POST /api/auth/logout')[0]?.headers.get('X-XSRF-TOKEN')).toBe('test-token')
  })
})
