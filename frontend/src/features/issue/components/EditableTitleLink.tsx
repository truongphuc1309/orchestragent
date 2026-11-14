import { Pencil } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { cn } from '@/shared/lib/utils'
import { Input } from '@/shared/ui/input'
import { MAX_TITLE, titleSchema } from '../schemas'

interface Props {
  title: string
  to: string
  /** Called with the new, trimmed title; not called when nothing changed or the title is invalid. */
  onSave: (title: string) => void
  /** Classes of the link that shows the title (how many lines, how big). */
  linkClassName?: string
}

/**
 * An issue title on a card or in a table row: the link opens the issue, the pencil next to it edits the title in
 * place. Enter or leaving the field saves, Escape cancels.
 */
export function EditableTitleLink({ title, to, onSave, linkClassName }: Props) {
  const [draft, setDraft] = useState<string | null>(null)

  if (draft === null) {
    return (
      <span className="group/title flex items-start gap-1">
        <Link
          to={to}
          className={cn('min-w-0 flex-1 text-foreground no-underline hover:no-underline', linkClassName)}
          draggable={false}
        >
          {title}
        </Link>
        <button
          type="button"
          aria-label="Edit title"
          onClick={() => setDraft(title)}
          className="mt-0.5 shrink-0 rounded-sm p-0.5 text-muted-foreground opacity-0 outline-none group-focus-within/title:opacity-100 group-hover/title:opacity-100 hover:bg-muted focus-visible:ring-1 focus-visible:ring-ring"
        >
          <Pencil className="size-3.5" aria-hidden />
        </button>
      </span>
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
    // A press inside the field must not start dragging the card it sits on.
    <span className="flex flex-col gap-1" onPointerDown={(event) => event.stopPropagation()}>
      <Input
        autoFocus
        aria-label="Title"
        aria-invalid={!parsed.success}
        value={draft}
        maxLength={MAX_TITLE + 50}
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
        <span role="alert" className="text-xs text-destructive">
          {parsed.error.issues[0]?.message}
        </span>
      )}
    </span>
  )
}
