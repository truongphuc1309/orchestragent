import { useState } from 'react'
import { Button } from '@/shared/ui/button'
import { Textarea } from '@/shared/ui/textarea'
import { MAX_DESCRIPTION } from '../schemas'

interface Props {
  description: string
  saving: boolean
  onSave: (description: string) => void
}

/** The issue description: click to edit, then Save or Cancel. Line breaks are kept. */
export function InlineDescription({ description, saving, onSave }: Props) {
  const [draft, setDraft] = useState<string | null>(null)

  if (draft === null) {
    return (
      <button
        type="button"
        onClick={() => setDraft(description)}
        aria-label="Edit description"
        className="w-full rounded-md px-2 py-1.5 text-left hover:bg-accent"
      >
        {description === '' ? (
          <span className="text-muted-foreground">Add a description…</span>
        ) : (
          <span className="block whitespace-pre-wrap break-words">{description}</span>
        )}
      </button>
    )
  }

  const tooLong = draft.length > MAX_DESCRIPTION
  return (
    <div className="flex flex-col gap-2">
      <Textarea
        autoFocus
        aria-label="Description"
        aria-invalid={tooLong}
        rows={6}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
      />
      {tooLong && (
        <p role="alert" className="text-sm text-destructive">
          Use at most {MAX_DESCRIPTION} characters
        </p>
      )}
      <div className="flex gap-2">
        <Button
          disabled={saving || tooLong}
          onClick={() => {
            if (draft.trim() !== description) {
              onSave(draft)
            }
            setDraft(null)
          }}
        >
          Save
        </Button>
        <Button variant="ghost" onClick={() => setDraft(null)}>
          Cancel
        </Button>
      </div>
    </div>
  )
}
