import { Check, Copy } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import type { CreatedApiToken } from '../schemas'

/**
 * Shows a freshly created token. This is the only time the secret is visible: the server stores just a hash.
 * The secret lives in this component's props and is dropped as soon as the dialog closes.
 */
export function NewTokenDialog({ created, onClose }: { created: CreatedApiToken | null; onClose: () => void }) {
  const [copied, setCopied] = useState(false)

  const copy = async () => {
    if (created === null) {
      return
    }
    try {
      await navigator.clipboard.writeText(created.secret)
      setCopied(true)
    } catch {
      toast.error('Could not copy automatically. Select the token and copy it by hand.')
    }
  }

  return (
    <Dialog
      open={created !== null}
      onOpenChange={(open) => {
        if (!open) {
          setCopied(false)
          onClose()
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Your new token</DialogTitle>
          <DialogDescription>
            Copy it now and keep it safe. For your security it will not be shown again.
          </DialogDescription>
        </DialogHeader>
        <div className="flex items-center gap-2">
          <code
            aria-label="New API token"
            className="min-w-0 flex-1 rounded-md border bg-muted px-2 py-1.5 font-mono text-xs break-all select-all"
          >
            {created?.secret}
          </code>
          <Button variant="secondary" onClick={() => void copy()} aria-label="Copy token">
            {copied ? <Check /> : <Copy />}
            {copied ? 'Copied' : 'Copy'}
          </Button>
        </div>
        <DialogFooter>
          <Button onClick={onClose}>Done</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
