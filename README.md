# EnglishTranslationTool-mobile — 考研英语翻译练习 Android App

Vue 3 + Capacitor 手机端英语翻译练习工具，支持 AI 评分、水波训练、反转训练、短语默写、词根分析等模式。6.67 英寸手机（375dp）三 Tab 底部导航布局。

## 功能概览

| Tab | 功能 |
|-----|------|
| 📚 **范文库** | 考研英语一真题范文列表、打卡日历、统计条、添加范文、图片导入 |
| ✏️ **练习** | 四种模式：API评分 / 窗口AI / 水波训练 / 反转训练；分段原文横向滑动；评分结果四维度展示 |
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

