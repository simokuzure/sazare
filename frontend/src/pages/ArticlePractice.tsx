import { useEffect, useMemo, useState } from 'react'
import { getErrorMessage } from '../api/client'
import { fetchRandomQuestion, generateArticle, submitQuestionAnswer } from '../api/questionApi'
import { fetchTags } from '../api/tagApi'
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
import type { AiArticleGenerationPayload, Question } from '../types/question'
import type { AnswerReview } from '../types/review'
import type { Tag } from '../types/tag'
import type { UserAnswerErrorConfirmation, UserErrorType } from '../types/userError'

type ArticleAnswerSession = {
  answerText: string
  answerSubmitted: boolean
  answerScoring: boolean
  answerReview: AnswerReview | null
  answerNotice: PracticeNotice | null
  errorCandidates: ErrorCandidateState[]
  errorConfirmationNotice: PracticeNotice | null
  errorConfirmationOpen: boolean
}

const EMPTY_ARTICLE_SESSION: ArticleAnswerSession = {
  answerText: '',
  answerSubmitted: false,
  answerScoring: false,
  answerReview: null,
  answerNotice: null,
  errorCandidates: [],
  errorConfirmationNotice: null,
  errorConfirmationOpen: false,
}

export default function ArticlePractice() {
  const [genreTags, setGenreTags] = useState<Tag[]>([])
  const [genreTagsLoading, setGenreTagsLoading] = useState(false)
  const [genreTagsError, setGenreTagsError] = useState<string | null>(null)
  const [level, setLevel] = useState('')
  const [difficulty, setDifficulty] = useState('3')
  const [genreTagCode, setGenreTagCode] = useState('')
  const [topic, setTopic] = useState('')
  const [extraRequirements, setExtraRequirements] = useState('')
  const [question, setQuestion] = useState<Question | null>(null)
  const [vocabularyHintsVisible, setVocabularyHintsVisible] = useState(false)
  const [questionGenerating, setQuestionGenerating] = useState(false)
  const [questionRandomizing, setQuestionRandomizing] = useState(false)
  const [session, setSession] = useState<ArticleAnswerSession>(EMPTY_ARTICLE_SESSION)
  const [practiceNotice, setPracticeNotice] = useState<PracticeNotice | null>(null)
  const [userErrorTypes, setUserErrorTypes] = useState<UserErrorType[]>([])
  const [userErrorTypesLoading, setUserErrorTypesLoading] = useState(false)
  const [errorConfirming, setErrorConfirming] = useState(false)

  useEffect(() => {
    const controller = new AbortController()

    async function loadGenreTags() {
      setGenreTagsLoading(true)
      setGenreTagsError(null)
      try {
        const result = await fetchTags(
          { tagType: 'GENRE', enabledOnly: true, page: 1, size: 100 },
          controller.signal,
        )
        setGenreTags(result.items)
      } catch (fetchError: unknown) {
        if (fetchError instanceof DOMException && fetchError.name === 'AbortError') return
        setGenreTags([])
        setGenreTagsError(getErrorMessage(fetchError))
      } finally {
        setGenreTagsLoading(false)
      }
    }

    void loadGenreTags()
    return () => controller.abort()
  }, [])

  const sourceSegments = useMemo(() => splitArticleSegments(question?.sourceText ?? ''), [question?.sourceText])
  const selectedErrorCount = session.errorCandidates.filter((candidate) => candidate.selected && !candidate.saved).length
  const questionLoading = questionGenerating || questionRandomizing
  const answerInputNotice = session.answerNotice ?? practiceNotice

  function replaceQuestion(nextQuestion: Question | null, notice: PracticeNotice) {
    setQuestion(nextQuestion)
    setSession(EMPTY_ARTICLE_SESSION)
    setVocabularyHintsVisible(false)
    setPracticeNotice(notice)
  }

  async function handleGenerateArticle() {
    if (!genreTagCode) {
      setPracticeNotice({ kind: 'error', title: '请选择体裁', message: 'AI 生成文章前需要选择一个体裁。' })
      return
    }

    setQuestionGenerating(true)
    setPracticeNotice(null)
    setVocabularyHintsVisible(false)
    try {
      const generated = await generateArticle(buildArticleGenerationPayload())
      replaceQuestion(generated, {
        kind: 'info',
        title: generated ? '文章已生成' : '未生成文章',
        message: generated ? `已生成文章题目 #${generated.id}。` : '请调整生成条件后重试。',
      })
    } catch (fetchError: unknown) {
      replaceQuestion(null, { kind: 'error', title: '生成失败', message: getErrorMessage(fetchError) })
    } finally {
      setQuestionGenerating(false)
    }
  }

  async function handleRandomArticle() {
    setQuestionRandomizing(true)
    setPracticeNotice(null)
    setVocabularyHintsVisible(false)
    try {
      const randomQuestion = await fetchRandomQuestion({
        questionType: 'TRANSLATION_ZH_TO_JA_ARTICLE',
        level,
        difficulty,
        tagCodes: genreTagCode ? [genreTagCode] : [],
      })
      replaceQuestion(randomQuestion, {
        kind: 'info',
        title: randomQuestion ? '文章已抽取' : '未找到文章',
        message: randomQuestion ? `已随机抽取文章题目 #${randomQuestion.id}。` : '当前筛选条件下没有可用文章。',
      })
    } catch (fetchError: unknown) {
      replaceQuestion(null, { kind: 'error', title: '抽题失败', message: getErrorMessage(fetchError) })
    } finally {
      setQuestionRandomizing(false)
    }
  }

  async function handleSubmitAnswer() {
    if (!question) {
      setPracticeNotice({ kind: 'error', title: '请先选择文章', message: '生成或随机抽取文章后再提交作答。' })
      return
    }
    const submittedAnswer = session.answerText.trim()
    if (!submittedAnswer) {
      setSession((current) => ({
        ...current,
        answerNotice: { kind: 'error', title: '请填写答案', message: '输入完整日语译文后再提交。' },
      }))
      return
    }

    setSession((current) => ({
      ...current,
      answerText: submittedAnswer,
      answerSubmitted: true,
      answerScoring: true,
      answerReview: null,
      answerNotice: null,
      errorCandidates: [],
      errorConfirmationNotice: null,
      errorConfirmationOpen: false,
    }))
    setPracticeNotice(null)

    try {
      const review = await submitQuestionAnswer(question.id, submittedAnswer)
      setSession((current) => ({
        ...current,
        answerReview: review,
        answerNotice: {
          kind: 'info',
          title: '评分完成',
          message: review ? `本次总分：${formatScore(review.totalScore)}` : '未获得评分结果。',
        },
        errorCandidates: review?.errorAnalysis.length ? review.errorAnalysis.map(toErrorCandidateState) : [],
      }))
    } catch (fetchError: unknown) {
      setSession((current) => ({
        ...current,
        answerNotice: { kind: 'error', title: '评分失败', message: getErrorMessage(fetchError) },
      }))
    } finally {
      setSession((current) => ({ ...current, answerScoring: false }))
    }
  }

  async function loadActiveUserErrorTypes() {
    setUserErrorTypesLoading(true)
    try {
      const result = await fetchUserErrorTypes({ status: 'ACTIVE', page: 1, size: 100 })
      setUserErrorTypes(result.items)
    } catch (fetchError: unknown) {
      setUserErrorTypes([])
      setSession((current) => ({
        ...current,
        errorConfirmationNotice: { kind: 'error', title: '无法加载已有类型', message: getErrorMessage(fetchError) },
      }))
    } finally {
      setUserErrorTypesLoading(false)
    }
  }

  async function handleConfirmErrors() {
    const answerReview = session.answerReview
    if (!answerReview) return
    const selectedItems = answerReview.errorAnalysis
      .map((analysis, index) => ({ analysis, candidate: session.errorCandidates[index], index }))
      .filter(({ candidate }) => candidate?.selected && !candidate.saved)
    if (selectedItems.length === 0) return

    const payload: UserAnswerErrorConfirmation[] = []
    for (const { analysis, candidate, index } of selectedItems) {
      if (candidate.mode === 'NEW_USER_ERROR_TYPE') {
        if (!candidate.userErrorTypeName.trim() || !candidate.userErrorTypeDescription.trim()) {
          setSession((current) => ({
            ...current,
            errorConfirmationNotice: { kind: 'error', title: '请补充用户错误类型', message: '新建类型需要名称和说明。' },
          }))
          return
        }
        payload.push(toNewErrorConfirmation(analysis, candidate, index))
      } else {
        if (!candidate.userErrorTypeId) {
          setSession((current) => ({
            ...current,
            errorConfirmationNotice: { kind: 'error', title: '请选择已有类型', message: '追加记录前请选择对应的用户错误类型。' },
          }))
          return
        }
        payload.push(toExistingErrorConfirmation(analysis, candidate, index))
      }
    }

    setErrorConfirming(true)
    setSession((current) => ({ ...current, errorConfirmationNotice: null }))
    try {
      await confirmUserAnswerErrors(answerReview.userAnswerId, { errors: payload })
      const confirmedIndexes = new Set(selectedItems.map(({ index }) => index))
      setSession((current) => ({
        ...current,
        errorCandidates: current.errorCandidates.map((item, index) => (
          confirmedIndexes.has(index) ? { ...item, selected: false, saved: true } : item
        )),
        errorConfirmationNotice: { kind: 'info', title: '错误已记录', message: `已确认 ${selectedItems.length} 条错误。` },
        errorConfirmationOpen: false,
      }))
      void loadActiveUserErrorTypes()
    } catch (fetchError: unknown) {
      setSession((current) => ({
        ...current,
        errorConfirmationNotice: { kind: 'error', title: '记录失败', message: getErrorMessage(fetchError) },
      }))
    } finally {
      setErrorConfirming(false)
    }
  }

  function updateErrorCandidate(index: number, patch: Partial<ErrorCandidateState>) {
    setSession((current) => ({
      ...current,
      errorCandidates: current.errorCandidates.map((item, itemIndex) => itemIndex === index ? { ...item, ...patch } : item),
      errorConfirmationNotice: null,
    }))
  }

  function buildArticleGenerationPayload(): AiArticleGenerationPayload {
    const payload: AiArticleGenerationPayload = { genreTagCode }
    if (level) payload.level = level
    if (difficulty) payload.difficulty = Number(difficulty)
    if (topic.trim()) payload.topic = topic.trim()
    if (extraRequirements.trim()) payload.extraRequirements = extraRequirements.trim()
    return payload
  }

  return (
    <div className="practice-grid article-practice-grid">
      <section className="surface generator-panel" aria-label="文章题目生成">
        <form className="form-grid article-generator-form" onSubmit={(event) => event.preventDefault()}>
          <label><span>JLPT 等级</span><select value={level} onChange={(event) => setLevel(event.target.value)}><option value="">默认 N3</option>{['N5', 'N4', 'N3', 'N2', 'N1'].map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
          <label><span>难度</span><select value={difficulty} onChange={(event) => setDifficulty(event.target.value)}>{[1, 2, 3, 4, 5].map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
          <label><span>文章体裁</span><select value={genreTagCode} disabled={genreTagsLoading} onChange={(event) => setGenreTagCode(event.target.value)}><option value="">{genreTagsLoading ? '加载中' : '不限（仅随机）'}</option>{genreTags.map((tag) => <option key={tag.id} value={tag.code}>{tag.name}</option>)}</select></label>
          <label><span>主题</span><input value={topic} maxLength={100} placeholder="可选，例如：周末旅行" onChange={(event) => setTopic(event.target.value)} /></label>
          <label className="wide-field"><span>补充要求</span><input value={extraRequirements} maxLength={500} placeholder="例如：书面语、保持敬体、关注篇章衔接" onChange={(event) => setExtraRequirements(event.target.value)} /></label>
          <button type="button" disabled={questionLoading} onClick={handleRandomArticle}>{questionRandomizing ? '抽题中' : '随机文章'}</button>
          <button type="button" className="primary-button" disabled={questionLoading || !genreTagCode} onClick={handleGenerateArticle}>{questionGenerating ? '生成中' : '生成文章'}</button>
        </form>
        {!genreTagCode && !genreTagsLoading ? <p className="field-hint">随机抽题可以不限体裁；AI 生成文章前需要选择体裁。</p> : null}
        {genreTagsError ? <div className="error-message">体裁标签加载失败：{genreTagsError}</div> : null}
      </section>

      <section className="surface question-panel article-question-panel" aria-label="文章题目预览">
        <div className="section-title"><span className="label">文章预览</span><strong>{question ? `题目 #${question.id}` : '尚未选择文章'}</strong></div>
        {question ? (
          <ol className="article-segment-list">
            {sourceSegments.map((segment, index) => <li key={`${index}-${segment}`}>{segment}</li>)}
          </ol>
        ) : <p className="empty-state">暂无文章</p>}
        <dl className="question-details article-metadata">
          <div><dt>语境</dt><dd>{question?.contextText ?? '暂无'}</dd></div>
          <div><dt>生词提示</dt><dd className="grammar-point"><button type="button" disabled={!question} aria-expanded={vocabularyHintsVisible} onClick={() => setVocabularyHintsVisible((visible) => !visible)}>{vocabularyHintsVisible ? '隐藏提示' : '显示提示'}</button>{vocabularyHintsVisible ? <span className="pre-wrap-text">{question?.grammarPoint ?? '暂无'}</span> : null}</dd></div>
          <div><dt>体裁</dt><dd>{question ? <span className="tag-chip-row">{question.tags.map((tag) => <span key={tag.id}>{tag.name}</span>)}</span> : '暂无'}</dd></div>
          <div><dt>难度</dt><dd>{question ? `${question.level} / ${question.difficulty}` : '暂无'}</dd></div>
        </dl>
      </section>

      <section className="surface answer-panel article-answer-panel" aria-label={session.answerSubmitted ? '文章评分结果' : '文章答案输入'}>
        <div className="section-title"><span className="label">{session.answerSubmitted ? '评分结果' : '作答'}</span><strong>{session.answerSubmitted ? '本次文章翻译结果' : '输入完整日语译文'}</strong></div>
        {!session.answerSubmitted ? (
          <>
            {answerInputNotice && (answerInputNotice.kind === 'error' || !question) ? <Notice notice={answerInputNotice} /> : null}
            <textarea className="article-answer-input" value={session.answerText} maxLength={5000} disabled={!question} placeholder={question ? '请输入完整日语译文；可以合并、拆分或调整句序' : '生成或随机抽取文章后即可作答'} onChange={(event) => setSession((current) => ({ ...current, answerText: event.target.value }))} />
            <div className="answer-input-footer"><span>{session.answerText.length} / 5000</span><div className="action-row"><button type="button" className="primary-button" disabled={!question || session.answerScoring} onClick={handleSubmitAnswer}>{session.answerScoring ? '评分中' : '提交答案'}</button><button type="button" disabled={!question && !session.answerText} onClick={() => { setSession(EMPTY_ARTICLE_SESSION); setPracticeNotice(null) }}>清空</button></div></div>
          </>
        ) : (
          <div className="answer-result">
            {session.answerNotice && (!session.answerReview || session.answerNotice.kind === 'error') ? <Notice notice={session.answerNotice} /> : null}
            {session.answerScoring ? <div className="notice"><strong>评分中</strong><p>正在按中文原句分析完整译文。</p></div> : null}
            <section className="submitted-answer pre-wrap-text"><span className="label">你的完整译文</span><p>{session.answerText}</p></section>
            {session.answerReview ? <ArticleReviewResult
              review={session.answerReview}
              candidates={session.errorCandidates}
              errorConfirmationOpen={session.errorConfirmationOpen}
              userErrorTypes={userErrorTypes}
              userErrorTypesLoading={userErrorTypesLoading}
              errorConfirmationNotice={session.errorConfirmationNotice}
              errorConfirming={errorConfirming}
              selectedErrorCount={selectedErrorCount}
              onUpdateErrorCandidate={updateErrorCandidate}
              onConfirmErrors={handleConfirmErrors}
              onOpenErrorConfirmation={() => { setSession((current) => ({ ...current, errorConfirmationNotice: null, errorConfirmationOpen: true })); void loadActiveUserErrorTypes() }}
              onCloseErrorConfirmation={() => setSession((current) => ({ ...current, errorConfirmationOpen: false }))}
            /> : null}
            <div className="action-row"><button type="button" className="primary-button" disabled={session.answerScoring || errorConfirming} onClick={() => { setSession((current) => ({ ...current, answerSubmitted: false, answerScoring: false, answerReview: null, answerNotice: null, errorCandidates: [], errorConfirmationNotice: null, errorConfirmationOpen: false })); setPracticeNotice(null) }}>修改答案</button><button type="button" disabled={session.answerScoring || errorConfirming} onClick={() => { setSession(EMPTY_ARTICLE_SESSION); setPracticeNotice(null) }}>清空</button></div>
          </div>
        )}
      </section>
    </div>
  )
}

type ArticleReviewResultProps = {
  review: AnswerReview
  candidates: ErrorCandidateState[]
  errorConfirmationOpen: boolean
  userErrorTypes: UserErrorType[]
  userErrorTypesLoading: boolean
  errorConfirmationNotice: PracticeNotice | null
  errorConfirming: boolean
  selectedErrorCount: number
  onUpdateErrorCandidate: (index: number, patch: Partial<ErrorCandidateState>) => void
  onConfirmErrors: () => void
  onOpenErrorConfirmation: () => void
  onCloseErrorConfirmation: () => void
}

function ArticleReviewResult(props: ArticleReviewResultProps) {
  const { review, candidates } = props
  return (
    <>
      <div className="score-summary"><span>总分</span><strong>{formatScore(review.totalScore)}</strong></div>
      <section className="review-section article-overall-comment"><strong>全文总评</strong><p>{review.overallComment}</p></section>
      <section className="article-revised-answer pre-wrap-text"><strong>完整修订译文</strong><p>{review.revisedAnswer || '本次未返回完整修订译文。'}</p></section>

      <details className="review-detail article-sentence-review-detail">
        <summary>逐句对照（{review.sentenceReviews.length} 句）</summary>
        <ol className="sentence-review-list">
          {review.sentenceReviews.map((item) => (
            <li key={item.sourceSegmentIndex}>
              <dl>
                <div><dt>中文原句</dt><dd>{item.sourceText}</dd></div>
                <div><dt>你的对应译文</dt><dd>{item.answerExcerpt || <span className="omission-text">未找到对应译文</span>}</dd></div>
                <div><dt>参考译文</dt><dd>{item.referenceText}</dd></div>
                {item.revisedText !== item.referenceText ? <div><dt>修订句</dt><dd>{item.revisedText}</dd></div> : null}
                <div><dt>评语</dt><dd>{item.comment}</dd></div>
              </dl>
            </li>
          ))}
        </ol>
      </details>

      <details className="review-detail">
        <summary>详细评分与错误</summary>
        <div className="review-result">
          <dl className="score-grid"><div><dt>语法与用词</dt><dd>{review.scores.grammarVocabularyScore}</dd></div><div><dt>自然度与篇章连贯</dt><dd>{review.scores.naturalFluencyScore}</dd></div><div><dt>体裁与语域</dt><dd>{review.scores.scenarioAdaptationScore}</dd></div><div><dt>忠实度与完整性</dt><dd>{review.scores.informationCompletenessScore}</dd></div></dl>
          <dl className="comment-list"><div><dt>语法</dt><dd>{review.comments.grammarComment}</dd></div><div><dt>词汇</dt><dd>{review.comments.vocabularyComment}</dd></div><div><dt>自然度与篇章</dt><dd>{review.comments.naturalnessComment}</dd></div><div><dt>体裁与语域</dt><dd>{review.comments.scenarioComment}</dd></div></dl>
          <ReviewList title="候选错误" emptyText="本次未发现明确错误。" items={review.errorAnalysis}>{(item) => <div><span>{item.errorTypeName} / {item.severity}</span><strong>{item.original}</strong><p>{item.issue}</p><p>{item.suggestion}</p></div>}</ReviewList>
          {review.errorAnalysis.length > 0 ? <div className="error-record-action"><span>{candidates.filter((candidate) => candidate.saved).length} / {review.errorAnalysis.length} 条错误已记录</span><button type="button" className="primary-button" disabled={candidates.every((candidate) => candidate.saved)} onClick={props.onOpenErrorConfirmation}>记录错误</button></div> : null}
          <ReviewList title="修改建议" emptyText="本次没有额外修改建议。" items={review.revisionSuggestions}>{(item) => <p>{item}</p>}</ReviewList>
          <ReviewList title="推荐表达" emptyText="本次没有推荐表达。" items={review.recommendedExpressions}>{(item) => <div><span>{item.formality}</span><strong>{item.expression}</strong><p>{item.usage}</p><p>{item.note}</p></div>}</ReviewList>
        </div>
      </details>

      {props.errorConfirmationOpen ? <ErrorConfirmationModal
        analyses={review.errorAnalysis}
        candidates={candidates}
        userErrorTypes={props.userErrorTypes}
        userErrorTypesLoading={props.userErrorTypesLoading}
        notice={props.errorConfirmationNotice}
        confirming={props.errorConfirming}
        selectedCount={props.selectedErrorCount}
        onUpdate={props.onUpdateErrorCandidate}
        onConfirm={props.onConfirmErrors}
        onClose={props.onCloseErrorConfirmation}
      /> : null}
    </>
  )
}

function splitArticleSegments(text: string) {
  const normalized = text.replace(/\r\n?/g, '\n').trim()
  if (!normalized) return []
  return normalized.split(/\n\s*\n/).map((segment) => segment.trim()).filter(Boolean)
}

function Notice({ notice }: { notice: PracticeNotice }) {
  return <div className={notice.kind === 'error' ? 'notice is-error' : 'notice'}><strong>{notice.title}</strong><p>{notice.message}</p></div>
}

function formatScore(score: number) {
  return score.toFixed(2)
}
