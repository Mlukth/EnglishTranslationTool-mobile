# EnglishTranslationTool — PC 移植 Mobile 经验文档

## 一句话架构

**同一个 Vue 项目，两套 UI 外壳，共享全部状态和业务逻辑。**

```
App.vue（4500行 — 大脑，唯一状态持有者）
├── 所有数据状态 + API 调用 + 评分逻辑 + 生词池 + 短语默写 + 图片导入 + 词根分析...
├── provide('ett', 全量状态)           ← 向子组件注入
├── <template v-if="!isMobile">        ← 桌面端 UI
│    三栏布局（范文列表 / 翻译练习 / 评分面板）
│    el-dialog 弹窗 / 鼠标批注
│    顶部工具栏 + Element Plus 组件库
└── <MobileApp v-if="isMobile" />      ← 移动端 UI（MobileApp.vue，~1070行）
     inject('ett') 拿到所有状态
     三 Tab 底部导航 / 触摸批注 / 全屏弹窗 / 底部抽屉
```

关键决策：**不做响应式 CSS 勉强适配，而是做两套完全独立的 template**。原因——手机端不是"缩小版的桌面端"，交互范式根本不同（手指 vs 鼠标、全屏 vs 弹窗、滑动 vs 点击、底部导航 vs 侧边栏）。

---

## 一、架构设计

### 1.1 模式检测

```js
// App.vue
const isMobile = ref(window.innerWidth < 768)
function checkMobile() { isMobile.value = window.innerWidth < 768 }
window.addEventListener('resize', checkMobile)
```

- 阈值 768px，和 Element Plus 响应式断点一致
- resize 事件动态切换，开发时 F12 切手机视图即可预览
- `MobileApp.vue` 里也有反向检查：如果窗口被拉到桌面宽度，通知父组件切回桌面端

### 1.2 状态共享：provide / inject

```js
// App.vue — 提供者
provide('ett', {
  essays, records, currentEssayId, scoringMode, apiKey, darkMode,
  isMobile, userTranslation, rightPanelRecord, vocabPool,
  submitTranslation, startPractice, onWordClick, toggleStarItem,
  // ... 全部 ~60 个属性/方法
})

// MobileApp.vue — 消费者
const $ = inject('ett')
// 使用：$.essays, $.currentEssay, $.submitTranslation(), ...
```

**为什么不用 Pinia/Vuex？** 这个项目只有一个消费者（MobileApp），引入状态管理库是过度工程。provide/inject 零依赖、零配置，$ 前缀访问简洁。

**代价：** MobileApp.vue 高度耦合 App.vue 的 provide 接口。如果你改 App.vue 的 provide 内容，必须同步检查 MobileApp.vue 里有没有用到。没有编译期检查。

### 1.3 组件拆分边界

| 层 | 在哪 | 职责 |
|----|------|------|
| 数据层 | App.vue | localStorage 读写、API 调用、数据 CRUD、计时器 |
| 计算层 | App.vue | computed（评分、diff、趋势图）、评分逻辑、水波分析 |
| UI 层（桌面） | App.vue `<template>` | 三栏布局、el-dialog、鼠标事件 |
| UI 层（手机） | MobileApp.vue | 三 Tab、全屏遮罩、触摸事件 |

**MobileApp.vue 绝不做的事：**
- `localStorage.setItem()`（除了 `mob_` 前缀的 UI 偏好）
- API 调用
- 修改 essays/records 等核心数据
- 拥有自己的 computed 数据（除了纯 UI 的）

### 1.4 弹窗适配：键盘感知二分法（方案 H）

手机端弹窗不能照搬桌面端的 `el-dialog`——375dp 宽度下弹窗太小，键盘弹出后更灾难。

| 弹窗类型 | 桌面 | 手机 | 判定标准 |
|----------|------|------|----------|
| 提示词配置 | el-dialog 780px | 全屏遮罩 | 有 textarea（需键盘） |
| 图片导入 | el-dialog 800px | 全屏遮罩 | 有 textarea（需键盘） |
| 生词池 | el-dialog 720px | 底部抽屉 | 纯浏览/搜索 |
| 短语默写 | el-dialog 800px | 全屏遮罩 | 有 textarea（需键盘） |
| 词根分析 | el-dialog 580px | 底部抽屉 | 有 input（需键盘但可接受） |
| 练习历史 | el-drawer | 底部抽屉 | 纯浏览 |

规则：**有 textarea 输入 → 全屏；纯浏览/搜索/选择 → 底部抽屉**。

---

## 二、移动端 UI 模式

### 2.1 三 Tab 底部导航

```html
<div class="mob-bottom-nav">
  <div @click="activeTab = 'essays'">📚 范文库</div>
  <div @click="activeTab = 'practice'">✏️ 练习</div>
  <div @click="activeTab = 'mine'">👤 我的</div>
</div>
```

- 固定 `position: fixed; bottom: 0`，`z-index: 100`
- 底部 padding 加 `env(safe-area-inset-bottom, 0px)` 适配刘海屏
- 页面内容 `padding-bottom: 48px` 避免被遮挡
- `v-if` 切换 Tab（不是 `v-show`），切换时销毁/重建 DOM，节省内存

### 2.2 字体缩放系统

```css
/* App.vue 注入 CSS 变量 */
document.documentElement.style.setProperty('--ett-fs', fontSize)

/* MobileApp.vue 全局使用 */
font-size: calc(12px * var(--ett-fs, 1));
```

- 用户在"我的"页面拖动滑块，0.8x ~ 2.0x
- 所有文字用 `calc(基数 * var(--ett-fs, 1))` 而非固定 px
- 滑块值持久化到 `localStorage.ett_mob_font_scale`

### 2.3 原文横向滑动

练习页的原文区做成类似 Instagram Stories 的**全幅横向滑动**：

```html
<div class="mob-src-scroll">
  <div v-for="seg in essay.segments" class="mob-seg" @click="mobSeg = i">
    <!-- 每段占满 100% 宽度，scroll-snap 吸附 -->
  </div>
</div>
<div class="mob-src-nav">
  ◀   ● ● ●   ▶    <!-- 底部导航点 -->
</div>
```

- `scroll-snap-type: x mandatory` + `scroll-snap-align: start`
- 底部导航点表示当前位置
- 点击左右箭头或直接滑动切换段落

### 2.4 吸顶布局

练习页顶部（标题 + 模式 + 原文展开区）使用 `position: sticky; top: 0`：

```css
.mob-practice-head {
  position: -webkit-sticky;
  position: sticky; top: 0; z-index: 60;
  background: #1a1a2e;  /* 必须有不透明背景，否则下面内容透上来 */
}
```

作用：展开原文往下读时，模式切换始终可见，不丢失上下文。

### 2.5 暗色模式

```js
// App.vue
const darkMode = ref(true)  // 默认暗色（考研场景，长时间阅读不刺眼）

// CSS
.ett-container.dark { background: #1a1a2e; color: #e0e0e0; }
```

整站默认暗色主题。Element Plus 通过 `element-plus/theme-chalk/dark/css-vars.css` 自动适配。

---

## 三、批注系统：两套独立实现

这是 PC 和 Mobile 差异最大的模块。核心原因：**鼠标事件和触摸事件的 API 完全不同，canvas 坐标系也不同。**

### 3.1 桌面端（App.vue）

```js
// 鼠标事件
@mousedown  → isDrawing = true, 记录起点
@mousemove  → 追加点、实时绘制
@mouseup    → 保存笔画、isDrawing = false
@mouseleave → 同上（鼠标移出 canvas 视为结束）

// 浮动工具栏
el-button + el-slider + el-divider  → Element Plus 组件
固定在中间栏顶部
```

存储：`localStorage.ett_annotations[{essayId}][{color, width, points: [{x,y}]}]`

### 3.2 移动端（MobileApp.vue）

```js
// 触摸事件（❗不能用 mouse 事件）
@touchstart  → 获取 e.touches[0] 坐标、isDrawing = true
@touchmove   → 获取 e.touches[0] 坐标、实时绘制
@touchend    → 保存笔画、isDrawing = false

// 坐标转换（canvas 物理像素 ≠ CSS 像素）
const rect = canvas.getBoundingClientRect()
const scaleX = canvas.width / rect.width    // devicePixelRatio
const scaleY = canvas.height / rect.height
return {
  x: (e.touches[0].clientX - rect.left) * scaleX,
  y: (e.touches[0].clientY - rect.top) * scaleY
}
```

存储：`localStorage.ett_annotations_mob[{essayId}][{color, width, points: [{x,y}]}]`

**注意：两个存储 key 是分开的**（`ett_annotations` vs `ett_annotations_mob`），因为坐标空间不同，无法互通。

### 3.3 指针模式（移动端特有）

桌面端批注不影响正常操作（鼠标点击工具栏即可切换工具）。移动端不行——一旦 canvas 盖满屏幕，触摸全被拦截，无法操作下方页面。

**解决方案：三模式切换**

```
✏️ 笔  |  🧹 橡皮  |  ↖ 指针
```

- 笔/橡皮模式：canvas `pointer-events: auto` + `e.preventDefault()` 拦截触摸
- 指针模式：canvas `pointer-events: none`，触摸穿透到下层页面，涂鸦保留可见

这是参考希沃白板的箭头图标设计——点箭头进入"只读模式"，操作页面同时不丢涂鸦。

### 3.4 悬浮工具栏（移动端）

```html
<div class="mob-anno-toolbar"
  @touchstart="onToolbarDragStart"
  @touchmove="onToolbarDragMove"
  @touchend="onToolbarDragEnd">
```

- 整个工具栏可拖拽移动
- 可折叠（只剩一个 🖊️ 图标）
- 笔/橡皮/指针三模式互斥
- 颜色选择 + 粗细滑块只在前两种模式显示
- 进入批注时智能定位到悬浮球附近

### 3.5 悬浮入口球（移动端特有）

```html
<div class="mob-anno-entry"
  @touchstart="onEntryDragStart"
  @touchmove="onEntryDragMove"
  @touchend="onEntryDragEnd"
  @click="onEntryClick">
```

设计参照 iOS AssistiveTouch：
- **可拖拽**：手指任意拖动到屏幕任何位置
- **拖拽时 1:1 跟手**：临时关闭 CSS transition，手指动多少球就动多少
- **松手 2 秒后半收起**：transform 向最近的屏幕边缘滑出 28px（44px 球只露 16px），透明度降到 35%
- **点击收回的球**：展开并进入批注模式
- **拖拽收回的球**：先展开再跟手移动
- **位置持久化**：`localStorage.ett_entry_pos`

**踩过的坑：**

1. `@touchstart.prevent` 阻止了 `@click` 合成 → 点击无效。改为移动超过 5px 才 `preventDefault`。
2. 收起时用 `left` 改位置 → 球的"家"跟着变。改用 `transform: translateX()` 做视觉位移，`left` 始终不变。
3. CSS transition 在拖拽时产生 0.4s 延迟 → 拖拽时 `el.style.transition = 'none'`。

---

## 四、API 调用共享

桌面端和移动端走**完全相同的 API 调用代码**（在 App.vue 中）：

```js
async function callDeepSeek(prompt, temperature = 0.7) {
  const res = await fetch('https://api.deepseek.com/v1/chat/completions', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${apiKey.value}`,
               'Content-Type': 'application/json' },
    body: JSON.stringify({
      model: 'deepseek-chat',
      messages: [{ role: 'user', content: prompt }],
      temperature
    })
  })
  // 追踪 token 用量
  const data = await res.json()
  tokenUsage.value.prompt += data.usage.prompt_tokens
  tokenUsage.value.completion += data.usage.completion_tokens
  tokenUsage.value.total += data.usage.total_tokens
  tokenUsage.value.calls++
  return data.choices[0].message.content
}
```

DeepSeek API 直连，不走代理。Token 用量实时追踪显示在桌面端顶栏。

---

## 五、数据持久化

### 5.1 双层存储

| 层 | 用途 | 实现 |
|----|------|------|
| localStorage | 主存储 | `ett_backup` key，JSON blob 包含所有数据 |
| Capacitor Filesystem | 备份导出/导入 | `@capacitor/filesystem` 写文件到 Downloads |

### 5.2 数据模型（一个 JSON blob）

```json
{
  "essays": [{id, title, source, date, originalEN, referenceTranslation, segments}],
  "records": [{id, essayId, date, userTranslation, score, feedback, timeSpent}],
  "vocabPool": [{item, meaning, level, count, dateCount}],
  "starredItems": {"word": true},
  "phraseCards": [{id, title, pairs[{en, zh}], practiceState}],
  "waveCache": {"essayId-0": {answer, timestamp}},
  "customPrompts": [{id, name, content}],
  "tokenUsage": {prompt, completion, total, calls}
}
```

- 全量存在一个 `ett_backup` key 下
- `syncData()` 在每次修改后自动调用（带 800ms 防抖）
- 导出/导入就是 JSON 文件的读写

### 5.3 offline 兜底

vite-plugin-pwa 生成 Service Worker，缓存策略：
- **JS/CSS/静态数据**（essays-data.json）→ CacheFirst
- **DeepSeek API 请求** → NetworkFirst（失败时用上次成功响应兜底）
- 首次打开后基本可离线使用（API 评分除外）

---

## 六、APK 封装完整链路

详见 [APK封装流程.md](./APK封装流程.md)，这里只记关键决策和最近修复的坑。

### 6.1 为什么选 Capacitor 而非 WebView 套壳

| 方案 | 优点 | 缺点 |
|------|------|------|
| 纯 WebView | 零依赖 | 没有原生 API（文件系统/分享） |
| Capacitor | 自动生成 Android 壳、插件生态、文件系统 API、App 生命周期 | 多一层构建依赖 |
| React Native/Flutter | 原生性能 | 需要重写整个 App |

Capacitor 是最佳选择：**0 行原生代码**（MainActivity 继承 BridgeActivity，1 行），前端代码完全复用。

### 6.2 构建流程

```bash
rebuild-apk.sh:
  1. npm run build          → Vite 构建 → dist/
  2. 更新 versionCode       → sed 写入 build.gradle
  3. npx cap sync android   → 复制 dist/ 到 android/assets/public/
  4. ./gradlew assembleDebug → 编译 APK
  5. 重命名 APK（带时间戳）
```

### 6.3 versionCode 自动递增（2026-07-08 修复）

**问题：** Android 靠 `versionCode`（整数）判断 APK 是否比已安装版本新。`build.gradle` 里 `versionCode 1` 是写死的，每次构建都是 1，系统拒绝覆盖安装。

**修复：**编译脚本中用 `date +%m%d%H%M` 自动更新 versionCode。

**踩过的两个 Groovy 坑：**

1. `202607081230`（12位）→ 超过 Java int 最大值 2,147,483,647。Groovy 会把它变成 Long，而 `versionCode` 的 setter 只接受 int → `Value is null`
2. `07081230`（8位，0开头）→ Groovy 把 0 开头的数字解析为八进制。`8` 和 `9` 不是合法的八进制数字 → `Invalid octal number`

**最终方案：** `1$(date +%m%d%H%M)` → `107081230`。以 1 开头避开八进制解析，长度 9 位远小于 int 上限，且严格单调递增。

### 6.4 环境要求

| 组件 | 路径 | 备注 |
|------|------|------|
| JDK 17 | `D:\java\jdk-17` | AGP 8.x 强制，不能高不能低 |
| Android SDK | `D:\android-sdk` | platform 34 + build-tools 34 |
| Gradle 缓存 | `D:\gradle-cache` | 不能放 C 盘 |

三者必须放 D 盘——C 盘仅剩 3GB。

---

## 七、移植 checklist（新项目复用）

把一个 Vue 桌面 Web 应用移植为 APK 的完整步骤：

### 阶段 A：代码适配（不改业务逻辑）
- [ ] 创建 `MobileApp.vue`，用 `inject` 消费父组件状态
- [ ] 父组件 `provide` 所有共享状态
- [ ] 添加 `isMobile` 检测（`window.innerWidth < 768`）
- [ ] 父组件 template 用 `v-if="!isMobile"` / `v-if="isMobile"` 分支
- [ ] 弹窗适配：有键盘→全屏遮罩，纯浏览→底部抽屉
- [ ] 触摸事件替换鼠标事件（`mousedown` → `touchstart` 等）
- [ ] canvas 触摸坐标加 devicePixelRatio 转换
- [ ] 吸顶区域加 `position: sticky` + `-webkit-sticky`
- [ ] 底部导航加 `env(safe-area-inset-bottom)` 适配刘海屏

### 阶段 B：Capacitor 初始化（每个项目一次）
- [ ] `npm i @capacitor/core @capacitor/cli @capacitor/android`
- [ ] `npx cap init "App名" "com.xxx.yyy"`
- [ ] `npx cap add android`
- [ ] 替换启动图标（mipmap 多密度）和应用名（strings.xml）
- [ ] 配置 splash screen

### 阶段 C：构建工具链（每台机器一次）
- [ ] 装 JDK 17 到 D 盘
- [ ] 装 Android SDK（cmdline-tools → sdkmanager → platform/build-tools）
- [ ] Gradle 首次下载（需要镜像，默认源连不上）
- [ ] 设置三个环境变量（JAVA_HOME / ANDROID_HOME / GRADLE_USER_HOME）

### 阶段 D：构建脚本
- [ ] 写 `rebuild-apk.sh`：build → versionCode → sync → gradle → rename
- [ ] versionCode 用 `1$(date +%m%d%H%M)` 自动递增

---

## 八、关键教训

1. **不要试图用 CSS 媒体查询把桌面端"挤"成手机端。** 手势体系不同（hover 在手机上不存在）、信息密度不同（三栏变一栏不是简单的 flex-wrap）、原生能力不同（鼠标精确点击 vs 手指 7mm 触摸目标）。直接写两套 template。

2. **触摸事件和 click 事件的关系。** 移动端 click 由 touchstart → touchend 序列合成。如果在 touchstart 里调了 `preventDefault()`，click 永远不会触发。正确做法：只在确认是拖拽（移动超阈值）后才 preventDefault。

3. **Canvas 坐标系。** canvas.width/height 是物理像素（受 devicePixelRatio 影响），getBoundingClientRect 返回 CSS 像素。触摸坐标必须乘以 `canvas.width / rect.width` 转换。

4. **Groovy 数字字面量的坑。** 0 开头 = 八进制。超过 2^31-1 = Long 不是 int。发生在 versionCode 这种看似无关的地方。

5. **Gradle 一句报错可能和代码无关。** `Value is null` 实际是 Groovy 把 Long 传给 int setter 失败。"Invalid octal number" 是数字里有 8/9 但被当成八进制解析。错误信息的字面意义都比你想的更 literal。

6. **Capacitor 的 appId 一旦发布就不能改。** Google Play 用 appId 作为唯一标识。`cap init` 时就要确定。

7. **微信/支付宝等第三方 SDK 不能直接在 WebView 里用。** Capacitor 的 WebView 运行的是 Web 代码，没有原生 Android Context。需要写 Capacitor Plugin 桥接。这个项目没用到所以不受影响。
