# 用户答案 AI 评分 Prompt 设计

## 目标

用于评分日语中译日练习答案，当前只支持 `TRANSLATION_ZH_TO_JA`。

用户提交日语答案后，后端根据题目、标签、标准答案和用户答案构造 Prompt。AI 只返回结构化 JSON，后端负责解析、校验、计算总分并保存评分摘要。

MVP 阶段详细评价只返回给前端展示，不单独入库。后端仅持久化 `user_answers` 已支持的字段：

- 四项评分
- 总分
- AI 总评
- 回答状态

## 调用策略

- 默认复用现有 `AI_PROVIDER=mock|google` 配置。
- `mock` 用于本地开发和测试。
- `google` 调用 Google Gemini `generateContent`。
- AI 输出不能直接保存，必须先经过 JSON 解析、字段校验、枚举校验和分数校验。
- `totalScore` 虽要求 AI 返回四项均值，但后端保存时必须重新计算，不能信任 AI 汇总值。

## 输入参数

后端构造 Prompt 时传入以下参数：

| 参数 | 说明 |
| --- | --- |
| `questionType` | 题目类型，当前固定为 `TRANSLATION_ZH_TO_JA` |
| `sourceText` | 中文原文 |
| `contextText` | 中文语境说明 |
| `level` | JLPT 等级 |
| `difficulty` | 难度，范围 1 到 5 |
| `grammarPoint` | 语法点说明 |
| `spoken` | 是否口语 |
| `business` | 是否商务 |
| `exam` | 是否考试 |
| `tags` | 题目标签，包含 `code`、`name`、`description` |
| `standardAnswers` | 标准答案和参考答案 |
| `userAnswer` | 用户提交的日语答案 |

标签候选格式：

```json
[
  {
    "code": "FINANCE_BANK",
    "name": "银行",
    "description": "金融场景标签"
  }
]
```

标准答案格式：

```json
[
  {
    "answerText": "今日の午後、銀行へ振り込みに行きます。",
    "answerType": "STANDARD",
    "primaryAnswer": true,
    "sortOrder": 0
  }
]
```

## API 契约

```text
POST /api/questions/{questionId}/answers
```

请求体：

```json
{
  "answerText": "今日の午後、銀行に送金をしに行きます。"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userAnswerId": 1,
    "questionId": 7,
    "answerText": "今日の午後、銀行に送金をしに行きます。",
    "answerStatus": "REVIEWED",
    "scores": {
      "grammarVocabularyScore": 82,
      "naturalFluencyScore": 78,
      "scenarioAdaptationScore": 80,
      "informationCompletenessScore": 86
    },
    "totalScore": 81.5,
    "overallComment": "整体意思基本准确，语法和用词可以再自然一些。",
    "comments": {
      "grammarComment": "句子结构基本正确，助词使用需要继续注意。",
      "vocabularyComment": "核心词汇能表达原意，但部分搭配不够自然。",
      "naturalnessComment": "表达可以理解，不过和日语母语者常用说法仍有距离。",
      "scenarioComment": "语气基本符合题目场景，敬体表达还可以更稳定。"
    },
    "errorAnalysis": [],
    "revisionSuggestions": [],
    "recommendedExpressions": [],
    "createdAt": "2026-07-31T16:00:00",
    "updatedAt": "2026-07-31T16:00:01"
  }
}
```

说明：

- `questionId` 必须大于 0。
- `answerText` 必填，最大长度 2000。
- 当前使用本地默认用户 `LOCAL_DEFAULT`。
- 评分成功时保存为 `REVIEWED`。
- AI 评分失败时保存为 `FAILED`，接口返回统一错误响应。

## 完整 System Prompt

```text
你是一个日语学习评分助手，服务对象是中文母语者。

你的任务是根据中文原文、语境、标准答案和用户提交的日语答案，对“中文 → 日语”翻译练习进行评分和纠错。

必须严格遵守以下规则：

1. 只处理题目类型为 TRANSLATION_ZH_TO_JA 的中译日答案。
2. 只返回合法 JSON，不要返回 Markdown、代码块标记、解释文字、注释或多余前后缀。
3. JSON 顶层必须是一个对象，且只包含 review 字段。
4. review 必须包含 scores、totalScore、overallComment、comments、errorAnalysis、revisionSuggestions、recommendedExpressions 字段。
5. 所有分数必须是 0 到 100 的整数。
6. scores 必须包含 grammarVocabularyScore、naturalFluencyScore、scenarioAdaptationScore、informationCompletenessScore。
7. totalScore 必须等于四项评分的算术平均值，保留 2 位小数。
8. 评分必须以中文原文含义、语境、JLPT 等级、难度、标准答案和用户答案为依据。
9. 不要求用户答案与标准答案完全一致；语义正确、自然且符合语境的表达可以得高分。
10. 如果用户答案语义正确但表达不够自然，应在自然度或场景适配上扣分。
11. 如果用户答案语义缺失、添加了原文没有的信息或误解原文，应在表达完整性上扣分。
12. 如果敬语、语气、商务/口语场景不匹配，应在场景适配上扣分。
13. 错误分析必须具体指出问题，不要只写“有语法错误”这类空泛描述。
14. 修改建议应给出可执行的改法。
15. 推荐表达必须是自然日语，可以包含标准答案或更适合语境的表达。
16. 不要编造题目中不存在的背景信息。
17. 不要输出 JSON 契约之外的字段。
18. 输出前自行检查 JSON 是否可解析、字段是否完整、分数是否在范围内。
```

## 完整 User Prompt 模板

```text
请评分下面这道中译日练习。

【题目信息】
- 题目类型：{{questionType}}
- 中文原文：{{sourceText}}
- 语境说明：{{contextText}}
- JLPT 等级：{{level}}
- 难度：{{difficulty}}
- 语法点：{{grammarPoint}}
- 是否口语：{{spoken}}
- 是否商务：{{business}}
- 是否考试：{{exam}}

【题目标签】
{{tagsJson}}

【标准答案和参考答案】
{{standardAnswersJson}}

【用户答案】
{{userAnswer}}

【输出要求】
只返回合法 JSON。
不要返回 Markdown。
不要使用代码块。
不要添加解释。
不要添加 JSON 之外的任何文字。

【JSON 结构】
{
  "review": {
    "scores": {
      "grammarVocabularyScore": 0,
      "naturalFluencyScore": 0,
      "scenarioAdaptationScore": 0,
      "informationCompletenessScore": 0
    },
    "totalScore": 0.00,
    "overallComment": "中文总评",
    "comments": {
      "grammarComment": "中文语法评价",
      "vocabularyComment": "中文词汇评价",
      "naturalnessComment": "中文自然度评价",
      "scenarioComment": "中文敬语与场景适配评价"
    },
    "errorAnalysis": [
      {
        "type": "GRAMMAR",
        "original": "用户答案中的问题片段",
        "issue": "中文说明具体问题",
        "suggestion": "中文说明如何修改",
        "severity": "MEDIUM"
      }
    ],
    "revisionSuggestions": [
      "中文修改建议"
    ],
    "recommendedExpressions": [
      {
        "expression": "自然日语表达",
        "usage": "中文说明适用场景",
        "formality": "POLITE",
        "note": "中文补充说明"
      }
    ]
  }
}

【枚举规则】
1. errorAnalysis.type 只能是 GRAMMAR、VOCABULARY、NATURALNESS、HONORIFIC、SCENARIO、COMPLETENESS。
2. errorAnalysis.severity 只能是 LOW、MEDIUM、HIGH。
3. recommendedExpressions.formality 只能是 CASUAL、NEUTRAL、POLITE、BUSINESS。
```

## JSON 输出契约

```json
{
  "review": {
    "scores": {
      "grammarVocabularyScore": 82,
      "naturalFluencyScore": 78,
      "scenarioAdaptationScore": 80,
      "informationCompletenessScore": 86
    },
    "totalScore": 81.5,
    "overallComment": "整体意思基本准确，语法和用词可以再自然一些。",
    "comments": {
      "grammarComment": "句子结构基本正确，助词使用需要继续注意。",
      "vocabularyComment": "核心词汇能表达原意，但部分搭配不够自然。",
      "naturalnessComment": "表达可以理解，不过和日语母语者常用说法仍有距离。",
      "scenarioComment": "语气基本符合题目场景，敬体表达还可以更稳定。"
    },
    "errorAnalysis": [
      {
        "type": "NATURALNESS",
        "original": "銀行に送金をしに行きます",
        "issue": "表达可以理解，但「振り込みに行く」更符合日常自然说法。",
        "suggestion": "将「送金をしに行く」改为「振り込みに行く」。",
        "severity": "MEDIUM"
      }
    ],
    "revisionSuggestions": [
      "保留时间表达，再用更自然的动词搭配表达转账行为。"
    ],
    "recommendedExpressions": [
      {
        "expression": "今日の午後、銀行へ振り込みに行きます。",
        "usage": "适合说明今天下午去银行办理转账的计划。",
        "formality": "POLITE",
        "note": "比直译式表达更自然。"
      }
    ]
  }
}
```

## 字段映射

| JSON 字段 | 保存位置 | 说明 |
| --- | --- | --- |
| `scores.grammarVocabularyScore` | `user_answers.grammar_vocabulary_score` | 语法与词汇评分 |
| `scores.naturalFluencyScore` | `user_answers.natural_fluency_score` | 自然度与流畅度评分 |
| `scores.scenarioAdaptationScore` | `user_answers.scenario_adaptation_score` | 敬语与场景适配评分 |
| `scores.informationCompletenessScore` | `user_answers.information_completeness_score` | 表达完整性评分 |
| `totalScore` | `user_answers.total_score` | 后端重新计算后保存 |
| `overallComment` | `user_answers.ai_overall_comment` | AI 总评 |
| `comments` | 不保存 | 仅接口响应返回 |
| `errorAnalysis` | 不保存 | 仅接口响应返回 |
| `revisionSuggestions` | 不保存 | 仅接口响应返回 |
| `recommendedExpressions` | 不保存 | 仅接口响应返回 |

## 后端校验规则

- AI 输出不能为空。
- AI 输出必须是合法 JSON。
- JSON 顶层必须是对象，且只能包含 `review` 字段。
- `review` 必须是对象。
- 四项评分必须存在，且必须是 0 到 100 的整数。
- `totalScore` 必须存在，且必须在 0 到 100 之间。
- 后端保存前必须重新计算 `totalScore`，计算方式为四项评分的算术平均值，保留 2 位小数。
- `overallComment` 必填。
- `comments` 必填，四个评价字段都不能为空。
- `errorAnalysis`、`revisionSuggestions`、`recommendedExpressions` 必须是数组，可以为空。
- `errorAnalysis.type`、`errorAnalysis.severity`、`recommendedExpressions.formality` 必须符合枚举规则。
- 评分成功后 `user_answers.answer_status` 更新为 `REVIEWED`。
- AI 评分输出非法时，已保存的用户答案状态更新为 `FAILED`。

## Mock 成功样例

```json
{
  "review": {
    "scores": {
      "grammarVocabularyScore": 82,
      "naturalFluencyScore": 78,
      "scenarioAdaptationScore": 80,
      "informationCompletenessScore": 86
    },
    "totalScore": 81.5,
    "overallComment": "整体意思基本准确，语法和用词可以再自然一些。",
    "comments": {
      "grammarComment": "句子结构基本正确，助词使用需要继续注意。",
      "vocabularyComment": "核心词汇能表达原意，但部分搭配不够自然。",
      "naturalnessComment": "表达可以理解，不过和日语母语者常用说法仍有距离。",
      "scenarioComment": "语气基本符合题目场景，敬体表达还可以更稳定。"
    },
    "errorAnalysis": [
      {
        "type": "NATURALNESS",
        "original": "今日の午後、銀行に送金をしに行きます。",
        "issue": "表达能传达大意，但整体不够像自然日语。",
        "suggestion": "参考标准答案调整助词和动词搭配。",
        "severity": "MEDIUM"
      }
    ],
    "revisionSuggestions": [
      "先确认中文原文中的时间、动作和对象是否完整保留。",
      "优先使用标准答案中的自然搭配，再替换成自己的表达。"
    ],
    "recommendedExpressions": [
      {
        "expression": "今日の午後、銀行へ振り込みに行きます。",
        "usage": "适合本题语境的基础推荐表达。",
        "formality": "POLITE",
        "note": "可以作为当前题目的优先记忆表达。"
      }
    ]
  }
}
```

## 异常样例

### 非 JSON 输出

```text
这句答案大致正确，但可以更自然。
```

处理方式：解析失败，用户答案状态更新为 `FAILED`。

### 非法分数

```json
{
  "review": {
    "scores": {
      "grammarVocabularyScore": 120,
      "naturalFluencyScore": 78,
      "scenarioAdaptationScore": 80,
      "informationCompletenessScore": 86
    },
    "totalScore": 91,
    "overallComment": "总评",
    "comments": {
      "grammarComment": "语法评价",
      "vocabularyComment": "词汇评价",
      "naturalnessComment": "自然度评价",
      "scenarioComment": "场景评价"
    },
    "errorAnalysis": [],
    "revisionSuggestions": [],
    "recommendedExpressions": []
  }
}
```

处理方式：校验失败，用户答案状态更新为 `FAILED`，提示评分字段不合法。
