import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <main className="mx-auto flex min-h-svh max-w-md flex-col justify-center gap-3 p-6">
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <p className="text-muted-foreground">The page you are looking for does not exist.</p>
      <Link to="/" className="text-sm underline underline-offset-4">
        Go to the start page
      </Link>
    </main>
  )
}
