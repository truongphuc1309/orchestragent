import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACME } from '@/test/fixtures'
import { fakeServer, json, noContent, problem } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'

const ADA = { id: '0198f1c0-0000-7000-8000-000000000001', email: 'ada@example.com', name: 'ada' }
const workspaces = () => json(200, [ACME])

function signedOut() {
  return problem(401, { title: 'Unauthorized' })
}

describe('LoginPage', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  async function sendCodeTo(email: string) {
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Email'), email)
    await user.click(screen.getByRole('button', { name: 'Send code' }))
    return user
  }

  it('asks for a valid email before sending anything', async () => {
    const server = fakeServer({ 'GET /api/me': signedOut })
    renderRoutes(routes, ['/login'])

    await sendCodeTo('not-an-email')

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument()
    expect(server.calls('POST /api/auth/code')).toHaveLength(0)
  })

  it('sends the code, then signs in once six digits are entered', async () => {
    const server = fakeServer({
      'GET /api/me': signedOut,
      'GET /api/workspaces': workspaces,
      'POST /api/auth/code': () => noContent(),
      'POST /api/auth/verify': () => json(200, ADA),
    })
    renderRoutes(routes, ['/login'])

    const user = await sendCodeTo('  ada@example.com ')
    expect(await screen.findByText('Enter the code we sent to ada@example.com.')).toBeInTheDocument()
    expect(server.calls('POST /api/auth/code')[0]?.body).toEqual({ email: 'ada@example.com' })
    expect(server.calls('POST /api/auth/code')[0]?.headers.get('X-XSRF-TOKEN')).toBe('test-token')

    await user.type(screen.getByRole('textbox', { name: /6-digit code/i }), '123456')

    expect(await screen.findByRole('heading', { name: 'Acme' })).toBeInTheDocument()
    expect(server.calls('POST /api/auth/verify')[0]?.body).toEqual({ email: 'ada@example.com', code: '123456' })
  })

  it('shows a clear message and clears the input when the code is wrong', async () => {
    fakeServer({
      'GET /api/me': signedOut,
      'POST /api/auth/code': () => noContent(),
      'POST /api/auth/verify': () => problem(401, { code: 'login.invalid_code', detail: 'The code is invalid or has expired' }),
    })
    renderRoutes(routes, ['/login'])
    const user = await sendCodeTo('ada@example.com')
    const input = await screen.findByRole('textbox', { name: /6-digit code/i })

    await user.type(input, '000000')

    expect(await screen.findByText(/wrong or has expired/i)).toBeInTheDocument()
    await waitFor(() => expect(input).toHaveValue(''))
    expect(screen.queryByRole('heading', { name: /Welcome/ })).not.toBeInTheDocument()
  })

  it('explains the rate limit when a code was requested too recently', async () => {
    fakeServer({
      'GET /api/me': signedOut,
      'POST /api/auth/code': () => problem(429, { code: 'login.code_rate_limited', retryAfterSeconds: 42 }),
    })
    renderRoutes(routes, ['/login'])

    await sendCodeTo('ada@example.com')

    expect(await screen.findByText(/You can ask for another in 42 seconds/)).toBeInTheDocument()
    expect(screen.queryByText(/Enter the code we sent/)).not.toBeInTheDocument()
  })

  it('makes the user wait before asking for another code', async () => {
    fakeServer({ 'GET /api/me': signedOut, 'POST /api/auth/code': () => noContent() })
    renderRoutes(routes, ['/login'])
    await sendCodeTo('ada@example.com')

    const resend = await screen.findByRole('button', { name: /Resend code \(\d+s\)/ })

    expect(resend).toBeDisabled()
  })

  it('lets the user go back and use a different email', async () => {
    fakeServer({ 'GET /api/me': signedOut, 'POST /api/auth/code': () => noContent() })
    renderRoutes(routes, ['/login'])
    const user = await sendCodeTo('ada@example.com')

    await user.click(await screen.findByRole('button', { name: 'Use a different email' }))

    expect(await screen.findByLabelText('Email')).toBeInTheDocument()
  })

  it('returns to the page the user wanted after signing in', async () => {
    fakeServer({
      'GET /api/me': signedOut,
      'POST /api/auth/code': () => noContent(),
      'POST /api/auth/verify': () => json(200, ADA),
    })
    const pages = [
      routes[0]!,
      { element: routes[1]?.element, children: [{ path: '/secret', element: <p>Secret page</p> }] },
    ]
    renderRoutes(pages, ['/secret'])

    const user = await sendCodeTo('ada@example.com')
    await user.type(await screen.findByRole('textbox', { name: /6-digit code/i }), '123456')

    expect(await screen.findByText('Secret page')).toBeInTheDocument()
  })

  it('skips the login page for a user who is already signed in', async () => {
    fakeServer({ 'GET /api/me': () => json(200, ADA), 'GET /api/workspaces': workspaces })
    renderRoutes(routes, ['/login'])

    expect(await screen.findByRole('heading', { name: 'Acme' })).toBeInTheDocument()
  })
})
