# AI 作答评分与错误确认

## 评分接口

```text
POST /api/questions/{questionId}/answers
```

评分服务会从 `error_types` 加载全部启用且未删除的二级错误类型，并将其作为 `errorTypeOptions` 传给 AI。每项包含：

```json
{
  "id": 7,
  "code": "PARTICLE",
  "name": "助词错误",
  "description": "助词选择或用法不正确",
  "parentCode": "GRAMMAR_SYNTAX",
  "parentName": "语法与句法"
}
```

AI 必须仅使用其中的 `code`，并在 `errorAnalysis` 中返回：

```json
{
  "errorTypeCode": "PARTICLE",
  "original": "公園で散歩します",
  "issue": "表示移动场所时助词使用错误。",
  "suggestion": "改为公園を散歩します。",
  "severity": "MEDIUM",
  "suggestedUserErrorTypeName": "散步时移动场所助词误用で而非を",
  "suggestedUserErrorTypeDescription": "以移动方式进行活动「散歩する」时，地点使用を，不能使用で。"
}
```

## 评分边界

- `grammarPoint` 是学习参考，不是必须使用的句型。用户采用其他自然、达意且符合语境的表达时，不能仅因没有使用该语法点扣分、列错或降低信息完整性。
- 日常口语中自然省略的主语、话题、已知宾语，以及随之省略的助词，不属于漏译或助词错误。只有省略造成关键信息缺失、语义歧义或场景不成立时，才可作为漏译分析。
- `suggestedUserErrorTypeName` 必须是可复用的具体模式，包含关键对象和误用方向或适用场景。例如“赶上交通工具时误用を而非に”，不能使用“助词错误”“词汇错误”“句型错误”“不自然表达”等泛称。
- `suggestedUserErrorTypeDescription` 说明触发情形、错误形式与正确用法，便于用户确认后复习。

服务端会校验错误类型编码、错误片段、严重程度、重复项及建议的用户错误类型名称和描述。评分阶段只保存 `user_answers` 的评分结果；`errorAnalysis` 是候选错误，不会直接写入错误记录表。

## 将候选错误加入复习卡片

```text
POST /api/user-answers/{userAnswerId}/errors
```

请求可一次确认多条错误。`mode` 为 `NEW_USER_ERROR_TYPE` 时创建或复用一张复习卡片；为 `EXISTING_USER_ERROR_TYPE` 时追加到已有复习卡片。底层字段名称保持兼容。

```json
{
  "errors": [
    {
      "mode": "NEW_USER_ERROR_TYPE",
      "errorTypeId": 7,
      "userErrorTypeName": "散步时移动场所助词误用で而非を",
      "userErrorTypeDescription": "以移动方式进行活动「散歩する」时，地点使用を，不能使用で。",
      "originalText": "公園で散歩します",
      "issue": "表示移动场所时助词使用错误。",
      "suggestion": "公園を散歩します。",
      "severity": "MEDIUM",
      "sortOrder": 0
    }
  ]
}
```

服务端仅允许当前用户确认自己的 `REVIEWED` 作答，并从该作答填充 `user_id`、`question_id`。所有确认项在同一事务内写入 `user_answer_errors`；新建卡片时同时写入 `user_error_types`。未确认的候选错误不会参与后续统计。

## 手动添加复习卡片

```text
POST /api/user-answers/{userAnswerId}/review-cards
```

```json
{
  "name": "练习更自然的移动表达",
  "targetExpression": "明日の午後、公園を散歩します。",
  "sourceSegmentIndex": 0,
  "reviewSourceText": null
}
```

短句和复习评分自动使用当前中文题面；文章必须通过 `sourceSegmentIndex` 选择中文原句；纯日语纠错不提交索引，而是通过 `reviewSourceText` 提供中文复习题面。服务端创建独立复习卡片和 `REVIEW_DERIVED` 短句题，目标表达作为标准答案。

成功响应中的 `data`：

```json
{
  "id": 12,
  "name": "练习更自然的移动表达",
  "status": "ACTIVE",
  "dueAt": "2026-08-18T07:00:00"
}
```

`name` 最长 128 字符，`targetExpression` 最长 2000 字符且必须包含日语假名。纯日语纠错的 `reviewSourceText` 最长 1000 字符，必须包含汉字且不能包含日语假名；不适用于当前题型的字段必须省略。

## 已有复习卡片查询

```text
GET /api/user-error-types?status=ACTIVE&page=1&size=20
```

默认查询当前用户状态为 `ACTIVE` 的底层用户错误类型，前端将其作为已有复习卡片供候选确认时选择。
