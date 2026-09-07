import { Component, type ErrorInfo, type ReactNode } from 'react'
import { useLanguage } from '../i18n/LanguageContext'

type ErrorBoundaryProps = {
  children: ReactNode
  scope: 'app' | 'module'
  title: string
  message: string
  reloadLabel: string
}

type ErrorBoundaryState = {
  failed: boolean
}

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { failed: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { failed: true }
  }

  componentDidCatch(_error: Error, _info: ErrorInfo) {
    // 仅展示错误提示，避免记录可能包含敏感数据的运行时详情。
  }

  render() {
    if (this.state.failed) {
      if (this.props.scope === 'module') {
        return (
          <section className="surface" role="alert">
            <h2>{this.props.title}</h2>
            <p>{this.props.message}</p>
          </section>
        )
      }
      return (
        <main className="workspace" aria-labelledby="app-error-title">
          <section className="surface">
            <h1 id="app-error-title">{this.props.title}</h1>
            <p>{this.props.message}</p>
            <button type="button" className="primary-button" onClick={() => window.location.reload()}>
              {this.props.reloadLabel}
            </button>
          </section>
        </main>
      )
    }

    return this.props.children
  }
}

export default function AppErrorBoundary({ children, scope = 'app' }: { children: ReactNode; scope?: 'app' | 'module' }) {
  const { text } = useLanguage()
  return (
    <ErrorBoundary
      scope={scope}
      title={scope === 'module' ? text('当前模块无法显示', 'This module is unavailable') : text('页面出现异常', 'Something went wrong')}
      message={scope === 'module'
        ? text('可以切换到其他页面或练习。若需刷新重试，请先保存未提交的答案，刷新会清空当前练习状态。', 'You can switch to another page or practice. Before reloading to try again, save any unsubmitted answers; reloading clears the current practice state.')
        : text('请刷新页面后重试。', 'Reload the page and try again.')}
      reloadLabel={text('刷新页面', 'Reload')}
    >
      {children}
    </ErrorBoundary>
  )
}
