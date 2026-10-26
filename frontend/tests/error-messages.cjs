const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

let storedMode = null
const cache = new Map()
function load(relative) {
  const filename = path.resolve(__dirname, '../src', relative)
  if (cache.has(filename)) return cache.get(filename)
  const source = fs.readFileSync(filename, 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
  const exports = {}
  cache.set(filename, exports)
  vm.runInNewContext(code, {
    exports, Error, TypeError,
    window: { localStorage: { getItem: () => storedMode } },
    require: specifier => load(path.relative(path.resolve(__dirname, '../src'), path.resolve(path.dirname(filename), specifier + '.ts'))),
  })
  return exports
}
const { englishErrorMessage, REQUEST_FAILED_EN } = load('i18n/errorMessages.ts')
const { getErrorMessage, readApiResponse } = load('api/client.ts')
const { validateImportDraft } = load('components/chat-import/chatImportModel.ts')
const draft = { mode: 'NEW_USER_ERROR_TYPE', errorTypeId: 9, name: '', description: '', reviewSourceText: '', referenceAnswer: '' }

test('import validation uses the learning mode for every field and language constraint', () => {
  const validate = (patch, mode) => validateImportDraft({ ...draft, ...patch }, mode, [], [])
  for (const patch of [{}, { mode: 'EXISTING_USER_ERROR_TYPE', userErrorTypeId: '999' },
    { name: 'a'.repeat(129), description: 'a'.repeat(256), reviewSourceText: 'a'.repeat(1001), referenceAnswer: 'あ'.repeat(2001) },
    { reviewSourceText: '中文' }, { reviewSourceText: 'かな' }, { referenceAnswer: 'English' }]) {
    const errors = Object.values(validate(patch, 'EN_TO_JA'))
    assert.ok(errors.length)
    for (const message of errors) assert.doesNotMatch(message, /\p{Script=Han}/u)
  }
  assert.match(validate({}, 'EN_TO_JA').name, /128/)
  assert.match(validate({}, 'EN_TO_JA').description, /255/)
  assert.match(validate({}, 'EN_TO_JA').reviewSourceText, /1000/)
  assert.match(validate({}, 'EN_TO_JA').referenceAnswer, /2000/)
  assert.match(validate({ reviewSourceText: '中文' }, 'EN_TO_JA').reviewSourceText, /Use English/)
  assert.match(validate({ referenceAnswer: 'English' }, 'EN_TO_JA').referenceAnswer, /Japanese kana/)
  for (const message of Object.values(validate({}, 'ZH_TO_JA'))) assert.match(message, /\p{Script=Han}/u)
})

test('all current controller and request DTO validation messages have specific English equivalents', () => {
  const root = path.resolve(__dirname, '../../backend/src/main/java/com/sazare')
  let count = 0
  for (const dir of ['dto', 'controller']) {
    for (const filename of fs.readdirSync(path.join(root, dir))) {
      if (!filename.endsWith('.java')) continue
      const source = fs.readFileSync(path.join(root, dir, filename), 'utf8')
      for (const [, message] of source.matchAll(/message\s*=\s*"([^"]+)"/g)) {
        const translated = englishErrorMessage(message)
        assert.doesNotMatch(translated, /\p{Script=Han}/u, `${filename}: ${message}`)
        assert.notEqual(translated, REQUEST_FAILED_EN, `${filename}: ${message}`)
        count++
      }
    }
  }
  assert.ok(count > 100)
})

test('backend validation retains field paths, bounds, enum choices and actionable errors', () => {
  assert.equal(englishErrorMessage('items[0].name: 复习重点名称不能超过 128 字符'), 'items[0].name: Review focus name must not exceed 128 characters.')
  assert.equal(englishErrorMessage('difficulty 必须在 1 到 5 之间'), 'difficulty must be between 1 and 5.')
  assert.equal(englishErrorMessage('severity 只能是 LOW、MEDIUM 或 HIGH'), 'severity must be one of: LOW, MEDIUM, HIGH.')
  assert.equal(englishErrorMessage('答案长度不能超过 2000 个字符'), 'Answer must not exceed 2000 characters.')
  assert.match(englishErrorMessage('复习卡片尚未到期'), /not due yet/)
  assert.match(englishErrorMessage('Markdown 代码围栏未闭合'), /code fences/)
  assert.match(englishErrorMessage('聊天正文不能超过 30000 字符'), /30,000/)
  assert.match(englishErrorMessage('未找到聊天角色标题，请使用 ## User 或 ## Assistant 标题分隔消息'), /## User or ## Assistant/)
})

test('API errors use English by default, preserve Chinese mode, and safely handle unknown failures', async () => {
  storedMode = null
  assert.match(getErrorMessage(new Error('聊天正文不能超过 30000 字符')), /30,000/)
  assert.equal(getErrorMessage(new Error('Unknown English error.')), 'Unknown English error.')
  assert.equal(getErrorMessage(new Error('__proto__')), '__proto__')
  assert.equal(getErrorMessage(new Error('未收录的中文错误')), REQUEST_FAILED_EN)
  assert.equal(getErrorMessage(null), REQUEST_FAILED_EN)
  assert.equal(getErrorMessage(new TypeError('Failed to fetch')), 'Network connection failed.')
  assert.equal(getErrorMessage(new Error('HTTP 503')), 'Request failed (HTTP 503).')
  storedMode = 'ZH_TO_JA'
  assert.equal(getErrorMessage(new Error('复习卡片尚未到期')), '复习卡片尚未到期')
  assert.match(getErrorMessage(new Error('复习卡片尚未到期'), 'EN_TO_JA'), /not due yet/)
  storedMode = 'EN_TO_JA'
  try {
    await readApiResponse(new Response(JSON.stringify({ code: 40001, message: 'sourceText: sourceText 不能为空', data: null })))
    assert.fail('Expected a validation error')
  } catch (error) {
    assert.equal(getErrorMessage(error), 'sourceText: sourceText is required.')
  }
})
