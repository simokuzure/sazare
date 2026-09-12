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
