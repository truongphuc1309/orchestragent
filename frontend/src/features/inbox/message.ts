import type { Notification } from './schemas'

/** Failure reasons the daemon and the server report, in words. */
const REASONS: Record<string, string> = {
  agent_error: 'the agent reported an error',
  timeout: 'it ran out of time',
  idle_timeout: 'it stopped responding',
  runtime_offline: 'its computer went offline',
  daemon_stopped: 'the daemon on its computer was stopped',
  provider_unavailable: 'the AI tool is not installed on its computer',
  provider_error: 'the AI tool crashed',
  workdir_error: 'its work folder could not be prepared',
}

export function failureReason(code: string | null): string {
  if (code === null || code === '') {
    return REASONS.agent_error ?? ''
  }
  return REASONS[code] ?? code.replaceAll('_', ' ')
}

/** What happened, in one sentence, with the issue key filled in: "Mia assigned you ACM-12". */
export function notificationMessage(notification: Notification, key: string): string {
  switch (notification.type) {
    case 'issue_assigned':
      return `${notification.actorName} assigned you ${key}`
    case 'agent_completed':
      return `${notification.actorName} finished ${key}: it is waiting for your review`
    case 'agent_failed':
      return `${notification.actorName} could not finish ${key}: ${failureReason(notification.detail)}`
  }
}
