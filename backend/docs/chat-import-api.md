# 聊天导入后端接口

所有接口位于 `/api` 下，返回现有 `{code,message,data}` 格式。校验和错误提示统一为中文。

## 分析

`POST /api/chat-imports/analyses?learningMode=ZH_TO_JA`

- Content-Type：`text/markdown` 或 `application/octet-stream`，请求体直接传文件字节，不用 multipart。
- UTF-8，可带 BOM。文件最多 131072 字节。
- 角色标题使用 `## User` / `## Assistant`（不区分大小写，可缩进 0～3 个空格）。首个角色标题之前可有总标题、分隔线、导出说明等，这些前置内容会被忽略。代码围栏中的角色标题不会分割消息，也不会将前置格式示例识别为聊天。
- 正文按所有消息内容合计，LF 规范化后最多 30000 个 Unicode 码点，空白也计数；前置内容和角色标题不计入正文。聊天开始后的分隔线等内容按原文保留。
- `learningMode` 为 `ZH_TO_JA`（默认）或 `EN_TO_JA`。
- 一次模型请求完成分析，不自动重试、不切换模型、不分段、不截断。失败由调用方决定是否重新分析。
- 使用共享 AI 请求超时，默认 300 秒（5 分钟），可由 `AI_REQUEST_TIMEOUT` 覆盖；修改配置后需重启后端。评估脚本等待 360 秒，避免先于默认后端超时结束。
- 返回 `checkedUserMessageIds` 和 `candidates`。候选包含 `errorTypeId,name,description,severity,evidence,correctedExpression,reviewSourceText,referenceAnswer`；引用包含 `messageId,originalText`。消息 ID 从 1 开始，包含助手消息的位置。
- 分析不保存聊天或候选。Mock provider 返回完整检查编号和空候选，不模拟真实纠错能力。

### 收录边界

- 检查用户自己使用的全部日语，包括疑问句。一条消息里的多个独立问题分别识别；同一具体学习重点跨消息合并并保留各处引用。
- 收录语法、词义、搭配、表记错误，以及在当前语境下有明确依据的明显不自然表达；不设最低候选数量。
- 引用、教学例句、询问用法时举出的片段不能直接视为用户用错；同一消息中用户自己的表达仍须检查。
- 正常口语省略、缩约、语气、个人风格和无依据的译名差异不作为错误。对小说内容的观点不属于语言纠错。
- 助手先前的纠正仅供参考，必须独立复核。多义词的原用法可以成立时不能判错，解释也不能把有例外的规则绝对化。
- 修正保留原意，复习题需脱离原聊天也能理解；涉及人物表记时提供必要提示，避免考查小说知识。

`checkedUserMessageIds` 只校验模型返回的用户消息编号是否齐全，不证明每个语言问题都被发现。
后端校验 JSON、分类、原文引用和题面格式，不代替语言正确性与覆盖率的人工评估。

模型内部响应使用 `messageChecks` 和 `candidates`：每条用户消息按原顺序列出 `messageId` 和 `findings`，
每个发现包含逐字摘录的 `originalText` 与从 0 开始的 `candidateIndex`。没有可收录问题的消息也需返回空 `findings`。
后端检查发现清单和候选引用双向对应，拒绝漏掉消息、发现未进入候选、索引越界或候选缺少对应检查记录的结果。
对外仍返回原有 `checkedUserMessageIds` 和 `candidates`，不暴露或保存内部检查清单，不修改确认接口。
这些校验约束的是模型输出的一致性，不能判断“模型是否把本来有错的消息标成了无问题”。

分析开始和完成日志只记录 `promptVersion`、用户消息数或候选数，不记录聊天正文或分析内容。
分析结果校验失败时，接口提示包含具体规则及候选序号、消息编号或引用序号，例如
“聊天分析结果不完整或不合法：第 1 个候选的第 1 条引用与用户消息 #3 的原文不一致，请重新分析”。
后端以 `聊天分析结果校验失败: reason=...` 记录相同原因，不记录原文、候选内容或解析器异常详情。
JSON 格式或字段类型错误单独提示；意外的内部异常仅额外记录异常类型，避免把内部错误解释成模型输出错误。
校验规则及失败时整体拒绝的行为不变，错误码仍为原有业务错误码，不触发自动重试。
本版提示词日志标识为 `message-checks-v1`；排查效果时可据此确认请求是否经过新版分析逻辑。
提示词通过少量多义词和搭配正用例校准判断边界，不使用程序中的词语黑名单过滤候选。

## 确认

`POST /api/chat-imports/confirmations`，Content-Type：`application/json`。

```json
{
  "learningMode": "ZH_TO_JA",
  "items": [{
    "mode": "NEW_USER_ERROR_TYPE",
    "errorTypeId": 1,
    "name": "〜前に的接续",
    "description": "动词接前に时使用辞书形",
    "severity": "MEDIUM",
    "reviewSourceText": "来日本之前，我学过日语。",
    "referenceAnswer": "日本に来る前に、日本語を勉強しました。"
  }]
}
```

分类 ID 必须来自实际启用的适用叶子分类，示例 ID 不保证存在。
追加已有卡片使用 `EXISTING_USER_ERROR_TYPE` 和 `userErrorTypeId`；已有卡片名称、分类、说明不被覆盖。
新建模式同名重点按现有规则复用。每次最多确认 200 个候选。

返回 `cards`，每项包含 `cardId,userErrorTypeId,questionIds,action`，action 为 `CREATED/UPDATED/REACTIVATED`。
同次提交每张卡只计一次失败，严重程度取最高；同题面及答案在当前提交内复用题目。
追加当前周期或重启已掌握卡片，遵循现有失败调度。新建卡片按现有规则周期失败数为 0，但首次错误事件和 SM-2 lapse 会记录。

不创建作答、聊天错误或批次记录。不提供跨请求幂等性；重复确认会再次生成题目并更新失败进度。
确认接口不要由客户端自动重试；网络结果不确定时应先检查卡片状态，再由用户决定是否重提。
导入事件来源为 `CHAT_IMPORT`，`user_answer_id` 为空。实际复习作答仍产生正常作答记录。
题目来源为 `REVIEW_DERIVED`，本次不触发 AI 标签补全。现有复习历史仅显示 `REVIEW` 作答。

## 数据库及验证

`src/main/resources/db/schema.sql` 是最终结构。现有数据库约束已按同一结构直接调整，不提供升级脚本。
运行常规测试：`./mvnw.cmd test`。
可选数据库集成测试 `ChatImportDatabaseTest` 使用 `CHAT_IMPORT_TEST_DB_URL`、`CHAT_IMPORT_TEST_DB_USER`、`CHAT_IMPORT_TEST_DB_PASSWORD`，目标库需有最终约束和基础用户/分类数据。测试结束回滚业务数据，不执行 DDL；数据库序列可能正常前进。

### 真实模型效果评估

先确认后端已重启或加载最新代码，并使用真实 AI provider。以下命令在 `backend` 目录的 PowerShell 7 中执行，无新增依赖。

```powershell
# 只读取和校验样例，不调用 API。
.\scripts\Evaluate-ChatImport.ps1 -ValidateOnly

# 默认评估全部 8 个样例；也可指定少量样例。
.\scripts\Evaluate-ChatImport.ps1 -CaseId japanese-question,assistant-misdiagnosis,castle-naturalness

# 评估本地真实聊天，文件不复制进项目。
.\scripts\Evaluate-ChatImport.ps1 -MarkdownPath 'C:\path\conversation.md'
```

样例及人工核对项位于 `src/test/resources/chat-import/evaluation-cases.json`，分为必须识别、可选自然度建议和不应判错。
覆盖疑问句、多错误、重复引用、助手误判、多义词、正常口语、引用归属、自然度和上下文表记。
每个样例仅调用一次分析接口，不自动重试，不调用确认接口，不写入题目或复习数据。
默认报告为 `target/chat-import-evaluation.json`，可用 `-OutputPath` 指定路径；报告包含分析候选和人工核对项，不包含完整输入聊天。
返回成功时仍标记为 `PENDING_MANUAL_REVIEW`，请求失败为 `REQUEST_FAILED`，不会把候选数量或结构校验通过当作语言效果通过。
复核时按学习重点和引用判断，不要求标题逐字一致；记录明确遗漏、误报、无依据的改写和题目质量，再决定是否需要调整提示词。
普通单元测试使用模拟模型响应，只验证请求、校验和业务行为，不证明真实模型的语言判断正确。
