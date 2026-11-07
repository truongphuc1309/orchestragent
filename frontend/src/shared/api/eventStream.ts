/** What {@link connectEventStream} needs from a browser {@code EventSource}; tests pass a fake. */
export interface EventSourceLike {
  readonly readyState: number
  close(): void
  addEventListener(type: string, listener: (event: Event) => void): void
}

export type EventSourceFactory = (url: string) => EventSourceLike

export interface EventStreamOptions {
  url: string
  /** Names of the server events to listen for (for example {@code issue.updated}). */
  eventNames: readonly string[]
  /** The JSON data of an event, already parsed. Events whose data is not JSON are dropped. */
  onEvent: (name: string, data: unknown) => void
  /** The stream is back after a break, so events may have been missed: fetch fresh data. */
  onResync: () => void
  createSource?: EventSourceFactory
  /** Reconnect delays start here and double up to {@link maxDelayMs}. */
  initialDelayMs?: number
  maxDelayMs?: number
  /**
   * The connection is treated as dead when nothing arrives for this long. The server sends a heartbeat
   * event every 25 seconds, so a longer silence means the connection broke without the browser noticing
   * (a proxy that keeps the socket open, a laptop that slept).
   */
  idleTimeoutMs?: number
}

/** The keep-alive event of the server; it keeps the connection checked and is not passed on. */
export const HEARTBEAT_EVENT = 'heartbeat'

const CLOSED = 2

/**
 * Listens to a Server-Sent Events stream and keeps it alive. The browser reconnects by itself after a lost
 * connection, but gives up for good when the server answers with an error status (for example while it
 * restarts), and it never notices a connection that died silently. In both cases this function reconnects
 * with a growing delay, and asks for fresh data once the stream is back. Returns a function that stops
 * everything.
 */
export function connectEventStream(options: EventStreamOptions): () => void {
  const create: EventSourceFactory = options.createSource ?? ((url) => new EventSource(url))
  const initialDelay = options.initialDelayMs ?? 1000
  const maxDelay = options.maxDelayMs ?? 30_000
  const idleTimeout = options.idleTimeoutMs ?? 60_000

  let source: EventSourceLike | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let idleTimer: ReturnType<typeof setTimeout> | null = null
  let delay = initialDelay
  let dropped = false
  let stopped = false

  const clearIdle = () => {
    if (idleTimer !== null) {
      clearTimeout(idleTimer)
      idleTimer = null
    }
  }

  /** Gives up on this connection and tries a new one after the current delay. */
  const reconnectLater = (current: EventSourceLike) => {
    dropped = true
    clearIdle()
    current.close()
    if (!stopped) {
      reconnectTimer = setTimeout(open, delay)
      delay = Math.min(delay * 2, maxDelay)
    }
  }

  /** Something arrived, so the connection is alive: wait for the next sign of life. */
  const heard = (current: EventSourceLike) => {
    clearIdle()
    idleTimer = setTimeout(() => reconnectLater(current), idleTimeout)
  }

  function open() {
    reconnectTimer = null
    const current = create(options.url)
    source = current

    current.addEventListener('open', () => {
      delay = initialDelay
      heard(current)
      if (dropped) {
        dropped = false
        options.onResync()
      }
    })

    current.addEventListener('error', () => {
      dropped = true
      if (current.readyState === CLOSED && !stopped) {
        // The browser will not retry; do it ourselves, a little later each time.
        reconnectLater(current)
      }
    })

    current.addEventListener(HEARTBEAT_EVENT, () => heard(current))

    for (const name of options.eventNames) {
      current.addEventListener(name, (event) => {
        heard(current)
        const data = parseData(event)
        if (data !== undefined) {
          options.onEvent(name, data)
        }
      })
    }
  }

  open()

  return () => {
    stopped = true
    clearIdle()
    if (reconnectTimer !== null) {
      clearTimeout(reconnectTimer)
    }
    source?.close()
  }
}

function parseData(event: Event): unknown {
  if (!(event instanceof MessageEvent) || typeof event.data !== 'string') {
    return undefined
  }
  try {
    return JSON.parse(event.data)
  } catch {
    return undefined
  }
}
