<template>
  <div class="ett-mobile-body" :style="{ '--ett-fs': fontScale }">
    <!-- Tab 1: 范文库 -->
    <div v-if="activeTab === 'essays'" class="mobile-tab">
      <div class="mob-header">
        <span class="mob-title">范文库</span>
        <span class="mob-count">{{ $.essays.length }}篇</span>
      </div>
      <div class="mob-stats-bar">
        <div class="mob-stat"><b>{{ $.streakDays }}</b>天连续</div>
        <div class="mob-stat"><b>{{ $.records.filter(r=>r.completed).length }}</b>篇已练</div>
        <div class="mob-stat"><b>{{ $.avgScore }}</b>均分</div>
        <div class="mob-stat"><b>{{ $.totalTime }}</b>总时</div>
      </div>
      <div class="mob-essay-list">
        <div v-for="e in $.essays" :key="e.id" class="mob-essay-item"
          :class="{ active: e.id === $.currentEssayId }"
          @click="$.currentEssayId = e.id; activeTab = 'practice'">
          <div class="mob-essay-info">
            <span class="mob-essay-title">{{ e.title }}</span>
            <span class="mob-essay-src">{{ e.source }}</span>
          </div>
          <span class="mob-essay-score" v-if="$.getRecord(e.id)?.completed"
            :style="{color: $.getRecord(e.id).totalScore>=80?'#22C55E':'#F59E0B'}">{{ $.getRecord(e.id).totalScore }}分</span>
          <span v-if="$.getRecord(e.id)?.completed" class="mob-essay-hist" @click.stop="$.openHistoryPanel(e.id)">历史</span>
          <span class="mob-essay-new" v-else>新</span>
        </div>
      </div>
      <div class="mob-cal-strip">
        <span style="font-size:calc(10px * var(--ett-fs, 1));color:#888;margin-right:4px">📅</span>
        <span v-for="d in 14" :key="d" class="cal-dot-sm" :style="{background: d<=10?'#22C55E':'#374151'}"></span>
      </div>
      <div class="mob-bottom-actions">
        <div class="mob-action-btn primary" @click="$.showAddDialog = true">+ 添加范文</div>
        <div class="mob-action-btn success" @click="$.openImageImport">📷 图片导入</div>
      </div>
    </div>

    <!-- Tab 2: 练习 -->
    <div v-if="activeTab === 'practice'" class="mobile-tab">
      <div class="mob-header">
        <span style="font-size:calc(11px * var(--ett-fs, 1));color:#888" @click="activeTab='essays'">← 范文库</span>
        <span class="mob-title">{{ $.currentEssay ? $.currentEssay.title : '翻译练习' }}</span>
        <span style="font-size:calc(10px * var(--ett-fs, 1));color:#409eff" @click="$.showPromptConfig=true">⚙ 提示词</span>
      </div>
      <div class="mob-mode-pills">
        <span v-for="m in [{k:'api',l:'API评分'},{k:'window',l:'窗口AI'},{k:'wave',l:'水波'},{k:'reverse',l:'反转'}]" :key="m.k"
          class="mob-pill" :class="{ active: $.scoringMode === m.k }" @click="$.scoringMode = m.k">{{ m.l }}</span>
      </div>
      <div class="mob-practice-scroll" v-if="$.currentEssay">
        <!-- 原文（水波模式隐藏——水波有自己的句子列表） -->
        <template v-if="$.scoringMode !== 'wave'">
          <div class="mob-src-bar" @click="mobSrcShow = !mobSrcShow">
            <span style="font-size:calc(10px * var(--ett-fs, 1));color:#aaa">📄 原文 · {{ $.currentEssay.source }} {{ mobSrcShow ? '▾' : '▸' }}</span>
            <span class="mob-src-count">{{ $.currentEssay.segments.length }}段</span>
          </div>
          <div v-if="mobSrcShow" class="mob-src-body">
            <div class="mob-src-scroll" ref="srcScrollRef">
              <div v-for="(seg, i) in $.currentEssay.segments" :key="i" class="mob-seg"
                :class="{ sel: mobSeg === i }" @click="mobSeg = i">
                <span class="mob-seg-num">{{ i + 1 }}</span>
                <span class="mob-seg-en" @click.stop="$.onWordClick($event)">{{ seg.en }}</span>
                <span class="mob-seg-kp" v-if="seg.keyPoints.length">{{ seg.keyPoints.join(' · ') }}</span>
              </div>
            </div>
            <div class="mob-src-nav">
              <span class="mob-src-arrow" @click="mobSeg > 0 ? mobSeg-- : null" :style="{opacity: mobSeg > 0 ? 1 : 0.3}">◀</span>
              <div class="mob-src-dots">
                <span v-for="i in $.currentEssay.segments.length" :key="i" class="mob-src-dot" :class="{ on: mobSeg === i-1 }" @click="mobSeg = i-1"></span>
              </div>
              <span class="mob-src-arrow" @click="mobSeg < $.currentEssay.segments.length - 1 ? mobSeg++ : null" :style="{opacity: mobSeg < $.currentEssay.segments.length - 1 ? 1 : 0.3}">▶</span>
            </div>
          </div>
        </template>
        <!-- 翻译输入 -->
        <div class="mob-section-label">✏️ 你的译文 <span style="color:#888;font-weight:400">⏱ {{ $.formatTime($.elapsed) }}</span></div>
        <textarea v-if="$.scoringMode !== 'wave'" class="mob-textarea" v-model="$.userTranslation" placeholder="在此输入你的中文翻译..."></textarea>
        <div class="mob-action-row" v-if="$.scoringMode === 'api'">
          <div class="mob-submit-btn" @click="$.submitTranslation" :style="{opacity: $.userTranslation.trim()?1:0.5}">提交 AI 评分</div>
        </div>
        <div class="mob-action-row" v-if="$.scoringMode === 'window'" style="display:flex;gap:6px">
          <div class="mob-submit-btn" style="background:#e6a23c;flex:1" @click="$.submitTranslation">复制拼接 prompt</div>
          <div class="mob-submit-btn" style="background:#409eff;flex:1" @click="openQwenMobile">🌐 {{ qwenUrl.trim() ? '打开 AI' : '打开 Qwen' }}</div>
        </div>
        <div v-if="$.scoringMode === 'window'" class="mob-paste-area">
          <div class="mob-section-label" style="color:#e6a23c">📋 粘贴 AI 返回的 JSON</div>
          <textarea class="mob-textarea" v-model="$.windowAIInput" placeholder='{"accuracy":20,...}' style="height:60px;font-size:calc(10px * var(--ett-fs, 1))"></textarea>
          <div class="mob-submit-btn" style="background:#22C55E;margin-top:4px" @click="$.submitWindowAI">解析并录入评分</div>
        </div>
        <!-- 评分结果 -->
        <template v-if="$.rightPanelRecord">
          <div class="mob-section-label">📊 评分结果</div>
          <div class="mob-score-row">
            <div style="font-size:calc(36px * var(--ett-fs, 1));font-weight:700" :style="{color: $.scoreColor($.rightPanelRecord.totalScore)}">{{ $.rightPanelRecord.totalScore }}</div>
            <div style="font-size:calc(10px * var(--ett-fs, 1));color:#888;margin-left:4px">/100</div>
            <div style="flex:1"></div>
            <div class="mob-dims">
              <div class="mob-dim"><span>准确性</span><div class="mob-dim-bar"><div :style="{width: $.rightPanelRecord.score.accuracy/25*100+'%',background:'#22C55E'}"></div></div><span>{{ $.rightPanelRecord.score.accuracy }}</span></div>
              <div class="mob-dim"><span>语法</span><div class="mob-dim-bar"><div :style="{width: $.rightPanelRecord.score.grammar/25*100+'%',background:'#409eff'}"></div></div><span>{{ $.rightPanelRecord.score.grammar }}</span></div>
              <div class="mob-dim"><span>词汇</span><div class="mob-dim-bar"><div :style="{width: $.rightPanelRecord.score.vocabulary/25*100+'%',background:'#e6a23c'}"></div></div><span>{{ $.rightPanelRecord.score.vocabulary }}</span></div>
              <div class="mob-dim"><span>流畅</span><div class="mob-dim-bar"><div :style="{width: $.rightPanelRecord.score.fluency/25*100+'%',background:'#a855f7'}"></div></div><span>{{ $.rightPanelRecord.score.fluency }}</span></div>
            </div>
          </div>
          <div class="mob-fb-card">
            <div class="mob-fb-title">AI 点评</div>
            <div class="mob-fb-text" v-html="$.renderedFeedback"></div>
          </div>
          <!-- 译文对照 -->
          <div v-if="$.diffResult.userLines.length && $.scoringMode !== 'reverse'" class="mob-section-label">译文对照 <span style="font-weight:400;font-size:calc(8px * var(--ett-fs, 1));color:#888">🟢匹配 🟡差异 🔴缺失</span></div>
          <div v-if="$.diffResult.userLines.length && $.scoringMode !== 'reverse'" class="mob-cmp">
            <div v-for="(line,i) in $.diffResult.userLines" :key="'c'+i" style="margin-bottom:6px">
              <div :style="{color: line.type==='match'?'#22C55E':line.type==='diff'?'#e6a23c':'#ef4444', fontSize: 'calc(9px * var(--ett-fs, 1))', lineHeight: '1.6'}">
                <b :style="{color: line.type==='match'?'#22C55E':'#f87171'}">你：</b>
                <span v-if="line.html" v-html="line.html"></span>
                <span v-else>{{ line.text }}</span>
              </div>
              <div :style="{color: ($.diffResult.refLines[i]?.type==='match'?'#22C55E':'#888'), fontSize: 'calc(9px * var(--ett-fs, 1))', lineHeight: '1.6'}">
                <b :style="{color: ($.diffResult.refLines[i]?.type==='match'?'#22C55E':'#888')}">参：</b>
                <span>{{ $.diffResult.refLines[i]?.text || '(空)' }}</span>
              </div>
            </div>
          </div>
          <!-- 错误结构分析（水波纠错） -->
          <template v-if="$.normalizeMistakeWaves($.rightPanelRecord).length && $.scoringMode !== 'reverse'">
            <div class="mob-section-label">🌊 错误结构分析（{{ $.normalizeMistakeWaves($.rightPanelRecord).length }}处）</div>
            <div v-for="(mw, wi) in $.normalizeMistakeWaves($.rightPanelRecord)" :key="'mw'+wi" class="mob-wave-box">
              <div style="display:flex;align-items:center;gap:6px;margin-bottom:6px">
                <span v-if="mw.sentenceIndex !== null" style="font-size:calc(9px*var(--ett-fs,1));background:#409eff;color:#fff;padding:1px 6px;border-radius:8px">第{{ mw.sentenceIndex + 1 }}句</span>
                <span style="font-size:calc(9px*var(--ett-fs,1));background:#e6a23c;color:#fff;padding:1px 6px;border-radius:8px">{{ mw.errorType || '结构性错误' }}</span>
              </div>
              <div class="mob-wave-row" v-if="mw.studentError">
                <span class="mob-wave-lbl">学生错译</span>
                <span style="font-size:calc(9px*var(--ett-fs,1));color:#ef4444;line-height:1.5">{{ mw.studentError }}</span>
              </div>
              <div class="mob-wave-row" v-if="mw.patternEN">
                <span class="mob-wave-lbl">卡住的{{ $.scoringMode==='reverse'?'英文表达':'英文结构' }}</span>
                <span class="mob-wave-en">{{ mw.patternEN }}</span>
              </div>
              <div class="mob-wave-row" v-if="mw.whereStuck">
                <span class="mob-wave-lbl">为什么容易卡</span>
                <span class="mob-wave-zh">{{ mw.whereStuck }}</span>
              </div>
              <div v-if="mw.examples?.length">
                <div class="mob-wave-lbl" style="padding:2px 0">💡 同类例句</div>
                <div v-for="(ex,i) in mw.examples" :key="'ex'+i" class="mob-wave-ex">
                  <span class="mob-wave-ex-en">{{ ex.en }}</span>
                  <span class="mob-wave-ex-arrow">→</span>
                  <span class="mob-wave-ex-zh">{{ ex.zh }}</span>
                </div>
              </div>
              <div class="mob-wave-row" v-if="mw.nextTime">
                <span class="mob-wave-lbl">下次遇到怎么拆</span>
                <span class="mob-wave-tip">{{ mw.nextTime }}</span>
              </div>
            </div>
          </template>
          <!-- 翻译错误对照（手机） -->
          <template v-if="$.rightPanelRecord?.translationErrors?.length">
            <div class="mob-section-label">📋 翻译错误对照</div>
            <div v-for="(te, i) in $.rightPanelRecord.translationErrors" :key="'te'+i" class="mob-wave-box" style="padding:8px 10px">
              <div style="display:flex;flex-wrap:wrap;gap:4px;font-size:calc(9px*var(--ett-fs,1));line-height:1.5">
                <span style="color:#ff5f00;font-family:monospace;margin-right:4px">{{ te.originalEN }}</span>
                <span style="color:#888">→</span>
                <span style="color:#22C55E;margin:0 4px">{{ $.scoringMode==='reverse' ? (te.correctEN || '') : (te.correctZH || '') }}</span>
                <span style="color:#888">（你译：</span>
                <span style="color:#ef4444;text-decoration:line-through">{{ $.scoringMode==='reverse' ? (te.studentEN || '') : (te.studentZH || '') }}</span>
                <span style="color:#888">）</span>
              </div>
              <div v-if="te.note" style="font-size:calc(8px*var(--ett-fs,1));color:#666;margin-top:2px">{{ te.note }}</div>
            </div>
          </template>
        </template>
        <!-- 水波训练 -->
        <template v-if="$.scoringMode === 'wave'">
          <div class="mob-section-label">🌊 点击句子分析</div>
          <div v-for="(seg, i) in $.currentEssay.segments" :key="i" class="mob-wave-item"
            :class="{ sel: $.waveSelectedIdx === i }" @click="$.selectWaveSegment(i)">
            <span class="mob-wave-num">{{ i+1 }}</span>
            <span class="mob-wave-txt">{{ seg.en.slice(0,50) }}...</span>
          </div>
          <div v-if="$.waveAnswer" class="mob-fb-card">
            <div class="mob-fb-title">📝 第{{ $.waveSelectedIdx+1 }}句分析</div>
            <div v-if="$.waveAnswer.grammarTree" style="font-size:calc(9px * var(--ett-fs, 1));color:#aaa;line-height:1.5">{{ $.waveAnswer.grammarTree }}</div>
          </div>
        </template>
        <!-- 反转训练 -->
        <template v-if="$.scoringMode === 'reverse'">
          <div class="mob-section-label">📖 中文参考译文</div>
          <div class="mob-rev-ref" v-if="$.reverseDisplayRef">
            <p v-for="(l,i) in $.reverseDisplayRef.split('\n').filter(Boolean)" :key="i" style="font-size:calc(10px * var(--ett-fs, 1));color:#bbb;line-height:1.6">{{ l }}</p>
          </div>
          <div class="mob-section-label">✏️ 你的英译</div>
          <textarea class="mob-textarea" v-model="$.reverseUserTranslation" placeholder="根据中文参考，输入英文翻译..."></textarea>
          <div class="mob-action-row" style="display:flex;gap:6px">
            <div class="mob-submit-btn" style="flex:1" @click="$.submitReverseTranslation">API评分</div>
            <div class="mob-submit-btn" style="background:#e6a23c;flex:1" @click="$.copyReversePrompt">复制prompt</div>
          </div>
          <!-- 反转评分结果 -->
          <template v-if="$.rightPanelRecord && $.rightPanelRecord.type === 'reverse'">
            <div class="mob-section-label">📊 评分结果</div>
            <div class="mob-score-row">
              <div style="font-size:calc(36px * var(--ett-fs, 1));font-weight:700" :style="{color: $.scoreColor($.rightPanelRecord.totalScore)}">{{ $.rightPanelRecord.totalScore }}</div>
              <div style="font-size:calc(10px * var(--ett-fs, 1));color:#888;margin-left:4px">/100</div>
              <div style="flex:1"></div>
              <div class="mob-dims">
                <div class="mob-dim"><span>准确性</span><div class="mob-dim-bar"><div :style="{width: $.rightPanelRecord.score.accuracy/25*100+'%',background:'#22C55E'}"></div></div><span>{{ $.rightPanelRecord.score.accuracy }}</span></div>
                <div class="mob-dim"><span>语法</span><div class="mob-dim-bar"><div :style="{width: $.rightPanelRecord.score.grammar/25*100+'%',background:'#409eff'}"></div></div><span>{{ $.rightPanelRecord.score.grammar }}</span></div>
                <div class="mob-dim"><span>词汇</span><div class="mob-dim-bar"><div :style="{width: $.rightPanelRecord.score.vocabulary/25*100+'%',background:'#e6a23c'}"></div></div><span>{{ $.rightPanelRecord.score.vocabulary }}</span></div>
                <div class="mob-dim"><span>流畅</span><div class="mob-dim-bar"><div :style="{width: $.rightPanelRecord.score.fluency/25*100+'%',background:'#a855f7'}"></div></div><span>{{ $.rightPanelRecord.score.fluency }}</span></div>
              </div>
            </div>
            <div class="mob-fb-card">
              <div class="mob-fb-title">AI 点评</div>
              <div class="mob-fb-text" v-html="$.renderedFeedback"></div>
            </div>
            <!-- 反转译文对照 -->
            <div v-if="$.reverseDiffResult.userLines.length" class="mob-section-label">译文对照（反转）</div>
            <div v-if="$.reverseDiffResult.userLines.length" class="mob-cmp">
              <div v-for="(line,i) in $.reverseDiffResult.userLines" :key="'rc'+i" style="margin-bottom:6px">
                <div :style="{color: line.type==='match'?'#22C55E':line.type==='diff'?'#e6a23c':'#ef4444', fontSize: 'calc(9px * var(--ett-fs, 1))', lineHeight: '1.6'}">
                  <b :style="{color: line.type==='match'?'#22C55E':'#f87171'}">你：</b>
                  <span v-if="line.html" v-html="line.html"></span>
                  <span v-else>{{ line.text }}</span>
                </div>
                <div :style="{color: ($.reverseDiffResult.refLines[i]?.type==='match'?'#22C55E':'#888'), fontSize: 'calc(9px * var(--ett-fs, 1))', lineHeight: '1.6'}">
                  <b :style="{color: ($.reverseDiffResult.refLines[i]?.type==='match'?'#22C55E':'#888')}">英文原文：</b>
                  <span>{{ $.reverseDiffResult.refLines[i]?.text || '(空)' }}</span>
                </div>
              </div>
            </div>
          </template>
        </template>
      </div>
      <el-empty v-if="!$.currentEssay" description="请先在范文库选择一篇范文" :image-size="80" style="margin-top:40px" />
      <div class="mob-bottom-actions">
        <div class="mob-action-btn" style="background:#374151" @click="$.practiceStarted ? null : $.startPractice()">
          {{ $.practiceStarted ? '⏱ ' + $.formatTime($.elapsed) : '开始练习' }}
        </div>
      </div>
    </div>

    <!-- Tab 3: 我的 -->
    <div v-if="activeTab === 'mine'" class="mobile-tab">
      <div class="mob-header">
        <span class="mob-title">我的</span>
        <span style="font-size:calc(10px * var(--ett-fs, 1));color:#888">考研英语一</span>
      </div>
      <div class="mob-stats-hero">
        <div class="mob-stat-big"><span class="mob-stat-num">{{ $.streakDays }}</span><span class="mob-stat-lbl">连续打卡</span></div>
        <div class="mob-stat-div"></div>
        <div class="mob-stat-big"><span class="mob-stat-num">{{ $.records.filter(r=>r.completed).length }}</span><span class="mob-stat-lbl">累计篇数</span></div>
        <div class="mob-stat-div"></div>
        <div class="mob-stat-big"><span class="mob-stat-num">{{ $.avgScore }}</span><span class="mob-stat-lbl">平均分</span></div>
        <div class="mob-stat-div"></div>
        <div class="mob-stat-big"><span class="mob-stat-num">{{ $.totalTime }}</span><span class="mob-stat-lbl">总耗时</span></div>
      </div>
      <div class="mob-mine-grid">
        <div class="mob-mine-item" @click="$.showVocabPoolDialog = true">
          <span style="font-size:calc(22px * var(--ett-fs, 1))">📖</span>
          <span style="font-size:calc(10px * var(--ett-fs, 1));color:#bbb">生词短语池</span>
        </div>
        <div class="mob-mine-item" @click="$.showPhrasePracticeDialog = true">
          <span style="font-size:calc(22px * var(--ett-fs, 1))">✍️</span>
          <span style="font-size:calc(10px * var(--ett-fs, 1));color:#bbb">短语默写</span>
        </div>
        <div class="mob-mine-item" @click="$.openHistoryPanel($.currentEssayId)">
          <span style="font-size:calc(22px * var(--ett-fs, 1))">🕐</span>
          <span style="font-size:calc(10px * var(--ett-fs, 1));color:#bbb">练习历史</span>
        </div>
        <div class="mob-mine-item" @click="$.showPromptConfig = true">
          <span style="font-size:calc(22px * var(--ett-fs, 1))">⚡</span>
          <span style="font-size:calc(10px * var(--ett-fs, 1));color:#bbb">提示词配置</span>
        </div>
        <div class="mob-mine-item" @click="$.openImageImport">
          <span style="font-size:calc(22px * var(--ett-fs, 1))">📷</span>
          <span style="font-size:calc(10px * var(--ett-fs, 1));color:#bbb">图片导入</span>
        </div>
        <div class="mob-mine-item" @click="$.showWordAnalysis = true">
          <span style="font-size:calc(22px * var(--ett-fs, 1))">🧩</span>
          <span style="font-size:calc(10px * var(--ett-fs, 1));color:#bbb">词根分析</span>
        </div>
      </div>
      <div class="mob-settings">
        <div class="mob-setting-row" @click="$.darkMode = !$.darkMode">
          <span>🌙 深色模式</span>
          <span class="mob-toggle" :class="{ on: $.darkMode }"></span>
        </div>
        <div class="mob-setting-row">
          <span>🔤 字体大小</span>
          <span class="mob-fontsize-ctl">
            <span style="font-size:calc(10px * var(--ett-fs, 1));color:#888;min-width:14px">A</span>
            <input type="range" min="0.8" max="2" step="0.05" v-model.number="fontScaleValue" class="mob-fs-slider" />
            <span style="font-size:calc(16px * var(--ett-fs, 1));color:#fff;min-width:18px;text-align:right">A</span>
            <span class="mob-fs-pct">{{ (fontScaleValue * 100).toFixed(0) }}%</span>
          </span>
        </div>
        <div class="mob-setting-row" @click="$.exportData">
          <span>📤 导出数据</span>
          <span style="font-size:calc(9px * var(--ett-fs, 1));color:#888">JSON 备份</span>
        </div>
        <div class="mob-setting-row" @click="$.shareBackup">
          <span>📲 分享备份</span>
          <span style="font-size:calc(9px * var(--ett-fs, 1));color:#888">发送到…</span>
        </div>
        <div class="mob-setting-row" @click="$.triggerImport">
          <span>📥 导入数据</span>
          <span style="font-size:calc(9px * var(--ett-fs, 1));color:#888">恢复备份</span>
        </div>
        <div class="mob-setting-row mob-apikey-row">
          <span style="flex-shrink:0">🔑 API Key</span>
          <el-input v-model="$.apiKey" type="password" placeholder="sk-..." size="small" show-password class="mob-apikey-input" />
        </div>
        <div class="mob-setting-row mob-apikey-row">
          <span style="flex-shrink:0">🌐 Qwen 地址</span>
          <el-input v-model="qwenUrl" placeholder="https://chat.qwen.ai" size="small" class="mob-apikey-input" @blur="saveQwenUrl" />
        </div>
        <div class="mob-setting-row mob-apikey-row">
          <span style="flex-shrink:0">📱 浏览器</span>
          <el-select v-model="qwenBrowser" size="small" class="mob-apikey-input" @change="saveQwenUrl" placeholder="系统默认">
            <el-option label="系统默认" value="" />
            <el-option label="Chrome" value="com.android.chrome" />
            <el-option label="Edge" value="com.microsoft.emmx" />
            <el-option label="Firefox" value="org.mozilla.firefox" />
            <el-option label="Opera" value="com.opera.browser" />
            <el-option label="Via" value="mark.via.gp" />
            <el-option label="Kiwi" value="com.kiwibrowser.browser" />
          </el-select>
        </div>
        <div class="mob-setting-row" style="font-size:calc(8px * var(--ett-fs, 1));color:#555;cursor:default;padding-top:0">
          <span>选"系统默认"时由系统弹窗选择</span>
        </div>
      </div>
    </div>
  </div>

  <!-- 底部导航栏 -->
  <div class="mob-bottom-nav">
    <div class="mob-nav-item" :class="{ active: activeTab === 'essays' }" @click="activeTab = 'essays'">
      <span style="font-size:calc(18px * var(--ett-fs, 1))">📚</span><span style="font-size:calc(10px * var(--ett-fs, 1))">范文库</span>
    </div>
    <div class="mob-nav-item" :class="{ active: activeTab === 'practice' }" @click="activeTab = 'practice'">
      <span style="font-size:calc(18px * var(--ett-fs, 1))">✏️</span><span style="font-size:calc(10px * var(--ett-fs, 1))">练习</span>
    </div>
    <div class="mob-nav-item" :class="{ active: activeTab === 'mine' }" @click="activeTab = 'mine'">
      <span style="font-size:calc(18px * var(--ett-fs, 1))">👤</span><span style="font-size:calc(10px * var(--ett-fs, 1))">我的</span>
    </div>
  </div>
</template>

<script setup>
import { ref, inject, computed, watch, onMounted, onUnmounted } from 'vue'

const $ = inject('ett')

const activeTab = ref('essays')
const mobSrcShow = ref(false)
const mobSeg = ref(0)
watch(() => $.currentEssayId, () => { mobSeg.value = 0; mobSrcShow.value = false })

const fontScaleValue = ref(parseFloat(localStorage.getItem('ett_mob_font_scale')) || 1)
const fontScale = computed(() => fontScaleValue.value)
watch(fontScaleValue, (v) => { localStorage.setItem('ett_mob_font_scale', v); document.documentElement.style.setProperty('--ett-fs', fontScale.value) })
watch(fontScale, (v) => document.documentElement.style.setProperty('--ett-fs', v), { immediate: true })

const srcScrollRef = ref(null)
watch(mobSeg, (idx) => {
  const el = srcScrollRef.value
  if (!el) return
  const segEl = el.children[idx]
  if (segEl) segEl.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'start' })
})

// Qwen 自定义
const qwenUrl = ref(localStorage.getItem('ett_qwen_url') || '')
const qwenBrowser = ref(localStorage.getItem('ett_qwen_browser') || '')
function saveQwenUrl() {
  localStorage.setItem('ett_qwen_url', qwenUrl.value)
  localStorage.setItem('ett_qwen_browser', qwenBrowser.value)
}
function openQwenMobile() {
  const url = qwenUrl.value.trim() || 'https://chat.qwen.ai'
  const pkg = qwenBrowser.value.trim()
  if (pkg) {
    // Android intent: try to open with specific browser
    const intentUrl = `intent://${url.replace(/^https?:\/\//, '')}#Intent;scheme=https;package=${pkg};S.browser_fallback_url=${encodeURIComponent(url)};end`
    const fallback = () => window.open(url, '_blank')
    try { window.location.href = intentUrl; setTimeout(fallback, 800) } catch { fallback() }
  } else {
    window.open(url, '_blank')
  }
}

function checkMobile() {
  if (window.innerWidth >= 768) { $.isMobile.value = false }
}
window.addEventListener('resize', checkMobile)
onUnmounted(() => window.removeEventListener('resize', checkMobile))
</script>

<style scoped>
* { box-sizing: border-box; }

.ett-mobile-body {
  flex: 1; display: flex; flex-direction: column; overflow: hidden;
  padding-bottom: 48px;
}

.mob-bottom-nav {
  position: fixed; bottom: 0; left: 0; right: 0; height: 48px;
  background: #1a1a2e; border-top: 1px solid #2d2d3f;
  display: flex; align-items: center; z-index: 100;
  padding-bottom: env(safe-area-inset-bottom, 0px);
}
.mob-nav-item {
  flex: 1; display: flex; flex-direction: column; align-items: center;
  gap: 2px; cursor: pointer; color: #666; padding: 4px 0;
}
.mob-nav-item.active { color: #409eff; }

.mobile-tab {
  flex: 1; display: flex; flex-direction: column; overflow-y: auto;
}

.mob-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 14px 8px; flex-shrink: 0;
}
.mob-title { font-size: calc(16px * var(--ett-fs, 1)); font-weight: 700; color: #fff; }
.mob-count { font-size: calc(10px * var(--ett-fs, 1)); color: #888; background: #374151; padding: 2px 8px; border-radius: 10px; }

.mob-stats-bar {
  display: flex; margin: 0 14px 8px; padding: 8px 6px;
  background: #2d2d3f; border-radius: 10px;
}
.mob-stat { flex: 1; text-align: center; font-size: calc(9px * var(--ett-fs, 1)); color: #888; }
.mob-stat b { display: block; font-size: calc(13px * var(--ett-fs, 1)); color: #e0e0e0; }

.mob-essay-list { flex: 1; overflow-y: auto; padding: 0 14px; }
.mob-essay-item {
  display: flex; align-items: center; padding: 10px 10px;
  border-bottom: 1px solid #2d2d3f; cursor: pointer;
}
.mob-essay-item.active { background: #1a2a3a; border-radius: 8px; border-bottom-color: transparent; }
.mob-essay-info { flex: 1; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.mob-essay-title { font-size: calc(12px * var(--ett-fs, 1)); color: #eee; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mob-essay-src { font-size: calc(9px * var(--ett-fs, 1)); color: #888; }
.mob-essay-score { font-size: calc(18px * var(--ett-fs, 1)); font-weight: 700; margin-left: 8px; }
.mob-essay-hist { font-size: calc(9px * var(--ett-fs, 1)); color: #888; margin-left: 6px; cursor: pointer; padding: 2px 5px; border-radius: 4px; background: #2d2d3f; }
.mob-essay-new { font-size: calc(10px * var(--ett-fs, 1)); color: #888; background: #374151; padding: 2px 6px; border-radius: 4px; margin-left: 8px; }

.mob-cal-strip { display: flex; align-items: center; padding: 8px 14px; gap: 3px; flex-wrap: wrap; }
.cal-dot-sm { width: 10px; height: 10px; border-radius: 2px; flex-shrink: 0; }

.mob-bottom-actions { display: flex; gap: 8px; padding: 8px 14px; flex-shrink: 0; }
.mob-action-btn { flex: 1; text-align: center; padding: 10px; border-radius: 10px; font-size: calc(11px * var(--ett-fs, 1)); color: #fff; cursor: pointer; }
.mob-action-btn.primary { background: #409eff; }
.mob-action-btn.success { background: #22C55E; }

/* 练习页 */
.mob-mode-pills { display: flex; gap: 4px; padding: 2px 14px 8px; flex-shrink: 0; }
.mob-pill { padding: 4px 10px; border-radius: 12px; font-size: calc(9px * var(--ett-fs, 1)); background: #2d2d3f; color: #888; cursor: pointer; }
.mob-pill.active { background: #e6a23c; color: #fff; }

.mob-practice-scroll { flex: 1; overflow-y: auto; padding: 0 14px; }
/* 原文区（对齐练习页方案A设计） */
.mob-src-bar { display: flex; align-items: center; justify-content: space-between; padding: 7px 10px; background: #2d2d3f; border-radius: 8px; cursor: pointer; margin-bottom: 4px; }
.mob-src-count { font-size: calc(9px * var(--ett-fs, 1)); color: #666; background: #374151; padding: 2px 6px; border-radius: 8px; }
.mob-src-body { background: #1e1e30; border-radius: 8px; padding: 0; margin-bottom: 4px; overflow: hidden; }
.mob-src-scroll { display: flex; overflow-x: auto; scroll-snap-type: x mandatory; gap: 0; padding: 6px 0; }
.mob-src-scroll::-webkit-scrollbar { height: 0; }
.mob-seg { min-width: 100%; max-width: 100%; scroll-snap-align: start; padding: 8px 10px; cursor: pointer; overflow: hidden; display: flex; flex-direction: column; gap: 6px; flex-shrink: 0; }
.mob-seg-num { display: inline-block; min-width: 16px; height: 16px; border-radius: 50%; background: #409eff; color: #fff; font-size: calc(9px * var(--ett-fs, 1)); font-weight: 600; text-align: center; line-height: 16px; align-self: flex-start; }
.mob-seg-en { font-size: calc(10px * var(--ett-fs, 1)); color: #bbb; line-height: 1.5; word-break: break-word; overflow-wrap: break-word; }
.mob-seg-kp { font-size: calc(8px * var(--ett-fs, 1)); color: #e6a23c; background: #2a2a1a; padding: 2px 6px; border-radius: 4px; align-self: flex-start; }
.mob-src-nav { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 4px 0 6px; }
.mob-src-arrow { color: #888; font-size: calc(10px * var(--ett-fs, 1)); cursor: pointer; user-select: none; padding: 2px 4px; }
.mob-src-dots { display: flex; gap: 4px; align-items: center; }
.mob-src-dot { width: 5px; height: 5px; border-radius: 3px; background: #374151; transition: all 0.2s; cursor: pointer; }
.mob-src-dot.on { width: 14px; background: #409eff; }

.mob-section-label { font-size: calc(10px * var(--ett-fs, 1)); color: #999; font-weight: 700; padding: 8px 0 4px; }
.mob-textarea {
  width: 100%; height: 80px; background: #2d2d3f; border: none; border-radius: 8px;
  padding: 10px; color: #ddd; font-size: calc(12px * var(--ett-fs, 1)); resize: none; outline: none; font-family: inherit;
}
.mob-textarea::placeholder { color: #555; }

.mob-action-row { padding: 4px 0; }
.mob-submit-btn {
  text-align: center; padding: 10px; border-radius: 10px;
  background: #409eff; color: #fff; font-size: calc(12px * var(--ett-fs, 1)); font-weight: 600; cursor: pointer;
}

.mob-paste-area { margin-top: 6px; }
.mob-score-row { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; flex-wrap: wrap; }
.mob-dims { display: flex; flex-direction: column; gap: 3px; flex: 1; min-width: 120px; }
.mob-dim { display: flex; align-items: center; gap: 4px; font-size: calc(8px * var(--ett-fs, 1)); color: #888; }
.mob-dim-bar { flex: 1; height: 6px; background: #374151; border-radius: 3px; overflow: hidden; }
.mob-dim-bar div { height: 100%; border-radius: 3px; }
.mob-fb-card { background: #2d2d3f; border-radius: 10px; padding: 10px; margin-bottom: 8px; }
.mob-fb-title { font-size: calc(10px * var(--ett-fs, 1)); color: #e6a23c; font-weight: 600; margin-bottom: 4px; }
.mob-fb-text { font-size: calc(9px * var(--ett-fs, 1)); color: #bbb; line-height: 1.6; }

.mob-wave-item {
  display: flex; align-items: center; gap: 6px; padding: 8px 10px;
  background: #2d2d3f; border-radius: 8px; margin-bottom: 4px; cursor: pointer;
}
.mob-wave-item.sel { border: 1px solid #409eff; }
.mob-wave-num { font-size: calc(10px * var(--ett-fs, 1)); color: #409eff; font-weight: 600; }
.mob-wave-txt { font-size: calc(9px * var(--ett-fs, 1)); color: #bbb; }

.mob-rev-ref { background: #2d2d3f; border-radius: 8px; padding: 10px; margin-bottom: 8px; }

/* 我的 */
.mob-stats-hero {
  display: flex; align-items: center; margin: 0 14px 10px; padding: 12px 8px;
  background: #2d2d3f; border-radius: 14px;
}
.mob-stat-big { flex: 1; text-align: center; }
.mob-stat-num { display: block; font-size: calc(18px * var(--ett-fs, 1)); font-weight: 700; color: #22C55E; }
.mob-stat-lbl { font-size: calc(9px * var(--ett-fs, 1)); color: #888; }
.mob-stat-div { width: 1px; height: 28px; background: #374151; }

.mob-mine-grid {
  display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px;
  padding: 0 14px;
}
.mob-mine-item {
  background: #2d2d3f; border-radius: 16px; aspect-ratio: 1;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 6px; cursor: pointer;
}

.mob-settings {
  padding: 12px 14px; display: flex; flex-direction: column; gap: 8px;
  border-top: 1px solid #2d2d3f; margin-top: 12px;
}
.mob-setting-row {
  display: flex; align-items: center; justify-content: space-between;
  font-size: calc(11px * var(--ett-fs, 1)); color: #aaa; padding: 4px 0; cursor: pointer;
}
.mob-toggle {
  width: 36px; height: 20px; border-radius: 10px; background: #374151; position: relative; transition: background 0.2s;
}
.mob-toggle::after {
  content: ''; position: absolute; top: 2px; left: 2px;
  width: 16px; height: 16px; border-radius: 8px; background: #fff; transition: transform 0.2s;
}
.mob-toggle.on { background: #22C55E; }
.mob-toggle.on::after { transform: translateX(16px); }
.mob-apikey-row { cursor: default !important; gap: 8px; }
.mob-apikey-input { flex: 1; max-width: 180px; }
.mob-apikey-input :deep(.el-input__wrapper) { background: #2d2d3f; box-shadow: none; padding: 2px 8px; }
.mob-apikey-input :deep(.el-input__inner) { color: #e0e0e0; font-size: calc(11px * var(--ett-fs, 1)); }

.mob-fontsize-ctl { display: flex; gap: 2px; background: #2d2d3f; border-radius: 8px; padding: 2px; }
.mob-fs-btn { width: 28px; height: 22px; display: flex; align-items: center; justify-content: center; border-radius: 6px; font-size: calc(10px * var(--ett-fs, 1)); color: #888; cursor: pointer; }
.mob-fs-btn.on { background: #409eff; color: #fff; }

/* 译文对照 */
.mob-cmp { margin-bottom: 8px; }
.mob-cmp div { word-break: break-word; overflow-wrap: break-word; min-width: 0; }
.mob-cmp b { font-weight: 600; margin-right: 4px; flex-shrink: 0; }

/* 字体大小滑块 */
.mob-fs-slider {
  -webkit-appearance: none; appearance: none;
  flex: 1; height: 4px; background: #374151; border-radius: 2px; outline: none;
}
.mob-fs-slider::-webkit-slider-thumb {
  -webkit-appearance: none; appearance: none;
  width: 18px; height: 18px; border-radius: 9px; background: #409eff; cursor: pointer;
}
.mob-fs-pct { font-size: calc(9px * var(--ett-fs, 1)); color: #888; min-width: 30px; text-align: center; }

/* 错误结构分析（水波纠错） */
.mob-wave-box { background: #2d2d3f; border-radius: 10px; padding: 10px; margin-bottom: 8px; }
.mob-wave-row { display: flex; flex-direction: column; gap: 2px; padding: 4px 0; }
.mob-wave-lbl { font-size: calc(8px * var(--ett-fs, 1)); color: #e6a23c; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
.mob-wave-en { font-size: calc(10px * var(--ett-fs, 1)); color: #ff5f00; font-family: monospace; line-height: 1.4; word-break: break-word; overflow-wrap: break-word; min-width: 0; }
.mob-wave-zh { font-size: calc(9px * var(--ett-fs, 1)); color: #bbb; line-height: 1.5; }
.mob-wave-tip { font-size: calc(9px * var(--ett-fs, 1)); color: #22C55E; line-height: 1.5; }
.mob-wave-ex { display: flex; align-items: baseline; gap: 4px; padding: 3px 0; font-size: calc(9px * var(--ett-fs, 1)); flex-wrap: wrap; }
.mob-wave-ex-en { color: #ff5f00; font-family: monospace; word-break: break-word; min-width: 0; }
.mob-wave-ex-arrow { color: #666; flex-shrink: 0; }
.mob-wave-ex-zh { color: #bbb; word-break: break-word; min-width: 0; }
</style>
