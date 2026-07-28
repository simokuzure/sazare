# 题目相关 API 设计

## 目标

基于当前数据库表和生成题目 Prompt 需求，先定义 MVP 阶段题目相关 REST API。

本设计覆盖：

- 标签查询
- AI 生成题目并入库
- 人工创建题目
- 题目列表查询
- 题目详情查询
- 题目启用、停用、删除

本设计暂不覆盖：

- 用户答题
- AI 评分
- 错题记录
- 复习计划

这些能力应在答题、评分、复习 API 设计中单独定义，避免题目接口承担过多职责。

## 通用约定

### Base URL

```text
/api
```

### 统一响应格式

所有接口统一使用现有 `ApiResponse<T>` 结构。

成功：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

失败：

```json
{
  "code": 40001,
  "message": "参数错误",
  "data": null
}
```

### 错误码

| code | message | 使用场景 |
| --- | --- | --- |
| `40001` | `参数错误` | 请求字段缺失、枚举非法、分页参数非法、标签 code 不存在 |
| `40002` | `业务处理失败` | AI 输出不合法、题目不存在、题目已删除、主标准答案规则冲突 |
| `50000` | `系统异常` | 未预期异常 |

### 枚举值

| 枚举 | 可选值 |
| --- | --- |
| `questionType` | `TRANSLATION_ZH_TO_JA` |
| `level` | `N5`、`N4`、`N3`、`N2`、`N1` |
| `difficulty` | `1` 到 `5` |
| `sourceType` | `AI`、`MANUAL` |
| `answerType` | `STANDARD`、`REFERENCE` |
| `tagType` | `SCENE`、`FUNCTION` |

## 数据对象

### TagVO

```json
{
  "id": 1,
  "tagType": "SCENE",
  "parentId": null,
  "code": "FINANCE_BANK",
  "name": "银行",
  "description": "金融场景标签",
  "sortOrder": 12010
}
```

字段来源：`tags`。

### QuestionAnswerVO

```json
{
  "id": 1,
  "answerText": "今日の午後、銀行へ振り込みに行きます。",
  "answerType": "STANDARD",
  "primaryAnswer": true,
  "sortOrder": 0
}
```

字段来源：`question_answers`。

### QuestionVO

```json
{
  "id": 1,
  "questionType": "TRANSLATION_ZH_TO_JA",
  "sourceText": "我今天下午要去银行办理转账。",
  "contextText": "日常生活中说明下午的计划。",
  "level": "N4",
  "difficulty": 3,
  "grammarPoint": "予定を表す表現",
  "spoken": true,
  "business": false,
  "exam": false,
  "sourceType": "AI",
  "enabled": true,
  "tags": [
    {
      "id": 1,
      "tagType": "SCENE",
      "parentId": 12,
      "code": "FINANCE_BANK",
      "name": "银行",
      "description": "金融场景标签",
      "sortOrder": 12010
    }
  ],
  "answers": [
    {
      "id": 1,
      "answerText": "今日の午後、銀行へ振り込みに行きます。",
      "answerType": "STANDARD",
      "primaryAnswer": true,
      "sortOrder": 0
    }
  ],
  "createdAt": "2026-07-27T19:30:00",
  "updatedAt": "2026-07-27T19:30:00"
}
```

字段来源：`questions`、`question_answers`、`question_tags`、`tags`。

### PageVO

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "total": 100
}
```

MVP 阶段使用 `page` 从 `1` 开始，`size` 默认 `20`，最大 `100`。

## 标签接口

### 查询标签列表

```http
GET /api/tags
```

用于前端筛选条件、人工录入题目、AI 生成题目时选择标签候选。

请求参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `tagType` | string | 否 | 无 | `SCENE` 或 `FUNCTION` |
| `parentId` | long | 否 | 无 | 查询指定父级下的标签 |
| `enabledOnly` | boolean | 否 | `true` | 是否只返回启用且未删除标签 |
| `page` | integer | 否 | `1` | 页码，从 `1` 开始 |
| `size` | integer | 否 | `20` | 每页数量，最大 `100` |

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "id": 1,
        "tagType": "SCENE",
        "parentId": null,
        "code": "FINANCE",
        "name": "金融",
        "description": "场景一级标签",
        "sortOrder": 12000
      }
    ],
    "page": 1,
    "size": 20,
    "total": 1
  }
}
```

实现要求：

- 默认只返回 `enabled = true` 且 `deleted = false` 的标签。
- 排序按 `sort_order ASC, id ASC`。
- 不返回已删除标签。
- 列表查询默认分页；除非后续需求明确说明不分页，否则列表接口都应返回 `PageVO` 结构。

## 题目接口

### AI 生成题目并入库

```http
POST /api/questions/ai-generations
```

用于调用 AI 生成题目。MVP 阶段先走 mock，仍按真实 AI 输出规则解析、校验、保存。

请求：

```json
{
  "questionCount": 3,
  "level": "N4",
  "difficulty": 3,
  "sceneTagCodes": ["DAILY_LIFE_WEATHER", "EDUCATION_SELF_STUDY"],
  "functionTagCodes": ["FUNCTION_PROPOSE_PLAN"],
  "excludedSourceTexts": [
    "如果明天下雨，我们就在家学习吧。"
  ],
  "extraRequirements": "偏口语"
}
```

请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `questionCount` | integer | 是 | 生成数量，范围 `1` 到 `5` |
| `level` | string | 是 | JLPT 等级 |
| `difficulty` | integer | 是 | 难度，范围 `1` 到 `5` |
| `sceneTagCodes` | array | 否 | 限定 AI 可选场景标签；为空时使用全部启用场景标签 |
| `functionTagCodes` | array | 否 | 限定 AI 可选功能标签；为空时使用全部启用功能标签 |
| `excludedSourceTexts` | array | 否 | 避免重复生成的中文原文 |
| `extraRequirements` | string | 否 | 额外要求 |

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "questionType": "TRANSLATION_ZH_TO_JA",
      "sourceText": "如果明天下雨，我们就在家学习吧。",
      "contextText": "朋友之间讨论明天的安排。",
      "level": "N4",
      "difficulty": 3,
      "grammarPoint": "条件表現「たら」",
      "spoken": true,
      "business": false,
      "exam": false,
      "sourceType": "AI",
      "enabled": true,
      "tags": [
        {
          "id": 1,
          "tagType": "SCENE",
          "parentId": 3,
          "code": "DAILY_LIFE_WEATHER",
          "name": "天气",
          "description": "日常生活场景标签",
          "sortOrder": 1040
        }
      ],
      "answers": [
        {
          "id": 1,
          "answerText": "明日雨が降ったら、家で勉強しましょう。",
          "answerType": "STANDARD",
          "primaryAnswer": true,
          "sortOrder": 0
        }
      ],
      "createdAt": "2026-07-27T19:30:00",
      "updatedAt": "2026-07-27T19:30:00"
    }
  ]
}
```

实现要求：

- 保存前必须校验 AI 返回 JSON，规则以 `docs/ai-question-generation-prompt.md` 为准。
- AI 返回的 `tagCodes` 必须存在于启用且未删除标签中。
- 每道题必须至少包含 1 个场景标签。
- 每道题必须有且只有 1 个 `answerType = STANDARD` 且 `primaryAnswer = true` 的答案。
- 入库时 `questions.source_type` 固定为 `AI`。
- 建议使用事务保存 `questions`、`question_answers`、`question_tags`，任一题校验失败则本次请求整体失败。

### 人工创建题目

```http
POST /api/questions
```

用于人工录入题目。AI 生成题目优先使用 `POST /api/questions/ai-generations`。

请求：

```json
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
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "questionType": "TRANSLATION_ZH_TO_JA",
    "sourceText": "我今天下午要去银行办理转账。",
    "contextText": "日常生活中说明下午的计划。",
    "level": "N4",
    "difficulty": 3,
    "grammarPoint": "予定を表す表現",
    "spoken": true,
    "business": false,
    "exam": false,
    "sourceType": "MANUAL",
    "enabled": true,
    "tags": [
      {
        "id": 1,
        "tagType": "SCENE",
        "parentId": 12,
        "code": "FINANCE_BANK",
        "name": "银行",
        "description": "金融场景标签",
        "sortOrder": 12010
      }
    ],
    "answers": [
      {
        "id": 1,
        "answerText": "今日の午後、銀行へ振り込みに行きます。",
        "answerType": "STANDARD",
        "primaryAnswer": true,
        "sortOrder": 0
      }
    ],
    "createdAt": "2026-07-27T19:30:00",
    "updatedAt": "2026-07-27T19:30:00"
  }
}
```

实现要求：

- `questionType` 当前只允许 `TRANSLATION_ZH_TO_JA`。
- `sourceText` 必填，且应包含中文字符。
- `contextText` 建议必填；数据库允许为空，但业务上应要求填写，便于学习者理解语境。
- `level` 必填，且只能是 `N5`、`N4`、`N3`、`N2`、`N1`。
- `difficulty` 必填，范围 `1` 到 `5`。
- `tagCodes` 必填，且至少包含 1 个启用的场景标签。
- `answers` 必填，且必须包含 1 个主标准答案。
- 入库时 `questions.source_type` 固定为 `MANUAL`。

### 查询题目列表

```http
GET /api/questions
```

请求参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `questionType` | string | 否 | `TRANSLATION_ZH_TO_JA` | 题目类型 |
| `level` | string | 否 | 无 | JLPT 等级 |
| `difficulty` | integer | 否 | 无 | 难度 |
| `tagCodes` | string | 否 | 无 | 逗号分隔标签 code，任一匹配即可 |
| `spoken` | boolean | 否 | 无 | 是否口语 |
| `business` | boolean | 否 | 无 | 是否商务 |
| `exam` | boolean | 否 | 无 | 是否考试 |
| `sourceType` | string | 否 | 无 | `AI` 或 `MANUAL` |
| `enabled` | boolean | 否 | `true` | 是否启用 |
| `page` | integer | 否 | `1` | 页码 |
| `size` | integer | 否 | `20` | 每页数量，最大 `100` |

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "id": 1,
        "questionType": "TRANSLATION_ZH_TO_JA",
        "sourceText": "我今天下午要去银行办理转账。",
        "contextText": "日常生活中说明下午的计划。",
        "level": "N4",
        "difficulty": 3,
        "grammarPoint": "予定を表す表現",
        "spoken": true,
        "business": false,
        "exam": false,
        "sourceType": "AI",
        "enabled": true,
        "tags": [
          {
            "id": 1,
            "tagType": "SCENE",
            "parentId": 12,
            "code": "FINANCE_BANK",
            "name": "银行",
            "description": "金融场景标签",
            "sortOrder": 12010
          }
        ],
        "answers": [
          {
            "id": 1,
            "answerText": "今日の午後、銀行へ振り込みに行きます。",
            "answerType": "STANDARD",
            "primaryAnswer": true,
            "sortOrder": 0
          }
        ],
        "createdAt": "2026-07-27T19:30:00",
        "updatedAt": "2026-07-27T19:30:00"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 1
  }
}
```

实现要求：

- 默认只查 `deleted = false` 的题目。
- 默认只返回 `enabled = true` 的题目。
- 列表默认按 `created_at DESC, id DESC` 排序。
- MVP 阶段列表可以返回主标准答案和标签；若性能实现复杂，可只返回标签和主标准答案，不返回所有参考答案。

### 查询题目详情

```http
GET /api/questions/{id}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "questionType": "TRANSLATION_ZH_TO_JA",
    "sourceText": "我今天下午要去银行办理转账。",
    "contextText": "日常生活中说明下午的计划。",
    "level": "N4",
    "difficulty": 3,
    "grammarPoint": "予定を表す表現",
    "spoken": true,
    "business": false,
    "exam": false,
    "sourceType": "AI",
    "enabled": true,
    "tags": [
      {
        "id": 1,
        "tagType": "SCENE",
        "parentId": 12,
        "code": "FINANCE_BANK",
        "name": "银行",
        "description": "金融场景标签",
        "sortOrder": 12010
      }
    ],
    "answers": [
      {
        "id": 1,
        "answerText": "今日の午後、銀行へ振り込みに行きます。",
        "answerType": "STANDARD",
        "primaryAnswer": true,
        "sortOrder": 0
      },
      {
        "id": 2,
        "answerText": "今日の午後、銀行に振り込みをしに行きます。",
        "answerType": "REFERENCE",
        "primaryAnswer": false,
        "sortOrder": 1
      }
    ],
    "createdAt": "2026-07-27T19:30:00",
    "updatedAt": "2026-07-27T19:30:00"
  }
}
```

实现要求：

- `id` 不存在或题目已删除时返回 `40002`。
- `answers` 只返回 `deleted = false` 的答案。
- `answers` 按 `sort_order ASC, id ASC` 排序。
- `tags` 不返回已删除标签。

### 更新题目启用状态

```http
PATCH /api/questions/{id}/enabled
```

请求：

```json
{
  "enabled": false
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

实现要求：

- 只更新 `questions.enabled` 和 `questions.updated_at`。
- `id` 不存在或题目已删除时返回 `40002`。
- 停用题目不删除答案和标签关联。

### 删除题目

```http
DELETE /api/questions/{id}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

实现要求：

- 采用逻辑删除，更新 `questions.deleted = true`。
- 同步更新 `questions.enabled = false` 和 `questions.updated_at`。
- 不物理删除 `question_answers` 和 `question_tags`。
- `id` 不存在或已删除时返回 `40002`。

## 校验规则

### 题目校验

- `questionType` 必须是 `TRANSLATION_ZH_TO_JA`。
- `sourceText` 不能为空，且应包含中文字符。
- `contextText` 不能为空。
- `level` 必须是 `N5`、`N4`、`N3`、`N2`、`N1`。
- `difficulty` 必须是 `1` 到 `5`。
- `grammarPoint` 不能为空。
- `spoken`、`business`、`exam` 不能为空。
- `tagCodes` 至少包含 1 个启用且未删除的场景标签。
- `tagCodes` 中的每个 code 必须存在且启用、未删除。

### 答案校验

- `answers` 不能为空。
- `answerType` 只能是 `STANDARD` 或 `REFERENCE`。
- `answerText` 不能为空，且应包含日语假名或汉字。
- 必须有且只有 1 个 `answerType = STANDARD` 且 `primaryAnswer = true` 的答案。
- 主标准答案的 `sortOrder` 必须是 `0`。
- `REFERENCE` 答案的 `primaryAnswer` 必须是 `false`。
- 同一道题下启用答案文本不能重复。

## 入库映射

| 请求字段 | 数据库字段 |
| --- | --- |
| `questionType` | `questions.question_type` |
| `sourceText` | `questions.source_text` |
| `contextText` | `questions.context_text` |
| `level` | `questions.level` |
| `difficulty` | `questions.difficulty` |
| `grammarPoint` | `questions.grammar_point` |
| `spoken` | `questions.spoken` |
| `business` | `questions.business` |
| `exam` | `questions.exam` |
| `sourceType` | `questions.source_type` |
| `tagCodes` | `question_tags.question_id` + `question_tags.tag_id` |
| `answers.answerText` | `question_answers.answer_text` |
| `answers.answerType` | `question_answers.answer_type` |
| `answers.primaryAnswer` | `question_answers.primary_answer` |
| `answers.sortOrder` | `question_answers.sort_order` |

## 实现建议

- Controller 只负责参数接收、基础校验、调用 Service 和返回 `ApiResponse`。
- Service 负责业务校验、标签 code 查询、AI 输出校验和事务保存。
- Mapper 只做简单 SQL，不写业务判断。
- DTO 使用 `record`，VO 使用 `record` 或普通类均可，保持项目风格一致。
- AI 生成接口先接 mock 服务，但 mock 输出也必须走同一套 JSON 解析和校验逻辑。
- 建议先实现标签查询、人工创建题目、题目列表、题目详情，再实现 AI 生成题目接口。
