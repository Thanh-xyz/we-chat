import { expect, test } from '@playwright/test'

const email = process.env.E2E_EMAIL
const password = process.env.E2E_PASSWORD
const userAEmail = process.env.E2E_USER_A_EMAIL
const userAPassword = process.env.E2E_USER_A_PASSWORD
const userBEmail = process.env.E2E_USER_B_EMAIL
const userBPassword = process.env.E2E_USER_B_PASSWORD
const conversationId = process.env.E2E_CONVERSATION_ID

async function login(page, identifier, secret) {
  await page.goto('/login')
  await page.getByLabel('Email hoặc tên người dùng').fill(identifier)
  await page.getByLabel('Mật khẩu').fill(secret)
  await page.getByRole('button', { name: 'Đăng nhập' }).click()
  await expect(page).toHaveURL(/\/app(?:\/|$)/)
}

test('gateway serves the SPA entry point and direct login route', async ({ page }) => {
  await page.goto('/login')
  await expect(page).toHaveTitle(/ChatSpace/i)
  await expect(page.getByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
})

test('single user can login, open the chat shell, and logout', async ({ page }) => {
  test.skip(!email || !password, 'Set E2E_EMAIL and E2E_PASSWORD for the authenticated smoke test.')
  await login(page, email, password)
  await expect(page.getByText('ChatSpace')).toBeVisible()
  await page.getByRole('button', { name: 'Đăng xuất' }).click()
  await expect(page).toHaveURL(/\/login$/)
})

test('two authenticated browsers receive one realtime message', async ({ browser }) => {
  test.skip(
    !(userAEmail && userAPassword && userBEmail && userBPassword && conversationId),
    'Set both user credentials and E2E_CONVERSATION_ID for the two-user runtime test.',
  )

  const contextA = await browser.newContext()
  const contextB = await browser.newContext()
  const pageA = await contextA.newPage()
  const pageB = await contextB.newPage()
  const content = `e2e-${Date.now()}`

  try {
    await login(pageA, userAEmail, userAPassword)
    await login(pageB, userBEmail, userBPassword)
    await pageA.goto(`/app/chat/${conversationId}`)
    await pageB.goto(`/app/chat/${conversationId}`)
    await expect(pageA.getByLabel('Nội dung tin nhắn')).toBeVisible()
    await expect(pageB.getByLabel('Nội dung tin nhắn')).toBeVisible()

    await pageA.getByLabel('Nội dung tin nhắn').fill(content)
    await pageA.getByRole('button', { name: 'Gửi tin nhắn' }).click()
    await expect(pageB.getByText(content, { exact: true })).toBeVisible({ timeout: 20_000 })
    await expect(pageB.getByText(content, { exact: true })).toHaveCount(1)
  } finally {
    await Promise.all([contextA.close(), contextB.close()])
  }
})
