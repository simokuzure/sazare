import { useEffect, useMemo, useRef, useState } from 'react'
import PageHeader from '../PageHeader'
import StatusNotice from '../StatusNotice'
import { useLanguage } from '../../i18n/LanguageContext'
import type { LeaveGuardListener } from '../../types/chatImport'
import ChatImportEditor from './ChatImportEditor'
import ChatImportSourceDialog from './ChatImportSourceDialog'
import ChatImportUpload from './ChatImportUpload'
import { matchingImportCard } from './chatImportModel'
import { formatDateTime } from '../review/reviewPresentation'
import { useChatImport } from './useChatImport'
import '../../styles/chat-import.css'

export default function ChatImportWorkbench({ onLeaveGuardChange, onBack, onViewCard, onSaved }: {
  onLeaveGuardChange: LeaveGuardListener; onBack: () => void; onViewCard: (id: number) => void; onSaved: () => void
}) {
  const { english, text } = useLanguage()
  const state = useChatImport(onLeaveGuardChange, onSaved)
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const [status, setStatus] = useState('PENDING')
  const [checkingCards, setCheckingCards] = useState(false)
  const [showExportPrompt, setShowExportPrompt] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const previousActive = useRef<number | null>(null)
  const busy = state.analyzing || state.confirming || state.reading
  const selected = state.drafts.filter(draft => draft.selected && !draft.saved)
  const visible = useMemo(() => state.drafts.filter(draft =>
    (status === 'ALL' || (status === 'SAVED' ? draft.saved : !draft.saved))
    && (!category || draft.errorTypeId === Number(category))
    && `${draft.name} ${draft.description}`.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase())),
  [state.drafts, category, search, status])
  const active = state.drafts.find(draft => draft.key === state.activeKey)
  const savedCount = state.drafts.filter(draft => draft.saved).length
  const actionable = visible.filter(draft => !draft.saved)
  const allSelected = actionable.length > 0 && actionable.every(draft => draft.selected)
  const currentResults = state.batches.at(-1)
  const hasFeedback = Boolean(state.notice || state.uncertain || state.typesError || state.cardsError || currentResults)
  const prompt = text(`请将本次对话精简导出为 UTF-8 Markdown，供日语纠错分析使用。

- 完整保留包含用户本人日语表达的消息原文，不纠正、改写或因认为正确而省略；保留引用标记。
- 保留理解用户原意必需的上下文，包括用户回复所回应的必要问题，以及 AI 针对日语表达的纠正和解释。
- 删除其他无关内容、重复说明和纯操作指令；不确定是否相关时保留。
- 不新增总结、错误清单或解释。
- 保持角色和消息顺序，用户消息使用独占一行的 ## User 标题，AI 消息使用 ## Assistant 标题，省略空消息。
- 代码围栏须闭合，不给整个文件添加代码围栏。
- 无法访问完整对话时明确说明。`,
    `Export a condensed version of this conversation as UTF-8 Markdown for Japanese error analysis.

- Preserve verbatim every message containing the user's own Japanese, including quotation markers. Do not correct, rewrite, or omit it because it seems correct.
- Keep context needed to understand the user's intended meaning, including questions the user is responding to when needed for context, and the AI's corrections and explanations of Japanese expressions.
- Remove other irrelevant content, repeated explanations, and purely operational instructions. Keep content when unsure of its relevance.
- Do not add summaries, error lists, or explanations.
- Preserve roles and message order. Use ## User for user messages and ## Assistant for AI messages, each on a separate line. Omit empty messages.
- Close all code fences. Do not wrap the entire file in a code fence.
- State explicitly if you cannot access the complete conversation.`)

  useEffect(() => {
    if (!state.hasAnalysis) { setSearch(''); setCategory(''); setStatus('PENDING'); setCheckingCards(false) }
  }, [state.hasAnalysis])

  useEffect(() => {
    if (state.activeKey !== previousActive.current) {
      document.getElementById(state.activeKey === null ? 'import-list-heading' : 'import-detail-heading')?.focus()
      previousActive.current = state.activeKey
    }
  }, [state.activeKey])

  function leave(action: () => void) { if (state.mayLeave()) action() }
  function startAnother() { if (state.mayLeave()) { input.current?.click() } }
  async function confirm() {
    await state.confirmSelected()
    requestAnimationFrame(() => document.querySelector<HTMLElement>('.import-editor [aria-invalid="true"]')?.focus())
  }

  return <section className={`page-content chat-import-page${state.drafts.length ? ' has-analysis' : ''}`} aria-label={text('导入聊天记录', 'Import chat history')}>
    <PageHeader eyebrow={text('复习卡片 / 聊天导入', 'Review cards / Chat import')} title={text('聊天分析', 'Chat analysis')}
      description={text('检查 AI 识别的错误，整理后加入复习。', 'Check the AI findings and prepare them for review.')}
      actions={<button type="button" disabled={state.confirming} onClick={() => leave(onBack)}>{text('返回复习列表', 'Back to review cards')}</button>} />
    <ChatImportUpload file={state.file} reading={state.reading} analyzing={state.analyzing} confirming={state.confirming}
      hasAnalysis={state.hasAnalysis} inputRef={input} onSelectFiles={state.selectFiles} onAnalyze={state.analyze} onShowPrompt={() => setShowExportPrompt(true)} />
    {showExportPrompt ? <ChatImportSourceDialog prompt={prompt} onClose={() => setShowExportPrompt(false)} /> : null}
    {state.reading || state.analyzing ? <p className="import-progress" role="status">{state.reading ? text('正在读取文件…', 'Reading file…') : text('正在分析聊天记录，请稍候。', 'Analyzing the conversation. Please wait.')}</p> : null}
    {hasFeedback ? <div className="import-feedback">
      {state.notice ? <StatusNotice notice={state.notice} /> : null}
      {state.uncertain ? <>
        <button type="button" disabled={state.cardsLoading} onClick={() => { setCheckingCards(true); state.reloadCards() }}>{text('先检查复习卡片', 'Check review cards first')}</button>
        {checkingCards ? <section className="surface import-results" aria-label={text('当前目标卡片状态', 'Current target card status')}>
          <h3>{text('刷新后的卡片状态（候选内容仍保留）', 'Refreshed card status (candidates retained)')}</h3>
          {state.cardsLoading ? <p role="status">{text('正在检查卡片…', 'Checking cards…')}</p> : <ul>{[...new Map(selected.map(draft => {
            const card = matchingImportCard(draft, state.cards)
            return [card ? `card-${card.id}` : `new-${draft.errorTypeId}-${draft.name.trim()}`, { card, draft }] as const
          })).values()].map(({ card, draft }) => <li key={card?.id ?? draft.key}><div><strong>{card?.userErrorTypeName ?? draft.name}</strong>
            <p>{card ? text(`${card.status === 'MASTERED' ? '已掌握' : '复习中'} · 周期 ${card.progress.cycleNo} · 累计失败 ${card.progress.failedReviewCount} · 原题 ${card.progress.originalQuestionCount} · 下次 ${formatDateTime(card.dueAt)}`,
              `${card.status} · Cycle ${card.progress.cycleNo} · Failures ${card.progress.failedReviewCount} · Questions ${card.progress.originalQuestionCount} · Next ${formatDateTime(card.dueAt)}`)
              : text('未找到匹配卡片，不能据此确定上次提交未成功。', 'No matching card found. This does not establish that the submission failed.')}</p></div></li>)}</ul>}
          <p>{text('请结合原有进度判断，系统不会据此自动认定保存成功或失败。', 'Compare with the earlier progress. These values do not automatically establish whether the submission succeeded.')}</p>
        </section> : null}
      </> : null}
      {state.typesError ? <StatusNotice notice={{ kind: 'error', title: text('分类加载失败', 'Could not load categories'), message: state.typesError }} actionLabel={text('重新加载分类', 'Reload categories')} onAction={state.reloadTypes} /> : null}
      {state.cardsError ? <StatusNotice notice={{ kind: 'error', title: text('卡片列表刷新失败', 'Could not refresh cards'), message: state.cardsError }} actionLabel={text('重新加载卡片', 'Reload cards')} onAction={state.reloadCards} /> : null}
      {currentResults ? <section className="surface import-results" aria-label={text('本批次保存结果', 'Latest batch results')}>
        <h3>{text(`本批次：新建 ${currentResults.filter(c => c.action === 'CREATED').length} · 更新 ${currentResults.filter(c => c.action === 'UPDATED').length} · 重新激活 ${currentResults.filter(c => c.action === 'REACTIVATED').length}`,
          `Latest batch: ${currentResults.filter(c => c.action === 'CREATED').length} created · ${currentResults.filter(c => c.action === 'UPDATED').length} updated · ${currentResults.filter(c => c.action === 'REACTIVATED').length} reactivated`)}</h3>
        <ul>{currentResults.map(card => <li key={card.cardId}><span>{state.cards.find(c => c.id === card.cardId)?.userErrorTypeName ?? text(`卡片 #${card.cardId}`, `Card #${card.cardId}`)}</span>
          <button type="button" onClick={() => leave(() => onViewCard(card.cardId))}>{text('查看卡片', 'View card')}</button></li>)}</ul>
      </section> : null}
    </div> : null}
    {state.hasAnalysis && !state.drafts.length ? <div className="surface empty-state" role="status">{text('未发现明确错误。可以更换文件继续分析。', 'No clear errors found. You can analyze another file.')}</div> : null}
    {state.drafts.length ? <>
      <div className="import-summary-row">
        <div className="import-summary"><h3>{text('分析结果', 'Analysis results')} <span>{state.drafts.length}</span></h3>
          <span role="status" aria-live="polite">{text(`已选 ${selected.length} 项 · 已保存 ${savedCount} 项`, `${selected.length} selected · ${savedCount} saved`)}</span></div>
        <button type="button" className="primary-button import-confirm-button" disabled={busy || !selected.length || state.typesLoading || state.cardsLoading || Boolean(state.typesError || state.cardsError)} onClick={() => void confirm()}>
          {state.confirming ? text('正在保存…', 'Saving…') : state.typesLoading || state.cardsLoading ? text('正在加载卡片与分类…', 'Loading cards and categories…') : state.uncertain ? text(`仍要重新提交 ${selected.length} 项`, `Submit ${selected.length} items again`) : text(`确认所选 ${selected.length} 项`, `Confirm ${selected.length} selected`)}</button>
      </div>
      <div className={`import-workspace${active ? ' has-detail' : ''}`}>
        <aside className="surface import-candidate-list" aria-label={text('候选列表', 'Candidates')}>
          <div className="import-panel-heading"><h3 id="import-list-heading" tabIndex={-1}>{text('错误候选', 'Error candidates')}</h3><span>{text(`${visible.length} 项`, `${visible.length} items`)}</span></div>
          <div className="import-filters">
            <label><span className="sr-only">{text('搜索候选', 'Search candidates')}</span><input type="search" placeholder={text('搜索候选…', 'Search candidates…')} value={search} onChange={event => setSearch(event.target.value)} /></label>
            <div className="import-filter-row"><label><span className="sr-only">{text('分类', 'Category')}</span><select value={category} onChange={event => setCategory(event.target.value)}>
              <option value="">{text('全部分类', 'All categories')}</option>{state.types.map(type => <option key={type.id} value={type.id}>{english ? type.nameEn || type.name : type.name}</option>)}</select></label>
              <label><span className="sr-only">{text('状态', 'Status')}</span><select value={status} onChange={event => setStatus(event.target.value)}><option value="PENDING">{text('待处理', 'Pending')}</option><option value="SAVED">{text('已保存', 'Saved')}</option><option value="ALL">{text('全部', 'All')}</option></select></label></div>
            <button type="button" disabled={busy || !actionable.length} onClick={() => actionable.forEach(draft => state.updateDraft(draft.key, { selected: !allSelected }))}>
              {allSelected ? text('取消选择筛选结果', 'Deselect filtered items') : text('选择筛选结果', 'Select filtered items')}</button>
          </div>
          <ul>{visible.map(draft => <li key={draft.key} className={`${draft.key === state.activeKey ? 'is-active' : ''}${draft.selected && !draft.saved ? ' is-selected' : ''}`}>
            <button type="button" aria-current={draft.key === state.activeKey ? 'true' : undefined} onClick={() => state.setActiveKey(draft.key)}>
              <strong>{draft.name || text('未命名复习重点', 'Untitled focus')}</strong><span className="import-candidate-meta"><span>{(() => { const type = state.types.find(t => t.id === draft.errorTypeId); return type ? english ? type.nameEn || type.name : type.name : `#${draft.errorTypeId}` })()}</span>
                <small>{text(`${draft.severity === 'LOW' ? '低' : draft.severity === 'HIGH' ? '高' : '中'} · ${draft.evidence.length} 处引用 · ${draft.saved ? '已保存' : draft.selected ? '已选择' : '待确认'}`,
                  `${draft.severity} · ${draft.evidence.length} excerpts · ${draft.saved ? 'Saved' : draft.selected ? 'Selected' : 'Pending'}`)}</small></span>
            </button></li>)}</ul>
          {!visible.length ? <p className="import-list-empty">{text('没有符合筛选条件的候选。', 'No candidates match the filters.')}</p> : null}
        </aside>
        {active ? <ChatImportEditor key={active.key} draft={active} candidateCount={state.drafts.length} types={state.types} cards={state.cards} busy={busy} errors={state.errors[active.key] ?? {}}
            onUpdate={patch => state.updateDraft(active.key, patch)} onBlur={() => state.checkDraft(active.key)} onBack={() => state.setActiveKey(null)} />
          : <section className="surface import-editor-placeholder"><h3>{text('逐项检查，再加入复习', 'Inspect each item before saving')}</h3><p>{text('选择左侧候选，查看原文引用，编辑题面和答案。', 'Choose a candidate to inspect its source excerpts and edit the review question.')}</p></section>}
      </div>
      {savedCount === state.drafts.length ? <div className="import-complete" role="status"><p>{text('本次候选已全部保存，将按卡片的到期安排进入复习。', 'All candidates are saved and will follow their review schedules.')}</p>
        <button type="button" onClick={onBack}>{text('返回复习列表', 'Back to review cards')}</button><button type="button" onClick={startAnother}>{text('导入另一份聊天', 'Import another conversation')}</button></div> : null}
    </> : null}
  </section>
}
