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

/** "45s", "3m 05s", "1h 02m" for a length of time in milliseconds; an em dash when it is not a usable number. */
export function formatDuration(milliseconds: number): string {
  if (!Number.isFinite(milliseconds) || milliseconds < 0) {
    return '—'
  }
  const totalSeconds = Math.floor(milliseconds / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  if (hours > 0) {
    return `${hours}h ${String(minutes).padStart(2, '0')}m`
  }
  if (minutes > 0) {
    return `${minutes}m ${String(seconds).padStart(2, '0')}s`
  }
  return `${seconds}s`
}

/** "950", "1.2k", "3.4M": a token count in a few characters. */
export function formatTokens(count: number): string {
  const short = (value: number, unit: string) => `${value.toFixed(1).replace(/\.0$/, '')}${unit}`
  if (count < 1000) {
    return String(count)
  }
  // Just below a million, one decimal of thousands would round up to "1000k": call that a million instead.
  return count < 999_950 ? short(count / 1000, 'k') : short(count / 1_000_000, 'M')
}

/** "$0.05", or "<$0.01" for a cost too small to show. */
export function formatCost(usd: number): string {
  if (usd > 0 && usd < 0.01) {
    return '<$0.01'
  }
  return `$${usd.toFixed(2)}`
}

/** First two letters of a name, for avatar fallbacks. */
export function initials(name: string): string {
  return name.trim().slice(0, 2).toUpperCase() || '?'
}
