/**
 * Where to go after signing in. `state` is the router location state set by the guard ({ from: '/some/path' }).
 * Only plain same-app paths are accepted, so a crafted link can never send the user to another site.
 */
export function redirectTarget(state: unknown): string {
  if (typeof state !== 'object' || state === null || !('from' in state) || typeof state.from !== 'string') {
    return '/'
  }
  const from = state.from
  const isAppPath = from.startsWith('/') && !from.startsWith('//') && !from.includes('\\')
  const isLoginPage = from === '/login' || from.startsWith('/login/') || from.startsWith('/login?')
  return isAppPath && !isLoginPage ? from : '/'
}
