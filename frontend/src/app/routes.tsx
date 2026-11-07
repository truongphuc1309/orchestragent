import type { RouteObject } from 'react-router'
import { AuthGuard } from '@/features/auth/components/AuthGuard'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { HomePage } from '@/features/home/pages/HomePage'
import { NotFoundPage } from './NotFoundPage'

/** Kept separate from the router instance so tests can mount it in a memory router. */
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <AuthGuard />,
    children: [{ path: '/', element: <HomePage /> }],
  },
  { path: '*', element: <NotFoundPage /> },
]
