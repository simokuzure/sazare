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
import type { AiArticleGenerationPayload, AiArticleLengthTier, Question } from '../types/question'
import type { AnswerReview } from '../types/review'
import type { Tag } from '../types/tag'
import type { UserAnswerErrorConfirmation, UserErrorType } from '../types/userError'
import { useLanguage } from '../i18n/LanguageContext'
import { scoreToneClassName } from '../utils/score'
import { getTagDisplayName } from '../utils/tag'

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
  const { english, learningMode, articleQuestionType, text } = useLanguage()
  const [genreTags, setGenreTags] = useState<Tag[]>([])
  const [genreTagsLoading, setGenreTagsLoading] = useState(false)
  const [genreTagsError, setGenreTagsError] = useState<string | null>(null)
  const [level, setLevel] = useState('N3')
  const [difficulty, setDifficulty] = useState('3')
  const [lengthTier, setLengthTier] = useState<AiArticleLengthTier>('MEDIUM')
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
    setQuestionGenerating(true)
    setPracticeNotice(null)
    setVocabularyHintsVisible(false)
    try {
      const generated = await generateArticle(buildArticleGenerationPayload())
      replaceQuestion(generated, {
        kind: 'info',
        title: generated ? text('文章已生成', 'Article generated') : text('未生成文章', 'No article generated'),
        message: generated ? text(`已生成文章题目 #${generated.id}。`, `Generated article question #${generated.id}.`) : text('请调整生成条件后重试。', 'Adjust the generation settings and try again.'),
      })
    } catch (fetchError: unknown) {
      replaceQuestion(null, { kind: 'error', title: text('生成失败', 'Generation failed'), message: getErrorMessage(fetchError) })
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
        questionType: articleQuestionType,
        level,
        difficulty,
        tagCodes: genreTagCode ? [genreTagCode] : [],
      })
      replaceQuestion(randomQuestion, {
        kind: 'info',
        title: randomQuestion ? text('文章已抽取', 'Article selected') : text('未找到文章', 'No article found'),
        message: randomQuestion ? text(`已随机抽取文章题目 #${randomQuestion.id}。`, `Randomly selected article question #${randomQuestion.id}.`) : text('当前筛选条件下没有可用文章。', 'No articles match the current filters.'),
      })
    } catch (fetchError: unknown) {
      replaceQuestion(null, { kind: 'error', title: text('抽题失败', 'Could not select an article'), message: getErrorMessage(fetchError) })
    } finally {
      setQuestionRandomizing(false)
    }
  }

  async function handleSubmitAnswer() {
    if (!question) {
      setPracticeNotice({ kind: 'error', title: text('请先选择文章', 'Select an article first'), message: text('生成或随机抽取文章后再提交作答。', 'Generate or select an article before submitting an answer.') })
      return
    }
    const submittedAnswer = session.answerText.trim()
    if (!submittedAnswer) {
      setSession((current) => ({
        ...current,
        answerNotice: { kind: 'error', title: text('请填写答案', 'Enter an answer'), message: text('输入完整日语译文后再提交。', 'Enter the complete Japanese translation before submitting.') },
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
          title: text('评分完成', 'Scoring complete'),
          message: review ? text(`本次总分：${formatScore(review.totalScore)}`, `Total score: ${formatScore(review.totalScore)}`) : text('未获得评分结果。', 'No score was returned.'),
        },
        errorCandidates: review?.errorAnalysis.length ? review.errorAnalysis.map(toErrorCandidateState) : [],
      }))
    } catch (fetchError: unknown) {
      setSession((current) => ({
        ...current,
        answerNotice: { kind: 'error', title: text('评分失败', 'Scoring failed'), message: getErrorMessage(fetchError) },
      }))
    } finally {
      setSession((current) => ({ ...current, answerScoring: false }))
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
        errorConfirmationNotice: { kind: 'error', title: text('无法加载已有复习卡片', 'Could not load review cards'), message: getErrorMessage(fetchError) },
      }))
    } finally {
      setUserErrorTypesLoading(false)
    }
  }

  async function handleConfirmErrors() {
    const answerReview = session.answerReview
    if (!answerReview) return false
    const selectedItems = answerReview.errorAnalysis
      .map((analysis, index) => ({ analysis, candidate: session.errorCandidates[index], index }))
      .filter(({ candidate }) => candidate?.selected && !candidate.saved)
    if (selectedItems.length === 0) return false

    const payload: UserAnswerErrorConfirmation[] = []
    for (const { analysis, candidate, index } of selectedItems) {
      if (candidate.mode === 'NEW_USER_ERROR_TYPE') {
        if (!candidate.userErrorTypeName.trim() || !candidate.userErrorTypeDescription.trim()) {
          setSession((current) => ({
            ...current,
            errorConfirmationNotice: { kind: 'error', title: text('请补充复习卡片', 'Complete the review card'), message: text('新建复习卡片需要名称和说明。', 'A new review card requires a name and description.') },
          }))
          return false
        }
        payload.push(toNewErrorConfirmation(analysis, candidate, index))
      } else {
        if (!candidate.userErrorTypeId) {
          setSession((current) => ({
            ...current,
            errorConfirmationNotice: { kind: 'error', title: text('请选择已有复习卡片', 'Select a review card'), message: text('添加记录前请选择对应的复习卡片。', 'Select the review card before adding this item.') },
          }))
          return false
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
        errorConfirmationNotice: { kind: 'info', title: text('复习卡片已更新', 'Review cards updated'), message: text(`已添加 ${selectedItems.length} 项复习内容。`, `Added ${selectedItems.length} review item(s).`) },
      }))
      void loadActiveUserErrorTypes()
      return true
    } catch (fetchError: unknown) {
      setSession((current) => ({
        ...current,
        errorConfirmationNotice: { kind: 'error', title: text('记录失败', 'Save failed'), message: getErrorMessage(fetchError) },
      }))
      return false
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
    const payload: AiArticleGenerationPayload = { learningMode, lengthTier }
    if (level) payload.level = level
    if (difficulty) payload.difficulty = Number(difficulty)
    if (genreTagCode) payload.genreTagCode = genreTagCode
    if (topic.trim()) payload.topic = topic.trim()
    if (extraRequirements.trim()) payload.extraRequirements = extraRequirements.trim()
    return payload
  }

  return (
    <div className="practice-grid article-practice-grid">
      <section className="surface generator-panel" aria-label={text('文章题目生成', 'Article generation')}>
        <form className="form-grid article-generator-form" onSubmit={(event) => event.preventDefault()}>
          <label><span>{text('JLPT 等级', 'JLPT')}</span><select value={level} onChange={(event) => setLevel(event.target.value)}>{['N5', 'N4', 'N3', 'N2', 'N1'].map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
          <label><span>{text('难度', 'Difficulty')}</span><select value={difficulty} onChange={(event) => setDifficulty(event.target.value)}>{[1, 2, 3, 4, 5].map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
          <label><span>{text('文章长度', 'Length')}</span><select value={lengthTier} onChange={(event) => setLengthTier(event.target.value as AiArticleLengthTier)}><option value="SHORT">{text('短篇（60–100 字）', 'Short (45–75 words)')}</option><option value="MEDIUM">{text('中篇（120–180 字）', 'Medium (90–135 words)')}</option><option value="LONG">{text('长篇（200–280 字）', 'Long (150–210 words)')}</option></select></label>
          <label><span>{text('文章体裁', 'Genre')}</span><select value={genreTagCode} disabled={genreTagsLoading} onChange={(event) => setGenreTagCode(event.target.value)}><option value="">{genreTagsLoading ? text('加载中', 'Loading') : text('不限（随机体裁）', 'Any genre')}</option>{genreTags.map((tag) => <option key={tag.id} value={tag.code}>{getTagDisplayName(tag, english)}</option>)}</select></label>
          <label><span>{text('主题', 'Topic')}</span><input value={topic} maxLength={100} placeholder={text('可选，例如：周末旅行', 'Optional, for example: a weekend trip')} onChange={(event) => setTopic(event.target.value)} /></label>
          <label className="wide-field"><span>{text('补充要求', 'Instructions')}</span><input value={extraRequirements} maxLength={500} placeholder={text('书面语、保持敬体、关注篇章衔接', 'Formal style, consistent register, or cohesive transitions')} onChange={(event) => setExtraRequirements(event.target.value)} /></label>
          <button type="button" className="primary-button" disabled={questionLoading} onClick={handleRandomArticle}>{questionRandomizing ? text('抽题中', 'Loading') : text('随机文章', 'Random')}</button>
          <button type="button" className="primary-button" disabled={questionLoading} onClick={handleGenerateArticle}>{questionGenerating ? text('生成中', 'Generating') : text('生成文章', 'Generate')}</button>
        </form>
        {genreTagsError ? <div className="error-message" role="alert">{text('体裁标签加载失败：', 'Could not load genre tags: ')}{genreTagsError}</div> : null}
      </section>

      <section className="surface question-panel article-question-panel" aria-label={text('文章题目预览', 'Article preview')}>
        <div className="section-title"><span className="label">{text('文章预览', 'Article preview')}</span><strong>{question ? text(`题目 #${question.id}`, `Question #${question.id}`) : text('尚未选择文章', 'No article selected')}</strong></div>
        {question ? (
          <ol className="article-segment-list">
            {sourceSegments.map((segment, index) => <li key={`${index}-${segment}`}>{segment}</li>)}
          </ol>
        ) : null}
        {!question ? <span className="sr-only" role="status">{text('尚未选择文章', 'No article selected')}</span> : null}
        <dl className={`question-details${question ? ' article-metadata' : ''}`}>
          {!question ? <div><dt>{text('文章原文', 'Source')}</dt><dd>{text('暂无文章', 'No article')}</dd></div> : null}
          <div><dt>{text('语境', 'Context')}</dt><dd>{question?.contextText ?? text('暂无', 'None')}</dd></div>
          <div><dt>{text('生词提示', 'Vocabulary hints')}</dt><dd className="grammar-point"><button type="button" disabled={!question} aria-expanded={vocabularyHintsVisible} onClick={() => setVocabularyHintsVisible((visible) => !visible)}>{vocabularyHintsVisible ? text('隐藏提示', 'Hide hints') : text('显示提示', 'Show hints')}</button>{vocabularyHintsVisible ? <span className="pre-wrap-text">{question?.grammarPoint ?? text('暂无', 'None')}</span> : null}</dd></div>
          <div><dt>{text('体裁', 'Genre')}</dt><dd>{question ? <span className="tag-chip-row">{question.tags.map((tag) => <span key={tag.id}>{getTagDisplayName(tag, english)}</span>)}</span> : text('暂无', 'None')}</dd></div>
          <div><dt>{text('难度', 'Difficulty')}</dt><dd>{question ? `${question.level} / ${question.difficulty}` : text('暂无', 'None')}</dd></div>
        </dl>
      </section>

      <section className="surface answer-panel article-answer-panel" aria-label={session.answerSubmitted ? text('文章评分结果', 'Article result') : text('文章答案输入', 'Article answer input')}>
        <div className="section-title"><span className="label">{session.answerSubmitted ? text('评分结果', 'Result') : text('作答', 'Answer')}</span><strong>{session.answerSubmitted ? text('本次文章翻译结果', 'Your article translation result') : text('输入完整日语译文', 'Enter the complete Japanese translation')}</strong></div>
        {!session.answerSubmitted ? (
          <>
            {answerInputNotice && (answerInputNotice.kind === 'error' || !question) ? <Notice notice={answerInputNotice} /> : null}
            <textarea aria-label={text('完整日语译文', 'Complete Japanese translation')} className="article-answer-input" value={session.answerText} maxLength={5000} disabled={!question} placeholder={question ? text('请输入完整日语译文；可以合并、拆分或调整句序', 'Enter the complete Japanese translation; you may merge, split, or reorder sentences.') : text('生成或随机抽取文章后即可作答', 'Generate or select an article to begin.')} onChange={(event) => setSession((current) => ({ ...current, answerText: event.target.value }))} />
            <div className="answer-input-footer"><div className="action-row"><button type="button" className="primary-button" disabled={!question || session.answerScoring} onClick={handleSubmitAnswer}>{session.answerScoring ? text('评分中', 'Scoring') : text('提交答案', 'Submit answer')}</button><button type="button" disabled={!question && !session.answerText} onClick={() => { setSession(EMPTY_ARTICLE_SESSION); setPracticeNotice(null) }}>{text('清空', 'Clear')}</button></div></div>
          </>
        ) : (
          <div className="answer-result">
            {session.answerNotice && (!session.answerReview || session.answerNotice.kind === 'error') ? <Notice notice={session.answerNotice} /> : null}
            {session.answerScoring ? <div className="notice" role="status" aria-live="polite"><strong>{text('评分中', 'Scoring')}</strong><p>{text('正在按中文原句分析完整译文。', 'Analyzing the complete translation against the English source.')}</p></div> : null}
            <section className="submitted-answer pre-wrap-text"><span className="label">{text('你的完整译文', 'Your complete translation')}</span><p>{session.answerText}</p></section>
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
              onCustomSaved={() => void loadActiveUserErrorTypes()}
              onOpenErrorConfirmation={() => { setSession((current) => ({ ...current, errorConfirmationNotice: null, errorConfirmationOpen: true })); if (session.answerReview?.errorAnalysis.length) void loadActiveUserErrorTypes() }}
              onCloseErrorConfirmation={() => setSession((current) => ({ ...current, errorConfirmationOpen: false }))}
            /> : null}
            <div className="action-row"><button type="button" className="primary-button" disabled={session.answerScoring || errorConfirming} onClick={() => { setSession((current) => ({ ...current, answerSubmitted: false, answerScoring: false, answerReview: null, answerNotice: null, errorCandidates: [], errorConfirmationNotice: null, errorConfirmationOpen: false })); setPracticeNotice(null) }}>{text('修改答案', 'Edit answer')}</button><button type="button" disabled={session.answerScoring || errorConfirming} onClick={() => { setSession(EMPTY_ARTICLE_SESSION); setPracticeNotice(null) }}>{text('清空', 'Clear')}</button></div>
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
  onConfirmErrors: () => Promise<boolean>
  onCustomSaved: () => void
  onOpenErrorConfirmation: () => void
  onCloseErrorConfirmation: () => void
}

function ArticleReviewResult(props: ArticleReviewResultProps) {
  const { text } = useLanguage()
  const { review, candidates } = props
  return (
    <>
      <div className="score-summary"><span>{text('总分', 'Total score')}</span><strong className={scoreToneClassName(review.totalScore)}>{formatScore(review.totalScore)}</strong></div>
      <section className="review-section article-overall-comment"><strong>{text('全文总评', 'Overall feedback')}</strong><p>{review.overallComment}</p></section>
      <section className="article-revised-answer pre-wrap-text"><strong>{text('完整修订译文', 'Complete revision')}</strong><p>{review.revisedAnswer || text('本次未返回完整修订译文。', 'No complete revision was returned.')}</p></section>

      <details className="review-detail article-sentence-review-detail">
        <summary>{text(`逐句对照（${review.sentenceReviews.length} 句）`, `Sentence comparison (${review.sentenceReviews.length})`)}</summary>
        <ol className="sentence-review-list">
          {review.sentenceReviews.map((item) => (
            <li key={item.sourceSegmentIndex}>
              <dl>
                <div><dt>{text('中文原句', 'English source sentence')}</dt><dd>{item.sourceText}</dd></div>
                <div><dt>{text('你的对应译文', 'Your corresponding translation')}</dt><dd>{item.answerExcerpt || <span className="omission-text">{text('未找到对应译文', 'No corresponding translation found')}</span>}</dd></div>
                <div><dt>{text('参考译文', 'Reference translation')}</dt><dd>{item.referenceText}</dd></div>
                {item.revisedText !== item.referenceText ? <div><dt>{text('修订句', 'Revised sentence')}</dt><dd>{item.revisedText}</dd></div> : null}
                <div><dt>{text('评语', 'Feedback')}</dt><dd>{item.comment}</dd></div>
              </dl>
            </li>
          ))}
        </ol>
      </details>

      <details className="review-detail">
        <summary>{text('详细评分与错误', 'Detailed scores and errors')}</summary>
        <div className="review-result">
          <dl className="score-grid"><div><dt>{text('语法与用词', 'Grammar & word choice')}</dt><dd className={scoreToneClassName(review.scores.grammarVocabularyScore)}>{review.scores.grammarVocabularyScore}</dd></div><div><dt>{text('自然度与篇章连贯', 'Fluency & coherence')}</dt><dd className={scoreToneClassName(review.scores.naturalFluencyScore)}>{review.scores.naturalFluencyScore}</dd></div><div><dt>{text('体裁与语域', 'Genre & register')}</dt><dd className={scoreToneClassName(review.scores.scenarioAdaptationScore)}>{review.scores.scenarioAdaptationScore}</dd></div><div><dt>{text('忠实度与完整性', 'Fidelity & completeness')}</dt><dd className={scoreToneClassName(review.scores.informationCompletenessScore)}>{review.scores.informationCompletenessScore}</dd></div></dl>
          <dl className="comment-list"><div><dt>{text('语法', 'Grammar')}</dt><dd>{review.comments.grammarComment}</dd></div><div><dt>{text('词汇', 'Vocabulary')}</dt><dd>{review.comments.vocabularyComment}</dd></div><div><dt>{text('自然度与篇章', 'Fluency & coherence')}</dt><dd>{review.comments.naturalnessComment}</dd></div><div><dt>{text('体裁与语域', 'Genre & register')}</dt><dd>{review.comments.scenarioComment}</dd></div></dl>
          <ReviewList title={text('候选错误', 'Candidate errors')} emptyText={text('本次未发现明确错误。', 'No clear errors were found.')} items={review.errorAnalysis}>{(item) => <div><span>{item.errorTypeName} / {item.severity}</span><strong>{item.original}</strong><p>{item.issue}</p><p>{item.suggestion}</p></div>}</ReviewList>
          <ReviewList title={text('修改建议', 'Revision suggestions')} emptyText={text('本次没有额外修改建议。', 'No additional revision suggestions.')} items={review.revisionSuggestions}>{(item) => <p>{item}</p>}</ReviewList>
          <ReviewList title={text('推荐表达', 'Recommended expressions')} emptyText={text('本次没有推荐表达。', 'No recommended expressions.')} items={review.recommendedExpressions}>{(item) => <div><span>{item.formality}</span><strong>{item.expression}</strong><p>{item.usage}</p><p>{item.note}</p></div>}</ReviewList>
        </div>
      </details>
      <div className="error-record-action"><span>{review.errorAnalysis.length > 0 ? text(`${candidates.filter((candidate) => candidate.saved).length} / ${review.errorAnalysis.length} 项已加入复习卡片`, `${candidates.filter((candidate) => candidate.saved).length} / ${review.errorAnalysis.length} added to review cards`) : text('可手动记录希望继续练习的表达', 'You can manually save an expression for further practice')}</span><button type="button" className="primary-button" onClick={props.onOpenErrorConfirmation}>{text('添加复习卡片', 'Add review card')}</button></div>

      {props.errorConfirmationOpen ? <ErrorConfirmationModal
        analyses={review.errorAnalysis}
        candidates={candidates}
        userErrorTypes={props.userErrorTypes}
        userErrorTypesLoading={props.userErrorTypesLoading}
        notice={props.errorConfirmationNotice}
        confirming={props.errorConfirming}
        selectedCount={props.selectedErrorCount}
        userAnswerId={review.userAnswerId}
        reviewCardSource={{ kind: 'ARTICLE', segments: review.sentenceReviews.map((item) => ({ index: item.sourceSegmentIndex, text: item.sourceText, referenceText: item.referenceText })) }}
        recommendedExpressions={review.recommendedExpressions.map((item) => item.expression)}
        onUpdate={props.onUpdateErrorCandidate}
        onConfirm={props.onConfirmErrors}
        onCustomSaved={props.onCustomSaved}
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
  return <div className={notice.kind === 'error' ? 'notice is-error' : 'notice'} role={notice.kind === 'error' ? 'alert' : 'status'}><strong>{notice.title}</strong><p>{notice.message}</p></div>
}

function formatScore(score: number) {
  return score.toFixed(2)
}
