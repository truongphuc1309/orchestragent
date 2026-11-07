import { Badge } from '@/shared/ui/badge'
import { ROLE_LABEL } from '../permissions'
import type { Role } from '../schemas'

const VARIANT = { owner: 'info', admin: 'neutral', member: 'outline' } as const

export function RoleBadge({ role }: { role: Role }) {
  return <Badge variant={VARIANT[role]}>{ROLE_LABEL[role]}</Badge>
}
