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
import PageHeader from '../components/PageHeader'
import type {
  LearningStatistics,
  LearningStatisticsFilters,
  LearningStatisticsPractice,
  LearningStatisticsRange,
} from '../types/learningStatistics'
import { useLanguage } from '../i18n/LanguageContext'
import { scoreToneClassName } from '../utils/score'

const RANGE_OPTIONS: Array<{ value: LearningStatisticsRange; label: string }> = [
  { value: 'LAST_7_DAYS', label: '近 7 天' },
  { value: 'LAST_30_DAYS', label: '近 30 天' },
  { value: 'LAST_90_DAYS', label: '近 90 天' },
  { value: 'CUSTOM', label: '自定义' },
]

const REVIEW_COLORS = ['var(--warning)', 'var(--brand)', 'var(--success)']
type PracticeType = 'TRANSLATION' | 'CORRECTION'

export default function LearningStatisticsPage() {
  const { learningMode, text } = useLanguage()
  const [filters, setFilters] = useState<LearningStatisticsFilters>({ learningMode, range: 'LAST_30_DAYS' })
  const [customStartDate, setCustomStartDate] = useState('')
  const [customEndDate, setCustomEndDate] = useState('')
  const [statistics, setStatistics] = useState<LearningStatistics | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  const [practiceType, setPracticeType] = useState<PracticeType>('TRANSLATION')

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
    const dimensions = practiceType === 'TRANSLATION'
      ? statistics.translation.scoreDimensions
      : statistics.correction.scoreDimensions
    return [
      { name: text('语法词汇', 'Grammar'), score: dimensions.grammarVocabularyScore },
      {
        name: practiceType === 'TRANSLATION' ? text('自然流畅', 'Fluency') : text('自然连贯', 'Coherence'),
        score: dimensions.naturalFluencyScore,
      },
      {
        name: practiceType === 'TRANSLATION' ? text('场景适配', 'Context') : text('语体一致', 'Register'),
        score: dimensions.scenarioAdaptationScore,
      },
      {
        name: practiceType === 'TRANSLATION' ? text('信息完整', 'Completeness') : text('表记完整', 'Writing'),
        score: dimensions.informationCompletenessScore,
      },
    ]
  }, [practiceType, statistics, text])

  const reviewStates = useMemo(() => {
    if (!statistics) return []
    return [
      { name: text('待复习', 'Due'), value: statistics.reviewOverview.dueCardCount, color: REVIEW_COLORS[0] },
      { name: text('进行中', 'Active'), value: statistics.reviewOverview.inProgressCardCount, color: REVIEW_COLORS[1] },
      { name: text('已掌握', 'Mastered'), value: statistics.reviewOverview.masteredCardCount, color: REVIEW_COLORS[2] },
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
    <section
      className="page-content learning-statistics-page"
      aria-label={text('学习分析', 'Learning analytics')}
      aria-busy={loading}
    >
      <PageHeader
        title={text('学习分析', 'Learning analytics')}
        description={text('按时间范围查看练习表现、评分维度与复习进度。', 'Review practice performance, score dimensions, and review progress by date range.')}
        actions={<div className="statistics-range-buttons" role="group" aria-label={text('统计范围', 'Statistics range')}>
          {RANGE_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={filters.range === option.value ? 'is-active' : undefined}
              aria-pressed={filters.range === option.value}
              onClick={() => selectRange(option.value)}
            >
              {text(option.label, option.value === 'LAST_7_DAYS' ? 'Last 7 days' : option.value === 'LAST_30_DAYS' ? 'Last 30 days' : option.value === 'LAST_90_DAYS' ? 'Last 90 days' : 'Custom')}
            </button>
          ))}
        </div>}
      />
      {filters.range === 'CUSTOM' ? (
        <section className="statistics-custom-range-panel" aria-label={text('自定义统计范围', 'Custom statistics range')}>
          <div className="statistics-custom-range">
            <label><span>{text('开始日期', 'Start date')}</span><input type="date" value={customStartDate} onChange={(event) => setCustomStartDate(event.target.value)} /></label>
            <label><span>{text('结束日期', 'End date')}</span><input type="date" value={customEndDate} onChange={(event) => setCustomEndDate(event.target.value)} /></label>
            <button type="button" onClick={applyCustomRange} disabled={!customStartDate || !customEndDate}>{text('应用', 'Apply')}</button>
          </div>
        </section>
      ) : null}

      {loading && !statistics ? <StatisticsSkeleton /> : null}
      {error ? <section className="surface state-surface"><div className="notice is-error" role="alert"><strong>{text('学习统计加载失败', 'Could not load learning statistics')}</strong><p>{error}</p><button type="button" onClick={() => setReloadToken((value) => value + 1)}>{text('重新加载', 'Reload')}</button></div></section> : null}

      {statistics ? <StatisticsContent
        statistics={statistics}
        practiceType={practiceType}
        practice={practiceType === 'TRANSLATION' ? statistics.translation : statistics.correction}
        scoreDimensions={scoreDimensions}
        reviewStates={reviewStates}
        onPracticeTypeChange={setPracticeType}
      /> : null}
    </section>
  )
}

function StatisticsSkeleton() {
  const { text } = useLanguage()
  return (
    <section className="statistics-skeleton" role="status" aria-live="polite">
      <span className="sr-only">{text('正在加载学习统计...', 'Loading learning statistics...')}</span>
      <div className="surface statistics-kpi-strip" aria-hidden="true">
        {Array.from({ length: 4 }).map((_, index) => (
          <div className="statistics-metric-item statistics-skeleton-metric" key={index}>
            <span className="statistics-skeleton-block is-label" />
            <span className="statistics-skeleton-block is-value" />
            <span className="statistics-skeleton-block is-detail" />
          </div>
        ))}
      </div>
      <div className="statistics-dashboard-grid" aria-hidden="true">
        <div className="surface statistics-skeleton-panel is-trend"><div className="statistics-skeleton-panel-heading"><span className="statistics-skeleton-block is-panel-title" /><span className="statistics-skeleton-block is-control" /></div><span className="statistics-skeleton-block is-chart" /></div>
        <div className="statistics-side-stack">
          <div className="surface statistics-skeleton-panel"><span className="statistics-skeleton-block is-panel-title" /><span className="statistics-skeleton-block is-chart" /></div>
          <div className="surface statistics-skeleton-panel"><span className="statistics-skeleton-block is-panel-title" /><span className="statistics-skeleton-block is-chart" /></div>
        </div>
      </div>
    </section>
  )
}

function StatisticsContent({
  statistics,
  practiceType,
  practice,
  scoreDimensions,
  reviewStates,
  onPracticeTypeChange,
}: {
  statistics: LearningStatistics
  practiceType: PracticeType
  practice: LearningStatisticsPractice
  scoreDimensions: Array<{ name: string; score: number | null }>
  reviewStates: Array<{ name: string; value: number; color: string }>
  onPracticeTypeChange: (value: PracticeType) => void
}) {
  const { text } = useLanguage()
  const isTranslation = practiceType === 'TRANSLATION'
  const hasDailyTrends = practice.dailyTrends.some((item) => item.attemptCount > 0 || item.averageTotalScore != null)
  const hasScoreDimensions = scoreDimensions.some((item) => item.score != null)
  return <>
    <section className="surface statistics-kpi-strip" aria-label={text('学习与练习概览指标', 'Learning and practice overview metrics')}>
      <MetricItem
        label={text('连续打卡', 'Current streak')}
        value={text(`${statistics.checkInOverview.currentStreakDays} 天`, `${statistics.checkInOverview.currentStreakDays} days`)}
        detail={text('今天尚未学习时保留截至昨天的连续天数', 'Keeps yesterday’s streak until today ends')}
      />
      <MetricItem
        label={text('累计打卡', 'Total check-ins')}
        value={text(`${statistics.checkInOverview.totalCheckInDays} 天`, `${statistics.checkInOverview.totalCheckInDays} days`)}
        detail={text('全部学习模式，按东京自然日去重', 'All learning modes, deduplicated by Tokyo date')}
      />
      <MetricItem
        label={isTranslation ? text('翻译次数', 'Translation attempts') : text('纠错次数', 'Correction attempts')}
        value={String(practice.attemptCount)}
        detail={`${statistics.period.startDate} – ${statistics.period.endDate}`}
      />
      <MetricItem
        label={isTranslation ? text('翻译平均总分', 'Average translation score') : text('纠错平均总分', 'Average correction score')}
        value={formatScore(practice.averageTotalScore)}
        detail={isTranslation ? text('短句与文章翻译', 'Sentence and article translations') : text('仅统计纯日语纠错记录', 'Japanese correction records only')}
        score={practice.averageTotalScore}
      />
    </section>

    <section className="statistics-dashboard-section" aria-labelledby="practice-statistics-title">
      <section className="statistics-dashboard-grid">
        <ChartSurface
          className="statistics-trend-panel"
          titleId="practice-statistics-title"
          title={isTranslation ? text('翻译练习趋势', 'Translation trend') : text('日语纠错趋势', 'Japanese correction trend')}
          description={text('每日练习次数与平均总分', 'Daily attempts and average score')}
          actions={<div className="statistics-segmented-control" role="group" aria-label={text('练习类型', 'Practice type')}>
            <button type="button" className={isTranslation ? 'is-active' : undefined} aria-pressed={isTranslation} onClick={() => onPracticeTypeChange('TRANSLATION')}>{text('翻译', 'Translation')}</button>
            <button type="button" className={!isTranslation ? 'is-active' : undefined} aria-pressed={!isTranslation} onClick={() => onPracticeTypeChange('CORRECTION')}>{text('日语纠错', 'Japanese correction')}</button>
          </div>}
        >
          {hasDailyTrends ? <div className="statistics-chart statistics-trend-chart"><ResponsiveContainer width="100%" height="100%">
              <ComposedChart accessibilityLayer data={practice.dailyTrends} margin={{ top: 8, right: 12, left: -10, bottom: 0 }}>
                <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="date" tickFormatter={formatShortDate} minTickGap={24} tickLine={false} axisLine={false} />
                <YAxis yAxisId="count" allowDecimals={false} tickLine={false} axisLine={false} />
                <YAxis yAxisId="score" orientation="right" domain={[0, 100]} tickLine={false} axisLine={false} />
                <Tooltip
                  labelFormatter={formatDateLabel}
                  formatter={(value, name) => String(name) === text('平均总分', 'Average score') ? <ScoreTooltipValue value={value} /> : value}
                />
                <Legend />
                <Bar yAxisId="count" dataKey="attemptCount" name={text('练习次数', 'Attempts')} fill={isTranslation ? 'var(--brand)' : 'var(--success)'} radius={[3, 3, 0, 0]} />
                <Line yAxisId="score" type="monotone" dataKey="averageTotalScore" name={text('平均总分', 'Average score')} stroke="var(--warning)" strokeWidth={2.5} connectNulls dot={{ r: 3, fill: 'var(--card)', strokeWidth: 2 }} activeDot={{ r: 5 }} />
              </ComposedChart>
            </ResponsiveContainer></div> : <EmptyChart text={text('所选期间暂无练习数据。', 'No practice data for this period.')} />}
          <ScreenReaderDataTable
            caption={isTranslation ? text('翻译练习趋势数据', 'Translation trend data') : text('日语纠错趋势数据', 'Japanese correction trend data')}
            headers={[text('日期', 'Date'), text('练习次数', 'Attempts'), text('平均总分', 'Average score')]}
            rows={practice.dailyTrends.map((item) => [item.date, String(item.attemptCount), formatScore(item.averageTotalScore)])}
          />
        </ChartSurface>

        <div className="statistics-side-stack">
          <ChartSurface
            className="statistics-skill-panel"
            title={isTranslation ? text('翻译四项能力', 'Translation skill dimensions') : text('纠错四项能力', 'Correction skill dimensions')}
            description={text('已评测练习的各项平均分', 'Average scores across evaluated attempts')}
          >
            {hasScoreDimensions ? <div className="statistics-chart statistics-skill-chart"><ResponsiveContainer width="100%" height="100%">
                <BarChart accessibilityLayer layout="vertical" data={scoreDimensions} margin={{ top: 0, right: 26, left: 4, bottom: 0 }}>
                  <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" horizontal={false} />
                  <XAxis type="number" domain={[0, 100]} tickLine={false} axisLine={false} />
                  <YAxis type="category" dataKey="name" width={92} tickLine={false} axisLine={false} />
                  <Tooltip formatter={(value) => <ScoreTooltipValue value={value} />} />
                  <Bar dataKey="score" name={text('平均分', 'Average score')} fill={isTranslation ? 'var(--brand)' : 'var(--success)'} radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer></div> : <EmptyChart text={text('所选期间暂无评分维度数据。', 'No score dimension data for this period.')} />}
            <ScreenReaderDataTable
              caption={isTranslation ? text('翻译四项能力数据', 'Translation skill dimension data') : text('纠错四项能力数据', 'Correction skill dimension data')}
              headers={[text('能力维度', 'Skill dimension'), text('平均分', 'Average score')]}
              rows={scoreDimensions.map((item) => [item.name, formatScore(item.score)])}
            />
          </ChartSurface>

          <ChartSurface
            className="statistics-review-panel"
            title={text('复习表现', 'Review performance')}
            description={text('卡片状态为实时数据，复习表现按所选期间统计', 'Card status is real time; review performance follows the selected period')}
          >
            <div className="statistics-review-compact">
              {reviewStates.length > 0 ? <div className="review-state-chart">
                <div className="review-state-pie"><ResponsiveContainer width="100%" height="100%">
                  <PieChart accessibilityLayer>
                    <Tooltip />
                    <Pie data={reviewStates} dataKey="value" nameKey="name" innerRadius={24} outerRadius={38} paddingAngle={2}>
                      {reviewStates.map((item) => <Cell key={item.name} fill={item.color} />)}
                    </Pie>
                  </PieChart>
                </ResponsiveContainer></div>
                <div className="review-state-legend" aria-label={text('复习卡片状态图例', 'Review card status legend')}>
                  {reviewStates.map((item) => <span key={item.name}><i aria-hidden="true" style={{ backgroundColor: item.color }} />{item.name}</span>)}
                </div>
              </div> : <EmptyChart text={text('当前暂无复习卡片。', 'No review cards yet.')} />}
              <dl className="review-period-summary">
                <div><dt>{text('期间复习', 'Reviews in period')}</dt><dd>{text(`${statistics.reviewOverview.periodReviewAttemptCount} 次`, String(statistics.reviewOverview.periodReviewAttemptCount))}</dd></div>
                <div><dt>{text('期间通过率', 'Pass rate')}</dt><dd>{formatScore(statistics.reviewOverview.periodReviewPassRate, '%')}</dd></div>
              </dl>
            </div>
            <ScreenReaderDataTable
              caption={text('复习卡片状态数据', 'Review card status data')}
              headers={[text('卡片状态', 'Card status'), text('数量', 'Count')]}
              rows={reviewStates.map((item) => [item.name, String(item.value)])}
            />
          </ChartSurface>
        </div>
      </section>
    </section>
  </>
}

function MetricItem({ label, value, detail, score }: { label: string; value: string; detail: string; score?: number | null }) {
  return <div className="statistics-metric-item"><span>{label}</span><strong className={score === undefined ? undefined : scoreToneClassName(score)}>{value}</strong><small>{detail}</small></div>
}

function ScoreTooltipValue({ value }: { value: unknown }) {
  const score = toFiniteNumber(value)
  return <span className={scoreToneClassName(score)}>{formatScore(score)}</span>
}

function toFiniteNumber(value: unknown) {
  const score = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : Number.NaN
  return Number.isFinite(score) ? score : null
}

function ChartSurface({ className, titleId, title, description, actions, children }: { className?: string; titleId?: string; title: string; description: string; actions?: React.ReactNode; children: React.ReactNode }) {
  return <section className={`surface statistics-chart-surface${className ? ` ${className}` : ''}`} aria-label={`${title}。${description}`}><div className="statistics-chart-heading"><div className="section-title"><span className="label">{description}</span><strong id={titleId}>{title}</strong></div>{actions}</div>{children}</section>
}

function EmptyChart({ text }: { text: string }) {
  return <p className="empty-state statistics-empty-chart" role="status">{text}</p>
}

function ScreenReaderDataTable({ caption, headers, rows }: { caption: string; headers: string[]; rows: string[][] }) {
  return <div className="sr-only"><table><caption>{caption}</caption><thead><tr>{headers.map((header) => <th key={header} scope="col">{header}</th>)}</tr></thead><tbody>{rows.map((row, rowIndex) => <tr key={`${caption}-${rowIndex}`}>{row.map((cell, cellIndex) => cellIndex === 0 ? <th key={cellIndex} scope="row">{cell}</th> : <td key={cellIndex}>{cell}</td>)}</tr>)}</tbody></table></div>
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
