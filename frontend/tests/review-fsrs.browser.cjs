// 使用独立 Playwright 运行时；接口全部模拟，不写数据库或调用 AI。
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

function fixture(kind, base = 6) {
  const modern = kind !== 'legacy'
  const target = modern ? base + 3 : 4
  return {
    id: 1, userErrorTypeId: 1, userErrorTypeName: 'FSRS focus', userErrorTypeDescription: '',
    errorTypeId: 1, errorTypeCode: 'GRAMMAR', errorTypeName: 'Grammar', status: 'ACTIVE',
    easeFactor: 2.5, repetitionCount: 1, intervalDays: 2, lapseCount: 2,
    dueAt: '2020-01-01T07:00:00', lastReviewedAt: '2020-01-01T07:00:00', masteredAt: null,
    reviewState: 'READY', reviewAttempts: [],
    ...(modern ? { schedulingAlgorithm: 'FSRS6', desiredRetention: 0.95,
      fsrsStability: kind === 'pending' ? null : 1.25, fsrsDifficulty: kind === 'pending' ? null : 7.2 } : {}),
    progress: { cycleNo: 1, successfulReviewCount: 3, failedReviewCount: 2, netSuccessCount: 1,
      targetSuccessCount: target, originalQuestionCount: 1, originalPassedCount: 0,
      retryQuestionCount: 1, pendingQuestionCount: 0,
      ...(modern ? { baseTargetSuccessCount: base, extraTargetSuccessCount: 3 } : {}) },
    currentQuestion: { questionId: 1, cycleQuestionId: 1, questionRole: 'ORIGINAL',
      sourceText: '我去了学校。', contextText: '', level: 'N3', difficulty: 3, grammarPoint: '',
      spoken: false, business: false, exam: false, tags: [], attemptCount: 1 },
  }
}

async function main() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const browser = await chromium.launch({ channel: process.env.SPEECH_TEST_BROWSER || 'chrome', headless: true })
  let checks = 0
  try {
    for (const mode of ['ZH_TO_JA', 'EN_TO_JA']) {
      const context = await browser.newContext({ viewport: { width: 1280, height: 1000 } })
      await context.addInitScript(value => localStorage.setItem('sazare.learningMode', value), mode)
      const page = await context.newPage()
      page.setDefaultTimeout(60_000)
      const errors = []
      page.on('pageerror', error => { errors.push(error.message); console.error(error.stack) })
      page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
      let detail = fixture('fsrs')
      let submissions = 0
      await page.route('**/api/**', async route => {
        const url = new URL(route.request().url())
        let data = { items: [], total: 0, page: 1, size: 20 }
        if (route.request().method() === 'POST') {
          assert.equal(url.pathname, '/api/review-cards/1/attempts')
          submissions++
          detail = { ...detail, progress: { ...detail.progress, failedReviewCount: 3, netSuccessCount: 0,
            targetSuccessCount: 12, extraTargetSuccessCount: 6 } }
          data = { userAnswerId: 1, quality: 2, result: 'FAIL', targetErrorResolved: false,
            feedback: 'Mock feedback', scores: { grammarVocabularyScore: 80, naturalFluencyScore: 80,
              scenarioAdaptationScore: 80, informationCompletenessScore: 80 }, totalScore: 80,
            errorAnalysis: [], progress: detail.progress, nextDueAt: '2099-01-01T07:00:00',
            cardStatus: 'ACTIVE', standardAnswers: [], derivedGenerationStatus: 'NOT_REQUIRED' }
        } else if (url.pathname === '/api/review-cards') {
          data = { items: [detail], total: 1, page: 1, size: 20 }
        } else if (url.pathname === '/api/review-cards/1') data = detail
        else if (url.pathname === '/api/questions/random') data = []
        await route.fulfill({ json: { code: 0, message: 'success', data } })
      })
      await page.goto(`http://127.0.0.1:${server.address().port}`)
      await page.bringToFront()
      await page.locator('.top-nav').getByRole('button', { name: /^(复习卡片|Review|Review cards)$/ }).click()
      await page.locator('.review-card-table').waitFor()
      assert.match(await page.locator('.review-progress').innerText(), /1\s*\/\s*9/)
      await page.locator('.review-card-actions button').first().click()
      const summary = page.locator('.review-cycle-summary')
      await summary.waitFor()
      assert.match(await summary.innerText(), /95%/)
      assert.match(await summary.innerText(), /7\.20/)
      assert.match(await summary.innerText(), /1\.25/)
      for (const width of [1280, 390]) {
        await page.setViewportSize({ width, height: 1000 })
        const fits = await summary.evaluate(element => element.scrollWidth <= element.clientWidth + 1)
        assert.ok(fits, `Review summary overflows at ${width}px in ${mode}`)
        checks++
      }
      await page.setViewportSize({ width: 1280, height: 1000 })
      const reload = () => page.getByRole('button', { name: /^(重新加载|Reload)$/ }).click()
      for (const base of [8, 6]) {
        detail = fixture('fsrs', base)
        await reload()
        await page.waitForFunction(target => document.querySelector('.review-cycle-progress strong')?.textContent === `1 / ${target}`, base + 3)
        assert.match(await summary.innerText(), new RegExp(`(?:基础|base) ${base} \\+ (?:追加|extra) 3`))
        checks++
      }
      detail = fixture('pending')
      await reload()
      await page.waitForFunction(() => document.querySelector('.review-cycle-schedule')?.textContent.includes('—'))
      assert.equal(await summary.locator('dd').filter({ hasText: /^—$/ }).count(), 2)
      checks++
      detail = fixture('legacy')
      await reload()
      await page.waitForFunction(() => !document.querySelector('.review-cycle-schedule')?.textContent.includes('FSRS'))
      assert.match(await summary.innerText(), /2\.5000/)
      assert.doesNotMatch(await summary.innerText(), /NaN|undefined/)
      checks++
      detail = fixture('fsrs')
      await reload()
      await page.getByRole('button', { name: /^(开始复习|Start review)$/ }).click()
      await page.getByRole('textbox', { name: /^(复习日语答案|Review answer in Japanese)$/ }).fill('学校に行きました。')
      await page.getByRole('button', { name: /^(提交答案|Submit answer)$/ }).click()
      await page.waitForFunction(() => document.querySelector('.review-cycle-progress strong')?.textContent === '0 / 12')
      assert.equal(submissions, 1)
      assert.deepEqual(errors, [])
      checks++
      await context.close()
    }
    console.log(`FSRS browser checks passed: ${checks}; both languages, latest severity, pending memory, old responses, failed review progress`)
  } finally {
    await browser.close()
    await new Promise(resolve => server.close(resolve))
  }
}

main().catch(error => { console.error(error); server.close(); process.exitCode = 1 })
