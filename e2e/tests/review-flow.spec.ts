import { expect, test, type BrowserContext, type Page } from '@playwright/test'
import { startMockDaemon, type RunningDaemon } from '../support/daemon'
import { LOGIN_CODE } from '../support/env'

/**
 * The whole product in one story: a person signs in, makes a workspace, connects a computer, creates an agent
 * and gives it an issue. The mock provider does the work, the issue lands in review, the person sends it back
 * once, then approves it.
 */
test('a person and an agent finish an issue together', async ({ page, context }) => {
  const email = `e2e-${Date.now()}@example.com`
  let daemon: RunningDaemon | undefined

  try {
    await test.step('sign in with the email code', async () => {
      await page.goto('/login')
      await page.getByLabel('Email').fill(email)
      await page.getByRole('button', { name: 'Send code' }).click()
      await page.getByLabel('6-digit code').pressSequentially(LOGIN_CODE)
      await expect(page).toHaveURL(/\/onboarding$/)
    })

    let base = ''
    await test.step('create a workspace', async () => {
      await page.getByLabel('Workspace name').fill('E2E Team')
      await page.getByRole('button', { name: 'Create workspace' }).click()
      await expect(page).toHaveURL(/\/w\/[^/]+/)
      base = new URL(page.url()).pathname.split('/').slice(0, 3).join('/')
    })

    await test.step('connect a computer (the daemon with the mock provider)', async () => {
      const token = await createApiToken(context)
      daemon = startMockDaemon(token)

      await page.goto(`${base}/runtimes`)
      // The daemon needs a few seconds to start; the page does not poll, so look again until it is there.
      await expect(async () => {
        await page.reload()
        await expect(page.getByText('Online').first()).toBeVisible({ timeout: 2_000 })
      }).toPass({ timeout: 60_000 })
      await expect(page.getByText('e2e-computer').first()).toBeVisible()
    })

    await test.step('create an agent on that computer', async () => {
      await page.goto(`${base}/agents`)
      await page.getByRole('button', { name: 'Create agent' }).click()
      await page.getByLabel('Name').fill('Mock Writer')
      await page.getByLabel('Instructions').fill('Write what the issue asks for and keep it short.')
      await page.getByRole('button', { name: 'Create', exact: true }).click()
      await expect(page.getByText('Mock Writer').first()).toBeVisible()
    })

    await test.step('create an issue assigned to the agent', async () => {
      await page.goto(`${base}/issues`)
      await page.getByRole('button', { name: 'Create issue' }).click()
      await page.getByLabel('Title').fill('Write a greeting')
      await page.getByLabel('Description').fill('Say hello to the team.')
      await page.getByRole('combobox', { name: 'Assignee' }).click()
      await page.getByRole('option', { name: /Mock Writer/ }).click()
      await page.getByRole('button', { name: 'Create', exact: true }).click()
      await page.getByRole('link', { name: /Write a greeting/ }).first().click()
      await expect(page.getByRole('heading', { name: 'Write a greeting' })).toBeVisible()
    })

    await test.step('the agent works and the issue waits for review', async () => {
      await waitForReview(page)
      const runs = runCards(page)
      await expect(runs).toHaveCount(1)
      await expect(runs.first()).toContainText('Completed')
      await expect(page.getByRole('list', { name: 'Comments' })).toContainText('Mock Writer')
    })

    await test.step('send it back with a comment, and the agent works again', async () => {
      await page.getByRole('button', { name: 'Request changes' }).click()
      await page.getByLabel('What should change?').fill('Please also say goodbye.')
      await page.getByRole('button', { name: 'Send back' }).click()

      const runs = runCards(page)
      await expect(runs).toHaveCount(2)
      await waitForReview(page)
      await expect(runs.first()).toContainText('Completed')
    })

    await test.step('approve: the issue is done', async () => {
      await page.getByRole('button', { name: 'Approve' }).click()
      await expect(page.getByRole('combobox', { name: 'Status' })).toContainText('Done')
      await expect(page.getByText('Waiting for your review')).toBeHidden()
    })

    await test.step('the inbox told the creator about the finished runs', async () => {
      await page.goto(`${base}/inbox`)
      await expect(page.getByRole('link', { name: /Write a greeting/ }).first()).toBeVisible()
    })
  } finally {
    daemon?.stop()
  }
})

/** One card per run; the log lines inside a card are list items too, so only direct children count. */
function runCards(page: Page) {
  return page.getByRole('list', { name: 'Agent runs' }).locator('> li')
}

/** The issue page updates by itself (server-sent events); no reload is needed to see the review bar. */
async function waitForReview(page: Page): Promise<void> {
  await expect(page.getByText('Waiting for your review')).toBeVisible({ timeout: 60_000 })
  await expect(page.getByRole('combobox', { name: 'Status' })).toContainText('In review')
}

/** Creates an API token for the signed-in user, the way the settings page does, and returns its secret. */
async function createApiToken(context: BrowserContext): Promise<string> {
  const cookies = await context.cookies()
  const csrf = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value
  expect(csrf, 'the browser has a CSRF cookie after signing in').toBeTruthy()

  const response = await context.request.post('/api/me/tokens', {
    data: { name: 'e2e daemon' },
    headers: { 'X-XSRF-TOKEN': csrf ?? '' },
  })
  expect(response.status()).toBe(201)
  const body: unknown = await response.json()
  const secret = typeof body === 'object' && body !== null && 'secret' in body ? body.secret : undefined
  if (typeof secret !== 'string') {
    throw new Error('The token response has no secret')
  }
  return secret
}
