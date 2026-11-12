// 验证三个列表的查询条件、标签和展示；接口全部模拟，不截图。
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const assert = require('node:assert/strict')
const http = require('node:http')
const fs = require('node:fs/promises')
const path = require('node:path')

const dist = path.resolve(__dirname, '../dist')
const server = http.createServer(async (req, res) => {
  const pathname = new URL(req.url, 'http://localhost').pathname
  const file = path.resolve(dist, '.' + (pathname === '/' ? '/index.html' : pathname))
  if (!file.startsWith(dist + path.sep)) { res.writeHead(403).end(); return }
  try {
    const content = await fs.readFile(file)
    res.setHeader('Content-Type', ({ '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream')
    res.end(content)
  } catch { res.writeHead(404).end() }
})

const progress = { cycleNo: 1, successfulReviewCount: 3, failedReviewCount: 2, netSuccessCount: 1,
  baseTargetSuccessCount: 6, extraTargetSuccessCount: 3, targetSuccessCount: 9,
  originalQuestionCount: 1, originalPassedCount: 0, retryQuestionCount: 1, pendingQuestionCount: 0 }
const card = (id, name, date, status = 'ACTIVE') => ({ id, userErrorTypeId: id, errorTypeId: 1,
  errorTypeCode: 'GRAMMAR', errorTypeName: 'Grammar', userErrorTypeName: name, userErrorTypeDescription: '', status,
  dueAt: status === 'MASTERED' ? null : date, masteredAt: status === 'MASTERED' ? date : null,
  lastReviewedAt: '2026-01-01T07:00:00', progress })
const due = [card(1, 'due-priority', '2020-01-01T07:00:00'), card(2, 'due-next', '2020-01-01T07:00:00')]
const waiting = [card(3, 'waiting-near', '2099-01-01T07:00:00'), card(4, 'waiting-later', '2099-01-02T07:00:00')]
const mastered = [card(5, 'mastered-recent', '2026-01-02T07:00:00', 'MASTERED'), card(6, 'mastered-old', '2026-01-01T07:00:00', 'MASTERED')]

async function main() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const browser = await chromium.launch({ channel: process.env.SPEECH_TEST_BROWSER || 'chrome', headless: true })
  let checks = 0
  try {
    for (const mode of ['ZH_TO_JA', 'EN_TO_JA']) {
      const context = await browser.newContext()
      await context.addInitScript(value => localStorage.setItem('sazare.learningMode', value), mode)
      const page = await context.newPage()
      page.setDefaultTimeout(60_000)
      const errors = []
      const requests = []
      page.on('pageerror', error => errors.push(error.message))
      page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
      await page.route('**/api/**', async route => {
        const url = new URL(route.request().url())
        assert.equal(route.request().method(), 'GET')
        let data = { items: [], page: 1, size: 20, total: 0 }
        if (url.pathname === '/api/questions/random') data = []
        if (url.pathname === '/api/review-cards') {
          requests.push(Object.fromEntries(url.searchParams))
          const list = url.searchParams.get('status') === 'MASTERED' ? mastered
            : url.searchParams.get('waitingOnly') === 'true' ? waiting
              : url.searchParams.get('dueOnly') === 'true' ? due : [...due, ...waiting]
          const pageNumber = Number(url.searchParams.get('page'))
          const size = Number(url.searchParams.get('size'))
          data = { items: list.slice((pageNumber - 1) * size, pageNumber * size), total: list.length, page: pageNumber, size }
        }
        await route.fulfill({ json: { code: 0, message: 'success', data } })
      })
      const names = () => page.locator('.review-card-table tbody tr td:first-child strong').allTextContents()
      await page.goto(`http://127.0.0.1:${server.address().port}`)
      await page.locator('.top-nav').getByRole('button', { name: /^(复习卡片|Review)$/ }).click()
      await page.getByText('due-priority', { exact: true }).waitFor()
      assert.deepEqual(await names(), ['due-priority', 'due-next'])
      assert.equal(requests.at(-1).dueOnly, 'true')
      assert.equal(requests.at(-1).waitingOnly, 'false')
      assert.deepEqual(await page.locator('.review-list-modes button').allTextContents(),
        mode === 'ZH_TO_JA' ? ['待复习', '进行中', '已掌握'] : ['Due', 'In progress', 'Mastered'])
      checks++

      await page.locator('.review-list-modes').getByRole('button', { name: /^(进行中|In progress)$/ }).click()
      await page.getByText('waiting-near', { exact: true }).waitFor()
      assert.deepEqual(await names(), ['waiting-near', 'waiting-later'])
      assert.equal(requests.at(-1).dueOnly, 'false')
      assert.equal(requests.at(-1).waitingOnly, 'true')
      assert.equal(requests.at(-1).status, 'ACTIVE')
      assert.match(await page.locator('.pagination-summary').innerText(), /1-2\s*\/\s*2/)
      assert.equal(await page.locator('.review-card-actions').getByRole('button', { name: /^(开始复习|Start review)$/ }).count(), 0)
      assert.equal(await page.locator('.review-card-actions').getByRole('button', { name: /^(提前复习|Review early)$/ }).count(), 2)
      checks++

      await page.locator('.review-list-modes').getByRole('button', { name: /^(已掌握|Mastered)$/ }).click()
      await page.getByText('mastered-recent', { exact: true }).waitFor()
      assert.deepEqual(await names(), ['mastered-recent', 'mastered-old'])
      assert.equal(requests.at(-1).status, 'MASTERED')
      assert.equal(requests.at(-1).waitingOnly, 'false')
      checks++

      const metadata = page.waitForResponse(response => {
        const url = new URL(response.url())
        return url.pathname === '/api/review-cards' && url.searchParams.get('size') === '100'
          && url.searchParams.get('status') === 'ACTIVE'
      })
      await page.getByRole('button', { name: /^(导入聊天记录|Import chat history)$/ }).click()
      const response = await metadata
      const query = new URL(response.url()).searchParams
      assert.equal(query.get('waitingOnly'), 'false')
      assert.equal(query.get('dueOnly'), 'false')
      assert.equal((await response.json()).data.total, 4)
      assert.deepEqual(errors, [])
      checks++
      await context.close()
    }
    console.log(`Review list checks passed: ${checks}; both languages, waiting-only queries, three tabs, import keeps all active cards`)
  } finally {
    await browser.close()
    await new Promise(resolve => server.close(resolve))
  }
}

main().catch(error => { console.error(error); server.close(); process.exitCode = 1 })
