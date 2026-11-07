import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { ACME, ADA, invitation, member } from '@/test/fixtures'
import { fakeServer, json, noContent, problem } from '@/test/fakeServer'
import { renderRoutes } from '@/test/render'
import { roleSchema, type Invitation, type Member, type Role } from '../schemas'

const OLIVIA = '0198f1c0-0000-7000-8000-0000000000c1'
const ADAM = '0198f1c0-0000-7000-8000-0000000000c2'
const MIA = '0198f1c0-0000-7000-8000-0000000000c3'
const ANN = '0198f1c0-0000-7000-8000-0000000000c4'
const membersPath = `/api/workspaces/${ACME.id}/members`
const invitationsPath = `/api/workspaces/${ACME.id}/invitations`

/** Mounts the members page as `myRole`, with ADA (the signed-in user) listed among the members. */
function setup(myRole: Role, others: Member[], initialInvitations: Invitation[] = []) {
  let members: Member[] = [member({ userId: ADA.id, name: 'ada', role: myRole }), ...others]
  let pending = initialInvitations
  let left = false
  const server = fakeServer({
    'GET /api/me': () => json(200, ADA),
    'GET /api/workspaces': () => json(200, left ? [] : [{ ...ACME, role: myRole }]),
    [`GET ${membersPath}`]: () => json(200, members),
    [`GET ${invitationsPath}`]: () => json(200, pending),
    [`POST ${invitationsPath}`]: (request) => {
      const body = request.body
      const created = invitation({
        id: '0198f1c0-0000-7000-8000-0000000000d1',
        email: typeof body === 'object' && body !== null && 'email' in body ? String(body.email) : '',
      })
      pending = [created, ...pending]
      return json(201, created)
    },
    [`DELETE ${invitationsPath}/0198f1c0-0000-7000-8000-0000000000d1`]: () => {
      pending = []
      return noContent()
    },
    ...Object.fromEntries(
      [OLIVIA, ADAM, MIA, ANN, ADA.id].flatMap((id) => [
        [
          `PATCH ${membersPath}/${id}`,
          (request: { body: unknown }) => {
            const body = request.body
            const role = roleSchema.parse(typeof body === 'object' && body !== null && 'role' in body ? body.role : undefined)
            members = members.map((m) => (m.userId === id ? { ...m, role } : m))
            return noContent()
          },
        ],
        [
          `DELETE ${membersPath}/${id}`,
          () => {
            members = members.filter((m) => m.userId !== id)
            if (id === ADA.id) {
              left = true
            }
            return noContent()
          },
        ],
      ]),
    ),
  })
  const view = renderRoutes(routes, ['/w/acme/settings/members'])
  return { server, ...view, setPending: (next: Invitation[]) => (pending = next) }
}

describe('MembersPage', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  describe('what each role is shown', () => {
    it('shows a plain member the list but no way to manage anything', async () => {
      const { server } = setup('member', [member({ userId: OLIVIA, name: 'Olivia', role: 'owner' })])

      expect(await screen.findByRole('heading', { name: 'Members (2)' })).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'Invite people' })).not.toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'Pending invitations' })).not.toBeInTheDocument()
      expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /^Remove / })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Leave Acme' })).toBeInTheDocument()
      expect(screen.getByText('(you)')).toBeInTheDocument()
      expect(within(screen.getByRole('list', { name: 'Members' })).getByText('Owner')).toBeInTheDocument()
      // The invitations are not even requested: they are none of a member's business.
      expect(server.calls(`GET ${invitationsPath}`)).toHaveLength(0)
    })

    it('gives an owner every control', async () => {
      setup('owner', [member({ userId: MIA, name: 'Mia' })])

      expect(await screen.findByRole('heading', { name: 'Invite people' })).toBeInTheDocument()
      expect(await screen.findByRole('heading', { name: 'Pending invitations' })).toBeInTheDocument()
      expect(screen.getByRole('combobox', { name: 'Role of Mia' })).toBeInTheDocument()
      expect(screen.getByRole('combobox', { name: 'Role of ada' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Remove Mia' })).toBeInTheDocument()
    })

    it('lets an admin manage members, but not owners or other admins', async () => {
      setup('admin', [
        member({ userId: OLIVIA, name: 'Olivia', role: 'owner' }),
        member({ userId: ANN, name: 'Ann', role: 'admin' }),
        member({ userId: MIA, name: 'Mia' }),
      ])
      const user = userEvent.setup()

      await screen.findByRole('heading', { name: 'Members (4)' })
      expect(screen.getByRole('combobox', { name: 'Role of Mia' })).toBeInTheDocument()
      expect(screen.queryByRole('combobox', { name: 'Role of Olivia' })).not.toBeInTheDocument()
      expect(screen.queryByRole('combobox', { name: 'Role of Ann' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Remove Mia' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Remove Olivia' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Remove Ann' })).not.toBeInTheDocument()

      // An admin cannot hand out the owner role, neither to members nor in an invitation.
      await user.click(screen.getByRole('combobox', { name: 'Role of Mia' }))
      expect(await screen.findAllByRole('option')).toHaveLength(2)
      expect(screen.queryByRole('option', { name: 'Owner' })).not.toBeInTheDocument()
    })

    it('does not offer the owner role in invitations to an admin', async () => {
      setup('admin', [])
      const user = userEvent.setup()

      await user.click(await screen.findByRole('combobox', { name: 'Role for the invitation' }))

      expect(await screen.findByRole('option', { name: 'Admin' })).toBeInTheDocument()
      expect(screen.getByRole('option', { name: 'Member' })).toBeInTheDocument()
      expect(screen.queryByRole('option', { name: 'Owner' })).not.toBeInTheDocument()
    })
  })

  describe('inviting', () => {
    it('invites by email and lists the pending invitation', async () => {
      const { server } = setup('owner', [])
      const user = userEvent.setup()

      await user.type(await screen.findByLabelText('Email address'), '  ivy@example.com ')
      await user.click(screen.getByRole('button', { name: 'Invite' }))

      expect(await screen.findByText('Invitation sent to ivy@example.com')).toBeInTheDocument()
      const list = await screen.findByRole('list', { name: 'Pending invitations' })
      expect(within(list).getByText('ivy@example.com')).toBeInTheDocument()
      expect(server.calls(`POST ${invitationsPath}`)[0]?.body).toEqual({ email: 'ivy@example.com', role: 'member' })
      expect(server.calls(`POST ${invitationsPath}`)[0]?.headers.get('X-XSRF-TOKEN')).toBe('test-token')
      expect(screen.getByLabelText('Email address')).toHaveValue('')
    })

    it('checks the email before sending anything', async () => {
      const { server } = setup('owner', [])
      const user = userEvent.setup()

      await user.type(await screen.findByLabelText('Email address'), 'not-an-email')
      await user.click(screen.getByRole('button', { name: 'Invite' }))

      expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument()
      expect(server.calls(`POST ${invitationsPath}`)).toHaveLength(0)
    })

    it('shows the explanation when the server refuses', async () => {
      const refuse = fakeServer({
        'GET /api/me': () => json(200, ADA),
        'GET /api/workspaces': () => json(200, [ACME]),
        [`GET ${membersPath}`]: () => json(200, [member({ userId: ADA.id, name: 'ada', role: 'owner' })]),
        [`GET ${invitationsPath}`]: () => json(200, []),
        [`POST ${invitationsPath}`]: () =>
          problem(409, { code: 'invitation.already_member', detail: 'This person is already a member' }),
      })
      renderRoutes(routes, ['/w/acme/settings/members'])
      const user = userEvent.setup()

      await user.type(await screen.findByLabelText('Email address'), 'mia@example.com')
      await user.click(screen.getByRole('button', { name: 'Invite' }))

      expect(await screen.findByText('This person is already a member')).toBeInTheDocument()
      expect(refuse.calls(`POST ${invitationsPath}`)).toHaveLength(1)
    })

    it('withdraws a pending invitation after confirmation', async () => {
      const { server } = setup('owner', [], [invitation({ id: '0198f1c0-0000-7000-8000-0000000000d1' })])
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Withdraw invitation to ivy@example.com' }))
      await user.click(await screen.findByRole('button', { name: 'Withdraw invitation' }))

      expect(await screen.findByText('No pending invitations.')).toBeInTheDocument()
      expect(server.calls(`DELETE ${invitationsPath}/0198f1c0-0000-7000-8000-0000000000d1`)).toHaveLength(1)
    })
  })

  describe('changing roles and removing', () => {
    it('changes a role from the dropdown', async () => {
      const { server } = setup('owner', [member({ userId: MIA, name: 'Mia' })])
      const user = userEvent.setup()

      await user.click(await screen.findByRole('combobox', { name: 'Role of Mia' }))
      await user.click(await screen.findByRole('option', { name: 'Admin' }))

      expect(await screen.findByText('Mia is now admin')).toBeInTheDocument()
      expect(server.calls(`PATCH ${membersPath}/${MIA}`)[0]?.body).toEqual({ role: 'admin' })
      await waitFor(() => expect(screen.getByRole('combobox', { name: 'Role of Mia' })).toHaveTextContent('Admin'))
    })

    it('shows the server explanation when a role change is refused', async () => {
      fakeServer({
        'GET /api/me': () => json(200, ADA),
        'GET /api/workspaces': () => json(200, [ACME]),
        [`GET ${membersPath}`]: () => json(200, [member({ userId: ADA.id, name: 'ada', role: 'owner' })]),
        [`GET ${invitationsPath}`]: () => json(200, []),
        [`PATCH ${membersPath}/${ADA.id}`]: () =>
          problem(409, { code: 'workspace.last_owner', detail: 'A workspace must keep at least one owner' }),
      })
      renderRoutes(routes, ['/w/acme/settings/members'])
      const user = userEvent.setup()

      await user.click(await screen.findByRole('combobox', { name: 'Role of ada' }))
      await user.click(await screen.findByRole('option', { name: 'Member' }))

      expect(await screen.findByText('A workspace must keep at least one owner')).toBeInTheDocument()
    })

    it('removes a member only after confirmation', async () => {
      const { server } = setup('owner', [member({ userId: MIA, name: 'Mia' })])
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Remove Mia' }))
      expect(await screen.findByText('Remove Mia?')).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Cancel' }))
      await waitFor(() => expect(screen.queryByText('Remove Mia?')).not.toBeInTheDocument())
      expect(server.calls(`DELETE ${membersPath}/${MIA}`)).toHaveLength(0)

      await user.click(screen.getByRole('button', { name: 'Remove Mia' }))
      await user.click(await screen.findByRole('button', { name: 'Remove member' }))

      expect(await screen.findByText('Mia was removed')).toBeInTheDocument()
      expect(server.calls(`DELETE ${membersPath}/${MIA}`)).toHaveLength(1)
      await waitFor(() => expect(screen.queryByText('Mia')).not.toBeInTheDocument())
    })

    it('lets a member leave and then lands on onboarding', async () => {
      const { server, router } = setup('member', [member({ userId: OLIVIA, name: 'Olivia', role: 'owner' })])
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name: 'Leave Acme' }))
      expect(await screen.findByText('Leave Acme?')).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Leave workspace' }))

      await waitFor(() => expect(router.state.location.pathname).toBe('/onboarding'))
      expect(server.calls(`DELETE ${membersPath}/${ADA.id}`)).toHaveLength(1)
    })
  })

  describe('loading', () => {
    it('offers a retry when the members cannot be loaded', async () => {
      let healthy = false
      fakeServer({
        'GET /api/me': () => json(200, ADA),
        'GET /api/workspaces': () => json(200, [{ ...ACME, role: 'member' }]),
        [`GET ${membersPath}`]: () =>
          healthy ? json(200, [member({ userId: ADA.id, name: 'ada' })]) : problem(500, {}),
      })
      renderRoutes(routes, ['/w/acme/settings/members'])
      const user = userEvent.setup()

      expect(await screen.findByText('Could not load the members.')).toBeInTheDocument()
      healthy = true
      await user.click(screen.getByRole('button', { name: 'Try again' }))

      expect(await screen.findByRole('heading', { name: 'Members (1)' })).toBeInTheDocument()
    })
  })
})
