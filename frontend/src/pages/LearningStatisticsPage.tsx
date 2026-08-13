import { useEffect, useMemo, useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { fetchLearningStatistics } from '../api/learningStatisticsApi'
import { getErrorMessage } from '../api/client'
import type { LearningStatistics, LearningStatisticsFilters, LearningStatisticsRange } from '../types/learningStatistics'

const RANGE_OPTIONS: Array<{ value: LearningStatisticsRange; label: string }> = [
  { value: 'LAST_7_DAYS', label: '近 7 天' },
  { value: 'LAST_30_DAYS', label: '近 30 天' },
  { value: 'LAST_90_DAYS', label: '近 90 天' },
  { value: 'CUSTOM', label: '自定义' },
]

const REVIEW_COLORS = ['#dc2626', '#2563eb', '#16a34a']

export default function LearningStatisticsPage() {
  const [filters, setFilters] = useState<LearningStatisticsFilters>({ range: 'LAST_30_DAYS' })
  const [customStartDate, setCustomStartDate] = useState('')
  const [customEndDate, setCustomEndDate] = useState('')
  const [statistics, setStatistics] = useState<LearningStatistics | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    fetchLearningStatistics(filters, controller.signal)
      .then((data) => setStatistics(data))
      .catch((requestError: unknown) => {
        if (!(requestError instanceof DOMException && requestError.name === 'AbortError')) {
          setError(getErrorMessage(requestError))
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false)
        }
      })
    return () => controller.abort()
  }, [filters, reloadToken])

  const scoreDimensions = useMemo(() => {
    if (!statistics) return []
    return [
      { name: '语法词汇', score: statistics.scoreDimensions.grammarVocabularyScore },
      { name: '自然流畅', score: statistics.scoreDimensions.naturalFluencyScore },
      { name: '场景适配', score: statistics.scoreDimensions.scenarioAdaptationScore },
      { name: '信息完整', score: statistics.scoreDimensions.informationCompletenessScore },
    ]
  }, [statistics])

  const correctionScoreDimensions = useMemo(() => {
    if (!statistics) return []
    return [
      { name: '语法词汇', score: statistics.correctionScoreDimensions.grammarVocabularyScore },
      { name: '自然连贯', score: statistics.correctionScoreDimensions.naturalFluencyScore },
      { name: '语体一致', score: statistics.correctionScoreDimensions.scenarioAdaptationScore },
      { name: '表记完整', score: statistics.correctionScoreDimensions.informationCompletenessScore },
    ]
  }, [statistics])

  const reviewStates = useMemo(() => {
    if (!statistics) return []
    return [
      { name: '待复习', value: statistics.reviewOverview.dueCardCount },
      { name: '进行中', value: statistics.reviewOverview.activeCardCount - statistics.reviewOverview.dueCardCount },
      { name: '已掌握', value: statistics.reviewOverview.masteredCardCount },
    ].filter((item) => item.value > 0)
  }, [statistics])

  function selectRange(range: LearningStatisticsRange) {
    if (range === 'CUSTOM') {
      const today = getTokyoToday()
      const startDate = customStartDate || today
      const endDate = customEndDate || today
      setCustomStartDate(startDate)
      setCustomEndDate(endDate)
      setFilters({ range, startDate, endDate })
      return
    }
    setFilters({ range })
  }

  function applyCustomRange() {
    setFilters({ range: 'CUSTOM', startDate: customStartDate, endDate: customEndDate })
  }

  return (
    <section className="page-content learning-statistics-page" aria-label="学习分析">
      <section className="surface learning-statistics-toolbar">
        <div className="section-title">
          <span className="label">学习记录分析</span>
          <strong>学习概览</strong>
        </div>
        <div className="statistics-filter-controls">
          <div className="statistics-range-buttons" role="group" aria-label="统计范围">
            {RANGE_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                className={filters.range === option.value ? 'is-active' : undefined}
                onClick={() => selectRange(option.value)}
              >
                {option.label}
              </button>
            ))}
          </div>
          {filters.range === 'CUSTOM' ? (
            <div className="statistics-custom-range">
              <label><span>开始日期</span><input type="date" value={customStartDate} onChange={(event) => setCustomStartDate(event.target.value)} /></label>
              <label><span>结束日期</span><input type="date" value={customEndDate} onChange={(event) => setCustomEndDate(event.target.value)} /></label>
              <button type="button" onClick={applyCustomRange} disabled={!customStartDate || !customEndDate}>应用</button>
            </div>
          ) : null}
        </div>
      </section>

      {loading && !statistics ? <section className="surface"><p className="loading-text">正在加载学习统计...</p></section> : null}
      {error ? <section className="surface"><div className="notice is-error"><strong>学习统计加载失败</strong><p>{error}</p><button type="button" onClick={() => setReloadToken((value) => value + 1)}>重新加载</button></div></section> : null}

      {statistics ? <StatisticsContent statistics={statistics} scoreDimensions={scoreDimensions} correctionScoreDimensions={correctionScoreDimensions} reviewStates={reviewStates} /> : null}
    </section>
  )
}

function StatisticsContent({ statistics, scoreDimensions, correctionScoreDimensions, reviewStates }: {
  statistics: LearningStatistics
  scoreDimensions: Array<{ name: string; score: number | null }>
  correctionScoreDimensions: Array<{ name: string; score: number | null }>
  reviewStates: Array<{ name: string; value: number }>
}) {
  return <>
    <section className="statistics-overview-grid" aria-label="学习概览指标">
      <MetricCard label="翻译作答次数" value={String(statistics.overview.answerCount)} detail={`${statistics.period.startDate} 至 ${statistics.period.endDate}`} />
      <MetricCard label="翻译已评测" value={String(statistics.overview.reviewedAnswerCount)} detail="短句与文章翻译" />
      <MetricCard label="翻译平均总分" value={formatScore(statistics.overview.averageTotalScore)} detail="已评测翻译作答的平均值" />
      <MetricCard label="已确认错误" value={String(statistics.overview.confirmedErrorCount)} detail="未确认 AI 候选不计入" />
    </section>

    <section className="statistics-chart-grid">
      <ChartSurface title="翻译作答与评分趋势" description="短句与文章翻译">
        <ResponsiveContainer width="100%" height={280}>
          <ComposedChart data={statistics.dailyTrends} margin={{ top: 8, right: 20, left: -8, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="date" tickFormatter={formatShortDate} minTickGap={24} />
            <YAxis yAxisId="count" allowDecimals={false} />
            <YAxis yAxisId="score" orientation="right" domain={[0, 100]} />
            <Tooltip labelFormatter={formatDateLabel} />
            <Legend />
            <Bar yAxisId="count" dataKey="answerCount" name="作答数" fill="#2563eb" radius={[3, 3, 0, 0]} />
            <Line yAxisId="score" type="monotone" dataKey="averageTotalScore" name="平均总分" stroke="#16a34a" strokeWidth={2} connectNulls dot={false} />
          </ComposedChart>
        </ResponsiveContainer>
      </ChartSurface>

      <ChartSurface title="翻译四项能力" description="已评测翻译作答的各项平均分">
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={scoreDimensions} margin={{ top: 8, right: 12, left: -14, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="name" />
            <YAxis domain={[0, 100]} />
            <Tooltip formatter={(value) => value == null ? '-' : Number(value).toFixed(2)} />
            <Bar dataKey="score" name="平均分" fill="#7c3aed" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </ChartSurface>
    </section>

    <section className="statistics-overview-grid" aria-label="日语纠错概览指标">
      <MetricCard label="纠错次数" value={String(statistics.correctionOverview.answerCount)} detail={`${statistics.period.startDate} 至 ${statistics.period.endDate}`} />
      <MetricCard label="纠错已评测" value={String(statistics.correctionOverview.reviewedAnswerCount)} detail="成功保存的日语纠错记录" />
      <MetricCard label="纠错平均总分" value={formatScore(statistics.correctionOverview.averageTotalScore)} detail="不与翻译分数混合" />
    </section>

    <section className="statistics-chart-grid">
      <ChartSurface title="日语纠错趋势" description="纠错次数与平均总分">
        <ResponsiveContainer width="100%" height={280}>
          <ComposedChart data={statistics.correctionDailyTrends} margin={{ top: 8, right: 20, left: -8, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="date" tickFormatter={formatShortDate} minTickGap={24} />
            <YAxis yAxisId="count" allowDecimals={false} />
            <YAxis yAxisId="score" orientation="right" domain={[0, 100]} />
            <Tooltip labelFormatter={formatDateLabel} />
            <Legend />
            <Bar yAxisId="count" dataKey="answerCount" name="纠错数" fill="#0f766e" radius={[3, 3, 0, 0]} />
            <Line yAxisId="score" type="monotone" dataKey="averageTotalScore" name="平均总分" stroke="#d97706" strokeWidth={2} connectNulls dot={false} />
          </ComposedChart>
        </ResponsiveContainer>
      </ChartSurface>

      <ChartSurface title="纠错四项能力" description="仅统计纯日语纠错记录">
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={correctionScoreDimensions} margin={{ top: 8, right: 12, left: -14, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="name" />
            <YAxis domain={[0, 100]} />
            <Tooltip formatter={(value) => value == null ? '-' : Number(value).toFixed(2)} />
            <Bar dataKey="score" name="平均分" fill="#0f766e" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </ChartSurface>
    </section>

    <section className="statistics-chart-grid">
      <ChartSurface title="薄弱项 Top 10" description="按已确认出现次数排序">
        {statistics.weaknesses.length > 0 ? <ResponsiveContainer width="100%" height={Math.max(240, statistics.weaknesses.length * 44)}>
          <BarChart data={[...statistics.weaknesses].reverse()} layout="vertical" margin={{ top: 8, right: 22, left: 18, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis type="number" allowDecimals={false} />
            <YAxis type="category" dataKey="userErrorTypeName" width={126} tick={{ fontSize: 12 }} />
            <Tooltip />
            <Bar dataKey="confirmedCount" name="确认次数" fill="#ea580c" radius={[0, 3, 3, 0]} />
          </BarChart>
        </ResponsiveContainer> : <EmptyChart text="所选期间内暂无已确认错误。" />}
      </ChartSurface>

      <ChartSurface title="复习状态" description="卡片状态为当前实时状态">
        {reviewStates.length > 0 ? <div className="review-state-chart"><ResponsiveContainer width="100%" height={220}>
          <PieChart>
            <Tooltip />
            <Pie data={reviewStates} dataKey="value" nameKey="name" innerRadius={54} outerRadius={86} paddingAngle={2}>
              {reviewStates.map((item, index) => <Cell key={item.name} fill={REVIEW_COLORS[index]} />)}
            </Pie>
            <Legend />
          </PieChart>
        </ResponsiveContainer></div> : <EmptyChart text="当前暂无复习卡片。" />}
        <dl className="review-period-summary">
          <div><dt>期间复习</dt><dd>{statistics.reviewOverview.periodReviewAttemptCount} 次</dd></div>
          <div><dt>期间通过率</dt><dd>{formatScore(statistics.reviewOverview.periodReviewPassRate, '%')}</dd></div>
          <div><dt>完成周期</dt><dd>{statistics.reviewOverview.periodCompletedCycleCount}</dd></div>
        </dl>
      </ChartSurface>
    </section>

    <section className="surface statistics-weakness-detail">
      <div className="section-title"><span className="label">已确认错误</span><strong>薄弱项明细</strong></div>
      {statistics.weaknesses.length === 0 ? <p className="empty-state">所选期间内暂无已确认错误。</p> : <div className="table-wrap"><table><thead><tr><th>用户错误类型</th><th>系统分类</th><th>确认次数</th><th>严重度</th><th>当前复习状态</th><th>最近确认</th></tr></thead><tbody>{statistics.weaknesses.map((item) => <tr key={item.userErrorTypeId}><td><strong>{item.userErrorTypeName}</strong>{item.userErrorTypeStatus === 'ARCHIVED' ? <span className="statistics-muted">已归档</span> : null}</td><td>{item.errorTypeName}</td><td>{item.confirmedCount}</td><td>低 {item.lowSeverityCount} / 中 {item.mediumSeverityCount} / 高 {item.highSeverityCount}</td><td>{reviewStateLabel(item.reviewState)}</td><td>{formatDateTime(item.lastConfirmedAt)}</td></tr>)}</tbody></table></div>}
    </section>
  </>
}

function MetricCard({ label, value, detail }: { label: string; value: string; detail: string }) {
  return <section className="surface statistics-metric-card"><span>{label}</span><strong>{value}</strong><small>{detail}</small></section>
}

function ChartSurface({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return <section className="surface statistics-chart-surface"><div className="section-title"><span className="label">{description}</span><strong>{title}</strong></div>{children}</section>
}

function EmptyChart({ text }: { text: string }) {
  return <p className="empty-state statistics-empty-chart">{text}</p>
}

function formatScore(value: number | null, suffix = '') {
  return value == null ? '-' : `${value.toFixed(2)}${suffix}`
}

function formatShortDate(value: string) {
  return value.slice(5)
}

function formatDateLabel(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function formatDateTime(value: string) {
  return value.replace('T', ' ').slice(0, 16)
}

function getTokyoToday() {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Tokyo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date())
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === type)?.value ?? ''
  return `${part('year')}-${part('month')}-${part('day')}`
}

function reviewStateLabel(value: LearningStatistics['weaknesses'][number]['reviewState']) {
  if (value === 'DUE') return '待复习'
  if (value === 'ACTIVE') return '进行中'
  if (value === 'MASTERED') return '已掌握'
  return '未创建'
}
