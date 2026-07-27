# 生成题目 AI Prompt 设计

## 目标

用于生成日语翻译练习题，当前只支持 `TRANSLATION_ZH_TO_JA`（中译日）。

本阶段只约定 Prompt 模板和 JSON 输出契约，不接入真实 AI API。后端实现时先使用 mock JSON 跑通流程，再通过配置切换到真实调用。

## 调用策略

- 默认策略：mock 优先。
- 真实 AI 输出不能直接入库，必须经过 JSON 解析、字段校验、标签校验和默认值处理。
- 标签必须来自后端提供的 `tags.code` 白名单，AI 不允许生成新标签。.\mvnw.cmd test
- 当前题目生成不处理去重，后续可在保存前增加文本相似度或 Embedding 检查。

## 输入参数

后端构造 Prompt 时传入以下参数：

| 参数 | 说明 |
| --- | --- |
| `questionCount` | 生成题目数量，建议 MVP 阶段为 1 到 5 |
| `level` | JLPT 等级，只允许 `N5`、`N4`、`N3`、`N2`、`N1` |
| `difficulty` | 难度，范围 1 到 5 |
| `sceneTagOptions` | 可选场景标签候选列表，来自 `tags` 表 |
| `functionTagOptions` | 可选功能标签候选列表，来自 `tags` 表 |
| `excludedSourceTexts` | 可选，需要避免重复生成的中文原文 |
| `extraRequirements` | 可选，额外限制，例如“偏口语”“商务场景”“考试表达” |

标签候选列表只需要提供 AI 选择所需字段：

```json
[
  {
    "code": "FINANCE_BANK",
    "name": "银行",
    "description": "金融场景标签"
  }
]
```

## 完整 System Prompt

```text
你是一个日语学习题目生成助手，服务对象是中文母语者。

你的任务是生成“中文 → 日语”的翻译练习题。

必须严格遵守以下规则：

1. 只生成题目类型为 TRANSLATION_ZH_TO_JA 的题目。
2. 只返回合法 JSON，不要返回 Markdown、代码块标记、解释文字、注释或多余前后缀。
3. JSON 顶层必须是一个对象，且只包含 questions 字段。
4. questions 必须是数组，数组长度必须等于用户要求的题目数量。
5. 每道题必须包含以下字段：
   - questionType
   - sourceText
   - contextText
   - level
   - difficulty
   - grammarPoint
   - spoken
   - business
   - exam
   - tagCodes
   - answers
6. questionType 必须固定为 TRANSLATION_ZH_TO_JA。
7. sourceText 必须是自然中文句子，适合作为中译日练习题。
8. sourceText 不要过长，N5/N4 建议 10 到 25 个汉字，N3/N2/N1 可以适当更长。
9. sourceText 不能包含日语原文、日语假名或明显提示答案的内容。
10. contextText 必须用中文描述题目的使用场景，帮助学习者理解语境。
11. level 只能使用用户指定的 JLPT 等级，不能自行更改。
12. difficulty 只能使用用户指定的难度，不能自行更改。
13. difficulty 必须是 1 到 5 的整数。
14. grammarPoint 用中文或日语简短说明本题重点语法或表达。
15. spoken、business、exam 必须是布尔值。
16. tagCodes 必须只从用户提供的 sceneTagOptions 和 functionTagOptions 中选择 code。
17. 不允许创造新的标签 code、标签名称、题目类型、答案类型或字段名。
18. tagCodes 至少包含 1 个场景标签 code，建议再包含 1 到 2 个功能标签 code。
19. answers 必须是数组，至少包含 1 个 STANDARD 标准答案。
20. 每道题必须有且只有 1 个答案同时满足 answerType = STANDARD 且 primaryAnswer = true。
21. 可以额外提供 0 到 2 个 REFERENCE 参考答案。
22. answerText 必须是自然、正确、符合语境的日语表达。
23. STANDARD 答案应优先选择最自然、最适合学习者掌握的表达。
24. REFERENCE 答案可以提供语义相近的自然表达，但不能明显偏离 sourceText。
25. sortOrder 从 0 开始，主标准答案必须为 0。
26. 如果用户提供 excludedSourceTexts，不要生成与其中任何一句语义高度相似的题目。
27. 如果用户提供 extraRequirements，必须在不违反以上规则的前提下满足。
28. 如果标签候选不足以准确覆盖题目，只能从已有候选中选择最接近的 code，不能编造。
29. 输出前自行检查 JSON 是否可解析、字段是否完整、枚举值是否合法。
```

## 完整 User Prompt 模板

```text
请生成日语中译日练习题。

【生成条件】
- 题目数量：{{questionCount}}
- 题目类型：TRANSLATION_ZH_TO_JA
- JLPT 等级：{{level}}
- 难度：{{difficulty}}
- 额外要求：{{extraRequirements}}

【可选场景标签】
只能从下面的 sceneTagOptions 中选择场景标签 code：
{{sceneTagOptionsJson}}

【可选功能标签】
只能从下面的 functionTagOptions 中选择功能标签 code：
{{functionTagOptionsJson}}

【需要避免重复的中文题目】
不要生成与下面任意一句语义高度相似的题目：
{{excludedSourceTextsJson}}

【输出要求】
只返回合法 JSON。
不要返回 Markdown。
不要使用代码块。
不要添加解释。
不要添加 JSON 之外的任何文字。

【JSON 结构】
{
  "questions": [
    {
      "questionType": "TRANSLATION_ZH_TO_JA",
      "sourceText": "中文题目原文",
      "contextText": "中文语境说明",
      "level": "{{level}}",
      "difficulty": {{difficulty}},
      "grammarPoint": "本题重点语法或表达",
      "spoken": true,
      "business": false,
      "exam": false,
      "tagCodes": ["只能使用上方标签候选中的code"],
      "answers": [
        {
          "answerText": "日语标准答案",
          "answerType": "STANDARD",
          "primaryAnswer": true,
          "sortOrder": 0
        }
      ]
    }
  ]
}

【字段规则】
1. questions 数组长度必须等于 {{questionCount}}。
2. questionType 固定为 TRANSLATION_ZH_TO_JA。
3. level 固定为 {{level}}。
4. difficulty 固定为 {{difficulty}}。
5. tagCodes 至少包含 1 个 sceneTagOptions 中的 code。
6. tagCodes 可以包含 1 到 2 个 functionTagOptions 中的 code。
7. tagCodes 不允许出现候选列表之外的 code。
8. answers 中必须有且只有 1 个 STANDARD 主答案。
9. STANDARD 主答案的 primaryAnswer 必须是 true，sortOrder 必须是 0。
10. REFERENCE 答案的 primaryAnswer 必须是 false。
```

## JSON 输出契约

```json
{
  "questions": [
    {
      "questionType": "TRANSLATION_ZH_TO_JA",
      "sourceText": "我今天下午要去银行办理转账。",
      "contextText": "日常生活中说明下午的计划。",
      "level": "N4",
      "difficulty": 3,
      "grammarPoint": "予定を表す表現",
      "spoken": true,
      "business": false,
      "exam": false,
      "tagCodes": ["FINANCE_BANK", "FINANCE_TRANSFER", "FUNCTION_EXPRESS_PLAN"],
      "answers": [
        {
          "answerText": "今日の午後、銀行へ振り込みに行きます。",
          "answerType": "STANDARD",
          "primaryAnswer": true,
          "sortOrder": 0
        },
        {
          "answerText": "今日の午後、銀行に振り込みをしに行きます。",
          "answerType": "REFERENCE",
          "primaryAnswer": false,
          "sortOrder": 1
        }
      ]
    }
  ]
}
```

## 字段映射

| JSON 字段 | 保存位置 | 说明 |
| --- | --- | --- |
| `questionType` | `questions.question_type` | 当前固定为 `TRANSLATION_ZH_TO_JA` |
| `sourceText` | `questions.source_text` | 中文题目原文 |
| `contextText` | `questions.context_text` | 语境说明 |
| `level` | `questions.level` | JLPT 等级 |
| `difficulty` | `questions.difficulty` | 难度 1 到 5 |
| `grammarPoint` | `questions.grammar_point` | 语法点说明 |
| `spoken` | `questions.spoken` | 是否口语 |
| `business` | `questions.business` | 是否商务 |
| `exam` | `questions.exam` | 是否考试 |
| `tagCodes` | `question_tags` | 根据 `tags.code` 查询 `tags.id` 后保存 |
| `answers` | `question_answers` | 标准答案和参考答案 |

## 后端校验规则

- `questions` 不能为空，数量必须等于请求的 `questionCount`。
- `questionType` 必须等于 `TRANSLATION_ZH_TO_JA`。
- `sourceText` 不能为空，且应包含中文字符。
- `contextText` 不能为空。
- `level` 必须是 `N5`、`N4`、`N3`、`N2`、`N1`。
- `difficulty` 必须是 1 到 5 的整数。
- `grammarPoint` 不能为空。
- `spoken`、`business`、`exam` 必须是布尔值。
- `tagCodes` 不能为空，且每个 code 必须存在于启用且未删除的标签中。
- `answers` 不能为空。
- 每道题必须有且只有一个 `answerType = STANDARD` 且 `primaryAnswer = true` 的答案。
- `answerType` 只能是 `STANDARD` 或 `REFERENCE`。
- `answerText` 不能为空，且应包含日语假名或汉字。
- `sortOrder` 从 0 开始，主标准答案固定为 0。

## Mock 成功样例

```json
{
  "questions": [
    {
      "questionType": "TRANSLATION_ZH_TO_JA",
      "sourceText": "如果明天下雨，我们就在家学习吧。",
      "contextText": "朋友之间讨论明天的安排。",
      "level": "N4",
      "difficulty": 3,
      "grammarPoint": "条件表現「たら」",
      "spoken": true,
      "business": false,
      "exam": false,
      "tagCodes": ["DAILY_LIFE_WEATHER", "EDUCATION_SELF_STUDY", "FUNCTION_PROPOSE_PLAN"],
      "answers": [
        {
          "answerText": "明日雨が降ったら、家で勉強しましょう。",
          "answerType": "STANDARD",
          "primaryAnswer": true,
          "sortOrder": 0
        },
        {
          "answerText": "もし明日雨なら、家で勉強しましょう。",
          "answerType": "REFERENCE",
          "primaryAnswer": false,
          "sortOrder": 1
        }
      ]
    }
  ]
}
```

## 异常样例

### 非 JSON 输出

```text
好的，下面是生成的题目：
...
```

处理方式：解析失败，不保存题目，返回参数或 AI 输出格式错误。

### 非法等级

```json
{
  "questions": [
    {
      "questionType": "TRANSLATION_ZH_TO_JA",
      "sourceText": "我想预约明天的会议。",
      "contextText": "商务预约场景。",
      "level": "N6",
      "difficulty": 3,
      "grammarPoint": "予約表現",
      "spoken": false,
      "business": true,
      "exam": false,
      "tagCodes": ["BUSINESS_MEETING"],
      "answers": [
        {
          "answerText": "明日の会議を予約したいです。",
          "answerType": "STANDARD",
          "primaryAnswer": true,
          "sortOrder": 0
        }
      ]
    }
  ]
}
```

处理方式：校验失败，不保存题目，提示 `level` 不合法。

### 标签不存在

```json
{
  "questions": [
    {
      "questionType": "TRANSLATION_ZH_TO_JA",
      "sourceText": "请告诉我车站在哪里。",
      "contextText": "问路场景。",
      "level": "N5",
      "difficulty": 2,
      "grammarPoint": "場所を尋ねる表現",
      "spoken": true,
      "business": false,
      "exam": false,
      "tagCodes": ["UNKNOWN_TAG"],
      "answers": [
        {
          "answerText": "駅はどこですか。",
          "answerType": "STANDARD",
          "primaryAnswer": true,
          "sortOrder": 0
        }
      ]
    }
  ]
}
```

处理方式：校验失败，不保存题目，提示存在非法标签 code。

### 没有主标准答案

```json
{
  "questions": [
    {
      "questionType": "TRANSLATION_ZH_TO_JA",
      "sourceText": "我昨天买了一本日语书。",
      "contextText": "说明过去发生的事情。",
      "level": "N5",
      "difficulty": 2,
      "grammarPoint": "過去形",
      "spoken": true,
      "business": false,
      "exam": false,
      "tagCodes": ["SHOPPING_PRODUCT_SELECTION", "FUNCTION_STATE_FACT"],
      "answers": [
        {
          "answerText": "昨日、日本語の本を買いました。",
          "answerType": "STANDARD",
          "primaryAnswer": false,
          "sortOrder": 0
        }
      ]
    }
  ]
}
```

处理方式：校验失败，不保存题目，提示缺少主标准答案。
