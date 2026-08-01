import { useEffect, useState } from 'react'
import { fetchHealth } from './api/client'
import StatusBadge from './components/StatusBadge'
import PracticePage from './pages/PracticePage'
import QuestionManagementPage from './pages/QuestionManagementPage'
import ReviewPage from './pages/ReviewPage'
import TagManagementPage from './pages/TagManagementPage'
import type { HealthResponse } from './types/api'
import './App.css'

type PageKey = 'practice' | 'tags' | 'questions' | 'reviews'

const NAV_ITEMS: { key: PageKey; label: string }[] = [
  { key: 'practice', label: '练习' },
  { key: 'tags', label: '标签管理' },
  { key: 'questions', label: '问题管理' },
  { key: 'reviews', label: '错题复习' },
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
      <section className="workspace">
        <header className="app-header">
          <div className="title-group">
            <div className="title-row">
              <h1>日语翻译练习</h1>
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
        </header>

        {activePage === 'practice' ? <PracticePage /> : null}
        {activePage === 'tags' ? <TagManagementPage /> : null}
        {activePage === 'questions' ? <QuestionManagementPage /> : null}
        {activePage === 'reviews' ? <ReviewPage /> : null}
      </section>
    </main>
  )
}

export default App
