# EnglishTranslationTool-mobile v2.0 — 技术方案

## 背景

评分系统存在三个问题待修复：
1. `mistakeWave` 只抓一个错误结构，AI的 `feedback` 里指出了全部错误但没有形成闭环
2. 手机版导出 JSON 走浏览器 Blob 下载，WebView 里用户不知道文件去哪了
3. 版本号始终 1.0.0，APK 总是覆盖

## 执行链

```
P0: 提示词重构 ──→ P1: 数据解析适配 ──→ P2: 桌面UI ──→ 验证
                                      └─→ P3: 手机UI ──→ 验证
                    P4: 导出改造 ──────────────────────→ 验证
                    P5: 版本时间戳 ────────────────────→ 验证
```

---

## P0: 评分提示词重构

### 文件：`src/App.vue` — 两个常量

#### SCORING_SYSTEM_PROMPT（英→中评分，第1848行起）

**改 `mistakeWave`（单对象）→ `mistakeWaves`（数组）：**

原指令（1899行）：
> mistakeWave：只选取学生译文中最典型的一处结构性错误进行分析，不必面面俱到。

改写成：
> mistakeWaves：对feedback中逐句指出的每一个结构性错误（如语序混乱、修饰位置错、英文硬翻、搭配断裂、否定漏译等），各生成一条水波分析。如果某句没有结构性错误则跳过。词汇性错误（单词不认识/选错词义）不放在这里，放在translationErrors。

新增字段 `sentenceIndex`（0-based，对应第几句）、`errorType`（错误类型中文标签）、`studentError`（学生译文中的具体错译片段）。

**新增 `translationErrors`：**

逐条列出词汇/短语翻译错误：
- `originalEN`：原文单词/短语
- `correctZH`：正确中文
- `studentZH`：学生错译成什么
- `note`：一句话提示（如"固定搭配不能逐字翻"）

#### REVERSE_SCORING_PROMPT（中→英反转评分，第1930行起）

同样改动，但字段语义适配反转模式：
- `mistakeWaves[].patternEN` = 学生英译中出错的英文片段
- `translationErrors[].originalEN` = 学生写的错误英文
- `translationErrors[].correctZH` → `correctEN` = 正确英文表达

### JSON schema 最终形态

```json
{
  "accuracy": 20, "grammar": 18, "vocabulary": 19, "fluency": 21,
  "total": 78,
  "feedback": "逐句点评...",
  "mistakeWaves": [
    {
      "sentenceIndex": 0,
      "errorType": "修饰位置错误",
      "studentError": "与显著的缺少直接地...",
      "patternEN": "contrasted with a pronounced lack of need for...",
      "whereStuck": "为什么卡的白话解释...",
      "examples": [{"en": "...", "zh": "..."}],
      "nextTime": "下次怎么拆..."
    }
  ],
  "translationErrors": [
    {
      "originalEN": "immediate family",
      "correctZH": "直系亲属",
      "studentZH": "短暂的家庭",
      "note": "固定搭配，immediate 在这里不是'立即的'"
    }
  ],
  "unknownItems": [...],
  "errorSpans": [...]
}
```

---

## P1: 数据解析 + 存储适配

### 文件：`src/App.vue`

#### saveScoreResult()（第2195-2229行）

- `parsed.mistakeWave` → `parsed.mistakeWaves`（数组）
- 新增 `parsed.translationErrors` → `record.translationErrors`
- **向后兼容**：如果旧数据有 `mistakeWave`（对象且非"无"），前端归一化成 `mistakeWaves: [{...mistakeWave, sentenceIndex: null, errorType: "结构性错误", studentError: ""}]`

#### saveReverseScoreResult()（第2319-2348行）

- 同样改法

#### 兼容辅助函数（新增）

```js
function normalizeMistakeWaves(record) {
  // 新格式优先
  if (record.mistakeWaves && Array.isArray(record.mistakeWaves)) return record.mistakeWaves
  // 旧格式归一化
  if (record.mistakeWave && record.mistakeWave.patternEN && record.mistakeWave.patternEN !== '无') {
    return [{ sentenceIndex: null, errorType: '结构性错误', studentError: '', ...record.mistakeWave }]
  }
  return []
}
```

---

## P2: 桌面端UI

### 文件：`src/App.vue` — 右侧评分面板（行380-402）

当前结构：
```
🌊 错误结构分析
  ├── patternEN
  ├── whereStuck
  ├── 同类例句 ×2
  └── nextTime
```

改成：
```
🌊 错误结构分析（N处）
  ├── 第1句 · 修饰位置错误
  │   ├── 错误片段：contrasted with...
  │   ├── 学生错译：与显著的缺少...
  │   ├── 为什么卡：...
  │   ├── 同类例句 ×2
  │   └── 下次怎么做：...
  ├── 第2句 · 搭配断裂
  │   └── ...
  └── ...

📋 翻译错误对照
  | 原文 | 正确翻译 | 你的翻译 | 提示 |
  | immediate family | 直系亲属 | 短暂的家庭 | 固定搭配 |
  | ties | 关系/羁绊 | 问题 | 熟词僻义 |
```

具体实现：`v-for` 遍历 `normalizeMistakeWaves(rightPanelRecord)`，每项一张 `.mw-card`。translationErrors 用简单的表格样式。

---

## P3: 手机端UI

### 文件：`src/components/MobileApp.vue`（行123-148）

与P2同样逻辑，用 `.mob-wave-box` 包裹每个 mistakeWave 条目，translationErrors 用移动端适配的列表。展示顺序与桌面端一致。

关键：手机端 `rightPanelRecord` 来自 `$.rightPanelRecord`（inject 的 computed），数据结构与桌面端相同，所以只需改模板。

---

## P4: 导出功能

### 文件：`src/App.vue` — `exportData()`（行2454-2473）

**新增导出目录选择 + 记忆：**
```js
const exportDir = ref(localStorage.getItem('ett_export_dir') || '')
```

**逻辑：**
1. 如果有 `exportDir`，直接写文件到该目录，Toast 显示路径
2. 如果没有，弹出系统文件夹选择器（用 Capacitor 的 `Directory` + 原生 intent 或提示用户手动输入路径）
3. 写成功后 Toast 显示完整路径

**分享按钮：**
```js
async function shareBackup() {
  const json = buildBackupJSON()
  // Web Share API
  if (navigator.share && navigator.canShare) {
    const file = new File([JSON.stringify(json)], `ett-backup-${date}.json`, { type: 'application/json' })
    await navigator.share({ files: [file], title: 'ETT数据备份' })
  } else {
    // fallback: 写临时文件 + 提示路径
  }
}
```

### 注意事项
- 桌面端 `exportData()` 保持 Blob 下载（浏览器支持好）
- 手机端（`isMobile = true`）走 Capacitor Filesystem + 分享
- `buildBackupJSON()` 抽取为公共函数，avoid 重复

---

## P5: 版本时间戳

### 文件：`vite.config.js`

注入构建时间全局常量：
```js
define: {
  __BUILD_TIME__: JSON.stringify(new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19))
}
```

### 文件：`rebuild-apk.sh`

```bash
# 构建后重命名
TIMESTAMP=$(date +%Y%m%d-%H%M)
cp app/build/outputs/apk/debug/app-debug.apk \
   "app/build/outputs/apk/debug/ett-v${TIMESTAMP}.apk"
echo "APK: ett-v${TIMESTAMP}.apk"
```

### 文件：`package.json`

`rebuild-apk.sh` 里加一步：
```bash
# 自动更新 version
node -e "const p=require('./package.json');p.version='1.0.'+Date.now().toString(36);require('fs').writeFileSync('./package.json',JSON.stringify(p,null,2))"
```

---

## 验证计划

1. **P0+P1**：打开浏览器 `localhost:5173`，选一篇范文，用窗口AI模式粘贴测试JSON，确认解析后 `mistakeWaves` 数组和 `translationErrors` 数组正确存入 record
2. **P2**：右侧面板确认多个错误结构卡片渲染、翻译错误对照表显示
3. **P3**：F12切375px移动端视图，确认 MobileApp 同样正确渲染
4. **P4**：手机上实测导出，确认文件出现在指定目录，Toast 显示路径
5. **P5**：跑 `rebuild-apk.sh`，确认 APK 文件名带时间戳、package.json version 已更新
