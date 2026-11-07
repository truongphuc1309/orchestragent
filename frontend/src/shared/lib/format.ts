const dateFormat = new Intl.DateTimeFormat('en', { dateStyle: 'medium' })

/** "Jan 5, 2026" from an ISO timestamp; an em dash when there is no date. */
export function formatDate(iso: string | null): string {
  if (iso === null) {
    return '—'
  }
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '—' : dateFormat.format(date)
}

const dateTimeFormat = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' })

/** "Jan 5, 2026, 10:30 AM" from an ISO timestamp; an em dash when it is not a date. */
export function formatDateTime(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '—' : dateTimeFormat.format(date)
}

/** First two letters of a name, for avatar fallbacks. */
export function initials(name: string): string {
  return name.trim().slice(0, 2).toUpperCase() || '?'
}
