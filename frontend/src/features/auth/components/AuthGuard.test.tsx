import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeServer, json, problem } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import { LoginPage } from '../pages/LoginPage'
import { AuthGuard } from './AuthGuard'

const ADA = { id: '0198f1c0-0000-7000-8000-000000000001', email: 'ada@example.com', name: 'ada' }

const routes = [
  { path: '/login', element: <LoginPage /> },
  { element: <AuthGuard />, children: [{ path: '/private', element: <p>Private content</p> }] },
]

describe('AuthGuard', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows the protected page to a signed-in user', async () => {
    fakeServer({ 'GET /api/me': () => json(200, ADA) })
    renderRoutes(routes, ['/private'])

    expect(await screen.findByText('Private content')).toBeInTheDocument()
  })

  it('sends a signed-out visitor to the login page', async () => {
    fakeServer({ 'GET /api/me': () => problem(401, {}) })
    const { router } = renderRoutes(routes, ['/private'])

    expect(await screen.findByText('Sign in to Orchestragent')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(screen.queryByText('Private content')).not.toBeInTheDocument()
  })

  it('shows a loading state while it does not know yet', () => {
    fakeServer({ 'GET /api/me': () => new Promise<Response>(() => {}) })
    renderRoutes(routes, ['/private'])

    expect(screen.getByText('Loading…')).toBeInTheDocument()
    expect(screen.queryByText('Private content')).not.toBeInTheDocument()
  })

  it('offers a retry instead of the login page when the server cannot be reached', async () => {
    let healthy = false
    fakeServer({
      'GET /api/me': () => (healthy ? json(200, ADA) : problem(500, { detail: 'boom' })),
    })
    const { router } = renderRoutes(routes, ['/private'])
    const user = userEvent.setup()

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the server')
    expect(router.state.location.pathname).toBe('/private')

    healthy = true
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Private content')).toBeInTheDocument()
  })
})
