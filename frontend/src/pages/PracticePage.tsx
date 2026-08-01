import { useEffect, useMemo, useState } from 'react'
import { getErrorMessage } from '../api/client'
import { generateQuestions, submitQuestionAnswer } from '../api/questionApi'
import { fetchTags } from '../api/tagApi'
import ReviewList from '../components/ReviewList'
import type { PracticeNotice } from '../types/api'
import type { Tag } from '../types/tag'
import type { AiQuestionGenerationPayload, Question } from '../types/question'
import type { AnswerReview } from '../types/review'

export default function PracticePage() {
  const [practiceTags, setPracticeTags] = useState<Tag[]>([])
  const [practiceTagsLoading, setPracticeTagsLoading] = useState(false)
  const [practiceTagsError, setPracticeTagsError] = useState<string | null>(null)
  const [questionCount, setQuestionCount] = useState('1')
  const [level, setLevel] = useState('')
  const [difficulty, setDifficulty] = useState('3')
  const [sceneTagCode, setSceneTagCode] = useState('')
  const [functionTagCode, setFunctionTagCode] = useState('')
  const [extraRequirements, setExtraRequirements] = useState('')
  const [generatedQuestions, setGeneratedQuestions] = useState<Question[]>([])
  const [selectedQuestionIndex, setSelectedQuestionIndex] = useState(0)
  const [questionGenerating, setQuestionGenerating] = useState(false)
  const [answerText, setAnswerText] = useState('')
  const [answerSubmitted, setAnswerSubmitted] = useState(false)
  const [answerScoring, setAnswerScoring] = useState(false)
  const [answerReview, setAnswerReview] = useState<AnswerReview | null>(null)
  const [practiceNotice, setPracticeNotice] = useState<PracticeNotice | null>(null)

  useEffect(() => {
    const controller = new AbortController()

    async function fetchPracticeTags() {
      setPracticeTagsLoading(true)
      setPracticeTagsError(null)

      try {
        const result = await fetchTags({ enabledOnly: true, page: 1, size: 100 }, controller.signal)

        setPracticeTags(result.items)
      } catch (fetchError: unknown) {
        if (fetchError instanceof DOMException && fetchError.name === 'AbortError') {
          return
        }
        setPracticeTags([])
        setPracticeTagsError(fetchError instanceof Error ? fetchError.message : '请求失败')
      } finally {
        setPracticeTagsLoading(false)
      }
    }

    fetchPracticeTags()

    return () => {
      controller.abort()
    }
  }, [])

  const sceneTags = useMemo(() => practiceTags.filter((tag) => tag.tagType === 'SCENE'), [practiceTags])
  const functionTags = useMemo(() => practiceTags.filter((tag) => tag.tagType === 'FUNCTION'), [practiceTags])
  const selectedQuestion = generatedQuestions[selectedQuestionIndex] ?? null

  async function handleGenerateQuestion() {
    setQuestionGenerating(true)
    setPracticeNotice(null)

    try {
      const questions = await generateQuestions(buildAiQuestionGenerationPayload())
      setGeneratedQuestions(questions)
      setSelectedQuestionIndex(0)
      setAnswerText('')
      setAnswerSubmitted(false)
      setAnswerReview(null)
      setPracticeNotice({
        kind: 'info',
        title: questions.length > 0 ? '题目已生成' : '没有返回题目',
        message: questions.length > 0 ? `已生成 ${questions.length} 道题。` : '后端没有返回可展示的题目。',
      })
    } catch (fetchError: unknown) {
      setGeneratedQuestions([])
      setSelectedQuestionIndex(0)
      setAnswerText('')
      setAnswerSubmitted(false)
      setAnswerReview(null)
      setPracticeNotice({
        kind: 'error',
        title: '题目生成失败',
        message: getErrorMessage(fetchError),
      })
    } finally {
      setQuestionGenerating(false)
    }
  }

  function handleSelectQuestion(index: number) {
    setSelectedQuestionIndex(index)
    setAnswerText('')
    setAnswerSubmitted(false)
    setAnswerReview(null)
    setPracticeNotice(null)
  }

  async function handleSubmitAnswer() {
    if (!selectedQuestion) {
      setPracticeNotice({
        kind: 'error',
        title: '请先生成题目',
        message: '生成题目后再填写日语回答。',
      })
      return
    }

    const submittedAnswer = answerText.trim()

    if (!submittedAnswer) {
      setPracticeNotice({
        kind: 'error',
        title: '请先输入答案',
        message: '提交前需要填写日语回答。',
      })
      return
    }

    setAnswerText(submittedAnswer)
    setAnswerSubmitted(true)
    setAnswerScoring(true)
    setAnswerReview(null)
    setPracticeNotice(null)

    try {
      const review = await submitQuestionAnswer(selectedQuestion.id, submittedAnswer)

      setAnswerReview(review)
      setPracticeNotice({
        kind: 'info',
        title: '评分完成',
        message: review ? `本次总分 ${formatScore(review.totalScore)}。` : '后端没有返回评分结果。',
      })
    } catch (fetchError: unknown) {
      setPracticeNotice({
        kind: 'error',
        title: '评分失败',
        message: getErrorMessage(fetchError),
      })
    } finally {
      setAnswerScoring(false)
    }
  }

  function handleClearAnswer() {
    setAnswerText('')
    setAnswerSubmitted(false)
    setAnswerReview(null)
    setPracticeNotice(null)
  }

  function handleEditAnswer() {
    setAnswerSubmitted(false)
    setAnswerReview(null)
    setPracticeNotice(null)
  }

  function buildAiQuestionGenerationPayload() {
    const payload: AiQuestionGenerationPayload = {}

    if (questionCount) {
      payload.questionCount = Number(questionCount)
    }
    if (level) {
      payload.level = level
    }
    if (difficulty) {
      payload.difficulty = Number(difficulty)
    }
    if (sceneTagCode) {
      payload.sceneTagCodes = [sceneTagCode]
    }
    if (functionTagCode) {
      payload.functionTagCodes = [functionTagCode]
    }
    if (generatedQuestions.length > 0) {
      payload.excludedSourceTexts = generatedQuestions.map((question) => question.sourceText)
    }
    if (extraRequirements.trim()) {
      payload.extraRequirements = extraRequirements.trim()
    }

    return payload
  }

  return (
          <section className="page-content" aria-label="practice page">
            <div className="practice-grid">
              <section className="surface generator-panel" aria-label="question generator">
                <form className="form-grid" onSubmit={(event) => event.preventDefault()}>
                  <label>
                    <span>题目数量</span>
                    <select value={questionCount} onChange={(event) => setQuestionCount(event.target.value)}>
                      <option value="1">1</option>
                      <option value="2">2</option>
                      <option value="3">3</option>
                      <option value="4">4</option>
                      <option value="5">5</option>
                    </select>
                  </label>

                  <label>
                    <span>JLPT 等级</span>
                    <select value={level} onChange={(event) => setLevel(event.target.value)}>
                      <option value="">默认 N3</option>
                      <option value="N5">N5</option>
                      <option value="N4">N4</option>
                      <option value="N3">N3</option>
                      <option value="N2">N2</option>
                      <option value="N1">N1</option>
                    </select>
                  </label>

                  <label>
                    <span>难度</span>
                    <select value={difficulty} onChange={(event) => setDifficulty(event.target.value)}>
                      <option value="1">1 - 入门</option>
                      <option value="2">2 - 简单</option>
                      <option value="3">3 - 标准</option>
                      <option value="4">4 - 较难</option>
                      <option value="5">5 - 挑战</option>
                    </select>
                  </label>

                  <label>
                    <span>场景标签</span>
                    <select value={sceneTagCode} onChange={(event) => setSceneTagCode(event.target.value)}>
                      <option value="">{practiceTagsLoading ? '标签加载中' : '不限制场景'}</option>
                      {sceneTags.map((tag) => (
                        <option key={tag.id} value={tag.code}>
                          {tag.name}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label>
                    <span>功能标签</span>
                    <select value={functionTagCode} onChange={(event) => setFunctionTagCode(event.target.value)}>
                      <option value="">{practiceTagsLoading ? '标签加载中' : '不限制功能'}</option>
                      {functionTags.map((tag) => (
                        <option key={tag.id} value={tag.code}>
                          {tag.name}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className="wide-field">
                    <span>额外要求</span>
                    <input
                      value={extraRequirements}
                      maxLength={500}
                      placeholder="例如：偏口语、商务场景、考试表达"
                      onChange={(event) => setExtraRequirements(event.target.value)}
                    />
                  </label>

                  <button
                    type="button"
                    className="primary-button"
                    disabled={questionGenerating}
                    onClick={handleGenerateQuestion}
                  >
                    {questionGenerating ? '生成中' : '生成题目'}
                  </button>
                </form>

                {practiceTagsError ? <div className="error-message">标签加载失败：{practiceTagsError}</div> : null}
              </section>

              <section className="surface question-panel" aria-label="question preview">
                <div className="section-title">
                  <span className="label">题目展示</span>
                  <strong>{selectedQuestion ? `题目 #${selectedQuestion.id}` : '等待生成题目'}</strong>
                </div>

                {generatedQuestions.length > 1 ? (
                  <div className="question-selector" aria-label="generated questions">
                    {generatedQuestions.map((question, index) => (
                      <button
                        key={question.id}
                        type="button"
                        className={selectedQuestionIndex === index ? 'is-selected' : ''}
                        onClick={() => handleSelectQuestion(index)}
                      >
                        {index + 1}
                      </button>
                    ))}
                  </div>
                ) : null}

                <dl className="question-details">
                  <div>
                    <dt>中文原文</dt>
                    <dd>{selectedQuestion?.sourceText ?? '待生成'}</dd>
                  </div>
                  <div>
                    <dt>语境</dt>
                    <dd>{selectedQuestion?.contextText ?? '待后端返回'}</dd>
                  </div>
                  <div>
                    <dt>语法点</dt>
                    <dd>{selectedQuestion?.grammarPoint ?? '待后端返回'}</dd>
                  </div>
                  <div>
                    <dt>标签</dt>
                    <dd>
                      {selectedQuestion ? (
                        <span className="tag-chip-row">
                          {selectedQuestion.tags.map((tag) => (
                            <span key={tag.id}>{tag.name}</span>
                          ))}
                        </span>
                      ) : (
                        '待后端返回'
                      )}
                    </dd>
                  </div>
                  <div>
                    <dt>难度</dt>
                    <dd>
                      {selectedQuestion
                        ? `${selectedQuestion.level} / ${selectedQuestion.difficulty}`
                        : '待后端返回'}
                    </dd>
                  </div>
                  <div>
                    <dt>来源</dt>
                    <dd>{selectedQuestion ? selectedQuestion.sourceType : '待后端返回'}</dd>
                  </div>
                </dl>
              </section>

              <section className="surface answer-panel" aria-label={answerSubmitted ? 'answer result' : 'answer input'}>
                <div className="section-title">
                  <span className="label">{answerSubmitted ? '结果' : '回答'}</span>
                  <strong>{answerSubmitted ? '本次提交结果' : '输入日语译文'}</strong>
                </div>

                {!answerSubmitted ? (
                  <>
                    {practiceNotice && (practiceNotice.kind === 'error' || !selectedQuestion) ? (
                      <div className={practiceNotice.kind === 'error' ? 'notice is-error' : 'notice'}>
                        <strong>{practiceNotice.title}</strong>
                        <p>{practiceNotice.message}</p>
                      </div>
                    ) : null}

                    <textarea
                      value={answerText}
                      disabled={!selectedQuestion}
                      placeholder={selectedQuestion ? '输入你的日语回答。' : '题目生成后，在这里输入你的日语回答。'}
                      onChange={(event) => setAnswerText(event.target.value)}
                    />

                    <div className="action-row">
                      <button
                        type="button"
                        className="primary-button"
                        disabled={!selectedQuestion || answerScoring}
                        onClick={handleSubmitAnswer}
                      >
                        {answerScoring ? '评分中' : '提交答案'}
                      </button>
                      <button type="button" disabled={!selectedQuestion && !answerText} onClick={handleClearAnswer}>
                        清空
                      </button>
                    </div>
                  </>
                ) : (
                  <div className="answer-result">
                    {practiceNotice && (!answerReview || practiceNotice.kind === 'error') ? (
                      <div className={practiceNotice.kind === 'error' ? 'notice is-error' : 'notice'}>
                        <strong>{practiceNotice.title}</strong>
                        <p>{practiceNotice.message}</p>
                      </div>
                    ) : null}

                    {answerScoring ? (
                      <div className="notice">
                        <strong>评分中</strong>
                        <p>答案已提交，正在等待后端返回评分结果。</p>
                      </div>
                    ) : null}

                    <section className="submitted-answer">
                      <span className="label">你的答案</span>
                      <p>{answerText}</p>
                    </section>

                    {answerReview ? (
                      <>
                        <div className="score-summary">
                          <span>总分</span>
                          <strong>{formatScore(answerReview.totalScore)}</strong>
                        </div>

                        <details className="review-detail">
                          <summary>查看详细评价</summary>
                          <div className="review-result">
                            <dl className="score-grid">
                              <div>
                                <dt>语法与词汇</dt>
                                <dd>{answerReview.scores.grammarVocabularyScore}</dd>
                              </div>
                              <div>
                                <dt>自然度与流畅度</dt>
                                <dd>{answerReview.scores.naturalFluencyScore}</dd>
                              </div>
                              <div>
                                <dt>敬语与场景</dt>
                                <dd>{answerReview.scores.scenarioAdaptationScore}</dd>
                              </div>
                              <div>
                                <dt>表达完整性</dt>
                                <dd>{answerReview.scores.informationCompletenessScore}</dd>
                              </div>
                            </dl>

                            <section className="review-section">
                              <strong>总评</strong>
                              <p>{answerReview.overallComment}</p>
                            </section>

                            <dl className="comment-list">
                              <div>
                                <dt>语法评价</dt>
                                <dd>{answerReview.comments.grammarComment}</dd>
                              </div>
                              <div>
                                <dt>词汇评价</dt>
                                <dd>{answerReview.comments.vocabularyComment}</dd>
                              </div>
                              <div>
                                <dt>自然度评价</dt>
                                <dd>{answerReview.comments.naturalnessComment}</dd>
                              </div>
                              <div>
                                <dt>场景适合度评价</dt>
                                <dd>{answerReview.comments.scenarioComment}</dd>
                              </div>
                            </dl>

                            <ReviewList title="错误分析" emptyText="本次没有返回具体错误。" items={answerReview.errorAnalysis}>
                              {(item) => (
                                <div>
                                  <span>{item.type} / {item.severity}</span>
                                  <strong>{item.original}</strong>
                                  <p>{item.issue}</p>
                                  <p>{item.suggestion}</p>
                                </div>
                              )}
                            </ReviewList>

                            <ReviewList title="修改建议" emptyText="本次没有返回修改建议。" items={answerReview.revisionSuggestions}>
                              {(item) => <p>{item}</p>}
                            </ReviewList>

                            <ReviewList
                              title="推荐表达"
                              emptyText="本次没有返回推荐表达。"
                              items={answerReview.recommendedExpressions}
                            >
                              {(item) => (
                                <div>
                                  <span>{item.formality}</span>
                                  <strong>{item.expression}</strong>
                                  <p>{item.usage}</p>
                                  <p>{item.note}</p>
                                </div>
                              )}
                            </ReviewList>
                          </div>
                        </details>
                      </>
                    ) : null}

                    {selectedQuestion ? (
                      <section className="answer-reference">
                        <strong>标准答案</strong>
                        <ol>
                          {selectedQuestion.answers.map((answer) => (
                            <li key={answer.id}>
                              <span>
                                {answer.answerType === 'STANDARD' ? '标准' : '参考'}
                                {answer.primaryAnswer ? ' / 主答案' : ''}
                              </span>
                              <strong>{answer.answerText}</strong>
                            </li>
                          ))}
                        </ol>
                      </section>
                    ) : null}

                    <div className="action-row">
                      <button type="button" className="primary-button" disabled={answerScoring} onClick={handleEditAnswer}>
                        返回修改
                      </button>
                      <button type="button" disabled={answerScoring} onClick={handleClearAnswer}>
                        清空
                      </button>
                    </div>
                  </div>
                )}
              </section>
            </div>
          </section>
  )
}

function formatScore(score: number) {
  return score.toFixed(2)
}
