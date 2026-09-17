# EnglishTranslationTool-mobile — 考研英语翻译练习 Android App

Vue 3 + Capacitor 手机端英语翻译练习工具，支持 AI 评分、水波训练、反转训练、短语默写、词根分析等模式。6.67 英寸手机（375dp）三 Tab 底部导航布局。

## 功能概览

| Tab | 功能 |
|-----|------|
| 📚 **范文库** | 考研英语一真题范文列表、打卡日历、统计条、添加范文、图片导入 |
| ✏️ **练习** | 四种模式：API评分 / 窗口AI / 水波训练 / 反转训练；**强制限时训练**（首次启动设限时 → 右上角倒计时 → 超时强制续费或作废）；分段原文横向滑动；评分结果四维度展示；反转模式支持粘贴评分JSON、水波错误结构分析、翻译错误对照 |
| 👤 **我的** | 打卡统计、生词短语池、短语默写、练习历史、提示词配置、词根分析、深色模式、数据导入导出 |

## 快速开始

```bash
# 安装依赖
npm install

# 开发模式（浏览器访问 http://localhost:5173）
npm run dev

# 构建 Web 产物
npm run build
```

浏览器 F12 切换到移动端视图（375×812）即可预览手机端效果。

## 构建 APK

需要 JDK 17 + Android SDK。本机已装好则直接运行：

```bash
bash rebuild-apk.sh
```

脚本流程：`npm run build` → `npx cap sync android` → `./gradlew assembleDebug`

产物：`android/app/build/outputs/apk/debug/app-debug.apk`

首次使用需配置环境变量（参见 [APK封装流程.md](./APK封装流程.md)）：

| 变量 | 值 |
|------|-----|
| `JAVA_HOME` | `D:\java\jdk-17` |
| `ANDROID_HOME` | `D:\android-sdk` |
| `GRADLE_USER_HOME` | `D:\gradle-cache` |

## 项目结构

```
src/
├── App.vue                     # 桌面端布局 + 对话框 + provide 共享状态
└── components/
    └── MobileApp.vue           # 手机端三 Tab 布局，通过 inject 获取状态
android/                        # Capacitor Android 原生壳
public/
├── essays-data.json            # 内置 30 篇考研英语一真题范文
├── pwa-192x192.png
└── pwa-512x512.png
rebuild-apk.sh                  # 一键重打包脚本
```

## 文档索引

| 文档 | 内容 |
|------|------|
| [APK封装流程.md](./APK封装流程.md) | Capacitor 初始化、Gradle 构建、JDK/SDK 环境、踩坑记录 |
| [PC移植Mobile经验.md](./PC移植Mobile经验.md) | 双布局架构、触摸适配、批注系统、弹窗二分法、移植 checklist |
| [TECHNICAL_PLAN_v2.0.md](./TECHNICAL_PLAN_v2.0.md) | v2.0 技术方案 |

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 (Composition API) + Element Plus + ECharts |
| 构建 | Vite 6 + vite-plugin-pwa |
| 原生桥接 | Capacitor 6 |
| 存储 | localStorage + @capacitor/filesystem (备份层) |
| AI API | DeepSeek |

## 手机端适配方案

弹窗按类型适配（方案 H — 键盘感知二分法）：

- **需键盘输入**（提示词配置、图片导入）→ 全屏弹窗
- **不需键盘**（生词池、短语默写、词根分析、历史记录）→ 底部抽屉，带拖拽手柄

## 最近更新

### 2026-09-17 — 限时训练：强制限时 + 全局记录卡

一套惩罚性限时机制，把「不设限时的练习」彻底堵死。

**数据模型**（`App.vue`，持久化进 `ett_backup`，`exportVersion` 6 → 7）

```js
cardDeadlines[essayId] = { limit, accumulated, setAt, voidCount, voidedAt }
recordCards            // 全局记录卡余额，初始 1 张
```

- `limit` — 该卡限时（秒），首次启动时设定后**永久锁死**
- `accumulated` — 该卡自上次结算以来的累计练习秒数，**计入全局总耗时**

**规则**

| 环节 | 行为 |
|------|------|
| 首次启动 | 强制弹窗设定限时（预设 30分/1h/1.5h/2h/3h/4h + 10分/30分微调）。不设定无法开始计时 |
| 计时 | `elapsed` 从 `accumulated` 续接，秒表每 tick 写回 `accumulated`；右上角倒计时 = `limit - 已用` |
| 限时内完成 | 记录卡 +1，`accumulated` 兑现进 `record.timeSpent` 后归零 |
| 倒计时归零 | 停表 + 全屏强制二选一：花 1 张记录卡续 1h / 接受作废（**无关闭按钮**） |
| 接受作废 | `accumulated = 0` + `voidCount++`。因 `accumulated` 被 `totalTime` 计入，归零即自动从全局总耗时中销毁 |

**关键实现点**

- `totalTime` 改为 `Σ records.timeSpent + Σ cardDeadlines[*].accumulated` —— 作废时清零 `accumulated` 就等于从总耗时里扣掉，「两者都作废」无需额外记账
- 封堵了 3 处裸 `setInterval(elapsed++)`（切卡 / 打字自动启动 / onMounted 恢复），统一走 `runTimer()`，否则会绕过超时检查
- 「打字自动启动计时」的两个 watcher 加了 `hasTimeLimit` 闸门 —— 否则在输入框里打字就能绕过强制弹窗开始计时
- 超时弹窗开启瞬间用 `timeoutCardId` 冻结涉事卡片，避免弹窗未决时切卡导致结算对象漂移
- 每 5s 调一次 `syncData()` 落盘累计计时（`cardDeadlines` 不在 deep watch 列表里）
- 删除范文时同步 `delete cardDeadlines[id]`

**UI**

- `MobileApp.vue`：练习页右上角倒计时（最后 5 分钟标红闪烁）、限时状态条（记录卡/限时/已练/作废次数）、范文库每卡限时标记、「我的」页记录卡保险库
- `App.vue`：桌面端顶栏记录卡、练习区头部倒计时、范文列表限时标记、两个强制弹窗

> ⚠️ 已知取舍：重复练习一张已完成的卡时，`record.timeSpent` 沿用原有 `Math.max` 语义，完成瞬间全局总耗时可能出现一次小幅回落（新一次用时短于旧记录时）。

> 测试：`D:\temp\ett-limit-test.mjs`（Playwright，移动端 390×844）。已验「强制设限」「超时→续费」两条主链路；「超时→作废」因夹具问题未跑完。

### 2026-09-16 — 手动导入 AI 评分统一接入解析诊断

- `App.vue`：`reportImportError` 放宽触发条件 —— 只要拿得到原文就弹诊断框，不再限定错误信息必须等于「未识别到JSON」（这样 `JSON.parse` 的 `Unexpected token` 也能给出诊断）
- 两个「粘贴窗口AI返回的JSON」入口（正向 `submitWindowAI` / 反转 `submitReverseWindowAI`）改走 `reportImportError`，与「图片导入」链路对齐
- `importData`（JSON 备份导入）同样接入 —— 它是最后一个用裸 `JSON.parse`、无诊断兜底的入口
- 背景：粘贴的 JSON 被安卓输入法改写后，原先只弹一句「JSON解析失败」，看不出坏在哪个字符

### 2025-06-30 — 译文对照对齐修复 + 原文吸顶 + 一键启动脚本

1. **译文对照不再错位**（`App.vue`）
   - `splitSentences()` 不再把回车 `\n` 当切分符，只按句末标点切分
   - 新增 `smartAlign()` 基于字符重叠率的贪心对齐，代替原来的数组下标硬对齐
   - 同时修复 `diffResult` 和 `reverseDiffResult`（反转训练对照）

2. **原文卡片吸顶**（`MobileApp.vue`）
   - 练习页顶部（标题栏 + 模式选择 + 原文展开内容）使用 `position: sticky` 吸附在屏幕顶部
   - 展开原文后往下滑，原文始终可见，翻译/评分内容在下方自由滚动
   - 原文区域限高 40vh，超出时自身可垂直滚动
   - 收起原文后恢复正常流式布局

3. **一键启动脚本**
   - `dev-server.cmd` — 双击启动开发服务器，自动打开浏览器

## 快速启动

双击项目目录下的 **`dev-server.cmd`** 即可启动开发服务器。

