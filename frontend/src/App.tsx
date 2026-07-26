import { useEffect, useState } from 'react'
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

function App() {
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

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
        setError(null)
      })
      .catch((fetchError: unknown) => {
        setError(fetchError instanceof Error ? fetchError.message : '请求失败')
      })
  }, [])

  const backendStatus = health?.data?.status ?? 'UNKNOWN'

  return (
    <main className="app-shell">
      <section className="workspace">
        <div className="title-group">
          <p className="eyebrow">Japanese Translation Practice</p>
          <h1>日语翻译练习</h1>
          <p className="description">
            当前阶段先完成项目骨架、环境连接和接口格式校验，业务表与 AI 接入后续再细化。
          </p>
        </div>

        <div className="status-panel">
          <div>
            <span className="label">后端服务</span>
            <strong>{backendStatus}</strong>
          </div>
          <div>
            <span className="label">响应状态</span>
            <strong>{error ?? health?.message ?? '等待响应'}</strong>
          </div>
        </div>

        <section className="practice-panel" aria-label="translation practice placeholder">
          <div>
            <span className="label">MVP 流程</span>
            <h2>生成题目、提交答案、查看评分</h2>
          </div>
          <textarea placeholder="这里后续用于输入日语答案" disabled />
          <button type="button" disabled>
            提交答案
          </button>
        </section>
      </section>
    </main>
  )
}

export default App
