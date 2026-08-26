const zh = {
  appTitle: '日语翻译练习',
  backend: '后端服务',
  learningMode: '学习模式',
  mainNavigation: '主导航',
  practice: '练习',
  reviewCards: '复习卡片',
  answerHistory: '答题记录',
  questionManagement: '题目管理',
  learningAnalytics: '学习分析',
  backToPractice: '返回练习首页',
} as const

type MessageKey = keyof typeof zh

const en = {
  appTitle: 'Japanese Translation Practice',
  backend: 'Backend',
  learningMode: 'Learning mode',
  mainNavigation: 'Main navigation',
  practice: 'Practice',
  reviewCards: 'Review',
  answerHistory: 'History',
  questionManagement: 'Questions',
  learningAnalytics: 'Progress',
  backToPractice: 'Back to practice',
} satisfies Record<MessageKey, string>

export const MESSAGES = { 'zh-CN': zh, 'en-US': en } as const
export type { MessageKey }
