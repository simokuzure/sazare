# TODO

## 已完成

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

## 待办事项

### P0

- [ ] 补充项目开发启动说明，包括 Docker、后端、前端的启动与停止命令。
- [ ] 确认本地开发配置是否需要拆分 `dev` profile。
- [ ] 详细讨论并确定 MVP 数据库表设计。

### P1

- [ ] 设计 MVP REST API，包括题目、答题、评分、错题和复习相关接口。
- [ ] 根据确认后的表结构实现 Entity、DTO、VO、Mapper、Service、Controller。
- [ ] 明确基础业务流程：生成题目、提交答案、保存评分、记录错误、加入复习。

### P2

- [ ] 先确定 AI 调用策略：mock 优先还是真实 API 优先。
- [ ] 设计固定 Prompt 模板。
- [ ] 约定 AI JSON 输出格式，并在后端做校验、容错和默认值处理。

### P3

- [ ] 完善前端练习流程页面。
- [ ] 实现题目展示、答案输入、提交、评分结果展示。
- [ ] 增加错题和复习内容的基础入口。

### P4

- [ ] 补充 Service 层和核心工具类测试。
- [ ] 完善日志记录，避免输出敏感信息。
- [ ] 设计错误记录与后续复习策略。
- [ ] 评估后续扩展能力，包括题库沉淀、学习统计和推荐能力。
