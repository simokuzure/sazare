// 使用已有 Playwright 运行时；不向项目添加依赖，也不访问真实麦克风或 Google。
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
    const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' }
    res.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream')
    res.end(content)
  } catch { res.writeHead(404).end() }
})

const question = (id = 1) => ({ id, questionType: 'TRANSLATION_ZH_TO_JA', sourceText: '今天学习日语。',
  contextText: '', level: 'N3', difficulty: 3, grammarPoint: '', spoken: false, business: false, exam: false,
  sourceType: 'MANUAL', enabled: true, tags: [], answers: [], createdAt: '2026-09-12T00:00:00', updatedAt: '2026-09-12T00:00:00' })
const card = { id: 1, userErrorTypeId: 1, userErrorTypeName: '助词', userErrorTypeDescription: '', errorTypeId: 1,
  errorTypeCode: 'GRAMMAR', errorTypeName: '语法', status: 'ACTIVE', easeFactor: 2.5, repetitionCount: 0,
  intervalDays: 0, lapseCount: 0, dueAt: '2020-01-01T00:00:00', lastReviewedAt: null, masteredAt: null,
  reviewState: 'READY', progress: { cycleNo: 1, successfulReviewCount: 0, failedReviewCount: 0, netSuccessCount: 0, targetSuccessCount: 4, originalQuestionCount: 1, originalPassedCount: 0, retryQuestionCount: 0, pendingQuestionCount: 1 }, currentQuestion: { ...question(), questionId: 1, cycleQuestionId: 1,
    questionRole: 'ORIGINAL', attemptCount: 0 }, reviewAttempts: [] }

async function main() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const browser = await chromium.launch({ channel: process.env.SPEECH_TEST_BROWSER || 'chrome', headless: true })
  const page = await browser.newPage({ viewport: { width: 1280, height: 1000 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  let unexpectedPosts = 0
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (route.request().method() !== 'GET') unexpectedPosts++
    let data = { items: [], total: 0, page: 1, size: 20 }
    if (url.pathname === '/api/questions/random') data = [question(1), question(2)]
    if (url.pathname === '/api/review-cards') data = { items: [card], total: 1, page: 1, size: 20 }
    if (url.pathname === '/api/review-cards/1') data = card
    await route.fulfill({ json: { code: 0, message: 'success', data } })
  })
  await page.addInitScript(() => {
    const state = window.__voice = { streams: [], requests: 0, transcript: '今日は日本語を勉強します。',
      permission: 'allow', pending: [], defer: false, fail: false, empty: false, supported: true, NativeRecorder: window.MediaRecorder }
    const stream = () => {
      const track = { stopped: false, stop() { this.stopped = true } }
      state.streams.push(track)
      return { getTracks: () => [track] }
    }
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: {
      getUserMedia: async () => {
        if (state.permission === 'deny') throw new DOMException('denied', 'NotAllowedError')
        if (state.permission === 'pending') await new Promise(resolve => { state.allowPermission = resolve })
        return stream()
      },
    } })
    window.MediaRecorder = class {
      static isTypeSupported() { return state.supported }
      constructor(_stream, options) { this.mimeType = options.mimeType; this.state = 'inactive'; state.recorder = this }
      start() { this.state = 'recording' }
      stop() {
        if (this.state === 'inactive') return
        this.state = 'inactive'
        queueMicrotask(() => {
          this.ondataavailable?.({ data: new Blob(state.empty ? [] : ['synthetic-audio']) })
          this.onstop?.()
        })
      }
    }
    const fetchOriginal = window.fetch.bind(window)
    window.fetch = async (url, options) => {
      if (url !== '/api/speech-transcriptions') return fetchOriginal(url, options)
      state.requests++
      state.lastType = options.headers['Content-Type']
      state.lastBlob = options.body instanceof Blob
      if (state.defer) await new Promise(resolve => state.pending.push(resolve))
      if (state.fail) throw new TypeError('network failed')
      return new Response(JSON.stringify({ code: 0, message: 'success', data: { text: state.transcript } }), {
        headers: { 'Content-Type': 'application/json' },
      })
    }
  })
  const url = `http://127.0.0.1:${server.address().port}`
  const speech = () => page.locator('.speech-input:visible')
  const input = () => page.locator('textarea:visible').filter({ hasNot: page.locator('.speech-input-draft') }).first()
  const voice = () => speech().getByRole('button', { name: /^(语音输入|Voice input)$/ })
  const stop = () => speech().getByRole('button', { name: /^(停止并转写|Stop and transcribe)$/ })
  const cancel = () => speech().getByRole('button', { name: /^(取消|Cancel)$/ })
  const waitValue = value => page.waitForFunction(value => document.querySelector('textarea:not([hidden])') !== null
    && [...document.querySelectorAll('textarea')].some(el => el.checkVisibility() && el.value === value), value)
  const tracksStopped = async () => assert.equal(await page.evaluate(() => window.__voice.streams.every(track => track.stopped)), true)
  const check = title => console.log(`PASS ${title}`)
  try {
    await page.goto(url)
    for (const direction of ['ZH_TO_JA', 'EN_TO_JA']) {
      await page.locator('.language-select select').selectOption(direction)
      for (const mode of ['sentence', 'article', 'correction', 'review']) {
        if (mode === 'review') {
          await page.locator('.top-nav button').nth(1).click()
          await page.getByRole('button', { name: /^(开始复习|Start review)$/ }).click()
        } else {
          await page.locator('.top-nav button').nth(0).click()
          await page.locator(`#${mode}-practice-tab`).click()
          if (mode !== 'correction') {
            const panel = page.locator(`#${mode}-practice-panel`)
            await panel.getByRole('button', { name: mode === 'sentence' ? /^(随机题目|Random)$/ : /^(随机文章|Random)$/ }).click()
          }
        }
        await input().fill('既存の文章')
        const submit = speech().getByRole('button', { name: /^(提交答案|Submit|Submit answer|开始纠错|Check text)$/ })
        const voiceBox = await voice().boundingBox()
        const submitBox = await submit.boundingBox()
        assert.ok(voiceBox.x < submitBox.x)
        assert.ok(Math.abs(voiceBox.y + voiceBox.height / 2 - submitBox.y - submitBox.height / 2) < 1)
        assert.equal(await speech().locator('.speech-input-help').count(), 0)
        await voice().click()
        await stop().waitFor()
        const stopBox = await stop().boundingBox()
        const timerBox = await speech().locator('.speech-input-timer').boundingBox()
        assert.ok(timerBox.x >= stopBox.x + stopBox.width)
        assert.ok(Math.abs(stopBox.y + stopBox.height / 2 - timerBox.y - timerBox.height / 2) < 1)
        assert.equal(await input().isDisabled(), true)
        const footerButtons = page.locator('button:visible').filter({ hasText: /^(清空|Clear|提交答案|Submit|Submit answer|开始纠错|Check text)$/ })
        for (const button of await footerButtons.all()) assert.equal(await button.isDisabled(), true)
        await stop().click()
        await waitValue('既存の文章\n今日は日本語を勉強します。')
        await tracksStopped()
        assert.equal(await speech().locator('.speech-input-status').innerText(), '')
        await voice().click(); await stop().click()
        await waitValue('既存の文章\n今日は日本語を勉強します。\n今日は日本語を勉強します。')
        if (process.env.SPEECH_TEST_SCREENSHOT && direction === 'EN_TO_JA') {
          const screenshotPath = process.env.SPEECH_TEST_SCREENSHOT.replace(/\.png$/, `-${mode}.png`)
          await page.screenshot({ path: screenshotPath, fullPage: true })
        }
        check(`${direction} ${mode}: append, repeated recordings, disabled actions`)
      }
    }
    await page.locator('.top-nav button').nth(0).click()
    await page.locator('#correction-practice-tab').click()
    await input().fill('原文')
    await voice().click(); await stop().waitFor(); await cancel().click(); await tracksStopped()
    assert.equal(await input().inputValue(), '原文')
    check('cancel recording preserves text and releases microphone')

    await voice().click(); await stop().waitFor()
    await page.locator('#article-practice-tab').click(); await tracksStopped()
    await page.locator('#correction-practice-tab').click()
    await voice().click(); await stop().waitFor()
    await page.locator('.top-nav button').nth(2).click(); await tracksStopped()
    await page.locator('.top-nav button').nth(0).click()
    check('hidden practice mode and hidden practice page release microphone')

    await page.evaluate(() => { window.__voice.permission = 'pending' })
    await voice().click(); await cancel().waitFor(); await cancel().click()
    await page.evaluate(() => window.__voice.allowPermission())
    await page.waitForFunction(() => window.__voice.streams.every(track => track.stopped))
    await page.evaluate(() => { window.__voice.permission = 'allow'; window.__voice.defer = true })
    await voice().click(); await stop().click()
    await page.waitForFunction(() => window.__voice.pending.length === 1)
    await cancel().click()
    await page.evaluate(() => { window.__voice.pending.shift()(); window.__voice.defer = false })
    await voice().click(); await stop().click()
    await waitValue('原文\n今日は日本語を勉強します。')
    check('late permission and cancelled transcription cannot restore stale state')

    await input().fill('あ'.repeat(4999))
    await voice().click(); await stop().click()
    const draft = speech().locator('.speech-input-draft textarea')
    await draft.waitFor()
    assert.equal((await input().inputValue()).length, 4999)
    await input().fill('短文')
    await draft.fill('修正済み')
    await speech().getByRole('button', { name: /^(追加文字|Append text)$/ }).click()
    await waitValue('短文\n修正済み')
    check('overflow preserves complete transcript for editing')

    await page.evaluate(() => { window.__voice.permission = 'deny' })
    await voice().click()
    await speech().getByRole('status').filter({ hasText: /permission|权限/ }).waitFor()
    await page.evaluate(() => { window.__voice.permission = 'allow'; window.__voice.fail = true })
    await voice().click(); await stop().click()
    await speech().getByRole('status').filter({ hasText: /failed|失败/ }).waitFor()
    await tracksStopped()
    await page.evaluate(() => { window.__voice.fail = false; window.__voice.empty = true })
    await voice().click(); await stop().click()
    await speech().getByRole('status').filter({ hasText: /empty|为空/ }).waitFor()
    await page.evaluate(() => { window.__voice.empty = false; window.__voice.supported = false })
    await voice().click()
    await speech().getByRole('status').filter({ hasText: /format|格式/ }).waitFor()
    await page.evaluate(() => { window.__voice.supported = true })
    check('permission, network, empty recording, unsupported format errors recover')

    await voice().click(); await stop().waitFor()
    await page.evaluate(() => window.__voice.recorder.ondataavailable({ data: new Blob([new Uint8Array(8 * 1024 * 1024 + 1)]) }))
    await speech().getByRole('status').filter({ hasText: /8 MiB/ }).waitFor(); await tracksStopped()
    await voice().click(); await stop().waitFor()
    await page.evaluate(() => { window.__originalNow = Date.now; Date.now = () => window.__originalNow() + 61000 })
    await voice().waitFor()
    await page.evaluate(() => { Date.now = window.__originalNow })
    await tracksStopped()
    check('size overflow stops recording; 60 seconds automatically transcribes')


    await page.locator('#sentence-practice-tab').click()
    await page.locator('#sentence-practice-panel').getByRole('button', { name: /^(随机题目|Random)$/ }).click()
    await input().fill('一問目')
    await page.evaluate(() => { window.__voice.defer = true })
    await voice().click(); await stop().click()
    await page.waitForFunction(() => window.__voice.pending.length === 1)
    assert.equal(await input().isDisabled(), true)
    await page.locator('.question-selector button').nth(1).click()
    await page.evaluate(() => { window.__voice.pending.shift()(); window.__voice.defer = false })
    await input().fill('二問目')
    await voice().click(); await stop().click()
    await waitValue('二問目\n今日は日本語を勉強します。')
    await page.locator('.question-selector button').nth(0).click()
    await waitValue('一問目')
    await voice().click(); await stop().waitFor()
    await page.locator('.language-select select').selectOption('ZH_TO_JA')
    await tracksStopped()
    check('question switching isolates pending results; language switching releases microphone')

    await page.locator('#correction-practice-tab').click()
    await voice().click(); await stop().waitFor()
    await page.evaluate(() => window.__voice.recorder.onerror())
    await speech().getByRole('status').filter({ hasText: /失败|failed/ }).waitFor()
    await tracksStopped()
    await page.evaluate(() => { window.__voice.transcript = ' ' })
    await voice().click(); await stop().click()
    await speech().getByRole('status').filter({ hasText: /未识别|failed/ }).waitFor()
    await page.evaluate(() => { window.__voice.transcript = '今日は日本語を勉強します。' })
    await voice().click(); await stop().waitFor()
    await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pagehide')))
    await tracksStopped()
    check('recorder error, no recognized speech and pagehide release resources')

    const nativeAudio = await page.evaluate(async () => {
      const Recorder = window.__voice.NativeRecorder
      const mimeType = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus'].find(type => Recorder.isTypeSupported(type))
      if (!mimeType) return { supported: false }
      const audio = new AudioContext()
      const destination = audio.createMediaStreamDestination()
      const oscillator = audio.createOscillator()
      oscillator.connect(destination); oscillator.start()
      const recorder = new Recorder(destination.stream, { mimeType })
      let size = 0
      recorder.ondataavailable = event => { size += event.data.size }
      const stopped = new Promise(resolve => { recorder.onstop = resolve })
      recorder.start()
      await new Promise(resolve => setTimeout(resolve, 300))
      recorder.stop(); await stopped
      oscillator.stop(); destination.stream.getTracks().forEach(track => track.stop()); await audio.close()
      return { supported: true, size, mimeType }
    })
    assert.equal(nativeAudio.supported, true)
    assert.ok(nativeAudio.size > 0)
    check('native MediaRecorder encodes synthetic audio in a supported format without files')

    await page.setViewportSize({ width: 390, height: 844 })
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true)
    if (process.env.SPEECH_TEST_SCREENSHOT) await page.screenshot({ path: process.env.SPEECH_TEST_SCREENSHOT, fullPage: true })
    assert.equal(unexpectedPosts, 0)
    assert.deepEqual(errors, [])
    assert.equal(await page.evaluate(() => window.__voice.lastBlob), true)
    assert.match(await page.evaluate(() => window.__voice.lastType), /audio\/webm/)
    check('narrow viewport, no automatic scoring, no runtime errors, binary request')
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)) }
}
main().catch(error => { console.error(error); server.close(); process.exitCode = 1 })
