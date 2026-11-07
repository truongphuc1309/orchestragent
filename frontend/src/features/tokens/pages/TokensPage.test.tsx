import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACME, ADA } from '@/test/fixtures'
import { fakeServer, json, noContent, problem } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import type { ApiToken } from '../schemas'

const SECRET = 'oat_SuperSecretValueThatIsShownOnlyOnce1234567890'
const NEW_ID = '0198f1c0-0000-7000-8000-0000000000e1'

function token(overrides: Partial<ApiToken> & Pick<ApiToken, 'id' | 'name'>): ApiToken {
  return {
    tokenPrefix: 'oat_AbCd12',
    lastUsedAt: null,
    revokedAt: null,
    createdAt: '2026-01-05T10:00:00Z',
    ...overrides,
  }
}

function setup(initial: ApiToken[]) {
  let tokens = initial
  const server = fakeServer({
    'GET /api/me': () => json(200, ADA),
    'GET /api/workspaces': () => json(200, [ACME]),
    'GET /api/me/tokens': () => json(200, tokens),
    'POST /api/me/tokens': (request) => {
      const body = request.body
      const name = typeof body === 'object' && body !== null && 'name' in body ? String(body.name) : ''
      const created = token({ id: NEW_ID, name, tokenPrefix: SECRET.slice(0, 10) })
      tokens = [created, ...tokens]
      return json(201, { token: created, secret: SECRET })
    },
    [`DELETE /api/me/tokens/${NEW_ID}`]: () => {
      tokens = tokens.map((t) => (t.id === NEW_ID ? { ...t, revokedAt: '2026-01-06T10:00:00Z' } : t))
      return noContent()
    },
  })
  renderRoutes(routes, ['/w/acme/settings/tokens'])
  return server
}

describe('TokensPage', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('shows an empty state', async () => {
    setup([])

    expect(await screen.findByText('You have no tokens yet.')).toBeInTheDocument()
  })

  it('lists tokens with their prefix and status, and only active ones can be revoked', async () => {
    setup([
      token({ id: '0198f1c0-0000-7000-8000-0000000000e2', name: 'Work laptop', lastUsedAt: '2026-01-05T12:00:00Z' }),
      token({ id: '0198f1c0-0000-7000-8000-0000000000e3', name: 'Old desktop', revokedAt: '2026-01-04T10:00:00Z' }),
    ])

    const list = await screen.findByRole('list', { name: 'Tokens' })
    const [active, revoked] = within(list).getAllByRole('listitem')
    expect(within(active!).getByText('Work laptop')).toBeInTheDocument()
    expect(within(active!).getByText('Active')).toBeInTheDocument()
    expect(within(active!).getByRole('button', { name: 'Revoke Work laptop' })).toBeInTheDocument()
    expect(within(active!).getByText(/oat_AbCd12…/)).toBeInTheDocument()
    expect(within(revoked!).getByText('Revoked')).toBeInTheDocument()
    expect(within(revoked!).queryByRole('button')).not.toBeInTheDocument()
    expect(within(revoked!).getByText(/Last used never/)).toBeInTheDocument()
  })

  it('shows the new token once, lets the user copy it, and never lists the secret', async () => {
    const server = setup([])
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Token name'), '  Work laptop ')
    await user.click(screen.getByRole('button', { name: 'Create token' }))

    expect(await screen.findByText('Your new token')).toBeInTheDocument()
    expect(screen.getByLabelText('New API token')).toHaveTextContent(SECRET)
    expect(server.calls('POST /api/me/tokens')[0]?.body).toEqual({ name: 'Work laptop' })
    expect(server.calls('POST /api/me/tokens')[0]?.headers.get('X-XSRF-TOKEN')).toBe('test-token')

    await user.click(screen.getByRole('button', { name: 'Copy token' }))
    expect(await screen.findByText('Copied')).toBeInTheDocument()
    expect(await navigator.clipboard.readText()).toBe(SECRET)

    await user.click(screen.getByRole('button', { name: 'Done' }))

    await waitFor(() => expect(screen.queryByText('Your new token')).not.toBeInTheDocument())
    expect(screen.queryByText(SECRET)).not.toBeInTheDocument()
    const list = await screen.findByRole('list', { name: 'Tokens' })
    expect(within(list).getByText('Work laptop')).toBeInTheDocument()
    expect(list).not.toHaveTextContent(SECRET)
  })

  it('does not create a token without a name', async () => {
    const server = setup([])
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Create token' }))

    expect(await screen.findByText('Enter a name for the token')).toBeInTheDocument()
    expect(server.calls('POST /api/me/tokens')).toHaveLength(0)
  })

  it('shows the server explanation when creating fails', async () => {
    fakeServer({
      'GET /api/me': () => json(200, ADA),
      'GET /api/workspaces': () => json(200, [ACME]),
      'GET /api/me/tokens': () => json(200, []),
      'POST /api/me/tokens': () => problem(400, { detail: 'Token name must be 1 to 100 characters' }),
    })
    renderRoutes(routes, ['/w/acme/settings/tokens'])
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Token name'), 'x')
    await user.click(screen.getByRole('button', { name: 'Create token' }))

    expect(await screen.findByText('Token name must be 1 to 100 characters')).toBeInTheDocument()
    expect(screen.queryByText('Your new token')).not.toBeInTheDocument()
  })

  it('revokes a token only after confirmation', async () => {
    const server = setup([token({ id: NEW_ID, name: 'Work laptop' })])
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Revoke Work laptop' }))
    expect(await screen.findByText('Revoke "Work laptop"?')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(screen.queryByText('Revoke "Work laptop"?')).not.toBeInTheDocument())
    expect(server.calls(`DELETE /api/me/tokens/${NEW_ID}`)).toHaveLength(0)

    await user.click(screen.getByRole('button', { name: 'Revoke Work laptop' }))
    await user.click(await screen.findByRole('button', { name: 'Revoke token' }))

    expect(await screen.findByText('Revoked')).toBeInTheDocument()
    expect(server.calls(`DELETE /api/me/tokens/${NEW_ID}`)).toHaveLength(1)
    expect(screen.queryByRole('button', { name: 'Revoke Work laptop' })).not.toBeInTheDocument()
  })

  it('offers a retry when the tokens cannot be loaded', async () => {
    let healthy = false
    fakeServer({
      'GET /api/me': () => json(200, ADA),
      'GET /api/workspaces': () => json(200, [ACME]),
      'GET /api/me/tokens': () => (healthy ? json(200, []) : problem(500, {})),
    })
    renderRoutes(routes, ['/w/acme/settings/tokens'])
    const user = userEvent.setup()

    expect(await screen.findByText('Could not load your tokens.')).toBeInTheDocument()
    healthy = true
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('You have no tokens yet.')).toBeInTheDocument()
  })
})
