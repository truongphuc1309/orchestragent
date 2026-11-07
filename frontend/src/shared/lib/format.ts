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

/**
 * How long ago something happened, in a few words: "just now", "5 min ago", "3 h ago", "2 d ago". A time in the
 * future (a clock that is slightly off) counts as "just now"; something that is not a date shows an em dash.
 */
export function formatAgo(iso: string, now: number = Date.now()): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) {
    return '—'
  }
  const seconds = Math.max(0, Math.floor((now - then) / 1000))
  if (seconds < 45) {
    return 'just now'
  }
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) {
    return `${minutes} min ago`
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return `${hours} h ago`
  }
  return `${Math.floor(hours / 24)} d ago`
}

/** First two letters of a name, for avatar fallbacks. */
export function initials(name: string): string {
  return name.trim().slice(0, 2).toUpperCase() || '?'
}
