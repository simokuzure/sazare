export const REQUEST_FAILED_EN = 'The request could not be completed. Please try again.'

// API 目前返回中文 message；集中适配显示文案，避免各页面遗漏或丢失校验细节。
const ERROR_MESSAGES: Record<string, string> = {
  '参数错误': 'Invalid request parameters.',
  '请求参数不合法': 'Invalid request parameters.',
  '业务处理失败': REQUEST_FAILED_EN,
  '系统异常': 'A server error occurred. Please try again.',
  '响应不是合法 JSON': 'The server response is not valid JSON.',
  '请求体不能为空或 JSON 格式错误': 'The request body is missing or contains invalid JSON.',
  '参数类型错误': 'Invalid parameter type.',
  'Content-Type 不受支持，请使用接口要求的媒体类型': 'Unsupported Content-Type. Use the media type required by this endpoint.',
  '网络连接中断，请检查连接。': 'The network connection was interrupted. Check your connection.',
  '服务器未返回完整结果。': 'The server did not return a complete result.',
  '服务器响应格式不正确。': 'The server response has an invalid format.',
  '请求被拒绝。': 'The request was rejected.',
  '列表加载不完整，请重新加载。': 'The list could not be fully loaded. Reload it and try again.',
  '后端没有返回日语纠错结果': 'The server did not return a Japanese correction result.',
  '后端没有返回学习统计数据': 'The server did not return learning statistics.',
  '后端没有返回复习卡片详情': 'The server did not return review card details.',
  '后端没有返回复习评分结果': 'The server did not return a review score.',
  '后端没有返回衍生题生成结果': 'The server did not return a derived question.',
  '后端没有返回答题记录详情': 'The server did not return answer record details.',
  '创建复习卡片未返回结果': 'The server did not return the created review card.',
  '聊天文件不能为空': 'The chat file must not be empty.',
  '聊天文件不能超过 128 KiB': 'The chat file must not exceed 128 KiB.',
  '聊天文件读取失败': 'The chat file could not be read.',
  '聊天文件必须使用 UTF-8 编码': 'The chat file must use UTF-8 encoding.',
  'Markdown 代码围栏未闭合': 'Close all Markdown code fences.',
  '未找到聊天角色标题，请使用 ## User 或 ## Assistant 标题分隔消息': 'No role headings were found. Separate messages with ## User or ## Assistant headings.',
  '聊天记录必须包含用户消息': 'The chat history must contain a user message.',
  '聊天正文不能超过 30000 字符': 'The chat body must not exceed 30,000 characters.',
  '聊天消息不能为空': 'Chat messages must not be empty.',
  '聊天分析服务请求失败，请稍后重新分析': 'Chat analysis failed. Please try analyzing again later.',
  '聊天分析未完整完成，请重新分析': 'Chat analysis did not finish. Please analyze again.',
  '聊天分析响应格式错误': 'The chat analysis response has an invalid format.',
  '聊天分析响应缺少唯一候选结果': 'The chat analysis response must contain exactly one result.',
  '聊天分析响应缺少文本内容': 'The chat analysis response is missing text.',
  '没有适用于聊天纠错的分类': 'No error categories are available for chat analysis.',
  '请选择需要确认的候选': 'Select candidates to confirm.',
  '一次最多确认 200 个候选': 'Confirm at most 200 candidates at a time.',
  '一次最多确认 20 条错误': 'Confirm at most 20 errors at a time.',
  '已有复习卡片不存在': 'The selected review card does not exist.',
  '目标复习卡片不存在或已删除': 'The target review card does not exist or was deleted.',
  '请选择已有复习重点': 'Select an existing review focus.',
  '已有复习重点不可用或学习模式不匹配': 'The selected review focus is unavailable or belongs to a different learning mode.',
  '新建复习重点需要分类、名称和说明': 'A new review focus requires a category, name, and description.',
  '错误分类不可用或不适用于日语纠错': 'The error category is unavailable or cannot be used for Japanese correction.',
  '复习题面必须使用当前学习模式的中文或英文，不能包含日语假名': 'Use the source language for the current learning mode in the review prompt, without Japanese kana.',
  '音频大小不能超过 8 MiB': 'Audio must not exceed 8 MiB.',
  '音频读取失败，请重新录音': 'The audio could not be read. Record again.',
  '音频不能为空': 'Audio must not be empty.',
  '音频格式只支持 audio/webm 或 audio/ogg': 'Only audio/webm and audio/ogg are supported.',
  '未识别到语音，请重新录音': 'No speech was recognized. Record again.',
  '语音转写请求过于频繁，请稍后重新录音': 'Too many transcription requests. Wait a moment and record again.',
  '语音转写服务异常，请稍后重新录音': 'The transcription service is unavailable. Try recording again later.',
  '语音转写请求失败，请检查 Google 配置或网络后重新录音': 'Transcription failed. Check the Google configuration or network connection and record again.',
  '语音转写结果格式错误，请重新录音': 'The transcription response has an invalid format. Record again.',
  '本地用户不存在或不可用': 'The local user does not exist or is unavailable.',
  '默认用户不存在或未启用': 'The default user does not exist or is disabled.',
  '答题记录不存在或已删除': 'The answer record does not exist or was deleted.',
  '作答记录不存在': 'The answer record does not exist.',
  '作答对应题目不存在': 'The question for this answer does not exist.',
  '仅已评分的作答可以确认错误': 'Errors can only be confirmed for scored answers.',
  '仅已评分的作答可以添加复习卡片': 'Review cards can only be added for scored answers.',
  '没有适用于日语纠错的二级错误类型': 'No second-level error categories are available for Japanese correction.',
  '没有可用的二级错误类型': 'No second-level error categories are available.',
  '自定义复习卡片分类不可用': 'The category for custom review cards is unavailable.',
  '同名复习卡片已存在，请使用其他复习重点': 'A review card with this name already exists. Choose a different review focus.',
  '同名用户错误类型已归档，请选择已有类型': 'An archived review focus already has this name. Select the existing focus.',
  '用户错误类型不存在或已归档': 'The review focus does not exist or was archived.',
  '用户错误类型不存在或不属于当前用户': 'The review focus does not exist or does not belong to the current user.',
  '用户错误类型不存在或未启用': 'The review focus does not exist or is disabled.',
  '错误类型不存在或未启用': 'The error category does not exist or is disabled.',
  '追加已有用户错误类型时 userErrorTypeId 不能为空': 'Select a review focus before adding to an existing one.',
  '新建用户错误类型时 errorTypeId 不能为空': 'Select an error category before creating a review focus.',
  '不支持的错误确认方式': 'This error confirmation method is not supported.',
  '当前题型不支持自定义复习卡片': 'This question type does not support custom review cards.',
  '文章复习卡片必须选择中文原句': 'Select a source sentence for the article review card.',
  'sourceSegmentIndex 超出文章原句范围': 'The source sentence index is outside the article.',
  '题目不存在或已删除': 'The question does not exist or was deleted.',
  '题目不存在或不可用': 'The question does not exist or is unavailable.',
  '题目不存在或未启用': 'The question does not exist or is disabled.',
  '题目没有可用标准答案': 'The question has no available standard answer.',
  '题目没有可用答案': 'The question has no available answers.',
  '新建题目只能是中译日或英译日短句': 'New questions must be Chinese-to-Japanese or English-to-Japanese sentences.',
  '没有可用场景标签': 'No scene tags are available.',
  '没有可用文章体裁': 'No article genres are available.',
  'genreTagCode 不存在、未启用或不是 GENRE 标签': 'The selected genre tag does not exist, is disabled, or is not a genre.',
  'tagCodes 至少需要 1 个场景标签': 'Select at least one scene tag.',
  '文章题必须且只能选择 1 个体裁标签': 'An article question requires exactly one genre tag.',
  '文章题必须有且只有 1 个主标准答案': 'An article question requires exactly one primary standard answer.',
  '文章题原文与日文参考答案段落数必须一致': 'The source article and Japanese reference must have the same number of paragraphs.',
  '文章题中日段落数不一致': 'The source article and Japanese reference have different paragraph counts.',
  '文章题日文参考句不能重复': 'Japanese reference sentences must not be duplicated.',
  '文章题标准答案不合法': 'The article standard answer is invalid.',
  '文章题每个 answerText 段落必须包含日语假名': 'Each Japanese answer paragraph must contain Japanese kana.',
  '文章题每个 sourceText 段落必须是不含中日文字的完整英文句子': 'Each source paragraph must be a complete English sentence without Chinese or Japanese characters.',
  '文章题每个 sourceText 段落必须是无假名的完整中文句子': 'Each source paragraph must be a complete Chinese sentence without Japanese kana.',
  'sourceText 必须包含英文且不能包含中文或日语假名': 'The source text must contain English without Chinese characters or Japanese kana.',
  'sourceText 必须包含中文且不能包含日语假名': 'The source text must contain Chinese characters without Japanese kana.',
  '复习题英文必须是英文且不能包含中文或日语假名': 'The review prompt must use English without Chinese characters or Japanese kana.',
  '纯日语纠错的复习题中文文本必须包含中文且不能包含日语假名': 'The review prompt must contain Chinese characters without Japanese kana.',
  '文章答案必须包含日语假名': 'The article answer must contain Japanese kana.',
  '目标日语表达必须包含日语假名': 'The target Japanese expression must contain Japanese kana.',
  '日语参考答案必须包含日语假名': 'The Japanese reference answer must contain Japanese kana.',
  '复习卡片不存在或已删除': 'The review card does not exist or was deleted.',
  '复习卡片没有进行中的周期': 'The review card has no active cycle.',
  '已掌握卡片不能生成衍生题': 'Derived questions cannot be generated for mastered cards.',
  '当前周期不需要生成衍生题': 'The current cycle does not need a derived question.',
  '复习周期已变化，当前不需要生成衍生题': 'The review cycle has changed and no derived question is currently needed.',
  '复习卡片归属不合法': 'The review card does not belong to the current user.',
  '复习题不存在或当前不可作答': 'The review question does not exist or cannot currently be answered.',
  '复习题当前不可作答': 'This review question cannot currently be answered.',
  '请先重试最近答错的题目': 'Retry the most recently failed question first.',
  '复习卡片已掌握': 'This review card has been mastered.',
  '复习卡片尚未到期': 'This review card is not due yet.',
  '当前周期缺少原题': 'The current cycle is missing its original question.',
  '复习原题不存在': 'The original review question does not exist.',
  '导入复习题不能为空': 'Select review questions to import.',
  'minTotalScore 不能大于 maxTotalScore': 'The minimum score must not exceed the maximum score.',
  'startDate 不能晚于 endDate': 'The start date must not be later than the end date.',
  'CUSTOM 范围必须同时提供 startDate 和 endDate': 'Provide both a start date and an end date for a custom range.',
  '预设范围不能提供 startDate 或 endDate': 'Do not specify custom dates for a preset range.',
  '不支持的统计范围': 'This statistics range is not supported.',
  'AI 服务请求失败': 'The AI service request failed. Please try again.',
  'AI 服务请求被中断': 'The AI service request was interrupted. Please try again.',
  '复习 AI 评分失败': 'AI review scoring failed. Please try again.',
  'AI 生成题目存在近似重复，补生成后仍未达到要求数量': 'The AI could not generate enough distinct questions. Please try again.',
  'AI 生成文章存在近似重复，补生成后仍未获得可用文章': 'The AI could not generate a distinct article. Please try again.',
}

const FIELD_NAMES: Record<string, string> = {
  '复习重点': 'Review focus', '复习重点名称': 'Review focus name', '复习重点说明': 'Review focus description',
  '复习题面': 'Review prompt', '复习题中文': 'Review prompt', '日语参考答案': 'Japanese reference answer',
  '目标日语表达': 'Target Japanese expression', '中文原句索引': 'Source sentence index', '学习模式': 'Learning mode',
  '严重程度': 'Severity', '错误级别': 'Severity', '错误分类 ID': 'Error category ID',
  '用户错误类型 ID': 'Review focus ID', '确认方式': 'Confirmation method', '候选': 'Candidates', '答案': 'Answer',
}

function fieldName(value: string) {
  const name = value.trim()
  return Object.hasOwn(FIELD_NAMES, name) ? FIELD_NAMES[name] : /^[\w.[\]]+$/.test(name) ? name : null
}

export function englishErrorMessage(message: string): string {
  if (Object.hasOwn(ERROR_MESSAGES, message)) return ERROR_MESSAGES[message]
  if (!/\p{Script=Han}/u.test(message)) return message

  // Bean Validation 的响应可能带有 items[0].name 等字段路径。
  const separator = message.indexOf(': ')
  if (separator !== -1 && /^[\w.[\]]+$/.test(message.slice(0, separator))) {
    return `${message.slice(0, separator)}: ${englishErrorMessage(message.slice(separator + 2))}`
  }

  const rules: [RegExp, (match: RegExpMatchArray, field: string) => string][] = [
    [/^(.+?)\s*不能为空$/, (_, field) => `${field} is required.`],
    [/^(.+?)\s*不能包含空值$/, (_, field) => `${field} must not contain empty items.`],
    [/^(.+?)\s*(?:长度不能超过|不能超过|最多)\s*(\d+)\s*(?:个字符|字符)?$/, (m, field) => `${field} must not exceed ${m[2]} characters.`],
    [/^(.+?)\s*最多\s*(\d+)\s*[个条]$/, (m, field) => `${field} must contain at most ${m[2]} items.`],
    [/^(.+?)\s*必须在\s*(\d+)\s*到\s*(\d+)\s*之间$/, (m, field) => `${field} must be between ${m[2]} and ${m[3]}.`],
    [/^(.+?)\s*(?:必须大于等于|不能小于)\s*(\d+)$/, (m, field) => `${field} must be at least ${m[2]}.`],
    [/^(.+?)\s*必须大于\s*(\d+)$/, (m, field) => `${field} must be greater than ${m[2]}.`],
    [/^(.+?)\s*(?:必须小于等于|不能大于)\s*(\d+)$/, (m, field) => `${field} must not exceed ${m[2]}.`],
    [/^(.+?)\s*只能是\s*([A-Z\d_、 ，或]+)$/, (m, field) => `${field} must be one of: ${m[2].trim().replace(/\s*或\s*/g, ', ').replace(/[、，]/g, ', ')}.`],
    [/^(.+?)\s*不合法$/, (_, field) => `${field} is invalid.`],
  ]
  for (const [pattern, format] of rules) {
    const match = message.match(pattern)
    const field = match && fieldName(match[1])
    if (match && field) return format(match, field)
  }

  if (message === '题目正在复习周期中，不能删除') return 'This question is part of an active review cycle and cannot be deleted.'
  if (/^题目正在复习周期中，不能/.test(message)) return 'This question is part of an active review cycle and cannot be changed.'
  if (/^标签 code 不存在或未启用: /.test(message)) return 'One or more selected tags do not exist or are disabled.'
  if (/^聊天分析结果不完整或不合法：/.test(message)) return 'The chat analysis result is incomplete or invalid. Please analyze again.'
  if (/^(AI |复习评分|复习生题|Google AI )/.test(message)) return 'The AI service returned an invalid or incomplete result. Please try again.'
  return REQUEST_FAILED_EN
}
