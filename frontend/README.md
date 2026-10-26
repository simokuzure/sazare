# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend enabling type-aware lint rules by installing `oxlint-tsgolint` and editing `.oxlintrc.json`:

```json
{
  "$schema": "./node_modules/oxlint/configuration_schema.json",
  "plugins": ["react", "typescript", "oxc"],
  "options": {
    "typeAware": true
  },
  "rules": {
    "react/rules-of-hooks": "error",
    "react/only-export-components": ["warn", { "allowConstantExport": true }]
  }
}
```

See the [Oxlint rules documentation](https://oxc.rs/docs/guide/usage/linter/rules) for the full list of rules and categories.


## 语音输入交互回归

先运行 `npm.cmd run build`，再用本机已有的 Playwright 运行时和 Chrome 执行：

```powershell
node tests/speech-input.browser.cjs
```

Playwright 不在项目依赖中。如果本机已有独立运行时，可通过 `PLAYWRIGHT_MODULE_PATH` 指向该包目录；`SPEECH_TEST_BROWSER=msedge` 可切换到 Edge。测试脚本启动临时 HTTP 服务和无头浏览器，模拟麦克风及 API，不调用真实 Google，也不保存音频。可选 `SPEECH_TEST_SCREENSHOT` 指定截图输出路径。

测试覆盖两种学习方向的四个入口、取消与迟到响应、隐藏页面清理、超长转写、录音限制和失败恢复。真实录音识别效果需另行联调。

## 聊天导入

入口位于复习卡片列表的“导入聊天记录”。选择 UTF-8 `.md` 文件后主动分析，文件最多 128 KiB，正文最多 30,000 字符（正文和格式由后端校验）。文件以 `text/markdown` 原始请求体发送。

分析前展示拖放上传区、文件就绪状态、导出入口及 Markdown 格式示例，分析完成后收起为紧凑文件栏。桌面工作区采用候选列表、原文与分析、卡片编辑三栏布局，分析完成后展开首项，候选默认不勾选。候选行不重复展示复选框，在详情中勾选后整行显示淡绿色，并标注“已选择”；打开候选只切换详情。确认按钮位于分析结果行右侧；导出提示词通过弹窗查看。长内容在各栏内滚动，窄屏通过候选列表进入详情。支持新建卡片、加入进行中或已掌握卡片，以及分批确认。已保存项锁定，剩余候选保留在内存中；刷新、离开或切换学习方向会提示丢弃未处理内容。不持久化文件或候选，不按文件内容去重。

确认接口无幂等保证。前端不自动重试，30 秒未收到完整确认结果时提示“提交结果未知”；可以在工作区内刷新目标卡片状态，再决定是否主动重提。后端校验信息暂时保持中文，包括英文界面。

验证命令：

```powershell
node --test tests/chat-import.model.cjs
npm.cmd run build
npm.cmd run lint
node tests/chat-import.browser.cjs

# 开发服务器运行时，另行覆盖 React StrictMode 下的弹窗交互
$env:CHAT_IMPORT_TEST_ORIGIN = 'http://localhost:5173'
node tests/chat-import.browser.cjs
Remove-Item Env:CHAT_IMPORT_TEST_ORIGIN
```

浏览器测试与语音测试共用本机已有的 Playwright 运行时（`PLAYWRIGHT_MODULE_PATH`），默认使用无头 Chrome，可通过 `CHAT_IMPORT_TEST_BROWSER=msedge` 切换到 Edge。默认验证生产构建，设置 `CHAT_IMPORT_TEST_ORIGIN` 后在独立浏览器会话中验证已启动的开发服务器，覆盖 StrictMode 重复初始化以及弹窗反复开关、焦点恢复。固定接口响应覆盖 1366×768 一屏布局、导出提示词弹窗、复制反馈、长内容滚动、分页、同卡分组、分批保存、结果未知、离开保护、过期分析结果及窄屏布局，不调用 Google 或写数据库。测试默认不截图，可设置 `CHAT_IMPORT_TEST_SCREENSHOTS=1` 开启。测试摘要及可选截图保存在 `node_modules/.tmp/chat-import-verification`，开发服务器验证结果位于其 `development` 子目录。真实后端与 AI 效果仍需运行对应服务后联调。
