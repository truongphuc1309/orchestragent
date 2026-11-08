import { z } from 'zod'
import { apiRequest } from '@/shared/api/client'
import { projectSchema, type Project, type ProjectForm } from './schemas'

const base = (workspaceId: string) => `/api/workspaces/${workspaceId}/projects`

/** Active projects ordered by key, and archived ones too when {@code includeArchived} is set. */
export function listProjects(workspaceId: string, includeArchived: boolean, signal?: AbortSignal): Promise<Project[]> {
  const query = includeArchived ? '?includeArchived=true' : ''
  return apiRequest(`${base(workspaceId)}${query}`, { schema: z.array(projectSchema), signal })
}

export function getProjectByKey(workspaceId: string, key: string, signal?: AbortSignal): Promise<Project> {
  return apiRequest(`${base(workspaceId)}/key/${encodeURIComponent(key)}`, { schema: projectSchema, signal })
}

export function createProject(workspaceId: string, form: ProjectForm): Promise<Project> {
  return apiRequest(base(workspaceId), {
    method: 'POST',
    body: { key: form.key, name: form.name, description: form.description },
    schema: projectSchema,
  })
}

/** Changes the name and description; the key of a project never changes. */
export function updateProject(
  workspaceId: string,
  projectId: string,
  changes: { name: string; description: string },
): Promise<Project> {
  return apiRequest(`${base(workspaceId)}/${projectId}`, { method: 'PATCH', body: changes, schema: projectSchema })
}

export function archiveProject(workspaceId: string, projectId: string): Promise<Project> {
  return apiRequest(`${base(workspaceId)}/${projectId}/archive`, { method: 'POST', schema: projectSchema })
}

export function restoreProject(workspaceId: string, projectId: string): Promise<Project> {
  return apiRequest(`${base(workspaceId)}/${projectId}/restore`, { method: 'POST', schema: projectSchema })
}
