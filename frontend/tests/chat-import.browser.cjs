// 使用已有 Playwright 验证构建或开发页面；固定接口响应不写数据库或调用 Google。
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const assert = require('node:assert/strict')
const http = require('node:http')
const fs = require('node:fs/promises')
const path = require('node:path')
const dist = path.resolve(__dirname, '../dist')
const output = path.resolve(__dirname, '../node_modules/.tmp/chat-import-verification', process.env.CHAT_IMPORT_TEST_ORIGIN ? 'development' : '')
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
const progress = { cycleNo: 1, successfulReviewCount: 0, failedReviewCount: 0, netSuccessCount: 0,
  targetSuccessCount: 4, originalQuestionCount: 1, originalPassedCount: 0, retryQuestionCount: 1, pendingQuestionCount: 0 }
const card = (id, status = 'ACTIVE') => ({ id, userErrorTypeId: id + 1000, errorTypeId: 1, errorTypeCode: 'GRAMMAR', errorTypeName: '语法',
  userErrorTypeName: id === 101 ? '后页卡片' : status === 'MASTERED' ? '已掌握卡片' : `练习重点 ${id}`, userErrorTypeDescription: '已有重点说明', status,
  dueAt: '2099-01-01T07:00:00', lastReviewedAt: null, masteredAt: null, progress })
const candidates = ['前に接续', '助词选择', '时态'].map((name, index) => ({ errorTypeId: 1, name, description: '明确的语法错误', severity: index === 1 ? 'HIGH' : 'MEDIUM',
  evidence: [{ messageId: 1, originalText: '日本に来た前に勉強しました。' }], correctedExpression: '日本に来る前に勉強しました。',
  reviewSourceText: '来日本之前学习过。', referenceAnswer: '日本に来る前に勉強しました。' }))

async function main() {
  await fs.mkdir(output, { recursive: true })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const browser = await chromium.launch({ channel: process.env.CHAT_IMPORT_TEST_BROWSER || 'chrome', headless: true })
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  const errors = [], checks = []
  page.on('pageerror', error => errors.push(error.message))
  const record = text => { checks.push(text); console.log('PASS', text) }
  const screenshot = async name => {
    if (process.env.CHAT_IMPORT_TEST_SCREENSHOTS === '1') await page.screenshot({ path: path.join(output, `${name}.png`), fullPage: true })
  }
  const waitUntil = async predicate => {
    const deadline = Date.now() + 15000
    while (!predicate()) { if (Date.now() > deadline) throw new Error('Timed out waiting for test request'); await new Promise(resolve => setTimeout(resolve, 30)) }
  }
  const state = { analyses: 0, confirmations: [], metadataPages: [], mode: 'ok', metadataFail: false, nextId: 200 }
  let releaseConfirmation, releaseAnalysis
  const activeCards = Array.from({ length: 101 }, (_, index) => card(index + 1))
  activeCards.push({ ...card(180), errorTypeId: 2, errorTypeCode: 'MISTRANSLATION', userErrorTypeName: '不适用分类卡片' })
  const mastered = [card(150, 'MASTERED')]
  const respond = (route, data) => route.fulfill({ json: { code: 0, message: 'success', data } })
  await page.addInitScript(() => localStorage.setItem('sazare.learningMode', 'ZH_TO_JA'))
  await page.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url())
    if (!url.pathname.startsWith('/api/')) return route.continue()
    if (url.pathname === '/api/chat-imports/analyses') {
      state.analyses++
      assert.match(request.headers()['content-type'], /text\/markdown/)
      assert.ok(request.postData().includes('## User'))
      if (state.mode === 'analysis-error') return route.fulfill({ json: { code: 40001, message: '聊天正文不能超过 30000 字符', data: null } })
      if (state.mode === 'empty') return respond(route, { checkedUserMessageIds: [1], candidates: [] })
      if (state.mode === 'long') return respond(route, { checkedUserMessageIds: [1], candidates: Array.from({ length: 30 }, (_, index) => ({
        ...candidates[0], name: `Long focus ${index + 1}`,
        evidence: [{ messageId: 1, originalText: '日本に来た前に勉強しました。'.repeat(120) }],
        reviewSourceText: 'I studied Japanese before coming to Japan.',
      })) })
      if (state.mode === 'analysis-hold') {
        await new Promise(resolve => { releaseAnalysis = resolve })
        return respond(route, { checkedUserMessageIds: [1], candidates: candidates.map(c => ({ ...c, name: '过期结果' })) }).catch(() => {})
      }
      return respond(route, { checkedUserMessageIds: [1], candidates })
    }
    if (url.pathname === '/api/chat-imports/confirmations') {
      const payload = request.postDataJSON()
      state.confirmations.push(payload)
      if (state.mode === 'network') return route.abort('failed')
      if (state.mode === 'confirmation-hold') await new Promise(resolve => { releaseConfirmation = resolve })
      const groups = new Map()
      for (const item of payload.items) {
        const key = item.mode === 'EXISTING_USER_ERROR_TYPE' ? item.userErrorTypeId : item.errorTypeId + ':' + item.name
        if (groups.has(key)) continue
        const existing = [...activeCards, ...mastered].find(c => c.userErrorTypeId === item.userErrorTypeId)
        const created = existing || { ...card(state.nextId++), userErrorTypeName: item.name }
        const action = existing ? existing.status === 'MASTERED' ? 'REACTIVATED' : 'UPDATED' : 'CREATED'
        if (!existing) activeCards.push(created)
        groups.set(key, { cardId: created.id, userErrorTypeId: created.userErrorTypeId, questionIds: [state.nextId + 500], action })
      }
      if (state.mode === 'refresh-fail') state.metadataFail = true
      return respond(route, { cards: [...groups.values()] })
    }
    if (url.pathname === '/api/review-cards') {
      if (state.metadataFail && url.searchParams.get('size') === '100') return route.fulfill({ json: { code: 40002, message: '列表刷新暂时失败', data: null } })
      const list = url.searchParams.get('status') === 'MASTERED' ? mastered : activeCards
      const pageNumber = Number(url.searchParams.get('page')), size = Number(url.searchParams.get('size'))
      state.metadataPages.push(`${url.searchParams.get('status')}:${size}:${pageNumber}`)
      return respond(route, { items: list.slice((pageNumber - 1) * size, pageNumber * size), page: pageNumber, size, total: list.length })
    }
    if (url.pathname === '/api/error-types') return respond(route, { items: [
      { id: 1, code: 'GRAMMAR', name: '语法', nameEn: 'Grammar', enabled: true },
      { id: 2, code: 'MISTRANSLATION', name: '误译', nameEn: 'Mistranslation', enabled: true },
    ], page: 1, size: 100, total: 2 })
    return respond(route, url.pathname === '/api/questions/random' ? [] : { items: [], page: 1, size: 20, total: 0 })
  })
  const origin = process.env.CHAT_IMPORT_TEST_ORIGIN || `http://127.0.0.1:${server.address().port}`
  const file = (name = 'conversation.md', body = '# Conversation\n\n## User\n日本に来た前に勉強しました。\n\n## Assistant\n来る前に。') => ({ name, mimeType: 'text/markdown', buffer: Buffer.from(body) })
  const upload = async value => page.locator('input[type=file]').setInputFiles(value || file())
  const analyze = async () => { await page.getByRole('button', { name: '开始分析', exact: true }).click(); await page.getByRole('heading', { name: '错误候选', exact: true }).waitFor() }
  const editor = () => page.locator('.import-editor')
  const open = async index => page.locator('.import-candidate-list li > button').nth(index).click()
  const confirm = () => page.locator('.import-confirm-button')
  const assertDialogRemainsOpen = async name => {
    const dialog = page.getByRole('dialog', { name, exact: true })
    await dialog.waitFor()
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
    assert.equal(await dialog.isVisible(), true)
    return dialog
  }
  try {
    await page.goto(origin)
    await page.getByRole('navigation').getByRole('button', { name: '复习卡片', exact: true }).click()
    await page.getByRole('button', { name: '导入聊天记录', exact: true }).click()
    await page.getByRole('heading', { name: '导入一段日语练习聊天', exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: '开始分析', exact: true }).count(), 0)
    assert.equal(await page.evaluate(() => {
      const upload = document.querySelector('.import-upload-dropzone').getBoundingClientRect()
      const guide = document.querySelector('.import-upload-guide').getBoundingClientRect()
      return upload.height >= 280 && upload.right < guide.left && document.documentElement.scrollWidth <= innerWidth
    }), true)
    await screenshot('upload-empty')
    await page.locator('.import-upload-dropzone').dispatchEvent('dragover')
    assert.equal(await page.locator('.import-file-panel.is-dragging').count(), 1)
    await page.locator('.import-upload-dropzone').dispatchEvent('dragleave')
    assert.equal(await page.locator('.import-file-panel.is-dragging').count(), 0)
    await page.getByRole('button', { name: '导出提示词', exact: true }).click()
    await assertDialogRemainsOpen('导出提示词')
    await page.keyboard.press('Escape')
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    for (let attempt = 0; attempt < 2; attempt++) {
      const exportButton = page.getByRole('button', { name: '导出提示词', exact: true })
      await exportButton.click()
      const dialog = await assertDialogRemainsOpen('导出提示词')
      await dialog.getByRole('button', { name: '关闭', exact: true }).click()
      await page.getByRole('dialog').waitFor({ state: 'hidden' })
      assert.equal(await exportButton.evaluate(element => document.activeElement === element), true)
    }
    await page.setViewportSize({ width: 390, height: 844 })
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    await screenshot('upload-mobile')
    await page.setViewportSize({ width: 1366, height: 768 })
    record('upload landing has a drop area, export guide, drag feedback, and a responsive mobile layout')
    await upload({ name: 'too-big.md', mimeType: 'text/markdown', buffer: Buffer.alloc(131073) })
    await page.getByText('文件不能为空，且不能超过 128 KiB。', { exact: true }).waitFor()
    assert.equal(state.analyses, 0)
    await upload()
    await page.getByText('文件已准备好', { exact: true }).waitFor()
    assert.equal(state.analyses, 0)
    await screenshot('upload-ready')
    assert.equal(await page.getByRole('button', { name: '查看聊天原文', exact: true }).count(), 0)
    await analyze()
    assert.equal(await page.locator('.import-upload-panel').count(), 0)
    assert.equal(await page.locator('.import-candidate-list input[type="checkbox"]').count(), 0)
    assert.equal(await page.locator('.import-candidate-list li.is-selected').count(), 0)
    assert.ok(state.metadataPages.includes('ACTIVE:100:2'))
    await page.locator('#import-detail-heading').waitFor()
    assert.equal(await page.locator('.import-confirm-bar, .import-steps').count(), 0)
    assert.equal(await confirm().isDisabled(), true)
    const desktopLayout = await page.evaluate(() => {
      const panels = ['.import-candidate-list', '.import-analysis', '.import-editor'].map(selector => document.querySelector(selector).getBoundingClientRect())
      return {
        fitsViewport: document.documentElement.scrollWidth <= innerWidth && document.documentElement.scrollHeight <= innerHeight,
        columnsAligned: panels.every(panel => Math.abs(panel.top - panels[0].top) < 1) && panels[0].right < panels[1].left && panels[1].right < panels[2].left,
        contentVisible: [...document.querySelectorAll('.import-analysis-body, .import-editor-body')].every(panel => panel.scrollHeight <= panel.clientHeight),
        confirmVisible: document.querySelector('.import-confirm-button').getBoundingClientRect().bottom <= innerHeight,
      }
    })
    assert.deepEqual(desktopLayout, { fitsViewport: true, columnsAligned: true, contentVisible: true, confirmVisible: true })
    await screenshot('desktop-ready')
    assert.equal(await page.getByRole('button', { name: '查看聊天原文', exact: true }).count(), 0)
    await page.getByRole('button', { name: '导出提示词', exact: true }).click()
    const promptDialog = page.getByRole('dialog', { name: '导出提示词', exact: true })
    await assertDialogRemainsOpen('导出提示词')
    await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', { configurable: true, value: {
      writeText: async value => { window.copiedImportPrompt = value },
    } }))
    await promptDialog.getByRole('button', { name: '复制导出提示词', exact: true }).click()
    await promptDialog.getByRole('status').waitFor()
    assert.equal(await page.evaluate(() => window.copiedImportPrompt), await promptDialog.locator('pre').innerText())
    await page.evaluate(() => { navigator.clipboard.writeText = async () => { throw new Error('Clipboard denied') } })
    await promptDialog.getByRole('button', { name: '复制导出提示词', exact: true }).click()
    await promptDialog.getByRole('alert').waitFor()
    await promptDialog.getByRole('button', { name: '关闭', exact: true }).click()
    assert.equal(await page.locator('.chat-import-page').innerText().then(value => value.includes('证据')), false)
    record('1366×768 results fit one screen; export prompt stays open and handles clipboard results; full conversation preview is removed')
    await page.getByRole('button', { name: '选择筛选结果', exact: true }).click()
    assert.equal(await page.locator('.import-candidate-list li.is-selected').count(), 3)
    await page.getByRole('button', { name: '取消选择筛选结果', exact: true }).click()
    assert.equal(await page.locator('.import-candidate-list li.is-selected').count(), 0)
    await editor().getByRole('checkbox', { name: '选择此项', exact: true }).check()
    const firstRow = page.locator('.import-candidate-list li').first()
    assert.equal(await firstRow.evaluate(element => getComputedStyle(element).backgroundColor), 'rgb(232, 242, 236)')
    assert.match(await firstRow.innerText(), /已选择/)
    await open(1)
    assert.equal(await firstRow.evaluate(element => getComputedStyle(element).backgroundColor), 'rgb(232, 242, 236)')
    assert.equal(await editor().getByRole('checkbox', { name: '选择此项', exact: true }).isChecked(), false)
    await open(0)
    await editor().getByRole('checkbox', { name: '选择此项', exact: true }).uncheck()
    assert.equal(await page.locator('.import-candidate-list li.is-selected').count(), 0)
    record('candidate rows have no duplicate checkboxes; detail and bulk selection update green rows without selecting the open item')
    await open(0)
    assert.equal(await editor().getByLabel('错误分类', { exact: true }).locator('option').count(), 1)
    await editor().getByLabel('日语参考答案', { exact: true }).fill('bad')
    await editor().getByRole('checkbox', { name: '选择此项', exact: true }).check()
    await confirm().click()
    await page.getByText('参考答案需要包含日语假名。', { exact: true }).waitFor()
    assert.equal(state.confirmations.length, 0)
    assert.equal(await editor().getByLabel('日语参考答案', { exact: true }).evaluate(el => document.activeElement === el), true)
    await editor().getByLabel('日语参考答案', { exact: true }).fill('日本に来る前に勉強しました。')
    await editor().getByLabel('加入已有卡片', { exact: true }).check()
    assert.equal(await editor().getByLabel('目标复习卡片', { exact: true }).getByText('不适用分类卡片', { exact: false }).count(), 0)
    await editor().getByLabel('搜索已有卡片', { exact: true }).fill('后页')
    await editor().getByLabel('目标复习卡片', { exact: true }).selectOption('1101')
    await open(1)
    await editor().getByLabel('加入已有卡片', { exact: true }).check()
    await editor().getByLabel('目标复习卡片', { exact: true }).selectOption('1101')
    await editor().getByRole('checkbox', { name: '选择此项', exact: true }).check()
    await page.evaluate(() => scrollTo(0, 0))
    await screenshot('desktop')
    assert.match(await page.locator('.import-summary').innerText(), /已选 2 项/)
    state.mode = 'confirmation-hold'
    await confirm().click()
    await page.getByText('正在保存…', { exact: true }).waitFor()
    await page.getByRole('navigation').getByRole('button', { name: '题目管理', exact: true }).click()
    assert.equal(await page.getByRole('heading', { name: '聊天分析' }).count(), 1)
    await waitUntil(() => releaseConfirmation)
    releaseConfirmation()
    await page.getByText('所选内容已保存', { exact: true }).waitFor()
    assert.equal(state.confirmations.length, 1)
    assert.equal(state.confirmations[0].items.length, 2)
    assert.ok(!('evidence' in state.confirmations[0].items[0]))
    assert.equal(await page.locator('.import-candidate-list li').count(), 1)
    record('file limit, complete pagination, explicit selection, field focus, same-card batch and navigation lock')

    await open(0)
    await editor().getByLabel('加入已有卡片', { exact: true }).check()
    await editor().getByLabel('目标复习卡片', { exact: true }).selectOption('1150')
    await page.getByText('确认后将重新激活这张卡片，开启新的复习周期。', { exact: true }).waitFor()
    await editor().getByRole('checkbox', { name: '选择此项', exact: true }).check()
    state.mode = 'refresh-fail'
    await confirm().click()
    await page.getByText('本次候选已全部保存，将按卡片的到期安排进入复习。', { exact: true }).waitFor()
    await page.getByText('卡片列表刷新失败', { exact: true }).waitFor()
    assert.match(await page.locator('.import-results').innerText(), /重新激活 1/)
    state.metadataFail = false
    state.mode = 'ok'
    await page.getByRole('button', { name: '重新加载卡片', exact: true }).click()
    await page.getByRole('button', { name: '重新加载卡片', exact: true }).waitFor({ state: 'hidden' })
    assert.equal(state.confirmations.length, 2)
    record('partial batches retain pending edits; mastered-card impact; refresh failure never resubmits')

    await upload(file('second.md'))
    await analyze()
    await open(0)
    await editor().getByRole('checkbox', { name: '选择此项', exact: true }).check()
    state.mode = 'network'
    await confirm().click()
    await page.getByText('提交结果未知', { exact: true }).waitFor()
    const posts = state.confirmations.length
    await page.getByRole('button', { name: '先检查复习卡片', exact: true }).click()
    await page.getByRole('region', { name: '当前目标卡片状态', exact: true }).waitFor()
    assert.equal(await editor().getByLabel('日语参考答案', { exact: true }).inputValue(), '日本に来る前に勉強しました。')
    page.once('dialog', dialog => dialog.dismiss())
    await confirm().click()
    assert.equal(state.confirmations.length, posts)
    state.mode = 'ok'
    page.once('dialog', dialog => dialog.accept())
    await confirm().click()
    await page.getByText('所选内容已保存', { exact: true }).waitFor()
    assert.equal(state.confirmations.length, posts + 1)
    record('unknown confirmation retains input, checks cards in place, requires explicit manual resubmission')

    page.once('dialog', dialog => dialog.dismiss())
    await page.getByRole('navigation').getByRole('button', { name: '题目管理', exact: true }).click()
    assert.equal(await page.getByRole('heading', { name: '聊天分析' }).count(), 1)
    page.once('dialog', dialog => dialog.dismiss())
    await page.getByLabel('练习语言方向', { exact: true }).selectOption('EN_TO_JA')
    assert.equal(await page.getByLabel('练习语言方向', { exact: true }).inputValue(), 'ZH_TO_JA')
    await page.setViewportSize({ width: 390, height: 844 })
    await page.locator('.import-analysis').getByRole('button', { name: '← 候选列表', exact: true }).click()
    await open(0)
    assert.equal(await page.locator('.import-candidate-list').isVisible(), false)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    await page.evaluate(() => scrollTo(0, 0))
    await screenshot('mobile')
    record('leave/mode guards and single-column layout without horizontal overflow')

    page.once('dialog', dialog => dialog.accept())
    await page.getByLabel('练习语言方向', { exact: true }).selectOption('EN_TO_JA')
    await page.getByRole('button', { name: 'Import chat history', exact: true }).click()
    await upload()
    state.mode = 'analysis-error'
    await page.getByRole('button', { name: 'Analyze conversation', exact: true }).click()
    await page.getByText('The chat body must not exceed 30,000 characters.', { exact: true }).waitFor()
    assert.doesNotMatch(await page.getByRole('alert').innerText(), /\p{Script=Han}/u)
    state.mode = 'empty'
    await page.getByRole('button', { name: 'Analyze conversation', exact: true }).click()
    await page.getByText('No clear errors found. You can analyze another file.', { exact: true }).waitFor()
    record('English interface translates concrete backend validation and handles no-error analysis')

    await upload(file('held.md'))
    state.mode = 'analysis-hold'
    await page.getByRole('button', { name: 'Analyze conversation', exact: true }).click()
    await waitUntil(() => releaseAnalysis)
    page.once('dialog', dialog => dialog.accept())
    await upload(file('newer.md'))
    state.mode = 'ok'
    await page.getByRole('button', { name: 'Analyze conversation', exact: true }).click()
    await page.locator('.import-workspace').waitFor()
    releaseAnalysis()
    await page.locator('#import-detail-heading').filter({ hasText: '前に接续' }).waitFor()
    assert.equal(await page.getByText('过期结果', { exact: true }).count(), 0)
    assert.equal(await page.evaluate(() => {
      const event = new Event('beforeunload', { cancelable: true })
      dispatchEvent(event)
      return event.defaultPrevented
    }), true)
    record('replacing a file ignores aborted stale analysis; unconfirmed candidates guard browser unload')
    await page.setViewportSize({ width: 1366, height: 768 })
    for (const label of ['Learning focus', 'Focus description', 'Review prompt (English)', 'Japanese reference answer']) {
      await editor().getByLabel(label, { exact: true }).fill('')
    }
    await editor().getByRole('checkbox', { name: 'Select item', exact: true }).check()
    const confirmationsBeforeValidation = state.confirmations.length
    await confirm().click()
    for (const message of ['Enter a name (up to 128 characters).', 'Enter a description (up to 255 characters).',
      'Enter a review prompt (up to 1000 characters).', 'Enter a Japanese answer (up to 2000 characters).']) {
      await page.getByText(message, { exact: true }).waitFor()
    }
    await editor().getByLabel('Review prompt (English)', { exact: true }).fill('中文')
    await editor().getByLabel('Japanese reference answer', { exact: true }).fill('English')
    await confirm().click()
    await page.getByText('Use English without Chinese characters or Japanese kana.', { exact: true }).waitFor()
    await page.getByText('The reference answer must contain Japanese kana.', { exact: true }).waitFor()
    for (const message of await page.locator('.import-field-error').allTextContents()) assert.doesNotMatch(message, /\p{Script=Han}/u)
    assert.equal(state.confirmations.length, confirmationsBeforeValidation)
    record('English field validation covers required values, limits and source/answer languages without submitting invalid drafts')
    page.once('dialog', dialog => dialog.accept())
    await upload(file('long.md'))
    state.mode = 'long'
    await page.getByRole('button', { name: 'Analyze conversation', exact: true }).click()
    await page.locator('.import-candidate-list li').nth(29).waitFor({ state: 'attached' })
    assert.equal(await page.evaluate(() => {
      const analysis = document.querySelector('.import-analysis-body')
      const list = document.querySelector('.import-candidate-list > ul')
      return document.documentElement.scrollHeight <= innerHeight && document.documentElement.scrollWidth <= innerWidth
        && analysis.scrollHeight > analysis.clientHeight && list.scrollHeight > list.clientHeight
    }), true)
    await page.locator('.import-candidate-list li > button').last().click()
    assert.equal(await page.locator('#import-detail-heading').innerText(), 'Long focus 30')
    assert.equal(await page.getByRole('button', { name: 'Confirm 0 selected', exact: true }).isVisible(), true)
    await page.setViewportSize({ width: 900, height: 900 })
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    await page.locator('.import-editor').getByLabel('Japanese reference answer', { exact: true }).scrollIntoViewIfNeeded()
    assert.equal(await page.locator('.import-editor').getByLabel('Japanese reference answer', { exact: true }).isVisible(), true)
    record('long evidence and many candidates scroll within desktop panels; English tablet editor remains reachable')
    await page.setViewportSize({ width: 1366, height: 768 })
    page.once('dialog', dialog => dialog.accept())
    await page.getByRole('navigation').getByRole('button').first().click()
    await page.locator('.chat-import-page').waitFor({ state: 'detached' })
    assert.equal(await page.locator('.app-shell').evaluate(element => getComputedStyle(element).display), 'block')
    assert.equal(await page.locator('.workspace').evaluate(element => getComputedStyle(element).display), 'block')
    record('leaving chat import restores the normal application layout')
    assert.deepEqual(errors, [])
    await fs.writeFile(path.join(output, 'results.json'), JSON.stringify({ checks, pageErrors: errors, analysisRequests: state.analyses, confirmationRequests: state.confirmations.length }, null, 2))
  } catch (error) {
    await screenshot('failure').catch(() => {})
    throw error
  } finally {
    releaseConfirmation?.(); releaseAnalysis?.()
    await browser.close()
    await new Promise(resolve => server.close(resolve))
  }
}
main().catch(error => { console.error(error); server.close(); process.exitCode = 1 })
