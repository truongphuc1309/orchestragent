import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACME, ADA, GLOBEX, invitation } from '@/test/fixtures'
import { fakeServer, json, noContent, problem } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import { useLastWorkspace } from '../lastWorkspace'

const NEW_WORKSPACE = { ...ACME, id: '0198f1c0-0000-7000-8000-0000000000a3', name: 'Initech', slug: 'initech' }

/** A workspace opens on "My Issues"; the switcher at the top of the sidebar names the workspace that is open. */
async function expectWorkspaceOpen(name: string) {
  await screen.findByRole('heading', { name: 'My Issues' })
  // Switching workspaces keeps the same heading on screen, so wait for the name instead of reading it once.
  await waitFor(() => expect(screen.getByRole('button', { name: 'Switch workspace' })).toHaveTextContent(name))
}

describe('workspace navigation', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  describe('start page', () => {
    it('sends a user without workspaces to onboarding', async () => {
      fakeServer({ 'GET /api/me': () => json(200, ADA), 'GET /api/workspaces': () => json(200, []) })
      const { router } = renderRoutes(routes, ['/'])

      expect(await screen.findByText('Create your workspace')).toBeInTheDocument()
      expect(router.state.location.pathname).toBe('/onboarding')
    })

    it('opens the first workspace when nothing is remembered', async () => {
      fakeServer({ 'GET /api/me': () => json(200, ADA), 'GET /api/workspaces': () => json(200, [ACME, GLOBEX]) })
      const { router } = renderRoutes(routes, ['/'])

      await expectWorkspaceOpen('Acme')
      expect(router.state.location.pathname).toBe('/w/acme/issues')
    })

    it('returns to the workspace the user used last', async () => {
      useLastWorkspace.setState({ lastSlug: 'globex' })
      fakeServer({ 'GET /api/me': () => json(200, ADA), 'GET /api/workspaces': () => json(200, [ACME, GLOBEX]) })
      const { router } = renderRoutes(routes, ['/'])

      await expectWorkspaceOpen('Globex')
      expect(router.state.location.pathname).toBe('/w/globex/issues')
    })

    it('ignores a remembered workspace the user no longer belongs to', async () => {
      useLastWorkspace.setState({ lastSlug: 'gone' })
      fakeServer({ 'GET /api/me': () => json(200, ADA), 'GET /api/workspaces': () => json(200, [ACME]) })
      const { router } = renderRoutes(routes, ['/'])

      await expectWorkspaceOpen('Acme')
      expect(router.state.location.pathname).toBe('/w/acme/issues')
    })

    it('offers a retry when the workspaces cannot be loaded', async () => {
      let healthy = false
      fakeServer({
        'GET /api/me': () => json(200, ADA),
        'GET /api/workspaces': () => (healthy ? json(200, [ACME]) : problem(500, {})),
      })
      renderRoutes(routes, ['/'])
      const user = userEvent.setup()

      expect(await screen.findByRole('alert')).toHaveTextContent('Could not load your workspaces')
      healthy = true
      await user.click(screen.getByRole('button', { name: 'Try again' }))

      await expectWorkspaceOpen('Acme')
    })
  })

  describe('workspace layout', () => {
    it('shows the sidebar with the switcher and the settings pages', async () => {
      fakeServer({ 'GET /api/me': () => json(200, ADA), 'GET /api/workspaces': () => json(200, [ACME, GLOBEX]) })
      renderRoutes(routes, ['/w/acme'])

      const nav = await screen.findByRole('navigation', { name: 'Workspace' })
      expect(within(nav).getByRole('link', { name: 'My Issues' })).toHaveAttribute('href', '/w/acme/issues')
      expect(within(nav).getByRole('link', { name: 'Members' })).toHaveAttribute('href', '/w/acme/settings/members')
      expect(within(nav).getByRole('link', { name: 'API tokens' })).toHaveAttribute('href', '/w/acme/settings/tokens')
      // The workspace address redirects to the issues page; the entry is marked once it got there.
      await waitFor(() => expect(within(nav).getByRole('link', { name: 'My Issues' })).toHaveClass('bg-selected'))
      expect(within(nav).getByRole('link', { name: 'Members' })).not.toHaveClass('bg-selected')
    })

    it('has no home page: "My Issues" comes first and the workspace address opens it', async () => {
      fakeServer({ 'GET /api/me': () => json(200, ADA), 'GET /api/workspaces': () => json(200, [ACME]) })
      const { router } = renderRoutes(routes, ['/w/acme'])

      const nav = await screen.findByRole('navigation', { name: 'Workspace' })
      expect(within(nav).queryByRole('link', { name: 'Home' })).not.toBeInTheDocument()
      expect(within(nav).getAllByRole('link')[0]).toHaveAccessibleName('My Issues')
      await waitFor(() => expect(router.state.location.pathname).toBe('/w/acme/issues'))
      // Going back must not trap the user in a redirect.
      expect(router.state.historyAction).toBe('REPLACE')
    })

    it('says so when the workspace does not exist or belongs to someone else', async () => {
      fakeServer({ 'GET /api/me': () => json(200, ADA), 'GET /api/workspaces': () => json(200, [ACME]) })
      renderRoutes(routes, ['/w/not-mine'])

      expect(await screen.findByRole('heading', { name: 'Workspace not found' })).toBeInTheDocument()
      expect(screen.queryByRole('navigation', { name: 'Workspace' })).not.toBeInTheDocument()
    })

    it('forgets a remembered workspace that turned out to be gone', async () => {
      useLastWorkspace.setState({ lastSlug: 'not-mine' })
      fakeServer({ 'GET /api/me': () => json(200, ADA), 'GET /api/workspaces': () => json(200, [ACME]) })
      renderRoutes(routes, ['/w/not-mine'])

      await screen.findByRole('heading', { name: 'Workspace not found' })
      await waitFor(() => expect(useLastWorkspace.getState().lastSlug).toBeNull())
    })

    it('remembers the workspace being viewed', async () => {
      fakeServer({ 'GET /api/me': () => json(200, ADA), 'GET /api/workspaces': () => json(200, [ACME, GLOBEX]) })
      renderRoutes(routes, ['/w/globex'])

      await expectWorkspaceOpen('Globex')
      await waitFor(() => expect(useLastWorkspace.getState().lastSlug).toBe('globex'))
    })

    it('redirects /settings to the members page', async () => {
      fakeServer({
        'GET /api/me': () => json(200, ADA),
        'GET /api/workspaces': () => json(200, [ACME]),
        [`GET /api/workspaces/${ACME.id}/members`]: () => json(200, []),
        [`GET /api/workspaces/${ACME.id}/invitations`]: () => json(200, []),
      })
      const { router } = renderRoutes(routes, ['/w/acme/settings'])

      expect(await screen.findByRole('heading', { name: 'Members' })).toBeInTheDocument()
      expect(router.state.location.pathname).toBe('/w/acme/settings/members')
    })
  })

  describe('workspace switcher', () => {
    it('lists the workspaces, marks the current one and switches', async () => {
      fakeServer({ 'GET /api/me': () => json(200, ADA), 'GET /api/workspaces': () => json(200, [ACME, GLOBEX]) })
      const { router } = renderRoutes(routes, ['/w/acme'])
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Switch workspace' }))
      const acme = await screen.findByRole('menuitem', { name: /Acme/ })
      expect(within(acme).getByLabelText('Current workspace')).toBeInTheDocument()
      expect(within(screen.getByRole('menuitem', { name: /Globex/ })).queryByLabelText('Current workspace')).toBeNull()

      await user.click(screen.getByRole('menuitem', { name: /Globex/ }))

      await expectWorkspaceOpen('Globex')
      expect(router.state.location.pathname).toBe('/w/globex/issues')
    })

    it('creates a workspace from the menu and opens it', async () => {
      let created = false
      const server = fakeServer({
        'GET /api/me': () => json(200, ADA),
        'GET /api/workspaces': () => json(200, created ? [ACME, NEW_WORKSPACE] : [ACME]),
        'POST /api/workspaces': () => {
          created = true
          return json(201, NEW_WORKSPACE)
        },
      })
      const { router } = renderRoutes(routes, ['/w/acme'])
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Switch workspace' }))
      await user.click(await screen.findByRole('menuitem', { name: /Create workspace/ }))
      await user.type(await screen.findByLabelText('Workspace name'), '  Initech ')
      await user.click(screen.getByRole('button', { name: 'Create workspace' }))

      await expectWorkspaceOpen('Initech')
      expect(router.state.location.pathname).toBe('/w/initech/issues')
      expect(server.calls('POST /api/workspaces')[0]?.body).toEqual({ name: 'Initech' })
      expect(server.calls('POST /api/workspaces')[0]?.headers.get('X-XSRF-TOKEN')).toBe('test-token')
    })
  })

  describe('onboarding', () => {
    it('creates the first workspace and opens it', async () => {
      let created = false
      const server = fakeServer({
        'GET /api/me': () => json(200, ADA),
        'GET /api/workspaces': () => json(200, created ? [ACME] : []),
        'GET /api/invitations': () => json(200, []),
        'POST /api/workspaces': () => {
          created = true
          return json(201, ACME)
        },
      })
      const { router } = renderRoutes(routes, ['/onboarding'])
      const user = userEvent.setup()

      await user.type(await screen.findByLabelText('Workspace name'), 'Acme')
      await user.click(screen.getByRole('button', { name: 'Create workspace' }))

      await expectWorkspaceOpen('Acme')
      expect(router.state.location.pathname).toBe('/w/acme/issues')
      expect(server.calls('POST /api/workspaces')[0]?.body).toEqual({ name: 'Acme' })
    })

    it('does not send an empty or too long name', async () => {
      const server = fakeServer({
        'GET /api/me': () => json(200, ADA),
        'GET /api/workspaces': () => json(200, []),
        'GET /api/invitations': () => json(200, []),
      })
      renderRoutes(routes, ['/onboarding'])
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Create workspace' }))
      expect(await screen.findByText('Enter a name for the workspace')).toBeInTheDocument()

      expect(server.calls('POST /api/workspaces')).toHaveLength(0)
    })

    it('shows the server explanation when creating fails', async () => {
      fakeServer({
        'GET /api/me': () => json(200, ADA),
        'GET /api/workspaces': () => json(200, []),
        'GET /api/invitations': () => json(200, []),
        'POST /api/workspaces': () => problem(400, { detail: 'Workspace name must not contain control characters' }),
      })
      renderRoutes(routes, ['/onboarding'])
      const user = userEvent.setup()

      await user.type(await screen.findByLabelText('Workspace name'), 'Acme')
      await user.click(screen.getByRole('button', { name: 'Create workspace' }))

      expect(await screen.findByText('Workspace name must not contain control characters')).toBeInTheDocument()
    })

    it('offers the invitations addressed to the user and joins the workspace', async () => {
      let joined = false
      const server = fakeServer({
        'GET /api/me': () => json(200, ADA),
        'GET /api/workspaces': () => json(200, joined ? [GLOBEX] : []),
        'GET /api/invitations': () =>
          json(200, joined ? [] : [invitation({ id: '0198f1c0-0000-7000-8000-0000000000b1', workspaceId: GLOBEX.id, workspaceName: 'Globex', role: 'admin', invitedByName: 'Grace' })]),
        'POST /api/invitations/0198f1c0-0000-7000-8000-0000000000b1/accept': () => {
          joined = true
          return json(200, GLOBEX)
        },
      })
      const { router } = renderRoutes(routes, ['/onboarding'])
      const user = userEvent.setup()

      expect(await screen.findByText('You have been invited')).toBeInTheDocument()
      expect(screen.getByText('Globex')).toBeInTheDocument()
      expect(screen.getByText(/Invited by Grace/)).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Accept' }))

      await expectWorkspaceOpen('Globex')
      expect(router.state.location.pathname).toBe('/w/globex/issues')
      expect(server.calls('POST /api/invitations/0198f1c0-0000-7000-8000-0000000000b1/accept')).toHaveLength(1)
    })
  })

  describe('invitation link', () => {
    const invitationId = '0198f1c0-0000-7000-8000-0000000000b2'

    it('lets the invited user accept from the emailed link', async () => {
      let joined = false
      fakeServer({
        'GET /api/me': () => json(200, ADA),
        'GET /api/workspaces': () => json(200, joined ? [ACME] : []),
        'GET /api/invitations': () => json(200, joined ? [] : [invitation({ id: invitationId })]),
        [`POST /api/invitations/${invitationId}/accept`]: () => {
          joined = true
          return json(200, ACME)
        },
      })
      const { router } = renderRoutes(routes, [`/invitations/${invitationId}`])
      const user = userEvent.setup()

      expect(await screen.findByText('Join Acme')).toBeInTheDocument()
      expect(screen.getByText(/signed in as ada@example.com/)).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Accept' }))

      await expectWorkspaceOpen('Acme')
      expect(router.state.location.pathname).toBe('/w/acme/issues')
    })

    it('explains when the invitation is not available to this account', async () => {
      fakeServer({
        'GET /api/me': () => json(200, ADA),
        'GET /api/workspaces': () => json(200, []),
        'GET /api/invitations': () => json(200, []),
      })
      renderRoutes(routes, [`/invitations/${invitationId}`])

      expect(await screen.findByText('This invitation is not available')).toBeInTheDocument()
      expect(screen.getByText(/different email address than ada@example.com/)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Accept' })).not.toBeInTheDocument()
    })

    it('brings someone who signs in from the emailed link back to the invitation, not to the start page', async () => {
      let signedIn = false
      fakeServer({
        'GET /api/me': () => (signedIn ? json(200, ADA) : problem(401, {})),
        'GET /api/workspaces': () => json(200, []),
        'GET /api/invitations': () => json(200, [invitation({ id: invitationId })]),
        'POST /api/auth/code': () => noContent(),
        'POST /api/auth/verify': () => {
          signedIn = true
          return json(200, ADA)
        },
      })
      const { router } = renderRoutes(routes, [`/invitations/${invitationId}`])
      const user = userEvent.setup()

      await user.type(await screen.findByLabelText('Email'), 'ada@example.com')
      await user.click(screen.getByRole('button', { name: 'Send code' }))
      await user.type(await screen.findByRole('textbox', { name: /6-digit code/i }), '123456')

      expect(await screen.findByText('Join Acme')).toBeInTheDocument()
      expect(router.state.location.pathname).toBe(`/invitations/${invitationId}`)
    })

    it('sends someone who is not signed in to the login page and brings them back', async () => {
      fakeServer({ 'GET /api/me': () => problem(401, {}) })
      const { router } = renderRoutes(routes, [`/invitations/${invitationId}`])

      expect(await screen.findByText('Sign in to Orchestragent')).toBeInTheDocument()
      expect(router.state.location.pathname).toBe('/login')
      expect(router.state.location.state).toEqual({ from: `/invitations/${invitationId}` })
    })
  })
})
