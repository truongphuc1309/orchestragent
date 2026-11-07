import { Navigate, Outlet, useLocation } from 'react-router'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { useMe } from '../hooks'

/** Route layout: shows the nested routes to signed-in users and sends everyone else to the login page. */
export function AuthGuard() {
  const me = useMe()
  const location = useLocation()

  if (me.isPending) {
    return (
      <div className="mx-auto flex min-h-svh max-w-md flex-col justify-center gap-3 p-6" aria-busy="true">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-4 w-64" />
        <span className="sr-only">Loading…</span>
      </div>
    )
  }

  if (me.isError) {
    return (
      <div className="mx-auto flex min-h-svh max-w-md flex-col items-start justify-center gap-3 p-6" role="alert">
        <h1 className="text-lg font-medium">Could not reach the server</h1>
        <p className="text-sm text-muted-foreground">Check your connection and try again.</p>
        <Button onClick={() => void me.refetch()}>Try again</Button>
      </div>
    )
  }

  if (me.data === null) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }

  return <Outlet />
}
