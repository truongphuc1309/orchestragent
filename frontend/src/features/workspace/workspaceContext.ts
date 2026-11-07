import { createContext, useContext } from 'react'
import type { Workspace } from './schemas'

export const WorkspaceContext = createContext<Workspace | null>(null)

/** The workspace of the current `/w/:slug` route. Only usable below the workspace layout. */
export function useWorkspace(): Workspace {
  const workspace = useContext(WorkspaceContext)
  if (workspace === null) {
    throw new Error('useWorkspace must be used inside a workspace route')
  }
  return workspace
}
