import { readApiResponse } from './client'
import type { PageData } from '../types/api'
import type { AnswerReview } from '../types/review'
import type { AiQuestionGenerationPayload, Question, QuestionFilterState, QuestionPayload, RandomQuestionFilter } from '../types/question'

export function parseCodeList(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

export async function generateQuestions(payload: AiQuestionGenerationPayload): Promise<Question[]> {
  const response = await fetch('/api/questions/ai-generations', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
  const result = await readApiResponse<Question[]>(response)
  return result.data ?? []
}

export async function submitQuestionAnswer(questionId: number, answerText: string): Promise<AnswerReview | null> {
  const response = await fetch(`/api/questions/${questionId}/answers`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ answerText }),
  })
  const result = await readApiResponse<AnswerReview>(response)
  return result.data
}

export async function fetchQuestions(filters: QuestionFilterState, signal?: AbortSignal): Promise<PageData<Question>> {
  const searchParams = new URLSearchParams({
    questionType: 'TRANSLATION_ZH_TO_JA',
    page: String(filters.page),
    size: String(filters.size),
  })
  if (filters.level) {
    searchParams.set('level', filters.level)
  }
  if (filters.difficulty) {
    searchParams.set('difficulty', filters.difficulty)
  }
  if (filters.sourceType) {
    searchParams.set('sourceType', filters.sourceType)
  }
  if (filters.enabled !== 'all') {
    searchParams.set('enabled', filters.enabled)
  }
  const tagCodes = parseCodeList(filters.tagCodes)
  if (tagCodes.length > 0) {
    searchParams.set('tagCodes', tagCodes.join(','))
  }

  const response = await fetch(`/api/questions?${searchParams.toString()}`, { signal })
  const result = await readApiResponse<PageData<Question>>(response)
  return result.data ?? { items: [], page: filters.page, size: filters.size, total: 0 }
}

export async function fetchRandomQuestion(filters: RandomQuestionFilter): Promise<Question | null> {
  const searchParams = new URLSearchParams({
    questionType: 'TRANSLATION_ZH_TO_JA',
  })
  if (filters.level) {
    searchParams.set('level', filters.level)
  }
  if (filters.difficulty) {
    searchParams.set('difficulty', filters.difficulty)
  }
  if (filters.tagCodes.length > 0) {
    searchParams.set('tagCodes', filters.tagCodes.join(','))
  }

  const response = await fetch(`/api/questions/random?${searchParams.toString()}`)
  const result = await readApiResponse<Question>(response)
  return result.data
}

export async function fetchQuestion(questionId: number): Promise<Question | null> {
  const response = await fetch(`/api/questions/${questionId}`)
  const result = await readApiResponse<Question>(response)
  return result.data
}

export async function saveQuestion(payload: QuestionPayload, questionId?: number): Promise<Question | null> {
  const response = await fetch(questionId ? `/api/questions/${questionId}` : '/api/questions', {
    method: questionId ? 'PUT' : 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
  const result = await readApiResponse<Question>(response)
  return result.data
}

export async function toggleQuestionEnabled(question: Question): Promise<void> {
  const response = await fetch(`/api/questions/${question.id}/enabled`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ enabled: !question.enabled }),
  })
  await readApiResponse<null>(response)
}

export async function deleteQuestion(questionId: number): Promise<void> {
  const response = await fetch(`/api/questions/${questionId}`, {
    method: 'DELETE',
  })
  await readApiResponse<null>(response)
}
