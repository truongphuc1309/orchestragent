import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { messageOf } from '@/shared/api/errors'
import { ConfirmDialog } from '@/shared/layout/ConfirmDialog'
import { formatDate } from '@/shared/lib/format'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Skeleton } from '@/shared/ui/skeleton'
import { NewTokenDialog } from '../components/NewTokenDialog'
import { useCreateToken, useRevokeToken, useTokens } from '../hooks'
import { createTokenFormSchema, MAX_TOKEN_NAME, type ApiToken, type CreatedApiToken, type CreateTokenForm } from '../schemas'

/** Settings → API tokens: tokens that let the daemon on your computer connect to Orchestragent as you. */
export function TokensPage() {
  const tokens = useTokens()
  const create = useCreateToken()
  const revoke = useRevokeToken()
  const [created, setCreated] = useState<CreatedApiToken | null>(null)
  const [revoking, setRevoking] = useState<ApiToken | null>(null)
  const form = useForm<CreateTokenForm>({ resolver: zodResolver(createTokenFormSchema), defaultValues: { name: '' } })
  const nameError = form.formState.errors.name

  const submit = form.handleSubmit(({ name }) =>
    create.mutate(name, {
      onSuccess: (result) => {
        setCreated(result)
        form.reset({ name: '' })
      },
    }),
  )

  const confirmRevoke = () => {
    if (revoking === null) {
      return
    }
    const target = revoking
    revoke.mutate(target.id, {
      onSuccess: () => {
        setRevoking(null)
        toast.success(`Token "${target.name}" revoked`)
      },
      onError: (error) => {
        setRevoking(null)
        toast.error(messageOf(error, 'Could not revoke the token.'))
      },
    })
  }

  return (
    <div className="flex max-w-3xl flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl leading-7 font-medium">API tokens</h1>
        <p className="text-muted-foreground">
          A token lets the Orchestragent daemon on your computer act as you. Treat it like a password.
        </p>
      </div>

      <section className="flex flex-col gap-3" aria-labelledby="create-token-heading">
        <h2 id="create-token-heading" className="text-base font-semibold">
          Create a token
        </h2>
        <form onSubmit={submit} noValidate className="flex flex-col gap-2">
          <div className="flex items-start gap-2">
            <div className="flex flex-1 flex-col gap-1">
              <Label htmlFor="token-name" className="sr-only">
                Token name
              </Label>
              <Input
                id="token-name"
                autoComplete="off"
                placeholder="Work laptop"
                maxLength={MAX_TOKEN_NAME}
                aria-invalid={nameError !== undefined}
                {...form.register('name')}
              />
            </div>
            <Button type="submit" disabled={create.isPending}>
              {create.isPending ? 'Creating…' : 'Create token'}
            </Button>
          </div>
          {nameError && (
            <p role="alert" className="text-sm text-destructive">
              {nameError.message}
            </p>
          )}
          {create.error && (
            <p role="alert" className="text-sm text-destructive">
              {messageOf(create.error, 'Could not create the token. Please try again.')}
            </p>
          )}
        </form>
      </section>

      <section className="flex flex-col gap-3" aria-labelledby="your-tokens-heading">
        <h2 id="your-tokens-heading" className="text-base font-semibold">
          Your tokens
        </h2>
        {tokens.isPending ? (
          <div className="flex flex-col gap-2" aria-busy="true">
            <Skeleton className="h-12 w-full" />
            <span className="sr-only">Loading tokens…</span>
          </div>
        ) : tokens.isError ? (
          <div role="alert" className="flex items-center gap-3 text-sm text-destructive">
            Could not load your tokens.
            <Button variant="secondary" size="sm" onClick={() => void tokens.refetch()}>
              Try again
            </Button>
          </div>
        ) : tokens.data.length === 0 ? (
          <p className="text-sm text-muted-foreground">You have no tokens yet.</p>
        ) : (
          <ul className="divide-y rounded-md border" aria-label="Tokens">
            {tokens.data.map((token) => (
              <li key={token.id} className="flex items-center gap-3 px-3 py-2">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{token.name}</p>
                  <p className="text-xs text-muted-foreground">
                    <code className="font-mono">{token.tokenPrefix}…</code> · Created {formatDate(token.createdAt)} ·
                    Last used {token.lastUsedAt === null ? 'never' : formatDate(token.lastUsedAt)}
                  </p>
                </div>
                {token.revokedAt === null ? (
                  <>
                    <Badge variant="success">Active</Badge>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-destructive"
                      aria-label={`Revoke ${token.name}`}
                      onClick={() => setRevoking(token)}
                    >
                      Revoke
                    </Button>
                  </>
                ) : (
                  <Badge variant="neutral">Revoked</Badge>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <NewTokenDialog created={created} onClose={() => setCreated(null)} />
      <ConfirmDialog
        open={revoking !== null}
        onOpenChange={(open) => !open && setRevoking(null)}
        title={`Revoke "${revoking?.name ?? ''}"?`}
        description="Anything using this token stops working immediately. This cannot be undone."
        confirmLabel="Revoke token"
        destructive
        pending={revoke.isPending}
        onConfirm={confirmRevoke}
      />
    </div>
  )
}
