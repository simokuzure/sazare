import { lazy, Suspense, useState } from 'react'
import PageHeader from '../components/PageHeader'
import AppErrorBoundary from '../components/AppErrorBoundary'
import { useLanguage } from '../i18n/LanguageContext'
import ShortSentencePractice from './practice/ShortSentencePractice'

const ArticlePractice = lazy(() => import('./ArticlePractice'))
const JapaneseCorrectionPractice = lazy(() => import('./JapaneseCorrectionPractice'))

type PracticeMode = 'sentence' | 'article' | 'correction'

const PRACTICE_MODES: PracticeMode[] = ['sentence', 'article', 'correction']

export default function PracticePage() {
  const { text } = useLanguage()
  const [activeMode, setActiveMode] = useState<PracticeMode>('sentence')
  const [loadedModes, setLoadedModes] = useState<Set<PracticeMode>>(() => new Set(['sentence']))

  function activateMode(mode: PracticeMode) {
    setLoadedModes((current) => current.has(mode) ? current : new Set(current).add(mode))
    setActiveMode(mode)
  }

  function handleTabKeyDown(event: React.KeyboardEvent<HTMLButtonElement>, currentMode: PracticeMode) {
    const currentIndex = PRACTICE_MODES.indexOf(currentMode)
    let nextIndex = currentIndex
    if (event.key === 'ArrowRight') nextIndex = (currentIndex + 1) % PRACTICE_MODES.length
    else if (event.key === 'ArrowLeft') nextIndex = (currentIndex - 1 + PRACTICE_MODES.length) % PRACTICE_MODES.length
    else if (event.key === 'Home') nextIndex = 0
    else if (event.key === 'End') nextIndex = PRACTICE_MODES.length - 1
    else return

    event.preventDefault()
    const nextMode = PRACTICE_MODES[nextIndex]
    activateMode(nextMode)
    requestAnimationFrame(() => document.getElementById(`${nextMode}-practice-tab`)?.focus())
  }

  return (
    <section className="page-content" aria-label={text('练习', 'Practice')}>
      <PageHeader
        title={text('翻译练习', 'Translation practice')}
        description={text('选择练习模式，生成题目并提交作答，AI 会即时评分与纠错。', 'Choose a practice type, generate a question, and receive immediate AI feedback.')}
        actions={<div className="practice-tabs" role="tablist" aria-label={text('翻译练习类型', 'Practice type')}>
        <button
          id="sentence-practice-tab"
          type="button"
          role="tab"
          aria-selected={activeMode === 'sentence'}
          aria-controls="sentence-practice-panel"
          className={activeMode === 'sentence' ? 'is-active' : ''}
          tabIndex={activeMode === 'sentence' ? 0 : -1}
          onKeyDown={(event) => handleTabKeyDown(event, 'sentence')}
          onClick={() => activateMode('sentence')}
        >
          {text('短句翻译', 'Sentences')}
        </button>
        <button
          id="article-practice-tab"
          type="button"
          role="tab"
          aria-selected={activeMode === 'article'}
          aria-controls="article-practice-panel"
          className={activeMode === 'article' ? 'is-active' : ''}
          tabIndex={activeMode === 'article' ? 0 : -1}
          onKeyDown={(event) => handleTabKeyDown(event, 'article')}
          onClick={() => activateMode('article')}
        >
          {text('文章翻译', 'Articles')}
        </button>
        <button
          id="correction-practice-tab"
          type="button"
          role="tab"
          aria-selected={activeMode === 'correction'}
          aria-controls="correction-practice-panel"
          className={activeMode === 'correction' ? 'is-active' : ''}
          tabIndex={activeMode === 'correction' ? 0 : -1}
          onKeyDown={(event) => handleTabKeyDown(event, 'correction')}
          onClick={() => activateMode('correction')}
        >
          {text('日语纠错', 'Proofread')}
        </button>
      </div>}
      />

      <div
        id="sentence-practice-panel"
        role="tabpanel"
        aria-labelledby="sentence-practice-tab"
        hidden={activeMode !== 'sentence'}
      >
        <ShortSentencePractice />
      </div>
      <div id="article-practice-panel" role="tabpanel" aria-labelledby="article-practice-tab" hidden={activeMode !== 'article'}>
        <AppErrorBoundary scope="module">
          <Suspense fallback={<div className="surface" role="status">{text('练习模块加载中…', 'Loading practice…')}</div>}>
            {loadedModes.has('article') ? <ArticlePractice /> : null}
          </Suspense>
        </AppErrorBoundary>
      </div>
      <div id="correction-practice-panel" role="tabpanel" aria-labelledby="correction-practice-tab" hidden={activeMode !== 'correction'}>
        <AppErrorBoundary scope="module">
          <Suspense fallback={<div className="surface" role="status">{text('练习模块加载中…', 'Loading practice…')}</div>}>
            {loadedModes.has('correction') ? <JapaneseCorrectionPractice /> : null}
          </Suspense>
        </AppErrorBoundary>
      </div>
    </section>
  )
}
