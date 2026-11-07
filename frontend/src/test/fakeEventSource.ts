import type { EventSourceLike } from '@/shared/api/eventStream'

type Listener = (event: Event) => void

/**
 * A stand-in for the browser's EventSource (jsdom has none). Tests drive it by hand: open it, emit server
 * events, or fail it as a browser would. Every instance is recorded in {@link FakeEventSource.instances}.
 */
export class FakeEventSource implements EventSourceLike {
  static instances: FakeEventSource[] = []

  static reset() {
    FakeEventSource.instances = []
  }

  /** The most recent connection to this URL. */
  static latest(url: string): FakeEventSource | undefined {
    return FakeEventSource.instances.findLast((source) => source.url === url)
  }

  readonly url: string
  readyState = 0
  closed = false
  private readonly listeners = new Map<string, Listener[]>()

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  addEventListener(type: string, listener: Listener) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener])
  }

  close() {
    this.closed = true
    this.readyState = 2
  }

  open() {
    this.readyState = 1
    this.dispatch('open', new Event('open'))
  }

  /** A server event with JSON (or, for the bad-data cases, raw text) as its data. */
  emit(name: string, data: unknown) {
    this.dispatch(name, new MessageEvent(name, { data: typeof data === 'string' ? data : JSON.stringify(data) }))
  }

  /** The connection broke. {@code giveUp} is what the browser does after a bad HTTP status. */
  fail(giveUp = false) {
    this.readyState = giveUp ? 2 : 0
    this.dispatch('error', new Event('error'))
  }

  private dispatch(type: string, event: Event) {
    for (const listener of this.listeners.get(type) ?? []) {
      listener(event)
    }
  }
}
