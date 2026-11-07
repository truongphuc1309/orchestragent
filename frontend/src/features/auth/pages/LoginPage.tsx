import { zodResolver } from '@hookform/resolvers/zod'
import { REGEXP_ONLY_DIGITS } from 'input-otp'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '@/shared/api/errors'
import { Logo } from '@/shared/layout/Logo'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { InputOTP, InputOTPGroup, InputOTPSlot } from '@/shared/ui/input-otp'
import { Label } from '@/shared/ui/label'
import { useMe, useRequestLoginCode, useVerifyLoginCode } from '../hooks'
import { redirectTarget } from '../redirect'
import { CODE_LENGTH, emailFormSchema, type EmailForm } from '../schemas'
import { useCooldown } from '../useCooldown'

const RESEND_SECONDS = 60

export function LoginPage() {
  const me = useMe()
  const [email, setEmail] = useState<string | null>(null)

  if (me.data) {
    return <Navigate to="/" replace />
  }

  return (
    <main className="flex min-h-svh items-center justify-center bg-muted p-6">
      <Card className="w-full max-w-[400px] gap-6 [--card-spacing:--spacing(8)]">
        <CardHeader className="justify-items-center text-center">
          <Logo className="mb-4" />
          <CardTitle>Sign in to Orchestragent</CardTitle>
          <CardDescription>
            {email === null ? 'We will email you a one-time code.' : `Enter the code we sent to ${email}.`}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {email === null ? (
            <EmailStep onCodeSent={setEmail} />
          ) : (
            <CodeStep email={email} onChangeEmail={() => setEmail(null)} />
          )}
        </CardContent>
      </Card>
    </main>
  )
}

function EmailStep({ onCodeSent }: { onCodeSent: (email: string) => void }) {
  const requestCode = useRequestLoginCode()
  const form = useForm<EmailForm>({ resolver: zodResolver(emailFormSchema), defaultValues: { email: '' } })

  const submit = form.handleSubmit(({ email }) => {
    requestCode.mutate(email, { onSuccess: () => onCodeSent(email) })
  })

  const error = requestCode.error
  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <Label htmlFor="email">Email</Label>
        <Input
          id="email"
          type="email"
          autoComplete="email"
          autoFocus
          placeholder="you@example.com"
          className="h-10"
          aria-invalid={form.formState.errors.email !== undefined}
          {...form.register('email')}
        />
        {form.formState.errors.email && (
          <p role="alert" className="text-sm text-destructive">
            {form.formState.errors.email.message}
          </p>
        )}
      </div>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {codeRequestMessage(error)}
        </p>
      )}
      <Button type="submit" size="lg" disabled={requestCode.isPending}>
        {requestCode.isPending ? 'Sending…' : 'Send code'}
      </Button>
    </form>
  )
}

function CodeStep({ email, onChangeEmail }: { email: string; onChangeEmail: () => void }) {
  const [code, setCode] = useState('')
  const verify = useVerifyLoginCode()
  const resend = useRequestLoginCode()
  const cooldown = useCooldown()
  const startCooldown = cooldown.start
  const navigate = useNavigate()
  const location = useLocation()

  // The code was just sent by the step before, so the resend button starts out waiting.
  useEffect(() => startCooldown(RESEND_SECONDS), [startCooldown])

  const submit = (value: string) => {
    if (value.length !== CODE_LENGTH || verify.isPending) {
      return
    }
    verify.mutate(
      { email, code: value },
      {
        onSuccess: () => void navigate(redirectTarget(location.state), { replace: true }),
        onError: () => setCode(''),
      },
    )
  }

  const resendCode = () => {
    resend.mutate(email, {
      onSuccess: () => cooldown.start(RESEND_SECONDS),
      onError: (error) => {
        if (error instanceof ApiError && error.retryAfterSeconds !== undefined) {
          cooldown.start(error.retryAfterSeconds)
        }
      },
    })
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col items-center gap-2">
        <Label htmlFor="code" className="sr-only">
          6-digit code
        </Label>
        <InputOTP
          id="code"
          maxLength={CODE_LENGTH}
          pattern={REGEXP_ONLY_DIGITS}
          value={code}
          onChange={setCode}
          onComplete={submit}
          disabled={verify.isPending}
          autoFocus
          aria-label="6-digit code"
        >
          <InputOTPGroup>
            {Array.from({ length: CODE_LENGTH }, (_, index) => (
              <InputOTPSlot key={index} index={index} />
            ))}
          </InputOTPGroup>
        </InputOTP>
      </div>
      {verify.error && (
        <p role="alert" className="text-center text-sm text-destructive">
          {verifyMessage(verify.error)}
        </p>
      )}
      {resend.error && !(resend.error instanceof ApiError && resend.error.status === 429) && (
        <p role="alert" className="text-center text-sm text-destructive">
          {codeRequestMessage(resend.error)}
        </p>
      )}
      <Button size="lg" onClick={() => submit(code)} disabled={code.length !== CODE_LENGTH || verify.isPending}>
        {verify.isPending ? 'Checking…' : 'Sign in'}
      </Button>
      <div className="flex items-center justify-between text-sm">
        <Button variant="link" size="sm" className="px-0" onClick={onChangeEmail}>
          Use a different email
        </Button>
        <Button variant="link" size="sm" className="px-0" disabled={cooldown.remaining > 0 || resend.isPending} onClick={resendCode}>
          {cooldown.remaining > 0 ? `Resend code (${cooldown.remaining}s)` : 'Resend code'}
        </Button>
      </div>
    </div>
  )
}

function codeRequestMessage(error: Error): string {
  if (error instanceof ApiError) {
    if (error.status === 429) {
      const wait = error.retryAfterSeconds
      return wait === undefined
        ? 'A code was sent recently. Please wait a moment before asking for another.'
        : `A code was sent recently. You can ask for another in ${wait} seconds.`
    }
    if (error.status === 400) {
      return 'That email address is not valid.'
    }
  }
  return 'Could not send the code. Please try again.'
}

function verifyMessage(error: Error): string {
  if (error instanceof ApiError && error.status === 401) {
    return 'That code is wrong or has expired. Check the email, or ask for a new code.'
  }
  return 'Could not check the code. Please try again.'
}
