# EnglishTranslationTool-mobile — 考研英语一翻译练习 Android APK

## 项目结构

```
EnglishTranslationTool-mobile/
├── src/
│   ├── App.vue       # 全部功能 3825 行（翻译练习/水波训练/反转训练/词根分析/短语默写/图片导入）
│   └── main.js       # Vue 入口，挂载 Element Plus
├── public/
│   ├── essays-data.json  # 内置 30 篇考研英语一真题范文
│   ├── pwa-192x192.png   # PWA 图标（已复用为 Android 图标源）
│   └── pwa-512x512.png
├── dist/                 # Vite 构建产物（npm run build）
├── android/              # Capacitor Android 原生壳
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── assets/public/     # cap sync 从 dist/ 同步进来
│   │   │   ├── java/.../MainActivity.java  # 唯一原生代码（1 行）
│   │   │   ├── res/mipmap-*/      # 启动图标（从 pwa-512x512 缩放而来）
│   │   │   └── res/drawable/      # 启动画面 splash.png
│   │   └── build/outputs/apk/debug/app-debug.apk  # 最终产物
│   └── build.gradle
├── capacitor.config.json   # Capacitor 配置（appId/com.ett.mobile）
├── vite.config.js          # Vite + PWA 插件
├── package.json            # Vue 3 + Element Plus + ECharts + Capacitor
└── rebuild-apk.sh          # 一键重打包脚本
```

## APK 封装流程（完整）

### 步骤 1：Capacitor 项目初始化

Capacitor 是 Ionic 团队维护的**跨平台原生桥接框架**。核心概念：

- **Web 层**：你的前端代码（Vue/React/任何静态站点）
- **Native 层**：各平台原生壳（Android/iOS），提供 WebView 容器 + 原生 API 桥接
- **Bridge**：`cap sync` 把 Web 产物复制到 Native 工程，`npx cap add <platform>` 生成原生壳

```bash
npm i @capacitor/core @capacitor/cli @capacitor/android
npx cap init "翻译练习" "com.ett.mobile"
```

`capacitor.config.json` 关键字段：
- `appId`：Android 包名 `com.ett.mobile`，全局唯一
- `webDir`：指向 `dist/`（Vite 构建输出目录）
- `server.androidScheme`：`https`（Android WebView 默认用 https 加载本地文件）

### 步骤 2：Vite 构建 + PWA 加持

Vite 把 Vue SFC 编译成浏览器可直接运行的 JS/CSS/HTML：

```bash
npm run build   # → dist/
```

同时 `vite-plugin-pwa` 生成 Service Worker + manifest：
- **离线缓存**：首次打开后，JS/CSS/静态数据（essays-data.json）全部缓存
- **DeepSeek API 请求**也做了 NetworkFirst 缓存（失败时用上次成功的响应兜底）
- **Standalone 模式**：添加到主屏幕后像原生 App

### 步骤 3：Capacitor 同步 → Android 工程

```bash
npx cap add android   # 生成 android/ 目录（只跑一次）
npx cap sync android  # 复制 dist/ → android/app/src/main/assets/public/
```

`cap add android` 生成的 Android 工程是标准的 Gradle 项目，包含：
- `MainActivity.java`（继承 `BridgeActivity`，1 行代码）
- `AndroidManifest.xml`（声明权限：INTERNET + FileProvider）
- `build.gradle`（compileSdk 34, targetSdk 34, minSdk 22）
- Gradle Wrapper（`gradlew` + `gradle-wrapper.properties`）

`cap sync` 做的事：
1. 把 `dist/` 下所有文件复制到 `android/app/src/main/assets/public/`
2. 把 `capacitor.config.json` 复制到 `assets/` 供运行时读取
3. 检查/更新原生插件依赖

### 步骤 4：Android 资源定制

#### 4.1 启动图标（替换默认 Capacitor 三角图标）

在 `android/app/src/main/res/mipmap-*/` 下：

| 密度 | 标准图标 | 圆形图标 | 自适应前景 (108dp) |
|------|---------|----------|-------------------|
| mdpi (1x)    | 48×48   | 48×48    | 108×108   |
| hdpi (1.5x)  | 72×72   | 72×72    | 162×162   |
| xhdpi (2x)   | 96×96   | 96×96    | 216×216   |
| xxhdpi (3x)  | 144×144 | 144×144  | 324×324   |
| xxxhdpi (4x) | 192×192 | 192×192  | 432×432   |

用 ImageMagick 从 `pwa-512x512.png` 一批缩放生成。

**自适应图标（Android 8+）：**
- `mipmap-anydpi-v26/ic_launcher.xml`：引用 `ic_launcher_background` 颜色 + `ic_launcher_foreground` 图片
- `drawable/ic_launcher_background.xml`：纯色 vector（`#1a1a2e` 深蓝，匹配 App 暗色主题）
- `values/ic_launcher_background.xml`：颜色定义 `ic_launcher_background = #1a1a2e`

#### 4.2 启动画面

##### splash.png（480×320 → 替换为 2880×2880）

原始是 Capacitor 默认 480×320 空白图。新的：深蓝背景 + 居中图标，覆盖所有屏幕尺寸。

#### 4.3 应用名称

`res/values/strings.xml`：`app_name = "翻译练习"`

### 步骤 5：Gradle 构建 → APK

```bash
cd android
./gradlew assembleDebug
```

`assembleDebug` 做了什么：
1. **合并 Manifest**：项目 Manifest + Capacitor 插件 Manifest → 最终 `AndroidManifest.xml`
2. **资源编译**：res/ 下的 XML/PNG → R.java + 编译后的二进制资源
3. **Java 编译**：`MainActivity.java` + Capacitor 库 → `.class` → `.dex`（Android 字节码）
4. **打包**：dex + 资源 + assets/public/（你的网页）+ libs → APK（本质是 zip）
5. **签名**：debug.keystore 自动签名（Release 需要手动配置签名密钥）

产物：`android/app/build/outputs/apk/debug/app-debug.apk`（4.5MB）

### 步骤 6：运行时发生了什么

```
用户点击桌面图标
  → Android 启动 MainActivity
  → Capacitor Bridge 初始化 WebView
  → WebView 加载 file:///android_asset/public/index.html
  → Vue App 挂载 → Element Plus 渲染 UI
  → 所有功能在 WebView 内运行（就是浏览器）
  → localStorage 持久化数据（练习记录/生词池/提示词设置）
  → DeepSeek API 直接走 HTTP（需要网络）
```

## 踩坑记录

### 坑 1：Gradle 下载超时（10s）

`./gradlew` 首次运行会下载 Gradle 8.2.1 发布包（~185MB），默认超时 10 秒，国内到 `services.gradle.org` 基本连不上。

**解决：**用腾讯云镜像手动下载到 D 盘
```bash
curl -L -o "D:/gradle-cache/wrapper/dists/gradle-8.2.1-all/<hash>/gradle-8.2.1-all.zip" \
  "https://mirrors.cloud.tencent.com/gradle/gradle-8.2.1-all.zip"
```
**教训：**事先 `export GRADLE_USER_HOME=D:/gradle-cache` 避免下到 C 盘。

### 坑 2：Java 版本连环套

| 尝试 | Java | 报错 |
|------|------|------|
| 1 | Java 8（系统默认）| AGP 8.x requires Java 11 |
| 2 | Java 11 (ms-11.0.29) | AGP 8.x requires Java 17 |
| 3 | Java 25 (openjdk-25.0.1) | Unsupported class file major version 69 |

**原因：**
- AGP 8.0+ 强制 JDK 17（与 Gradle 本身支持无关）
- Java 25 是的 class file version 69，Gradle 8.2.1 最高只支持到 Java 20
- C 盘剩 3GB，不能装 JDK 到 C 盘

**解决：**从清华镜像下载 Eclipse Temurin JDK 17.0.19 免安装版到 D 盘
```bash
curl -L -o "D:/java/jdk-17.zip" \
  "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/OpenJDK17U-jdk_x64_windows_hotspot_17.0.19_10.zip"
unzip -q "D:/java/jdk-17.zip" -d "D:/java/jdk-17"
```

### 坑 3：Android SDK 完全不存在

`npx cap doctor android` 回报 "Android looking great!" 但实际上 SDK 根本没装——它只检查了环境通用条件（Java 存在、Gradle 可运行等），不检查 SDK 是否存在。

**构建时的报错：**
```
SDK location not found. Define a valid SDK location with an ANDROID_HOME
environment variable or by setting the sdk.dir path in local.properties
```

**解决：**
1. 从 Google 下载 cmdline-tools（137MB）→ `D:\android-sdk\cmdline-tools\latest\`
2. Java 直调 sdkmanager（.bat 在 bash 下路径转义有问题）
3. 接受许可协议（7 个 `y`）
4. 安装最小构建依赖：
   - `platform-tools`（adb/fastboot 等）
   - `platforms;android-34`（android.jar + framework 资源）
   - `build-tools;34.0.0`（aapt/d8/zipalign 等编译工具）

**经验：**
- cmdline-tools 解压后要平铺目录结构（`cmdline-tools/latest/bin/` 而非 `cmdline-tools/latest/cmdline-tools/bin/`）
- `.bat` 文件在 Git Bash 下 `cmd.exe //c` 调用路径转义容易出错，直接用 Java 执行 jar 更稳
- 整套 SDK（cmdline-tools + platform + build-tools）约 700MB，必须放 D 盘

### 坑 4：Gradle Daemon 端口被占用

多次失败的构建留下了残留的 Gradle Daemon 进程，新构建报 `BindException: Address already in use`。

**解决：** `taskkill //F //IM java.exe`（杀死所有 Java 进程），然后用 `--no-daemon` 构建。

## 环境变量（必须）

构建 APK 前需设置：

| 变量 | 值 | 用途 |
|------|-----|------|
| `JAVA_HOME` | `D:\java\jdk-17` | Java 17 运行时 |
| `ANDROID_HOME` | `D:\android-sdk` | Android SDK 根目录 |
| `GRADLE_USER_HOME` | `D:\gradle-cache` | Gradle 缓存（不能放 C 盘） |

## 一键重打包

改完代码后运行：

```bash
bash D:/photovoltaic/EnglishTranslationTool-mobile/rebuild-apk.sh
```

脚本流程：`npm run build`（可选）→ `npx cap sync android` → `./gradlew assembleDebug`。

## 技术栈

| 层 | 技术 | 版本 |
|----|------|------|
| 前端 | Vue 3 + Element Plus + ECharts | 3.5 / 2.13 / 6.0 |
| 构建 | Vite + vite-plugin-pwa | 6.2 / 0.21 |
| 原生桥接 | Capacitor | 6.2.1 |
| 原生构建 | Gradle + AGP | 8.2.1 |
| Java | Eclipse Temurin (OpenJDK) | 17.0.19 |
| Android SDK | platform + build-tools | 34 |
| AI API | DeepSeek | — |

## 复用工具链：新项目封装 APK

本机已安装的通用工具链（所有项目共享，无需重复配置）：

| 组件 | 路径 | 说明 |
|------|------|------|
| JDK 17 | `D:\java\jdk-17` | 所有 APK 编译共用 |
| Android SDK | `D:\android-sdk` | 任意项目都走它 |
| Gradle 缓存 | `D:\gradle-cache` | 依赖包跨项目共享 |

新项目封装只需：

```bash
# 1. 装 Capacitor（每个项目一次）
cd 新项目/
npm i @capacitor/core @capacitor/cli @capacitor/android

# 2. 生成 Android 壳（每个项目一次）
npx cap add android
npx cap sync android

# 3. 构建 APK（每次改代码后）
export JAVA_HOME=D:/java/jdk-17
export ANDROID_HOME=D:/android-sdk
export GRADLE_USER_HOME=D:/gradle-cache
cd android && ./gradlew assembleDebug
```

环境变量三行照抄，唯一要改的是项目路径。`rebuild-apk.sh` 模板同理。
