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

type PageKey = 'practice' | 'answerRecords' | 'statistics' | 'tags' | 'questions' | 'errorTypes' | 'reviews'

const NAV_ITEMS: { key: PageKey; label: string }[] = [
  { key: 'practice', label: '练习' },
  { key: 'reviews', label: '复习卡片' },
  { key: 'answerRecords', label: '答题记录' },
  { key: 'questions', label: '问题管理' },
  { key: 'statistics', label: '学习分析' },
]

function App() {
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

  return (
    <main className="app-shell">
      <header className="app-header">
        <div className="app-header-inner">
          <div className="title-group">
            <div className="brand-row">
              <button type="button" className="brand-link" onClick={() => setActivePage('practice')} aria-label="返回练习首页">
                <span className="brand-mark" aria-hidden="true">訳</span>
                <h1>日语翻译练习</h1>
              </button>
              <StatusBadge label="后端服务" value={backendStatus} />
            </div>
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
        </div>
      </header>

      <section className="workspace">
        <div hidden={activePage !== 'practice'}>
          <PracticePage />
        </div>
        {activePage === 'answerRecords' ? <AnswerRecordsPage /> : null}
        {activePage === 'statistics' ? <LearningStatisticsPage /> : null}
        {activePage === 'tags' ? <TagManagementPage /> : null}
        {activePage === 'questions' ? <QuestionManagementPage /> : null}
        {activePage === 'errorTypes' ? <ErrorTypeManagementPage /> : null}
        {activePage === 'reviews' ? <ReviewPage /> : null}
      </section>
    </main>
  )
}

export default App
