import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import type { User } from '@/features/auth/schemas'
import { ACME, ADA } from '@/test/fixtures'
import { fakeServer, json, problem, type RecordedRequest } from '@/test/fakeServer'
import { stubImagesAsLoaded } from '@/test/images'
import { renderRoutes } from '@/test/render'

const AVATAR_URL = `/api/users/${ADA.id}/avatar/0198f1c0-0000-7000-8000-0000000000f1`

function png(name = 'me.png', size = 128): File {
  return new File([new Uint8Array(size)], name, { type: 'image/png' })
}

function bodyOf(request: RecordedRequest): { name?: unknown; bio?: unknown } {
  const body = request.body
  return typeof body === 'object' && body !== null ? body : {}
}

/** Mounts the profile page for ADA. The fake server keeps the profile, like the real one. */
function setup(start: Partial<User> = {}, overrides: Parameters<typeof fakeServer>[0] = {}) {
  let me: User = { ...ADA, ...start }
  const server = fakeServer({
    'GET /api/me': () => json(200, me),
    'GET /api/workspaces': () => json(200, [ACME]),
    'PATCH /api/me': (request) => {
      const { name, bio } = bodyOf(request)
      me = { ...me, name: String(name).trim(), bio: String(bio).trim() }
      return json(200, me)
    },
    'PUT /api/me/avatar': () => {
      me = { ...me, avatarUrl: AVATAR_URL }
      return json(200, me)
    },
    'DELETE /api/me/avatar': () => {
      me = { ...me, avatarUrl: null }
      return json(200, me)
    },
    ...overrides,
  })
  const view = renderRoutes(routes, ['/w/acme/settings/profile'])
  return { server, ...view }
}

describe('ProfilePage', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    stubImagesAsLoaded()
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('shows the current name, bio and email, and nothing to save yet', async () => {
    setup({ name: 'Ada Lovelace', bio: 'First programmer' })

    expect(await screen.findByRole('heading', { name: 'Profile' })).toBeInTheDocument()
    expect(await screen.findByLabelText('Name')).toHaveValue('Ada Lovelace')
    expect(screen.getByLabelText('About you')).toHaveValue('First programmer')
    expect(screen.getByLabelText('Email')).toHaveValue('ada@example.com')
    expect(screen.getByLabelText('Email')).toHaveAttribute('readonly')
    expect(screen.getByText('16/500 characters')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Upload photo' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove photo' })).not.toBeInTheDocument()
  })

  it('saves the name and bio, and the account menu shows the new name', async () => {
    const { server } = setup()
    const user = userEvent.setup()

    const name = await screen.findByLabelText('Name')
    await user.clear(name)
    await user.type(name, '  Ada Lovelace ')
    await user.type(screen.getByLabelText('About you'), 'Line one{Enter}Line two')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Profile saved')).toBeInTheDocument()
    const [request] = server.calls('PATCH /api/me')
    expect(request?.body).toEqual({ name: 'Ada Lovelace', bio: 'Line one\nLine two' })
    expect(request?.headers.get('X-XSRF-TOKEN')).toBe('test-token')
    expect(screen.getByLabelText('Name')).toHaveValue('Ada Lovelace')
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Account menu' }))
    expect(await screen.findByText('Ada Lovelace', { selector: 'span' })).toBeInTheDocument()
  })

  it('needs a name and sends nothing without one', async () => {
    const { server } = setup()
    const user = userEvent.setup()

    await user.clear(await screen.findByLabelText('Name'))
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Enter your name')
    expect(screen.getByLabelText('Name')).toHaveAttribute('aria-invalid', 'true')
    expect(server.calls('PATCH /api/me')).toHaveLength(0)
  })

  it('shows what the server refused, until the person edits again', async () => {
    setup({}, { 'PATCH /api/me': () => problem(400, { code: 'profile.name_invalid', detail: 'Name must not contain control characters' }) })
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Name'), 'x')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Name must not contain control characters')
    await user.type(screen.getByLabelText('Name'), 'y')
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })

  it('uploads a photo and shows it on the page and in the account menu', async () => {
    const { server } = setup()
    const user = userEvent.setup()
    const file = png()

    await user.upload(await screen.findByLabelText('Photo file'), file)

    expect(await screen.findByText('Photo updated')).toBeInTheDocument()
    const [request] = server.calls('PUT /api/me/avatar')
    expect(request?.rawBody).toBeInstanceOf(FormData)
    expect(request?.rawBody instanceof FormData && request.rawBody.get('file')).toBe(file)
    expect(request?.headers.get('X-XSRF-TOKEN')).toBe('test-token')
    expect(await screen.findByRole('button', { name: 'Change photo' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Remove photo' })).toBeInTheDocument()

    const pictures = document.querySelectorAll(`img[src="${AVATAR_URL}"]`)
    // The large one on the page and the one on the account menu button.
    expect(pictures).toHaveLength(2)
    expect(within(screen.getByRole('button', { name: 'Account menu' })).getByRole('presentation')).toHaveAttribute(
      'src',
      AVATAR_URL,
    )
  })

  it('refuses files that are not PNG, JPEG or WebP, or larger than 2 MB, without uploading them', async () => {
    const { server } = setup()
    // The file picker filters by type itself; a drop or another browser may not, so the page checks again.
    const user = userEvent.setup({ applyAccept: false })
    const input = await screen.findByLabelText('Photo file')

    await user.upload(input, new File(['<svg/>'], 'logo.svg', { type: 'image/svg+xml' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Choose a PNG, JPEG or WebP image.')

    await user.upload(input, png('big.png', 2 * 1024 * 1024 + 1))
    expect(await screen.findByRole('alert')).toHaveTextContent('The photo must be at most 2 MB.')

    expect(server.calls('PUT /api/me/avatar')).toHaveLength(0)
  })

  it('accepts a photo of exactly 2 MB', async () => {
    const { server } = setup()
    const user = userEvent.setup()

    await user.upload(await screen.findByLabelText('Photo file'), png('edge.png', 2 * 1024 * 1024))

    expect(await screen.findByText('Photo updated')).toBeInTheDocument()
    expect(server.calls('PUT /api/me/avatar')).toHaveLength(1)
  })

  it('shows why an upload failed', async () => {
    setup({}, {
      'PUT /api/me/avatar': () =>
        problem(503, { code: 'storage.unavailable', detail: 'File storage is not available. Please try again later.' }),
    })
    const user = userEvent.setup()

    await user.upload(await screen.findByLabelText('Photo file'), png())

    expect(await screen.findByRole('alert')).toHaveTextContent('File storage is not available. Please try again later.')
    expect(screen.getByRole('button', { name: 'Upload photo' })).toBeEnabled()
  })

  it('removes the photo', async () => {
    const { server } = setup({ avatarUrl: AVATAR_URL })
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Remove photo' }))

    expect(await screen.findByText('Photo removed')).toBeInTheDocument()
    expect(server.calls('DELETE /api/me/avatar')).toHaveLength(1)
    expect(await screen.findByRole('button', { name: 'Upload photo' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove photo' })).not.toBeInTheDocument()
    expect(document.querySelector(`img[src="${AVATAR_URL}"]`)).toBeNull()
  })

  it('is reached from the sidebar and from the account menu', async () => {
    fakeServer({
      'GET /api/me': () => json(200, ADA),
      'GET /api/workspaces': () => json(200, [ACME]),
      [`GET /api/workspaces/${ACME.id}/members`]: () => json(200, []),
      [`GET /api/workspaces/${ACME.id}/invitations`]: () => json(200, []),
    })
    const { router } = renderRoutes(routes, ['/w/acme/settings/members'])
    const user = userEvent.setup()

    await user.click(await screen.findByRole('link', { name: 'Profile' }))
    expect(await screen.findByRole('heading', { name: 'Profile' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/w/acme/settings/profile')

    await router.navigate('/w/acme/settings/members')
    await user.click(await screen.findByRole('button', { name: 'Account menu' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Profile' }))
    expect(await screen.findByRole('heading', { name: 'Profile' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/w/acme/settings/profile')
  })
})
