import { useNavigate } from 'react-router'
import { UserMenu } from '@/features/auth/components/UserMenu'
import { TopBar } from '@/shared/layout/TopBar'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { CreateWorkspaceFormView } from '../components/CreateWorkspaceForm'
import { InvitationCard } from '../components/InvitationCard'
import { useMyInvitations } from '../hooks'

/** First screen for someone without a workspace: accept an invitation, or create one. */
export function OnboardingPage() {
  const navigate = useNavigate()
  const invitations = useMyInvitations()
  const pending = invitations.data ?? []

  return (
    <div className="min-h-svh bg-muted">
      <TopBar actions={<UserMenu />} />
      <main className="mx-auto flex max-w-[480px] flex-col gap-6 px-6 py-10">
        {pending.length > 0 && (
          <Card className="gap-4 [--card-spacing:--spacing(6)]">
            <CardHeader>
              <CardTitle>You have been invited</CardTitle>
              <CardDescription>Join an existing workspace.</CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col gap-2">
              {pending.map((invitation) => (
                <InvitationCard key={invitation.id} invitation={invitation} />
              ))}
            </CardContent>
          </Card>
        )}
        <Card className="gap-4 [--card-spacing:--spacing(6)]">
          <CardHeader>
            <CardTitle>{pending.length > 0 ? 'Or create your own workspace' : 'Create your workspace'}</CardTitle>
            <CardDescription>A workspace is where your team and its agents work on the same issues.</CardDescription>
          </CardHeader>
          <CardContent>
            <CreateWorkspaceFormView onCreated={(workspace) => void navigate(`/w/${workspace.slug}`)} />
          </CardContent>
        </Card>
      </main>
    </div>
  )
}
