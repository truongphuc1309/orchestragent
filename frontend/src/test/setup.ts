import '@testing-library/jest-dom/vitest'
import { toast } from 'sonner'
import { vi } from 'vitest'
import { useLastWorkspace } from '@/features/workspace/lastWorkspace'
import { FakeEventSource } from './fakeEventSource'

// jsdom lacks a few browser APIs that Radix UI and input-otp touch.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver = ResizeObserverStub

// sonner (toasts) asks the browser for the colour scheme.
window.matchMedia = (query: string): MediaQueryList => ({
  matches: false,
  media: query,
  onchange: null,
  addEventListener: () => {},
  removeEventListener: () => {},
  addListener: () => {},
  removeListener: () => {},
  dispatchEvent: () => false,
})

Element.prototype.scrollIntoView = () => {}
Element.prototype.hasPointerCapture = () => false
Element.prototype.releasePointerCapture = () => {}
document.elementFromPoint = () => null

// State that outlives a component (persisted store, local storage) must not leak from one test to the next.
beforeEach(() => {
  localStorage.clear()
  useLastWorkspace.setState({ lastSlug: null })
  // jsdom has no EventSource; the live-updates stream of a workspace talks to this one. Stubbed again for
  // every test because tests call vi.unstubAllGlobals().
  vi.stubGlobal('EventSource', FakeEventSource)
  FakeEventSource.reset()
  // Toasts live in a global store, so one still on screen from the previous test would show up in this one.
  toast.dismiss()
})
