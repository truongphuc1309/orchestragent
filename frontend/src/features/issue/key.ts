/**
 * The short code people use to talk about an issue, such as "ACM-12". The prefix comes from the first three
 * letters or digits of the workspace slug, so it needs no extra setting and never changes.
 */
export function issueKey(workspaceSlug: string, number: number): string {
  const prefix = workspaceSlug.replace(/[^a-z0-9]/gi, '').slice(0, 3).toUpperCase()
  return `${prefix || 'ISS'}-${number}`
}
