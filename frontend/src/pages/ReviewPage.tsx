import { useEffect, useMemo, useState, type ReactNode } from 'react'
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
import {
  type ErrorCandidateState,
  toErrorCandidateState,
  toExistingErrorConfirmation,
  toNewErrorConfirmation,
} from '../components/errorConfirmation'
import ReviewList from '../components/ReviewList'
import type { PageData, PracticeNotice } from '../types/api'
import type {
  ReviewAttemptHistory,
  ReviewAttemptResult,
  ReviewCard,
  ReviewCardDetail,
  ReviewCardFilterState,
  ReviewCardListMode,
  ReviewCycleProgress,
} from '../types/review'
import type { UserAnswerErrorConfirmation, UserErrorType } from '../types/userError'
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
        expectedAttemptCount: detail.currentQuestion.attemptCount,
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
    const selectedItems = attemptResult.errorAnalysis
      .map((analysis, index) => ({ analysis, candidate: errorCandidates[index], index }))
      .filter(({ candidate }) => candidate?.selected && !candidate.saved)
    if (selectedItems.length === 0) return false

    const payload: UserAnswerErrorConfirmation[] = []
    for (const { analysis, candidate, index } of selectedItems) {
      if (candidate.mode === 'NEW_USER_ERROR_TYPE') {
        if (!candidate.userErrorTypeName.trim() || !candidate.userErrorTypeDescription.trim()) {
          setErrorConfirmationNotice({ kind: 'error', title: text('请补充复习卡片', 'Complete the review card'), message: text('新建复习卡片需要名称和说明。', 'A new review card requires a name and description.') })
          return false
        }
        payload.push(toNewErrorConfirmation(analysis, candidate, index))
      } else {
        if (!candidate.userErrorTypeId) {
          setErrorConfirmationNotice({ kind: 'error', title: text('请选择已有复习卡片', 'Select a review card'), message: text('添加记录前请选择对应的复习卡片。', 'Select the review card before adding this item.') })
          return false
        }
        payload.push(toExistingErrorConfirmation(analysis, candidate, index))
      }
    }

    setErrorConfirming(true)
    setErrorConfirmationNotice(null)
    try {
      await confirmUserAnswerErrors(attemptResult.userAnswerId, { errors: payload })
      const confirmedIndexes = new Set(selectedItems.map(({ index }) => index))
      setErrorCandidates((current) => current.map((candidate, index) => (
        confirmedIndexes.has(index) ? { ...candidate, selected: false, saved: true } : candidate
      )))
      setErrorConfirmationNotice({ kind: 'info', title: text('复习卡片已更新', 'Review cards updated'), message: text(`已添加 ${selectedItems.length} 项复习内容。`, `Added ${selectedItems.length} review item(s).`) })
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
    <section className="surface review-surface review-workbench target-list-panel">

      {listNotice ? <Notice notice={listNotice} /> : null}
      {listError ? <Notice notice={{ kind: 'error', title: text('加载失败', 'Load failed'), message: listError }} actionLabel={text('重试', 'Retry')} onAction={() => setListReloadToken((value) => value + 1)} /> : null}
      {listLoading ? <p className="loading-text">{text('正在加载复习卡片...', 'Loading review cards...')}</p> : null}
      {!listLoading && !listError && cards.items.length === 0 ? <div className="empty-state">{text(emptyListText(filters.mode), 'No review cards in this view.')}</div> : null}

      {!listLoading && !listError && cards.items.length > 0 ? <div className="table-scroll">
        <table className="responsive-list-table review-card-table">
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

function ReviewCardOverview({ detail, loading, error, notice, deleting, onStart, onStartEarly, onReload, onBack, onDelete }: {
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
      {notice ? <Notice notice={notice} /> : null}
      {loading && !detail ? <p className="loading-text">{text('正在加载卡片详情...', 'Loading card details...')}</p> : null}
      {error ? <Notice notice={{ kind: 'error', title: text('卡片加载失败', 'Could not load card'), message: error }} actionLabel={text('重新加载', 'Reload')} onAction={onReload} /> : null}
      {detail ? <>
        <ReviewCardHeading detail={detail} showDescription />
        {detail.progress ? <ReviewMetrics progress={detail.progress} /> : null}
        <ReviewSchedule detail={detail} />
        {detail.reviewState === 'READY' ? <StateMessage title={text('卡片已到期', 'Card is due')} message={text('当前卡片已经可以复习。进入复习后才会显示题目和答案输入。', 'This card is ready for review. The question and answer input appear after you start.')} actionLabel={text('开始复习', 'Start review')} onAction={onStart} /> : null}
        {detail.reviewState === 'WAITING' ? <StateMessage title={text('等待下次复习', 'Waiting for next review')} message={text(`下次到期时间：${formatDateTime(detail.dueAt)}。也可以现在提前复习，计划将从本次实际作答日期重新计算。`, `Next due: ${formatDateTime(detail.dueAt)}. You may review early; the schedule will restart from this attempt.`)} actionLabel={text('提前复习', 'Review early')} onAction={onStartEarly} /> : null}
        {detail.reviewState === 'DERIVED_GENERATION_REQUIRED' ? <StateMessage title={text('需要继续本周期', 'Continue this cycle')} message={text('本周期原题已经通过，但净成功尚未达到 4，需要生成衍生题继续复习。', 'The original question passed, but net successes are below 4. Generate a derived question to continue.')} actionLabel={text('继续复习', 'Continue review')} onAction={onStart} /> : null}
        {detail.reviewState === 'MASTERED' ? <StateMessage title={text('本周期已掌握', 'Cycle mastered')} message={text(`完成时间：${formatDateTime(detail.masteredAt)}。再次在普通练习中记录同一复习重点时，会开启下一周期。`, `Completed: ${formatDateTime(detail.masteredAt)}. Saving the same focus from regular practice starts a new cycle.`)} /> : null}
        <ReviewAttemptHistoryList attempts={detail.reviewAttempts} />
      </> : null}
    </section>
  </section>
}

function ReviewDetailView({ detail, loading, error, answerText, submitting, derivedGenerating, earlyReview, notice, onAnswerChange, onSubmit, onGenerate, onReload, onBack }: {
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
  const { text } = useLanguage()
  return <section className="page-content target-page review-page" aria-label={text('复习卡片详情', 'Review card details')}>
    <PageHeader eyebrow={text('复习卡片', 'Review cards')} title={earlyReview ? text('提前复习', 'Early review') : text('复习卡片详情', 'Review card details')} actions={<><button type="button" disabled={submitting} onClick={onBack}>{text('返回列表', 'Back to list')}</button><button type="button" disabled={loading || submitting || derivedGenerating} onClick={onReload}>{text('重新加载', 'Reload')}</button></>} />
    <section className="surface review-surface review-detail-surface">
      {loading && !detail ? <p className="loading-text">{text('正在加载卡片详情...', 'Loading card details...')}</p> : null}
      {error ? <Notice notice={{ kind: 'error', title: text('卡片加载失败', 'Could not load card'), message: error }} actionLabel={text('重新加载', 'Reload')} onAction={onReload} /> : null}
      {detail ? <>
        <ReviewCardHeading detail={detail} showDescription={false} />
        {detail.progress ? <ReviewMetrics progress={detail.progress} /> : null}
        <ReviewSchedule detail={detail} />
        {earlyReview ? <div className="notice"><strong>{text('提前复习', 'Early review')}</strong><p>{text('本次作答会正式更新周期进度，下次复习时间从今天开始计算。', 'This attempt updates the cycle and recalculates the next review from today.')}</p></div> : null}
        {notice ? <Notice notice={notice} /> : null}
        {detail.reviewState === 'READY' && detail.currentQuestion ? <div className="review-attempt-grid">
          <section className="review-question-block"><div className="section-title"><span className="label">{detail.currentQuestion.questionRole === 'DERIVED' ? text('衍生题', 'Derived question') : text('原题', 'Original question')}</span><strong>{text('请翻译为日语', 'Translate into Japanese')}</strong></div><p className="review-question-source">{detail.currentQuestion.sourceText}</p>{detail.currentQuestion.contextText ? <p className="review-question-context">{detail.currentQuestion.contextText}</p> : null}<QuestionMetadata detail={detail} /></section>
          <section className="review-answer-block"><div className="section-title"><span className="label">{text('作答', 'Answer')}</span><strong>{text('输入日语答案', 'Enter your Japanese answer')}</strong></div><textarea value={answerText} maxLength={2000} disabled={submitting} placeholder={text('请输入日语答案', 'Enter your Japanese answer')} onChange={(event) => onAnswerChange(event.target.value)} /><div className="action-row"><button type="button" className="primary-button" disabled={submitting || !answerText.trim()} onClick={onSubmit}>{submitting ? text('评分中', 'Scoring') : earlyReview ? text('提交提前复习', 'Submit early review') : text('提交答案', 'Submit answer')}</button><span className="answer-length">{answerText.length} / 2000</span></div></section>
        </div> : null}
        {detail.reviewState === 'WAITING' ? <StateMessage title={text('等待下次复习', 'Waiting for next review')} message={text(`下次到期时间：${formatDateTime(detail.dueAt)}。到期前不能提交答案。`, `Next due: ${formatDateTime(detail.dueAt)}. Answers cannot be submitted before then.`)} /> : null}
        {detail.reviewState === 'DERIVED_GENERATION_REQUIRED' ? <StateMessage title={text('需要生成衍生题', 'Generate a derived question')} message={text('本周期原题已通过，但净成功尚未达到 4，生成一道衍生题继续积累净成功。', 'The original question passed, but net successes are below 4. Generate a derived question to continue.')} actionLabel={derivedGenerating ? text('生成中', 'Generating') : text('生成衍生题', 'Generate question')} actionDisabled={derivedGenerating} onAction={onGenerate} /> : null}
        {detail.reviewState === 'MASTERED' ? <StateMessage title={text('本周期已掌握', 'Cycle mastered')} message={text(`完成时间：${formatDateTime(detail.masteredAt)}。再次在普通练习中记录同一复习重点时，会开启下一周期。`, `Completed: ${formatDateTime(detail.masteredAt)}. Saving the same focus from regular practice starts a new cycle.`)} /> : null}
      </> : null}
    </section>
  </section>
}

function ReviewCardHeading({ detail, showDescription }: { detail: ReviewCardDetail; showDescription: boolean }) {
  const { text } = useLanguage()
  return <div className="review-card-heading"><div><span className="label">{detail.errorTypeName} · {detail.errorTypeCode}</span><h2>{detail.userErrorTypeName}</h2>{showDescription ? <p>{detail.userErrorTypeDescription || text('暂无说明', 'No description')}</p> : null}</div><span className={`review-state-badge ${reviewStateClass(detail.reviewState)}`}>{reviewStateLabel(detail.reviewState, text)}</span></div>
}

function ReviewSchedule({ detail }: { detail: ReviewCardDetail }) {
  const { text } = useLanguage()
  return <dl className="review-sm2-grid"><div><dt>{text('难度因子', 'Ease factor')}</dt><dd>{detail.easeFactor.toFixed(4)}</dd></div><div><dt>{text('连续成功', 'Consecutive successes')}</dt><dd>{detail.repetitionCount}</dd></div><div><dt>{text('当前间隔', 'Current interval')}</dt><dd>{text(`${detail.intervalDays} 天`, `${detail.intervalDays} days`)}</dd></div><div><dt>{text('累计未通过次数', 'Total failures')}</dt><dd>{detail.lapseCount}</dd></div><div><dt>{text('最近复习', 'Last reviewed')}</dt><dd>{formatDateTime(detail.lastReviewedAt)}</dd></div><div><dt>{text('下次到期', 'Next due')}</dt><dd>{formatDateTime(detail.dueAt)}</dd></div></dl>
}

function ReviewAttemptHistoryList({ attempts }: { attempts: ReviewAttemptHistory[] }) {
  const { text } = useLanguage()
  return <section className="review-attempt-history" aria-label={text('复习记录', 'Review history')}>
    <div className="section-title"><span className="label">{text('历史', 'History')}</span><strong>{text('复习记录', 'Review history')}</strong></div>
    {attempts.length === 0 ? <p className="empty-inline">{text('暂无正式复习记录。', 'No formal review attempts yet.')}</p> : <ol className="review-attempt-history-list">
      {attempts.map((attempt) => <li key={attempt.id}>
        <div className="review-attempt-history-header">
          <div className="review-attempt-history-meta"><time dateTime={attempt.createdAt}>{formatDateTime(attempt.createdAt)}</time><span>{text(`第 ${attempt.cycleNo} 周期`, `Cycle ${attempt.cycleNo}`)}</span><span>{attempt.questionRole === 'DERIVED' ? text('衍生题', 'Derived') : text('原题', 'Original')}</span></div>
          <div className="review-attempt-history-summary"><span className={`review-attempt-result ${attempt.result === 'PASS' ? 'is-pass' : 'is-fail'}`}>{attempt.result === 'PASS' ? text('通过', 'Passed') : text('失败', 'Failed')}</span><strong>{text(`总分 ${formatReviewAttemptScore(attempt.totalScore)}`, `Score ${formatReviewAttemptScore(attempt.totalScore)}`)}</strong><span>{text('质量', 'Quality')} {attempt.quality} / 5</span></div>
        </div>
        <div className="review-attempt-history-content"><div><span>{text('题目', 'Question')}</span><p>{attempt.sourceText}</p><div className="review-attempt-reference"><span>{text('参考答案', 'Reference answer')}</span><p>{attempt.referenceAnswer || text('暂无参考答案。', 'No reference answer.')}</p></div></div><div><span>{text('你的答案', 'Your answer')}</span><p>{attempt.answerText}</p></div></div>
      </li>)}
    </ol>}
  </section>
}

function ReviewResultView({ card, result, submittedAnswer, candidates, notice, derivedGenerating, nextCardLoading, onGenerate, onOpenErrorConfirmation, onContinueEarly, onNext, onBack, children }: {
  card: ReviewCardDetail | null
  result: ReviewAttemptResult
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
    <PageHeader eyebrow={text('复习卡片', 'Review cards')} title={text('复习结果', 'Review result')} />
    <section className="surface review-surface review-result-surface">
      <div className={`review-result-banner ${passed ? 'is-pass' : 'is-fail'}`}><div><span className="label">{text('本次评分', 'Score')}</span><h2>{passed ? text('回答通过', 'Passed') : text('需要重试', 'Retry needed')}</h2><p>{result.feedback}</p></div><div className="review-score-summary"><div className="review-total-score"><span>{text('总分', 'Total score')}</span><strong>{formatTotalScore(result.totalScore)}</strong><small>/ 100</small></div><div className="review-quality"><span>{text('复习质量', 'Review quality')}</span><strong>{result.quality}</strong><small>/ 5</small></div></div></div>
      {notice ? <Notice notice={notice} /> : null}
      <div className="review-result-grid"><section><span className="label">{text('你的答案', 'Your answer')}</span><p className="review-submitted-answer">{submittedAnswer}</p></section><section><span className="label">{text('目标内容', 'Target focus')}</span><strong>{result.targetErrorResolved ? text('已掌握', 'Mastered') : text('待加强', 'Needs practice')}</strong><p>{result.targetErrorResolved ? text('本次回答已经体现当前复习重点。', 'This answer demonstrates the current review focus.') : text('下一次仍会优先重试这道题。', 'This question will remain a priority for the next attempt.')}</p></section></div>
      <dl className="score-grid review-score-grid"><div><dt>{text('语法与词汇', 'Grammar & vocabulary')}</dt><dd>{result.scores.grammarVocabularyScore}</dd></div><div><dt>{text('自然流畅度', 'Fluency')}</dt><dd>{result.scores.naturalFluencyScore}</dd></div><div><dt>{text('场景适配度', 'Context fit')}</dt><dd>{result.scores.scenarioAdaptationScore}</dd></div><div><dt>{text('信息完整性', 'Completeness')}</dt><dd>{result.scores.informationCompletenessScore}</dd></div></dl>
      <ReviewMetrics progress={result.progress} />
      <section className="review-standard-answers"><div className="section-title"><span className="label">{text('评分后公开', 'Shown after scoring')}</span><strong>{text('标准与参考答案', 'Standard and reference answers')}</strong></div>{result.standardAnswers.length > 0 ? <ol>{[...result.standardAnswers].sort((left, right) => left.sortOrder - right.sortOrder).map((answer) => <li key={answer.id}><span>{answer.primaryAnswer ? text('标准', 'Standard') : text('参考', 'Reference')}</span><p>{answer.answerText}</p></li>)}</ol> : <p className="empty-inline">{text('暂无标准答案。', 'No standard answers.')}</p>}</section>
      <ReviewList title={text('候选新错误', 'Candidate errors')} emptyText={text('本次未发现需要确认的新错误。', 'No new errors require confirmation.')} items={result.errorAnalysis}>{(item) => <div><span>{item.errorTypeName} / {item.severity}</span><strong>{item.original}</strong><p>{item.issue}</p><p>{item.suggestion}</p></div>}</ReviewList>
      <div className="error-record-action"><span>{result.errorAnalysis.length > 0 ? text(`${savedCount} / ${result.errorAnalysis.length} 项已加入复习卡片`, `${savedCount} / ${result.errorAnalysis.length} added to review cards`) : text('可手动记录希望继续练习的表达', 'You can manually save an expression for further practice')}</span><button type="button" className="primary-button" onClick={onOpenErrorConfirmation}>{text('添加复习卡片', 'Add review card')}</button></div>
      {result.derivedGenerationStatus === 'FAILED' ? <div className="review-generation-row"><div><strong>{text('衍生题生成失败', 'Derived question generation failed')}</strong><p>{text('本次评分和复习进度已经保存，可单独重试生成。', 'The score and progress were saved. You can retry generation separately.')}</p></div><button type="button" className="primary-button" disabled={derivedGenerating} onClick={onGenerate}>{derivedGenerating ? text('生成中', 'Generating') : text('重试生成', 'Retry generation')}</button></div> : null}
      {result.derivedGenerationStatus === 'SUCCEEDED' ? <div className="notice"><strong>{text('衍生题已生成', 'Derived question generated')}</strong><p>{text('卡片已准备好后续复习题。', 'The card is ready for further review.')}</p></div> : null}
      <div className="review-result-actions">{result.cardStatus === 'ACTIVE' ? <button type="button" className="primary-button" disabled={nextCardLoading || derivedGenerating} onClick={onContinueEarly}>{text('继续提前复习当前卡片', 'Continue reviewing this card early')}</button> : null}<button type="button" className={result.cardStatus === 'MASTERED' ? 'primary-button' : undefined} disabled={nextCardLoading || derivedGenerating} onClick={onNext}>{nextCardLoading ? text('加载中', 'Loading') : text('复习下一张', 'Review next card')}</button><button type="button" disabled={nextCardLoading || derivedGenerating} onClick={onBack}>{text('返回列表', 'Back to list')}</button><span>{result.cardStatus === 'MASTERED' ? text('当前卡片已掌握', 'This card is mastered') : text(`下次到期：${formatDateTime(result.nextDueAt)}`, `Next due: ${formatDateTime(result.nextDueAt)}`)}</span></div>
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
  return <div className="review-question-meta"><span>{question.level}</span><span>{text('难度', 'Difficulty')} {question.difficulty}</span>{flags.map((flag) => <span key={flag}>{flag}</span>)}{question.tags.map((tag) => <span key={tag.id}>{english ? tag.nameEn : tag.name}</span>)}<span>{text(`已作答 ${question.attemptCount} 次`, `${question.attemptCount} attempts`)}</span></div>
}

function ReviewMetrics({ progress }: { progress: ReviewCycleProgress }) {
  const { text } = useLanguage()
  return <div className="review-metric-grid"><div><span>{text('当前周期', 'Current cycle')}</span><strong>{text(`第 ${progress.cycleNo} 轮`, `Cycle ${progress.cycleNo}`)}</strong></div><div><span>{text('净成功进度', 'Net success progress')}</span><strong>{progress.netSuccessCount} / {progress.targetSuccessCount}</strong><ProgressBar progress={progress} /><small>{text(`成功 ${progress.successfulReviewCount} · 失败 ${progress.failedReviewCount}`, `Passed ${progress.successfulReviewCount} · Failed ${progress.failedReviewCount}`)}</small></div><div><span>{text('原题通过', 'Originals passed')}</span><strong>{progress.originalPassedCount} / {progress.originalQuestionCount}</strong></div><div><span>{text('待处理', 'Pending')}</span><strong>{progress.retryQuestionCount + progress.pendingQuestionCount}</strong><small>{text(`${progress.retryQuestionCount} 道重试`, `${progress.retryQuestionCount} retries`)}</small></div></div>
}

function ProgressBar({ progress }: { progress: ReviewCycleProgress }) {
  const { text } = useLanguage()
  const percentage = progress.targetSuccessCount === 0 ? 0 : Math.max(0, Math.min(100, Math.round(progress.netSuccessCount / progress.targetSuccessCount * 100)))
  return <div className="review-progress" aria-label={text(`净成功进度 ${percentage}%`, `Net success progress ${percentage}%`)}><div className="review-progress-track"><span style={{ width: `${percentage}%` }} /></div><small>{progress.netSuccessCount} / {progress.targetSuccessCount}</small></div>
}

function StateMessage({ title, message, actionLabel, actionDisabled = false, onAction }: { title: string; message: string; actionLabel?: string; actionDisabled?: boolean; onAction?: () => void }) {
  return <div className="review-state-message"><strong>{title}</strong><p>{message}</p>{actionLabel && onAction ? <button type="button" className="primary-button" disabled={actionDisabled} onClick={onAction}>{actionLabel}</button> : null}</div>
}

function Notice({ notice, actionLabel, onAction }: { notice: PracticeNotice; actionLabel?: string; onAction?: () => void }) {
  return <div className={notice.kind === 'error' ? 'notice is-error review-notice' : 'notice review-notice'}><strong>{notice.title}</strong><p>{notice.message}</p>{actionLabel && onAction ? <button type="button" onClick={onAction}>{actionLabel}</button> : null}</div>
}

function listModeLabel(mode: ReviewCardListMode) {
  if (mode === 'DUE') return '待复习'
  if (mode === 'ACTIVE') return '全部进行中'
  return '已掌握'
}

function emptyListText(mode: ReviewCardListMode) {
  if (mode === 'DUE') return '当前没有到期卡片。'
  if (mode === 'ACTIVE') return '当前没有进行中的复习卡片。'
  return '目前还没有已掌握卡片。'
}

function reviewStateLabel(state: ReviewCardDetail['reviewState'], text: (zh: string, en: string) => string) {
  if (state === 'READY') return text('可复习', 'Ready')
  if (state === 'WAITING') return text('等待到期', 'Waiting')
  if (state === 'DERIVED_GENERATION_REQUIRED') return text('待生成衍生题', 'Derived question needed')
  return text('已掌握', 'Mastered')
}

function reviewStateClass(state: ReviewCardDetail['reviewState']) {
  if (state === 'READY') return 'is-ready'
  if (state === 'WAITING') return 'is-waiting'
  if (state === 'DERIVED_GENERATION_REQUIRED') return 'is-generation'
  return 'is-mastered'
}

function dueText(value: string | null, text: (zh: string, en: string) => string) {
  if (!value) return '-'
  return isDue(value) ? text(`到期于 ${formatDateTime(value)}`, `Due ${formatDateTime(value)}`) : text(`下次 ${formatDateTime(value)}`, `Next ${formatDateTime(value)}`)
}

function isDue(value: string | null) {
  if (!value) return false
  const time = new Date(value).getTime()
  return Number.isFinite(time) && time <= Date.now()
}

function formatDateTime(value: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function formatTotalScore(value: number) {
  return value.toFixed(2)
}

function formatReviewAttemptScore(value: number | null) {
  return value === null ? '-' : `${value.toFixed(2)} / 100`
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}
