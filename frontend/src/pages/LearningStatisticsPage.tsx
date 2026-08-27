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
import type {
  LearningStatistics,
  LearningStatisticsFilters,
  LearningStatisticsPractice,
  LearningStatisticsRange,
} from '../types/learningStatistics'
import { useLanguage } from '../i18n/LanguageContext'

const RANGE_OPTIONS: Array<{ value: LearningStatisticsRange; label: string }> = [
  { value: 'LAST_7_DAYS', label: '近 7 天' },
  { value: 'LAST_30_DAYS', label: '近 30 天' },
  { value: 'LAST_90_DAYS', label: '近 90 天' },
  { value: 'CUSTOM', label: '自定义' },
]

const REVIEW_COLORS = ['#dc2626', '#2563eb', '#16a34a']
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
      { name: text('待复习', 'Due'), value: statistics.reviewOverview.dueCardCount },
      { name: text('进行中', 'Active'), value: statistics.reviewOverview.inProgressCardCount },
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
  reviewStates: Array<{ name: string; value: number }>
  onPracticeTypeChange: (value: PracticeType) => void
}) {
  const { text } = useLanguage()
  const isTranslation = practiceType === 'TRANSLATION'
  return <>
    <section className="statistics-overview-grid statistics-check-in-grid" aria-label={text('学习打卡', 'Learning check-ins')}>
      <MetricCard
        label={text('连续打卡', 'Current streak')}
        value={text(`${statistics.checkInOverview.currentStreakDays} 天`, `${statistics.checkInOverview.currentStreakDays} days`)}
        detail={text('今天尚未学习时保留截至昨天的连续天数', 'Keeps yesterday’s streak until today ends')}
      />
      <MetricCard
        label={text('累计打卡', 'Total check-ins')}
        value={text(`${statistics.checkInOverview.totalCheckInDays} 天`, `${statistics.checkInOverview.totalCheckInDays} days`)}
        detail={text('全部学习模式，按东京自然日去重', 'All learning modes, deduplicated by Tokyo date')}
      />
    </section>

    <section className="statistics-dashboard-section" aria-labelledby="practice-statistics-title">
      <header className="statistics-section-header">
        <div className="section-title">
          <span className="label">{text('练习数据', 'Practice data')}</span>
          <strong id="practice-statistics-title">{text('练习表现', 'Practice performance')}</strong>
        </div>
        <div className="statistics-segmented-control" role="group" aria-label={text('练习类型', 'Practice type')}>
          <button type="button" className={isTranslation ? 'is-active' : undefined} aria-pressed={isTranslation} onClick={() => onPracticeTypeChange('TRANSLATION')}>{text('翻译', 'Translation')}</button>
          <button type="button" className={!isTranslation ? 'is-active' : undefined} aria-pressed={!isTranslation} onClick={() => onPracticeTypeChange('CORRECTION')}>{text('日语纠错', 'Japanese correction')}</button>
        </div>
      </header>

      <section className="statistics-overview-grid statistics-practice-metrics" aria-label={text('练习概览指标', 'Practice overview metrics')}>
        <MetricCard
          label={isTranslation ? text('翻译次数', 'Translation attempts') : text('纠错次数', 'Correction attempts')}
          value={String(practice.attemptCount)}
          detail={`${statistics.period.startDate} – ${statistics.period.endDate}`}
        />
        <MetricCard
          label={isTranslation ? text('翻译平均总分', 'Average translation score') : text('纠错平均总分', 'Average correction score')}
          value={formatScore(practice.averageTotalScore)}
          detail={isTranslation ? text('短句与文章翻译', 'Sentence and article translations') : text('仅统计纯日语纠错记录', 'Japanese correction records only')}
        />
      </section>

      <section className="statistics-chart-grid">
        <ChartSurface
          title={isTranslation ? text('翻译练习趋势', 'Translation trend') : text('日语纠错趋势', 'Japanese correction trend')}
          description={text('每日练习次数与平均总分', 'Daily attempts and average score')}
        >
          <ResponsiveContainer width="100%" height={280}>
            <ComposedChart data={practice.dailyTrends} margin={{ top: 8, right: 20, left: -8, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" tickFormatter={formatShortDate} minTickGap={24} />
              <YAxis yAxisId="count" allowDecimals={false} />
              <YAxis yAxisId="score" orientation="right" domain={[0, 100]} />
              <Tooltip labelFormatter={formatDateLabel} />
              <Legend />
              <Bar yAxisId="count" dataKey="attemptCount" name={text('练习次数', 'Attempts')} fill={isTranslation ? '#2563eb' : '#0f766e'} radius={[3, 3, 0, 0]} />
              <Line yAxisId="score" type="monotone" dataKey="averageTotalScore" name={text('平均总分', 'Average score')} stroke={isTranslation ? '#16a34a' : '#d97706'} strokeWidth={2} connectNulls dot={false} />
            </ComposedChart>
          </ResponsiveContainer>
        </ChartSurface>

        <ChartSurface
          title={isTranslation ? text('翻译四项能力', 'Translation skill dimensions') : text('纠错四项能力', 'Correction skill dimensions')}
          description={text('已评测练习的各项平均分', 'Average scores across evaluated attempts')}
        >
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={scoreDimensions} margin={{ top: 8, right: 12, left: -14, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis domain={[0, 100]} />
              <Tooltip formatter={(value) => value == null ? '-' : Number(value).toFixed(2)} />
              <Bar dataKey="score" name={text('平均分', 'Average score')} fill={isTranslation ? '#7c3aed' : '#0f766e'} radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartSurface>
      </section>
    </section>

    <section className="surface statistics-review-surface" aria-labelledby="review-statistics-title">
      <div className="section-title">
        <span className="label">{text('卡片状态为当前实时状态，复习表现按所选期间统计', 'Card status is real time; review performance follows the selected period')}</span>
        <strong id="review-statistics-title">{text('复习表现', 'Review performance')}</strong>
      </div>
      <div className="statistics-review-layout">
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
        </dl>
      </div>
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
