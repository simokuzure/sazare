import { useEffect, useMemo, useState } from 'react'
import { getErrorMessage } from '../api/client'
import {
  deleteReviewCard,
  fetchReviewCard,
  fetchReviewCards,
  generateDerivedReviewQuestion,
  submitReviewAttempt,
} from '../api/reviewApi'
import { confirmUserAnswerErrors, fetchUserErrorTypes } from '../api/userErrorApi'
import ErrorConfirmationModal from '../components/ErrorConfirmationModal'
import PageHeader from '../components/PageHeader'
import StatusNotice from '../components/StatusNotice'
import {
  buildErrorConfirmations,
  type ErrorCandidateState,
  toErrorCandidateState,
} from '../components/errorConfirmation'
import {
  ProgressBar,
  ReviewCardOverview,
  ReviewDetailView,
  ReviewResultView,
} from '../components/review/ReviewViews'
import { dueText, emptyListText, formatDateTime, isDue, listModeLabel } from '../components/review/reviewPresentation'
import type { PageData, PracticeNotice } from '../types/api'
import type {
  ReviewAttemptResult,
  ReviewCard,
  ReviewCardDetail,
  ReviewCardFilterState,
  ReviewCardListMode,
} from '../types/review'
import type { UserErrorType } from '../types/userError'
import { useLanguage } from '../i18n/LanguageContext'

const INITIAL_FILTERS: ReviewCardFilterState = { mode: 'DUE', page: 1, size: 20 }
const EMPTY_PAGE: PageData<ReviewCard> = { items: [], page: 1, size: 20, total: 0 }

type WorkbenchView = 'LIST' | 'CARD_DETAIL' | 'REVIEW' | 'RESULT'

export default function ReviewPage() {
  const { learningMode, text } = useLanguage()
  const [filters, setFilters] = useState<ReviewCardFilterState>({ ...INITIAL_FILTERS, learningMode })
  const [cards, setCards] = useState<PageData<ReviewCard>>(EMPTY_PAGE)
  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState<string | null>(null)
  const [listNotice, setListNotice] = useState<PracticeNotice | null>(null)
  const [listReloadToken, setListReloadToken] = useState(0)
  const [deletingCardId, setDeletingCardId] = useState<number | null>(null)
  const [view, setView] = useState<WorkbenchView>('LIST')
  const [selectedCardId, setSelectedCardId] = useState<number | null>(null)
  const [earlyReview, setEarlyReview] = useState(false)
  const [detail, setDetail] = useState<ReviewCardDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [detailReloadToken, setDetailReloadToken] = useState(0)
  const [answerText, setAnswerText] = useState('')
  const [submittedAnswer, setSubmittedAnswer] = useState('')
  const [submittedQuestionSource, setSubmittedQuestionSource] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [attemptResult, setAttemptResult] = useState<ReviewAttemptResult | null>(null)
  const [actionNotice, setActionNotice] = useState<PracticeNotice | null>(null)
  const [derivedGenerating, setDerivedGenerating] = useState(false)
  const [nextCardLoading, setNextCardLoading] = useState(false)
  const [errorCandidates, setErrorCandidates] = useState<ErrorCandidateState[]>([])
  const [errorConfirmationOpen, setErrorConfirmationOpen] = useState(false)
  const [errorConfirmationNotice, setErrorConfirmationNotice] = useState<PracticeNotice | null>(null)
  const [errorConfirming, setErrorConfirming] = useState(false)
  const [userErrorTypes, setUserErrorTypes] = useState<UserErrorType[]>([])
  const [userErrorTypesLoading, setUserErrorTypesLoading] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    setListLoading(true)
    setListError(null)
    fetchReviewCards(filters, controller.signal)
      .then(setCards)
      .catch((error: unknown) => {
        if (isAbortError(error)) return
        setCards({ ...EMPTY_PAGE, page: filters.page, size: filters.size })
        setListError(getErrorMessage(error))
      })
      .finally(() => {
        if (!controller.signal.aborted) setListLoading(false)
      })
    return () => controller.abort()
  }, [filters, listReloadToken])

  useEffect(() => {
    if (selectedCardId === null) return
    const controller = new AbortController()
    setDetailLoading(true)
    setDetailError(null)
    fetchReviewCard(selectedCardId, earlyReview, controller.signal)
      .then(setDetail)
      .catch((error: unknown) => {
        if (isAbortError(error)) return
        setDetailError(getErrorMessage(error))
      })
      .finally(() => {
        if (!controller.signal.aborted) setDetailLoading(false)
      })
    return () => controller.abort()
  }, [detailReloadToken, earlyReview, selectedCardId])

  const totalPages = Math.max(1, Math.ceil(cards.total / filters.size))
  const firstItemNo = cards.total === 0 ? 0 : (filters.page - 1) * filters.size + 1
  const lastItemNo = Math.min(filters.page * filters.size, cards.total)
  const selectedErrorCount = useMemo(
    () => errorCandidates.filter((candidate) => candidate.selected && !candidate.saved).length,
    [errorCandidates],
  )

  function changeMode(mode: ReviewCardListMode) {
    setFilters((current) => ({ ...current, mode, page: 1 }))
  }

  function changePageSize(size: number) {
    setFilters((current) => ({ ...current, size, page: 1 }))
  }

  function openCard(
    cardId: number,
    nextView: Extract<WorkbenchView, 'CARD_DETAIL' | 'REVIEW'>,
    nextEarlyReview = false,
  ) {
    setSelectedCardId(cardId)
    setEarlyReview(nextEarlyReview)
    setDetail(null)
    setDetailError(null)
    setAnswerText('')
    setSubmittedAnswer('')
    setSubmittedQuestionSource('')
    setAttemptResult(null)
    setActionNotice(null)
    setErrorCandidates([])
    setErrorConfirmationOpen(false)
    setErrorConfirmationNotice(null)
    setView(nextView)
  }

  function startReview(cardId: number) {
    openCard(cardId, 'REVIEW')
  }

  function startEarlyReview(cardId: number) {
    openCard(cardId, 'REVIEW', true)
  }

  function viewCard(cardId: number) {
    openCard(cardId, 'CARD_DETAIL')
  }

  function startReviewFromDetail() {
    setDetail(null)
    setAnswerText('')
    setActionNotice(null)
    setView('REVIEW')
    setDetailReloadToken((value) => value + 1)
  }

  function startEarlyReviewFromDetail() {
    setDetail(null)
    setAnswerText('')
    setActionNotice(null)
    setEarlyReview(true)
    setView('REVIEW')
  }

  function returnToList() {
    if (submitting) return
    setView('LIST')
    setSelectedCardId(null)
    setEarlyReview(false)
    setDetail(null)
    setAttemptResult(null)
    setActionNotice(null)
    setListReloadToken((value) => value + 1)
  }

  function reloadDetail() {
    setActionNotice(null)
    setDetailReloadToken((value) => value + 1)
  }

  function refreshListAfterDeletion() {
    if (cards.items.length === 1 && filters.page > 1) {
      setFilters((current) => ({ ...current, page: current.page - 1 }))
      return
    }
    setListReloadToken((value) => value + 1)
  }

  async function deleteCard(cardId: number, cardName: string, returnToListAfterDelete: boolean) {
    const confirmed = window.confirm(
      text(`确认删除复习卡片“${cardName}”？\n\n删除后将从复习列表移除；历史记录保留，再次添加会创建全新卡片。`, `Delete review card “${cardName}”?\n\nIt will be removed from the review list. History is retained, and adding it again creates a new card.`),
    )
    if (!confirmed) return

    setDeletingCardId(cardId)
    setListNotice(null)
    try {
      await deleteReviewCard(cardId)
      setListNotice({ kind: 'info', title: text('复习卡片已删除', 'Review card deleted'), message: text(`“${cardName}”已从复习列表移除。`, `“${cardName}” was removed from the review list.`) })
      if (returnToListAfterDelete) {
        setView('LIST')
        setSelectedCardId(null)
        setEarlyReview(false)
        setDetail(null)
        setDetailError(null)
        setAttemptResult(null)
        setActionNotice(null)
      }
      refreshListAfterDeletion()
    } catch (error: unknown) {
      const notice = { kind: 'error' as const, title: text('删除失败', 'Delete failed'), message: getErrorMessage(error) }
      if (returnToListAfterDelete) {
        setActionNotice(notice)
      } else {
        setListNotice(notice)
      }
    } finally {
      setDeletingCardId(null)
    }
  }

  async function handleSubmitAttempt() {
    if (!detail?.currentQuestion || selectedCardId === null) return
    const normalizedAnswer = answerText.trim()
    if (!normalizedAnswer) {
      setActionNotice({ kind: 'error', title: text('请填写答案', 'Enter an answer'), message: text('输入日语答案后再提交。', 'Enter your Japanese answer before submitting.') })
      return
    }

    const questionSource = detail.currentQuestion.sourceText
    setSubmitting(true)
    setActionNotice(null)
    try {
      const result = await submitReviewAttempt(selectedCardId, {
        cycleQuestionId: detail.currentQuestion.cycleQuestionId,
        answerText: normalizedAnswer,
        earlyReview,
      })
      setSubmittedAnswer(normalizedAnswer)
      setSubmittedQuestionSource(questionSource)
      setAttemptResult(result)
      setErrorCandidates(result.errorAnalysis.map(toErrorCandidateState))
      setErrorConfirmationNotice(null)
      setErrorConfirmationOpen(false)
      setView('RESULT')
      setListReloadToken((value) => value + 1)
      setDetailReloadToken((value) => value + 1)
    } catch (error: unknown) {
      setActionNotice({ kind: 'error', title: text('提交失败', 'Submission failed'), message: getErrorMessage(error) })
    } finally {
      setSubmitting(false)
    }
  }

  async function handleGenerateDerivedQuestion() {
    if (selectedCardId === null) return
    setDerivedGenerating(true)
    setActionNotice(null)
    try {
      await generateDerivedReviewQuestion(selectedCardId)
      setAttemptResult((current) => current ? { ...current, derivedGenerationStatus: 'SUCCEEDED' } : current)
      setActionNotice({ kind: 'info', title: text('衍生题已生成', 'Derived question generated'), message: text('已刷新卡片，可以继续本周期复习。', 'The card was refreshed. You can continue this review cycle.') })
      setView('REVIEW')
      setAnswerText('')
      setDetailReloadToken((value) => value + 1)
      setListReloadToken((value) => value + 1)
    } catch (error: unknown) {
      setActionNotice({ kind: 'error', title: text('生成失败', 'Generation failed'), message: getErrorMessage(error) })
    } finally {
      setDerivedGenerating(false)
    }
  }

  async function handleNextDueCard() {
    if (selectedCardId === null) return
    setNextCardLoading(true)
    setActionNotice(null)
    try {
      const dueCards = await fetchReviewCards({ learningMode, mode: 'DUE', page: 1, size: 100 })
      const nextCard = dueCards.items.find((card) => card.id !== selectedCardId)
      if (!nextCard) {
        setActionNotice({ kind: 'info', title: text('本轮已完成', 'Review round complete'), message: text('目前没有其他到期卡片。', 'There are no other due cards.') })
        return
      }
      startReview(nextCard.id)
    } catch (error: unknown) {
      setActionNotice({ kind: 'error', title: text('无法获取下一张', 'Could not load the next card'), message: getErrorMessage(error) })
    } finally {
      setNextCardLoading(false)
    }
  }

  function continueEarlyReview() {
    if (selectedCardId === null) return
    setAnswerText('')
    setSubmittedAnswer('')
    setSubmittedQuestionSource('')
    setAttemptResult(null)
    setActionNotice(null)
    setErrorCandidates([])
    setEarlyReview(true)
    setView('REVIEW')
    setDetailReloadToken((value) => value + 1)
  }

  async function loadActiveUserErrorTypes() {
    setUserErrorTypesLoading(true)
    setErrorConfirmationNotice(null)
    try {
      const result = await fetchUserErrorTypes({ learningMode, status: 'ACTIVE', page: 1, size: 100 })
      setUserErrorTypes(result.items)
    } catch (error: unknown) {
      setUserErrorTypes([])
      setErrorConfirmationNotice({ kind: 'error', title: text('无法加载已有复习卡片', 'Could not load review cards'), message: getErrorMessage(error) })
    } finally {
      setUserErrorTypesLoading(false)
    }
  }

  function openErrorConfirmation() {
    setErrorConfirmationNotice(null)
    setErrorConfirmationOpen(true)
    if (attemptResult?.errorAnalysis.length) void loadActiveUserErrorTypes()
  }

  function updateErrorCandidate(index: number, patch: Partial<ErrorCandidateState>) {
    setErrorCandidates((current) => current.map((candidate, candidateIndex) => (
      candidateIndex === index ? { ...candidate, ...patch } : candidate
    )))
    setErrorConfirmationNotice(null)
  }

  async function handleConfirmErrors() {
    if (!attemptResult) return false
    const confirmation = buildErrorConfirmations(attemptResult.errorAnalysis, errorCandidates)
    if (confirmation.ok === false) {
      setErrorConfirmationNotice(confirmation.reason === 'MISSING_NEW_CARD_DETAILS'
        ? { kind: 'error', title: text('请补充复习卡片', 'Complete the review card'), message: text('新建复习卡片需要名称和说明。', 'A new review card requires a name and description.') }
        : { kind: 'error', title: text('请选择已有复习卡片', 'Select a review card'), message: text('添加记录前请选择对应的复习卡片。', 'Select the review card before adding this item.') })
      return false
    }
    if (confirmation.selectedIndexes.length === 0) return false

    setErrorConfirming(true)
    setErrorConfirmationNotice(null)
    try {
      await confirmUserAnswerErrors(attemptResult.userAnswerId, { errors: confirmation.payload })
      const confirmedIndexes = new Set(confirmation.selectedIndexes)
      setErrorCandidates((current) => current.map((candidate, index) => (
        confirmedIndexes.has(index) ? { ...candidate, selected: false, saved: true } : candidate
      )))
      setErrorConfirmationNotice({ kind: 'info', title: text('复习卡片已更新', 'Review cards updated'), message: text(`已添加 ${confirmation.selectedIndexes.length} 项复习内容。`, `Added ${confirmation.selectedIndexes.length} review item(s).`) })
      setListReloadToken((value) => value + 1)
      void loadActiveUserErrorTypes()
      return true
    } catch (error: unknown) {
      setErrorConfirmationNotice({ kind: 'error', title: text('记录失败', 'Save failed'), message: getErrorMessage(error) })
      return false
    } finally {
      setErrorConfirming(false)
    }
  }

  if (view === 'CARD_DETAIL') {
    return <ReviewCardOverview
      detail={detail}
      loading={detailLoading}
      error={detailError}
      notice={actionNotice}
      deleting={deletingCardId === selectedCardId}
      onStart={startReviewFromDetail}
      onStartEarly={startEarlyReviewFromDetail}
      onReload={reloadDetail}
      onBack={returnToList}
      onDelete={() => {
        if (detail) void deleteCard(detail.id, detail.userErrorTypeName, true)
      }}
    />
  }

  if (view === 'REVIEW') {
    return <ReviewDetailView
      detail={detail}
      loading={detailLoading}
      error={detailError}
      answerText={answerText}
      submitting={submitting}
      derivedGenerating={derivedGenerating}
      earlyReview={earlyReview}
      notice={actionNotice}
      onAnswerChange={(value) => { setAnswerText(value); setActionNotice(null) }}
      onSubmit={handleSubmitAttempt}
      onGenerate={handleGenerateDerivedQuestion}
      onReload={reloadDetail}
      onBack={returnToList}
    />
  }

  if (view === 'RESULT' && attemptResult) {
    return <ReviewResultView
      card={detail}
      result={attemptResult}
      submittedQuestionSource={submittedQuestionSource}
      submittedAnswer={submittedAnswer}
      candidates={errorCandidates}
      notice={actionNotice}
      derivedGenerating={derivedGenerating}
      nextCardLoading={nextCardLoading}
      onGenerate={handleGenerateDerivedQuestion}
      onOpenErrorConfirmation={openErrorConfirmation}
      onContinueEarly={continueEarlyReview}
      onNext={handleNextDueCard}
      onBack={returnToList}
    >
      {errorConfirmationOpen ? <ErrorConfirmationModal
        analyses={attemptResult.errorAnalysis}
        candidates={errorCandidates}
        userErrorTypes={userErrorTypes}
        userErrorTypesLoading={userErrorTypesLoading}
        notice={errorConfirmationNotice}
        confirming={errorConfirming}
        selectedCount={selectedErrorCount}
        userAnswerId={attemptResult.userAnswerId}
        reviewCardSource={{ kind: 'FIXED', sourceText: submittedQuestionSource }}
        recommendedExpressions={attemptResult.standardAnswers.map((item) => item.answerText)}
        onUpdate={updateErrorCandidate}
        onConfirm={handleConfirmErrors}
        onCustomSaved={() => { setListReloadToken((value) => value + 1); void loadActiveUserErrorTypes() }}
        onClose={() => setErrorConfirmationOpen(false)}
      /> : null}
    </ReviewResultView>
  }

  return <section className="page-content target-page review-page" aria-label={text('复习卡片', 'Review cards')}>
    <PageHeader
      title={text('复习卡片', 'Review cards')}
      description={text('按复习重点聚合的间隔复习计划，跟踪周期进度、待重试与下次到期时间。', 'Review cards organized by learning focus, with progress, retries, and due dates.')}
      actions={<div className="segmented-control review-list-modes" aria-label={text('复习卡片视图', 'Review card view')}>
          {(['DUE', 'ACTIVE', 'MASTERED'] as const).map((mode) => (
            <button key={mode} type="button" className={filters.mode === mode ? 'is-selected' : ''} onClick={() => changeMode(mode)}>
              {text(listModeLabel(mode), mode === 'DUE' ? 'Due' : mode === 'ACTIVE' ? 'Active' : 'Mastered')}
            </button>
          ))}
        </div>}
    />
    <section className="surface review-surface review-workbench target-list-panel" aria-busy={listLoading}>

      {listNotice ? <StatusNotice notice={listNotice} className="review-notice" /> : null}
      {listError ? <StatusNotice notice={{ kind: 'error', title: text('加载失败', 'Load failed'), message: listError }} className="review-notice" actionLabel={text('重试', 'Retry')} onAction={() => setListReloadToken((value) => value + 1)} /> : null}
      {listLoading ? <p className="loading-text" role="status" aria-live="polite">{text('正在加载复习卡片...', 'Loading review cards...')}</p> : null}
      {!listLoading && !listError && cards.items.length === 0 ? <div className="empty-state" role="status">{text(emptyListText(filters.mode), 'No review cards in this view. Try another view or add cards from a practice result.')}</div> : null}

      {!listLoading && !listError && cards.items.length > 0 ? <div className="table-scroll">
        <table className="responsive-list-table review-card-table">
          <caption className="sr-only">{text('复习卡片列表', 'Review card list')}</caption>
          <thead><tr><th>{text('复习重点', 'Learning focus')}</th><th>{text('全局分类', 'Category')}</th><th>{text('到期状态', 'Due status')}</th><th>{text('周期进度', 'Cycle progress')}</th><th>{text('原题', 'Original')}</th><th>{text('待重试', 'Retries')}</th><th>{text('最近活动', 'Latest activity')}</th><th>{text('操作', 'Actions')}</th></tr></thead>
          <tbody>{cards.items.map((card) => {
            const due = card.status === 'ACTIVE' && isDue(card.dueAt)
            const deleting = deletingCardId === card.id
            return <tr key={card.id}>
              <td className="table-ellipsis-cell" data-label={text('复习重点', 'Learning focus')} title={card.userErrorTypeName}><strong>{card.userErrorTypeName}</strong></td>
              <td className="table-ellipsis-cell" data-label={text('全局分类', 'Category')} title={`${card.errorTypeName} (${card.errorTypeCode})`}>{card.errorTypeName}<span className="table-secondary-text">{card.errorTypeCode}</span></td>
              <td data-label={text('到期状态', 'Due status')}><span className={`review-state-badge ${card.status === 'MASTERED' ? 'is-mastered' : due ? 'is-ready' : 'is-waiting'}`}>{card.status === 'MASTERED' ? text('已掌握', 'Mastered') : due ? text('已到期', 'Due') : text('等待中', 'Waiting')}</span><span className="table-secondary-text">{card.status === 'MASTERED' ? formatDateTime(card.masteredAt) : dueText(card.dueAt, text)}</span></td>
              <td data-label={text('周期进度', 'Cycle progress')}><ProgressBar progress={card.progress} /></td>
              <td data-label={text('原题', 'Original')}>{card.progress.originalPassedCount} / {card.progress.originalQuestionCount}</td>
              <td data-label={text('待重试', 'Retries')}>{card.progress.retryQuestionCount}</td>
              <td data-label={text('最近活动', 'Latest activity')}>{formatDateTime(card.status === 'MASTERED' ? card.masteredAt : card.lastReviewedAt)}</td>
              <td data-label={text('操作', 'Actions')}><div className="review-card-actions"><button type="button" className="compact-button" disabled={deleting} onClick={() => viewCard(card.id)}>{text('查看', 'View')}</button>{due ? <button type="button" className="primary-button compact-button" disabled={deleting} onClick={() => startReview(card.id)}>{text('开始复习', 'Start review')}</button> : filters.mode === 'ACTIVE' ? <button type="button" className="primary-button compact-button" disabled={deleting} onClick={() => startEarlyReview(card.id)}>{text('提前复习', 'Review early')}</button> : null}<button type="button" className="danger-button compact-button" disabled={deleting} onClick={() => void deleteCard(card.id, card.userErrorTypeName, false)}>{deleting ? text('删除中', 'Deleting') : text('删除', 'Delete')}</button></div></td>
            </tr>
          })}</tbody>
        </table>
      </div> : null}

      <div className="pagination-bar">
        <div className="pagination-summary"><span>{listLoading ? text('加载中', 'Loading') : text(`第 ${filters.page} / ${totalPages} 页 · ${firstItemNo}-${lastItemNo} / ${cards.total}`, `Page ${filters.page} / ${totalPages} · ${firstItemNo}-${lastItemNo} / ${cards.total}`)}</span><label className="page-size-field"><span>{text('每页数量', 'Page size')}</span><select value={filters.size} onChange={(event) => changePageSize(Number(event.target.value))}><option value={10}>10</option><option value={20}>20</option><option value={50}>50</option><option value={100}>100</option></select></label></div>
        <div className="pagination-actions"><button type="button" disabled={filters.page <= 1 || listLoading} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>{text('上一页', 'Previous')}</button><button type="button" disabled={filters.page >= totalPages || listLoading} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>{text('下一页', 'Next')}</button><button type="button" disabled={listLoading} onClick={() => setListReloadToken((value) => value + 1)}>{text('刷新', 'Refresh')}</button></div>
      </div>
    </section>
  </section>
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}
