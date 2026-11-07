import { Badge } from '@/shared/ui/badge'
import type { RuntimeStatus } from '../schemas'

/** Online is green, offline is the neutral grey of a lozenge. */
export function RuntimeStatusLozenge({ status }: { status: RuntimeStatus }) {
  return <Badge variant={status === 'online' ? 'success' : 'neutral'}>{status === 'online' ? 'Online' : 'Offline'}</Badge>
}
