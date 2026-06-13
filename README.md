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

