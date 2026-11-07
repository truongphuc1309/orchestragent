import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderRoutes } from '@/test/render'
import { RouteErrorPage } from './RouteErrorPage'

function Broken(): never {
  throw new Error('secret internal detail')
}

describe('RouteErrorPage', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('replaces a page that crashes with a friendly message and a way out', () => {
    renderRoutes([{ path: '/', element: <Broken />, errorElement: <RouteErrorPage /> }])

    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong')
    expect(screen.getByRole('button', { name: 'Reload the page' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to the start page' })).toHaveAttribute('href', '/')
  })

  it('never shows the text of the error to the user', () => {
    renderRoutes([{ path: '/', element: <Broken />, errorElement: <RouteErrorPage /> }])

    expect(screen.queryByText(/secret internal detail/)).not.toBeInTheDocument()
  })
})
