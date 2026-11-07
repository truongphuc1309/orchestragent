import { UserMenu } from '@/features/auth/components/UserMenu'
import { useMe } from '@/features/auth/hooks'
import { TopBar } from '@/shared/layout/TopBar'

/** Placeholder landing page until workspaces arrive in step 4. */
export function HomePage() {
  const me = useMe()

  return (
    <div className="min-h-svh bg-background">
      <TopBar actions={<UserMenu />} />
      <main className="mx-auto flex max-w-3xl flex-col gap-2 px-6 py-10">
        <h1 className="text-2xl leading-7 font-medium">Welcome{me.data ? `, ${me.data.name}` : ''}</h1>
        <p className="text-muted-foreground">
          You are signed in as <strong className="font-semibold text-foreground">{me.data?.email}</strong>. Workspaces
          and issues are coming next.
        </p>
      </main>
    </div>
  )
}
