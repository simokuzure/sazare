const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

// 使用项目已有 TypeScript 转译纯函数，无需引入测试框架或运行时依赖。
function load(relative) {
  const source = fs.readFileSync(path.join(__dirname, '../src', relative), 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
  const exports = {}
  vm.runInNewContext(code, { exports, require, fetch: (...args) => global.fetch(...args), DOMException, AbortController, setTimeout, clearTimeout })
  return exports
}
const model = load('components/chat-import/chatImportModel.ts')
const api = load('api/chatImportApi.ts')
const candidate = { errorTypeId: 1, name: '前に接续', description: '使用辞书形', severity: 'MEDIUM',
  evidence: [{ messageId: 1, originalText: '来た前に' }], correctedExpression: '来る前に',
  reviewSourceText: '来日本之前学习过。', referenceAnswer: '日本に来る前に勉強しました。' }
const types = [{ id: 1 }]
const cards = [{ id: 10, userErrorTypeId: 101, errorTypeId: 1, userErrorTypeName: '前に接续', status: 'MASTERED' }]

test('drafts are unselected; payload excludes chat evidence and existing-card metadata', () => {
  const draft = model.createImportDraft(candidate, 0)
  assert.equal(draft.selected, false)
  assert.equal(draft.saved, false)
  const item = model.toImportItem({ ...draft, mode: 'EXISTING_USER_ERROR_TYPE', userErrorTypeId: '101' })
  assert.equal(item.userErrorTypeId, 101)
  for (const key of ['evidence', 'correctedExpression', 'errorTypeId', 'name', 'description']) assert.equal(key in item, false)
})

test('target count follows backend name/category reuse, including mastered cards', () => {
  const draft = model.createImportDraft(candidate, 0)
  assert.equal(model.matchingImportCard(draft, cards).id, 10)
  assert.equal(model.importTargetCount([draft, { ...draft, mode: 'EXISTING_USER_ERROR_TYPE', userErrorTypeId: '101' }], cards), 1)
  assert.equal(model.importTargetCount([draft, { ...draft, name: '另一个重点' }], cards), 2)
})

test('validation covers unavailable targets, source language and Japanese answer', () => {
  const draft = model.createImportDraft(candidate, 0)
  assert.equal(Object.keys(model.validateImportDraft(draft, 'ZH_TO_JA', types, cards)).length, 0)
  assert.equal(Object.keys(model.validateImportDraft({ ...draft, reviewSourceText: 'Before coming to Japan, I studied.' }, 'EN_TO_JA', types, cards)).length, 0)
  const errors = model.validateImportDraft({ ...draft, errorTypeId: 9, name: '', description: '', referenceAnswer: 'English', reviewSourceText: '日本に来る' }, 'ZH_TO_JA', types, cards)
  for (const key of ['errorTypeId', 'name', 'description', 'referenceAnswer', 'reviewSourceText']) assert.ok(errors[key])
  assert.ok(model.validateImportDraft({ ...draft, mode: 'EXISTING_USER_ERROR_TYPE', userErrorTypeId: '999' }, 'ZH_TO_JA', types, cards).userErrorTypeId)
  assert.ok(model.validateImportDraft({ ...draft, mode: 'EXISTING_USER_ERROR_TYPE', userErrorTypeId: '101' }, 'ZH_TO_JA', [], cards).userErrorTypeId)
})

test('confirmation differentiates explicit business rejection from uncertain results; never retries', async () => {
  const original = global.fetch
  try {
    let calls = 0
    global.fetch = async () => { calls++; return new Response(JSON.stringify({ code: 40001, message: '中文校验提示' })) }
    await assert.rejects(api.confirmChatImport({}), error => error.message === '中文校验提示' && error.uncertain === false)
    assert.equal(calls, 1)
    global.fetch = async () => { calls++; throw new TypeError('network') }
    await assert.rejects(api.confirmChatImport({}), error => error.uncertain === true)
    assert.equal(calls, 2)
    global.fetch = async () => new Response('{}')
    await assert.rejects(api.confirmChatImport({}), error => error.uncertain === true)
    global.fetch = async () => new Response(JSON.stringify({ code: 0, data: { cards: [] } }))
    await assert.rejects(api.confirmChatImport({}), error => error.uncertain === true)
  } finally { global.fetch = original }
})
