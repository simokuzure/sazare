import type { ReactNode } from 'react'
import SpeechInput from '../SpeechInput'
import { useSpeechInput } from '../../hooks/useSpeechInput'
import PageHeader from '../PageHeader'
import ReviewList from '../ReviewList'
import StatusNotice from '../StatusNotice'
import { useLanguage } from '../../i18n/LanguageContext'
import type { PracticeNotice } from '../../types/api'
import type {
  ReviewAttemptHistory,
  ReviewAttemptResult,
  ReviewCardDetail,
  ReviewCycleProgress,
} from '../../types/review'
import type { ErrorCandidateState } from '../errorConfirmation'
import { scoreToneClassName } from '../../utils/score'
import { getTagDisplayName } from '../../utils/tag'
import { formatDateTime } from './reviewPresentation'

export function ReviewCardOverview({ detail, loading, error, notice, deleting, onStart, onStartEarly, onReload, onBack, onDelete }: {
  detail: ReviewCardDetail | null
  loading: boolean
  error: string | null
  notice: PracticeNotice | null
  deleting: boolean
  onStart: () => void
  onStartEarly: () => void
  onReload: () => void
  onBack: () => void
  onDelete: () => void
}) {
  const { text } = useLanguage()
  return <section className="page-content target-page review-page" aria-label={text('复习卡片查看', 'Review card details')}>
    <PageHeader eyebrow={text('复习卡片', 'Review cards')} title={text('复习卡片详情', 'Review card details')} actions={<><button type="button" disabled={deleting} onClick={onBack}>{text('返回列表', 'Back to list')}</button><button type="button" disabled={loading || deleting} onClick={onReload}>{text('重新加载', 'Reload')}</button><button type="button" className="danger-button" disabled={!detail || deleting} onClick={onDelete}>{deleting ? text('删除中', 'Deleting') : text('删除卡片', 'Delete card')}</button></>} />
    <section className="surface review-surface review-detail-surface">
      {notice ? <StatusNotice notice={notice} className="review-notice" /> : null}
      {loading && !detail ? <p className="loading-text" role="status" aria-live="polite">{text('正在加载卡片详情...', 'Loading card details...')}</p> : null}
      {error ? <StatusNotice notice={{ kind: 'error', title: text('卡片加载失败', 'Could not load card'), message: error }} className="review-notice" actionLabel={text('重新加载', 'Reload')} onAction={onReload} /> : null}
      {detail ? <>
        {detail.progress ? <ReviewCycleSummary detail={detail} progress={detail.progress} /> : null}
        <ReviewCardHeading detail={detail} showDescription action={<ReviewOverviewAction detail={detail} onStart={onStart} onStartEarly={onStartEarly} />} />
        <ReviewAttemptHistoryList attempts={detail.reviewAttempts} />
      </> : null}
    </section>
  </section>
}

export function ReviewDetailView({ detail, loading, error, answerText, submitting, derivedGenerating, earlyReview, notice, onAnswerChange, onSubmit, onGenerate, onReload, onBack }: {
  detail: ReviewCardDetail | null
  loading: boolean
  error: string | null
  answerText: string
  submitting: boolean
  derivedGenerating: boolean
  earlyReview: boolean
  notice: PracticeNotice | null
  onAnswerChange: (value: string) => void
  onSubmit: () => void
  onGenerate: () => void
  onReload: () => void
  onBack: () => void
}) {
  const { text, learningMode } = useLanguage()
  const speech = useSpeechInput({
    contextKey: `${learningMode}:${detail?.id}:${detail?.currentQuestion?.cycleQuestionId}`,
    enabled: detail?.reviewState === 'READY' && !!detail.currentQuestion && !loading && !submitting && !derivedGenerating,
    value: answerText,
    maxLength: 2000,
    onChange: onAnswerChange,
  })
  return <section className="page-content target-page review-page" aria-label={text('复习答题', 'Review answer')}>
    <PageHeader eyebrow={text('复习卡片', 'Review cards')} title={earlyReview ? text('提前复习', 'Early review') : text('复习答题', 'Review answer')} actions={<><button type="button" disabled={submitting} onClick={onBack}>{text('返回列表', 'Back to list')}</button><button type="button" disabled={loading || submitting || derivedGenerating} onClick={onReload}>{text('重新加载', 'Reload')}</button></>} />
    <section className="surface review-surface review-detail-surface">
      {loading && !detail ? <p className="loading-text" role="status" aria-live="polite">{text('正在加载卡片详情...', 'Loading card details...')}</p> : null}
      {error ? <StatusNotice notice={{ kind: 'error', title: text('卡片加载失败', 'Could not load card'), message: error }} className="review-notice" actionLabel={text('重新加载', 'Reload')} onAction={onReload} /> : null}
      {detail ? <>
        {detail.progress ? <ReviewCycleSummary detail={detail} progress={detail.progress} /> : null}
        {earlyReview ? <div className="notice review-early-notice"><strong>{text('提前复习', 'Early review')}</strong><p>{text('本次作答会正式更新周期进度，下次复习时间从今天开始计算。', 'This attempt updates the cycle and recalculates the next review from today.')}</p></div> : null}
        {notice ? <StatusNotice notice={notice} className="review-notice" /> : null}
        {detail.reviewState === 'READY' && detail.currentQuestion ? <div className="review-attempt-grid">
          <section className="review-question-block"><div className="section-title"><span className="label">{detail.currentQuestion.questionRole === 'DERIVED' ? text('衍生题', 'Derived question') : text('原题', 'Original question')}</span><strong>{text('请翻译为日语', 'Translate into Japanese')}</strong></div><p className="review-question-source">{detail.currentQuestion.sourceText}</p>{detail.currentQuestion.contextText ? <p className="review-question-context"><strong>{text('语境：', 'Context: ')}</strong>{detail.currentQuestion.contextText}</p> : null}<QuestionMetadata detail={detail} /></section>
          <section className="review-answer-block"><div className="section-title"><span className="label">{text('作答', 'Answer')}</span><strong>{text('输入日语答案', 'Enter your Japanese answer')}</strong></div><textarea aria-label={text('复习日语答案', 'Review answer in Japanese')} value={answerText} maxLength={2000} disabled={submitting || speech.busy} placeholder={text('请输入日语答案', 'Enter your Japanese answer')} onChange={(event) => onAnswerChange(event.target.value)} /><SpeechInput speech={speech}><div className="review-answer-footer"><div className="action-row"><button type="button" disabled={submitting || speech.busy || answerText.length === 0} onClick={() => onAnswerChange('')}>{text('清空', 'Clear')}</button><button type="button" className="primary-button" disabled={submitting || speech.busy || !answerText.trim()} onClick={onSubmit}>{submitting ? text('评分中', 'Scoring') : earlyReview ? text('提交提前复习', 'Submit early review') : text('提交答案', 'Submit answer')}</button></div></div></SpeechInput></section>
        </div> : null}
        {detail.reviewState === 'READY' && detail.currentQuestion ? <p className="review-answer-helper">{text('提交后将显示评分、标准答案和下一步复习安排。', 'After submitting, you will see the score, standard answers, and next review steps.')}</p> : null}
        {detail.reviewState === 'WAITING' ? <StateMessage title={text('等待下次复习', 'Waiting for next review')} message={text(`下次到期时间：${formatDateTime(detail.dueAt)}。到期前不能提交答案。`, `Next due: ${formatDateTime(detail.dueAt)}. Answers cannot be submitted before then.`)} /> : null}
        {detail.reviewState === 'DERIVED_GENERATION_REQUIRED' ? <StateMessage title={text('需要生成衍生题', 'Generate a derived question')} message={text('本周期原题已通过，但净成功尚未达到 4，生成一道衍生题继续积累净成功。', 'The original question passed, but net successes are below 4. Generate a derived question to continue.')} actionLabel={derivedGenerating ? text('生成中', 'Generating') : text('生成衍生题', 'Generate question')} actionDisabled={derivedGenerating} onAction={onGenerate} /> : null}
        {detail.reviewState === 'MASTERED' ? <StateMessage title={text('本周期已掌握', 'Cycle mastered')} message={text(`完成时间：${formatDateTime(detail.masteredAt)}。再次在普通练习中记录同一复习重点时，会开启下一周期。`, `Completed: ${formatDateTime(detail.masteredAt)}. Saving the same focus from regular practice starts a new cycle.`)} /> : null}
      </> : null}
    </section>
  </section>
}

function ReviewCardHeading({ detail, showDescription, action }: { detail: ReviewCardDetail; showDescription: boolean; action?: ReactNode }) {
  const { text } = useLanguage()
  return <div className="review-card-heading"><div><span className="label">{detail.errorTypeName} · {detail.errorTypeCode}</span><h2>{detail.userErrorTypeName}</h2>{showDescription ? <p>{detail.userErrorTypeDescription || text('暂无说明', 'No description')}</p> : null}</div>{action}</div>
}

function ReviewOverviewAction({ detail, onStart, onStartEarly }: { detail: ReviewCardDetail; onStart: () => void; onStartEarly: () => void }) {
  const { text } = useLanguage()
  if (detail.reviewState === 'READY') return <div className="review-overview-action"><span>{text('当前卡片已到期', 'This card is due')}</span><button type="button" className="primary-button" onClick={onStart}>{text('开始复习', 'Start review')}</button></div>
  if (detail.reviewState === 'WAITING') return <div className="review-overview-action"><span>{text(`下次到期：${formatDateTime(detail.dueAt)}`, `Next due: ${formatDateTime(detail.dueAt)}`)}</span><button type="button" className="primary-button" onClick={onStartEarly}>{text('提前复习', 'Review early')}</button></div>
  if (detail.reviewState === 'DERIVED_GENERATION_REQUIRED') return <div className="review-overview-action"><span>{text('需要继续本周期', 'Continue this cycle')}</span><button type="button" className="primary-button" onClick={onStart}>{text('继续复习', 'Continue review')}</button></div>
  return <div className="review-overview-action"><span>{text('当前卡片已掌握', 'This card is mastered')}</span></div>
}

function ReviewCycleSummary({ detail, progress, dueAt }: { detail: ReviewCardDetail | null; progress: ReviewCycleProgress; dueAt?: string | null }) {
  const { text } = useLanguage()
  const percentage = progress.targetSuccessCount === 0 ? 0 : Math.max(0, Math.min(100, Math.round(progress.netSuccessCount / progress.targetSuccessCount * 100)))
  return <section className="review-cycle-summary" aria-label={text('复习周期摘要', 'Review cycle summary')}>
    <div className="review-cycle-metrics">
      <div><span>{text('当前周期', 'Current cycle')}</span><strong>{text(`第 ${progress.cycleNo} 轮`, `Cycle ${progress.cycleNo}`)}</strong></div>
      <div className="review-cycle-progress"><span>{text('净成功', 'Net success')}</span><strong>{progress.netSuccessCount} / {progress.targetSuccessCount}</strong><div className="review-progress-track" aria-label={text(`净成功进度 ${percentage}%`, `Net success progress ${percentage}%`)}><span style={{ width: `${percentage}%` }} /></div></div>
      <div><span>{text('原题通过', 'Originals passed')}</span><strong>{progress.originalPassedCount} / {progress.originalQuestionCount}</strong></div>
      <div><span>{text('待处理', 'Pending')}</span><strong>{progress.retryQuestionCount + progress.pendingQuestionCount}</strong></div>
    </div>
    {detail ? <dl className="review-cycle-schedule"><div><dt>{text('难度', 'Ease')}</dt><dd>{detail.easeFactor.toFixed(4)}</dd></div><div><dt>{text('连续通过', 'Pass streak')}</dt><dd>{detail.repetitionCount}</dd></div><div><dt>{text('间隔', 'Interval')}</dt><dd>{text(`${detail.intervalDays} 天`, `${detail.intervalDays} days`)}</dd></div><div><dt>{text('累计未通过', 'Total failures')}</dt><dd>{detail.lapseCount}</dd></div><div><dt>{text('下次到期', 'Next due')}</dt><dd>{formatDateTime(dueAt === undefined ? detail.dueAt : dueAt)}</dd></div></dl> : null}
  </section>
}

function ReviewAttemptHistoryList({ attempts }: { attempts: ReviewAttemptHistory[] }) {
  const { text } = useLanguage()
  return <section className="review-attempt-history" aria-label={text('复习记录', 'Review history')}>
    <div className="review-history-heading"><div className="section-title"><span className="label">{text('历史', 'History')}</span><strong>{text('复习记录', 'Review history')}</strong></div><span>{text(`共 ${attempts.length} 次`, `${attempts.length} attempts`)}</span></div>
    {attempts.length === 0 ? <p className="empty-inline">{text('暂无正式复习记录。', 'No formal review attempts yet.')}</p> : <ol className="review-attempt-history-list">
      {attempts.map((attempt, index) => <li key={attempt.id}>
        <div className="review-attempt-history-header">
          <div className="review-attempt-history-meta"><span className="review-attempt-index" aria-hidden="true">{String(index + 1).padStart(2, '0')}</span><time dateTime={attempt.createdAt}>{formatDateTime(attempt.createdAt)}</time><span>{text(`第 ${attempt.cycleNo} 周期`, `Cycle ${attempt.cycleNo}`)}</span><span aria-hidden="true">·</span><span>{attempt.questionRole === 'DERIVED' ? text('衍生题', 'Derived') : text('原题', 'Original')}</span></div>
          <div className="review-attempt-history-summary"><span className={`review-attempt-result ${attempt.result === 'PASS' ? 'is-pass' : 'is-fail'}`}>{attempt.result === 'PASS' ? text('通过', 'Passed') : text('失败', 'Failed')}</span><strong className={scoreToneClassName(attempt.totalScore)}>{text(`总分 ${formatReviewAttemptScore(attempt.totalScore)}`, `Score ${formatReviewAttemptScore(attempt.totalScore)}`)}</strong><span>{text('质量', 'Quality')} {attempt.quality} / 5</span></div>
        </div>
        <div className="review-attempt-history-content"><div className="review-attempt-question"><span>{text('题目', 'Question')}</span><p>{attempt.sourceText}</p></div><div className="review-attempt-reference"><span>{text('参考答案', 'Reference answer')}</span><p>{attempt.referenceAnswer || text('暂无参考答案。', 'No reference answer.')}</p></div><div className="review-attempt-answer"><span>{text('你的答案', 'Your answer')}</span><p>{attempt.answerText}</p></div></div>
      </li>)}
    </ol>}
  </section>
}

export function ReviewResultView({ card, result, submittedQuestionSource, submittedAnswer, candidates, notice, derivedGenerating, nextCardLoading, onGenerate, onOpenErrorConfirmation, onContinueEarly, onNext, onBack, children }: {
  card: ReviewCardDetail | null
  result: ReviewAttemptResult
  submittedQuestionSource: string
  submittedAnswer: string
  candidates: ErrorCandidateState[]
  notice: PracticeNotice | null
  derivedGenerating: boolean
  nextCardLoading: boolean
  onGenerate: () => void
  onOpenErrorConfirmation: () => void
  onContinueEarly: () => void
  onNext: () => void
  onBack: () => void
  children: ReactNode
}) {
  const { text } = useLanguage()
  const passed = result.result === 'PASS'
  const savedCount = candidates.filter((candidate) => candidate.saved).length
  return <section className="page-content target-page review-page" aria-label={text('复习结果', 'Review result')}>
    <PageHeader eyebrow={text('复习卡片', 'Review cards')} title={text('复习评分', 'Review score')} actions={<><button type="button" disabled={nextCardLoading || derivedGenerating} onClick={onBack}>{text('返回列表', 'Back to list')}</button><button type="button" className="primary-button" disabled={nextCardLoading || derivedGenerating} onClick={onNext}>{nextCardLoading ? text('加载中', 'Loading') : text('复习下一张', 'Review next card')}</button></>} />
    <section className="surface review-surface review-result-surface">
      <ReviewCycleSummary detail={card} progress={result.progress} dueAt={result.nextDueAt} />
      <div className={`review-result-banner ${passed ? 'is-pass' : 'is-fail'}`}><div><span className="label">{text('本次评分', 'Score')}</span><h2>{passed ? text('回答通过', 'Passed') : text('需要重试', 'Retry needed')}</h2><p>{result.feedback}</p></div><div className="review-score-summary"><div className="review-total-score"><span>{text('总分', 'Total score')}</span><strong className={scoreToneClassName(result.totalScore)}>{formatTotalScore(result.totalScore)}</strong><small>/ 100</small></div><div className="review-quality"><span>{text('复习质量', 'Review quality')}</span><strong>{result.quality}</strong><small>/ 5</small></div></div></div>
      {notice ? <StatusNotice notice={notice} className="review-notice" /> : null}
      <section className="review-answer-comparison" aria-label={text('答案对照', 'Answer comparison')}>
        <div className="review-result-row review-result-question"><span>{text('原题', 'Original question')}</span><p>{submittedQuestionSource}</p></div>
        <div className="review-result-row review-result-user-answer"><span>{text('你的答案', 'Your answer')}</span><p>{submittedAnswer}</p></div>
        {result.standardAnswers.length > 0 ? [...result.standardAnswers].sort((left, right) => left.sortOrder - right.sortOrder).map((answer) => <div className="review-result-row review-result-standard-answer" key={answer.id}><span>{answer.primaryAnswer ? text('标准答案', 'Standard answer') : text('参考答案', 'Reference answer')}</span><p>{answer.answerText}</p></div>) : <div className="review-result-row review-result-standard-answer"><span>{text('标准答案', 'Standard answer')}</span><p>{text('暂无标准答案。', 'No standard answers.')}</p></div>}
        <div className="review-result-row review-result-focus"><span>{text('目标重点', 'Target focus')}</span><strong className={result.targetErrorResolved ? 'is-mastered' : 'is-needs-practice'}>{result.targetErrorResolved ? text('已掌握', 'Mastered') : text('待加强', 'Needs practice')}</strong><p>{result.targetErrorResolved ? text('本次回答已经体现当前复习重点。', 'This answer demonstrates the current review focus.') : text('下一次仍会优先重试这道题。', 'This question will remain a priority for the next attempt.')}</p></div>
      </section>
      <dl className="score-grid review-score-grid"><div><dt>{text('语法与词汇', 'Grammar & vocabulary')}</dt><dd className={scoreToneClassName(result.scores.grammarVocabularyScore)}>{result.scores.grammarVocabularyScore}</dd></div><div><dt>{text('自然流畅度', 'Fluency')}</dt><dd className={scoreToneClassName(result.scores.naturalFluencyScore)}>{result.scores.naturalFluencyScore}</dd></div><div><dt>{text('场景适配度', 'Context fit')}</dt><dd className={scoreToneClassName(result.scores.scenarioAdaptationScore)}>{result.scores.scenarioAdaptationScore}</dd></div><div><dt>{text('信息完整性', 'Completeness')}</dt><dd className={scoreToneClassName(result.scores.informationCompletenessScore)}>{result.scores.informationCompletenessScore}</dd></div></dl>
      <section className="review-error-candidates"><ReviewList title={text('候选新错误', 'Candidate errors')} emptyText={text('本次未发现需要确认的新错误。', 'No new errors require confirmation.')} items={result.errorAnalysis}>{(item) => <div><span>{item.errorTypeName} / {item.severity}</span><strong>{item.original}</strong><p>{item.issue}</p><p>{item.suggestion}</p></div>}</ReviewList><div className="error-record-action"><span>{result.errorAnalysis.length > 0 ? text(`${savedCount} / ${result.errorAnalysis.length} 项已加入复习卡片`, `${savedCount} / ${result.errorAnalysis.length} added to review cards`) : text('可手动记录希望继续练习的表达', 'You can manually save an expression for further practice')}</span><button type="button" onClick={onOpenErrorConfirmation}>{text('添加复习卡片', 'Add review card')}</button></div></section>
      {result.derivedGenerationStatus === 'FAILED' ? <div className="review-generation-row"><div><strong>{text('衍生题生成失败', 'Derived question generation failed')}</strong><p>{text('本次评分和复习进度已经保存，可单独重试生成。', 'The score and progress were saved. You can retry generation separately.')}</p></div><button type="button" className="primary-button" disabled={derivedGenerating} onClick={onGenerate}>{derivedGenerating ? text('生成中', 'Generating') : text('重试生成', 'Retry generation')}</button></div> : null}
      {result.derivedGenerationStatus === 'SUCCEEDED' ? <div className="notice"><strong>{text('衍生题已生成', 'Derived question generated')}</strong><p>{text('卡片已准备好后续复习题。', 'The card is ready for further review.')}</p></div> : null}
      <div className="review-result-actions"><span>{result.cardStatus === 'MASTERED' ? text('当前卡片已掌握', 'This card is mastered') : text(`下次到期：${formatDateTime(result.nextDueAt)}`, `Next due: ${formatDateTime(result.nextDueAt)}`)}</span><div>{result.cardStatus === 'ACTIVE' ? <button type="button" className="primary-button" disabled={nextCardLoading || derivedGenerating} onClick={onContinueEarly}>{text('继续提前复习当前卡片', 'Continue reviewing this card early')}</button> : null}<button type="button" className={result.cardStatus === 'MASTERED' ? 'primary-button' : undefined} disabled={nextCardLoading || derivedGenerating} onClick={onNext}>{nextCardLoading ? text('加载中', 'Loading') : text('复习下一张', 'Review next card')}</button><button type="button" disabled={nextCardLoading || derivedGenerating} onClick={onBack}>{text('返回列表', 'Back to list')}</button></div></div>
      {card ? <p className="review-result-context">{card.userErrorTypeName} · {text(`第 ${result.progress.cycleNo} 周期`, `Cycle ${result.progress.cycleNo}`)}</p> : null}
      {children}
    </section>
  </section>
}

function QuestionMetadata({ detail }: { detail: ReviewCardDetail }) {
  const { english, text } = useLanguage()
  const question = detail.currentQuestion
  if (!question) return null
  const flags = [question.spoken ? text('口语', 'Spoken') : null, question.business ? text('商务', 'Business') : null, question.exam ? text('考试', 'Exam') : null].filter(Boolean)
  return <div className="review-question-meta"><span>{question.level}</span><span>{text('难度', 'Difficulty')} {question.difficulty}</span>{flags.map((flag) => <span key={flag}>{flag}</span>)}{question.tags.map((tag) => <span key={tag.id}>{getTagDisplayName(tag, english)}</span>)}<span>{text(`已作答 ${question.attemptCount} 次`, `${question.attemptCount} attempts`)}</span></div>
}

export function ProgressBar({ progress }: { progress: ReviewCycleProgress }) {
  const { text } = useLanguage()
  const percentage = progress.targetSuccessCount === 0 ? 0 : Math.max(0, Math.min(100, Math.round(progress.netSuccessCount / progress.targetSuccessCount * 100)))
  return <div className="review-progress" aria-label={text(`净成功进度 ${percentage}%`, `Net success progress ${percentage}%`)}><div className="review-progress-track"><span style={{ width: `${percentage}%` }} /></div><small>{progress.netSuccessCount} / {progress.targetSuccessCount}</small></div>
}

function StateMessage({ title, message, actionLabel, actionDisabled = false, onAction }: { title: string; message: string; actionLabel?: string; actionDisabled?: boolean; onAction?: () => void }) {
  return <div className="review-state-message"><strong>{title}</strong><p>{message}</p>{actionLabel && onAction ? <button type="button" className="primary-button" disabled={actionDisabled} onClick={onAction}>{actionLabel}</button> : null}</div>
}

function formatTotalScore(value: number) {
  return value.toFixed(2)
}

function formatReviewAttemptScore(value: number | null) {
  return value === null ? '-' : `${value.toFixed(2)} / 100`
}
