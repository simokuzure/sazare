import { useState } from 'react'
import { getErrorMessage } from '../api/client'
import { correctJapanese } from '../api/japaneseCorrectionApi'
import { confirmUserAnswerErrors, fetchUserErrorTypes } from '../api/userErrorApi'
import ErrorConfirmationModal from '../components/ErrorConfirmationModal'
import {
  type ErrorCandidateState,
  toErrorCandidateState,
  toExistingErrorConfirmation,
  toNewErrorConfirmation,
} from '../components/errorConfirmation'
import ReviewList from '../components/ReviewList'
import type { PracticeNotice } from '../types/api'
import type { JapaneseCorrectionReview } from '../types/review'
import type { UserAnswerErrorConfirmation, UserErrorType } from '../types/userError'
import { useLanguage } from '../i18n/LanguageContext'
import { scoreToneClassName } from '../utils/score'

type CorrectionSession = {
  text: string
  submitted: boolean
  correcting: boolean
  review: JapaneseCorrectionReview | null
  notice: PracticeNotice | null
  candidates: ErrorCandidateState[]
  confirmationOpen: boolean
  confirmationNotice: PracticeNotice | null
}

const EMPTY_SESSION: CorrectionSession = {
  text: '',
  submitted: false,
  correcting: false,
  review: null,
  notice: null,
  candidates: [],
  confirmationOpen: false,
  confirmationNotice: null,
}

export default function JapaneseCorrectionPractice() {
  const { learningMode, text } = useLanguage()
  const [session, setSession] = useState<CorrectionSession>(EMPTY_SESSION)
  const [userErrorTypes, setUserErrorTypes] = useState<UserErrorType[]>([])
  const [userErrorTypesLoading, setUserErrorTypesLoading] = useState(false)
  const [errorConfirming, setErrorConfirming] = useState(false)
  const selectedErrorCount = session.candidates.filter((candidate) => candidate.selected && !candidate.saved).length

  async function handleCorrect() {
    const inputText = session.text.trim()
    if (!inputText) {
      setSession((current) => ({
        ...current,
        notice: { kind: 'error', title: text('请输入日语', 'Enter Japanese text'), message: text('填写需要检查的日语文本后再提交。', 'Enter the Japanese text you want to check before submitting.') },
      }))
      return
    }

    setSession((current) => ({
      ...current,
      text: inputText,
      submitted: true,
      correcting: true,
      review: null,
      notice: null,
      candidates: [],
      confirmationOpen: false,
      confirmationNotice: null,
    }))
    try {
      const review = await correctJapanese(inputText, learningMode)
      setSession((current) => ({
        ...current,
        review,
        notice: { kind: 'info', title: text('纠错完成', 'Check complete'), message: text(`本次总分：${formatScore(review.totalScore)}`, `Total score: ${formatScore(review.totalScore)}`) },
        candidates: review.errorAnalysis.map(toErrorCandidateState),
      }))
    } catch (fetchError: unknown) {
      setSession((current) => ({
        ...current,
        notice: { kind: 'error', title: text('纠错失败', 'Check failed'), message: getErrorMessage(fetchError) },
      }))
    } finally {
      setSession((current) => ({ ...current, correcting: false }))
    }
  }

  async function loadActiveUserErrorTypes() {
    setUserErrorTypesLoading(true)
    try {
      const result = await fetchUserErrorTypes({ learningMode, status: 'ACTIVE', page: 1, size: 100 })
      setUserErrorTypes(result.items)
    } catch (fetchError: unknown) {
      setUserErrorTypes([])
      setSession((current) => ({
        ...current,
        confirmationNotice: { kind: 'error', title: text('无法加载已有复习卡片', 'Could not load review cards'), message: getErrorMessage(fetchError) },
      }))
    } finally {
      setUserErrorTypesLoading(false)
    }
  }

  async function handleConfirmErrors() {
    if (!session.review) return false
    const selectedItems = session.review.errorAnalysis
      .map((analysis, index) => ({ analysis, candidate: session.candidates[index], index }))
      .filter(({ candidate }) => candidate?.selected && !candidate.saved)
    if (selectedItems.length === 0) return false

    const payload: UserAnswerErrorConfirmation[] = []
    for (const { analysis, candidate, index } of selectedItems) {
      if (candidate.mode === 'NEW_USER_ERROR_TYPE') {
        if (!candidate.userErrorTypeName.trim() || !candidate.userErrorTypeDescription.trim()) {
          setSession((current) => ({
            ...current,
            confirmationNotice: { kind: 'error', title: text('请补充复习卡片', 'Complete the review card'), message: text('新建复习卡片需要名称和说明。', 'A new review card requires a name and description.') },
          }))
          return false
        }
        payload.push(toNewErrorConfirmation(analysis, candidate, index))
      } else {
        if (!candidate.userErrorTypeId) {
          setSession((current) => ({
            ...current,
            confirmationNotice: { kind: 'error', title: text('请选择已有复习卡片', 'Select a review card'), message: text('添加记录前请选择对应的复习卡片。', 'Select the review card before adding this item.') },
          }))
          return false
        }
        payload.push(toExistingErrorConfirmation(analysis, candidate, index))
      }
    }

    setErrorConfirming(true)
    setSession((current) => ({ ...current, confirmationNotice: null }))
    try {
      await confirmUserAnswerErrors(session.review.userAnswerId, { errors: payload })
      const confirmedIndexes = new Set(selectedItems.map(({ index }) => index))
      setSession((current) => ({
        ...current,
        candidates: current.candidates.map((item, index) => (
          confirmedIndexes.has(index) ? { ...item, selected: false, saved: true } : item
        )),
        confirmationNotice: { kind: 'info', title: text('复习卡片已更新', 'Review cards updated'), message: text(`已添加 ${selectedItems.length} 项复习内容。`, `Added ${selectedItems.length} review item(s).`) },
      }))
      void loadActiveUserErrorTypes()
      return true
    } catch (fetchError: unknown) {
      setSession((current) => ({
        ...current,
        confirmationNotice: { kind: 'error', title: text('记录失败', 'Save failed'), message: getErrorMessage(fetchError) },
      }))
      return false
    } finally {
      setErrorConfirming(false)
    }
  }

  function updateCandidate(index: number, patch: Partial<ErrorCandidateState>) {
    setSession((current) => ({
      ...current,
      candidates: current.candidates.map((item, itemIndex) => itemIndex === index ? { ...item, ...patch } : item),
      confirmationNotice: null,
    }))
  }

  function handleEdit() {
    setSession((current) => ({
      ...EMPTY_SESSION,
      text: current.text,
    }))
  }

  return (
    <div className="japanese-correction-grid">
      <section className="surface answer-panel japanese-correction-panel" aria-label={session.submitted ? text('日语纠错结果', 'Proofreading result') : text('日语纠错输入', 'Japanese proofreading')}>
        <div className="section-title">
          <span className="label">{text('日语纠错', 'Japanese proofreading')}</span>
          <strong>{session.submitted ? text('本次检查结果', 'Proofreading result') : text('输入需要检查的日语文本', 'Check your Japanese')}</strong>
        </div>

        {!session.submitted ? (
          <>
            {session.notice ? <Notice notice={session.notice} /> : null}
            <textarea
              aria-label={text('需要检查的日语文本', 'Japanese text to check')}
              className="article-answer-input"
              value={session.text}
              maxLength={5000}
              placeholder={text('请输入一段日语；AI 会检查语法、词汇、自然度、语体和表记', 'Enter Japanese text. AI will check grammar, vocabulary, fluency, register, and writing.')}
              onChange={(event) => setSession((current) => ({ ...current, text: event.target.value, notice: null }))}
            />
            <div className="answer-input-footer">
              <span>{session.text.length} / 5000</span>
              <div className="action-row">
                <button type="button" className="primary-button" disabled={session.correcting} onClick={handleCorrect}>
                  {session.correcting ? text('纠错中', 'Checking') : text('开始纠错', 'Check text')}
                </button>
                <button type="button" disabled={!session.text} onClick={() => setSession(EMPTY_SESSION)}>{text('清空', 'Clear')}</button>
              </div>
            </div>
          </>
        ) : (
          <div className="answer-result">
            {session.notice && (!session.review || session.notice.kind === 'error') ? <Notice notice={session.notice} /> : null}
            {session.correcting ? <div className="notice" role="status" aria-live="polite"><strong>{text('纠错中', 'Checking')}</strong><p>{text('正在检查日语文本并生成修订稿。', 'Checking the Japanese text and preparing a revision.')}</p></div> : null}
            <section className="submitted-answer pre-wrap-text"><span className="label">{text('你的日语原文', 'Your Japanese text')}</span><p>{session.text}</p></section>
            {session.review ? <CorrectionResult
              review={session.review}
              candidates={session.candidates}
              selectedErrorCount={selectedErrorCount}
              confirmationOpen={session.confirmationOpen}
              confirmationNotice={session.confirmationNotice}
              userErrorTypes={userErrorTypes}
              userErrorTypesLoading={userErrorTypesLoading}
              errorConfirming={errorConfirming}
              onUpdateCandidate={updateCandidate}
              onOpenConfirmation={() => {
                setSession((current) => ({ ...current, confirmationOpen: true, confirmationNotice: null }))
                if (session.review?.errorAnalysis.length) void loadActiveUserErrorTypes()
              }}
              onCloseConfirmation={() => setSession((current) => ({ ...current, confirmationOpen: false }))}
              onConfirmErrors={handleConfirmErrors}
              onCustomSaved={() => void loadActiveUserErrorTypes()}
            /> : null}
            <div className="action-row">
              <button type="button" className="primary-button" disabled={session.correcting || errorConfirming} onClick={handleEdit}>{text('修改原文', 'Edit text')}</button>
              <button type="button" disabled={session.correcting || errorConfirming} onClick={() => setSession(EMPTY_SESSION)}>{text('清空', 'Clear')}</button>
            </div>
          </div>
        )}
      </section>
    </div>
  )
}

type CorrectionResultProps = {
  review: JapaneseCorrectionReview
  candidates: ErrorCandidateState[]
  selectedErrorCount: number
  confirmationOpen: boolean
  confirmationNotice: PracticeNotice | null
  userErrorTypes: UserErrorType[]
  userErrorTypesLoading: boolean
  errorConfirming: boolean
  onUpdateCandidate: (index: number, patch: Partial<ErrorCandidateState>) => void
  onOpenConfirmation: () => void
  onCloseConfirmation: () => void
  onConfirmErrors: () => Promise<boolean>
  onCustomSaved: () => void
}

function CorrectionResult(props: CorrectionResultProps) {
  const { text } = useLanguage()
  const { review, candidates } = props
  return (
    <>
      <div className="score-summary"><span>{text('总分', 'Total score')}</span><strong className={scoreToneClassName(review.totalScore)}>{formatScore(review.totalScore)}</strong></div>
      <section className="review-section article-overall-comment"><strong>{text('总体评价', 'Overall feedback')}</strong><p>{review.overallComment}</p></section>
      <section className="article-revised-answer pre-wrap-text"><strong>{text('完整纠正文稿', 'Complete revision')}</strong><p>{review.revisedText}</p></section>

      <details className="review-detail">
        <summary>{text('详细评分与错误', 'Detailed scores and errors')}</summary>
        <div className="review-result">
          <dl className="score-grid">
            <div><dt>{text('语法与词汇准确性', 'Grammar & vocabulary accuracy')}</dt><dd className={scoreToneClassName(review.scores.grammarVocabularyScore)}>{review.scores.grammarVocabularyScore}</dd></div>
            <div><dt>{text('自然度与篇章连贯', 'Fluency & coherence')}</dt><dd className={scoreToneClassName(review.scores.naturalFluencyScore)}>{review.scores.naturalFluencyScore}</dd></div>
            <div><dt>{text('语体与风格一致性', 'Register & style consistency')}</dt><dd className={scoreToneClassName(review.scores.scenarioAdaptationScore)}>{review.scores.scenarioAdaptationScore}</dd></div>
            <div><dt>{text('表记与输入完整性', 'Writing & input completeness')}</dt><dd className={scoreToneClassName(review.scores.informationCompletenessScore)}>{review.scores.informationCompletenessScore}</dd></div>
          </dl>
          <dl className="comment-list">
            <div><dt>{text('语法与词汇', 'Grammar & vocabulary')}</dt><dd>{review.comments.grammarVocabularyComment}</dd></div>
            <div><dt>{text('自然度与篇章', 'Fluency & coherence')}</dt><dd>{review.comments.naturalFluencyComment}</dd></div>
            <div><dt>{text('语体与风格', 'Register & style')}</dt><dd>{review.comments.styleConsistencyComment}</dd></div>
            <div><dt>{text('表记与完整性', 'Writing & completeness')}</dt><dd>{review.comments.writingCompletenessComment}</dd></div>
          </dl>
          <ReviewList title={text('候选错误', 'Candidate errors')} emptyText={text('本次未发现明确错误。', 'No clear errors were found.')} items={review.errorAnalysis}>
            {(item) => <div><span>{item.errorTypeName} / {item.severity}</span><strong>{item.original}</strong><p>{item.issue}</p><p>{text('修订：', 'Revision: ')}{item.suggestion}</p></div>}
          </ReviewList>
          <ReviewList title={text('修改建议', 'Revision suggestions')} emptyText={text('本次没有额外修改建议。', 'No additional revision suggestions.')} items={review.revisionSuggestions}>{(item) => <p>{item}</p>}</ReviewList>
          <ReviewList title={text('推荐表达', 'Recommended expressions')} emptyText={text('本次没有推荐表达。', 'No recommended expressions.')} items={review.recommendedExpressions}>{(item) => <div><span>{item.formality}</span><strong>{item.expression}</strong><p>{item.usage}</p><p>{item.note}</p></div>}</ReviewList>
        </div>
      </details>
      <div className="error-record-action"><span>{review.errorAnalysis.length > 0 ? text(`${candidates.filter((candidate) => candidate.saved).length} / ${review.errorAnalysis.length} 项已加入复习卡片`, `${candidates.filter((candidate) => candidate.saved).length} / ${review.errorAnalysis.length} added to review cards`) : text('可手动记录希望继续练习的表达', 'You can manually save an expression for further practice')}</span><button type="button" className="primary-button" onClick={props.onOpenConfirmation}>{text('添加复习卡片', 'Add review card')}</button></div>

      {props.confirmationOpen ? <ErrorConfirmationModal
        analyses={review.errorAnalysis}
        candidates={candidates}
        userErrorTypes={props.userErrorTypes}
        userErrorTypesLoading={props.userErrorTypesLoading}
        notice={props.confirmationNotice}
        confirming={props.errorConfirming}
        selectedCount={props.selectedErrorCount}
        userAnswerId={review.userAnswerId}
        reviewCardSource={{ kind: 'CORRECTION' }}
        recommendedExpressions={review.recommendedExpressions.map((item) => item.expression)}
        onUpdate={props.onUpdateCandidate}
        onConfirm={props.onConfirmErrors}
        onCustomSaved={props.onCustomSaved}
        onClose={props.onCloseConfirmation}
      /> : null}
    </>
  )
}

function Notice({ notice }: { notice: PracticeNotice }) {
  return <div className={notice.kind === 'error' ? 'notice is-error' : 'notice'} role={notice.kind === 'error' ? 'alert' : 'status'}><strong>{notice.title}</strong><p>{notice.message}</p></div>
}

function formatScore(score: number) {
  return score.toFixed(2)
}
