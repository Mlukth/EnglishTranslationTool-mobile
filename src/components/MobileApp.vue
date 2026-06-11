<template>
  <div class="ett-mobile-body">
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
          <span class="mob-essay-new" v-else>新</span>
        </div>
      </div>
      <div class="mob-cal-strip">
        <span style="font-size:10px;color:#888;margin-right:4px">📅</span>
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
        <span style="font-size:11px;color:#888" @click="activeTab='essays'">← 范文库</span>
        <span class="mob-title">{{ $.currentEssay ? $.currentEssay.title : '翻译练习' }}</span>
        <span style="font-size:10px;color:#409eff" @click="$.showPromptConfig=true">⚙ 提示词</span>
      </div>
      <div class="mob-mode-pills">
        <span v-for="m in [{k:'api',l:'API评分'},{k:'window',l:'窗口AI'},{k:'wave',l:'水波'},{k:'reverse',l:'反转'}]" :key="m.k"
          class="mob-pill" :class="{ active: $.scoringMode === m.k }" @click="$.scoringMode = m.k">{{ m.l }}</span>
      </div>
      <div class="mob-practice-scroll" v-if="$.currentEssay">
        <!-- 原文 -->
        <div class="mob-src-bar" @click="mobSrcShow = !mobSrcShow">
          <span style="font-size:10px;color:#aaa">📄 原文 · {{ $.currentEssay.source }} {{ mobSrcShow ? '▾' : '▸' }}</span>
        </div>
        <div v-if="mobSrcShow" class="mob-src-cards">
          <div v-for="(seg, i) in $.currentEssay.segments" :key="i" class="mob-src-card" @click="$.selectedSeg = i">
            <span class="mob-src-num">{{ i+1 }}</span>
            <span class="mob-src-en" @click.stop="$.onWordClick($event)">{{ seg.en }}</span>
            <span class="mob-src-kp" v-if="seg.keyPoints.length">{{ seg.keyPoints.join(' · ') }}</span>
          </div>
        </div>
        <!-- 翻译输入 -->
        <div class="mob-section-label">✏️ 你的译文 <span style="color:#888;font-weight:400">⏱ {{ $.formatTime($.elapsed) }}</span></div>
        <textarea v-if="$.scoringMode !== 'wave'" class="mob-textarea" v-model="$.userTranslation" placeholder="在此输入你的中文翻译..."></textarea>
        <div class="mob-action-row" v-if="$.scoringMode === 'api'">
          <div class="mob-submit-btn" @click="$.submitTranslation" :style="{opacity: $.userTranslation.trim()?1:0.5}">提交 AI 评分</div>
        </div>
        <div class="mob-action-row" v-if="$.scoringMode === 'window'" style="display:flex;gap:6px">
          <div class="mob-submit-btn" style="background:#e6a23c;flex:1" @click="$.submitTranslation">复制拼接 prompt</div>
          <div class="mob-submit-btn" style="background:#409eff;flex:1" @click="$.openQwen">🌐 打开 Qwen</div>
        </div>
        <div v-if="$.scoringMode === 'window'" class="mob-paste-area">
          <div class="mob-section-label" style="color:#e6a23c">📋 粘贴 AI 返回的 JSON</div>
          <textarea class="mob-textarea" v-model="$.windowAIInput" placeholder='{"accuracy":20,...}' style="height:60px;font-size:10px"></textarea>
          <div class="mob-submit-btn" style="background:#22C55E;margin-top:4px" @click="$.submitWindowAI">解析并录入评分</div>
        </div>
        <!-- 评分结果 -->
        <template v-if="$.rightPanelRecord">
          <div class="mob-section-label">📊 评分结果</div>
          <div class="mob-score-row">
            <div style="font-size:36px;font-weight:700" :style="{color: $.scoreColor($.rightPanelRecord.totalScore)}">{{ $.rightPanelRecord.totalScore }}</div>
            <div style="font-size:10px;color:#888;margin-left:4px">/100</div>
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
            <div v-if="$.waveAnswer.grammarTree" style="font-size:9px;color:#aaa;line-height:1.5">{{ $.waveAnswer.grammarTree }}</div>
          </div>
        </template>
        <!-- 反转训练 -->
        <template v-if="$.scoringMode === 'reverse'">
          <div class="mob-section-label">📖 中文参考译文</div>
          <div class="mob-rev-ref" v-if="$.reverseDisplayRef">
            <p v-for="(l,i) in $.reverseDisplayRef.split('\n').filter(Boolean)" :key="i" style="font-size:10px;color:#bbb;line-height:1.6">{{ l }}</p>
          </div>
          <div class="mob-section-label">✏️ 你的英译</div>
          <textarea class="mob-textarea" v-model="$.reverseUserTranslation" placeholder="根据中文参考，输入英文翻译..."></textarea>
          <div class="mob-action-row" style="display:flex;gap:6px">
            <div class="mob-submit-btn" style="flex:1" @click="$.submitReverseTranslation">API评分</div>
            <div class="mob-submit-btn" style="background:#e6a23c;flex:1" @click="$.copyReversePrompt">复制prompt</div>
          </div>
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
        <span style="font-size:10px;color:#888">考研英语一</span>
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
          <span style="font-size:22px">📖</span>
          <span style="font-size:10px;color:#bbb">生词短语池</span>
        </div>
        <div class="mob-mine-item" @click="$.showPhrasePracticeDialog = true">
          <span style="font-size:22px">✍️</span>
          <span style="font-size:10px;color:#bbb">短语默写</span>
        </div>
        <div class="mob-mine-item" @click="$.openHistoryPanel($.currentEssayId)">
          <span style="font-size:22px">🕐</span>
          <span style="font-size:10px;color:#bbb">练习历史</span>
        </div>
        <div class="mob-mine-item" @click="$.showPromptConfig = true">
          <span style="font-size:22px">⚡</span>
          <span style="font-size:10px;color:#bbb">提示词配置</span>
        </div>
        <div class="mob-mine-item" @click="$.openImageImport">
          <span style="font-size:22px">📷</span>
          <span style="font-size:10px;color:#bbb">图片导入</span>
        </div>
        <div class="mob-mine-item" @click="$.showWordAnalysis = true">
          <span style="font-size:22px">🧩</span>
          <span style="font-size:10px;color:#bbb">词根分析</span>
        </div>
      </div>
      <div class="mob-settings">
        <div class="mob-setting-row" @click="$.darkMode = !$.darkMode">
          <span>🌙 深色模式</span>
          <span class="mob-toggle" :class="{ on: $.darkMode }"></span>
        </div>
        <div class="mob-setting-row" @click="$.exportData">
          <span>📤 导出数据</span>
          <span style="font-size:9px;color:#888">JSON 备份</span>
        </div>
        <div class="mob-setting-row" @click="$.triggerImport">
          <span>📥 导入数据</span>
          <span style="font-size:9px;color:#888">恢复备份</span>
        </div>
        <div class="mob-setting-row mob-apikey-row">
          <span style="flex-shrink:0">🔑 API Key</span>
          <el-input v-model="$.apiKey" type="password" placeholder="sk-..." size="small" show-password class="mob-apikey-input" />
        </div>
      </div>
    </div>
  </div>

  <!-- 底部导航栏 -->
  <div class="mob-bottom-nav">
    <div class="mob-nav-item" :class="{ active: activeTab === 'essays' }" @click="activeTab = 'essays'">
      <span style="font-size:18px">📚</span><span style="font-size:10px">范文库</span>
    </div>
    <div class="mob-nav-item" :class="{ active: activeTab === 'practice' }" @click="activeTab = 'practice'">
      <span style="font-size:18px">✏️</span><span style="font-size:10px">练习</span>
    </div>
    <div class="mob-nav-item" :class="{ active: activeTab === 'mine' }" @click="activeTab = 'mine'">
      <span style="font-size:18px">👤</span><span style="font-size:10px">我的</span>
    </div>
  </div>
</template>

<script setup>
import { ref, inject, onMounted, onUnmounted } from 'vue'

const $ = inject('ett')

const activeTab = ref('essays')
const mobSrcShow = ref(false)

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
.mob-title { font-size: 16px; font-weight: 700; color: #fff; }
.mob-count { font-size: 10px; color: #888; background: #374151; padding: 2px 8px; border-radius: 10px; }

.mob-stats-bar {
  display: flex; margin: 0 14px 8px; padding: 8px 6px;
  background: #2d2d3f; border-radius: 10px;
}
.mob-stat { flex: 1; text-align: center; font-size: 9px; color: #888; }
.mob-stat b { display: block; font-size: 13px; color: #e0e0e0; }

.mob-essay-list { flex: 1; overflow-y: auto; padding: 0 14px; }
.mob-essay-item {
  display: flex; align-items: center; padding: 10px 10px;
  border-bottom: 1px solid #2d2d3f; cursor: pointer;
}
.mob-essay-item.active { background: #1a2a3a; border-radius: 8px; border-bottom-color: transparent; }
.mob-essay-info { flex: 1; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.mob-essay-title { font-size: 12px; color: #eee; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mob-essay-src { font-size: 9px; color: #888; }
.mob-essay-score { font-size: 18px; font-weight: 700; margin-left: 8px; }
.mob-essay-new { font-size: 10px; color: #888; background: #374151; padding: 2px 6px; border-radius: 4px; margin-left: 8px; }

.mob-cal-strip { display: flex; align-items: center; padding: 8px 14px; gap: 3px; flex-wrap: wrap; }
.cal-dot-sm { width: 10px; height: 10px; border-radius: 2px; flex-shrink: 0; }

.mob-bottom-actions { display: flex; gap: 8px; padding: 8px 14px; flex-shrink: 0; }
.mob-action-btn { flex: 1; text-align: center; padding: 10px; border-radius: 10px; font-size: 11px; color: #fff; cursor: pointer; }
.mob-action-btn.primary { background: #409eff; }
.mob-action-btn.success { background: #22C55E; }

/* 练习页 */
.mob-mode-pills { display: flex; gap: 4px; padding: 2px 14px 8px; flex-shrink: 0; }
.mob-pill { padding: 4px 10px; border-radius: 12px; font-size: 9px; background: #2d2d3f; color: #888; cursor: pointer; }
.mob-pill.active { background: #e6a23c; color: #fff; }

.mob-practice-scroll { flex: 1; overflow-y: auto; padding: 0 14px; }
.mob-src-bar { padding: 6px 10px; background: #2d2d3f; border-radius: 6px; cursor: pointer; margin-bottom: 6px; }
.mob-src-cards { display: flex; gap: 8px; overflow-x: auto; scroll-snap-type: x mandatory; padding-bottom: 6px; margin-bottom: 8px; }
.mob-src-card {
  flex: 0 0 85%; scroll-snap-align: start;
  background: #2d2d3f; border-radius: 10px; padding: 10px;
  position: relative;
}
.mob-src-num { font-size: 10px; color: #409eff; font-weight: 700; }
.mob-src-en { font-size: 10px; color: #bbb; line-height: 1.5; word-break: break-word; cursor: pointer; }
.mob-src-kp { font-size: 8px; color: #e6a23c; margin-top: 4px; display: block; }

.mob-section-label { font-size: 10px; color: #999; font-weight: 700; padding: 8px 0 4px; }
.mob-textarea {
  width: 100%; height: 80px; background: #2d2d3f; border: none; border-radius: 8px;
  padding: 10px; color: #ddd; font-size: 12px; resize: none; outline: none; font-family: inherit;
}
.mob-textarea::placeholder { color: #555; }

.mob-action-row { padding: 4px 0; }
.mob-submit-btn {
  text-align: center; padding: 10px; border-radius: 10px;
  background: #409eff; color: #fff; font-size: 12px; font-weight: 600; cursor: pointer;
}

.mob-paste-area { margin-top: 6px; }
.mob-score-row { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; flex-wrap: wrap; }
.mob-dims { display: flex; flex-direction: column; gap: 3px; flex: 1; min-width: 120px; }
.mob-dim { display: flex; align-items: center; gap: 4px; font-size: 8px; color: #888; }
.mob-dim-bar { flex: 1; height: 6px; background: #374151; border-radius: 3px; overflow: hidden; }
.mob-dim-bar div { height: 100%; border-radius: 3px; }
.mob-fb-card { background: #2d2d3f; border-radius: 10px; padding: 10px; margin-bottom: 8px; }
.mob-fb-title { font-size: 10px; color: #e6a23c; font-weight: 600; margin-bottom: 4px; }
.mob-fb-text { font-size: 9px; color: #bbb; line-height: 1.6; }

.mob-wave-item {
  display: flex; align-items: center; gap: 6px; padding: 8px 10px;
  background: #2d2d3f; border-radius: 8px; margin-bottom: 4px; cursor: pointer;
}
.mob-wave-item.sel { border: 1px solid #409eff; }
.mob-wave-num { font-size: 10px; color: #409eff; font-weight: 600; }
.mob-wave-txt { font-size: 9px; color: #bbb; }

.mob-rev-ref { background: #2d2d3f; border-radius: 8px; padding: 10px; margin-bottom: 8px; }

/* 我的 */
.mob-stats-hero {
  display: flex; align-items: center; margin: 0 14px 10px; padding: 12px 8px;
  background: #2d2d3f; border-radius: 14px;
}
.mob-stat-big { flex: 1; text-align: center; }
.mob-stat-num { display: block; font-size: 18px; font-weight: 700; color: #22C55E; }
.mob-stat-lbl { font-size: 9px; color: #888; }
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
  font-size: 11px; color: #aaa; padding: 4px 0; cursor: pointer;
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
.mob-apikey-input :deep(.el-input__inner) { color: #e0e0e0; font-size: 11px; }
</style>
