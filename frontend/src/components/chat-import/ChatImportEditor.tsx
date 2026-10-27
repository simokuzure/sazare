import { useState } from 'react'
import { useLanguage } from '../../i18n/LanguageContext'
import type { ChatImportDraft, ImportSeverity } from '../../types/chatImport'
import type { ErrorType } from '../../types/errorType'
import type { ReviewCard } from '../../types/review'
import { matchingImportCard, type DraftErrors, type DraftField } from './chatImportModel'

export default function ChatImportEditor({ draft, candidateCount, types, cards, busy, errors, onUpdate, onBlur, onBack }: {
  draft: ChatImportDraft; candidateCount: number; types: ErrorType[]; cards: ReviewCard[]; busy: boolean; errors: DraftErrors
  onUpdate: (patch: Partial<ChatImportDraft>) => void; onBlur: () => void; onBack: () => void
}) {
  const { learningMode, english, text } = useLanguage()
  const [cardSearch, setCardSearch] = useState('')
  const matched = matchingImportCard(draft, cards)
  const selectedCard = cards.find(card => card.userErrorTypeId === Number(draft.userErrorTypeId))
  const matchesSearch = (card: ReviewCard) => `${card.userErrorTypeName} ${card.errorTypeName}`.toLocaleLowerCase().includes(cardSearch.trim().toLocaleLowerCase())
    || card.userErrorTypeId === Number(draft.userErrorTypeId)
  const availableCards = cards.filter(card => types.some(type => type.id === card.errorTypeId))
  const options = availableCards.filter(matchesSearch).sort((a, b) => Number(b.errorTypeId === draft.errorTypeId) - Number(a.errorTypeId === draft.errorTypeId))
  const labels: Record<DraftField, string> = {
    errorTypeId: text('学习分类', 'Learning category'), name: text('复习重点名称', 'Learning focus'),
    description: text('重点说明', 'Focus description'), userErrorTypeId: text('目标复习卡片', 'Target review card'),
    reviewSourceText: learningMode === 'ZH_TO_JA' ? '复习题面（中文）' : 'Review prompt (English)',
    referenceAnswer: text('日语参考答案', 'Japanese reference answer'),
  }
  const attrs = (field: DraftField) => ({ id: `import-${draft.key}-${field}`, 'aria-label': labels[field], 'aria-invalid': Boolean(errors[field]),
    'aria-describedby': errors[field] ? `import-error-${field}` : undefined, onBlur })
  const error = (field: DraftField) => errors[field] ? <span className="import-field-error" id={`import-error-${field}`}>{errors[field]}</span> : null

  return <>
    <section className="surface import-analysis" aria-label={text('原文与分析', 'Source and analysis')}>
      <div className="import-panel-heading">
        <button type="button" className="import-mobile-back" onClick={onBack}>{text('← 候选列表', '← Candidates')}</button>
        <h3>{text('原文与分析', 'Source and analysis')}</h3><span>{text(`候选 ${draft.key + 1} / ${candidateCount}`, `Candidate ${draft.key + 1} / ${candidateCount}`)}</span>
      </div>
      <div className="import-analysis-body">
        <div className="import-detail-title"><h4 id="import-detail-heading" tabIndex={-1}>{draft.name || text('未命名复习重点', 'Untitled focus')}</h4>
          <span>{(() => { const type = types.find(item => item.id === draft.errorTypeId); return type ? english ? type.nameEn || type.code : type.name : `#${draft.errorTypeId}` })()}</span>
          <small>{text(`严重程度：${draft.severity === 'LOW' ? '低' : draft.severity === 'HIGH' ? '高' : '中'}`, `Severity: ${draft.severity}`)}</small></div>
        <section className="import-evidence">
          <h4>{text(`原文引用 · ${draft.evidence.length} 处`, `Source excerpts · ${draft.evidence.length}`)}</h4>
          {draft.evidence.map((evidence, index) => <blockquote key={index}>
            <span>{text(`用户消息 #${evidence.messageId}`, `User message #${evidence.messageId}`)}</span>
            <p lang="ja">{evidence.originalText}</p>
          </blockquote>)}
        </section>
        <section className="import-correction"><h4>{text('AI 修正或优化表达', 'AI correction or improvement')}</h4><p lang="ja">{draft.correctedExpression}</p></section>
        <section className="import-explanation"><h4>{text('重点说明', 'Focus description')}</h4><p>{draft.description}</p></section>
      </div>
    </section>
    <section className="surface import-editor" aria-label={text('候选详情', 'Candidate details')}>
      <div className="import-panel-heading"><h3>{text('复习卡片内容', 'Review card content')}</h3>
        <label className="import-select-item"><input type="checkbox" aria-label={text('选择此项', 'Select item')} checked={draft.selected} disabled={busy || draft.saved}
          onChange={event => onUpdate({ selected: event.target.checked })} />
          <span>{draft.saved ? text('此项已保存', 'Item saved') : draft.selected ? text('已选择此项', 'Item selected') : text('选择此项', 'Select item')}</span></label></div>
      <div className="import-editor-body">
        <fieldset className="import-editor-fields" disabled={busy || draft.saved}>
          <legend className="sr-only">{text('复习卡片内容', 'Review card content')}</legend>
          <div className="import-method" role="radiogroup" aria-label={text('添加方式', 'Add method')}>
            <label><input type="radio" name={`import-mode-${draft.key}`} checked={draft.mode === 'NEW_USER_ERROR_TYPE'}
              onChange={() => onUpdate({ mode: 'NEW_USER_ERROR_TYPE' })} />{text('新建复习卡片', 'Create a card')}</label>
            <label><input type="radio" name={`import-mode-${draft.key}`} checked={draft.mode === 'EXISTING_USER_ERROR_TYPE'}
              disabled={!availableCards.length} onChange={() => onUpdate({ mode: 'EXISTING_USER_ERROR_TYPE' })} />{text('加入已有卡片', 'Add to a card')}</label>
          </div>
          <div className="import-field-row">
            {draft.mode === 'NEW_USER_ERROR_TYPE' ? <label><span>{text('学习分类', 'Learning category')}</span>
              <select {...attrs('errorTypeId')} value={draft.errorTypeId} onChange={event => onUpdate({ errorTypeId: Number(event.target.value) })}>
                {!types.some(type => type.id === draft.errorTypeId) ? <option value={draft.errorTypeId}>{text('请选择可用分类', 'Choose an available category')}</option> : null}
                {types.map(type => <option key={type.id} value={type.id}>{english ? type.nameEn || type.code : type.name}</option>)}
              </select>{error('errorTypeId')}</label> : null}
            <label><span>{text('严重程度', 'Severity')}</span><select value={draft.severity} onChange={event => onUpdate({ severity: event.target.value as ImportSeverity })}>
              <option value="LOW">{text('低', 'Low')}</option><option value="MEDIUM">{text('中', 'Medium')}</option><option value="HIGH">{text('高', 'High')}</option>
            </select></label>
          </div>
          {draft.mode === 'NEW_USER_ERROR_TYPE' ? <>
            <label><span>{text('复习重点名称', 'Learning focus')}</span><input {...attrs('name')} value={draft.name} maxLength={128}
              onChange={event => onUpdate({ name: event.target.value })} />{error('name')}</label>
            <label><span>{text('重点说明', 'Focus description')}</span><textarea {...attrs('description')} value={draft.description} maxLength={255} rows={3}
              onChange={event => onUpdate({ description: event.target.value })} />{error('description')}</label>
            {matched ? <p className="import-impact">{text('同分类下已有同名重点，将加入已有卡片：', 'A matching focus exists. This will add to: ')}<strong>{matched.userErrorTypeName}</strong></p> : null}
          </> : <>
            <label><span>{text('搜索已有卡片', 'Search existing cards')}</span><input type="search" value={cardSearch}
              onChange={event => setCardSearch(event.target.value)} /></label>
            <label><span>{text('目标复习卡片', 'Target review card')}</span><select {...attrs('userErrorTypeId')} value={draft.userErrorTypeId}
              onChange={event => onUpdate({ userErrorTypeId: event.target.value })}>
              <option value="">{text('请选择已有卡片', 'Choose a card')}</option>
              {(['ACTIVE', 'MASTERED'] as const).map(status => <optgroup key={status} label={status === 'ACTIVE' ? text('复习中', 'Active') : text('已掌握', 'Mastered')}>
                {options.filter(card => card.status === status).map(card => <option key={card.id} value={card.userErrorTypeId}>{card.userErrorTypeName} · {card.errorTypeName}</option>)}
              </optgroup>)}
            </select>{error('userErrorTypeId')}</label>
            {selectedCard ? <div className="import-target-info"><strong>{selectedCard.userErrorTypeName}</strong><span>{selectedCard.errorTypeName}</span><p>{selectedCard.userErrorTypeDescription}</p></div> : null}
          </>}
          {matched ? <p className="import-impact">{matched.status === 'MASTERED'
            ? text('确认后将重新激活这张卡片，开启新的复习周期。', 'Confirmation reactivates this card with a new review cycle.')
            : text('确认后会加入当前周期，并增加一次失败记录。', 'Confirmation adds questions to the current cycle and records one failure.')}</p> : null}
          <div className="import-question-fields">
            <label><span>{learningMode === 'ZH_TO_JA' ? '复习题面（中文）' : 'Review prompt (English)'}</span>
              <textarea {...attrs('reviewSourceText')} value={draft.reviewSourceText} maxLength={1000} rows={3}
                onChange={event => onUpdate({ reviewSourceText: event.target.value })} />{error('reviewSourceText')}</label>
            <label><span>{text('日语参考答案', 'Japanese reference answer')}</span>
              <textarea {...attrs('referenceAnswer')} lang="ja" value={draft.referenceAnswer} maxLength={2000} rows={4}
                onChange={event => onUpdate({ referenceAnswer: event.target.value })} />{error('referenceAnswer')}</label>
          </div>
        </fieldset>
      </div>
    </section>
  </>
}
