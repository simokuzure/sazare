# AGENTS.md

## 项目定位

这是一个个人练手项目。

目标：

- 保持代码质量
- 尽量贴近企业开发规范
- 不为了未来可能发生的需求而过度设计（YAGNI）
- 优先保证代码可读性、可维护性、可扩展性

所有代码应符合 Java 社区最佳实践。

---

# 技术栈

## 后端

- Java 25（如未指定则使用当前 LTS）
- Spring Boot
- MyBatis
- PostgreSQL
- Redis
- Maven

## 前端

- React
- TypeScript

---

# 编码原则

遵循以下原则：

- 单一职责（SRP）
- 开闭原则（OCP）
- 依赖倒置（DIP）
- KISS（Keep It Simple）
- DRY（Don't Repeat Yourself）
- YAGNI（You Aren't Gonna Need It）

优先简单实现。

不要为了展示技术而增加复杂度。

---

# 代码风格

## Java

优先使用：

- record（适合DTO）
- Optional（适量使用）
- Stream（保持可读性）
- Lombok（减少样板代码）

避免：

- 过长的方法
- 过深的嵌套
- 魔法数字
- 魔法字符串

一个方法只做一件事。

方法长度建议不超过 200 行。

---

# 项目结构

保持标准 Spring Boot 分层：

```
controller
service
service/impl
mapper
entity
dto
vo
config
exception
common
util
```

不要随意新增新的架构层。

---

# Controller

Controller 只负责：

- 参数接收
- 参数校验
- 调用 Service
- 返回结果

不要写业务逻辑。

---

# Service

业务逻辑全部放在 Service。

Service 应保持：

- 高内聚
- 易测试
- 不直接处理 HTTP

Service 不重复校验 DTO 注解已经覆盖的基础入参。

Service 只处理业务规则校验，例如：

- 数据库记录是否存在
- 启用 / 删除状态是否允许操作
- 权限或归属关系
- 跨字段业务约束
- AI 返回内容合法性

---

# Mapper

Mapper 仅负责数据库操作。

不要把业务逻辑写进 SQL。

SQL 保持简单清晰。

---

# DTO / VO

DTO：

用于请求参数。

DTO 应优先使用 Bean Validation 注解完成基础入参校验。

注解必须写明确的中文 `message`，避免返回默认英文或不清晰提示。

常见基础校验包括：

- 必填
- 非空字符串
- 数值范围
- 集合长度
- 集合元素非空
- 固定格式或枚举值

VO：

用于返回数据。

不要直接返回 Entity。

---

# Entity

Entity 与数据库保持一致。

不要把业务逻辑写进 Entity。

---

# 数据库设计

遵循：

- 第三范式
- 合理索引
- 字段命名统一
- 尽量避免冗余字段

命名：

主键：

id

创建时间：

created_at

更新时间：

updated_at

删除标记：

deleted

---

# SQL

优先：

简单 SQL

避免：

超长 SQL

复杂查询优先拆分。

---

# 异常处理

统一异常处理。

不要直接：

```
throw new RuntimeException(...)
```

使用：

- 自定义业务异常
- 全局异常处理

返回统一错误格式。

---

# 日志

使用 SLF4J。

不要：

```
System.out.println()
```

日志级别：

debug

info

warn

error

避免打印敏感信息。

---

# Redis

Redis 仅用于：

- 缓存
- Session（如有）
- 临时数据

不要作为主数据库。

---

# AI 相关

如果涉及 AI：

Prompt 尽量模板化。

AI 返回：

统一 JSON。

不要依赖 AI 返回自由文本。

后端负责：

- JSON 校验
- 容错
- 默认值处理

AI 输出永远不能直接相信。

---

# API

RESTful 风格。

例如：

GET

POST

PUT

DELETE

路径使用：

```
/questions
/questions/{id}
/reviews
```

不要使用：

```
/getQuestions
/updateQuestion
```

---

# 返回格式

统一：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

错误：

```json
{
  "code": 40001,
  "message": "参数错误",
  "data": null
}
```

---

# 命名规范

类：

PascalCase

变量：

camelCase

常量：

UPPER_SNAKE_CASE

数据库：

snake_case

不要使用拼音命名。

---

# 注释

只写必要注释。

优先让代码表达含义。

避免：

```java
// 设置name
user.setName(name);
```

对于复杂业务，应解释：

- 为什么这样做
- 为什么不能采用其它方案

而不是解释代码本身。

---

# 测试

重要业务建议编写单元测试。

优先覆盖：

- Service
- 工具类
- 核心算法

---

# 第三方依赖

优先使用：

成熟、稳定、社区活跃的库。

不要为了一个小功能引入重量级依赖。

---

# 当存在多个实现方案时

请：

1. 简要比较方案优缺点
2. 推荐最适合当前项目的方案
3. 说明推荐原因

优先考虑：

- 可维护性
- 开发效率
- 社区最佳实践

而不是追求最复杂或最前沿的技术。

---

# 输出代码要求

生成的代码应：

- 可以直接编译
- 可以直接运行
- 包含必要 import
- 保持统一风格
- 不省略关键实现
- 不使用伪代码

除非明确要求，否则不要省略实现细节。

---

# 修改已有代码

修改代码时：

- 尽量保持原有风格
- 尽量减少改动范围
- 不随意重构无关代码
- 不修改无关文件

优先实现最小修改（Minimal Change）。

---

# 回答原则

默认使用中文回答。

代码中的：

- 类名
- 方法名
- 变量名
- 数据库字段

统一使用英文。

解释应简洁明确。

如果发现需求存在明显问题，可以指出并给出更合理的建议，而不是直接按照错误需求生成代码。
