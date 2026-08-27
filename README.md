# 日语翻译练习

面向个人本地使用的日语练习项目，覆盖中译日、英译日的短句与文章翻译，以及纯日语内容纠错。系统使用结构化 AI 输出完成题目生成、评分与纠错，并将用户确认或主动记录的复习内容接入学习闭环。

## 当前功能

### 练习与评分

- 中译日 / 英译日短句：AI 生成、人工维护、随机抽题和结构化评分。
- 中译日 / 英译日文章：按体裁生成或随机抽取，支持逐句评语、错误分析和完整译文评分。
- 纯日语纠错：直接提交日语文本，返回完整修订稿、四项评分、错误候选和修改建议。
- AI 提供 `mock` 与 Google 两种实现，由统一的 `AI_PROVIDER` 配置切换。
- AI 返回内容使用固定 JSON 契约，后端负责字段、枚举、分数、错误类型和业务关系校验。

### 复习卡片与统计

- AI 错误分析默认是候选，用户确认后可加入复习卡片；没有明确错误时，也可以手动记录需要优化的表达。
- 复习内容继承当前翻译方向，并通过复习周期和 SM-2 调度持续练习；卡片支持逻辑删除，删除后保留历史，再次添加时按全新卡片开始。
- 答题记录支持短句、文章和日语纠错筛选，以及列表/详情页内切换。
- 学习统计展示连续与累计打卡天数，并通过页内切换分别查看翻译、日语纠错的练习趋势和评分维度；复习区域展示实时卡片状态、期间复习次数与通过率。

### 题库管理

- 标签分页查询，以及短句、文章题目的创建、分页、详情、编辑、启停和逻辑删除。
- PostgreSQL `pgvector` 语义向量检索，用于 AI 生成题目的近似去重。
- 历史题目向量可通过 `POST /api/questions/embedding-backfills` 分批回填。

## 当前边界

- 当前是单机、单用户练习项目，业务数据统一归属内置用户 `LOCAL_DEFAULT`，尚未实现登录和真实用户隔离。
- Redis 已纳入本地基础设施，但不作为主数据库。
- Google AI 客户端已经实现；真实 API、本地数据库全流程和浏览器端到端回归仍需按 [TODO](TODO.md) 完成。
- 项目不包含社区、排行榜或复杂推荐算法。

## 技术栈

- 后端：Java 25、Spring Boot 4、MyBatis、Maven Wrapper。
- 前端：React 19、TypeScript 5、Vite 7、Recharts。
- 数据：PostgreSQL 16 + pgvector、Redis 7。
- 本地环境：Docker Compose、Windows PowerShell。

## 项目结构

```text
jt/
├─ backend/                  Spring Boot 后端
│  └─ src/main/resources/
│     ├─ db/schema.sql       数据库结构
│     ├─ db/seed.sql         初始化数据
│     └─ mapper/             MyBatis XML
├─ frontend/                 React 前端
├─ docs/                     API 与 AI Prompt 设计
├─ docker-compose.yml        PostgreSQL、Redis
└─ TODO.md                   已完成事项与后续任务
```

## 启动项目

### 1. 启动基础服务

在项目根目录执行：

```powershell
docker compose up -d
```

默认端口：

- PostgreSQL：`localhost:5432`
- Redis：`localhost:6379`

停止服务：

```powershell
docker compose down
```

`docker compose down -v` 会删除本地 PostgreSQL 和 Redis 数据卷，仅在确认不需要保留数据时使用。

### 2. 初始化数据库

项目没有集成自动迁移工具。请通过 PostgreSQL 客户端依次执行 [schema.sql](backend/src/main/resources/db/schema.sql) 和 [seed.sql](backend/src/main/resources/db/seed.sql)：前者创建数据库结构，后者写入默认用户、标签和错误类型初始化数据。

### 3. 选择 AI 提供方

本地无 API Key 调试可使用 mock：

```powershell
$env:AI_PROVIDER = "mock"
```

使用 Google AI 时至少配置：

```powershell
$env:AI_PROVIDER = "google"
$env:GOOGLE_AI_API_KEY = "你的 API Key"
```

可选配置：

- `GOOGLE_AI_MODEL`：文本生成与评分模型，默认 `gemini-3.6-flash`。
- `GOOGLE_AI_EMBEDDING_MODEL`：向量模型，默认 `gemini-embedding-001`。
- `GOOGLE_AI_ARTICLE_TEMPERATURE`：文章生成 temperature，默认 `1.1`，允许范围 `0.0`～`2.0`。
- `GOOGLE_AI_ARTICLE_TOP_P`：文章生成 topP，默认 `0.98`，取值必须大于 `0.0` 且不超过 `1.0`。
- `GOOGLE_AI_BASE_URL`：Google AI API 地址，默认 `https://generativelanguage.googleapis.com/v1beta`。
- `AI_REQUEST_TIMEOUT`：AI 请求超时时间，默认 `180s`；例如设置为 `240s`。

不要将 API Key 写入代码、配置文件或提交记录。

### 4. 启动后端

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

后端 API 基地址：`http://localhost:8080/api`。

### 5. 启动前端

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

前端地址：`http://localhost:5173`。Vite 已将 `/api` 代理到 `http://localhost:8080`。

## 验证

后端测试：

```powershell
cd backend
.\mvnw.cmd test
```

前端检查：

```powershell
cd frontend
npm.cmd run lint
npm.cmd run build
```

当前工作区已验证后端 117 项测试和前端生产构建通过。前端构建仍有主 JavaScript chunk 超过 500 kB 的性能提示，已记录在 [TODO](TODO.md)。

## 设计文档

- [题目 API 设计](docs/question-api-design.md)
- [AI 题目生成 Prompt](docs/ai-question-generation-prompt.md)
- [AI 答题评分 Prompt](docs/ai-answer-scoring-prompt.md)
