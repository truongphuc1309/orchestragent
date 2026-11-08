import { Archive, ArchiveRestore, Pencil, Plus } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { useMe } from '@/features/auth/hooks'
import { useWorkspace } from '@/features/workspace/workspaceContext'
import { messageOf } from '@/shared/api/errors'
import { ConfirmDialog } from '@/shared/layout/ConfirmDialog'
import { formatDate } from '@/shared/lib/format'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { ProjectFormDialog } from '../components/ProjectFormDialog'
import { useArchiveProject, useProjects, useRestoreProject } from '../hooks'
import { canManageProject } from '../permissions'
import type { Project } from '../schemas'

/** The projects of a workspace: create, rename, archive and restore them. */
export function ProjectsPage() {
  const workspace = useWorkspace()
  const me = useMe()
  const navigate = useNavigate()
  const [showArchived, setShowArchived] = useState(false)
  const projects = useProjects(workspace.id, showArchived)
  const archive = useArchiveProject(workspace.id)
  const restore = useRestoreProject(workspace.id)
  // `null` while closed; a new project is `{ project: null }`.
  const [editing, setEditing] = useState<{ project: Project | null } | null>(null)
  const [archiving, setArchiving] = useState<Project | null>(null)

  const confirmArchive = () => {
    if (archiving === null) {
      return
    }
    const target = archiving
    archive.mutate(target.id, {
      onSuccess: () => {
        setArchiving(null)
        toast.success(`${target.name} was archived`)
      },
      onError: (error) => {
        setArchiving(null)
        toast.error(messageOf(error, 'Could not archive the project.'))
      },
    })
  }

  return (
    <div className="flex max-w-4xl flex-col gap-4">
      <div className="flex items-center gap-3">
        <div className="flex flex-1 flex-col gap-1">
          <h1 className="text-2xl leading-7 font-medium">Projects</h1>
          <p className="text-muted-foreground">
            Every issue of {workspace.name} belongs to a project, and its key starts with the project key.
          </p>
        </div>
        <Button variant="secondary" aria-pressed={showArchived} onClick={() => setShowArchived((shown) => !shown)}>
          {showArchived ? 'Hide archived' : 'Show archived'}
        </Button>
        <Button onClick={() => setEditing({ project: null })}>
          <Plus />
          Create project
        </Button>
      </div>

      {projects.isPending ? (
        <div className="flex flex-col gap-2" aria-busy="true">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <span className="sr-only">Loading projects…</span>
        </div>
      ) : projects.isError ? (
        <div role="alert" className="flex items-center gap-3 text-sm text-destructive">
          Could not load the projects.
          <Button variant="secondary" size="sm" onClick={() => void projects.refetch()}>
            Try again
          </Button>
        </div>
      ) : projects.data.length === 0 ? (
        <p className="rounded-md border border-dashed p-6 text-center text-muted-foreground">
          {showArchived ? 'There are no projects yet.' : 'No projects yet. Create the first one.'}
        </p>
      ) : (
        <div className="relative overflow-x-auto rounded-md border">
          <table className="w-full text-left text-sm" aria-label="Projects">
            <thead className="border-b bg-muted text-xs text-muted-foreground uppercase">
              <tr>
                <th scope="col" className="w-28 px-3 py-2 font-semibold">
                  Key
                </th>
                <th scope="col" className="px-3 py-2 font-semibold">
                  Name
                </th>
                <th scope="col" className="w-40 px-3 py-2 font-semibold">
                  Created by
                </th>
                <th scope="col" className="w-32 px-3 py-2 font-semibold">
                  Created
                </th>
                <th scope="col" className="w-28 px-3 py-2 font-semibold">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {projects.data.map((project) => {
                const mayManage = canManageProject(workspace.role, me.data?.id === project.createdById)
                return (
                  <tr key={project.id} className="hover:bg-accent">
                    <td className="px-3 py-2 font-medium whitespace-nowrap">{project.key}</td>
                    <td className="px-3 py-2">
                      <span className="flex items-center gap-2">
                        <Link to={`/w/${workspace.slug}/projects/${project.key}`} className="text-foreground">
                          {project.name}
                        </Link>
                        {project.archived && <Badge variant="default">Archived</Badge>}
                      </span>
                      {project.description && (
                        <span className="line-clamp-1 text-muted-foreground">{project.description}</span>
                      )}
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">{project.createdByName}</td>
                    <td className="px-3 py-2 whitespace-nowrap text-muted-foreground">{formatDate(project.createdAt)}</td>
                    <td className="px-3 py-2">
                      {mayManage && (
                        <span className="flex justify-end gap-1">
                          {project.archived ? (
                            <Button
                              variant="ghost"
                              size="icon"
                              aria-label={`Restore ${project.name}`}
                              disabled={restore.isPending}
                              onClick={() =>
                                restore.mutate(project.id, {
                                  onSuccess: () => toast.success(`${project.name} was restored`),
                                  onError: (error) => toast.error(messageOf(error, 'Could not restore the project.')),
                                })
                              }
                            >
                              <ArchiveRestore />
                            </Button>
                          ) : (
                            <>
                              <Button
                                variant="ghost"
                                size="icon"
                                aria-label={`Edit ${project.name}`}
                                onClick={() => setEditing({ project })}
                              >
                                <Pencil />
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                aria-label={`Archive ${project.name}`}
                                onClick={() => setArchiving(project)}
                              >
                                <Archive />
                              </Button>
                            </>
                          )}
                        </span>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      <ProjectFormDialog
        workspace={workspace}
        project={editing?.project ?? null}
        open={editing !== null}
        onOpenChange={(open) => !open && setEditing(null)}
        onCreated={(created) => void navigate(`/w/${workspace.slug}/projects/${created.key}`)}
      />
      <ConfirmDialog
        open={archiving !== null}
        onOpenChange={(open) => !open && setArchiving(null)}
        title={`Archive ${archiving?.name ?? 'this project'}?`}
        description="No new issues can be created in it. Its issues stay as they are and remain visible, and its key is never reused. You can restore it later."
        confirmLabel="Archive"
        pending={archive.isPending}
        onConfirm={confirmArchive}
      />
    </div>
  )
}
