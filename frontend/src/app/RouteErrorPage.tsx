import { isRouteErrorResponse, Link, useRouteError } from 'react-router'
import { Button } from '@/shared/ui/button'

/**
 * Shown when a page crashes while rendering (a bug, not a failed request: those have their own message on
 * each page). The error itself goes to the console for developers and is never shown to the user.
 */
export function RouteErrorPage() {
  const error = useRouteError()
  console.error('Page crashed', isRouteErrorResponse(error) ? error.statusText : error)

  return (
    <main role="alert" className="mx-auto flex min-h-svh max-w-md flex-col justify-center gap-3 p-6">
      <h1 className="text-2xl font-semibold">Something went wrong</h1>
      <p className="text-muted-foreground">
        This page could not be shown. Reload it, or go back to the start page. If it keeps happening, tell us what
        you were doing.
      </p>
      <div className="flex items-center gap-3">
        <Button onClick={() => window.location.reload()}>Reload the page</Button>
        <Link to="/" className="text-sm underline underline-offset-4">
          Go to the start page
        </Link>
      </div>
    </main>
  )
}
