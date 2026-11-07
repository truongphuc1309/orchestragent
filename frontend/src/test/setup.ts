import '@testing-library/jest-dom/vitest'

// jsdom lacks a few browser APIs that Radix UI and input-otp touch.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver = ResizeObserverStub

Element.prototype.scrollIntoView = () => {}
Element.prototype.hasPointerCapture = () => false
Element.prototype.releasePointerCapture = () => {}
document.elementFromPoint = () => null
