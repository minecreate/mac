# UI Baseline · 2026-10-10 v1

当前云平台前端视觉基线快照（Ant Design 壳 + 登录页工业风 + 运维总览）。

## 回退方式

在 `linenexus-web` 目录执行（PowerShell），将本快照覆盖回 `src`：

```powershell
$snap = ".\.ui-snapshots\2026-10-10-v1-baseline"
$src  = ".\src"
$files = @(
  "styles\main.css",
  "App.vue",
  "views\HomeView.vue",
  "views\LoginView.vue",
  "views\PlaceholderView.vue",
  "views\EdgeGatewaysView.vue",
  "views\account\ProfileView.vue",
  "views\sys\SystemLogsView.vue",
  "views\ops\AlarmsView.vue",
  "views\monitor\HistoryTrendView.vue",
  "views\hmi\HmiRuntimeView.vue",
  "views\hmi\HmiEditorView.vue",
  "views\hmi\HmiScreenListView.vue",
  "views\platform\PlatformLoginView.vue",
  "views\platform\PlatformLayout.vue",
  "views\platform\TenantManageView.vue",
  "components\ChinaDeviceMap.vue",
  "hmi\symbols\PalettePanel.vue",
  "hmi\editor\PageTreePanel.vue",
  "hmi\canvas\StageHost.vue",
  "hmi\canvas\HmiImageShape.vue"
)
foreach ($f in $files) {
  Copy-Item -LiteralPath (Join-Path $snap $f) -Destination (Join-Path $src $f) -Force
}
if (Test-Path (Join-Path $snap "index.html")) {
  Copy-Item (Join-Path $snap "index.html") ".\index.html" -Force
}
```

注意：回退会覆盖对应文件的**全部内容**（含逻辑，不只是样式）。若之后只改了样式、功能已演进，请按需手工挑回 `<style>` 块。

## 包含范围

- 全局壳：`styles/main.css`、`App.vue`
- 登录 / 平台运营登录
- 运维总览、地图、告警、日志、资料
- 组态编辑器 / 运行态相关视图样式
