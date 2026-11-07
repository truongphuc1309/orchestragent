import { Pencil } from 'lucide-react'
import { useState } from 'react'
import { Input } from '@/shared/ui/input'
import { MAX_TITLE, titleSchema } from '../schemas'

interface Props {
  title: string
  /** Called with the new, trimmed title; not called when nothing changed or the title is invalid. */
  onSave: (title: string) => void
}

/** The issue title: click to edit, Enter or leaving the field saves, Escape cancels. */
export function InlineTitle({ title, onSave }: Props) {
  const [draft, setDraft] = useState<string | null>(null)

  if (draft === null) {
    return (
      <button
        type="button"
        onClick={() => setDraft(title)}
        aria-label="Edit title"
        className="group flex w-full items-start gap-2 rounded-md px-2 py-1 text-left hover:bg-accent"
      >
        <h1 className="flex-1 text-2xl leading-7 font-medium break-words">{title}</h1>
        <Pencil className="mt-1 size-4 shrink-0 text-muted-foreground opacity-0 group-hover:opacity-100" aria-hidden />
      </button>
    )
  }

  const parsed = titleSchema.safeParse(draft)
  const finish = (save: boolean) => {
    if (save && parsed.success && parsed.data !== title) {
      onSave(parsed.data)
    }
    setDraft(null)
  }

  return (
    <div className="flex flex-col gap-1">
      <Input
        autoFocus
        aria-label="Title"
        aria-invalid={!parsed.success}
        value={draft}
        maxLength={MAX_TITLE + 50}
        className="h-10 text-xl font-medium"
        onChange={(event) => setDraft(event.target.value)}
        onBlur={() => finish(true)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault()
            finish(true)
          } else if (event.key === 'Escape') {
            finish(false)
          }
        }}
      />
      {!parsed.success && (
        <p role="alert" className="text-sm text-destructive">
          {parsed.error.issues[0]?.message}
        </p>
      )}
    </div>
  )
}
