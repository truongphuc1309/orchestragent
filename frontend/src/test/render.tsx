import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider, type InitialEntry, type RouteObject } from 'react-router'
import { Toaster } from '@/shared/ui/sonner'

export function renderRoutes(routes: RouteObject[], initialEntries: InitialEntry[] = ['/']) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(routes, { initialEntries })
  const view = render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <Toaster />
    </QueryClientProvider>,
  )
  return { ...view, router, queryClient }
}
