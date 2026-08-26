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
import { useLanguage } from '../i18n/LanguageContext'

const RANGE_OPTIONS: Array<{ value: LearningStatisticsRange; label: string }> = [
  { value: 'LAST_7_DAYS', label: '近 7 天' },
  { value: 'LAST_30_DAYS', label: '近 30 天' },
  { value: 'LAST_90_DAYS', label: '近 90 天' },
  { value: 'CUSTOM', label: '自定义' },
]

const REVIEW_COLORS = ['#dc2626', '#2563eb', '#16a34a']

export default function LearningStatisticsPage() {
  const { learningMode, text } = useLanguage()
  const [filters, setFilters] = useState<LearningStatisticsFilters>({ learningMode, range: 'LAST_30_DAYS' })
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
      { name: text('语法词汇', 'Grammar'), score: statistics.scoreDimensions.grammarVocabularyScore },
      { name: text('自然流畅', 'Fluency'), score: statistics.scoreDimensions.naturalFluencyScore },
      { name: text('场景适配', 'Context'), score: statistics.scoreDimensions.scenarioAdaptationScore },
      { name: text('信息完整', 'Completeness'), score: statistics.scoreDimensions.informationCompletenessScore },
    ]
  }, [statistics, text])

  const correctionScoreDimensions = useMemo(() => {
    if (!statistics) return []
    return [
      { name: text('语法词汇', 'Grammar'), score: statistics.correctionScoreDimensions.grammarVocabularyScore },
      { name: text('自然连贯', 'Coherence'), score: statistics.correctionScoreDimensions.naturalFluencyScore },
      { name: text('语体一致', 'Register'), score: statistics.correctionScoreDimensions.scenarioAdaptationScore },
      { name: text('表记完整', 'Writing'), score: statistics.correctionScoreDimensions.informationCompletenessScore },
    ]
  }, [statistics, text])

  const reviewStates = useMemo(() => {
    if (!statistics) return []
    return [
      { name: text('待复习', 'Due'), value: statistics.reviewOverview.dueCardCount },
      { name: text('进行中', 'Active'), value: statistics.reviewOverview.activeCardCount - statistics.reviewOverview.dueCardCount },
      { name: text('已掌握', 'Mastered'), value: statistics.reviewOverview.masteredCardCount },
    ].filter((item) => item.value > 0)
  }, [statistics, text])

  function selectRange(range: LearningStatisticsRange) {
    if (range === 'CUSTOM') {
      const today = getTokyoToday()
      const startDate = customStartDate || today
      const endDate = customEndDate || today
      setCustomStartDate(startDate)
      setCustomEndDate(endDate)
      setFilters({ learningMode, range, startDate, endDate })
      return
    }
    setFilters({ learningMode, range })
  }

  function applyCustomRange() {
    setFilters({ learningMode, range: 'CUSTOM', startDate: customStartDate, endDate: customEndDate })
  }

  return (
    <section className="page-content learning-statistics-page" aria-label={text('学习分析', 'Learning analytics')}>
      <section className="surface learning-statistics-toolbar">
        <div className="section-title">
          <span className="label">{text('学习记录分析', 'Learning progress')}</span>
          <strong>{text('学习概览', 'Progress overview')}</strong>
        </div>
        <div className="statistics-filter-controls">
          <div className="statistics-range-buttons" role="group" aria-label={text('统计范围', 'Statistics range')}>
            {RANGE_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                className={filters.range === option.value ? 'is-active' : undefined}
                onClick={() => selectRange(option.value)}
              >
                {text(option.label, option.value === 'LAST_7_DAYS' ? 'Last 7 days' : option.value === 'LAST_30_DAYS' ? 'Last 30 days' : option.value === 'LAST_90_DAYS' ? 'Last 90 days' : 'Custom')}
              </button>
            ))}
          </div>
          {filters.range === 'CUSTOM' ? (
            <div className="statistics-custom-range">
              <label><span>{text('开始日期', 'Start date')}</span><input type="date" value={customStartDate} onChange={(event) => setCustomStartDate(event.target.value)} /></label>
              <label><span>{text('结束日期', 'End date')}</span><input type="date" value={customEndDate} onChange={(event) => setCustomEndDate(event.target.value)} /></label>
              <button type="button" onClick={applyCustomRange} disabled={!customStartDate || !customEndDate}>{text('应用', 'Apply')}</button>
            </div>
          ) : null}
        </div>
      </section>

      {loading && !statistics ? <section className="surface"><p className="loading-text">{text('正在加载学习统计...', 'Loading learning statistics...')}</p></section> : null}
      {error ? <section className="surface"><div className="notice is-error"><strong>{text('学习统计加载失败', 'Could not load learning statistics')}</strong><p>{error}</p><button type="button" onClick={() => setReloadToken((value) => value + 1)}>{text('重新加载', 'Reload')}</button></div></section> : null}

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
  const { text } = useLanguage()
  return <>
    <section className="statistics-overview-grid" aria-label={text('学习概览指标', 'Learning overview metrics')}>
      <MetricCard label={text('翻译作答次数', 'Translation attempts')} value={String(statistics.overview.answerCount)} detail={`${statistics.period.startDate} – ${statistics.period.endDate}`} />
      <MetricCard label={text('翻译已评测', 'Scored translations')} value={String(statistics.overview.reviewedAnswerCount)} detail={text('短句与文章翻译', 'Sentence and article translations')} />
      <MetricCard label={text('翻译平均总分', 'Average translation score')} value={formatScore(statistics.overview.averageTotalScore)} detail={text('已评测翻译作答的平均值', 'Average across scored translations')} />
      <MetricCard label={text('已记录内容', 'Saved review items')} value={String(statistics.overview.confirmedErrorCount)} detail={text('已加入复习卡片的内容记录数', 'Items added to review cards')} />
    </section>

    <section className="statistics-chart-grid">
      <ChartSurface title={text('翻译作答与评分趋势', 'Translation attempts and score trend')} description={text('短句与文章翻译', 'Sentence and article translations')}>
        <ResponsiveContainer width="100%" height={280}>
          <ComposedChart data={statistics.dailyTrends} margin={{ top: 8, right: 20, left: -8, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="date" tickFormatter={formatShortDate} minTickGap={24} />
            <YAxis yAxisId="count" allowDecimals={false} />
            <YAxis yAxisId="score" orientation="right" domain={[0, 100]} />
            <Tooltip labelFormatter={formatDateLabel} />
            <Legend />
            <Bar yAxisId="count" dataKey="answerCount" name={text('作答数', 'Attempts')} fill="#2563eb" radius={[3, 3, 0, 0]} />
            <Line yAxisId="score" type="monotone" dataKey="averageTotalScore" name={text('平均总分', 'Average score')} stroke="#16a34a" strokeWidth={2} connectNulls dot={false} />
          </ComposedChart>
        </ResponsiveContainer>
      </ChartSurface>

      <ChartSurface title={text('翻译四项能力', 'Translation skill dimensions')} description={text('已评测翻译作答的各项平均分', 'Average scores across evaluated translations')}>
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={scoreDimensions} margin={{ top: 8, right: 12, left: -14, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="name" />
            <YAxis domain={[0, 100]} />
            <Tooltip formatter={(value) => value == null ? '-' : Number(value).toFixed(2)} />
            <Bar dataKey="score" name={text('平均分', 'Average score')} fill="#7c3aed" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </ChartSurface>
    </section>

    <section className="statistics-overview-grid" aria-label={text('日语纠错概览指标', 'Japanese correction overview metrics')}>
      <MetricCard label={text('纠错次数', 'Correction attempts')} value={String(statistics.correctionOverview.answerCount)} detail={text(`${statistics.period.startDate} 至 ${statistics.period.endDate}`, `${statistics.period.startDate} to ${statistics.period.endDate}`)} />
      <MetricCard label={text('纠错已评测', 'Scored corrections')} value={String(statistics.correctionOverview.reviewedAnswerCount)} detail={text('成功保存的日语纠错记录', 'Successfully saved Japanese correction records')} />
      <MetricCard label={text('纠错平均总分', 'Average correction score')} value={formatScore(statistics.correctionOverview.averageTotalScore)} detail={text('不与翻译分数混合', 'Kept separate from translation scores')} />
    </section>

    <section className="statistics-chart-grid">
      <ChartSurface title={text('日语纠错趋势', 'Japanese correction trend')} description={text('纠错次数与平均总分', 'Attempts and average score')}>
        <ResponsiveContainer width="100%" height={280}>
          <ComposedChart data={statistics.correctionDailyTrends} margin={{ top: 8, right: 20, left: -8, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="date" tickFormatter={formatShortDate} minTickGap={24} />
            <YAxis yAxisId="count" allowDecimals={false} />
            <YAxis yAxisId="score" orientation="right" domain={[0, 100]} />
            <Tooltip labelFormatter={formatDateLabel} />
            <Legend />
            <Bar yAxisId="count" dataKey="answerCount" name={text('纠错数', 'Corrections')} fill="#0f766e" radius={[3, 3, 0, 0]} />
            <Line yAxisId="score" type="monotone" dataKey="averageTotalScore" name={text('平均总分', 'Average score')} stroke="#d97706" strokeWidth={2} connectNulls dot={false} />
          </ComposedChart>
        </ResponsiveContainer>
      </ChartSurface>

      <ChartSurface title={text('纠错四项能力', 'Correction skill dimensions')} description={text('仅统计纯日语纠错记录', 'Japanese correction records only')}>
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={correctionScoreDimensions} margin={{ top: 8, right: 12, left: -14, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="name" />
            <YAxis domain={[0, 100]} />
            <Tooltip formatter={(value) => value == null ? '-' : Number(value).toFixed(2)} />
            <Bar dataKey="score" name={text('平均分', 'Average score')} fill="#0f766e" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </ChartSurface>
    </section>

    <section className="statistics-chart-grid">
      <ChartSurface title={text('复习重点 Top 10', 'Top 10 review focuses')} description={text('按加入复习卡片的次数排序', 'Ranked by times added to review cards')}>
        {statistics.weaknesses.length > 0 ? <ResponsiveContainer width="100%" height={Math.max(240, statistics.weaknesses.length * 44)}>
          <BarChart data={[...statistics.weaknesses].reverse()} layout="vertical" margin={{ top: 8, right: 22, left: 18, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis type="number" allowDecimals={false} />
            <YAxis type="category" dataKey="userErrorTypeName" width={126} tick={{ fontSize: 12 }} />
            <Tooltip />
            <Bar dataKey="confirmedCount" name={text('记录次数', 'Times recorded')} fill="#ea580c" radius={[0, 3, 3, 0]} />
          </BarChart>
        </ResponsiveContainer> : <EmptyChart text={text('所选期间内暂无复习卡片内容。', 'No review-card items in the selected period.')} />}
      </ChartSurface>

      <ChartSurface title={text('复习状态', 'Review status')} description={text('卡片状态为当前实时状态', 'Current real-time card status')}>
        {reviewStates.length > 0 ? <div className="review-state-chart"><ResponsiveContainer width="100%" height={220}>
          <PieChart>
            <Tooltip />
            <Pie data={reviewStates} dataKey="value" nameKey="name" innerRadius={54} outerRadius={86} paddingAngle={2}>
              {reviewStates.map((item, index) => <Cell key={item.name} fill={REVIEW_COLORS[index]} />)}
            </Pie>
            <Legend />
          </PieChart>
        </ResponsiveContainer></div> : <EmptyChart text={text('当前暂无复习卡片。', 'No review cards yet.')} />}
        <dl className="review-period-summary">
          <div><dt>{text('期间复习', 'Reviews in period')}</dt><dd>{text(`${statistics.reviewOverview.periodReviewAttemptCount} 次`, String(statistics.reviewOverview.periodReviewAttemptCount))}</dd></div>
          <div><dt>{text('期间通过率', 'Pass rate')}</dt><dd>{formatScore(statistics.reviewOverview.periodReviewPassRate, '%')}</dd></div>
          <div><dt>{text('完成周期', 'Completed cycles')}</dt><dd>{statistics.reviewOverview.periodCompletedCycleCount}</dd></div>
        </dl>
      </ChartSurface>
    </section>

    <section className="surface statistics-weakness-detail">
      <div className="section-title"><span className="label">{text('已记录内容', 'Saved items')}</span><strong>{text('复习重点明细', 'Review focus details')}</strong></div>
      {statistics.weaknesses.length === 0 ? <p className="empty-state">{text('所选期间内暂无复习卡片内容。', 'No review-card items in the selected period.')}</p> : <div className="table-wrap"><table><thead><tr><th>{text('复习重点', 'Review focus')}</th><th>{text('系统分类', 'System category')}</th><th>{text('记录次数', 'Times recorded')}</th><th>{text('严重度', 'Severity')}</th><th>{text('当前复习状态', 'Current status')}</th><th>{text('最近记录', 'Last recorded')}</th></tr></thead><tbody>{statistics.weaknesses.map((item) => <tr key={item.userErrorTypeId}><td><strong>{item.userErrorTypeName}</strong>{item.userErrorTypeStatus === 'ARCHIVED' ? <span className="statistics-muted">{text('已归档', 'Archived')}</span> : null}</td><td>{item.errorTypeName}</td><td>{item.confirmedCount}</td><td>{text(`低 ${item.lowSeverityCount} / 中 ${item.mediumSeverityCount} / 高 ${item.highSeverityCount}`, `Low ${item.lowSeverityCount} / Medium ${item.mediumSeverityCount} / High ${item.highSeverityCount}`)}</td><td>{reviewStateLabel(item.reviewState, text)}</td><td>{formatDateTime(item.lastConfirmedAt)}</td></tr>)}</tbody></table></div>}
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

function reviewStateLabel(value: LearningStatistics['weaknesses'][number]['reviewState'], text: (zh: string, en: string) => string) {
  if (value === 'DUE') return text('待复习', 'Due')
  if (value === 'ACTIVE') return text('进行中', 'Active')
  if (value === 'MASTERED') return text('已掌握', 'Mastered')
  return text('未创建', 'Not created')
}
