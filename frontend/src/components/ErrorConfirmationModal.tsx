import { useState } from 'react'
import { createReviewCard } from '../api/userErrorApi'
import { getErrorMessage } from '../api/client'
import type { PracticeNotice } from '../types/api'
import type { AnswerErrorAnalysis } from '../types/review'
import type { ReviewCardCreated, UserErrorType } from '../types/userError'
import type { ErrorCandidateState } from './errorConfirmation'

export type ReviewCardSource =
  | { kind: 'FIXED', sourceText: string }
  | { kind: 'ARTICLE', segments: Array<{ index: number, text: string, referenceText: string }> }
  | { kind: 'CORRECTION' }

export default function ErrorConfirmationModal({ analyses, candidates, userErrorTypes, userErrorTypesLoading, notice, confirming, selectedCount, userAnswerId, reviewCardSource, recommendedExpressions, onUpdate, onConfirm, onCustomSaved, onClose }: {
  analyses: AnswerErrorAnalysis[]
  candidates: ErrorCandidateState[]
  userErrorTypes: UserErrorType[]
  userErrorTypesLoading: boolean
  notice: PracticeNotice | null
  confirming: boolean
  selectedCount: number
  userAnswerId: number
  reviewCardSource: ReviewCardSource
  recommendedExpressions: string[]
  onUpdate: (index: number, patch: Partial<ErrorCandidateState>) => void
  onConfirm: () => Promise<boolean>
  onCustomSaved?: (card: ReviewCardCreated) => void
  onClose: () => void
}) {
  const [customEditing, setCustomEditing] = useState(analyses.length === 0)
  const [customSaving, setCustomSaving] = useState(false)
  const [savingAll, setSavingAll] = useState(false)
  const [customName, setCustomName] = useState('')
  const [targetExpression, setTargetExpression] = useState('')
  const [sourceSegmentIndex, setSourceSegmentIndex] = useState('')
  const [reviewSourceText, setReviewSourceText] = useState('')
  const [customNotice, setCustomNotice] = useState<PracticeNotice | null>(null)
  const articleReferenceText = reviewCardSource.kind === 'ARTICLE' && sourceSegmentIndex
    ? reviewCardSource.segments.find((item) => item.index === Number(sourceSegmentIndex))?.referenceText
    : null
  const visibleRecommendedExpressions = [...new Set(
    (reviewCardSource.kind === 'ARTICLE' ? [articleReferenceText ?? ''] : recommendedExpressions)
      .map((item) => item.trim())
      .filter(Boolean),
  )]
  const busy = confirming || customSaving || savingAll
  const pendingCount = selectedCount + (customEditing ? 1 : 0)

  function resetCustomForm() {
    setCustomName('')
    setTargetExpression('')
    setSourceSegmentIndex('')
    setReviewSourceText('')
  }

  function cancelCustomEditing() {
    if (customSaving || savingAll) return
    resetCustomForm()
    setCustomEditing(false)
    setCustomNotice(null)
  }

  function validateCustomCard() {
    const name = customName.trim()
    const expression = targetExpression.trim()
    if (!name || !expression) {
      setCustomNotice({ kind: 'error', title: '请补充卡片内容', message: '复习重点和目标日语表达均不能为空。' })
      return false
    }
    if (reviewCardSource.kind === 'ARTICLE' && !sourceSegmentIndex) {
      setCustomNotice({ kind: 'error', title: '请选择中文原句', message: '文章复习卡片必须绑定一条中文原句。' })
      return false
    }
    if (reviewCardSource.kind === 'CORRECTION' && !reviewSourceText.trim()) {
      setCustomNotice({ kind: 'error', title: '请填写复习题中文', message: '纯日语纠错需要提供中译日复习题面。' })
      return false
    }
    return true
  }

  async function saveCustomCard() {
    setCustomSaving(true)
    setCustomNotice(null)
    try {
      const card = await createReviewCard(userAnswerId, {
        name: customName.trim(),
        targetExpression: targetExpression.trim(),
        ...(reviewCardSource.kind === 'ARTICLE' ? { sourceSegmentIndex: Number(sourceSegmentIndex) } : {}),
        ...(reviewCardSource.kind === 'CORRECTION' ? { reviewSourceText: reviewSourceText.trim() } : {}),
      })
      onCustomSaved?.(card)
      return true
    } catch (error: unknown) {
      setCustomNotice({ kind: 'error', title: '添加失败', message: getErrorMessage(error) })
      return false
    } finally {
      setCustomSaving(false)
    }
  }

  async function handleSaveAll() {
    if (savingAll || confirming || customSaving) return
    if (customEditing && !validateCustomCard()) return

    setSavingAll(true)
    try {
      if (selectedCount > 0 && !await onConfirm()) return
      if (customEditing && !await saveCustomCard()) return
      onClose()
    } finally {
      setSavingAll(false)
    }
  }

  return <div className="modal-backdrop" role="presentation">
    <section className="error-confirmation error-confirmation-modal" role="dialog" aria-modal="true" aria-label="添加复习卡片">
      <header className="modal-header">
        <div className="section-title"><span className="label">复习卡片</span><strong>{analyses.length === 0 ? '添加复习卡片' : '选择或添加复习内容'}</strong></div>
        <button type="button" className="modal-close" aria-label="关闭" disabled={busy} onClick={onClose}>×</button>
      </header>
      <div className="error-confirmation-body">
        {analyses.length > 0 ? <>
          <div className="error-confirmation-intro"><span className="candidate-count">已选 {selectedCount} 项</span><p className="error-confirmation-hint">AI 分析仅作候选；确认后才会加入复习卡片。</p></div>
          {notice ? <div className={notice.kind === 'error' ? 'notice is-error' : 'notice'}><strong>{notice.title}</strong><p>{notice.message}</p></div> : null}
          {analyses.map((analysis, index) => {
            const candidate = candidates[index]
            if (!candidate) return null
            return <article key={`${analysis.errorTypeCode}-${index}`} className={candidate.selected ? 'candidate-error-item is-selected' : 'candidate-error-item'}>
              <div className="candidate-error-header"><label className="candidate-select"><input type="checkbox" checked={candidate.selected || candidate.saved} disabled={candidate.saved || busy} onChange={(event) => onUpdate(index, { selected: event.target.checked })} /><span>{candidate.saved ? '已添加' : '添加此项'}</span></label><span>{analysis.errorTypeName} / {analysis.severity}</span></div>
              <div className="candidate-error-content"><strong>{analysis.original}</strong><p>{analysis.issue}</p><p>{analysis.suggestion}</p></div>
              {candidate.selected && !candidate.saved ? <div className="candidate-error-controls">
                <div className="choice-grid" role="radiogroup" aria-label="添加方式"><label><input type="radio" name={`mode-${index}`} checked={candidate.mode === 'NEW_USER_ERROR_TYPE'} onChange={() => onUpdate(index, { mode: 'NEW_USER_ERROR_TYPE' })} />新建复习卡片</label><label><input type="radio" name={`mode-${index}`} disabled={userErrorTypes.length === 0 || userErrorTypesLoading} checked={candidate.mode === 'EXISTING_USER_ERROR_TYPE'} onChange={() => onUpdate(index, { mode: 'EXISTING_USER_ERROR_TYPE', userErrorTypeId: '' })} />加入已有复习卡片</label></div>
                {candidate.mode === 'NEW_USER_ERROR_TYPE' ? <div className="error-confirmation-fields"><label><span>卡片名称</span><input value={candidate.userErrorTypeName} maxLength={128} onChange={(event) => onUpdate(index, { userErrorTypeName: event.target.value })} /></label><label><span>卡片说明</span><textarea value={candidate.userErrorTypeDescription} maxLength={255} onChange={(event) => onUpdate(index, { userErrorTypeDescription: event.target.value })} /></label></div> : <label className="existing-error-type-select"><span>复习卡片列表</span><select value={candidate.userErrorTypeId} onChange={(event) => onUpdate(index, { userErrorTypeId: event.target.value })}><option value="">请选择要加入的复习卡片</option>{userErrorTypes.map((item) => <option key={item.id} value={item.id}>{item.name}（{item.errorTypeName}）</option>)}</select></label>}
              </div> : null}
            </article>
          })}
        </> : <p className="candidate-empty-message">本次未发现明确错误。</p>}

        <section className="custom-review-card-section">
          {customNotice ? <div className={customNotice.kind === 'error' ? 'notice is-error' : 'notice'}><strong>{customNotice.title}</strong><p>{customNotice.message}</p></div> : null}
          {!customEditing ? <button type="button" className="primary-button" disabled={busy} onClick={() => { setCustomEditing(true); setCustomNotice(null) }}>{analyses.length > 0 ? '添加自定义复习卡片' : '添加复习卡片'}</button> : <div className="custom-review-card-form">
            <div className="section-title"><span className="label">自定义</span><strong>添加自定义复习卡片</strong></div>
            <label><span>复习重点</span><input value={customName} maxLength={128} disabled={busy} onChange={(event) => setCustomName(event.target.value)} /></label>
            {reviewCardSource.kind === 'FIXED' ? <label><span>复习题中文</span><textarea value={reviewCardSource.sourceText} readOnly /></label> : null}
            {reviewCardSource.kind === 'ARTICLE' ? <label><span>选择中文原句</span><select value={sourceSegmentIndex} disabled={busy} onChange={(event) => setSourceSegmentIndex(event.target.value)}><option value="">请选择</option>{reviewCardSource.segments.map((segment) => <option key={segment.index} value={segment.index}>第 {segment.index + 1} 句：{segment.text}</option>)}</select></label> : null}
            {reviewCardSource.kind === 'CORRECTION' ? <label><span>复习题中文</span><textarea value={reviewSourceText} maxLength={1000} disabled={busy} placeholder="输入用于中译日复习的中文题面" onChange={(event) => setReviewSourceText(event.target.value)} /></label> : null}
            {visibleRecommendedExpressions.length > 0 ? <div className="custom-review-recommendations"><span>{reviewCardSource.kind === 'ARTICLE' ? '所选原句的日语翻译答案' : '推荐日语表达'}</span><ul>{visibleRecommendedExpressions.map((expression) => <li key={expression}>{expression}</li>)}</ul></div> : null}
            <label><span>目标日语表达</span><textarea value={targetExpression} maxLength={2000} disabled={busy} placeholder="输入希望掌握的完整日语表达" onChange={(event) => setTargetExpression(event.target.value)} /></label>
            <div className="custom-review-card-actions"><button type="button" disabled={busy} onClick={cancelCustomEditing}>取消添加自定义卡片</button></div>
          </div>}
        </section>
      </div>
      <footer className="error-confirmation-footer"><button type="button" className="primary-button" disabled={pendingCount === 0 || busy} onClick={() => void handleSaveAll()}>{busy ? '添加中' : `确认添加 ${pendingCount} 项`}</button></footer>
    </section>
  </div>
}
