import { Link, useParams } from 'react-router'
import { UserMenu } from '@/features/auth/components/UserMenu'
import { useMe } from '@/features/auth/hooks'
import { TopBar } from '@/shared/layout/TopBar'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Skeleton } from '@/shared/ui/skeleton'
import { InvitationCard } from '../components/InvitationCard'
import { useMyInvitations } from '../hooks'

/** `/invitations/:invitationId`: the page the invitation email links to. */
export function AcceptInvitationPage() {
  const { invitationId } = useParams()
  const me = useMe()
  const invitations = useMyInvitations()
  const invitation = invitations.data?.find((candidate) => candidate.id === invitationId)

  return (
    <div className="min-h-svh bg-muted">
      <TopBar actions={<UserMenu />} />
      <main className="mx-auto max-w-[480px] px-6 py-10">
        <Card className="gap-4 [--card-spacing:--spacing(6)]">
          {invitations.isPending ? (
            <CardContent aria-busy="true">
              <Skeleton className="h-12 w-full" />
              <span className="sr-only">Loading…</span>
            </CardContent>
          ) : invitations.isError ? (
            <CardHeader role="alert">
              <CardTitle>Could not load the invitation</CardTitle>
              <CardDescription>Check your connection and reload the page.</CardDescription>
            </CardHeader>
          ) : invitation ? (
            <>
              <CardHeader>
                <CardTitle>Join {invitation.workspaceName}</CardTitle>
                <CardDescription>You are signed in as {me.data?.email}.</CardDescription>
              </CardHeader>
              <CardContent>
                <InvitationCard invitation={invitation} />
              </CardContent>
            </>
          ) : (
            <>
              <CardHeader>
                <CardTitle>This invitation is not available</CardTitle>
                <CardDescription>
                  It may have been withdrawn or already used, or it was sent to a different email address than{' '}
                  {me.data?.email ?? 'yours'}. Sign out and sign in with the invited address to use it.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <Link to="/">Go to your workspaces</Link>
              </CardContent>
            </>
          )}
        </Card>
      </main>
    </div>
  )
}
