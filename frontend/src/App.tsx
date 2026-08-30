import { useState } from 'react'
import AnswerRecordsPage from './pages/AnswerRecordsPage'
import ErrorTypeManagementPage from './pages/ErrorTypeManagementPage'
import LearningStatisticsPage from './pages/LearningStatisticsPage'
import PracticePage from './pages/PracticePage'
import QuestionManagementPage from './pages/QuestionManagementPage'
import ReviewPage from './pages/ReviewPage'
import TagManagementPage from './pages/TagManagementPage'
import './App.css'
import { useLanguage } from './i18n/LanguageContext'

type PageKey = 'practice' | 'answerRecords' | 'statistics' | 'tags' | 'questions' | 'errorTypes' | 'reviews'

function App() {
  const { english, learningMode, setLearningMode, t } = useLanguage()
  const [activePage, setActivePage] = useState<PageKey>('practice')
  const navItems: { key: PageKey; label: string }[] = [
    { key: 'practice', label: t('practice') },
    { key: 'reviews', label: t('reviewCards') },
    { key: 'answerRecords', label: t('answerHistory') },
    { key: 'questions', label: t('questionManagement') },
    { key: 'statistics', label: t('learningAnalytics') },
  ]

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-workspace">
        {english ? 'Skip to main content' : '跳到主要内容'}
      </a>
      <header className="app-header">
        <div className="app-header-inner">
          <div className="title-group">
            <button type="button" className="brand-link" onClick={() => setActivePage('practice')} aria-label={t('backToPractice')}>
              <span className="brand-logo-image" aria-hidden="true" />
              <span className="brand-copy">
                <h1>{t('appTitle')}</h1>
                <span className="brand-tagline">{t('appTagline')}</span>
              </span>
            </button>
          </div>

          <div className="header-actions">
            <label className="language-select">
              <span className="sr-only">{t('learningMode')}</span>
              <select
                value={learningMode}
                aria-label={t('learningMode')}
                onChange={(event) => setLearningMode(event.target.value === 'EN_TO_JA' ? 'EN_TO_JA' : 'ZH_TO_JA')}
              >
                <option value="ZH_TO_JA">中 → 日</option>
                <option value="EN_TO_JA">EN → JP</option>
              </select>
              <svg viewBox="0 0 16 16" aria-hidden="true">
                <path d="m4 6 4 4 4-4" />
              </svg>
            </label>

            <nav className="top-nav" aria-label={t('mainNavigation')}>
              {navItems.map((item) => (
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
          </div>
        </div>
      </header>

      <main id="main-workspace" className="workspace" tabIndex={-1}>
        <div hidden={activePage !== 'practice'}>
          <PracticePage key={learningMode} />
        </div>
        {activePage === 'answerRecords' ? <AnswerRecordsPage key={learningMode} /> : null}
        {activePage === 'statistics' ? <LearningStatisticsPage key={learningMode} /> : null}
        {activePage === 'tags' ? <TagManagementPage key={learningMode} /> : null}
        {activePage === 'questions' ? <QuestionManagementPage key={learningMode} /> : null}
        {activePage === 'errorTypes' ? <ErrorTypeManagementPage key={learningMode} /> : null}
        {activePage === 'reviews' ? <ReviewPage key={learningMode} /> : null}
      </main>
    </div>
  )
}

export default App
