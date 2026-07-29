import { useEffect, useMemo, useState } from 'react'
import './App.css'

type HealthResponse = {
  code: number
  message: string
  data: {
    status: string
    service: string
    timestamp: string
  } | null
}

type ApiResponse<T> = {
  code: number
  message: string
  data: T | null
}

type PageKey = 'practice' | 'tags' | 'questions' | 'reviews'
type TagFilter = '' | 'SCENE' | 'FUNCTION'
type NoticeKind = 'info' | 'error'

type Tag = {
  id: number
  tagType: 'SCENE' | 'FUNCTION'
  parentId: number | null
  code: string
  name: string
  description: string | null
  sortOrder: number
}

type QuestionAnswer = {
  id: number
  answerText: string
  answerType: 'STANDARD' | 'REFERENCE'
  primaryAnswer: boolean
  sortOrder: number
}

type Question = {
  id: number
  questionType: 'TRANSLATION_ZH_TO_JA'
  sourceText: string
  contextText: string
  level: string
  difficulty: number
  grammarPoint: string
  spoken: boolean
  business: boolean
  exam: boolean
  sourceType: 'AI' | 'MANUAL'
  enabled: boolean
  tags: Tag[]
  answers: QuestionAnswer[]
  createdAt: string
  updatedAt: string
}

type PageData<T> = {
  items: T[]
  page: number
  size: number
  total: number
}

type PracticeNotice = {
  kind: NoticeKind
  title: string
  message: string
}

const NAV_ITEMS: { key: PageKey; label: string }[] = [
  { key: 'practice', label: '练习' },
  { key: 'tags', label: '标签管理' },
  { key: 'questions', label: '问题管理' },
  { key: 'reviews', label: '错题复习' },
]

function App() {
  const [activePage, setActivePage] = useState<PageKey>('practice')
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [healthError, setHealthError] = useState<string | null>(null)

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
  const [practiceNotice, setPracticeNotice] = useState<PracticeNotice | null>(null)

  const [tags, setTags] = useState<Tag[]>([])
  const [total, setTotal] = useState(0)
  const [tagType, setTagType] = useState<TagFilter>('')
  const [parentId, setParentId] = useState('')
  const [enabledOnly, setEnabledOnly] = useState(true)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const [tagLoading, setTagLoading] = useState(false)
  const [tagError, setTagError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/health')
      .then((response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`)
        }
        return response.json() as Promise<HealthResponse>
      })
      .then((data) => {
        setHealth(data)
        setHealthError(null)
      })
      .catch((fetchError: unknown) => {
        setHealthError(fetchError instanceof Error ? fetchError.message : '请求失败')
      })
  }, [])

  useEffect(() => {
    const controller = new AbortController()

    async function fetchPracticeTags() {
      setPracticeTagsLoading(true)
      setPracticeTagsError(null)

      const searchParams = new URLSearchParams({
        enabledOnly: 'true',
        page: '1',
        size: '100',
      })

      try {
        const response = await fetch(`/api/tags?${searchParams.toString()}`, {
          signal: controller.signal,
        })
        const result = await readApiResponse<PageData<Tag>>(response)

        setPracticeTags(result.data?.items ?? [])
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

  useEffect(() => {
    const controller = new AbortController()

    async function fetchTags() {
      setTagLoading(true)
      setTagError(null)

      const searchParams = new URLSearchParams()
      if (tagType) {
        searchParams.set('tagType', tagType)
      }
      if (parentId.trim()) {
        searchParams.set('parentId', parentId.trim())
      }
      searchParams.set('enabledOnly', String(enabledOnly))
      searchParams.set('page', String(page))
      searchParams.set('size', String(size))

      try {
        const response = await fetch(`/api/tags?${searchParams.toString()}`, {
          signal: controller.signal,
        })
        const result = await readApiResponse<PageData<Tag>>(response)

        setTags(result.data?.items ?? [])
        setTotal(result.data?.total ?? 0)
      } catch (fetchError: unknown) {
        if (fetchError instanceof DOMException && fetchError.name === 'AbortError') {
          return
        }
        setTags([])
        setTotal(0)
        setTagError(fetchError instanceof Error ? fetchError.message : '请求失败')
      } finally {
        setTagLoading(false)
      }
    }

    fetchTags()

    return () => {
      controller.abort()
    }
  }, [tagType, parentId, enabledOnly, page, size])

  useEffect(() => {
    setPage(1)
  }, [tagType, parentId, enabledOnly, size])

  const sceneTags = useMemo(() => practiceTags.filter((tag) => tag.tagType === 'SCENE'), [practiceTags])
  const functionTags = useMemo(() => practiceTags.filter((tag) => tag.tagType === 'FUNCTION'), [practiceTags])
  const backendStatus = health?.data?.status ?? 'UNKNOWN'
  const sceneCount = tags.filter((tag) => tag.tagType === 'SCENE').length
  const functionCount = tags.filter((tag) => tag.tagType === 'FUNCTION').length
  const totalPages = Math.max(Math.ceil(total / size), 1)
  const firstItemNo = total === 0 ? 0 : (page - 1) * size + 1
  const lastItemNo = Math.min(page * size, total)
  const selectedQuestion = generatedQuestions[selectedQuestionIndex] ?? null

  async function handleGenerateQuestion() {
    setQuestionGenerating(true)
    setPracticeNotice(null)

    try {
      const response = await fetch('/api/questions/ai-generations', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(buildAiQuestionGenerationPayload()),
      })
      const result = await readApiResponse<Question[]>(response)

      const questions = result.data ?? []
      setGeneratedQuestions(questions)
      setSelectedQuestionIndex(0)
      setAnswerText('')
      setPracticeNotice({
        kind: 'info',
        title: questions.length > 0 ? '题目已生成' : '没有返回题目',
        message: questions.length > 0 ? `已生成 ${questions.length} 道题。` : '后端没有返回可展示的题目。',
      })
    } catch (fetchError: unknown) {
      setGeneratedQuestions([])
      setSelectedQuestionIndex(0)
      setPracticeNotice({
        kind: 'error',
        title: '题目生成失败',
        message: getErrorMessage(fetchError),
      })
    } finally {
      setQuestionGenerating(false)
    }
  }

  function handleSubmitAnswer() {
    if (!selectedQuestion) {
      setPracticeNotice({
        kind: 'error',
        title: '请先生成题目',
        message: '生成题目后再填写日语回答。',
      })
      return
    }

    if (!answerText.trim()) {
      setPracticeNotice({
        kind: 'error',
        title: '请先输入答案',
        message: '提交前需要填写日语回答。',
      })
      return
    }

    setPracticeNotice({
      kind: 'info',
      title: '评分接口待接入',
      message: '当前仅完成题目生成。后续接入评分 API 后，这里会提交答案并展示评价。',
    })
  }

  function handleClearAnswer() {
    setAnswerText('')
    setPracticeNotice(null)
  }

  function buildAiQuestionGenerationPayload() {
    const payload: {
      questionCount?: number
      level?: string
      difficulty?: number
      sceneTagCodes?: string[]
      functionTagCodes?: string[]
      excludedSourceTexts?: string[]
      extraRequirements?: string
    } = {}

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
    <main className="app-shell">
      <section className="workspace">
        <header className="app-header">
          <div className="title-group">
            <p className="eyebrow">Japanese Translation Practice</p>
            <h1>日语翻译练习</h1>
          </div>

          <nav className="top-nav" aria-label="主导航">
            {NAV_ITEMS.map((item) => (
              <button
                key={item.key}
                type="button"
                className={activePage === item.key ? 'nav-button is-active' : 'nav-button'}
                aria-current={activePage === item.key ? 'page' : undefined}
                onClick={() => setActivePage(item.key)}
              >
                {item.label}
              </button>
            ))}
          </nav>
        </header>

        {activePage === 'practice' ? (
          <section className="page-content" aria-label="practice page">
            <header className="page-heading">
              <div>
                <p className="eyebrow">首页</p>
                <h2>问题生成与回答</h2>
              </div>
              <StatusBadge label="后端服务" value={backendStatus} />
            </header>

            <div className="practice-grid">
              <section className="surface generator-panel" aria-label="question generator">
                <div className="section-title">
                  <span className="label">生成条件</span>
                  <strong>中译日题目</strong>
                </div>

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
                        onClick={() => setSelectedQuestionIndex(index)}
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

              <section className="surface answer-panel" aria-label="answer input">
                <div className="section-title">
                  <span className="label">回答</span>
                  <strong>输入日语译文</strong>
                </div>

                <textarea
                  value={answerText}
                  disabled={!selectedQuestion}
                  placeholder={selectedQuestion ? '输入你的日语回答。' : '题目生成后，在这里输入你的日语回答。'}
                  onChange={(event) => setAnswerText(event.target.value)}
                />

                <div className="action-row">
                  <button type="button" className="primary-button" disabled={!selectedQuestion} onClick={handleSubmitAnswer}>
                    提交答案
                  </button>
                  <button type="button" disabled={!selectedQuestion && !answerText} onClick={handleClearAnswer}>
                    清空
                  </button>
                </div>
              </section>

              <section className="surface result-panel" aria-label="answer result">
                <div className="section-title">
                  <span className="label">结果</span>
                  <strong>评分状态</strong>
                </div>

                {practiceNotice ? (
                  <div className={practiceNotice.kind === 'error' ? 'notice is-error' : 'notice'}>
                    <strong>{practiceNotice.title}</strong>
                    <p>{practiceNotice.message}</p>
                  </div>
                ) : (
                  <div className="notice">
                    <strong>等待生成</strong>
                    <p>生成题目后这里会显示接口状态和标准答案。</p>
                  </div>
                )}

                {selectedQuestion ? (
                  <details className="answer-reference">
                    <summary>查看标准答案</summary>
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
                  </details>
                ) : null}
              </section>
            </div>
          </section>
        ) : null}

        {activePage === 'tags' ? (
          <section className="page-content" aria-label="tag management page">
            <header className="page-heading">
              <div>
                <p className="eyebrow">管理</p>
                <h2>标签管理</h2>
              </div>
              <StatusBadge label="后端服务" value={backendStatus} />
            </header>

            <div className="status-panel">
              <StatusBlock label="后端服务" value={backendStatus} />
              <StatusBlock label="响应状态" value={healthError ?? health?.message ?? '等待响应'} />
              <StatusBlock label="当前结果" value={tagLoading ? '加载中' : `${firstItemNo}-${lastItemNo} / ${total}`} />
              <StatusBlock label="本页类型" value={`场景 ${sceneCount} / 功能 ${functionCount}`} />
            </div>

            <section className="surface tag-panel" aria-label="tag query">
              <form className="filter-bar" onSubmit={(event) => event.preventDefault()}>
                <label>
                  <span>标签类型</span>
                  <select value={tagType} onChange={(event) => setTagType(event.target.value as TagFilter)}>
                    <option value="">全部</option>
                    <option value="SCENE">场景</option>
                    <option value="FUNCTION">功能</option>
                  </select>
                </label>

                <label>
                  <span>父级 ID</span>
                  <input
                    inputMode="numeric"
                    pattern="[0-9]*"
                    placeholder="不限制"
                    value={parentId}
                    onChange={(event) => setParentId(event.target.value.replace(/\D/g, ''))}
                  />
                </label>

                <label className="checkbox-field">
                  <input
                    type="checkbox"
                    checked={enabledOnly}
                    onChange={(event) => setEnabledOnly(event.target.checked)}
                  />
                  <span>仅启用</span>
                </label>

                <label>
                  <span>每页数量</span>
                  <select value={size} onChange={(event) => setSize(Number(event.target.value))}>
                    <option value={10}>10</option>
                    <option value={20}>20</option>
                    <option value={50}>50</option>
                    <option value={100}>100</option>
                  </select>
                </label>
              </form>

              {tagError ? <div className="error-message">{tagError}</div> : null}

              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>类型</th>
                      <th>父级</th>
                      <th>编码</th>
                      <th>名称</th>
                      <th>说明</th>
                      <th>排序</th>
                    </tr>
                  </thead>
                  <tbody>
                    {tags.map((tag) => (
                      <tr key={tag.id}>
                        <td>{tag.id}</td>
                        <td>{tag.tagType === 'SCENE' ? '场景' : '功能'}</td>
                        <td>{tag.parentId ?? '-'}</td>
                        <td className="code-cell">{tag.code}</td>
                        <td>{tag.name}</td>
                        <td>{tag.description ?? '-'}</td>
                        <td>{tag.sortOrder}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                {!tagLoading && tags.length === 0 ? <p className="empty-state">暂无标签数据</p> : null}
              </div>

              <div className="pagination-bar">
                <span>
                  第 {page} / {totalPages} 页
                </span>
                <div className="pagination-actions">
                  <button
                    type="button"
                    disabled={page <= 1 || tagLoading}
                    onClick={() => setPage((value) => value - 1)}
                  >
                    上一页
                  </button>
                  <button
                    type="button"
                    disabled={page >= totalPages || tagLoading}
                    onClick={() => setPage((value) => value + 1)}
                  >
                    下一页
                  </button>
                </div>
              </div>
            </section>
          </section>
        ) : null}

        {activePage === 'questions' ? (
          <ManagementPlaceholder
            eyebrow="管理"
            title="问题管理"
            description="题目列表、启停、编辑和答案维护将在后端题目 API 完成后接入。"
            items={['题目列表', '启用 / 禁用', '编辑题干与标签', '维护标准答案']}
          />
        ) : null}

        {activePage === 'reviews' ? (
          <ManagementPlaceholder
            eyebrow="复习"
            title="错题复习"
            description="错题记录、复习计划和再练习流程将在答题评分链路完成后接入。"
            items={['错题列表', '复习计划', '再练习入口', '学习记录']}
          />
        ) : null}
      </section>
    </main>
  )
}

function StatusBadge({ label, value }: { label: string; value: string }) {
  return (
    <div className="status-badge">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求失败'
}

async function readApiResponse<T>(response: Response): Promise<ApiResponse<T>> {
  let result: ApiResponse<T> | null = null

  try {
    result = (await response.json()) as ApiResponse<T>
  } catch {
    throw new Error(response.ok ? '响应不是合法 JSON' : `HTTP ${response.status}`)
  }

  if (!response.ok || result.code !== 0) {
    throw new Error(result.message || `HTTP ${response.status}`)
  }

  return result
}

function StatusBlock({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span className="label">{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function ManagementPlaceholder({
  eyebrow,
  title,
  description,
  items,
}: {
  eyebrow: string
  title: string
  description: string
  items: string[]
}) {
  return (
    <section className="page-content" aria-label={title}>
      <header className="page-heading">
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h2>{title}</h2>
        </div>
      </header>

      <section className="surface placeholder-panel">
        <div>
          <span className="label">当前状态</span>
          <strong>入口已预留，功能待接入</strong>
          <p>{description}</p>
        </div>

        <div className="capability-list" aria-label={`${title} planned capabilities`}>
          {items.map((item) => (
            <span key={item}>{item}</span>
          ))}
        </div>
      </section>
    </section>
  )
}

export default App
