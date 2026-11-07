import { useNavigate } from 'react-router'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { CreateWorkspaceFormView } from './CreateWorkspaceForm'

export function CreateWorkspaceDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const navigate = useNavigate()

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create a workspace</DialogTitle>
          <DialogDescription>A workspace is where your team and its agents work on the same issues.</DialogDescription>
        </DialogHeader>
        <CreateWorkspaceFormView
          onCreated={(workspace) => {
            onOpenChange(false)
            void navigate(`/w/${workspace.slug}`)
          }}
        />
      </DialogContent>
    </Dialog>
  )
}
