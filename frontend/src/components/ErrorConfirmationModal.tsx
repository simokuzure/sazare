import type { PracticeNotice } from '../types/api'
import type { AnswerErrorAnalysis } from '../types/review'
import type { UserErrorType } from '../types/userError'
import type { ErrorCandidateState } from './errorConfirmation'

export default function ErrorConfirmationModal({ analyses, candidates, userErrorTypes, userErrorTypesLoading, notice, confirming, selectedCount, onUpdate, onConfirm, onClose }: {
  analyses: AnswerErrorAnalysis[]
  candidates: ErrorCandidateState[]
  userErrorTypes: UserErrorType[]
  userErrorTypesLoading: boolean
  notice: PracticeNotice | null
  confirming: boolean
  selectedCount: number
  onUpdate: (index: number, patch: Partial<ErrorCandidateState>) => void
  onConfirm: () => void
  onClose: () => void
}) {
  if (analyses.length === 0) return null

  return <div className="modal-backdrop" role="presentation">
    <section className="error-confirmation error-confirmation-modal" role="dialog" aria-modal="true" aria-label="记录错误">
      <header className="modal-header">
        <div className="section-title"><span className="label">候选错误</span><strong>确认要记录的错误</strong></div>
        <button type="button" className="modal-close" aria-label="关闭" disabled={confirming} onClick={onClose}>×</button>
      </header>
      <div className="error-confirmation-body">
        <div className="error-confirmation-intro"><span className="candidate-count">已选 {selectedCount} 项</span><p className="error-confirmation-hint">AI 分析仅作候选；勾选并确认后才会写入错题复习。</p></div>
        {notice ? <div className={notice.kind === 'error' ? 'notice is-error' : 'notice'}><strong>{notice.title}</strong><p>{notice.message}</p></div> : null}
        {analyses.map((analysis, index) => {
          const candidate = candidates[index]
          if (!candidate) return null
          return <article key={`${analysis.errorTypeCode}-${index}`} className={candidate.selected ? 'candidate-error-item is-selected' : 'candidate-error-item'}>
            <div className="candidate-error-header"><label className="candidate-select"><input type="checkbox" checked={candidate.selected || candidate.saved} disabled={candidate.saved || confirming} onChange={(event) => onUpdate(index, { selected: event.target.checked })} /><span>{candidate.saved ? '已记录' : '记录此错误'}</span></label><span>{analysis.errorTypeName} / {analysis.severity}</span></div>
            <div className="candidate-error-content"><strong>{analysis.original}</strong><p>{analysis.issue}</p><p>{analysis.suggestion}</p></div>
            {candidate.selected && !candidate.saved ? <div className="candidate-error-controls">
              <div className="choice-grid" role="radiogroup" aria-label="记录方式"><label><input type="radio" name={`mode-${index}`} checked={candidate.mode === 'NEW_USER_ERROR_TYPE'} onChange={() => onUpdate(index, { mode: 'NEW_USER_ERROR_TYPE' })} />新建用户错误类型</label><label><input type="radio" name={`mode-${index}`} disabled={userErrorTypes.length === 0 || userErrorTypesLoading} checked={candidate.mode === 'EXISTING_USER_ERROR_TYPE'} onChange={() => onUpdate(index, { mode: 'EXISTING_USER_ERROR_TYPE', userErrorTypeId: '' })} />追加到已有类型</label></div>
              {candidate.mode === 'NEW_USER_ERROR_TYPE' ? <div className="error-confirmation-fields"><label><span>类型名称</span><input value={candidate.userErrorTypeName} maxLength={128} onChange={(event) => onUpdate(index, { userErrorTypeName: event.target.value })} /></label><label><span>类型说明</span><textarea value={candidate.userErrorTypeDescription} maxLength={255} onChange={(event) => onUpdate(index, { userErrorTypeDescription: event.target.value })} /></label></div> : <label className="existing-error-type-select"><span>错误记录列表</span><select value={candidate.userErrorTypeId} onChange={(event) => onUpdate(index, { userErrorTypeId: event.target.value })}><option value="">请选择要追加的错误记录</option>{userErrorTypes.map((item) => <option key={item.id} value={item.id}>{item.name}（{item.errorTypeName}）</option>)}</select></label>}
            </div> : null}
          </article>
        })}
      </div>
      <footer className="error-confirmation-footer"><button type="button" className="primary-button" disabled={selectedCount === 0 || confirming} onClick={onConfirm}>{confirming ? '记录中' : `确认记录 ${selectedCount} 项`}</button></footer>
    </section>
  </div>
}
