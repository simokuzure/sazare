import { useEffect, useState } from 'react'
import { fetchHealth } from './api/client'
import AnswerRecordsPage from './pages/AnswerRecordsPage'
import ErrorTypeManagementPage from './pages/ErrorTypeManagementPage'
import LearningStatisticsPage from './pages/LearningStatisticsPage'
import StatusBadge from './components/StatusBadge'
import PracticePage from './pages/PracticePage'
import QuestionManagementPage from './pages/QuestionManagementPage'
import ReviewPage from './pages/ReviewPage'
import TagManagementPage from './pages/TagManagementPage'
import type { HealthResponse } from './types/api'
import './App.css'
import { useLanguage } from './i18n/LanguageContext'

type PageKey = 'practice' | 'answerRecords' | 'statistics' | 'tags' | 'questions' | 'errorTypes' | 'reviews'

function App() {
  const { english, learningMode, setLearningMode, t } = useLanguage()
  const [activePage, setActivePage] = useState<PageKey>('practice')
  const [health, setHealth] = useState<HealthResponse | null>(null)

  useEffect(() => {
    fetchHealth()
      .then((data) => {
        setHealth(data)
      })
      .catch(() => {
        setHealth(null)
      })
  }, [])

  const backendStatus = health?.data?.status ?? 'UNKNOWN'
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
            <div className="brand-row">
              <button type="button" className="brand-link" onClick={() => setActivePage('practice')} aria-label={t('backToPractice')}>
                <span className="brand-mark" aria-hidden="true">訳</span>
                <h1>{t('appTitle')}</h1>
              </button>
              <StatusBadge label={t('backend')} value={backendStatus} />
              <div className="language-switch" role="group" aria-label={t('learningMode')}>
                <button type="button" title="中译日" aria-pressed={!english} className={!english ? 'is-active' : ''} onClick={() => setLearningMode('ZH_TO_JA')}>中→日</button>
                <button type="button" title="English to Japanese" aria-pressed={english} className={english ? 'is-active' : ''} onClick={() => setLearningMode('EN_TO_JA')}>EN→JA</button>
              </div>
            </div>
          </div>

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
