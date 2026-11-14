import { zodResolver } from '@hookform/resolvers/zod'
import { useRef, useState, type ChangeEvent } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { useMe } from '@/features/auth/hooks'
import type { User } from '@/features/auth/schemas'
import { messageOf } from '@/shared/api/errors'
import { UserAvatar } from '@/shared/components/UserAvatar'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Skeleton } from '@/shared/ui/skeleton'
import { Textarea } from '@/shared/ui/textarea'
import { useRemoveAvatar, useUpdateProfile, useUploadAvatar } from '../hooks'
import { AVATAR_TYPES, avatarFileProblem, MAX_BIO, MAX_NAME, profileFormSchema, type ProfileForm } from '../schemas'

/** Settings → Profile: the photo, name and "About you" that other people see in every workspace. */
export function ProfilePage() {
  const me = useMe()

  return (
    <div className="flex max-w-3xl flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl leading-7 font-medium">Profile</h1>
        <p className="text-muted-foreground">How other people see you, in every workspace you belong to.</p>
      </div>
      {me.isPending ? (
        <div className="flex flex-col gap-4" aria-busy="true" aria-label="Loading profile">
          <Skeleton className="size-20 rounded-full" />
          <Skeleton className="h-8 w-80" />
          <Skeleton className="h-24 w-full" />
        </div>
      ) : me.isError ? (
        <div role="alert" className="flex flex-col items-start gap-2">
          <p>{messageOf(me.error, 'Could not load your profile.')}</p>
          <Button variant="outline" onClick={() => void me.refetch()}>
            Try again
          </Button>
        </div>
      ) : me.data ? (
        <>
          <PhotoSection user={me.data} />
          <DetailsSection key={me.data.id} user={me.data} />
        </>
      ) : null}
    </div>
  )
}

function PhotoSection({ user }: { user: User }) {
  const upload = useUploadAvatar()
  const remove = useRemoveAvatar()
  const input = useRef<HTMLInputElement>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const busy = upload.isPending || remove.isPending

  const onFileChosen = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    // Cleared, so choosing the same file again (after an error) is a change too.
    event.target.value = ''
    if (file === undefined) {
      return
    }
    const fileProblem = avatarFileProblem(file)
    setProblem(fileProblem)
    if (fileProblem !== null) {
      return
    }
    upload.mutate(file, {
      onSuccess: () => toast.success('Photo updated'),
      onError: (error) => setProblem(messageOf(error, 'Could not upload the photo. Please try again.')),
    })
  }

  const onRemove = () => {
    setProblem(null)
    remove.mutate(undefined, {
      onSuccess: () => toast.success('Photo removed'),
      onError: (error) => setProblem(messageOf(error, 'Could not remove the photo. Please try again.')),
    })
  }

  return (
    <section className="flex flex-col gap-3" aria-labelledby="photo-heading">
      <h2 id="photo-heading" className="text-base font-semibold">
        Photo
      </h2>
      <div className="flex items-center gap-4">
        <UserAvatar name={user.name} avatarUrl={user.avatarUrl} className="size-20" />
        <div className="flex flex-col gap-2">
          <div className="flex gap-2">
            <Button variant="outline" disabled={busy} onClick={() => input.current?.click()}>
              {upload.isPending ? 'Uploading…' : user.avatarUrl === null ? 'Upload photo' : 'Change photo'}
            </Button>
            {user.avatarUrl !== null && (
              <Button variant="ghost" disabled={busy} onClick={onRemove}>
                {remove.isPending ? 'Removing…' : 'Remove photo'}
              </Button>
            )}
          </div>
          <p id="photo-hint" className="text-sm text-muted-foreground">
            PNG, JPEG or WebP, up to 2 MB.
          </p>
        </div>
        {/* The button opens this input; it is not reachable on its own. */}
        <input
          ref={input}
          type="file"
          accept={AVATAR_TYPES.join(',')}
          aria-label="Photo file"
          aria-describedby="photo-hint"
          tabIndex={-1}
          className="sr-only"
          onChange={onFileChosen}
        />
      </div>
      {problem !== null && (
        <p role="alert" className="text-sm text-destructive">
          {problem}
        </p>
      )}
    </section>
  )
}

function DetailsSection({ user }: { user: User }) {
  const update = useUpdateProfile()
  const form = useForm<ProfileForm>({
    resolver: zodResolver(profileFormSchema),
    defaultValues: { name: user.name, bio: user.bio },
  })
  const nameError = form.formState.errors.name
  const bioError = form.formState.errors.bio
  const bioLength = useWatch({ control: form.control, name: 'bio' }).length

  // A server error is about what was sent: it goes away as soon as the person edits again.
  const forgetServerError = () => {
    if (update.error) {
      update.reset()
    }
  }

  const submit = form.handleSubmit((values) =>
    update.mutate(values, {
      onSuccess: (saved) => {
        // The saved values (trimmed by the server) become the new starting point, so Save is off again.
        form.reset({ name: saved.name, bio: saved.bio })
        toast.success('Profile saved')
      },
    }),
  )

  return (
    <section className="flex flex-col gap-3" aria-labelledby="details-heading">
      <h2 id="details-heading" className="text-base font-semibold">
        Details
      </h2>
      <form onSubmit={submit} noValidate className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="profile-name">Name</Label>
          <Input
            id="profile-name"
            autoComplete="name"
            className="max-w-sm"
            maxLength={MAX_NAME}
            aria-invalid={nameError !== undefined}
            {...form.register('name', { onChange: forgetServerError })}
          />
          {nameError && (
            <p role="alert" className="text-sm text-destructive">
              {nameError.message}
            </p>
          )}
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="profile-bio">About you</Label>
          <Textarea
            id="profile-bio"
            rows={4}
            maxLength={MAX_BIO}
            placeholder="What you work on, what people can ask you about…"
            aria-invalid={bioError !== undefined}
            aria-describedby="profile-bio-count"
            {...form.register('bio', { onChange: forgetServerError })}
          />
          <p id="profile-bio-count" className="text-sm text-muted-foreground">
            {bioLength}/{MAX_BIO} characters
          </p>
          {bioError && (
            <p role="alert" className="text-sm text-destructive">
              {bioError.message}
            </p>
          )}
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="profile-email">Email</Label>
          <Input
            id="profile-email"
            className="max-w-sm"
            value={user.email}
            readOnly
            aria-readonly
            aria-describedby="profile-email-hint"
          />
          <p id="profile-email-hint" className="text-sm text-muted-foreground">
            You sign in with this address. It cannot be changed here.
          </p>
        </div>

        {update.error && (
          <p role="alert" className="text-sm text-destructive">
            {messageOf(update.error, 'Could not save your profile. Please try again.')}
          </p>
        )}
        <div>
          <Button type="submit" disabled={!form.formState.isDirty || update.isPending}>
            {update.isPending ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </form>
    </section>
  )
}
