# TODO

更新时间：2026-07-31

## 已完成
- [x] 补充项目开发启动说明，包括 Docker、后端、前端的启动与停止命令。
- [x] 确定生成题目 AI 调用策略：支持 mock 与 Google AI provider 配置切换。
- [x] 设计生成题目 AI 固定 Prompt 模板。
- [x] 约定生成题目 AI JSON 输出契约和后端校验规则。
- [x] 初始化 `backend + frontend` 单仓库结构。
- [x] 初始化后端 Spring Boot 项目，并使用 Maven Wrapper。
- [x] 后端包名确定为 `com.jt.learning`。
- [x] 建立后端基础分层目录：`controller`、`service`、`service.impl`、`mapper`、`entity`、`dto`、`vo`、`config`、`exception`、`common`、`util`。
- [x] 实现统一响应结构 `ApiResponse<T>`。
- [x] 实现业务异常 `BusinessException` 和错误码 `ErrorCode`。
- [x] 实现全局异常处理 `GlobalExceptionHandler`。
- [x] 实现健康检查接口 `GET /api/health`。
- [x] 配置 `docker-compose.yml`，使用 `postgres:16-alpine` 和 `redis:7-alpine`。
- [x] 初始化前端 Vite + React + TypeScript 项目。
- [x] 配置前端开发代理，将 `/api` 转发到 `http://localhost:8080`。
- [x] 前端页面已接入后端健康检查接口。
- [x] 已验证后端测试通过、前端构建通过、健康检查接口可访问。
- [x] 详细讨论并确定 MVP 数据库表设计。
- [x] 实现 PostgreSQL 业务表结构：`tags`、`questions`、`question_answers`、`question_tags`、`users`、`user_answers`。
- [x] 补充业务表索引、约束、中文表注释和字段注释。
- [x] 初始化固定标签库 seed 数据，采用 `tags.code` 作为 AI/API 稳定标识。
- [x] 根据当前表结构实现核心 Entity：`Tag`、`Question`、`QuestionAnswer`、`QuestionTag`、`User`、`UserAnswer`。
- [x] 为 Entity 持久化字段补充中文 JavaDoc。
- [x] 设计题目相关 MVP REST API 文档，包括标签查询、AI 生成题目、人工录入、列表、详情、启停和删除。
- [x] 实现标签分页查询接口 `GET /api/tags`。
- [x] 实现通用分页返回结构 `PageVO<T>`。
- [x] 前端标签管理页面接入 `GET /api/tags`，支持筛选、分页和页大小切换。
- [x] 实现生成题目请求 DTO 与基础 Bean Validation 校验。
- [x] 实现 AI Prompt 构建器，将标签候选、排除题目和额外要求写入 Prompt。
- [x] 实现生成题目 AI mock 服务。
- [x] 实现 Google AI 生成题目客户端。
- [x] 实现 `AI_PROVIDER`、`GOOGLE_AI_MODEL`、`GOOGLE_AI_API_KEY`、`GOOGLE_AI_BASE_URL` 配置项。
- [x] 实现 AI 生成题目接口 `POST /api/questions/ai-generations`。
- [x] 实现 AI 输出 JSON 解析、字段校验、标签白名单校验、标准答案规则校验。
- [x] 实现 AI 生成题目入库逻辑，保存 `questions`、`question_answers`、`question_tags`。
- [x] 前端首页调整为练习优先，保留标签管理、题目管理、错题复习入口。
- [x] 前端练习页接入题目生成接口，支持生成条件、题目展示、答案输入和评分待接入提示。
- [x] 补充 AI 生成请求、Prompt 构建、生成入库、AI provider 配置和 Google AI 客户端相关测试。

## 待办事项

### P0

- [ ] 确认本地开发配置是否需要拆分 `dev` profile。
- [ ] 补充真实 Google AI 调用的本地验证说明，包括环境变量配置、失败排查和 mock 回退方式。

### P1

- [ ] 实现人工创建题目接口 `POST /api/questions`。
- [ ] 实现题目列表查询接口 `GET /api/questions`，保持分页返回。
- [ ] 实现题目详情查询接口 `GET /api/questions/{id}`。
- [ ] 实现题目启用/停用接口 `PATCH /api/questions/{id}/enabled`。
- [ ] 实现题目逻辑删除接口 `DELETE /api/questions/{id}`。
- [ ] 前端题目管理页面接入真实题目列表、详情、启停、删除能力。
- [ ] 设计答题、评分、错题和复习相关 REST API。
- [ ] 明确完整业务流程：提交答案、保存评分、记录错误、加入复习。

### P2

- [x] 设计 AI 评分 Prompt、JSON 输出契约和后端校验规则。
- [x] 后端实现提交答案接口。
- [x] 后端实现 AI 评分 mock 服务。
- [x] 后端实现 AI 评分结果解析、字段校验、分数计算和容错处理。
- [x] 后端实现评分结果保存到 `user_answers`。
- [x] 后续接入真实 AI 评分 API，并通过配置在 mock 和真实调用之间切换。

### P3

- [x] 前端练习页接入提交答案接口。
- [x] 前端实现评分结果展示，包括总分、分项评分、错误分析和修改建议。
- [ ] 增加错题和复习内容的基础入口。
- [ ] 实现错题列表和复习计划的最小可用页面。

### P4

- [ ] 补充题目人工创建、列表、详情、启停和删除相关测试。
- [ ] 补充答题评分 Service 层和核心工具类测试。
- [ ] 完善日志记录，避免输出敏感信息。
- [ ] 设计错误记录与后续复习策略。
- [ ] 评估后续扩展能力，包括题库沉淀、学习统计和推荐能力。
