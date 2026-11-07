import { Navigate, type RouteObject } from 'react-router'
import { AuthGuard } from '@/features/auth/components/AuthGuard'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { AgentsPage } from '@/features/agent/pages/AgentsPage'
import { RuntimesPage } from '@/features/agent/pages/RuntimesPage'
import { InboxPage } from '@/features/inbox/pages/InboxPage'
import { IssueDetailPage } from '@/features/issue/pages/IssueDetailPage'
import { IssuesPage } from '@/features/issue/pages/IssuesPage'
import { TokensPage } from '@/features/tokens/pages/TokensPage'
import { UsagePage } from '@/features/usage/pages/UsagePage'
import { AcceptInvitationPage } from '@/features/workspace/pages/AcceptInvitationPage'
import { MembersPage } from '@/features/workspace/pages/MembersPage'
import { OnboardingPage } from '@/features/workspace/pages/OnboardingPage'
import { RootRedirectPage } from '@/features/workspace/pages/RootRedirectPage'
import { WorkspaceHomePage } from '@/features/workspace/pages/WorkspaceHomePage'
import { WorkspaceLayout } from '@/features/workspace/pages/WorkspaceLayout'
import { NotFoundPage } from './NotFoundPage'
import { RouteErrorPage } from './RouteErrorPage'

/** Kept separate from the router instance so tests can mount it in a memory router. */
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage />, errorElement: <RouteErrorPage /> },
  {
    element: <AuthGuard />,
    errorElement: <RouteErrorPage />,
    children: [
      { path: '/', element: <RootRedirectPage /> },
      { path: '/onboarding', element: <OnboardingPage /> },
      { path: '/invitations/:invitationId', element: <AcceptInvitationPage /> },
      {
        path: '/w/:slug',
        element: <WorkspaceLayout />,
        children: [
          { index: true, element: <WorkspaceHomePage /> },
          { path: 'inbox', element: <InboxPage /> },
          { path: 'issues', element: <IssuesPage /> },
          { path: 'issues/:number', element: <IssueDetailPage /> },
          { path: 'agents', element: <AgentsPage /> },
          { path: 'runtimes', element: <RuntimesPage /> },
          { path: 'usage', element: <UsagePage /> },
          { path: 'settings', element: <Navigate to="members" replace /> },
          { path: 'settings/members', element: <MembersPage /> },
          { path: 'settings/tokens', element: <TokensPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
]
