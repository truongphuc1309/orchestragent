import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ActorAvatar } from '@/features/issue/components/ActorAvatar'
import { stubImagesAsLoaded } from '@/test/images'
import { UserAvatar } from './UserAvatar'

const URL = '/api/users/0198f1c0-0000-7000-8000-000000000001/avatar/0198f1c0-0000-7000-8000-0000000000f1'

describe('UserAvatar', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the picture once it has loaded', () => {
    stubImagesAsLoaded()
    const { container } = render(<UserAvatar name="Ada Lovelace" avatarUrl={URL} />)

    expect(container.querySelector('img')).toHaveAttribute('src', URL)
    expect(screen.queryByText('AD')).not.toBeInTheDocument()
  })

  it('shows the initials without a picture', () => {
    stubImagesAsLoaded()
    const { container } = render(<UserAvatar name="Ada Lovelace" avatarUrl={null} />)

    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByText('AD')).toBeInTheDocument()
  })

  it('shows the initials while the picture loads', () => {
    const { container } = render(<UserAvatar name="Ada Lovelace" avatarUrl={URL} />)

    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByText('AD')).toBeInTheDocument()
  })
})

describe('ActorAvatar', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the picture of a member', () => {
    stubImagesAsLoaded()
    const { container } = render(
      <ActorAvatar actor={{ type: 'member', id: '0198f1c0-0000-7000-8000-000000000001', name: 'Ada', avatarUrl: URL }} />,
    )

    expect(container.querySelector('img')).toHaveAttribute('src', URL)
  })

  it('never shows a picture for an agent or the system', () => {
    stubImagesAsLoaded()
    const agent = render(
      <ActorAvatar actor={{ type: 'agent', id: '0198f1c0-0000-7000-8000-000000000002', name: 'Bot', avatarUrl: URL }} />,
    )
    const system = render(<ActorAvatar actor={{ type: 'system', id: null, name: 'System', avatarUrl: URL }} />)

    expect(agent.container.querySelector('img')).toBeNull()
    expect(system.container.querySelector('img')).toBeNull()
    expect(system.getByText('S')).toBeInTheDocument()
  })
})
