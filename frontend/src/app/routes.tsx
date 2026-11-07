import { Navigate, type RouteObject } from 'react-router'
import { AuthGuard } from '@/features/auth/components/AuthGuard'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { TokensPage } from '@/features/tokens/pages/TokensPage'
import { AcceptInvitationPage } from '@/features/workspace/pages/AcceptInvitationPage'
import { MembersPage } from '@/features/workspace/pages/MembersPage'
import { OnboardingPage } from '@/features/workspace/pages/OnboardingPage'
import { RootRedirectPage } from '@/features/workspace/pages/RootRedirectPage'
import { WorkspaceHomePage } from '@/features/workspace/pages/WorkspaceHomePage'
import { WorkspaceLayout } from '@/features/workspace/pages/WorkspaceLayout'
import { NotFoundPage } from './NotFoundPage'

/** Kept separate from the router instance so tests can mount it in a memory router. */
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <AuthGuard />,
    children: [
      { path: '/', element: <RootRedirectPage /> },
      { path: '/onboarding', element: <OnboardingPage /> },
      { path: '/invitations/:invitationId', element: <AcceptInvitationPage /> },
      {
        path: '/w/:slug',
        element: <WorkspaceLayout />,
        children: [
          { index: true, element: <WorkspaceHomePage /> },
          { path: 'settings', element: <Navigate to="members" replace /> },
          { path: 'settings/members', element: <MembersPage /> },
          { path: 'settings/tokens', element: <TokensPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
]
