import type { Question, QuestionFormState } from '../../types/question'

export function toQuestionForm(question: Question): QuestionFormState {
  const standardAnswer = question.answers.find((answer) => answer.answerType === 'STANDARD' && answer.primaryAnswer)
  const referenceAnswers = question.answers
    .filter((answer) => answer.answerType === 'REFERENCE')
    .map((answer) => answer.answerText)
    .join('\n')

  return {
    sourceText: question.sourceText,
    contextText: question.contextText,
    level: question.level,
    difficulty: String(question.difficulty),
    grammarPoint: question.grammarPoint,
    spoken: question.spoken,
    business: question.business,
    exam: question.exam,
    tagCodes: question.tags.map((tag) => tag.code).join(', '),
    standardAnswer: standardAnswer?.answerText ?? '',
    referenceAnswers,
  }
}

export function formatQuestionFlags(question: Question, english: boolean) {
  const flags = [
    question.spoken ? (english ? 'Spoken' : '口语') : null,
    question.business ? (english ? 'Business' : '商务') : null,
    question.exam ? (english ? 'Exam' : '考试') : null,
  ].filter(Boolean)

  return flags.join(english ? ', ' : '、') || '-'
}

export function formatQuestionSourceType(sourceType: Question['sourceType'], english: boolean) {
  if (sourceType === 'AI') return 'AI'
  if (sourceType === 'REVIEW_DERIVED') return english ? 'Review-derived' : '复习衍生'
  return english ? 'Manual' : '人工'
}

export function formatQuestionType(questionType: Question['questionType'], english: boolean) {
  return questionType.endsWith('_ARTICLE') ? (english ? 'Article' : '文章翻译') : (english ? 'Sentence' : '短句翻译')
}
