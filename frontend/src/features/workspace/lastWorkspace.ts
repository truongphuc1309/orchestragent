import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface LastWorkspaceState {
  /** Slug of the workspace the user opened last, so signing in again returns there. */
  lastSlug: string | null
  remember: (slug: string) => void
  forget: () => void
}

export const useLastWorkspace = create<LastWorkspaceState>()(
  persist(
    (set) => ({
      lastSlug: null,
      remember: (slug) => set({ lastSlug: slug }),
      forget: () => set({ lastSlug: null }),
    }),
    { name: 'orchestragent.lastWorkspace' },
  ),
)
