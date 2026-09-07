import type { Question } from '../../types/question'
import { splitArticleParagraphs } from '../../utils/article'
import { getTagDisplayName } from '../../utils/tag'
import { formatQuestionFlags, formatQuestionSourceType, formatQuestionType } from './questionPresentation'

type QuestionDetailProps = {
  question: Question
  english: boolean
  text: (chinese: string, english: string) => string
}

function QuestionMetaStrip({ question, english, text }: QuestionDetailProps) {
  return (
    <dl className="question-meta-strip" aria-label={text('题目信息', 'Question information')}>
      <div><dt>{text('题型', 'Type')}</dt><dd>{formatQuestionType(question.questionType, english)}</dd></div>
      <div><dt>{text('等级/难度', 'Level / difficulty')}</dt><dd>{question.level} / {question.difficulty}</dd></div>
      <div><dt>{text('来源', 'Source')}</dt><dd><span className={question.sourceType === 'AI' ? 'data-badge is-brand' : 'data-badge'}>{formatQuestionSourceType(question.sourceType, english)}</span></dd></div>
      <div><dt>{text('状态', 'Status')}</dt><dd><span className={question.enabled ? 'data-badge is-success' : 'data-badge'}>{question.enabled ? text('启用', 'Enabled') : text('停用', 'Disabled')}</span></dd></div>
      <div><dt>{text('属性', 'Properties')}</dt><dd>{formatQuestionFlags(question, english)}</dd></div>
      <div className="question-meta-tags">
        <dt>{text('标签', 'Tags')}</dt>
        <dd><span className="tag-chip-row">{question.tags.map((tag) => <span key={tag.id}>{getTagDisplayName(tag, english)}</span>)}</span></dd>
      </div>
    </dl>
  )
}

export function ShortQuestionDetail({ question, english, text }: QuestionDetailProps) {
  const standardAnswer = question.answers.find((answer) => answer.answerType === 'STANDARD') ?? question.answers[0]
  const additionalAnswers = question.answers.filter((answer) => answer.id !== standardAnswer?.id)

  return (
    <div className="short-question-detail">
      <QuestionMetaStrip question={question} english={english} text={text} />
      <dl className="question-detail-notes">
        <div><dt>{text('语境', 'Context')}</dt><dd>{question.contextText}</dd></div>
        <div><dt>{text('语法点', 'Grammar point')}</dt><dd>{question.grammarPoint}</dd></div>
      </dl>
      <section className="short-question-comparison" aria-label={text('原文与答案对照', 'Source and answer comparison')}>
        <div className="short-question-comparison-header" aria-hidden="true"><span>{text('中文原文', 'English source')}</span><span>{text('标准答案', 'Standard answer')}</span></div>
        <div className="short-question-comparison-row">
          <div className="short-question-text"><span className="short-question-mobile-label">{text('中文原文', 'English source')}</span><p>{question.sourceText}</p></div>
          <div className="short-question-answer-stack">
            <div className="short-question-text"><span className="short-question-mobile-label">{text('标准答案', 'Standard answer')}</span><p>{standardAnswer?.answerText ?? '-'}</p></div>
            {additionalAnswers.map((answer) => <div className="short-question-additional-answer" key={answer.id}><p>{answer.answerText}</p></div>)}
          </div>
        </div>
      </section>
    </div>
  )
}

export function ArticleQuestionDetail({ question, english, text }: QuestionDetailProps) {
  const standardAnswer = question.answers.find((answer) => answer.answerType === 'STANDARD')
  const sourceSegments = splitArticleParagraphs(question.sourceText)
  const answerSegments = splitArticleParagraphs(standardAnswer?.answerText ?? '')
  const segmentCount = Math.max(sourceSegments.length, answerSegments.length)

  return (
    <div className="article-question-detail">
      <QuestionMetaStrip question={question} english={english} text={text} />
      <dl className="question-detail-notes">
        <div><dt>{text('语境', 'Context')}</dt><dd>{question.contextText}</dd></div>
        <div><dt>{text('生词提示', 'Vocabulary hints')}</dt><dd className="pre-wrap-text">{question.grammarPoint}</dd></div>
      </dl>
      <section className="article-question-comparison" aria-label={text('原文与标准答案逐句对照', 'Sentence-aligned source and standard answer')}>
        <div className="article-question-comparison-header" aria-hidden="true"><span>{text('中文原文', 'English source')}</span><span>{text('标准答案', 'Standard answer')}</span></div>
        <ol className="article-question-segment-list">
          {Array.from({ length: segmentCount }, (_, index) => (
            <li key={`${index}-${sourceSegments[index] ?? answerSegments[index]}`}>
              <span className="article-question-segment-number" aria-hidden="true">{index + 1}</span>
              <div className="article-question-segment-text"><span className="article-question-mobile-label">{text('中文原文', 'English source')}</span><p>{sourceSegments[index] ?? '-'}</p></div>
              <div className="article-question-segment-text"><span className="article-question-mobile-label">{text('标准答案', 'Standard answer')}</span><p>{answerSegments[index] ?? '-'}</p></div>
            </li>
          ))}
        </ol>
      </section>
    </div>
  )
}
