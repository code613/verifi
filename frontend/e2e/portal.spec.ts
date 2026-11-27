import { expect, test, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

async function signIn(page: Page) {
  const email = process.env.VERIFI_DEMO_EMAIL;
  const password = process.env.VERIFI_DEMO_PASSWORD;
  if (!email || !password) {
    throw new Error('Set VERIFI_DEMO_EMAIL and VERIFI_DEMO_PASSWORD for the local test user.');
  }

  await page.goto('/');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

test('a local staff request survives a reload and stays queued', async ({ page }) => {
  const surname = 'Sample-' + randomUUID().slice(0, 8);
  await signIn(page);
  await page.getByRole('button', { name: 'New screening request' }).click();
  await page.getByRole('button', { name: 'Add a new applicant' }).click();
  await page.getByLabel('First name').fill('Morgan');
  await page.getByLabel('Last name').fill(surname);
  await page.getByLabel('Email address').fill(surname.toLowerCase() + '@example.test');
  await page.getByRole('button', { name: 'Save applicant' }).click();
  await page.getByRole('button', { name: 'Continue to package' }).click();
  await page.getByRole('radio').first().check();
  await page.getByRole('button', { name: 'Continue to authorization' }).click();
  await page.getByLabel('Authorization reference').fill('Fictional consent ' + surname);
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: 'Save authorization and review' }).click();

  const orderResponse = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/orders' && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Queue screening request' }).click();
  const savedOrder = await orderResponse;
  expect(savedOrder.status()).toBe(201);
  expect(await savedOrder.json()).toMatchObject({ status: 'QUEUED' });
  await expect(page.getByText(/Request saved and queued locally/i)).toBeVisible();

  await page.reload();
  const applicantButton = page.getByRole('button', { name: 'Morgan ' + surname, exact: true });
  await expect(applicantButton).toHaveCount(1);
  await applicantButton.click();
  await expect(page.getByRole('dialog')).toContainText(surname);
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  expect((await page.request.get('/api/orders')).status()).toBe(401);
});

test('changing applicants clears the previous consent on a narrow screen', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await signIn(page);
  await expect(page.getByRole('button', { name: 'New screening request' })).toBeVisible();
  const session = await (await page.request.get('/api/session')).json();
  const users: string[] = [];
  const suffix = randomUUID().slice(0, 8);
  for (const firstName of ['Avery', 'Jordan']) {
    const response = await page.request.post('/api/applicants', {
      headers: { 'X-CSRF-TOKEN': session.csrfToken },
      data: { firstName, lastName: 'Sample-' + suffix, email: firstName.toLowerCase() + '.' + suffix + '@example.test' },
    });
    expect(response.status()).toBe(201);
    users.push((await response.json()).id);
  }

  await page.getByRole('button', { name: 'New screening request' }).click();
  const applicant = page.getByRole('combobox', { name: 'Applicant' });
  await applicant.selectOption(users[0]);
  await page.getByRole('button', { name: 'Continue to package' }).click();
  await page.getByRole('radio').first().check();
  await page.getByRole('button', { name: 'Continue to authorization' }).click();
  await page.getByLabel('Authorization reference').fill('Fictional consent for Avery ' + suffix);
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: 'Save authorization and review' }).click();
  await expect(page.getByRole('button', { name: 'Queue screening request' })).toBeEnabled();

  for (let step = 4; step > 1; step--) {
    await page.getByRole('button', { name: 'Back', exact: true }).click();
  }
  await applicant.selectOption(users[1]);
  await page.getByRole('button', { name: 'Continue to package' }).click();
  await page.getByRole('button', { name: 'Continue to authorization' }).click();
  await expect(page.getByLabel('Authorization reference')).toHaveValue('');
  await expect(page.getByRole('checkbox')).not.toBeChecked();
  await expect(page.getByRole('button', { name: 'Save authorization and review' })).toBeDisabled();
});
