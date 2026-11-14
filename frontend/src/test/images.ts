import { vi } from 'vitest'

/**
 * jsdom never loads images, so avatar pictures would always show their fallback. With this stub every image counts
 * as loaded at once (Radix's Avatar checks `complete` and `naturalWidth`). `vi.unstubAllGlobals()` removes it.
 */
export function stubImagesAsLoaded() {
  class LoadedImage extends EventTarget {
    complete = true
    naturalWidth = 64
    src = ''
    crossOrigin: string | null = null
    referrerPolicy = ''
  }
  vi.stubGlobal('Image', LoadedImage)
}
