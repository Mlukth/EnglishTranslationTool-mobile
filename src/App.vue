<template>
  <div class="ett-container" :class="{ dark: darkMode }">
    <!-- 桌面端布局 -->
    <template v-if="!isMobile">
    <!-- 顶部工具栏 -->
    <div class="ett-header">
      <h2 class="ett-title">英语翻译练习 — 考研英语一</h2>
      <div class="ett-header-actions">
        <el-input v-model="apiKey" type="password" placeholder="API Key" size="small" :style="{width: isMobile ? '110px' : '240px'}" show-password />
        <el-radio-group v-model="scoringMode" size="small">
          <el-radio-button value="api">API评分</el-radio-button>
          <el-radio-button value="window">窗口AI</el-radio-button>
          <el-radio-button value="wave">水波训练</el-radio-button>
          <el-radio-button value="reverse">反转训练</el-radio-button>
        </el-radio-group>
        <el-button size="small" @click="showPromptConfig = true" :icon="Setting">提示词</el-button>
        <el-button type="primary" size="small" @click="startPractice" :disabled="!currentEssay">开始练习</el-button>
        <el-button size="small" @click="showAddDialog = true">+ 添加范文</el-button>
        <el-button size="small" @click="exportData">导出</el-button>
        <el-upload :show-file-list="false" :before-upload="importData" accept=".json" style="display:inline-block;margin-left:4px">
          <el-button size="small">导入</el-button>
        </el-upload>
        <el-button size="small" type="info" @click="showVocabPoolDialog = true" :disabled="!vocabPool.length" style="margin-left:4px">生词池 ({{ vocabPool.length }})</el-button>
        <el-button size="small" type="success" @click="openImageImport" style="margin-left:4px">图片导入</el-button>
        <el-button size="small" type="warning" @click="showPhrasePracticeDialog = true" :disabled="!phraseCards.length" style="margin-left:4px">短语默写 ({{ phraseCards.length }})</el-button>
        <el-switch v-model="darkMode" size="small" active-text="🌙" inactive-text="☀️" style="margin-left:12px" />
        <el-button size="small" @click="syncFromServer" title="从服务器同步数据">同步</el-button>

            <el-divider v-if="!isMobile" direction="vertical" />
            <span v-if="!isMobile" class="token-usage" title="Token用量（本次会话）">
              <span class="token-label">Tokens:</span>
              <span class="token-val">{{ (tokenUsage.total / 1000).toFixed(1) }}k</span>
              <span class="token-detail">({{ tokenUsage.calls }}次)</span>
            </span></div>
    </div>

    <!-- 主体三栏 -->
    <div class="ett-body" :class="{ dark: darkMode }">
      <!-- 左侧栏：范文列表 + 日历 -->
      <aside class="ett-left">
        <el-tabs model-value="list" type="border-card" class="ett-left-tabs">
          <el-tab-pane label="范文库" name="list">
            <div class="essay-list">
              <div v-for="(e, idx) in essays" :key="e.id"
                class="essay-item"
                :class="{ active: e.id === currentEssayId, done: getRecord(e.id)?.completed, 'drag-over': dragOverIdx === idx }"
                @click="currentEssayId = e.id"
                draggable="true"
                @dragstart="onEssayDragStart($event, e.id)"
                @dragend="onEssayDragEnd"
                @dragover="onEssayDragOver($event, idx)"
                @dragleave="onEssayDragLeave"
                @drop="onEssayDrop($event, idx)">
                <el-button class="essay-delete-btn" size="small" text type="danger" @click.stop="deleteEssay(e.id)" title="删除范文">×</el-button>
                <div class="essay-item-title">{{ e.title }}</div>
                <div class="essay-item-meta">{{ e.source }} · {{ e.date }}</div>
                <div class="essay-item-score" v-if="getRecord(e.id)?.completed">
                  <el-tag :type="scoreTag(getRecord(e.id).totalScore)">{{ getRecord(e.id).totalScore }}分</el-tag>
                  <el-button size="small" text type="info" @click.stop="openHistoryPanel(e.id)" class="history-btn">
                    历史
                  </el-button>
                </div>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="打卡日历" name="calendar">
            <el-calendar v-model="calendarDate">
              <template #default="{ data }">
                <div class="cal-cell" @click="calendarDate = data.date"
                  :class="{ checked: hasRecord(data.date), 'cal-today': isToday(data.date) }">
                  {{ data.date.getDate() }}
                  <span v-if="hasRecord(data.date)" class="cal-dot" :style="{ background: scoreDotColor(data.date) }"></span>
                </div>
              </template>
            </el-calendar>
          </el-tab-pane>
        </el-tabs>

        <!-- 统计面板 -->
        <div class="stats-panel" v-if="records.length > 0">
          <div class="stat-row"><span>连续打卡</span><strong>{{ streakDays }}天</strong></div>
          <div class="stat-row"><span>累计练习</span><strong>{{ records.filter(r=>r.completed).length }}篇</strong></div>
          <div class="stat-row"><span>平均分</span><strong>{{ avgScore }}分</strong></div>
          <div class="stat-row"><span>总耗时</span><strong>{{ totalTime }}</strong></div>
        </div>
      </aside>

      <!-- 中间栏：翻译练习区 -->
      <main class="ett-main" :class="{ 'anno-main': annoMode }" ref="annoMainRef">
        <!-- 批注浮动工具栏 -->
        <div v-if="annoMode" class="anno-float-toolbar">
          <span v-for="c in drawColors" :key="c.color"
            class="color-dot" :class="{ active: drawColor === c.color && !isErasing }"
            :style="{ background: c.css }"
            @click="setDrawColor(c.color)" :title="c.name"></span>
          <el-divider direction="vertical" />
          <span class="toolbar-label">粗细</span>
          <el-slider v-model="drawWidth" :min="1" :max="12" size="small" style="width:70px" />
          <el-divider direction="vertical" />
          <el-button size="small" :type="isErasing ? 'warning' : 'default'" @click="toggleErase">
            {{ isErasing ? '橡皮擦中' : '橡皮擦' }}
          </el-button>
          <el-button size="small" @click="clearAnnoDrawings" :disabled="currentAnnoCount === 0">清除</el-button>
          <span class="anno-count" v-if="currentAnnoCount > 0">{{ currentAnnoCount }}笔</span>
          <el-button size="small" type="info" @click="toggleAnnoMode" style="margin-left:auto">退出批注</el-button>
        </div>
        <canvas v-if="annoMode" ref="annoCanvasRef"
          class="anno-canvas"
          @mousedown="onAnnoMouseDown"
          @mousemove="onAnnoMouseMove"
          @mouseup="onAnnoMouseUp"
          @mouseleave="onAnnoMouseUp"
        ></canvas>
        <template v-if="currentEssay">
          <!-- 原文区 -->
          <div class="section" v-if="!(scoringMode === 'reverse' && practiceStarted)">
            <div class="section-header">
              <span class="section-label">原文</span>
              <span class="section-source">{{ currentEssay.source }}</span>
              <el-button size="small" text @click="toggleHighlight" v-if="practiceStarted && !annoMode">划词模式</el-button>
              <el-button size="small" :type="annoMode ? 'warning' : 'default'" @click="toggleAnnoMode" style="margin-left:auto">
                {{ annoMode ? '退出批注' : '批注模式' }}
              </el-button>
            </div>
            <div class="original-text" ref="originalRef">
              <p v-for="(seg, i) in currentEssay.segments" :key="i" class="orig-seg"
                :class="{ selected: selectedSeg === i }" @click="selectedSeg = i">
                <span class="seg-num">{{ i + 1 }}</span>
                <span class="seg-en" @click.stop="onWordClick">{{ seg.en }}</span>
                <span class="seg-hint" v-if="seg.keyPoints.length">考点：{{ seg.keyPoints.join('、') }}</span>
              </p>
            </div>
          </div>

          
          <!-- 水波训练区（独立模式） -->
          <div class="section wave-section" v-if="scoringMode === 'wave' && currentEssay">
            <div class="section-header">
              <span class="section-label">🌊 水波训练</span>
              <span class="hint-text">点击句子进行语法结构分析</span>
              <el-button size="small" text @click="resetWave" v-if="waveSelectedIdx >= 0">重置</el-button>
            </div>
            <div class="wave-seg-list">
              <div v-for="(seg, i) in currentEssay.segments" :key="i"
                class="wave-seg-item"
                :class="{ active: waveSelectedIdx === i, analyzing: waveAnalyzingIdx === i, cached: hasWaveCache(currentEssay.id, i) }"
                @click="selectWaveSegment(i)">
                <span class="wave-seg-num">{{ i + 1 }}</span>
                <span class="wave-seg-text" @click.stop="onWordClick">{{ seg.en }}</span>
                <span class="wave-seg-badge" v-if="hasWaveCache(currentEssay.id, i) && waveSelectedIdx !== i">✓</span>
                <el-icon v-if="waveAnalyzingIdx === i" class="is-loading"><Loading /></el-icon>
              </div>
            </div>
            <!-- 水波答案卡片 -->
            <div class="wave-answer-card" v-if="waveAnswer">
              <div class="wave-answer-header">
                <span class="wave-answer-title">📝 第{{ waveSelectedIdx + 1 }}句分析</span>
              </div>
              <div class="wave-answer-content" v-if="waveAnswer.grammarTree">
                <div class="wave-tree-section">
                  <div class="wave-tree-label">语法结构</div>
                  <div class="wave-tree-text">{{ waveAnswer.grammarTree }}</div>
                </div>
                <div class="wave-tree-section" v-if="waveAnswer.logicSplit">
                  <div class="wave-tree-label">逻辑切分</div>
                  <div class="wave-tree-text">{{ waveAnswer.logicSplit }}</div>
                </div>
                <div class="wave-tree-section" v-if="waveAnswer.stuckPoint">
                  <div class="wave-tree-label">🚧 卡点</div>
                  <div class="wave-tree-text stuck">{{ waveAnswer.stuckPoint }}</div>
                </div>
                <div class="wave-tree-section" v-if="waveAnswer.resolveTip">
                  <div class="wave-tree-label">💡 突破</div>
                  <div class="wave-tree-text resolve">{{ waveAnswer.resolveTip }}</div>
                </div>
                <div class="wave-tree-section" v-if="waveAnswer.analogy">
                  <div class="wave-tree-label">🌊 类比</div>
                  <div class="wave-tree-text analogy">{{ waveAnswer.analogy }}</div>
                </div>
              </div>
              <div class="wave-answer-content" v-else-if="waveAnswer.raw">
                <pre class="wave-raw">{{ waveAnswer.raw }}</pre>
              </div>
            </div>
            <!-- 水波总结 -->
            <div class="wave-summary-section" v-if="waveSummaryData">
              <el-divider />
              <div class="wave-summary-header">📊 今日水波总结</div>
              <div class="wave-summary-overview">{{ waveSummaryData.overview }}</div>
              <div class="wave-summary-grid" v-if="waveSummaryData.patterns?.length || waveSummaryData.strengths?.length">
                <div class="wave-summary-col" v-if="waveSummaryData.patterns?.length">
                  <div class="wave-summary-label">⚠️ 共性问题</div>
                  <ul><li v-for="p in waveSummaryData.patterns" :key="p">{{ p }}</li></ul>
                </div>
                <div class="wave-summary-col" v-if="waveSummaryData.strengths?.length">
                  <div class="wave-summary-label">✅ 做得好的</div>
                  <ul><li v-for="s in waveSummaryData.strengths" :key="s">{{ s }}</li></ul>
                </div>
              </div>
              <div class="wave-summary-focus" v-if="waveSummaryData.focusAreas?.length">
                <div class="wave-summary-label">🎯 后续重点</div>
                <div class="focus-tags">
                  <el-tag v-for="(f, i) in waveSummaryData.focusAreas" :key="i" size="small" type="warning">{{ f }}</el-tag>
                </div>
              </div>
              <div class="wave-summary-cheer" v-if="waveSummaryData.encouragement">{{ waveSummaryData.encouragement }}</div>
              <el-button size="small" type="primary" @click="requestWaveSummary" :loading="waveLoading" style="margin-top:8px">
                {{ waveSummaryData ? '重新生成总结' : '生成今日总结' }}
              </el-button>
            </div>
            <div style="text-align:center;margin-top:12px" v-if="currentEssay.segments.some((_,i) => hasWaveCache(currentEssay.id, i)) && !waveSummaryData">
              <el-button size="small" type="primary" @click="requestWaveSummary" :loading="waveLoading">生成今日总结</el-button>
            </div>
          </div>
<!-- 反转训练区 -->
          <div class="section" v-if="scoringMode === 'reverse' && practiceStarted">
            <div class="section-header">
              <span class="section-label">中文参考译文</span>
              <span class="section-source" v-if="currentEssay.referenceTranslation">（来自范文）</span>
              <el-button v-if="!currentEssay.referenceTranslation && !reverseChineseRef" size="small" type="primary" @click="generateReverseChineseRef" :loading="reverseGenerating" :disabled="!apiKey">
                AI生成参考译文
              </el-button>
              <el-button v-if="!currentEssay.referenceTranslation && reverseChineseRef" size="small" text type="info" @click="generateReverseChineseRef" :loading="reverseGenerating">
                重新生成
              </el-button>
            </div>
            <div class="reverse-ref-text" v-if="reverseDisplayRef">
              <p v-for="(line, i) in reverseDisplayRef.split('\n').filter(Boolean)" :key="i">{{ line }}</p>
            </div>
            <el-empty v-else-if="!reverseGenerating" description="请先生成中文参考译文" :image-size="60" />
            <div v-if="reverseGenerating" style="text-align:center;padding:20px;color:#777">AI正在翻译中文参考...</div>
          </div>

          <!-- 反转训练：英译输入区 -->
          <div class="section" v-if="scoringMode === 'reverse' && practiceStarted && reverseDisplayRef">
            <div class="section-header">
              <span class="section-label">你的英译（中→英）</span>
              <span class="timer">{{ formatTime(elapsed) }}</span>
              <el-button size="small" type="primary" @click="submitReverseTranslation" :loading="scoring" :disabled="!reverseUserTranslation.trim() || !apiKey">
                API评分
              </el-button>
              <el-button size="small" type="warning" @click="copyReversePrompt" :disabled="!reverseUserTranslation.trim()">
                窗口AI：复制prompt
              </el-button>
              <el-button size="small" type="success" @click="openQwen">
                🌐 打开 Qwen
              </el-button>
            </div>
            <el-input v-model="reverseUserTranslation" type="textarea" :rows="8" resize="vertical"
              placeholder="根据中文参考译文，在此输入你的英文翻译..." />
            <div class="window-ai-paste" style="margin-top:10px">
              <div class="window-ai-label">粘贴窗口AI返回的评分JSON结果：</div>
              <el-input v-model="reverseWindowAIInput" type="textarea" :rows="5" placeholder='{"accuracy":20,"grammar":18,"vocabulary":19,"fluency":21,"total":78,"feedback":"..."}' />
              <el-button type="success" size="small" @click="submitReverseWindowAI" :disabled="!reverseWindowAIInput.trim()" style="margin-top:6px">
                解析并录入评分
              </el-button>
            </div>
          </div>

          <!-- 反转训练对照区 -->
          <div class="section" v-if="scoringMode === 'reverse' && reverseScoredRecord">
            <div class="section-header">
              <span class="section-label">译文对照（反转）</span>
            </div>
            <div class="compare-view">
              <div class="compare-col yours">
                <div class="compare-col-title">你的英译</div>
                <p v-for="(line, i) in reverseDiffResult.userLines" :key="i"
                  :class="line.type"><span v-if="line.html" v-html="line.html" /><span v-else>{{ line.text }}</span></p>
              </div>
              <div class="compare-col ref">
                <div class="compare-col-title">英文原文</div>
                <p v-for="(line, i) in reverseDiffResult.refLines" :key="i"
                  :class="line.type">{{ line.text }}</p>
              </div>
            </div>
          </div>

<!-- 翻译输入区 -->
          <div class="section" v-if="scoringMode !== 'wave' && scoringMode !== 'reverse' && practiceStarted">
            <div class="section-header">
              <span class="section-label">你的译文</span>
              <span class="timer">{{ formatTime(elapsed) }}</span>
              <template v-if="scoringMode === 'api'">
                <el-button size="small" type="primary" @click="submitTranslation" :loading="scoring" :disabled="!userTranslation.trim()">
                  提交AI评分
                </el-button>
              </template>
              <template v-else-if="scoringMode === 'window'">
                <el-button size="small" type="warning" @click="submitTranslation" :disabled="!userTranslation.trim()">
                  一键复制拼接prompt
                </el-button>
                <el-button size="small" type="success" @click="openQwen">
                  🌐 打开 Qwen
                </el-button>
              </template>
            </div>
            <el-input v-model="userTranslation" type="textarea" :rows="8" resize="vertical"
              placeholder="在此输入你的中文翻译..." />
            <!-- 窗口AI模式：粘贴结果 -->
            <div v-if="scoringMode === 'window'" class="window-ai-paste">
              <div class="window-ai-label">粘贴窗口AI返回的评分JSON结果：</div>
              <el-input v-model="windowAIInput" type="textarea" :rows="5" placeholder='{"accuracy":20,"grammar":18,"vocabulary":19,"fluency":21,"total":78,"feedback":"..."}' />
              <el-button type="success" size="small" @click="submitWindowAI" :disabled="!windowAIInput.trim()" style="margin-top:6px">
                解析并录入评分
              </el-button>
            </div>
          </div>

          <!-- 对照区 -->
          <div class="section" v-if="scoredRecord">
            <div class="section-header">
              <span class="section-label">译文对照</span>
            </div>
            <div class="compare-view">
              <div class="compare-col yours">
                <div class="compare-col-title">你的译文</div>
                <p v-for="(line, i) in diffResult.userLines" :key="i"
                  :class="line.type"><span v-if="line.html" v-html="line.html" /><span v-else>{{ line.text }}</span></p>
              </div>
              <div class="compare-col ref">
                <div class="compare-col-title">参考译文</div>
                <p v-for="(line, i) in diffResult.refLines" :key="i"
                  :class="line.type">{{ line.text }}</p>
              </div>
            </div>
          </div>
        </template>
        <el-empty v-else description="选择一篇范文，点击「开始练习」" :image-size="120" />
      </main>

      <!-- 右侧栏：AI评分面板 -->
      <aside class="ett-right" v-if="!isMobile || rightPanelRecord">
        <template v-if="rightPanelRecord">
          <div class="score-card">
            <div class="total-score" :style="{ color: scoreColor(rightPanelRecord.totalScore) }">
              {{ rightPanelRecord.totalScore }}<span class="score-unit">/100</span>
            </div>
            <el-divider />
            <div class="dim-scores">
              <div class="dim-item">
                <span>准确性 ({{ rightPanelRecord.score.accuracy }}/25)</span>
                <el-progress :percentage="rightPanelRecord.score.accuracy / 25 * 100" :color="dimColor(rightPanelRecord.score.accuracy,25)" :stroke-width="8" />
              </div>
              <div class="dim-item">
                <span>语法结构 ({{ rightPanelRecord.score.grammar }}/25)</span>
                <el-progress :percentage="rightPanelRecord.score.grammar / 25 * 100" :color="dimColor(rightPanelRecord.score.grammar,25)" :stroke-width="8" />
              </div>
              <div class="dim-item">
                <span>词汇表达 ({{ rightPanelRecord.score.vocabulary }}/25)</span>
                <el-progress :percentage="rightPanelRecord.score.vocabulary / 25 * 100" :color="dimColor(rightPanelRecord.score.vocabulary,25)" :stroke-width="8" />
              </div>
              <div class="dim-item">
                <span>流畅度 ({{ rightPanelRecord.score.fluency }}/25)</span>
                <el-progress :percentage="rightPanelRecord.score.fluency / 25 * 100" :color="dimColor(rightPanelRecord.score.fluency,25)" :stroke-width="8" />
              </div>
            </div>
          </div>
          <div class="feedback-card">
            <div class="feedback-title">AI 点评</div>
            <div class="feedback-content" v-html="renderedFeedback"></div>
            <div v-if="bilibiliMatches.length" class="bilibili-links">
              <div class="bilibili-title">相关知识点视频：</div>
              <a v-for="link in bilibiliMatches" :key="link.bvid"
                :href="`https://www.bilibili.com/video/${link.bvid}`" target="_blank"
                class="bilibili-card">
                <span class="bilibili-tag">{{ link.keyword }}</span>
                <span class="bilibili-name">{{ link.title }}</span>
                <span class="bilibili-desc">{{ link.desc }}</span>
              </a>
            </div>
          </div>
          <div class="history-card" v-if="records.length > 1">
            <div class="feedback-title">趋势</div>
            <div ref="trendChartRef" style="width:100%;height:160px"></div>
            <div ref="radarChartRef" style="width:100%;height:180px;margin-top:8px"></div>
          </div>

          <!-- 错误结构分析（水波纠错） -->
          <div v-if="normalizeMistakeWaves(rightPanelRecord).length">
            <div class="feedback-title">🌊 错误结构分析（{{ normalizeMistakeWaves(rightPanelRecord).length }}处）</div>
            <div v-for="(mw, wi) in normalizeMistakeWaves(rightPanelRecord)" :key="'mw'+wi" class="mw-card">
              <div class="mw-card-header">
                <span v-if="mw.sentenceIndex !== null" class="mw-sentence-tag">第{{ mw.sentenceIndex + 1 }}句</span>
                <span class="mw-error-tag">{{ mw.errorType || '结构性错误' }}</span>
              </div>
              <div class="mw-block" v-if="mw.studentError">
                <div class="mw-label">学生错译</div>
                <p class="mw-text" style="color:#ef4444">{{ mw.studentError }}</p>
              </div>
              <div class="mw-block" v-if="mw.patternEN">
                <div class="mw-label">卡住你的{{ scoringMode === 'reverse' ? '英文表达' : '英文结构' }}</div>
                <p class="mw-pattern">{{ mw.patternEN }}</p>
              </div>
              <div class="mw-block" v-if="mw.whereStuck">
                <div class="mw-label">为什么容易卡</div>
                <p class="mw-text">{{ mw.whereStuck }}</p>
              </div>
              <div class="mw-block" v-if="mw.examples?.length">
                <div class="mw-label">同类例句</div>
                <div v-for="(ex, i) in mw.examples" :key="i" class="mw-ex-row">
                  <span class="mw-ex-en">{{ ex.en }}</span>
                  <span class="mw-ex-arrow">→</span>
                  <span class="mw-ex-zh">{{ ex.zh }}</span>
                </div>
              </div>
              <div class="mw-block" v-if="mw.nextTime">
                <div class="mw-label">下次遇到怎么拆</div>
                <p class="mw-text" style="color:#22C55E">{{ mw.nextTime }}</p>
              </div>
            </div>
          </div>

          <!-- 翻译错误对照 -->
          <div v-if="rightPanelRecord?.translationErrors?.length">
            <div class="feedback-title">📋 翻译错误对照</div>
            <div class="te-table">
              <div v-for="(te, i) in rightPanelRecord.translationErrors" :key="'te'+i" class="te-row">
                <div class="te-cell te-orig">{{ te.originalEN }}</div>
                <div class="te-cell te-correct">{{ scoringMode === 'reverse' ? (te.correctEN || '') : (te.correctZH || '') }}</div>
                <div class="te-cell te-wrong">{{ scoringMode === 'reverse' ? (te.studentEN || '') : (te.studentZH || '') }}</div>
                <div class="te-cell te-note">{{ te.note || '' }}</div>
              </div>
            </div>
          </div>

          <!-- 生词短语池 -->
          <div class="vocab-pool-card" v-if="vocabPool.length">
            <div class="feedback-title" style="cursor:pointer" @click="showVocabPool = !showVocabPool">
              📚 生词短语池 ({{ vocabPool.length }})
              <span style="font-size:12px;color:#999">{{ showVocabPool ? '收起' : '展开' }}</span>
            </div>
            <div v-if="showVocabPool" class="vocab-list">
              <div v-for="item in vocabPool.slice(0, 20)" :key="item.item" class="vocab-item">
                <div class="vocab-word">
                  <span class="vocab-text">{{ item.item }}</span>
                  <el-tag size="small" :type="item.level === '考研' ? 'warning' : item.level === '六级' ? 'primary' : 'info'">{{ item.level }}</el-tag>
                  <el-tag size="small" type="success" v-if="item.category">{{ item.category }}</el-tag>
                </div>
                <div class="vocab-meaning">{{ item.meaning }}</div>
                <div class="vocab-meta">出现 {{ item.count }} 次 · {{ item.dateCount }} 天</div>
              </div>
            </div>
          </div>
        </template>
        <el-empty v-else description="提交翻译后查看评分" :image-size="100" />
      </aside>
    </div>
    </template>

    <MobileApp v-if="isMobile" />

    <!-- 提示词配置对话框 (P0) -->
    <el-dialog v-model="showPromptConfig" title="提示词配置" width="780px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="评分提示词">
          <el-input v-model="promptConfig.scoringPrompt" type="textarea" :rows="14" />
          <p class="hint-text">窗口AI模式会把这段提示词+原文+译文拼接复制到剪贴板</p>
        </el-form-item>
        <el-form-item label="分段提示词">
          <el-input v-model="promptConfig.segmentPrompt" type="textarea" :rows="8" />
          <p class="hint-text">添加范文时用于AI自动分段出题的提示词</p>
        </el-form-item>
        <el-form-item label="模式说明">
          <div class="mode-desc">
            <p><b>API评分</b>：直接调用DeepSeek API，填写Key即可一键评分</p>
            <p><b>窗口AI</b>：点击按钮复制拼接好的完整prompt→粘贴到任意AI窗口→把AI回复的JSON粘贴回来</p>
          </div>
        </el-form-item>
        <el-form-item label="自定义提示词">
          <div style="width:100%">
            <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
              <el-button size="small" @click="addCustomPrompt">+ 添加</el-button>
            </div>
            <div v-for="p in customPrompts" :key="p.id" style="margin-bottom:10px;padding:8px;border:1px solid #374151;border-radius:6px">
              <div style="display:flex;align-items:center;gap:8px;margin-bottom:4px">
                <el-input v-model="p.name" size="small" style="width:200px" placeholder="提示词名称" />
                <el-button size="small" type="danger" text @click="deleteCustomPrompt(p.id)">删除</el-button>
              </div>
              <el-input v-model="p.content" type="textarea" :rows="6" size="small" placeholder="提示词内容..." />
            </div>
            <p v-if="customPrompts.length === 0" class="hint-text">尚未添加自定义提示词，点击"+ 添加"创建</p>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPromptConfig = false">取消</el-button>
        <el-button type="primary" @click="savePromptConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- 历史记录详情面板 (P2) -->
    <el-drawer v-model="showHistoryPanel" title="练习历史" size="85%" direction="rtl">
      <template v-if="historyEssay">
        <div class="history-essay-info">
          <h3>{{ historyEssay.title }}</h3>
          <p class="history-meta">{{ historyEssay.source }} · {{ historyEssay.date }}</p>
        </div>
        <el-divider />
        <div v-if="historyRecords.length === 0" class="history-empty">
          <el-empty description="暂无练习记录" :image-size="80" />
        </div>
        <div v-for="(rec, idx) in historyRecords" :key="rec.id" class="history-record-card">
          <div class="history-record-header">
            <span class="history-record-date">{{ rec.date }}</span>
            <el-tag :type="scoreTag(rec.totalScore)" size="small">{{ rec.totalScore }}分</el-tag>
            <span class="history-time">{{ formatTime(rec.timeSpent || 0) }}</span>
          </div>
          <div class="history-dims">
            <span>准确性{{ rec.score.accuracy }}</span>
            <span>语法{{ rec.score.grammar }}</span>
            <span>词汇{{ rec.score.vocabulary }}</span>
            <span>流畅{{ rec.score.fluency }}</span>
          </div>
          <div class="history-translation">
            <div class="history-label">你的译文：</div>
            <p>{{ rec.userTranslation?.slice(0, 200) }}{{ rec.userTranslation?.length > 200 ? '...' : '' }}</p>
          </div>
          <div class="history-feedback" v-if="rec.feedback">
            <div class="history-label">点评：</div>
            <p v-html="rec.feedback.replace(/\n/g, '<br>').replace(/\*\*(.*?)\*\*/g, '<b>$1</b>').slice(0, 300)"></p>
          </div>
          <el-divider v-if="idx < historyRecords.length - 1" />
        </div>
      </template>
    </el-drawer>

    <!-- 添加范文对话框 -->
    <el-dialog v-model="showAddDialog" title="添加练习范文" width="640px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="newEssay.title" placeholder="如：环境保护" />
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="newEssay.source" placeholder="如：考研英语一 2023 Text 4" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="newEssay.date" type="date" value-format="YYYY-MM-DD" placeholder="练习日期" />
        </el-form-item>
        <el-form-item label="英文原文">
          <el-input v-model="newEssay.originalEN" type="textarea" :rows="8" placeholder="粘贴英文原文..." />
        </el-form-item>
        <el-form-item label="参考译文">
          <el-input v-model="newEssay.referenceTranslation" type="textarea" :rows="6" placeholder="粘贴参考译文..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="addEssay" :loading="aiProcessing" :disabled="!newEssay.originalEN.trim()">
          AI自动分段出题
        </el-button>
      </template>
    </el-dialog>
  </div>


    <!-- 生词池全量查看对话框 -->
    <el-dialog v-model="showVocabPoolDialog" title="生词短语池" width="720px" destroy-on-close class="mob-sheet-d">
      <div style="margin-bottom:12px;display:flex;align-items:center;gap:8px">
        <el-input v-model="vocabSearchQuery" placeholder="搜索单词或释义..." clearable size="small" style="width:260px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="vocabLevelFilter" placeholder="难度筛选" clearable size="small" style="width:130px">
          <el-option label="四级" value="四级" />
          <el-option label="六级" value="六级" />
          <el-option label="考研" value="考研" />
          <el-option label="雅思" value="雅思" />
          <el-option label="托福" value="托福" />
          <el-option label="超纲" value="超纲" />
        </el-select>
        <span style="font-size:12px;color:#777;white-space:nowrap">共 {{ filteredVocabPool.length }} 词</span>
      </div>
      <div class="vocab-pool-full-list">
        <div v-for="item in filteredVocabPool" :key="item.item" class="vocab-item">
          <div class="vocab-word">
            <span class="vocab-text">{{ item.item }}</span>
            <el-tag size="small" :type="item.level === '考研' ? 'warning' : item.level === '六级' ? 'primary' : 'info'">{{ item.level }}</el-tag>
            <el-tag size="small" type="success" v-if="item.category">{{ item.category }}</el-tag>
          </div>
          <div class="vocab-meaning">{{ item.meaning }}</div>
          <div class="vocab-meta">出现 {{ item.count }} 次 · {{ item.dateCount }} 天</div>
        </div>
        <el-empty v-if="filteredVocabPool.length === 0 && vocabPool.length > 0" description="无匹配结果" :image-size="60" />
        <el-empty v-if="vocabPool.length === 0" description="暂无生词数据，完成评分后自动累积" :image-size="60" />
      </div>
    </el-dialog>

    <!-- 图片导入翻译题对话框 -->
    <el-dialog v-model="showImageImportDialog" title="图片导入翻译题" width="800px" destroy-on-close @opened="onImageDialogOpened">
      <!-- 一级：导入模式 -->
      <div class="extract-mode-bar">
        <el-radio-group v-model="imageImportMode" size="small">
          <el-radio-button value="single">单题多图</el-radio-button>
          <el-radio-button value="batch">逐张批量</el-radio-button>
        </el-radio-group>
      </div>
      <!-- 二级：提取模式 -->
      <div class="extract-mode-bar" style="margin-top:6px">
        <el-radio-group v-model="imageExtractMode" @change="onExtractModeChange" size="small">
          <el-radio-button value="strict">教辅·一字不易</el-radio-button>
          <el-radio-button value="reference">参考·灵活提取</el-radio-button>
          <el-radio-button value="phrase">反转短语·中留英填</el-radio-button>
        </el-radio-group>
        <el-button size="small" text @click="saveImagePrompt" style="margin-left:8px">保存当前提示词</el-button>
      </div>

      <!-- ===== 单题多图模式 ===== -->
      <div v-if="imageImportMode === 'single'" class="image-import-layout">
        <div class="image-import-left">
          <div class="image-slots-section">
            <div class="image-import-label">
              截图粘贴区（点击框内 Ctrl+V 粘贴，最多5张）
              <el-button size="small" text @click="addImageSlot" :disabled="imageSlots.length >= 5">+ 添加槽位</el-button>
              <el-button size="small" text type="primary" @click="selectImageFiles('single')">📁 选择图片</el-button>
            </div>
            <div class="image-slots-grid">
              <div v-for="(slot, si) in imageSlots" :key="si" class="image-slot-wrap">
                <div class="image-slot"
                  :class="{ 'has-image': slot.url }"
                  @paste="onSlotPaste($event, si)"
                  tabindex="0">
                  <img v-if="slot.url" :src="slot.url" class="slot-preview" />
                  <div v-else class="slot-placeholder">
                    <span>槽 {{ si + 1 }}</span>
                    <span class="slot-hint">Ctrl+V</span>
                  </div>
                </div>
                <el-button v-if="imageSlots.length > 1" size="small" type="danger" text @click="removeImageSlot(si)" class="slot-remove">✕</el-button>
              </div>
            </div>
          </div>
        </div>
        <div class="image-import-right">
          <div class="image-import-section">
            <div class="image-import-label">提示词模板（可修改 · {{ imageExtractMode === 'strict' ? '一字不易' : '灵活提取' }}模式）</div>
            <el-input v-model="imageImportPrompt" type="textarea" :rows="8" resize="vertical" />
            <el-button type="primary" size="small" @click="copyImagePrompt" style="margin-top:8px" :disabled="!imageSlots.some(s => s.url)">
              一键复制提示词（{{ imageSlots.filter(s => s.url).length }}图）
            </el-button>
            <span class="hint-text" style="margin-left:8px">复制后粘贴到其他AI窗口，同时粘贴截图</span>
          </div>
          <el-divider />
          <div class="image-import-section">
            <div class="image-import-label">粘贴AI返回的JSON结果：</div>
            <el-input v-model="imageImportResult" type="textarea" :rows="8" placeholder="粘贴另一个AI返回的JSON..." />
            <div style="margin-top:8px;display:flex;gap:8px">
              <el-button type="success" size="small" @click="importFromImageJson" :disabled="!imageImportResult.trim()">
                解析导入
              </el-button>
              <el-button type="success" size="small" @click="importBatchFromImageJson" :disabled="!imageImportResult.trim()">
                解析批量导入
              </el-button>
              <el-button v-if="imageExtractMode === 'phrase'" type="warning" size="small" @click="importPhraseFromImageJson" :disabled="!imageImportResult.trim()">
                导入短语
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== 逐张批量模式 ===== -->
      <div v-else class="batch-import-layout">
        <div class="batch-import-left">
          <div class="image-import-label">
            逐张粘贴区（点击后 Ctrl+V 粘贴，或点"选择图片"批量选取）
            <el-button size="small" text type="primary" @click="selectImageFiles('batch')">📁 选择图片</el-button>
          </div>
          <div class="batch-paste-zone" @paste="onBatchImagePaste" tabindex="0">
            <span v-if="!batchImages.length">在此区域 Ctrl+V 粘贴截图，每次一张，自动累积</span>
            <span v-else>已累积 {{ batchImages.length }} 张，继续 Ctrl+V 添加</span>
          </div>
          <div v-if="batchImages.length" class="batch-image-list">
            <div v-for="(img, bi) in batchImages" :key="bi" class="batch-image-item">
              <img :src="img.url" class="batch-thumb" />
              <span class="batch-idx">{{ bi + 1 }}</span>
              <el-button size="small" type="danger" text @click="removeBatchImage(bi)" class="batch-remove">✕</el-button>
            </div>
          </div>
          <div v-if="batchImages.length" style="margin-top:8px;display:flex;gap:8px">
            <el-button type="primary" size="small" @click="copyAllBatchPrompt">一键复制提示词+全部截图</el-button>
            <el-button size="small" type="danger" text @click="clearBatchImages">清空全部</el-button>
          </div>
        </div>
        <div class="image-import-right">
          <div class="image-import-section">
            <div class="image-import-label">提示词模板（可修改 · {{ imageExtractMode === 'strict' ? '一字不易' : '灵活提取' }}模式）</div>
            <el-input v-model="imageImportPrompt" type="textarea" :rows="8" resize="vertical" />
            <span class="hint-text" style="margin-top:4px;display:block">粘贴完所有截图后，点左边按钮一键复制（提示词+全部截图竖拼），粘贴到AI窗口即可</span>
          </div>
          <el-divider />
          <div class="image-import-section">
            <div class="image-import-label">粘贴AI返回的JSON数组：</div>
            <el-input v-model="imageImportResult" type="textarea" :rows="8" placeholder="粘贴合并后的JSON数组 [{...},{...}]" />
            <el-button type="success" size="small" style="margin-top:8px" @click="importAllBatch" :disabled="!imageImportResult.trim()">
              全部导入（{{ batchImages.length || 0 }}张 → 逐张出题 → 合并数组）
            </el-button>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 短语默写练习弹窗（方案H：需键盘→全屏） -->
    <template v-if="isMobile && showPhrasePracticeDialog">
      <div class="mob-fullscreen-overlay">
        <div class="mob-fullscreen-hdr">
          <span style="width:24px"></span>
          <span class="mob-fullscreen-title">短语默写</span>
          <span @click="showPhrasePracticeDialog = false; phraseRevealAnswer=false; phraseUserAnswer=''" style="font-size:18px;line-height:1">✕</span>
        </div>
        <div class="mob-fullscreen-body" v-if="phraseCards.length">
          <!-- 卡片组选择 -->
          <div class="mob-phrase-set-bar">
            <el-select v-model="phraseSelectedSetId" size="small" placeholder="选择卡片组" style="flex:1" @change="selectPhraseSet(phraseSelectedSetId)">
              <el-option v-for="set in phraseCards" :key="set.id" :label="set.title + ' (' + set.pairs.length + '对)'" :value="set.id" />
            </el-select>
            <span style="font-size:calc(9px*var(--ett-fs,1));color:#888">{{ phraseFilterReview ? '需复习' : '全部' }}</span>
            <el-switch v-model="phraseFilterReview" size="small" style="margin-left:4px" />
          </div>
          <!-- 进度条 -->
          <div class="mob-phrase-progress" v-if="displayPhrasePairs.length">
            <span style="font-size:calc(9px*var(--ett-fs,1));color:#888">{{ phraseCurrentIdx + 1 }}/{{ displayPhrasePairs.length }}</span>
            <el-progress :percentage="Math.round((phraseCurrentIdx + 1) / displayPhrasePairs.length * 100)" :stroke-width="4" style="flex:1;margin:0 8px" />
          </div>
          <!-- 练习卡片 -->
          <div v-if="displayPhrasePairs.length" class="mob-phrase-card" :class="{ revealed: phraseRevealAnswer }">
            <div class="mob-phrase-zh">{{ displayPhrasePairs[phraseCurrentIdx]?.zh }}</div>
            <div class="mob-phrase-input-area">
              <div class="mob-phrase-label">你的英文：</div>
              <el-input v-model="phraseUserAnswer" type="textarea" :rows="3" resize="vertical"
                placeholder="根据中文写出英文..." @keyup.enter.exact="phraseRevealAnswer ? phraseMarkReview() : revealPhraseAnswer()" />
            </div>
            <div class="mob-phrase-answer" v-if="phraseRevealAnswer">
              <div class="mob-phrase-label">原文：</div>
              <div class="mob-phrase-original">{{ displayPhrasePairs[phraseCurrentIdx]?.en }}</div>
            </div>
            <div class="mob-phrase-btns" v-if="phraseRevealAnswer">
              <el-button type="danger" plain size="small" @click="phraseMarkReview" style="flex:1">需复习</el-button>
              <el-button type="success" size="small" @click="phraseMarkCorrect" style="flex:1">正确</el-button>
            </div>
            <div class="mob-phrase-btns" v-else>
              <el-button type="primary" size="small" @click="revealPhraseAnswer" :disabled="!phraseUserAnswer.trim()" style="flex:1">查看答案</el-button>
            </div>
          </div>
          <el-empty v-else description="暂无短语" :image-size="80" />
          <!-- 翻页 -->
          <div class="mob-phrase-nav" v-if="displayPhrasePairs.length > 1">
            <el-button size="small" @click="phrasePrevCard" :disabled="phraseCurrentIdx <= 0">◀ 上一张</el-button>
            <el-button size="small" @click="phraseNextCard" :disabled="phraseCurrentIdx >= displayPhrasePairs.length - 1">下一张 ▶</el-button>
          </div>
        </div>
        <el-empty v-else description="暂无短语卡片组" :image-size="80" style="margin-top:60px" />
      </div>
    </template>
    <el-dialog v-else v-model="showPhrasePracticeDialog" title="短语默写·中留英填" width="800px" destroy-on-close @closed="phraseRevealAnswer=false;phraseUserAnswer=''">
      <div v-if="!phraseCards.length" style="text-align:center;padding:40px;color:#777">
        <p>暂无短语卡片组，请先通过「图片导入 → 反转短语·中留英填」导入</p>
      </div>
      <div v-else class="phrase-practice-layout">
        <div class="phrase-set-list">
          <div class="phrase-set-label">
            卡片组 ({{ phraseCards.length }})
            <el-button size="small" text class="phrase-set-toggle" @click="phraseShowSetList = !phraseShowSetList">
              {{ phraseShowSetList ? '收起' : '展开' }}
            </el-button>
          </div>
          <div v-for="(set, si) in phraseCards" :key="set.id" v-show="phraseShowSetList"
            class="phrase-set-item"
            :class="{ active: phraseSelectedSetId === set.id }"
            @click="selectPhraseSet(set.id)">
            <el-button class="phrase-set-delete" size="small" text type="danger" @click.stop="deletePhraseSet(set.id)" title="删除">×</el-button>
            <div class="phrase-set-title">{{ set.title }}</div>
            <div class="phrase-set-meta">{{ set.pairs.length }}对 · {{ set.date }}</div>
            <div class="phrase-set-progress">{{ getPhraseProgress(set) }}</div>
          </div>
        </div>
        <div class="phrase-practice-area" v-if="phraseSelectedSet">
          <div class="phrase-practice-header">
            <span class="phrase-practice-title">{{ phraseSelectedSet.title }}</span>
            <span class="phrase-practice-source">{{ phraseSelectedSet.source }}</span>
            <el-switch v-model="phraseFilterReview" size="small" active-text="只看需复习" inactive-text="全部" style="margin-left:auto" />
          </div>
          <div class="phrase-progress-bar">
            <span>{{ displayPhrasePairs.length ? phraseCurrentIdx + 1 : 0 }} / {{ displayPhrasePairs.length }}</span>
            <el-progress :percentage="displayPhrasePairs.length ? Math.round((phraseCurrentIdx + 1) / displayPhrasePairs.length * 100) : 0" :stroke-width="6" style="flex:1;margin:0 8px" />
          </div>
          <div v-if="displayPhrasePairs.length === 0" style="text-align:center;padding:40px;color:#777">
            <p v-if="phraseFilterReview">暂无需要复习的短语，干得漂亮！</p>
            <p v-else>暂无短语</p>
          </div>
          <div v-else class="phrase-card" :class="{ revealed: phraseRevealAnswer }">
            <div class="phrase-zh-display">{{ displayPhrasePairs[phraseCurrentIdx]?.zh }}</div>
            <el-divider />
            <div class="phrase-en-area">
              <div class="phrase-en-label">你的英文：</div>
              <el-input v-model="phraseUserAnswer" type="textarea" :rows="3" resize="vertical"
                placeholder="根据中文写出英文..." @keyup.enter.exact="phraseRevealAnswer ? phraseMarkReview() : revealPhraseAnswer()" />
            </div>
            <div class="phrase-answer-reveal" v-if="phraseRevealAnswer">
              <div class="phrase-en-label">原文：</div>
              <div class="phrase-en-original">{{ displayPhrasePairs[phraseCurrentIdx]?.en }}</div>
            </div>
            <div class="phrase-actions" v-if="phraseRevealAnswer">
              <el-button type="danger" plain size="small" @click="phraseMarkReview">需复习</el-button>
              <el-button type="success" size="small" @click="phraseMarkCorrect">正确</el-button>
            </div>
            <div class="phrase-actions" v-else>
              <el-button type="primary" size="small" @click="revealPhraseAnswer" :disabled="!phraseUserAnswer.trim()">查看答案</el-button>
            </div>
          </div>
          <div class="phrase-nav" v-if="displayPhrasePairs.length > 1">
            <el-button size="small" @click="phrasePrevCard" :disabled="phraseCurrentIdx <= 0">上一张</el-button>
            <el-button size="small" @click="phraseNextCard" :disabled="phraseCurrentIdx >= displayPhrasePairs.length - 1">下一张</el-button>
          </div>
        </div>
        <div class="phrase-practice-area" v-else>
          <el-empty description="请从左侧选择一个卡片组开始练习" :image-size="80" />
        </div>
      </div>
      <template #footer>
        <el-button @click="showPhrasePracticeDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 词根词缀分析 -->
    <el-dialog v-model="showWordAnalysis" title="词根词缀分析" width="580px" destroy-on-close class="wa-dialog mob-bottom-sheet" @closed="wordAnalysis=null;showWordAnswer=false;userMeaningGuess='';selectedWord='';wordInput=''">
      <div v-if="!selectedWord && !wordAnalysis" class="wa-init">
        <p style="font-size:11px;color:#888;text-align:center;margin-bottom:10px">输入一个英文单词，分析其词根词缀结构</p>
        <div style="display:flex;gap:8px">
          <el-input v-model="wordInput" placeholder="输入单词，如：unbelievable" size="small" @keyup.enter="startWordAnalysis" />
          <el-button type="primary" size="small" @click="startWordAnalysis" :disabled="!wordInput.trim()">分析</el-button>
        </div>
      </div>
      <div v-else-if="wordAnalyzing" class="wa-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>分析中...</span>
      </div>
      <template v-else-if="wordAnalysis">
        <!-- JSON解析失败回退：直接显示AI原始输出 -->
        <template v-if="wordAnalysis._parseFailed">
          <div class="wa-word">{{ selectedWord }}</div>
          <div class="wa-raw-notice">JSON解析失败，以下是AI原始分析结果：</div>
          <div class="wa-raw-content">{{ wordAnalysis.raw }}</div>
        </template>
        <!-- 正常结构化展示 -->
        <template v-else>
        <div class="wa-word">{{ wordAnalysis.word || selectedWord }}</div>
        <div class="wa-breakdown">
          <div v-for="(part, i) in wordAnalysis.breakdown" :key="i" class="wa-part"
            :class="{ 'wa-part-misleading': part.reliability === 'misleading' }">
            <span class="wa-part-text">{{ part.part }}</span>
            <el-tag size="small" :type="part.type === 'prefix' ? 'primary' : part.type === 'suffix' ? 'success' : 'warning'">
              {{ part.type === 'prefix' ? '前缀' : part.type === 'suffix' ? '后缀' : '词根' }}
            </el-tag>
            <span class="wa-part-meaning">{{ part.meaning }}</span>
            <span v-if="part.reliability === 'misleading'" class="wa-badge wa-badge-bad">✗ 误导</span>
            <span v-else-if="part.reliability === 'uncommon'" class="wa-badge wa-badge-warn">△ 不常见</span>
            <span v-else class="wa-badge wa-badge-good">✓</span>
          </div>
        </div>
        <div v-if="wordAnalysis.hasMisleadingRoot && wordAnalysis.misleadingDetail" class="wa-warning">
          ⚠️ {{ wordAnalysis.misleadingDetail }}
        </div>

        <el-divider />

        <!-- Step 2: 推理测试 -->
        <div class="wa-test">
          <div class="wa-test-title">根据以上词根词缀，你推测这个词是什么意思？写下你的推理过程：</div>
          <el-input v-model="userMeaningGuess" type="textarea" :rows="3" resize="vertical"
            placeholder="如：un=不 + believe=相信 + able=能的 → 不能相信的 → 难以置信的"
            @keyup.enter.exact="onWordAnswerEnter" />
          <el-button type="primary" size="small" @click="showWordAnswer = true" :disabled="!userMeaningGuess.trim()" style="margin-top:8px">
            确认，看答案
          </el-button>
        </div>

        <!-- Step 3: 答案 -->
        <div v-if="showWordAnswer" class="wa-answer">
          <el-divider />
          <div class="wa-answer-label">正确答案</div>
          <div class="wa-answer-meaning">{{ wordAnalysis.wordMeaning }}</div>
          <div class="wa-answer-label" style="margin-top:10px">推理链</div>
          <div class="wa-answer-reasoning">{{ wordAnalysis.rootReasoning }}</div>
          <el-divider />
          <div class="wa-answer-label">你的推理</div>
          <div class="wa-answer-yours">{{ userMeaningGuess }}</div>
          <div style="text-align:center;margin-top:12px">
            <el-button type="success" size="small" @click="addWordToVocab" :disabled="wordInVocab">
              {{ wordInVocab ? '已在生词池' : '+ 加入生词池' }}
            </el-button>
          </div>
        </div>
        </template>
      </template>
      <el-empty v-else description="分析失败，请重试" :image-size="80" />
    </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, nextTick, h, provide } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting, CopyDocument, Link, VideoPlay, ArrowLeft, Loading, Search } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { Filesystem, Directory } from '@capacitor/filesystem'
import { App } from '@capacitor/app'
import MobileApp from './components/MobileApp.vue'

// ========== 数据状态 ==========
const apiKey = ref(localStorage.getItem('ett_apikey') || '')

const essays = ref([])
const records = ref([])
const settings = reactive({
  dailyGoal: 1,
  targetScore: 80
})

const currentEssayId = ref(null)
const userTranslation = ref('')
const practiceStarted = ref(false)
const scoring = ref(false)
const elapsed = ref(0)
let timerInterval = null

const darkMode = ref(true)
const isMobile = ref(false)
const fontSize = ref(localStorage.getItem("ett_fontSize") || "medium")
function checkMobile() { isMobile.value = window.innerWidth < 768 }
window.addEventListener('resize', checkMobile)
function openQwen() {
  window.open('https://chat.qwen.ai', '_blank')
}
const selectedSeg = ref(null)
const showAddDialog = ref(false)
const aiProcessing = ref(false)
const calendarDate = ref(new Date())

// P0: 提示词系统
const scoringMode = ref('api')
const showPromptConfig = ref(false)
const promptConfig = ref({
  scoringPrompt: '',
  segmentPrompt: '',
  imageStrictPrompt: '',
  imageRefPrompt: '',
  imagePhrasePrompt: '',
  wavePrompt: ''
})
const windowAIInput = ref('')
// 反转训练
const reverseUserTranslation = ref('')
const reverseGenerating = ref(false)
const reverseChineseRef = ref('')
const reverseWindowAIInput = ref('')
const reverseScoredRecord = ref(null)
// 词根词缀分析
const showWordAnalysis = ref(false)
const selectedWord = ref('')
const wordInput = ref('')
const wordAnalysis = ref(null)
const wordAnalyzing = ref(false)
const userMeaningGuess = ref('')
const showWordAnswer = ref(false)
const wordAnalysisCache = reactive({})
const wordRootsStore = reactive({})
let _wordRootsDirty = false
let _wordRootsTimer = null

function loadWordRoots() {
  // Word roots are loaded from ett_backup localStorage via loadData()
}
function saveWordRoots() {
  if (!_wordRootsDirty) return
  _wordRootsDirty = false
  clearTimeout(_wordRootsTimer)
  _wordRootsTimer = setTimeout(() => {
    // Saved via syncData in ett_backup localStorage
    syncData()
  }, 800)
}
const manualVocab = ref([])
const customPrompts = ref([])
// 反转短语卡片
const phraseCards = ref([])
const showPhrasePracticeDialog = ref(false)
const phraseSelectedSetId = ref(null)
const phraseCurrentIdx = ref(0)
const phraseUserAnswer = ref('')
const phraseRevealAnswer = ref(false)
const phraseFilterReview = ref(false)
const phraseShowSetList = ref(true)

// 短语默写 computed
const phraseSelectedSet = computed(() => phraseCards.value.find(c => c.id === phraseSelectedSetId.value) || null)
const displayPhrasePairs = computed(() => {
  if (!phraseSelectedSet.value) return []
  if (!phraseFilterReview.value) return phraseSelectedSet.value.pairs
  return phraseSelectedSet.value.pairs.filter((_, i) => phraseSelectedSet.value.practiceState[i] === 'review')
})

// 短语默写方法
function selectPhraseSet(id) {
  phraseSelectedSetId.value = id
  phraseCurrentIdx.value = 0
  phraseUserAnswer.value = ''
  phraseRevealAnswer.value = false
}
function getPhraseProgress(set) {
  const total = set.pairs.length
  const done = Object.keys(set.practiceState || {}).length
  const correct = Object.values(set.practiceState || {}).filter(s => s === 'correct').length
  return done ? `已练${done}/${total} ✓${correct}` : '未开始'
}
function revealPhraseAnswer() {
  if (!phraseUserAnswer.value.trim()) return
  phraseRevealAnswer.value = true
}
function phraseMarkCorrect() {
  const set = phraseSelectedSet.value
  if (!set) return
  set.practiceState[phraseCurrentIdx.value] = 'correct'
  syncData()
  phraseAdvance()
}
function phraseMarkReview() {
  const set = phraseSelectedSet.value
  if (!set) return
  set.practiceState[phraseCurrentIdx.value] = 'review'
  syncData()
  phraseAdvance()
}
function phraseAdvance() {
  const pairs = displayPhrasePairs.value
  if (phraseCurrentIdx.value < pairs.length - 1) {
    phraseCurrentIdx.value++
    phraseUserAnswer.value = ''
    phraseRevealAnswer.value = false
  } else {
    ElMessage.success('本轮练习完成！')
    phraseRevealAnswer.value = false
    phraseUserAnswer.value = ''
  }
}
function phrasePrevCard() {
  if (phraseCurrentIdx.value > 0) {
    phraseCurrentIdx.value--
    phraseUserAnswer.value = ''
    phraseRevealAnswer.value = false
  }
}
function phraseNextCard() {
  phraseAdvance()
}
function deletePhraseSet(id) {
  phraseCards.value = phraseCards.value.filter(c => c.id !== id)
  if (phraseSelectedSetId.value === id) {
    phraseSelectedSetId.value = phraseCards.value[0]?.id || null
    phraseCurrentIdx.value = 0
    phraseRevealAnswer.value = false
  }
  syncData()
  ElMessage.success('卡片组已删除')
}

// 图片导入
const showImageImportDialog = ref(false)
const imagePreviewUrl = ref('')
const imagePasteZoneRef = ref(null)
const imageImportPrompt = ref('')
const imageImportResult = ref('')

// 批注系统（全局，每个essay独立存储）
const annoMode = ref(false)
const isDrawing = ref(false)
const drawColor = ref('#FF0000')
const drawWidth = ref(3)
const isErasing = ref(false)
const currentAnnoStroke = ref([])
const annoCanvasRef = ref(null)
const annoMainRef = ref(null)
const originalRef = ref(null)

const drawColors = [
  { color: '#FF0000', css: '#FF0000', name: '红色' },
  { color: '#00AA00', css: '#00AA00', name: '绿色' },
  { color: '#0066FF', css: '#0066FF', name: '蓝色' },
  { color: '#FF8800', css: '#FF8800', name: '橙色' },
  { color: '#000000', css: '#000000', name: '黑色' },
]

// 每个essay的批注存储: { [essayId]: [{points, color, width}, ...] }
const essayAnnotations = ref({})
const currentAnnoCount = computed(() => {
  if (!currentEssayId.value) return 0
  return (essayAnnotations.value[currentEssayId.value] || []).length
})

	const IMAGE_IMPORT_DEFAULT_PROMPT = `请分析这张考研英语教辅截图（通常为多张拼接：顶部题目原文+下方解析）。你的任务是：【意群逻辑切分】+【简洁考点提取】+【教辅详解无损暂存】。

请严格输出以下JSON格式的数据，不要包含任何markdown标记（如\`\`\`json），不要任何额外解释文字：

{
  "raw_text_archive": "【无损存储层】将截图所有文字一字不落地提取到这里。包括：英文原句、选择题题干及[A][B][C][D]选项、所有中文解析、小标题、编号、例句对比等。保留所有换行符(\\n)和标点，绝对禁止概括、删减。",

  "title": "根据文章主题自拟一个简洁中文标题（如'人工智能的伦理困境''气候变化的科学争议''美国司法体系的演变'等），必须贴合原文内容，禁止用'考研英语阅读理解'或'长难句分析'等泛称",
  "source": "来源（如：考研英语一 20XX年 Text X）",
  "date": "YYYY-MM-DD（如无法确定填YYYY-01-01）",
  "sourceNote": "用一句话说明截图资料类型",

  "originalEN": "【展示层】从raw_text_archive顶部提取完整的英文原文段落，一字不易。若截图底部英文被截断，请根据句法逻辑自动补全。",

  "referenceTranslation": "【展示层】优先提取截图中带'参考译文/译文/翻译'标识的中文段落；若截图中未提供参考译文，请你自己作为考研英语翻译专家进行精准翻译。要求：忠实原文结构、术语准确、行文符合中文学术表达习惯，严禁生硬机翻腔。",

  "segments": [
    {
      "en": "【展示层】按语法意群切分的英文分句（确保语义完整，不要碎片化；若教辅编号有误请智能修正）",
      "contextZH": "【展示层-简洁】该分句的简短中文翻译或结构提示（1句话，如'祈使句结构，以...开始'）",
      "keyPoints": ["【展示层-简洁】核心考点关键词1（短语形式，如'祈使句'）", "考点2（如'begin with短语'）", "考点3（如'for作连词表原因'）"],
      "raw_teaching_note": "【存储层-可选】从raw_text_archive复制该分句对应的教辅详细解析原文（包含易错点、例句对比、词义辨析等完整讲解）；若无详细解析则留空字符串''"
    }
  ]
}

⚠️ 核心铁律：

【无损存储层】
1. raw_text_archive 必须100%还原截图所有文字（含选择题、长篇解析、例句、编号笔误等），供后端存储/后续环节调用。

【展示层-简洁为主】
2. originalEN / segments.en：英文原文一字不易；segments按语法意群逻辑切分（通常3-4段），不要机械照抄教辅的碎片化编号。
3. contextZH：简短中文翻译或结构提示，1句话以内，不要长篇大论。
4. keyPoints：核心考点关键词，用短语形式（如'祈使句'、'for表原因'、'where引导不定式'），禁止复制教辅长篇解析。
5. 若截图底部英文被截断，originalEN和segments.en必须根据完整句法逻辑自动补全，严禁输出残缺句。

【参考译文-智能处理】
6. referenceTranslation 字段：截图有明确译文则原样提取；截图无译文则由模型直接生成高质量翻译。生成译文需达到考研阅卷标准，语义严密、逻辑通顺。

【存储层-详解隔离】
7. raw_teaching_note：仅当教辅对该分句有详细解析时，从raw_text_archive复制对应原文；否则留空""。这是存放长篇详解的唯一位置，不要混入keyPoints。

【输出格式】
8. 只返回纯JSON字符串，不要使用\`\`\`json代码块包裹，不要有任何开头或结尾的废话。
9. 遇到字迹模糊无法辨认的，用[?]标注不确定位置，不要猜词。`;

const IMAGE_IMPORT_PROMPT_REFERENCE = `请分析以下考研英语一翻译相关截图。截图中的教辅资料（真题解析、参考译文、长难句讲解等）作为参考依据，可以综合判断后提取最优内容。提取并生成以下JSON格式的数据：

{
  "raw_text_archive": "截图全部文字OCR结果",
  "title": "根据文章主题自拟简洁中文标题",
  "source": "来源（如：考研英语一 20XX年 Text X）",
  "date": "YYYY-MM-DD",
  "sourceNote": "用一句话说明截图资料类型",
  "originalEN": "完整的英文原文",
  "referenceTranslation": "参考中文翻译",
  "segments": [
    {
      "en": "英文分句1",
      "contextZH": "简短中文背景提示",
      "keyPoints": ["考点1", "考点2"],
      "raw_teaching_note": "教辅解析原文（如有；无则空字符串）"
    }
  ]
}

要求：
1. originalEN完整提取英文原文，如有多个版本以最清晰的为准
2. referenceTranslation提取参考中文翻译，如有多个版本综合取最优
3. 教辅中的断句和考点分析可作为参考，但你可以根据原文结构灵活调整
4. sourceNote用一句话描述截图资料类型
5. raw_text_archive存截图全文OCR，raw_teaching_note存逐句教辅解析
6. 只返回JSON，不要加任何其他文字`;

const IMAGE_IMPORT_PROMPT_PHRASE = `请分析这张考研英语教辅截图（可能包含单词/短语替换表、例句+中文翻译、写作升级表达等表格型内容）。你的任务是将截图内容转化为"看中文写英文"的默写练习对。

⚠️ 此工具用于"反转默写训练"：用户看到 zh 字段的中文提示，凭记忆写出对应的英文。所以 zh 必须是可直接当作翻译提示的自然中文，不能是标题/标签/分类名。

请严格输出以下JSON格式，不要包含任何markdown标记（如\`\`\`json），不要任何额外解释文字：

{
  "title": "根据内容领域自拟简洁中文标题（如'图画描述引入句式''原因分析升级词''举例论证句式'等）",
  "source": "来源（如：考研英语作文模板 B站：AI归来 微信公众号：AI归来）",
  "date": "YYYY-MM-DD（如无法确定填当前日期）",
  "sourceNote": "用一句话说明截图资料类型（如'被替换词→替换词升级表''待选句式+中文翻译'等）",
  "pairs": [
    {"en": "用户需要写出的英文", "zh": "帮用户回忆起这个英文的中文提示"},
    {"en": "英文表达2", "zh": "中文提示2"}
  ]
}

【zh 字段的核心铁律】
zh 必须是用户读完后就知道要写什么英文的自然中文。以下是正确与错误的写法对比：

❌ 错误 zh（标签/分类/元信息，不可用）：
  "图画描述（升级前）"  ← 这是分类标签，不是中文意思
  "图画描述."           ← 这是功能说明，不是翻译
  "图画描述引入句被替换词" ← 这是小节标题
  "如果是两幅图..."     ← 这是教学说明

✅ 正确 zh（自然中文翻译或提示）：
  "如上图所示"                        （对应 As is shown in the picture above）
  "如上图象征性地描绘的那样"            （对应 As is symbolically depicted in the figure above）
  "图中无可争议的是..."                （对应 What looks beyond dispute in the drawing is that）
  "这幅漫画呈现了一个发人深省的场景："   （对应 The cartoon provides us with a thought-provoking scene:）
  "同样地/相反地/不幸地/与此同时"      （对应 Similarly/On the contrary/Unfortunately/Meanwhile）

【处理不同截图内容的规则】

1. 替换词升级表（如"被替换词→替换词"或"升级前→升级后"）：
   每行做成一个 pair，en=升级后的英文，zh=该英文的中文释义。
   并在 zh 末尾用括号标注原词，如 "展示、描绘（原词：show）"
   多个同义替换词可以合并为一对，用 / 分隔，如 en: "illustrated/ depicted/ demonstrated"
   升级前的句子和升级后的句子各自独立成对，zh 写整句中文翻译。

2. 待选句式+中文翻译：
   en=英文句式（一字不易），zh=截图中已有的中文翻译。
   如果截图没有中文翻译，请你自己准确翻译，zh 必须自然通顺、符合中文学术表达。

3. 例句+中文翻译：
   en=英文例句，zh=对应的中文翻译。

4. 纯中文教学说明/注释（如"注1：受限于字数要求..."）：
   不要提取为 pair。这些是教学提示，不是默写内容。

5. 表格中的"被替换词"列（旧词/简单词）：
   如果截图同时有升级表达，en 填升级表达，zh 填中文释义+括号标注原词。
   不要为旧词单独建 pair（除非截图专门在训练旧词的英文拼写）。

6. 图片中如果有手写或 OCR 导致的模糊/错误：
   用你的理解修正为正确的英文，不要保留明显笔误。

7. 英文必须从截图提取，保留原始大小写和标点。
   不要遗漏任何可用的英文表达对。
   只返回纯JSON，不要加任何其他文字。`;



// P2: 历史面板

// 水波训练
const waveAnalyzingIdx = ref(-1)
const waveSegments = ref([])
const waveSelectedIdx = ref(-1)
const waveAnswer = ref(null)
const waveSummaryData = ref(null)
const waveLoading = ref(false)
const waveCache = ref({})
const revealedSegs = ref({})

function waveCacheKey(essayId, segIdx) { return essayId + '-' + segIdx }

function loadWaveCache() {
  if (Object.keys(waveCache.value).length === 0) {
    try {
      const raw = localStorage.getItem('ett_backup')
      if (raw) {
        const backup = JSON.parse(raw)
        if (backup.waveCache) waveCache.value = backup.waveCache
      }
    } catch {}
  }
}
function saveWaveCache() {
  syncData()
}

const hasWaveCache = (essayId, segIdx) => {
  const key = waveCacheKey(essayId, segIdx)
  return !!waveCache.value[key]?.segments
}

// ========== 通用JSON提取 ==========
function extractJSON(text) {
  try { return JSON.parse(text) } catch {}
  // Strip markdown fences + surrounding whitespace
  let cleaned = text.replace(/```json\s*/gi, '').replace(/```\s*/g, '').trim()
  // Brace-depth matching (handles nested {})
  const first = cleaned.indexOf('{')
  if (first === -1) return null
  let depth = 0
  for (let i = first; i < cleaned.length; i++) {
    if (cleaned[i] === '{') depth++
    else if (cleaned[i] === '}') { depth--; if (depth === 0) { cleaned = cleaned.slice(first, i + 1); break } }
  }
  try { return JSON.parse(cleaned) } catch {}
  return null
}

// ========== 数据结构兼容 ==========
// 将旧格式单对象 mistakeWave 和新格式数组 mistakeWaves 统一为数组
function normalizeMistakeWaves(record) {
  if (!record) return []
  // 新格式优先
  if (record.mistakeWaves && Array.isArray(record.mistakeWaves)) return record.mistakeWaves
  // 旧格式归一化
  if (record.mistakeWave && record.mistakeWave.patternEN && record.mistakeWave.patternEN !== '无') {
    return [{ sentenceIndex: null, errorType: '结构性错误', studentError: '', ...record.mistakeWave }]
  }
  return []
}

// ========== 水波训练 ==========
async function startWaveAnalysis(segIdx) {
  if (!currentEssay.value || !apiKey.value) { ElMessage.warning('请先选择范文并填写API Key'); return }
  const essay = currentEssay.value
  const seg = essay.segments[segIdx]
  if (!seg) return

  waveSelectedIdx.value = segIdx
  waveAnalyzingIdx.value = segIdx
  waveAnswer.value = null
  waveLoading.value = true

  const key = waveCacheKey(essay.id, segIdx)
  if (waveCache.value[key]?.answer) {
    waveAnswer.value = waveCache.value[key].answer
    waveAnalyzingIdx.value = -1
    waveLoading.value = false
    scrollToWaveAnswer()
    return
  }

  const prompt = promptConfig.value.wavePrompt || WAVE_SYSTEM_PROMPT
  const fullPrompt = `${prompt}

【英文原文段落】
${essay.originalEN}

【目标分句】
${seg.en}

【分句考点】
${seg.keyPoints.join('、')}

【教辅解析参考】
${seg.rawTeachingNote || '（无）'}`

  try {
    const result = await callDeepSeek(fullPrompt, 0.5)
    if (result) {
      const parsed = extractJSON(result)
      waveAnswer.value = parsed || { raw: result }
      waveCache.value[key] = { answer: waveAnswer.value, timestamp: Date.now() }
      saveWaveCache()
      scrollToWaveAnswer()
    }
  } catch (e) { ElMessage.error('水波分析失败: ' + e.message) }
  waveAnalyzingIdx.value = -1
  waveLoading.value = false
}

function selectWaveSegment(idx) {
  if (waveAnalyzingIdx.value >= 0) return
  waveSelectedIdx.value = idx
  startWaveAnalysis(idx)
}

function resetWave() {
  waveSelectedIdx.value = -1
  waveAnswer.value = null
  waveAnalyzingIdx.value = -1
  waveSummaryData.value = null
  waveSegments.value = []
  revealedSegs.value = {}
}

function scrollToWaveAnswer() {
  nextTick(() => {
    const el = document.querySelector('.wave-answer-card')
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}

async function requestWaveSummary() {
  if (!currentEssay.value || !apiKey.value) return
  const essay = currentEssay.value
  const keys = essay.segments.map((_, i) => waveCacheKey(essay.id, i))
  const answers = keys.map(k => waveCache.value[k]?.answer).filter(Boolean)
  if (answers.length < 2) { ElMessage.warning('至少需要2个分句的水波分析结果'); return }

  waveLoading.value = true
  const prompt = `你是一位考研英语一教学专家。以下是学生今天完成的长难句水波训练的汇总数据。请生成一份"今日水波总结"，格式为JSON：

{
  "overview": "一句话总结今天的训练主题和薄弱环节",
  "patterns": ["发现的共性问题1", "共性问题2"],
  "strengths": ["学生做得好的方面1"],
  "focusAreas": ["建议后续重点练习的方向1", "方向2"],
  "encouragement": "一句鼓励的话"
}

各分句水波分析记录：
${answers.map((a, i) => `【第${i+1}句】${JSON.stringify(a)}`).join('\n\n')}`

  try {
    const result = await callDeepSeek(prompt, 0.5)
    if (result) {
      const parsed = extractJSON(result)
      waveSummaryData.value = parsed || { overview: result }
    }
  } catch {}
  waveLoading.value = false
}

// API token 花费追踪
const tokenUsage = ref({ prompt: 0, completion: 0, total: 0, calls: 0 })
function resetTokenUsage() {
  tokenUsage.value = { prompt: 0, completion: 0, total: 0, calls: 0 }
  syncData()
}

// 翻译草稿 + 独立计时
const translationDrafts = reactive({})
const timerStates = reactive({})

// 范文排序
const essayOrder = ref([])
let _dataSyncTimer = null

function touchEssay(id) {
  const e = essays.value.find(x => x.id === id)
  if (e) {
    const now = new Date()
    e.lastActivity = now.getFullYear() + '-' + String(now.getMonth()+1).padStart(2,'0') + '-' + String(now.getDate()).padStart(2,'0') + ' ' + String(now.getHours()).padStart(2,'0') + ':' + String(now.getMinutes()).padStart(2,'0')
    sortEssays()
  }
}

function deleteEssay(id) {
  essays.value = essays.value.filter(e => e.id !== id)
  records.value = records.value.filter(r => r.essayId !== id)
  if (currentEssayId.value === id) currentEssayId.value = essays.value[0]?.id || null
  ElMessage.success('已删除')
}

// ========== 工具函数（补充） ==========
const ANNO_STORAGE_KEY = 'ett_annotations'

function sortEssays() {
  const orderMap = {}
  essayOrder.value.forEach((id, i) => { orderMap[id] = i })
  const hasOrder = essayOrder.value.length > 0
  essays.value.sort((a, b) => {
    if (hasOrder && orderMap[a.id] !== undefined && orderMap[b.id] !== undefined) {
      return orderMap[a.id] - orderMap[b.id]
    }
    const la = a.lastActivity || a.date || ''
    const lb = b.lastActivity || b.date || ''
    return lb.localeCompare(la)
  })
}

function loadPromptConfig() {
  // 提示词全部来自代码常量，无需持久化
  promptConfig.value.scoringPrompt = SCORING_SYSTEM_PROMPT
  promptConfig.value.segmentPrompt = SEGMENT_PROMPT
  promptConfig.value.imageStrictPrompt = IMAGE_IMPORT_DEFAULT_PROMPT
  promptConfig.value.imageRefPrompt = IMAGE_IMPORT_PROMPT_REFERENCE
  promptConfig.value.imagePhrasePrompt = IMAGE_IMPORT_PROMPT_PHRASE
  promptConfig.value.wavePrompt = WAVE_SYSTEM_PROMPT
  loadCustomPrompts()
}

function savePromptConfig() {
  saveCustomPrompts()
  showPromptConfig.value = false
  ElMessage.success('提示词已保存（会话内有效，代码常量优先）')
}

function loadCustomPrompts() {
  // 已在 loadData 中从 ett_backup 加载，此处仅作旧数据迁移
  if (customPrompts.value.length > 0) return
  try {
    const raw = localStorage.getItem('ett_custom_prompts')
    if (raw) {
      customPrompts.value = JSON.parse(raw)
      localStorage.removeItem('ett_custom_prompts') // 迁移后清理旧key
    }
  } catch {}
}
function saveCustomPrompts() {
  syncData()
}
function addCustomPrompt() {
  customPrompts.value.push({ id: Date.now().toString(36), name: '新提示词', content: '' })
  saveCustomPrompts()
}
function deleteCustomPrompt(id) {
  customPrompts.value = customPrompts.value.filter(p => p.id !== id)
  saveCustomPrompts()
}

const showHistoryPanel = ref(false)
const historyEssayId = ref(null)

const newEssay = reactive({
  title: '', source: '', date: '', originalEN: '', referenceTranslation: ''
})

const trendChartRef = ref(null)
const radarChartRef = ref(null)

// ========== 30篇考研英语一真题范文(内置题库) ==========
const BUILTIN_ESSAYS = [];

// 从 public/essays-data.json 加载真题数据
async function loadBuiltinEssays() {
  try {
    const res = await fetch('/essays-data.json');
    if (res.ok) BUILTIN_ESSAYS.length = 0, BUILTIN_ESSAYS.push(...await res.json());
  } catch(e) { /* fallback to empty */ }
}
loadBuiltinEssays();



// ========== B站知识点视频链接库 (P2) ==========
const BILIBILI_LINKS = [
  { keyword: '定语从句', bvid: 'BV1aY411b7nW', title: '定语从句精讲', desc: '关系代词that/which/who区别与省略规则' },
  { keyword: '被动语态', bvid: 'BV1s4411C7Wx', title: '被动语态全解析', desc: '英汉被动转换策略' },
  { keyword: '倒装', bvid: 'BV1Hb411p7JJ', title: '倒装句完全掌握', desc: '部分倒装与完全倒装的识别与翻译' },
  { keyword: '虚拟语气', bvid: 'BV1Zt4y1m7uN', title: '虚拟语气三大句型', desc: 'if条件句、wish、as if的翻译处理' },
  { keyword: '分词结构', bvid: 'BV1aL4y1F7Xr', title: '分词作状语/定语', desc: '现在分词与过去分词的翻译技巧' },
  { keyword: '同位语', bvid: 'BV1tG4y1U7QM', title: '同位语从句', desc: 'that引导同位语从句vs定语从句的区分' },
  { keyword: '名词性从句', bvid: 'BV1yW4y1D7GN', title: '名词性从句体系', desc: '主语/宾语/表语/同位语从句的翻译' },
  { keyword: '状语从句', bvid: 'BV1uT4y1S7Fq', title: '状语从句翻译策略', desc: '时间/原因/让步/条件状语从句' },
  { keyword: '强调句', bvid: 'BV1NK4y1U7eJ', title: '强调句型识别', desc: 'It is...that...结构的翻译方法' },
  { keyword: '比较结构', bvid: 'BV18V411b7LH', title: '比较级与最高级', desc: 'as...as, more than, the more...等结构' },
  { keyword: '长难句拆分', bvid: 'BV1gF411i7Hj', title: '长难句拆分五步法', desc: '找主干→定语→状语→并列→嵌套的处理顺序' },
  { keyword: '代词指代', bvid: 'BV1xB4y1T7Dc', title: '代词指代还原', desc: 'it/they/this/that指代判断，英译汉代词还原技巧' },
  { keyword: '固定搭配', bvid: 'BV1KM4y1U7mX', title: '考研英语常见搭配', desc: '动词+介词、形容词+介词等高频搭配' },
  { keyword: '插入语', bvid: 'BV1iL41177KN', title: '插入语的处理', desc: '双破折号/逗号分隔的插入成分翻译' },
  { keyword: '否定结构', bvid: 'BV1fY4y1C7vT', title: '否定表达辨析', desc: '部分否定/全部否定/双重否定的翻译' },
  { keyword: '省略句', bvid: 'BV19P4y1U7Xd', title: '省略句还原技巧', desc: '并列结构省略、状语从句省略的识别' },
  { keyword: 'with结构', bvid: 'BV1hN4y1T7yF', title: 'with复合结构', desc: 'with+名词+分词/形容词/介词短语的翻译' },
  { keyword: 'as用法', bvid: 'BV1KW4y1H7Qk', title: 'as的多重用法', desc: 'as作介词/连词/关系代词的翻译区分' },
  { keyword: 'it句型', bvid: 'BV1SP411p7TG', title: 'it形式主语句型', desc: 'It is+adj+that/to do结构的翻译' },
  { keyword: '独立主格', bvid: 'BV1Dd4y1m7LB', title: '独立主格结构', desc: '名词+分词/形容词的独立结构翻译' },
]

function matchBilibiliLinks(feedback) {
  if (!feedback) return []
  const matched = []
  const seen = new Set()
  for (const item of BILIBILI_LINKS) {
    if (feedback.includes(item.keyword) && !seen.has(item.bvid)) {
      matched.push(item)
      seen.add(item.bvid)
    }
    if (matched.length >= 3) break
  }
  return matched
}

// ========== 计算属性 ==========
const currentEssay = computed(() => essays.value.find(e => e.id === currentEssayId.value))
const scoredRecord = computed(() => currentEssayId.value ? getRecord(currentEssayId.value) : null)
const rightPanelRecord = computed(() => {
  if (scoringMode.value === 'reverse') return reverseScoredRecord.value
  return scoredRecord.value
})

const streakDays = computed(() => {
  let streak = 0
  const today = new Date()
  for (let d = new Date(today); ; d.setDate(d.getDate() - 1)) {
    const ds = d.toISOString().slice(0, 10)
    if (hasRecord(ds)) streak++
    else break
  }
  return streak
})
const avgScore = computed(() => {
  const done = records.value.filter(r => r.completed)
  if (!done.length) return 0
  return Math.round(done.reduce((s, r) => s + r.totalScore, 0) / done.length)
})
const totalTime = computed(() => {
  const sec = records.value.reduce((s, r) => s + (r.timeSpent || 0), 0)
  const h = Math.floor(sec / 3600), m = Math.floor(sec % 3600 / 60)
  return h > 0 ? `${h}h ${m}m` : `${m}m`
})

const diffResult = computed(() => {
  if (!scoredRecord.value || !currentEssay.value) return { userLines: [], refLines: [] }
  const userSentences = splitSentences(scoredRecord.value.userTranslation)
  const refSentences = splitSentences(currentEssay.value.referenceTranslation)
  return smartAlign(userSentences, refSentences, scoredRecord.value.errorSpans)
})

const renderedFeedback = computed(() => {
  const rec = rightPanelRecord.value
  if (!rec?.feedback) return ''
  return rec.feedback.replace(/\n/g, '<br>').replace(/\*\*(.*?)\*\*/g, '<b>$1</b>')
})

const bilibiliMatches = computed(() => matchBilibiliLinks(rightPanelRecord.value?.feedback || ''))

const historyRecords = computed(() => {
  if (!historyEssayId.value) return []
  return records.value.filter(r => r.essayId === historyEssayId.value && r.completed)
    .sort((a, b) => b.date.localeCompare(a.date))
})

const historyEssay = computed(() => essays.value.find(e => e.id === historyEssayId.value))

const todayTime = computed(() => {
  const today = new Date().toISOString().slice(0, 10)
  const sec = records.value
    .filter(r => r.date === today)
    .reduce((s, r) => s + (r.timeSpent || 0), 0)
  const m = Math.floor(sec / 60)
  if (m < 60) return `${m}分钟`
  const h = Math.floor(m / 60)
  return `${h}小时${m % 60}分钟`
})

const reverseDisplayRef = computed(() => {
  if (currentEssay.value?.referenceTranslation) return currentEssay.value.referenceTranslation
  return reverseChineseRef.value || ''
})

const reverseDiffResult = computed(() => {
  if (!reverseScoredRecord.value || !currentEssay.value) return { userLines: [], refLines: [] }
  const userSentences = splitSentences(reverseScoredRecord.value.userTranslation)
  const refSentences = splitSentences(currentEssay.value.originalEN)
  return smartAlign(userSentences, refSentences, reverseScoredRecord.value.errorSpans)
})

const showVocabPool = ref(false)

// 生词池全量查看
const showVocabPoolDialog = ref(false)
const wordInVocab = computed(() => {
  const w = (wordAnalysis.value?.word || selectedWord.value).toLowerCase().trim()
  if (!w) return false
  if (manualVocab.value.some(v => v.item?.toLowerCase().trim() === w)) return true
  return vocabPool.value.some(v => v.item?.toLowerCase().trim() === w)
})

const vocabSearchQuery = ref('')
const vocabLevelFilter = ref('')

const filteredVocabPool = computed(() => {
  let items = vocabPool.value
  const query = vocabSearchQuery.value.toLowerCase().trim()
  if (query) {
    items = items.filter(v =>
      v.item.toLowerCase().includes(query) ||
      (v.meaning && v.meaning.toLowerCase().includes(query))
    )
  }
  if (vocabLevelFilter.value) {
    items = items.filter(v => v.level === vocabLevelFilter.value)
  }
  return items
})

const vocabPool = computed(() => {
  const pool = {}
  records.value.filter(r => r.completed && r.unknownItems?.length).forEach(r => {
    r.unknownItems.forEach(item => {
      const key = item.item?.toLowerCase().trim()
      if (!key) return
      if (!pool[key]) {
        pool[key] = { ...item, count: 0, dates: new Set() }
      }
      pool[key].count++
      pool[key].dates.add(r.date)
    })
  })
  // Merge manual vocab entries
  manualVocab.value.forEach(item => {
    const key = item.item?.toLowerCase().trim()
    if (!key) return
    if (!pool[key]) {
      pool[key] = { ...item, count: 1, dates: new Set([item.date]) }
    }
  })
  return Object.values(pool)
    .map(v => ({ ...v, dateCount: v.dates.size }))
    .sort((a, b) => b.count - a.count)
})

function togglePractice() {
  if (practiceStarted.value) {
    stopTimer()
    if (currentEssay.value) {
      timerStates[currentEssay.value.id] = elapsed.value
    }
    practiceStarted.value = false
  } else {
    startPractice()
  }
}

// ========== 工具函数 ==========
function generateId() { return 'ett_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8) }
function getRecord(essayId) { const ms = records.value.filter(r => r.essayId === essayId); return ms.length ? ms.sort((a,b) => b.date > a.date ? 1 : -1)[0] : null }
function hasRecord(dateStr) { return records.value.some(r => r.date === dateStr && r.completed) }
function isToday(d) { return new Date().toDateString() === d.toDateString() }

function splitSentences(text) {
  const norm = (text || '').replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  // 先按中英文句末标点切分
  let parts = norm.split(/(?<=[。！？；.!?;])/)
  let out = []
  for (const p of parts) {
    const trimmed = p.trim()
    if (trimmed) out.push(trimmed)
  }
  if (!out.length) return [norm.trim()].filter(Boolean)
  // 检测标点密度：如果几乎没打句末标点（平均>80字才一个），
  // 说明用户靠换行分隔句子——把换行当作句子边界
  const punctCount = (norm.match(/[。！？.!?]/g) || []).length
  if (punctCount === 0 || norm.length / punctCount > 80) {
    const out2 = []
    for (const s of out) {
      if (s.includes('\n')) {
        // 先按连续换行（段落）拆，再按单换行拆
        const paras = s.split(/\n{2,}/).filter(Boolean)
        for (const para of paras) {
          const lines = para.split(/\n/).filter(Boolean)
          for (const line of lines) {
            const t = line.trim()
            if (t) out2.push(t)
          }
        }
      } else {
        out2.push(s)
      }
    }
    out = out2
  }
  // 标点充足 → 忽略换行（用户习惯好，换行只是视觉换行）
  return out
}

/** 基于LCS最长公共子序列的贪心对齐：参考句找最相似的用户句（中文远优于字符集重叠率） */
function smartAlign(userSentences, refSentences, errorSpans) {
  if (!refSentences.length) {
    return {
      userLines: userSentences.map(u => ({ text: u, html: highlightErrors(u, errorSpans), type: 'diff' })),
      refLines: userSentences.map(() => ({ text: '(缺)', type: 'missing' }))
    }
  }
  if (!userSentences.length) {
    return {
      userLines: refSentences.map(() => ({ text: '(缺)', type: 'missing' })),
      refLines: refSentences.map(r => ({ text: r, type: 'diff' }))
    }
  }
  // LCS 长度（滚动数组节省内存）
  function lcsLen(a, b) {
    const m = a.length, n = b.length
    const dp = new Array(n + 1).fill(0)
    for (let i = 1; i <= m; i++) {
      let prev = 0
      for (let j = 1; j <= n; j++) {
        const temp = dp[j]
        dp[j] = a[i - 1] === b[j - 1] ? prev + 1 : Math.max(dp[j], dp[j - 1])
        prev = temp
      }
    }
    return dp[n]
  }
  // Dice 系数 = 2*LCS / (lenA + lenB)，捕捉字符顺序而非仅字符集合
  const sim = (a, b) => {
    if (!a || !b) return 0
    const sa = a.replace(/\s/g, ''), sb = b.replace(/\s/g, '')
    if (!sa || !sb) return 0
    const lcs = lcsLen(sa, sb)
    return (2 * lcs) / (sa.length + sb.length)
  }
  const usedUser = new Set()
  const pairs = []
  for (const ref of refSentences) {
    let bestIdx = -1, bestScore = 0
    for (let i = 0; i < userSentences.length; i++) {
      if (usedUser.has(i)) continue
      const score = sim(userSentences[i], ref)
      if (score > bestScore) { bestScore = score; bestIdx = i }
    }
    if (bestIdx >= 0 && bestScore > 0.15) {
      usedUser.add(bestIdx)
      pairs.push({ u: userSentences[bestIdx], r: ref })
    } else {
      pairs.push({ u: null, r: ref })
    }
  }
  // 未匹配的用户句追加到末尾
  for (let i = 0; i < userSentences.length; i++) {
    if (!usedUser.has(i)) pairs.push({ u: userSentences[i], r: null })
  }
  // 生成结果行
  const userLines = [], refLines = []
  for (const p of pairs) {
    const u = p.u, r = p.r
    if (u && r && u === r) {
      userLines.push({ text: u, type: 'match' })
      refLines.push({ text: r, type: 'match' })
    } else {
      userLines.push({
        text: u || '(缺)',
        html: u ? highlightErrors(u, errorSpans) : '',
        type: u ? (r ? 'diff' : 'extra') : 'missing'
      })
      refLines.push({
        text: r || '(缺)',
        type: r ? (u ? 'diff' : 'extra') : 'missing'
      })
    }
  }
  return { userLines, refLines }
}

function highlightErrors(text, errorSpans) {
  if (!text || !errorSpans?.length) return text
  let result = text
  const sorted = [...errorSpans].sort((a, b) => b.length - a.length)
  const used = new Set()
  for (const span of sorted) {
    if (used.has(span)) continue
    used.add(span)
    const escaped = span.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    result = result.replace(new RegExp(escaped, 'g'), m => `<mark>${m}</mark>`)
  }
  return result
}

function formatTime(sec) {
  const m = Math.floor(sec / 60), s = sec % 60
  return `${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`
}

function scoreColor(s) { if (s >= 80) return '#22C55E'; if (s >= 60) return '#F59E0B'; return '#EF4444' }
function scoreTag(s) { if (s >= 80) return 'success'; if (s >= 60) return 'warning'; return 'danger' }
function dimColor(v, max) { const p = v / max; if (p >= 0.8) return '#22C55E'; if (p >= 0.6) return '#F59E0B'; return '#EF4444' }
function scoreDotColor(dateStr) {
  const r = records.value.find(r => r.date === dateStr && r.completed)
  if (!r) return '#ccc'
  return scoreColor(r.totalScore)
}

// ========== 持久化 (localStorage + 自动备份) ==========
function syncData() {
  clearTimeout(_dataSyncTimer)
  _dataSyncTimer = setTimeout(() => {
    try {
      const backup = {
        essays: essays.value,
        records: records.value,
        essayOrder: essayOrder.value,
        annotations: essayAnnotations.value,
        tokenUsage: tokenUsage.value,
        customPrompts: customPrompts.value,
        waveCache: waveCache.value,
        manualVocab: manualVocab.value,
        wordRoots: wordRootsStore,
        phraseCards: phraseCards.value,
        translationDrafts: Object.assign({}, translationDrafts),
        timerStates: Object.assign({}, timerStates),
        exportVersion: 6
      }
      localStorage.setItem('ett_backup', JSON.stringify(backup))
    } catch {}
  }, 800)
}
const FILE_BACKUP = 'ett_data.json'

async function fileSaveBackup(data) {
  try {
    await Filesystem.writeFile({
      path: FILE_BACKUP,
      data: JSON.stringify(data),
      directory: Directory.Data,
      recursive: true
    })
  } catch {} // Silently fail if Capacitor bridge not available (browser)
}

async function fileRestoreBackup() {
  try {
    const result = await Filesystem.readFile({
      path: FILE_BACKUP,
      directory: Directory.Data
    })
    return JSON.parse(result.data)
  } catch { return null } // File doesn't exist or bridge not available
}

async function flushSave() {
  clearTimeout(_dataSyncTimer)
  const backup = {
    essays: essays.value,
    records: records.value,
    essayOrder: essayOrder.value,
    annotations: essayAnnotations.value,
    tokenUsage: tokenUsage.value,
    customPrompts: customPrompts.value,
    waveCache: waveCache.value,
    manualVocab: manualVocab.value,
    wordRoots: wordRootsStore,
    phraseCards: phraseCards.value,
    translationDrafts: Object.assign({}, translationDrafts),
    timerStates: Object.assign({}, timerStates),
    exportVersion: 6
  }
  try { localStorage.setItem('ett_backup', JSON.stringify(backup)) } catch {}
  fileSaveBackup(backup) // Capacitor Filesystem backup (no await — fire and forget)
}
function saveEssayOrder() { syncData() }
function saveData() { syncData() }

function syncFromServer() {
  ElMessage.info('移动版使用本地存储，所有数据保存在手机中。导入导出功能可用于数据迁移。')
}

async function loadData() {
  // 1. Try Capacitor Filesystem first (app private storage, survives cache clear)
  const fileData = await fileRestoreBackup()
  if (fileData) {
    restoreBackupData(fileData)
  } else {
    // 2. Fall back to localStorage
    try {
      const raw = localStorage.getItem('ett_backup')
      if (raw) restoreBackupData(JSON.parse(raw))
    } catch {}
  }
  if (essays.value.length === 0) {
    essays.value = BUILTIN_ESSAYS.map(e => ({ ...e, id: generateId() }))
    syncData()
  }
  sortEssays()
}

function restoreBackupData(data) {
  essays.value = data.essays || []
  records.value = data.records || []
  essayOrder.value = data.essayOrder || []
  essayAnnotations.value = data.annotations || {}
  tokenUsage.value = data.tokenUsage || { prompt: 0, completion: 0, total: 0, calls: 0 }
  if (data.customPrompts) customPrompts.value = data.customPrompts
  if (data.waveCache) waveCache.value = data.waveCache
  if (data.manualVocab) manualVocab.value = data.manualVocab
  if (data.wordRoots) { Object.assign(wordRootsStore, data.wordRoots); Object.assign(wordAnalysisCache, data.wordRoots) }
  if (data.phraseCards) phraseCards.value = data.phraseCards
  if (data.translationDrafts) Object.assign(translationDrafts, data.translationDrafts)
  if (data.timerStates) Object.assign(timerStates, data.timerStates)
}

// ========== AI评分 ==========
async function callDeepSeek(prompt, temperature = 0.3, systemPrompt = null) {
  if (!apiKey.value) { ElMessage.warning('请先填写 DeepSeek API Key'); return null }
  try {
    const sysPrompt = systemPrompt || promptConfig.value.scoringPrompt || SCORING_SYSTEM_PROMPT
    const res = await fetch('https://api.deepseek.com/v1/chat/completions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey.value}` },
      body: JSON.stringify({ model: 'deepseek-chat', messages: [{ role: 'system', content: sysPrompt }, { role: 'user', content: prompt }], temperature, max_tokens: 2048 }),
    })
    const data = await res.json()
    if (!data.choices?.length) throw new Error(data.error?.message || 'API返回异常')
    if (data.usage) {
      tokenUsage.value.prompt += data.usage.prompt_tokens || 0
      tokenUsage.value.completion += data.usage.completion_tokens || 0
      tokenUsage.value.total += data.usage.total_tokens || 0
      tokenUsage.value.calls++
      syncData()
    }
    return data.choices[0].message.content
  } catch (e) { ElMessage.error('AI调用失败: ' + e.message); return null }
}

const SCORING_SYSTEM_PROMPT = `你是考研英语一翻译题的资深评分老师。你需要对学生提交的中文译文进行四维评分，满分100分（每个维度25分）。

【评分维度】
各维度分数必须为整数（0-25），不要返回小数。
1. 准确性(0-25)：原文意思是否准确传达，无漏译、误译、增译。特别注意原文的限定词、否定范围、比较结构是否完整还原。
2. 语法结构(0-25)：长难句和特殊语法结构（定语从句、被动语态、倒装、虚拟语气、分隔结构、省略等）是否被正确识别并转化为通顺中文。
3. 词汇表达(0-25)：用词是否准确地道，搭配是否自然，术语翻译是否恰当。注意一词多义在语境中的选择是否合理。
4. 流畅度(0-25)：中文表达是否通顺，语序是否符合汉语习惯，句间衔接是否自然。避免欧化长句、生硬直译。

【教辅参考资料的使用规则】
如果提示词下方提供了"教辅详解存档"或"逐句教辅解析"内容：
- 这些来自专业考研教辅（真题解析、长难句讲解等），代表了专业老师的分析视角。
- 评分前，先浏览教辅中标注的考点和解析要点，对这篇文章的评分重点建立预期。
- 教辅中重点分析的长难句结构、易错词汇、翻译陷阱——这些是评分时应当特别留意的位置，学生译文在这些点上的表现直接影响对应维度的得分。
- 教辅的解析角度可作为评分校准参考（如教辅从"定语从句拆分"角度分析某句，你的语法结构评分应同步关注学生是否处理好了这个定语从句）。
- 教辅不等于标准答案。你对学生译文的整体判断拥有最终决定权。如果教辅的解析角度与学生实际错误不完全对应，以你的专业判断为准。
- 教辅中的词汇辨析、例句对比等内容，可酌情融入"mistakeWaves"和"unknownItems"的回答中，但要标注来源（如"教辅指出……"）。
- 如果题目没有附带教辅资料，则跳过上述步骤，完全依靠你的专业能力独立评分。

【输出格式】
严格按照以下JSON格式返回，不要加markdown代码块，不要加任何其他文字：

{
  "accuracy": 20,
  "grammar": 18,
  "vocabulary": 19,
  "fluency": 21,
  "total": 78,
  "feedback": "逐句点评（按原文句子编号逐条分析，每句先说学生译文的处理情况，再指出问题或亮点）：\n1. 第一句……\n2. 第二句……",
  "mistakeWaves": [
    {
      "sentenceIndex": 0,
      "errorType": "错误类型中文标签（如：修饰位置错误、语序混乱、英文硬翻、搭配断裂、否定漏译、分隔结构未识别等）",
      "studentError": "学生译文中对应的错译片段（摘录原文）",
      "patternEN": "原文中导致学生翻错的英文句子片段",
      "whereStuck": "这个英文结构为什么容易卡住中国学生（用白话解释，禁止使用语法术语如'定语从句''后置定语'等，要说'这部分在补充说明xxx''这个词其实是修饰前面的xxx'）",
      "examples": [
        {"en": "与错误结构同类的英文例句1", "zh": "对应的中文翻译1"},
        {"en": "与错误结构同类的英文例句2", "zh": "对应的中文翻译2"}
      ],
      "nextTime": "下次看到类似英文结构时，大脑应该怎么做（给出可操作的拆解步骤，用白话，禁止术语）"
    }
  ],
  "translationErrors": [
    {
      "originalEN": "原文中翻错的单词/短语",
      "correctZH": "正确的中文翻译",
      "studentZH": "学生错误翻译成什么",
      "note": "一句话解释为什么容易错（如：固定搭配不能逐字翻、熟词僻义、构词法漏看否定前缀等）"
    }
  ],
  "unknownItems": [
    {
      "item": "原文中翻错或不认识的单词/短语",
      "meaning": "在该语境下的准确中文释义",
      "type": "word或phrase",
      "category": "归类标签（法律/经济/科技/抽象概念/固定搭配/近义易混/熟词僻义等）",
      "level": "四级/六级/考研/雅思/托福/超纲"
    }
  ],
  "errorSpans": ["学生译文中翻错的具体字词片段1", "翻错片段2"]
}

【字段说明】
- mistakeWaves：对feedback中逐句指出的每一个结构性错误（如语序混乱、修饰位置错、搭配断裂、否定漏译等），各生成一条水波分析。sentenceIndex从0开始，对应第几句。errorType用中文标签概括错误类型。studentError摘录学生错译原文。如果某句没有结构性错误则跳过该句。如果整篇没有结构性错误，返回空数组[]。
- translationErrors：逐条列出学生译文中的词汇/短语翻译错误。originalEN是原文单词/短语，correctZH是正确翻译，studentZH是学生错译成什么，note用一句话提示为什么容易错。词汇性错误与结构性错误分开——这里的错误是因为不认识单词、选错词义、望文生义，不是因为看不懂句子结构。如果全部翻译正确，返回空数组[]。
- unknownItems：同上，提取所有翻错或不认识的单词短语喂养生词池。每个词标注category和level。如果全部正确返回空数组[]。
- errorSpans：精确摘录学生译文中翻译有误的中文字词片段（不是整句），用于前端标红显示。每个片段尽量控制在2-6个字，定位到具体的错误词或短语。如果无法精确定位到片段，返回空数组[]。`;

const SEGMENT_PROMPT = `请将以下英文段落处理为考研英语一翻译练习题格式。返回严格JSON：
{
  "segments": [{"en":"原文分句1", "contextZH":"简短中文背景提示", "keyPoints":["考点1","考点2"]}]
}
每段segments的en为原文按句拆分。keyPoints标注每句涉及的语法考点（如定语从句、被动语态、倒装等）。

英文原文：`


const WAVE_SYSTEM_PROMPT = `你是一位考研英语一长难句教学专家，擅长用"水波法"（由核心向外层层扩展）分析英文长难句。学生点击句子后，你需要对该句进行水波式拆解分析。

请严格输出以下JSON格式，不要加markdown代码块：

{
  "grammarTree": "【句法主干】先提取句子核心主干（主语+谓语+宾语/表语），然后逐层向外添加修饰成分，用树形结构或分层缩进展示：\nLayer 0 (核心): ... \nLayer 1 (第一层修饰): ...\nLayer 2 (第二层修饰): ...",
  "logicSplit": "【逻辑切分】将长句按意群分成2-4段，每段用简短中文标注它的语法作用（如'让步状语''后置定语补充说明xx'等）",
  "stuckPoint": "【卡点诊断】指出这个句子最容易让中国学生卡住的一个结构点（用白话解释为什么容易卡，禁止使用语法术语）",
  "resolveTip": "【突破方法】针对上述卡点，给出简单明了的拆解/理解策略（如'先找到那个xxx，然后把它暂时遮住，读剩下的部分'）",
  "analogy": "【类比助记】用一个生活化/自然界的类比来帮助理解这个句子结构（如'这个句子像一颗洋葱/一个俄罗斯套娃...'）"
}

要求：
1. grammarTree必须分层展示，从核心到外层
2. logicSplit不要机械照抄教辅编号
3. stuckPoint和resolveTip用白话，禁止使用'定语从句''分词状语'等术语
4. analogy要生动有趣，帮助学生形成画面记忆`;

const REVERSE_SCORING_PROMPT = `你是考研英语翻译题的资深评分老师。这是反转训练模式：学生根据中文参考译文将内容翻译回英文。你需要对比学生的英文译文与英文原文，进行四维评分，满分100分（每个维度25分）。

【评分维度】
各维度分数必须为整数（0-25），不要返回小数。
1. 准确性(0-25)：学生的英文是否准确传达了中文参考的意思，且与英文原文的核心信息一致。重点检查：是否漏译关键信息、是否曲解原文逻辑关系（因果/转折/让步等）、代词指代是否正确。
2. 语法结构(0-25)：英文语法是否正确。重点检查：时态、语态、主谓一致、冠词用法、从句结构、非谓语动词使用是否规范。
3. 词汇表达(0-25)：用词是否精准地道。重点检查：选词是否符合英文表达习惯（而非中式英语）、搭配是否自然、同义词选择是否恰当、是否有词不达意的情况。
4. 流畅度(0-25)：英文是否自然流畅。重点检查：语序是否符合英语思维习惯、句子之间是否有合理的衔接、是否存在生硬直译的痕迹、整体读起来是否像地道英文。

【输出格式】
严格按照以下JSON格式返回，不要加markdown代码块，不要加任何其他文字：

{
  "accuracy": 20,
  "grammar": 18,
  "vocabulary": 19,
  "fluency": 21,
  "total": 78,
  "feedback": "逐句点评（按句子逐条分析学生的英译与原文的差异）：\\n1. 第一句……\\n2. 第二句……",
  "mistakeWaves": [
    {
      "sentenceIndex": 0,
      "errorType": "错误类型中文标签（如：时态错误、语序混乱、搭配错误、冠词遗漏、句式选择不当等）",
      "studentError": "学生英译中对应的出错片段",
      "patternEN": "学生英文译文中出错的英文片段",
      "whereStuck": "这个表达为什么容易出错，中文母语者在转英文时为什么会踩这个坑（用白话解释）",
      "examples": [
        {"en": "正确的英文表达例句1", "zh": "对应的中文1"},
        {"en": "正确的英文表达例句2", "zh": "对应的中文2"}
      ],
      "nextTime": "下次从中文转英文时，遇到类似表达应该怎么处理（给出可操作的步骤，用白话）"
    }
  ],
  "translationErrors": [
    {
      "originalEN": "学生写的错误英文表达",
      "correctEN": "正确的地道英文表达",
      "studentEN": "学生写的错误版本（摘录）",
      "note": "一句话解释为什么容易错（如：中式英语、搭配不当、词不达意等）"
    }
  ],
  "unknownItems": [
    {
      "item": "学生用错或不认识的英文单词/短语",
      "meaning": "正确的地道表达",
      "type": "word或phrase",
      "category": "归类标签（搭配错误/词不达意/语法错误/拼写错误/中式英语等）",
      "level": "四级/六级/考研/雅思/托福/超纲"
    }
  ],
  "errorSpans": ["学生英译中出错的具体英文片段1", "出错片段2"]
}

【字段说明】
- mistakeWaves：对feedback中逐句指出的每一个结构性/表达性错误，各生成一条水波分析。sentenceIndex从0开始。errorType用中文标签。如果整篇没有明显错误，返回空数组[]。
- translationErrors：逐条列出词汇/短语层面的翻译错误，给出正确vs错误的对照。词汇性错误与结构性错误分开。没有则返回空数组[]。
- unknownItems：同上，提取所有用错或不地道的单词短语。没有则返回空数组[]。
- errorSpans：精确摘录学生英译中有误的英文片段（不是整句），每个片段尽量控制在2-5个单词，用于前端标红显示。如果无法精确定位，返回空数组[]。`;

const WORD_ROOT_PROMPT = `你是英语词源学专家，精通词根词缀拆解。分析以下英文单词，返回严格的JSON格式（不要markdown代码块）：

{
  "word": "原词",
  "breakdown": [
    {"part": "词缀/词根", "type": "prefix/root/suffix", "meaning": "该部分的中文含义", "reliability": "common/uncommon/misleading"}
  ],
  "hasMisleadingRoot": false,
  "misleadingDetail": "",
  "wordMeaning": "单词的准确中文释义",
  "rootReasoning": "从词根词缀组合到最终词义的推理链条，用中文写，串联起来展示推导过程"
}

【reliability 判定规则】
- "common"：该词根/词缀非常常见、认知度高，学生可以放心依赖这个拆分（如 un-=不, pre-=前, -able=能的, dict-=说）
- "uncommon"：正确但不常见，学生可能不熟悉（如 anthrop-=人类, chrom-=颜色）
- "misleading"：有误导性！该部分看起来像某个常见词根但实际上不是，或者容易与其他词混淆（如 "car" 在 "carnivore" 中不是"汽车"而是"肉"的词根；"believe" 中的 "lieve" 不是 "leave" 的变化）

有任何一个 breakdown 项的 reliability 为 "misleading" 时，hasMisleadingRoot 必须为 true，并在 misleadingDetail 中用中文说明哪个部分为什么容易误导。如果没有误导性词根，misleadingDetail 为空字符串 ""。

【重要】
- 拆解要精准，不要过度拆解
- 每个 part 只标注一个含义
- wordMeaning 要简洁准确
- rootReasoning 要把推导过程串联起来`;

// ========== 词根词缀分析 ==========
function getWordAtClick(event) {
  const el = event.currentTarget
  const text = el.textContent
  const range = document.createRange()
  let offset = 0
  // Find click position in text
  for (const node of el.childNodes) {
    if (node.nodeType === Node.TEXT_NODE) {
      const nodeLen = node.textContent.length
      const rects = []
      // Create ranges for each character to find click position
      for (let i = 0; i < nodeLen; i++) {
        range.setStart(node, i)
        range.setEnd(node, i + 1)
        rects.push(range.getBoundingClientRect())
      }
      for (let i = 0; i < rects.length; i++) {
        const r = rects[i]
        if (r && event.clientX >= r.left && event.clientX <= r.right &&
            event.clientY >= r.top && event.clientY <= r.bottom) {
          const charIdx = offset + i
          // Expand to word boundaries
          let start = charIdx, end = charIdx
          while (start > 0 && /[a-zA-Z]/.test(text[start - 1])) start--
          while (end < text.length && /[a-zA-Z]/.test(text[end])) end++
          const word = text.slice(start, end)
          return word.length >= 2 ? word : null
        }
      }
      offset += nodeLen
    } else {
      offset += node.textContent.length
    }
  }
  return null
}

async function analyzeWordRoot(word) {
  if (wordAnalysisCache[word]) {
    wordAnalysis.value = wordAnalysisCache[word]
    wordAnalyzing.value = false
    return
  }
  if (!apiKey.value) { ElMessage.warning('请先填写API Key'); wordAnalyzing.value = false; return }

  try {
    const res = await fetch('https://api.deepseek.com/v1/chat/completions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey.value}` },
      body: JSON.stringify({
        model: 'deepseek-chat',
        messages: [
          { role: 'system', content: WORD_ROOT_PROMPT },
          { role: 'user', content: `请分析单词: ${word}` }
        ],
        temperature: 0.1,
        max_tokens: 1024
      }),
    })
    const data = await res.json()
    if (!data.choices?.length) throw new Error(data.error?.message || 'API返回异常')
    const rawContent = data.choices[0].message.content
    const result = extractJSON(rawContent)
    if (result) {
      wordAnalysis.value = result
      wordAnalysisCache[word] = result
      wordRootsStore[word] = result
    } else {
      wordAnalysis.value = { raw: rawContent, _parseFailed: true }
      wordAnalysisCache[word] = wordAnalysis.value
      wordRootsStore[word] = { raw: rawContent, _parseFailed: true }
    }
    _wordRootsDirty = true
    saveWordRoots()
    if (data.usage) {
      tokenUsage.value.prompt += data.usage.prompt_tokens || 0
      tokenUsage.value.completion += data.usage.completion_tokens || 0
      tokenUsage.value.total += data.usage.total_tokens || 0
      tokenUsage.value.calls++
      syncData()
    }
  } catch (e) { ElMessage.error('词根分析失败: ' + e.message) }
  wordAnalyzing.value = false
}

function onWordAnswerEnter() {
  if (userMeaningGuess.value.trim()) showWordAnswer.value = true
}

function startWordAnalysis() {
  const word = wordInput.value.trim()
  if (!word || word.length < 2) { ElMessage.warning('请输入至少2个字母的单词'); return }
  selectedWord.value = word
  wordAnalysis.value = null
  userMeaningGuess.value = ''
  showWordAnswer.value = false
  wordAnalyzing.value = true
  analyzeWordRoot(word)
}

function onWordClick(event) {
  const word = getWordAtClick(event)
  if (!word || word.length < 2) return
  selectedWord.value = word
  wordInput.value = word
  wordAnalysis.value = null
  userMeaningGuess.value = ''
  showWordAnswer.value = false
  showWordAnalysis.value = true
  wordAnalyzing.value = true
  analyzeWordRoot(word)
}

function addWordToVocab() {
  const word = wordAnalysis.value?.word || selectedWord.value
  const meaning = wordAnalysis.value?.wordMeaning || ''
  if (!word) return
  const key = word.toLowerCase().trim()
  if (manualVocab.value.some(v => v.item?.toLowerCase().trim() === key)) {
    ElMessage.info('该词已在生词池中')
    return
  }
  manualVocab.value.push({
    item: word,
    meaning: meaning,
    type: 'word',
    category: '手动添加',
    level: '考研',
    date: new Date().toISOString().slice(0, 10)
  })
  syncData()
  ElMessage.success(`「${word}」已加入生词池`)
  showWordAnalysis.value = false
}

// ========== 窗口AI模式 ==========
function buildScoringPrompt() {
  const essay = currentEssay.value
  const hasRawText = essay.rawTextArchive || essay.segments?.some(s => s.rawTeachingNote)
  const rawTextBlock = hasRawText ? `
【教辅详解存档（来自截图OCR，评分时请参考）】
${essay.rawTextArchive || ''}
${essay.segments?.filter(s => s.rawTeachingNote).length ? `【逐句教辅解析】
${essay.segments.filter(s => s.rawTeachingNote).map((s, i) => `第${i + 1}句：${s.rawTeachingNote}`).join('\\n')}` : ''}` : ''
  const imageSourceBlock = essay.imageSource ? `
【教辅资料来源】
此题目来自以下教辅资料的截图提取：
${essay.imageSource}
（以上资料信息仅供你了解题目出处，评分时请参考其中涉及的考点解析角度）` : ''
  return `${promptConfig.value.scoringPrompt}

【英文原文】
${essay.originalEN}

【参考译文】
${essay.referenceTranslation}

【学生译文】
${userTranslation.value}

【考点提示】
${essay.segments.map(s => s.keyPoints.join('、')).join(' | ')}${rawTextBlock}${imageSourceBlock}
`
	}

async function copyPromptToClipboard() {
  if (!userTranslation.value.trim()) { ElMessage.warning('请先输入译文'); return }
  const full = buildScoringPrompt()
  try {
    await navigator.clipboard.writeText(full)
    if (practiceStarted.value) {
      stopTimer()
      if (currentEssay.value) timerStates[currentEssay.value.id] = elapsed.value
    }
    ElMessage.success('提示词已复制到剪贴板（计时已暂停），粘贴到窗口AI中获取评分')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

function submitWindowAI() {
  if (!windowAIInput.value.trim()) { ElMessage.warning('请粘贴AI返回的JSON结果'); return }
  try {
    const parsed = extractJSON(windowAIInput.value)
    if (!parsed) throw new Error('未识别到JSON')
    saveScoreResult(parsed)
    windowAIInput.value = ''
    ElMessage.success(`评分完成：${parsed.total}/100`)
  } catch (e) {
    ElMessage.error('JSON解析失败：' + e.message)
  }
}

function saveScoreResult(parsed) {
  const essay = currentEssay.value
  const record = getRecord(essay.id)
  if (record) {
    record.userTranslation = userTranslation.value
    record.score = { accuracy: parsed.accuracy, grammar: parsed.grammar, vocabulary: parsed.vocabulary, fluency: parsed.fluency }
    record.totalScore = parsed.total
    record.feedback = parsed.feedback
    record.completed = true
    record.timeSpent = Math.max(record.timeSpent || 0, elapsed.value)
    record.date = essay.date
    if (parsed.mistakeWaves) record.mistakeWaves = parsed.mistakeWaves
    if (parsed.translationErrors) record.translationErrors = parsed.translationErrors
    if (parsed.unknownItems) record.unknownItems = parsed.unknownItems
    if (parsed.errorSpans) record.errorSpans = parsed.errorSpans
  } else {
    records.value.push({
      id: generateId(),
      essayId: essay.id,
      date: essay.date,
      userTranslation: userTranslation.value,
      score: { accuracy: parsed.accuracy, grammar: parsed.grammar, vocabulary: parsed.vocabulary, fluency: parsed.fluency },
      totalScore: parsed.total,
      feedback: parsed.feedback,
      timeSpent: elapsed.value,
      completed: true,
      ...(parsed.mistakeWaves ? { mistakeWaves: parsed.mistakeWaves } : {}),
      ...(parsed.translationErrors ? { translationErrors: parsed.translationErrors } : {}),
      ...(parsed.unknownItems ? { unknownItems: parsed.unknownItems } : {}),
      ...(parsed.errorSpans ? { errorSpans: parsed.errorSpans } : {})
    })
  }
  stopTimer()
  practiceStarted.value = false
  delete timerStates[essay.id]
  touchEssay(essay.id)
  nextTick(() => { renderTrendChart(); renderRadarChart() })
}

// ========== 历史面板 ==========
function openHistoryPanel(essayId) {
  historyEssayId.value = essayId
  showHistoryPanel.value = true
}

async function submitTranslation() {
  if (!userTranslation.value.trim()) return
  if (scoringMode.value === 'window') { copyPromptToClipboard(); return }
  if (!apiKey.value) { ElMessage.warning('请先填写 DeepSeek API Key'); return }

  scoring.value = true
  const prompt = buildScoringPrompt()

  try {
    const result = await callDeepSeek(prompt, 0.3)
    if (!result) { scoring.value = false; return }

    const parsed = extractJSON(result)
    if (!parsed) throw new Error('未识别到评分JSON')
    saveScoreResult(parsed)
  } catch (e) {
    ElMessage.error('评分解析失败：' + e.message)
  }
  scoring.value = false
}

// ========== 反转训练 ==========
function buildReverseScoringPrompt() {
  const essay = currentEssay.value
  const refText = reverseDisplayRef.value
  return `${REVERSE_SCORING_PROMPT}

【英文原文（评分标准）】
${essay.originalEN}

【中文参考译文】
${refText}

【学生英译（中→英）】
${reverseUserTranslation.value}
`
}

async function generateReverseChineseRef() {
  if (!currentEssay.value || !apiKey.value) { ElMessage.warning('请先填写API Key'); return }
  reverseGenerating.value = true
  try {
    const prompt = `请将以下英文翻译成中文，要求：忠实原文、行文流畅、符合中文学术表达习惯，避免生硬机翻腔。

${currentEssay.value.originalEN}`
    const result = await callDeepSeek(prompt, 0.3, '你是一位专业的中英文翻译。请将用户提供的英文准确翻译成中文，只返回译文，不要加任何解释。')
    if (result) {
      reverseChineseRef.value = result.trim()
    }
  } catch (e) { ElMessage.error('生成中文参考失败: ' + e.message) }
  reverseGenerating.value = false
}

async function copyReversePrompt() {
  if (!reverseUserTranslation.value.trim()) { ElMessage.warning('请先输入英文译文'); return }
  const full = buildReverseScoringPrompt()
  try {
    await navigator.clipboard.writeText(full)
    if (practiceStarted.value) {
      stopTimer()
      if (currentEssay.value) timerStates[currentEssay.value.id] = elapsed.value
    }
    ElMessage.success('反转训练提示词已复制，粘贴到窗口AI中获取评分')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

function submitReverseWindowAI() {
  if (!reverseWindowAIInput.value.trim()) { ElMessage.warning('请粘贴AI返回的JSON结果'); return }
  try {
    const parsed = extractJSON(reverseWindowAIInput.value)
    if (!parsed) throw new Error('未识别到JSON')
    saveReverseScoreResult(parsed)
    reverseWindowAIInput.value = ''
    ElMessage.success(`反转评分完成：${parsed.total}/100`)
  } catch (e) {
    ElMessage.error('JSON解析失败：' + e.message)
  }
}

function saveReverseScoreResult(parsed) {
  reverseScoredRecord.value = {
    userTranslation: reverseUserTranslation.value,
    score: { accuracy: parsed.accuracy, grammar: parsed.grammar, vocabulary: parsed.vocabulary, fluency: parsed.fluency },
    totalScore: parsed.total,
    feedback: parsed.feedback,
    mistakeWaves: parsed.mistakeWaves,
    translationErrors: parsed.translationErrors,
    unknownItems: parsed.unknownItems,
    errorSpans: parsed.errorSpans,
    timeSpent: elapsed.value,
    date: currentEssay.value.date
  }
  // Also save to records for vocab pool / history
  const existingRecord = records.value.find(r => r.essayId === currentEssay.value.id && r.type === 'reverse')
  if (existingRecord) {
    Object.assign(existingRecord, reverseScoredRecord.value, { id: existingRecord.id, essayId: currentEssay.value.id, completed: true, type: 'reverse' })
  } else {
    records.value.push({
      id: generateId(),
      essayId: currentEssay.value.id,
      type: 'reverse',
      ...reverseScoredRecord.value,
      completed: true
    })
  }
  stopTimer()
  practiceStarted.value = false
  touchEssay(currentEssay.value.id)
  nextTick(() => { renderTrendChart(); renderRadarChart() })
}

async function submitReverseTranslation() {
  if (!reverseUserTranslation.value.trim()) return
  if (!apiKey.value) { ElMessage.warning('请先填写 DeepSeek API Key'); return }

  scoring.value = true
  const prompt = buildReverseScoringPrompt()

  try {
    const result = await callDeepSeek(prompt, 0.3, REVERSE_SCORING_PROMPT)
    if (!result) { scoring.value = false; return }

    const parsed = extractJSON(result)
    if (!parsed) throw new Error('未识别到评分JSON')
    saveReverseScoreResult(parsed)
  } catch (e) {
    ElMessage.error('反转评分解析失败：' + e.message)
  }
  scoring.value = false
}

// ========== 练习流程 ==========
function startPractice() {
  if (!currentEssay.value) return
  stopTimer()
  practiceStarted.value = true
  userTranslation.value = ''
  if (scoringMode.value === 'reverse') {
    reverseUserTranslation.value = ''
    reverseScoredRecord.value = null
  }
  elapsed.value = 0
  timerInterval = setInterval(() => { elapsed.value++ }, 1000)
}

function stopTimer() {
  if (timerInterval) { clearInterval(timerInterval); timerInterval = null }
}

function toggleHighlight() { /* 简单划词：点击词→查词典API，暂不实现 */ }

// ========== 范文管理 ==========
async function addEssay() {
  if (!newEssay.originalEN.trim() || !apiKey.value) {
    ElMessage.warning('请填写英文原文和API Key')
    return
  }
  aiProcessing.value = true
  try {
    const segmentPrompt = promptConfig.value.segmentPrompt || SEGMENT_PROMPT
    const prompt = segmentPrompt + newEssay.originalEN

    const result = await callDeepSeek(prompt, 0.3, segmentPrompt)
    if (!result) { aiProcessing.value = false; return }
    const parsed = extractJSON(result) || { segments: [{ en: newEssay.originalEN, contextZH: '', keyPoints: [] }] }

    essays.value.push({
      id: generateId(),
      date: newEssay.date || new Date().toISOString().slice(0, 10),
      title: newEssay.title || '未命名',
      source: newEssay.source || '自定义',
      originalEN: newEssay.originalEN,
      referenceTranslation: newEssay.referenceTranslation || '',
      segments: parsed.segments
    })
    ElMessage.success('范文添加成功')
    showAddDialog.value = false
    Object.assign(newEssay, { title: '', source: '', date: '', originalEN: '', referenceTranslation: '' })
  } catch (e) { ElMessage.error('AI处理失败：' + e.message) }
  aiProcessing.value = false
}


// ====== Drag & Drop reorder ======
let dragOverIdx = ref(-1)
function onEssayDragStart(e, essayId) {
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', essayId)
  e.currentTarget.classList.add('dragging')
}
function onEssayDragEnd(e) {
  e.currentTarget.classList.remove('dragging')
  dragOverIdx.value = -1
}
function onEssayDragOver(e, idx) {
  e.preventDefault()
  e.dataTransfer.dropEffect = 'move'
  dragOverIdx.value = idx
}
function onEssayDragLeave() {
  dragOverIdx.value = -1
}
function onEssayDrop(e, dropIdx) {
  e.preventDefault()
  dragOverIdx.value = -1
  const dragId = e.dataTransfer.getData('text/plain')
  if (!dragId || dragId === essays.value[dropIdx]?.id) return
  const arr = essayOrder.value.filter(id => id !== dragId)
  arr.splice(dropIdx, 0, dragId)
  essayOrder.value = arr
  saveEssayOrder()
  sortEssays()
}

// ========== 导入导出 ==========
function buildBackupJSON() {
  return {
    essays: essays.value,
    records: records.value,
    settings,
    annotations: essayAnnotations.value,
    promptConfig: promptConfig.value,
    customPrompts: customPrompts.value,
    tokenUsage: tokenUsage.value,
    waveCache: waveCache.value,
    essayOrder: essayOrder.value,
    manualVocab: manualVocab.value,
    wordRoots: wordRootsStore,
    phraseCards: phraseCards.value,
    exportVersion: 6
  }
}

async function exportData() {
  const json = JSON.stringify(buildBackupJSON(), null, 2)
  const fileName = `ett-backup-${new Date().toISOString().slice(0,10)}.json`

  if (isMobile.value) {
    // 手机端：写 Capacitor Filesystem + 分享
    try {
      const { Filesystem, Directory } = await import('@capacitor/filesystem')
      await Filesystem.writeFile({
        path: fileName,
        data: json,
        directory: Directory.Documents,
      })
      ElMessage.success(`已导出到 app 文档目录: ${fileName}`)
    } catch (e) {
      // fallback: Blob 下载
      const blob = new Blob([json], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a'); a.href = url; a.download = fileName; a.click()
      URL.revokeObjectURL(url)
      ElMessage.success('已导出（浏览器下载）')
    }
  } else {
    // 桌面端：Blob 下载
    const blob = new Blob([json], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = fileName; a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('完整备份已导出（含全部6组提示词、短语卡片、生词数据、批注、用量）')
  }
}

async function shareBackup() {
  const json = JSON.stringify(buildBackupJSON(), null, 2)
  const fileName = `ett-backup-${new Date().toISOString().slice(0,10)}.json`
  try {
    if (navigator.share && navigator.canShare) {
      const file = new File([json], fileName, { type: 'application/json' })
      const data = { files: [file], title: 'ETT 数据备份' }
      if (navigator.canShare(data)) {
        await navigator.share(data)
        return
      }
    }
  } catch (e) { /* 用户取消分享 */ }
  // fallback: 写文件 + 提示
  try {
    const { Filesystem, Directory } = await import('@capacitor/filesystem')
    await Filesystem.writeFile({ path: fileName, data: json, directory: Directory.Documents })
    ElMessage.success(`已保存到 app 文档目录: ${fileName}`)
  } catch (e) {
    ElMessage.error('分享和保存均失败，请检查存储权限')
  }
}

let _autoExported = false
function autoExportOnLoad() {
  if (_autoExported) return
  if (essays.value.length === 0 && records.value.length === 0) return
  _autoExported = true
  setTimeout(() => {
    try {
      const backup = {
        essays: essays.value,
        records: records.value,
        settings,
        annotations: essayAnnotations.value,
        promptConfig: promptConfig.value,
        customPrompts: customPrompts.value,
        tokenUsage: tokenUsage.value,
        waveCache: waveCache.value,
        essayOrder: essayOrder.value,
        manualVocab: manualVocab.value,
        phraseCards: phraseCards.value,
        exportVersion: 6
      }
    } catch {}
  }, 500)
}

function triggerImport() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = () => {
    const file = input.files?.[0]
    if (file) importData(file)
  }
  input.click()
}

function importData(file) {
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const data = JSON.parse(e.target.result)
      // 自动识别：如果顶层有 pairs 且元素含 en/zh → 当作单组短语卡片导入
      if (data.pairs && Array.isArray(data.pairs) && data.pairs.length && data.pairs[0].en !== undefined && data.pairs[0].zh !== undefined) {
        phraseCards.value.push({
          id: generateId(),
          title: data.title || 'JSON导入',
          source: data.source || '',
          date: data.date || new Date().toISOString().slice(0, 10),
          sourceNote: data.sourceNote || '',
          pairs: data.pairs.map(p => ({ en: p.en, zh: p.zh })),
          practiceState: {}
        })
        syncData()
        ElMessage.success(`短语卡片导入成功：「${data.title || '未命名'}」(${data.pairs.length}对)`)
        return
      }
      // 如果顶层是数组且元素含 pairs → 当作多组短语卡片
      if (Array.isArray(data) && data.length && data[0].pairs && data[0].pairs[0]?.en !== undefined) {
        let imported = 0
        for (const item of data) {
          if (!item.pairs?.length) continue
          phraseCards.value.push({
            id: generateId(),
            title: item.title || '批量导入',
            source: item.source || '',
            date: item.date || new Date().toISOString().slice(0, 10),
            sourceNote: item.sourceNote || '',
            pairs: item.pairs.map(p => ({ en: p.en, zh: p.zh })),
            practiceState: {}
          })
          imported += item.pairs.length
        }
        syncData()
        ElMessage.success(`短语卡片批量导入：${data.length}组，共${imported}对`)
        return
      }
      if (data.essays) essays.value = data.essays
      if (data.records) records.value = data.records
      if (data.settings) Object.assign(settings, data.settings)
      if (data.annotations) essayAnnotations.value = data.annotations
      if (data.tokenUsage) tokenUsage.value = data.tokenUsage
      if (data.essayOrder) essayOrder.value = data.essayOrder
      if (data.waveCache) waveCache.value = data.waveCache
      if (data.customPrompts) customPrompts.value = data.customPrompts
      if (data.manualVocab) manualVocab.value = data.manualVocab
      if (data.wordRoots) { Object.assign(wordRootsStore, data.wordRoots); Object.assign(wordAnalysisCache, data.wordRoots) }
      if (data.phraseCards) phraseCards.value = data.phraseCards
      if (data.promptConfig) {
        if (data.promptConfig.scoringPrompt) promptConfig.value.scoringPrompt = data.promptConfig.scoringPrompt
        if (data.promptConfig.segmentPrompt) promptConfig.value.segmentPrompt = data.promptConfig.segmentPrompt
        if (data.promptConfig.imageStrictPrompt) promptConfig.value.imageStrictPrompt = data.promptConfig.imageStrictPrompt
        if (data.promptConfig.imageRefPrompt) promptConfig.value.imageRefPrompt = data.promptConfig.imageRefPrompt
        if (data.promptConfig.imagePhrasePrompt) promptConfig.value.imagePhrasePrompt = data.promptConfig.imagePhrasePrompt
        if (data.promptConfig.wavePrompt) promptConfig.value.wavePrompt = data.promptConfig.wavePrompt
      } else {
        if (data.scoringPrompt) promptConfig.value.scoringPrompt = data.scoringPrompt
        if (data.segmentPrompt) promptConfig.value.segmentPrompt = data.segmentPrompt
      }
      syncData()
      ElMessage.success(`导入成功：${data.essays?.length || 0}篇范文，${data.records?.length || 0}条记录`)
    } catch (ex) { ElMessage.error('文件格式错误：' + ex.message) }
  }
  reader.readAsText(file)
  return false
}

// ========== 图表 ==========
function renderTrendChart() {
  if (!trendChartRef.value || records.value.length < 2) return
  const done = records.value.filter(r => r.completed).sort((a, b) => a.date.localeCompare(b.date))
  if (done.length < 2) return
  const isDark = darkMode.value
  const chart = echarts.init(trendChartRef.value)
  chart.setOption({
    backgroundColor: 'transparent',
    textStyle: { color: isDark ? '#777' : '#666' },
    grid: { top: 8, right: 8, bottom: 24, left: 32 },
    xAxis: { type: 'category', data: done.map(r => r.date.slice(5)), axisLabel: { fontSize: 10, color: isDark ? '#777' : '#666' }, axisLine: { lineStyle: { color: isDark ? '#333' : '#ddd' } } },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { fontSize: 10, color: isDark ? '#777' : '#666' }, splitLine: { lineStyle: { color: isDark ? '#1e1e1e' : '#eee' } } },
    series: [{ data: done.map(r => r.totalScore), type: 'line', smooth: true, areaStyle: { opacity: 0.15, color: isDark ? '#ff5f00' : '#409EFF' }, lineStyle: { color: isDark ? '#ff5f00' : '#409EFF' }, itemStyle: { color: isDark ? '#ff5f00' : '#409EFF' }, symbolSize: 4 }],
  })
  chart.resize()
}

function renderRadarChart() {
  if (!radarChartRef.value || !rightPanelRecord.value) return
  const s = rightPanelRecord.value.score
  const isDark = darkMode.value
  const chart = echarts.init(radarChartRef.value)
  chart.setOption({
    backgroundColor: 'transparent',
    textStyle: { color: isDark ? '#777' : '#666' },
    radar: { center: ['50%', '50%'], radius: '70%', axisName: { fontSize: 9, color: isDark ? '#777' : '#666' }, splitArea: { areaStyle: { color: ['transparent'] } }, splitLine: { lineStyle: { color: isDark ? '#1e1e1e' : '#eee' } }, axisLine: { lineStyle: { color: isDark ? '#333' : '#ddd' } }, indicator: [{ name: '准确性', max: 25 }, { name: '语法结构', max: 25 }, { name: '词汇表达', max: 25 }, { name: '流畅度', max: 25 }] },
    series: [{ type: 'radar', data: [{ value: [s.accuracy, s.grammar, s.vocabulary, s.fluency], name: '本次', areaStyle: { color: isDark ? 'rgba(255,95,0,0.2)' : 'rgba(64,158,255,0.2)' }, lineStyle: { color: isDark ? '#ff5f00' : '#409EFF' }, itemStyle: { color: isDark ? '#ff5f00' : '#409EFF' } }], symbolSize: 3 }],
  })
  chart.resize()
}



// ========== 图片导入增强 ==========
function openImageImport() {
  imageImportMode.value = 'single'
  imageExtractMode.value = 'strict'
  imageImportPrompt.value = promptConfig.value.imageStrictPrompt || IMAGE_IMPORT_DEFAULT_PROMPT
  // Preserve existing images/slots — only init if empty
  if (!imageSlots.value.length || !imageSlots.value.some(s => s.url)) {
    imageSlots.value = [{ url: '', blob: null }]
  }
  batchImages.value = []
  imageImportResult.value = ''
  showImageImportDialog.value = true
}

function onImageDialogOpened() {
  // Focus handled by individual slot paste handlers
}

function onImagePaste(e) {
  // Backward compat: redirect to slot 0
  onSlotPaste(e, 0)
}

const imageImportMode = ref('single') // 'single' | 'batch'
const imageExtractMode = ref('strict') // 'strict' | 'reference'
const imageSlots = ref([{ url: '', blob: null }]) // single mode: multi-image slots
const batchImages = ref([]) // batch mode: accumulated images

function onExtractModeChange(mode) {
  imageExtractMode.value = mode
  if (mode === 'strict') {
    imageImportPrompt.value = promptConfig.value.imageStrictPrompt || IMAGE_IMPORT_DEFAULT_PROMPT
  } else if (mode === 'phrase') {
    imageImportPrompt.value = promptConfig.value.imagePhrasePrompt || IMAGE_IMPORT_PROMPT_PHRASE
  } else {
    imageImportPrompt.value = promptConfig.value.imageRefPrompt || IMAGE_IMPORT_PROMPT_REFERENCE
  }
}

function saveImagePrompt() {
  if (imageExtractMode.value === 'strict') {
    promptConfig.value.imageStrictPrompt = imageImportPrompt.value
  } else if (imageExtractMode.value === 'phrase') {
    promptConfig.value.imagePhrasePrompt = imageImportPrompt.value
  } else {
    promptConfig.value.imageRefPrompt = imageImportPrompt.value
  }
  savePromptConfig()
  ElMessage.success('提示词已保存')
}

function addImageSlot() {
  if (imageSlots.value.length >= 5) { ElMessage.warning('最多5张截图'); return }
  imageSlots.value.push({ url: '', blob: null })
}

function removeImageSlot(idx) {
  if (imageSlots.value.length <= 1) { ElMessage.warning('至少保留1个槽位'); return }
  imageSlots.value.splice(idx, 1)
}

// 批量选择图片文件（单题多图模式：填槽位；逐张批量模式：追加到列表）
function selectImageFiles(mode) {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.multiple = true
  input.onchange = () => {
    const files = Array.from(input.files || [])
    if (!files.length) return
    if (mode === 'single') {
      // Fill slots, auto-expand if needed (max 5)
      const startIdx = imageSlots.value.findIndex(s => !s.url)
      const fillFrom = startIdx >= 0 ? startIdx : imageSlots.value.length
      files.forEach((file, i) => {
        const targetIdx = fillFrom + i
        while (targetIdx >= imageSlots.value.length && imageSlots.value.length < 5) {
          imageSlots.value.push({ url: '', blob: null })
        }
        if (targetIdx < imageSlots.value.length) {
          const reader = new FileReader()
          reader.onload = (ev) => { imageSlots.value[targetIdx].url = ev.target.result; imageSlots.value[targetIdx].blob = file }
          reader.readAsDataURL(file)
        }
      })
      ElMessage.success(`已加载 ${Math.min(files.length, 5 - fillFrom)} 张图片到槽位`)
    } else {
      // Batch mode: append to list
      files.forEach(file => {
        const reader = new FileReader()
        reader.onload = (ev) => { batchImages.value.push({ url: ev.target.result, blob: file }) }
        reader.readAsDataURL(file)
      })
      setTimeout(() => ElMessage.success(`已添加 ${files.length} 张，共 ${batchImages.value.length} 张`), 100)
    }
  }
  input.click()
}

function onSlotPaste(e, idx) {
  const items = e.clipboardData?.items
  if (!items) return
  const images = []
  for (const item of items) {
    if (item.type.startsWith('image/')) images.push(item)
  }
  if (!images.length) return
  e.preventDefault()
  // Fill slots starting from idx, auto-expand if needed
  images.forEach((item, i) => {
    const targetIdx = idx + i
    while (targetIdx >= imageSlots.value.length && imageSlots.value.length < 5) {
      imageSlots.value.push({ url: '', blob: null })
    }
    if (targetIdx < imageSlots.value.length) {
      const blob = item.getAsFile()
      const reader = new FileReader()
      reader.onload = (ev) => { imageSlots.value[targetIdx].url = ev.target.result; imageSlots.value[targetIdx].blob = blob }
      reader.readAsDataURL(blob)
    }
  })
  if (images.length > 1) ElMessage.success(`已识别 ${images.length} 张图片，自动填充槽位`)
}

function onBatchPaste(e) {
  const items = e.clipboardData?.items
  if (!items) return
  const images = []
  for (const item of items) {
    if (item.type.startsWith('image/')) images.push(item)
  }
  if (!images.length) return
  e.preventDefault()
  // Auto-expand slots to fit all images (max 5)
  while (imageSlots.value.length < Math.min(images.length, 5)) {
    imageSlots.value.push({ url: '', blob: null })
  }
  images.forEach((item, i) => {
    if (i >= imageSlots.value.length) return
    const blob = item.getAsFile()
    const reader = new FileReader()
    reader.onload = (ev) => {
      imageSlots.value[i].url = ev.target.result
      imageSlots.value[i].blob = blob
    }
    reader.readAsDataURL(blob)
  })
  ElMessage.success(`已识别 ${Math.min(images.length, 5)} 张图片`)
}

const buildImagePrompt = computed(() => {
  const base = imageImportPrompt.value
  const slotCount = imageSlots.value.filter(s => s.url).length
  if (slotCount <= 1) return base
  return `${base}\n\n⚠️ 本次共提供了 ${slotCount} 张截图（多图拼接/连续多页），请综合所有截图内容进行分析。`
})

async function copyImagePrompt() {
  const prompt = buildImagePrompt.value
  if (!prompt.trim()) { ElMessage.warning('提示词为空'); return }
  const filledSlots = imageSlots.value.filter(s => s.url)
  if (!filledSlots.length) { ElMessage.warning('请先粘贴至少一张截图'); return }

  try {
    // Load all images
    const imgs = await Promise.all(filledSlots.map(s => {
      return new Promise((resolve, reject) => {
        const img = new Image()
        img.onload = () => resolve(img)
        img.onerror = reject
        img.src = s.url
      })
    }))

    let imageBlob
    if (imgs.length === 1) {
      // Single image: Chrome clipboard only accepts image/png, convert if needed
      const blob = filledSlots[0].blob
      if (blob && blob.type === 'image/png') {
        imageBlob = blob
      } else {
        // Convert to PNG via canvas (handles JPEG, WebP, etc.)
        imageBlob = await new Promise(resolve => {
          const canvas = document.createElement('canvas')
          canvas.width = imgs[0].naturalWidth
          canvas.height = imgs[0].naturalHeight
          const ctx = canvas.getContext('2d')
          ctx.drawImage(imgs[0], 0, 0)
          canvas.toBlob(resolve, 'image/png')
        })
      }
    } else {
      // Stitch images vertically into one combined image
      const maxW = Math.max(...imgs.map(img => img.naturalWidth))
      const totalH = imgs.reduce((s, img) => s + img.naturalHeight, 0) + (imgs.length - 1) * 4 // 4px gap
      const canvas = document.createElement('canvas')
      canvas.width = maxW
      canvas.height = totalH
      const ctx = canvas.getContext('2d')
      ctx.fillStyle = '#fff'
      ctx.fillRect(0, 0, maxW, totalH)

      let y = 0
      for (const img of imgs) {
        ctx.drawImage(img, 0, y, img.naturalWidth, img.naturalHeight)
        y += img.naturalHeight + 4
      }

      imageBlob = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'))
    }

    // 手机端优先用 Web Share API（可同时分享文字+图片到其他应用）
    if (isMobile.value && navigator.share && navigator.canShare) {
      const file = new File([imageBlob], 'prompt-image.png', { type: 'image/png' })
      const shareData = { text: prompt, files: [file] }
      if (navigator.canShare(shareData)) {
        await navigator.share(shareData)
        ElMessage.success('已打开分享面板，选择 AI 应用即可发送提示词+截图')
        return
      }
    }

    // 桌面端：ClipboardItem（同时复制文字+图片到剪贴板）
    const clipboardItem = new ClipboardItem({
      'text/plain': Promise.resolve(new Blob([prompt], { type: 'text/plain' })),
      'image/png': Promise.resolve(imageBlob)
    })
    await navigator.clipboard.write([clipboardItem])
    ElMessage.success(`提示词+${imgs.length}张截图${imgs.length > 1 ? '(已竖拼)' : ''}已复制，直接粘贴到AI窗口即可`)
  } catch (e) {
    console.error('copyImagePrompt error:', e)
    // 剪贴板失败后尝试 Web Share（手机端兜底）
    if (navigator.share && navigator.canShare && imageBlob) {
      try {
        const file = new File([imageBlob], 'prompt-image.png', { type: 'image/png' })
        const shareData = { text: prompt, files: [file] }
        if (navigator.canShare(shareData)) {
          await navigator.share(shareData)
          ElMessage.success('已打开分享面板')
          return
        }
      } catch (shareErr) {
        if (shareErr.name === 'AbortError') return
      }
    }
    // 最终兜底：仅文字
    try {
      await navigator.clipboard.writeText(prompt)
      ElMessage.warning('仅复制了提示词文本，请手动附上截图')
    } catch {
      ElMessage.error('复制失败，请手动复制')
    }
  }
}

function importFromImageJson() {
  if (!imageImportResult.value.trim()) { ElMessage.warning('请粘贴AI返回的JSON'); return }
  try {
    const parsed = extractJSON(imageImportResult.value)
    if (!parsed) throw new Error('未识别到JSON')
    if (!parsed.originalEN) throw new Error('JSON缺少originalEN字段')

    essays.value.push({
      id: generateId(),
      date: new Date().toISOString().slice(0, 10),
      title: parsed.title || '图片导入',
      source: parsed.source || '图片导入',
      originalEN: parsed.originalEN,
      referenceTranslation: parsed.referenceTranslation || '',
      segments: (parsed.segments || [{ en: parsed.originalEN, contextZH: '', keyPoints: [] }]).map(seg => ({
        ...seg, rawTeachingNote: seg.raw_teaching_note || ''
      })),
      rawTextArchive: parsed.raw_text_archive || '',
      imageSource: parsed.sourceNote || ''
    })
    sortEssays()
    ElMessage.success(`导入成功：${parsed.title || '未命名'}（${parsed.segments?.length || 1}段）`)
    imageSlots.value = [{ url: '', blob: null }]
    imageImportResult.value = ''
    showImageImportDialog.value = false
  } catch (e) {
    ElMessage.error('JSON解析失败：' + e.message)
  }
}

function importBatchFromImageJson() {
  if (!imageImportResult.value.trim()) { ElMessage.warning('请粘贴AI返回的JSON'); return }
  try {
    // Try array first, then single object
    let items = extractJSON(imageImportResult.value)
    if (!items) throw new Error('未识别到JSON')
    if (!Array.isArray(items)) items = [items]

    let imported = 0
    for (const parsed of items) {
      if (!parsed.originalEN) continue
      essays.value.push({
        id: generateId(),
        date: new Date().toISOString().slice(0, 10),
        title: parsed.title || '图片导入',
        source: parsed.source || '图片导入',
        originalEN: parsed.originalEN,
        referenceTranslation: parsed.referenceTranslation || '',
        segments: (parsed.segments || [{ en: parsed.originalEN, contextZH: '', keyPoints: [] }]).map(seg => ({
          ...seg, rawTeachingNote: seg.raw_teaching_note || ''
        })),
        rawTextArchive: parsed.raw_text_archive || '',
        imageSource: parsed.sourceNote || ''
      })
      imported++
    }
    sortEssays()
    ElMessage.success(`导入成功：${imported}篇`)
    imageSlots.value = [{ url: '', blob: null }]
    imageImportResult.value = ''
    showImageImportDialog.value = false
  } catch (e) {
    ElMessage.error('JSON解析失败：' + e.message)
  }
}

// ========== 反转短语导入 ==========
function importPhraseFromImageJson() {
  if (!imageImportResult.value.trim()) { ElMessage.warning('请粘贴AI返回的JSON'); return }
  try {
    let parsed = extractJSON(imageImportResult.value)
    if (!parsed) throw new Error('未识别到JSON')
    // Support both single object and array
    let items = Array.isArray(parsed) ? parsed : [parsed]
    let imported = 0
    for (const item of items) {
      if (!item.pairs?.length) continue
      phraseCards.value.push({
        id: generateId(),
        title: item.title || '短语导入',
        source: item.source || '图片导入',
        date: item.date || new Date().toISOString().slice(0, 10),
        sourceNote: item.sourceNote || '',
        pairs: item.pairs.map(p => ({ en: p.en, zh: p.zh })),
        practiceState: {}
      })
      imported += item.pairs.length
    }
    flushSave()
    ElMessage.success(`导入成功：${items.length}组，共 ${imported} 个短语对`)
    imageSlots.value = [{ url: '', blob: null }]
    imageImportResult.value = ''
    showImageImportDialog.value = false
  } catch (e) {
    ElMessage.error('JSON解析失败：' + e.message)
  }
}

// ========== 逐张批量模式 ==========
function onBatchImagePaste(e) {
  const items = e.clipboardData?.items
  if (!items) return
  const images = []
  for (const item of items) {
    if (item.type.startsWith('image/')) images.push(item)
  }
  if (!images.length) return
  e.preventDefault()
  images.forEach(item => {
    const blob = item.getAsFile()
    const reader = new FileReader()
    reader.onload = (ev) => {
      batchImages.value.push({ url: ev.target.result, blob })
    }
    reader.readAsDataURL(blob)
  })
  ElMessage.success(`已添加 ${images.length} 张，共 ${batchImages.value.length} 张`)
}

function removeBatchImage(idx) {
  batchImages.value.splice(idx, 1)
}

function clearBatchImages() {
  batchImages.value = []
  imageImportResult.value = ''
}

async function copyAllBatchPrompt() {
  if (!batchImages.value.length) { ElMessage.warning('请先粘贴至少一张截图'); return }
  try {
    // Load all images and stitch vertically with separator bars
    const imgs = await Promise.all(batchImages.value.map(s => {
      return new Promise((resolve, reject) => {
        const img = new Image()
        img.onload = () => resolve(img)
        img.onerror = reject
        img.src = s.url
      })
    }))
    const sepHeight = 40
    const maxW = Math.max(...imgs.map(img => img.naturalWidth))
    const totalH = imgs.reduce((s, img) => s + img.naturalHeight + sepHeight, 0) - sepHeight
    const canvas = document.createElement('canvas')
    canvas.width = maxW
    canvas.height = totalH
    const ctx = canvas.getContext('2d')
    ctx.fillStyle = '#1a1a1a'
    ctx.fillRect(0, 0, maxW, totalH)

    let y = 0
    for (let i = 0; i < imgs.length; i++) {
      const img = imgs[i]
      ctx.drawImage(img, 0, y, img.naturalWidth, img.naturalHeight)
      y += img.naturalHeight
      if (i < imgs.length - 1) {
        // Separator bar with index label
        ctx.fillStyle = '#0d0d0d'
        ctx.fillRect(0, y, maxW, sepHeight)
        ctx.fillStyle = '#ff5f00'
        ctx.font = 'bold 16px monospace'
        ctx.textAlign = 'center'
        ctx.fillText(`── 第 ${i + 2} / ${imgs.length} 张 ──`, maxW / 2, y + sepHeight / 2 + 6)
        ctx.fillStyle = '#1a1a1a'
        y += sepHeight
      }
    }

    const imageBlob = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'))
    const promptWithHint = `${imageImportPrompt.value}\n\n⚠️ 以上共 ${batchImages.value.length} 张截图（已竖拼，以分隔线区分），请逐张分析，返回一个JSON数组：[{...第1张...}, {...第2张...}, ...]，不要用代码块包裹。`
    // 手机端优先用 Web Share API（可同时分享文字+图片到其他应用）
    if (isMobile.value && navigator.share && navigator.canShare) {
      const file = new File([imageBlob], 'batch-images.png', { type: 'image/png' })
      const shareData = { text: promptWithHint, files: [file] }
      if (navigator.canShare(shareData)) {
        await navigator.share(shareData)
        ElMessage.success('已打开分享面板，选择 AI 应用即可发送提示词+截图')
        return
      }
    }

        const clipboardItem = new ClipboardItem({
      'text/plain': Promise.resolve(new Blob([promptWithHint], { type: 'text/plain' })),
      'image/png': Promise.resolve(imageBlob)
    })
    await navigator.clipboard.write([clipboardItem])
    ElMessage.success(`提示词+${imgs.length}张截图已复制（图片间有分隔线），直接粘贴到AI窗口`)
  } catch (e) {
    console.error('copyAllBatchPrompt error:', e)
    // 手机端兜底：Web Share API
    if (navigator.share && navigator.canShare && imageBlob) {
      try {
        const file = new File([imageBlob], 'batch-images.png', { type: 'image/png' })
        const shareData = { text: promptWithHint, files: [file] }
        if (navigator.canShare(shareData)) {
          await navigator.share(shareData)
          ElMessage.success('已打开分享面板')
          return
        }
      } catch (shareErr) {
        if (shareErr.name === 'AbortError') return
      }
    }
    // 最终兜底：仅文字
    try {
      await navigator.clipboard.writeText(imageImportPrompt.value)
      ElMessage.warning('仅复制了提示词文本，请手动附上截图')
    } catch {
      ElMessage.error('复制失败')
    }
  }
}

function importAllBatch() {
  if (!imageImportResult.value.trim()) { ElMessage.warning('请粘贴AI返回的JSON数组'); return }
  try {
    let items = extractJSON(imageImportResult.value)
    if (!items) throw new Error('未识别到JSON')
    if (!Array.isArray(items)) {
      if (items.originalEN) items = [items]
      else throw new Error('请粘贴JSON数组 [{...},{...}]')
    }
    let imported = 0
    for (const parsed of items) {
      if (!parsed.originalEN) continue
      essays.value.push({
        id: generateId(),
        date: new Date().toISOString().slice(0, 10),
        title: parsed.title || '图片导入',
        source: parsed.source || '图片导入',
        originalEN: parsed.originalEN,
        referenceTranslation: parsed.referenceTranslation || '',
        segments: (parsed.segments || [{ en: parsed.originalEN, contextZH: '', keyPoints: [] }]).map(seg => ({
          ...seg, rawTeachingNote: seg.raw_teaching_note || ''
        })),
        rawTextArchive: parsed.raw_text_archive || '',
        imageSource: parsed.sourceNote || ''
      })
      imported++
    }
    sortEssays()
    ElMessage.success(`批量导入成功：${imported}篇`)
    batchImages.value = []
    imageImportResult.value = ''
    showImageImportDialog.value = false
  } catch (e) {
    ElMessage.error('JSON解析失败：' + e.message)
  }
}

// ========== 批注系统 ==========
function loadAnnotations() {
  try {
    const raw = localStorage.getItem(ANNO_STORAGE_KEY)
    if (raw) essayAnnotations.value = JSON.parse(raw)
  } catch { essayAnnotations.value = {} }
}

function saveAnnotations() {
  localStorage.setItem(ANNO_STORAGE_KEY, JSON.stringify(essayAnnotations.value))
}

function toggleAnnoMode() {
  annoMode.value = !annoMode.value
  if (annoMode.value) {
    nextTick(() => {
      initAnnoCanvas()
      const main = annoMainRef.value
      if (main) {
        main.addEventListener('scroll', onMainScroll, { passive: true })
      }
      window.addEventListener('resize', onWindowResize)
    })
  } else {
    const main = annoMainRef.value
    if (main) {
      main.removeEventListener('scroll', onMainScroll)
    }
    window.removeEventListener('resize', onWindowResize)
  }
}

function initAnnoCanvas() {
  const canvas = annoCanvasRef.value
  const main = annoMainRef.value
  if (!canvas || !main) return

  // Cover the FULL scrollable content of .ett-main
  const w = main.scrollWidth
  const h = main.scrollHeight
  canvas.width = w
  canvas.height = h
  canvas.style.width = w + 'px'
  canvas.style.height = h + 'px'
  redrawAnnoCanvas()
}

function redrawAnnoCanvas() {
  const canvas = annoCanvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, canvas.width, canvas.height)

  const anns = getCurrentAnno()
  for (const ann of anns) {
    drawAnnoStroke(ctx, ann.points, ann.color, ann.width)
  }
  if (currentAnnoStroke.value.length > 1) {
    drawAnnoStroke(ctx, currentAnnoStroke.value, drawColor.value, drawWidth.value)
  }
}

function drawAnnoStroke(ctx, points, color, width) {
  if (points.length < 2) return
  ctx.beginPath()
  ctx.strokeStyle = color
  ctx.lineWidth = width
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.moveTo(points[0].x, points[0].y)
  for (let i = 1; i < points.length; i++) {
    ctx.lineTo(points[i].x, points[i].y)
  }
  ctx.stroke()
}

function getAnnoCoords(e) {
  const canvas = annoCanvasRef.value
  const main = annoMainRef.value
  if (!canvas || !main) return null
  const rect = canvas.getBoundingClientRect()
  const scaleX = canvas.width / rect.width
  const scaleY = canvas.height / rect.height
  // Account for scroll position so annotations align with scrolled content
  return {
    x: (e.clientX - rect.left) * scaleX,
    y: (e.clientY - rect.top) * scaleY + main.scrollTop * scaleY
  }
}

function getCurrentAnno() {
  if (!currentEssayId.value) return []
  return essayAnnotations.value[currentEssayId.value] || []
}

function setCurrentAnno(anns) {
  if (currentEssayId.value) {
    essayAnnotations.value[currentEssayId.value] = anns
    saveAnnotations()
  }
}

function onAnnoMouseDown(e) {
  if (!annoMode.value) return
  const pos = getAnnoCoords(e)
  if (!pos) return

  if (isErasing.value) {
    const erased = eraseAnnoAtPos(pos)
    if (erased) redrawAnnoCanvas()
  } else {
    isDrawing.value = true
    currentAnnoStroke.value = [{ x: pos.x, y: pos.y }]
  }
}

function onAnnoMouseMove(e) {
  if (!annoMode.value) return
  const pos = getAnnoCoords(e)
  if (!pos) return

  if (isDrawing.value) {
    currentAnnoStroke.value.push({ x: pos.x, y: pos.y })
    redrawAnnoCanvas()
  } else if (isErasing.value && e.buttons === 1) {
    const erased = eraseAnnoAtPos(pos)
    if (erased) redrawAnnoCanvas()
  }
}

function onAnnoMouseUp() {
  if (isDrawing.value && currentAnnoStroke.value.length > 1) {
    const anns = getCurrentAnno()
    anns.push({
      points: [...currentAnnoStroke.value],
      color: drawColor.value,
      width: drawWidth.value
    })
    setCurrentAnno(anns)
  }
  isDrawing.value = false
  currentAnnoStroke.value = []
}

function eraseAnnoAtPos(pos) {
  const size = drawWidth.value * 4 + 4
  const half = size / 2
  const rect = { left: pos.x - half, right: pos.x + half, top: pos.y - half, bottom: pos.y + half }

  const anns = getCurrentAnno()
  const newAnns = []
  let changed = false

  for (const ann of anns) {
    const segments = splitAnnoStrokeByRect(ann.points, rect)
    if (segments.length === 1 && segments[0].length === ann.points.length) {
      newAnns.push(ann)
    } else {
      changed = true
      for (const seg of segments) {
        if (seg.length > 1) {
          newAnns.push({ points: seg, color: ann.color, width: ann.width })
        }
      }
    }
  }

  if (changed) setCurrentAnno(newAnns)
  return changed
}

function splitAnnoStrokeByRect(points, rect) {
  const segments = []
  let current = []
  for (const p of points) {
    const inside = p.x >= rect.left && p.x <= rect.right && p.y >= rect.top && p.y <= rect.bottom
    if (inside) {
      if (current.length > 1) { segments.push(current); current = [] }
      else { current = [] }
    } else {
      if (current.length === 0) current.push(p)
      else current.push(p)
    }
  }
  if (current.length > 1) segments.push(current)
  return segments
}

function setDrawColor(color) {
  drawColor.value = color
  isErasing.value = false
}

function toggleErase() {
  isErasing.value = !isErasing.value
}

function clearAnnoDrawings() {
  setCurrentAnno([])
  currentAnnoStroke.value = []
  redrawAnnoCanvas()
}

// 切换essay时重新初始化canvas
watch(currentEssayId, (newId, oldId) => {
  if (annoMode.value && newId) {
    nextTick(() => { initAnnoCanvas() })
  }
})

// 监听主区域滚动，同步canvas覆盖
function onMainScroll() {
  if (!annoMode.value) return
  const main = annoMainRef.value
  const canvas = annoCanvasRef.value
  if (!main || !canvas) return
  // Resize canvas when content changes (e.g., scoredRecord appears)
  const w = main.scrollWidth
  const h = main.scrollHeight
  if (canvas.width !== w || canvas.height !== h) {
    initAnnoCanvas()
  }
}

// 窗口大小变化时重设canvas
function onWindowResize() {
  if (annoMode.value) initAnnoCanvas()
}

watch([essays, records, essayOrder, essayAnnotations, tokenUsage, customPrompts, waveCache, phraseCards], syncData, { deep: true })
watch(currentEssayId, (newId, oldId) => {
  // Save current essay's translation draft and timer state
  if (oldId) {
    translationDrafts[oldId] = userTranslation.value
    if (practiceStarted.value) {
      timerStates[oldId] = { elapsed: elapsed.value, running: timerInterval !== null }
    }
  }
  stopTimer()
  // Restore new essay's draft
  userTranslation.value = translationDrafts[newId] || ''
  const saved = timerStates[newId]
  if (saved) {
    elapsed.value = saved.elapsed
    practiceStarted.value = true
    if (saved.running) {
      timerInterval = setInterval(() => { elapsed.value++ }, 1000)
    }
  } else {
    elapsed.value = 0
    practiceStarted.value = false
  }
})
watch(userTranslation, (val) => {
  // Persist draft on every change
  if (currentEssay.value) translationDrafts[currentEssay.value.id] = val
  if (val.trim() && !practiceStarted.value && currentEssay.value) {
    practiceStarted.value = true
    elapsed.value = 0
    stopTimer()
    timerInterval = setInterval(() => { elapsed.value++ }, 1000)
  }
})
watch(reverseUserTranslation, (val) => {
  if (val.trim() && !practiceStarted.value && currentEssay.value && scoringMode.value === 'reverse') {
    practiceStarted.value = true
    elapsed.value = 0
    stopTimer()
    timerInterval = setInterval(() => { elapsed.value++ }, 1000)
  }
})
watch(apiKey, (v) => localStorage.setItem('ett_apikey', v))
watch(customPrompts, () => saveCustomPrompts(), { deep: true })

// 全局暗色模式：el-dialog teleport 到 body 脱离组件树，需要给 html 加 class 配合非 scoped 样式
watch(darkMode, (v) => {
  document.documentElement.classList.toggle('ett-dark', v)
}, { immediate: true })

// ========== 生命周期 ==========
// 字体大小持久化
watch(fontSize, (v) => localStorage.setItem('ett_fontSize', v))

// provide shared reactive state for MobileApp component
const ett = reactive({
  // refs
  essays, records, currentEssayId, userTranslation, practiceStarted, elapsed,
  darkMode, isMobile, fontSize, apiKey, scoringMode, selectedSeg,
  waveSelectedIdx, waveAnswer, reverseUserTranslation, windowAIInput,
  showAddDialog, showPromptConfig, showVocabPoolDialog, showPhrasePracticeDialog, showWordAnalysis,
  // computed
  get currentEssay() { return essays.value.find(e => e.id === currentEssayId.value) },
  get rightPanelRecord() {
    const eid = currentEssayId.value
    if (!eid) return null
    const essayRecords = records.value.filter(r => r.essayId === eid).sort((a, b) => b.date > a.date ? 1 : -1)
    return essayRecords[0] || null
  },
  get streakDays() { return streakDays.value },
  get avgScore() { return avgScore.value },
  get totalTime() { return totalTime.value },
  get renderedFeedback() { return renderedFeedback.value },
  get reverseDisplayRef() { return reverseDisplayRef.value },
  get diffResult() {
    const rec = this.rightPanelRecord
    if (!rec || !this.currentEssay) return { userLines: [], refLines: [] }
    return smartAlign(
      splitSentences(rec.userTranslation),
      splitSentences(this.currentEssay.referenceTranslation || ''),
      rec.errorSpans
    )
  },
  get reverseDiffResult() {
    const rec = this.rightPanelRecord
    if (!rec || rec.type !== 'reverse' || !this.currentEssay) return { userLines: [], refLines: [] }
    return smartAlign(
      splitSentences(rec.userTranslation),
      splitSentences(this.currentEssay.originalEN || ''),
      rec.errorSpans
    )
  },
  // functions
  getRecord, scoreColor, formatTime,
  submitTranslation, submitWindowAI, submitReverseTranslation, copyReversePrompt,
  startPractice, openQwen, openImageImport, openHistoryPanel,
  selectWaveSegment, onWordClick, exportData, triggerImport, normalizeMistakeWaves, shareBackup,
})
provide('ett', ett)


onMounted(async () => {
  checkMobile()
  await loadData()
  loadPromptConfig()
  loadWaveCache()
  loadWordRoots()
  autoExportOnLoad()
  if (essays.value.length > 0 && !currentEssayId.value) currentEssayId.value = essays.value[0].id
  // 恢复当前范文的译文草稿（解决切后台被杀后重载丢失问题）
  if (currentEssayId.value && translationDrafts[currentEssayId.value]) {
    userTranslation.value = translationDrafts[currentEssayId.value]
    const saved = timerStates[currentEssayId.value]
    if (saved) {
      elapsed.value = saved.elapsed
      practiceStarted.value = true
      if (saved.running) {
        timerInterval = setInterval(() => { elapsed.value++ }, 1000)
      }
    }
  }
  // 监听原生 App 前后台切换（钩入 Android onPause/onStop，比 visibilitychange 可靠）
  try {
    App.addListener('appStateChange', ({ isActive }) => {
      if (!isActive) {
        // 切后台 — 立即刷盘，不等 800ms debounce
        flushSave()
      }
    })
  } catch {
    // 不在 Capacitor 环境中（浏览器调试），降级用 visibilitychange + pagehide
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') flushSave()
    })
    window.addEventListener('pagehide', () => flushSave())
  }
})
</script>

<style scoped>
.ett-container { display:flex; flex-direction:column; height:calc(100vh - 80px); padding:12px; gap:8px; box-sizing:border-box; }
.ett-container.dark { background:#060606; }
.ett-header { display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
.ett-title { margin:0; font-size:18px; white-space:nowrap; color:#f8fafc; }
.ett-header-actions { display:flex; align-items:center; gap:6px; margin-left:auto; }
.ett-body { display:flex; flex:1; gap:8px; overflow:hidden; background:#0a0a0a; border-radius:8px; color:#c1c1c1; }

/* 左侧 */
.ett-left { width:260px; flex-shrink:0; display:flex; flex-direction:column; overflow:hidden; border-right:1px solid #1a1a1a; }
.ett-left-tabs { flex:1; overflow:hidden; }
.ett-left-tabs :deep(.el-tabs__content) { overflow-y:auto; height:calc(100% - 40px); }
.essay-list { padding:8px; }
.essay-item { padding:8px; border-radius:6px; cursor:pointer; margin-bottom:4px; transition:background .15s; border:1px solid transparent; position:relative; }
.essay-item:hover { background:#141414; }
.essay-item.active { background:#1a1008; border-color:#ff5f00; }
.essay-item.done { opacity:.85; }
.essay-delete-btn { position:absolute; top:4px; right:4px; font-size:16px; width:22px; height:22px; padding:0; border-radius:4px; opacity:0; transition:opacity .15s; color:#f87171; }
.essay-item:hover .essay-delete-btn { opacity:1; }
.essay-item-title { font-weight:600; font-size:13px; padding-right:22px; color:#f1f5f9; }
.essay-item-meta { font-size:11px; color:#777; margin-top:2px; }
.essay-item-score { margin-top:4px; }
.cal-cell { position:relative; cursor:pointer; padding:4px; text-align:center; }
.cal-cell.checked { font-weight:700; }
.cal-dot { display:inline-block; width:6px; height:6px; border-radius:50%; margin-left:2px; vertical-align:middle; }
.stats-panel { padding:12px; border-top:1px solid #1a1a1a; display:grid; grid-template-columns:1fr 1fr; gap:8px; }
.stat-row { text-align:center; }
.stat-row span { font-size:11px; color:#777; display:block; text-transform:uppercase; letter-spacing:.05em; }
.stat-row strong { font-size:16px; color:#f8fafc; }

/* 中间 */
.ett-main { flex:1; overflow-y:auto; padding:12px; }
.section { margin-bottom:16px; border:1px solid #1e1e1e; border-radius:8px; padding:12px; position:relative; z-index:1; background:#0d0d0d; }
.section-header { display:flex; align-items:center; gap:8px; margin-bottom:8px; position:relative; z-index:60; }
.section-label { font-weight:700; font-size:14px; color:#f8fafc; }
.section-source { font-size:12px; color:#777; }
.timer { font-family:monospace; font-size:14px; color:#ff5f00; margin-left:auto; }
.original-text { max-height:260px; overflow-y:auto; }
.orig-seg { display:flex; align-items:flex-start; gap:6px; padding:6px 4px; cursor:pointer; border-radius:4px; transition:background .15s; }
.orig-seg:hover { background:#141414; }
.orig-seg.selected { background:#1a1008; }
.seg-num { flex-shrink:0; width:20px; height:20px; border-radius:50%; background:#ff5f00; color:#fff; font-size:11px; text-align:center; line-height:20px; }
.seg-en { flex:1; font-size:14px; line-height:1.6; color:#c1c1c1; }
.seg-hint { font-size:11px; color:#ff5f00; flex-shrink:0; }

/* 对照视图 */
.compare-view { display:flex; gap:8px; }
.compare-col { flex:1; border:1px solid #1e1e1e; border-radius:6px; padding:8px; background:#0d0d0d; }
.compare-col-title { font-size:12px; color:#777; margin-bottom:6px; font-weight:600; }
.compare-col p { margin:4px 0; padding:4px; border-radius:3px; font-size:13px; line-height:1.6; color:#c1c1c1; }
.compare-col p.match { background:#0a1a0a; }
.compare-col p.diff { background:#1a1408; }
.compare-col p.missing { background:#1a0a0a; color:#777; font-style:italic; }

/* 右侧评分 */
.ett-right { width:320px; flex-shrink:0; overflow-y:auto; padding:12px; border-left:1px solid #1a1a1a; }
.score-card { text-align:center; }
.total-score { font-size:48px; font-weight:800; color:#f8fafc; }
.score-unit { font-size:18px; color:#777; }
.dim-scores { margin-top:8px; }
.dim-item { margin-bottom:10px; }
.dim-item span { font-size:12px; color:#777; display:block; margin-bottom:4px; }
.feedback-card { margin-top:16px; border:1px solid #1e1e1e; border-radius:8px; padding:12px; background:#0d0d0d; }
.feedback-title { font-weight:700; font-size:14px; margin-bottom:8px; color:#f8fafc; }
.feedback-content { font-size:13px; line-height:1.8; color:#c1c1c1; }
.history-card { margin-top:12px; }

/* Light mode overrides (when darkMode is off) */
.ett-container:not(.dark) { background:#f5f7fa; }
.ett-body:not(.dark) { background:#fff; color:#333; }
.ett-body:not(.dark) .ett-title { color:#333; }
.ett-body:not(.dark) .ett-left { border-right-color:#eee; }
.ett-body:not(.dark) .essay-item:hover { background:#f0f4ff; }
.ett-body:not(.dark) .essay-item.active { background:#e8f0fe; }
.ett-body:not(.dark) .essay-item-title { color:#333; }
.ett-body:not(.dark) .essay-item-meta { color:#8492a6; }
.ett-body:not(.dark) .section { background:#fff; border-color:#eee; }
.ett-body:not(.dark) .section-label { color:#333; }
.ett-body:not(.dark) .section-source { color:#8492a6; }
.ett-body:not(.dark) .timer { color:#409EFF; }
.ett-body:not(.dark) .orig-seg:hover { background:#f8f9fa; }
.ett-body:not(.dark) .orig-seg.selected { background:#e8f0fe; }
.ett-body:not(.dark) .seg-en { color:#333; }
.ett-body:not(.dark) .compare-col { background:#fff; border-color:#eee; }
.ett-body:not(.dark) .compare-col-title { color:#8492a6; }
.ett-body:not(.dark) .compare-col p { color:#333; }
.ett-body:not(.dark) .compare-col p.match { background:#f0fff0; }
.ett-body:not(.dark) .compare-col p.diff { background:#fff3cd; }
.ett-body:not(.dark) .compare-col p.missing { background:#ffe0e0; color:#aaa; }
.ett-body:not(.dark) .ett-right { border-left-color:#eee; }
.ett-body:not(.dark) .feedback-card,.ett-body:not(.dark) .score-card { border-color:#eee; }
.ett-body:not(.dark) .feedback-title,.ett-body:not(.dark) .total-score { color:#333; }
.ett-body:not(.dark) .feedback-content { color:#606266; }
.ett-body:not(.dark) .dim-item span { color:#606266; }
.ett-body:not(.dark) .stat-row span { color:#8492a6; }
.ett-body:not(.dark) .stat-row strong { color:#333; }
.ett-body:not(.dark) .stats-panel { border-top-color:#eee; }
.ett-body:not(.dark) .wave-seg-item { color:#333; border-color:#e4e7ed; }
.ett-body:not(.dark) .wave-seg-item:hover { background:#ecf5ff; border-color:#409EFF; }
.ett-body:not(.dark) .wave-seg-item.active { background:#d9ecff; }
.ett-body:not(.dark) .wave-answer-card { background:linear-gradient(135deg,#f0f7ff,#e8f4fd); border-color:#b3d8ff; }
.ett-body:not(.dark) .wave-answer-title,.ett-body:not(.dark) .wave-tree-text,.ett-body:not(.dark) .wave-summary-overview { color:#303133; }
.ett-body:not(.dark) .wave-tree-label,.ett-body:not(.dark) .wave-summary-label,.ett-body:not(.dark) .wave-summary-col ul { color:#606266; }
.ett-body:not(.dark) .wave-summary-col { background:#fafafa; }
.ett-body:not(.dark) .wave-summary-cheer { background:linear-gradient(135deg,#e8f8e8,#d4edda); color:#2d6a4f; }
.ett-body:not(.dark) .mw-label { color:#8492a6; }
.ett-body:not(.dark) .mw-pattern { color:#e6a23c; }
.ett-body:not(.dark) .mw-text { color:#606266; }
.ett-body:not(.dark) .mw-ex-en { color:#333; }
.ett-body:not(.dark) .mw-ex-zh { color:#8492a6; }
.ett-body:not(.dark) .vocab-pool-card { background:#fff; border-color:#e4e7ed; }
.ett-body:not(.dark) .vocab-text { color:#303133; }
.ett-body:not(.dark) .vocab-meaning { color:#606266; }
.ett-body:not(.dark) .vocab-item { border-bottom-color:#f2f3f5; }
.ett-body:not(.dark) .bilibili-card { background:#fff7fa; border-color:#ffe0ea; color:#333; }
.ett-body:not(.dark) .bilibili-card:hover { background:#ffe0ea; }
.ett-body:not(.dark) .window-ai-paste { background:#fdf6e3; border-color:#e6c560; }
.ett-body:not(.dark) .window-ai-label { color:#b8860b; }
.ett-body:not(.dark) .image-slot { background:#fafafa; border-color:#dcdfe6; }
.ett-body:not(.dark) .image-paste-zone { background:#f5f5f5; border-color:#d9d9d9; }
.ett-body:not(.dark) .batch-paste-zone { background:#fdf6ec; color:#e6a23c; }
.ett-body:not(.dark) .anno-float-toolbar { background:#fffaeb; border-color:#ffe8a0; }
.ett-body:not(.dark) .score-unit,.ett-body:not(.dark) .seg-hint { color:inherit; }
.ett-body:not(.dark) .token-usage { color:#909399; }
.ett-body:not(.dark) .token-val { color:#409EFF; }
.ett-body:not(.dark) .token-detail { color:#c0c4cc; }
.ett-body:not(.dark) .hint-text,.ett-body:not(.dark) .mode-desc p,.ett-body:not(.dark) .history-meta,.ett-body:not(.dark) .history-label { color:#8492a6; }
.ett-body:not(.dark) .mode-desc b,.ett-body:not(.dark) .history-record-date,.ett-body:not(.dark) .history-essay-info h3 { color:#333; }
.ett-body:not(.dark) .history-dims span { background:#f0f4ff; }
.ett-body:not(.dark) .history-translation p { color:#444; }
.ett-body:not(.dark) .history-feedback p { color:#606266; }
.ett-body:not(.dark) .essay-item.drag-over { background:#ecf5ff; border-color:#409EFF; }
.ett-body:not(.dark) .essay-item.active { background:#e8f0fe; border-color:#409EFF; }
.ett-body:not(.dark) .seg-num { background:#409EFF; }
.ett-body:not(.dark) .feedback-card { background:#fff; }
.ett-body:not(.dark) .timer { color:#409EFF; }
.ett-body:not(.dark) .seg-hint { color:#F59E0B; }
.ett-body:not(.dark) mark { background:#fff3cd; color:#856404; }

/* Element Plus dark mode deep overrides */
.dark :deep(.el-tabs--border-card) { background:#0a0a0a; border-color:#1a1a1a; }
.dark :deep(.el-tabs--border-card > .el-tabs__header) { background:#0d0d0d; border-bottom-color:#1a1a1a; }
.dark :deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item) { color:#777; border-color:#1a1a1a; }
.dark :deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item.is-active) { color:#f8fafc; background:#0a0a0a; }
.dark :deep(.el-dialog) { background:#0d0d0d; border:1px solid #1e1e1e; --el-dialog-bg-color:#0d0d0d; }
.dark :deep(.el-dialog__title) { color:#f8fafc; }
.dark :deep(.el-dialog__header) { border-bottom:1px solid #1e1e1e; }
.dark :deep(.el-dialog__body) { color:#c1c1c1; }
.dark :deep(.el-divider--horizontal) { border-top-color:#1e1e1e; }
.dark :deep(.el-drawer) { background:#0d0d0d; }
.dark :deep(.el-drawer__title) { color:#f8fafc; }
.dark :deep(.el-input__wrapper) { background:#141414; box-shadow:0 0 0 1px #1e1e1e; }
.dark :deep(.el-input__inner) { color:#f8fafc; }
.dark :deep(.el-select .el-input__wrapper) { background:#141414; }
.dark :deep(.el-select-dropdown) { background:#141414; border:1px solid #1e1e1e; }
.dark :deep(.el-select-dropdown__item) { color:#c1c1c1; }
.dark :deep(.el-select-dropdown__item.hover) { background:#1a1008; }
.dark :deep(.el-select-dropdown__item.selected) { color:#ff5f00; }
.dark :deep(.el-calendar) { background:#0a0a0a; }
.dark :deep(.el-calendar__header) { border-bottom:1px solid #1a1a1a; }
.dark :deep(.el-calendar__title) { color:#f8fafc; }
.dark :deep(.el-calendar-table .el-calendar-day) { border-top:1px solid #1a1a1a; border-left:1px solid #1a1a1a; }
.dark :deep(.el-calendar-table td.is-today) { color:#ff5f00; }
.dark :deep(.el-radio-button__inner) { background:#141414; border-color:#1e1e1e; color:#777; }
.dark :deep(.el-radio-button__orig-radio:checked + .el-radio-button__inner) { background:#ff5f00; border-color:#ff5f00; color:#fff; }
.dark :deep(.el-button--default) { background:#141414; border-color:#1e1e1e; color:#c1c1c1; }
.dark :deep(.el-button--default:hover) { background:#1a1008; border-color:#ff5f00; color:#f8fafc; }
.dark :deep(.el-textarea__inner) { background:#141414; border-color:#1e1e1e; color:#f8fafc; }
.dark :deep(.el-progress-bar__outer) { background:#1e1e1e; }
.dark :deep(.el-tabs__item) { color:#777; }
.dark :deep(.el-tabs__item.is-active) { color:#ff5f00; }
.dark :deep(.el-tabs__active-bar) { background-color:#ff5f00; }

/* Window AI mode */
.window-ai-paste { margin-top:12px; padding:10px; background:#141414; border-radius:6px; border:1px dashed #ff5f00; }
.window-ai-label { font-size:12px; color:#ff5f00; margin-bottom:6px; font-weight:600; }

/* B站链接 */
.bilibili-links { margin-top:12px; }
.bilibili-title { font-size:12px; color:#fb7299; font-weight:600; margin-bottom:6px; }
.bilibili-card { display:flex; flex-wrap:wrap; align-items:center; gap:6px; padding:8px; margin-bottom:6px; background:#0d0608; border:1px solid #1a0a10; border-radius:6px; text-decoration:none; color:#c1c1c1; transition:background .15s; }
.bilibili-card:hover { background:#1a0a10; }
.bilibili-tag { font-size:11px; background:#fb7299; color:#fff; padding:1px 6px; border-radius:3px; flex-shrink:0; }
.bilibili-name { font-size:13px; font-weight:600; color:#fb7299; }
.bilibili-desc { font-size:11px; color:#777; width:100%; }

/* History */
.history-btn { font-size:11px; padding:0 2px; margin-left:6px; }
.history-essay-info h3 { margin:0 0 4px; font-size:calc(16px * var(--ett-fs, 1)); color:#f8fafc; }
.history-meta { font-size:calc(12px * var(--ett-fs, 1)); color:#777; margin:0; }
.history-empty { padding:40px 0; }
.history-record-card { padding:8px 0; }
.history-record-header { display:flex; align-items:center; gap:8px; margin-bottom:6px; }
.history-record-date { font-size:calc(13px * var(--ett-fs, 1)); font-weight:600; color:#f8fafc; }
.history-time { font-size:calc(12px * var(--ett-fs, 1)); color:#777; margin-left:auto; }
.history-dims { display:flex; gap:12px; font-size:calc(11px * var(--ett-fs, 1)); color:#777; margin-bottom:6px; }
.history-dims span { background:#1a1008; padding:2px 6px; border-radius:3px; color:#c1c1c1; }
.history-translation { margin-bottom:6px; }
.history-translation p { font-size:calc(12px * var(--ett-fs, 1)); color:#a8a8a8; margin:2px 0 0; line-height:1.6; }
.history-label { font-size:calc(11px * var(--ett-fs, 1)); color:#777; font-weight:600; }
.history-feedback p { font-size:calc(12px * var(--ett-fs, 1)); line-height:1.6; margin:2px 0 0; color:#c1c1c1; }

/* Hint text */
.hint-text { font-size:11px; color:#777; margin-top:4px; }
.mode-desc p { margin:4px 0; font-size:12px; color:#777; }
.mode-desc b { color:#f8fafc; }


/* Image import dialog */
.image-import-layout { display:flex; gap:16px; }
.image-import-left { width:340px; flex-shrink:0; }
.image-import-right { flex:1; display:flex; flex-direction:column; }
.image-paste-label { font-size:13px; font-weight:600; margin-bottom:6px; color:#c1c1c1; }
.image-paste-zone {
  width:100%; height:320px; border:2px dashed #2a2a2a; border-radius:8px;
  display:flex; align-items:center; justify-content:center; cursor:pointer;
  outline:none; transition:border-color .2s; overflow:hidden; background:#080808;
}
.image-paste-zone:focus { border-color:#ff5f00; }
.image-paste-zone.has-image { border-style:solid; border-color:#ff5f00; padding:0; background:#080808; }
.paste-placeholder { text-align:center; color:#555; display:flex; flex-direction:column; align-items:center; gap:8px; font-size:13px; }
.paste-icon { font-size:36px; }
.pasted-image-preview { width:100%; height:100%; object-fit:contain; }
.image-import-section { display:flex; flex-direction:column; }
.image-import-label { font-size:13px; font-weight:600; margin-bottom:6px; color:#c1c1c1; }

/* Annotation system */
.anno-main { position:relative; }
.anno-float-toolbar {
  display:flex; align-items:center; gap:6px; padding:4px 10px;
  background:#141414; border:1px solid #1e1e1e; border-bottom:2px solid #2a2a2a;
  border-radius:0 0 6px 6px; flex-wrap:wrap;
  position:sticky; top:0; z-index:100;
}
.toolbar-label { font-size:11px; color:#777; white-space:nowrap; }
.color-dot {
  width:18px; height:18px; border-radius:50%; cursor:pointer;
  border:2px solid transparent; transition:border-color .15s, transform .15s; flex-shrink:0;
}
.color-dot:hover { transform:scale(1.15); }
.color-dot.active { border-color:#f8fafc; box-shadow:0 0 0 2px rgba(255,255,255,0.15); }
.anno-count { font-size:11px; color:#777; white-space:nowrap; margin-left:4px; }

.anno-canvas {
  position:absolute; top:0; left:0; z-index:50; cursor:crosshair;
  pointer-events:auto; background:transparent;
}

/* ========== 短语默写练习 ========== */
.phrase-practice-layout { display:flex; gap:12px; min-height:400px; }
.phrase-set-list { width:220px; flex-shrink:0; border-right:1px solid #1e1e1e; overflow-y:auto; max-height:460px; padding-right:8px; }
.phrase-set-label { font-size:12px; color:#777; font-weight:600; margin-bottom:8px; display:flex; align-items:center; gap:8px; }
.phrase-set-toggle { font-size:11px; color:#ff5f00; padding:0 4px; }
.phrase-set-item { padding:8px; border-radius:6px; cursor:pointer; margin-bottom:4px; border:1px solid transparent; transition:all .15s; position:relative; }
.phrase-set-delete { position:absolute; top:4px; right:4px; font-size:16px; width:22px; height:22px; padding:0; border-radius:4px; opacity:0; transition:opacity .15s; color:#f87171; }
.phrase-set-item:hover .phrase-set-delete { opacity:1; }
.phrase-set-item:hover { background:#141414; }
.phrase-set-item.active { background:#1a1008; border-color:#ff5f00; }
.phrase-set-title { font-size:13px; font-weight:600; color:#f8fafc; }
.phrase-set-meta { font-size:11px; color:#777; margin-top:2px; }
.phrase-set-progress { font-size:11px; color:#ff5f00; margin-top:2px; }
.phrase-practice-area { flex:1; display:flex; flex-direction:column; }
.phrase-practice-header { display:flex; align-items:center; gap:8px; margin-bottom:8px; }
.phrase-practice-title { font-size:15px; font-weight:700; color:#f8fafc; }
.phrase-practice-source { font-size:11px; color:#777; }
.phrase-progress-bar { display:flex; align-items:center; font-size:12px; color:#777; margin-bottom:12px; }
.phrase-card { border:1px solid #1e1e1e; border-radius:10px; padding:20px; background:#0d0d0d; flex:1; }
.phrase-card.revealed { border-color:#ff5f00; }
.phrase-zh-display { font-size:18px; line-height:1.8; color:#f8fafc; padding:12px; background:#141414; border-radius:8px; margin-bottom:12px; }
.phrase-en-area { margin-bottom:12px; }
.phrase-en-label { font-size:12px; color:#777; font-weight:600; margin-bottom:4px; }
.phrase-answer-reveal { margin-top:12px; padding:12px; background:#0a1a0a; border-radius:8px; border:1px solid #1a2a1a; }
.phrase-en-original { font-size:16px; line-height:1.6; color:#4a4; font-weight:600; }
.phrase-actions { display:flex; gap:8px; margin-top:12px; justify-content:center; }
.phrase-nav { display:flex; gap:8px; justify-content:center; margin-top:12px; }

/* Light mode for phrase practice */
.ett-body:not(.dark) .phrase-set-list { border-right-color:#eee; }
.ett-body:not(.dark) .phrase-set-item:hover { background:#f8f9fa; }
.ett-body:not(.dark) .phrase-set-item.active { background:#e8f0fe; border-color:#409EFF; }
.ett-body:not(.dark) .phrase-set-title { color:#333; }
.ett-body:not(.dark) .phrase-card { background:#fff; border-color:#eee; }
.ett-body:not(.dark) .phrase-card.revealed { border-color:#409EFF; }
.ett-body:not(.dark) .phrase-zh-display { background:#f8f9fa; color:#333; }
.ett-body:not(.dark) .phrase-en-original { color:#2d6a4f; }
.ett-body:not(.dark) .phrase-answer-reveal { background:#f0fff0; border-color:#d4edda; }
.ett-body:not(.dark) .phrase-practice-title { color:#333; }

/* ========== 手机端适配 ========== */
@media (max-width:768px) {
  .ett-container { height:100dvh; padding:0; gap:0; }
  .ett-header { display:none; }
  .ett-body { display:none; }
  /* 图片导入双栏改纵向 */
  .image-import-layout { flex-direction: column !important; }
  .image-import-left, .image-import-right { width: 100% !important; }
  .timer { font-size:13px; }
  .el-textarea__inner { font-size:14px !important; }
  /* 生词池弹窗 */
  .vocab-dialog { width:95% !important; }
  /* 短语默写弹窗 */
  .phrase-practice-layout { flex-direction:column; overflow-x:hidden; width:100%; }
  .phrase-practice-layout * { max-width:100%; }
  .phrase-set-list { width:100%; min-width:0; border-right:none; border-bottom:1px solid #1e1e1e; max-height:35vh; overflow-y:auto; padding:0 0 6px 0; flex-shrink:0; }
  .phrase-set-item { padding:6px 8px; }
  .phrase-set-item .phrase-set-delete { opacity:1; }
  .phrase-set-title { font-size:12px; overflow:hidden; text-overflow:ellipsis; }
  .phrase-set-meta { font-size:10px; }
  .phrase-set-progress { font-size:10px; }
  .phrase-practice-area { min-width:0; overflow-x:hidden; }
  .phrase-practice-header { flex-wrap:wrap; }
  .phrase-zh-display { font-size:16px; word-break:break-word; overflow-wrap:break-word; }
  .phrase-en-original { font-size:14px; word-break:break-word; overflow-wrap:break-word; }
  .phrase-card { padding:12px; min-width:0; overflow-x:hidden; }
  .phrase-progress-bar { font-size:11px; flex-wrap:wrap; }
  .phrase-nav { flex-wrap:wrap; }
  .phrase-en-area :deep(.el-textarea__inner) { font-size:14px; }
}

/* ========== Token 用量 ========== */
.token-usage {
  font-size: 12px;
  color: #777;
  white-space: nowrap;
}
.token-label { margin-right: 2px; }
.token-val { color: #ff5f00; font-weight: 600; }
.token-detail { color: #555; }

/* ========== 水波训练 ========== */
.wave-section { margin-top: 12px; }
.wave-seg-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 8px 0;
}
.wave-seg-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid #1e1e1e;
  transition: all 0.2s;
  font-size: 14px;
  color: #c1c1c1;
}
.wave-seg-item:hover { border-color: #ff5f00; background:#1a1008; }
.wave-seg-item.active { border-color: #ff5f00; background:#1a1008; box-shadow: 0 0 0 2px rgba(255,95,0,0.2); }
.wave-seg-item.analyzing { opacity: 0.7; pointer-events: none; }
.wave-seg-item.cached { border-color: #1a2a1a; }
.wave-seg-num {
  width: 24px; height: 24px;
  border-radius: 50%;
  background: #ff5f00;
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}
.wave-seg-text { flex: 1; }
.wave-seg-badge { color: #4a4; font-size: 12px; flex-shrink: 0; }

.wave-answer-card {
  margin-top: 12px;
  padding: 16px;
  border-radius: 10px;
  background: #0d0d0d;
  border: 1px solid #1e1e1e;
}
.wave-answer-header { margin-bottom: 10px; }
.wave-answer-title { font-weight: 700; font-size: 15px; color: #f8fafc; }
.wave-tree-section { margin-bottom: 10px; }
.wave-tree-label {
  font-size: 13px;
  color: #777;
  font-weight: 600;
  margin-bottom: 4px;
}
.wave-tree-text {
  font-size: 14px;
  color: #c1c1c1;
  line-height: 1.6;
  padding-left: 8px;
  border-left: 3px solid #ff5f00;
}
.wave-tree-text.stuck { border-left-color: #ff5f00; background:#1a1008; padding:8px; border-radius:0 6px 6px 0; }
.wave-tree-text.resolve { border-left-color: #4a4; background:#0a1a0a; padding:8px; border-radius:0 6px 6px 0; }
.wave-tree-text.analogy { border-left-color: #f59e0b; background:#1a1408; padding:8px; border-radius:0 6px 6px 0; font-style:italic; }
.wave-raw { font-size:13px; white-space:pre-wrap; color:#777; margin:0; }

.wave-summary-section { margin-top:16px; }
.wave-summary-header { font-weight:700; font-size:16px; margin-bottom:10px; color:#f8fafc; }
.wave-summary-overview { font-size:14px; color:#c1c1c1; line-height:1.6; margin-bottom:12px; }
.wave-summary-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-bottom:12px; }
.wave-summary-col { background:#0d0d0d; border-radius:8px; padding:10px; border:1px solid #1e1e1e; }
.wave-summary-label { font-size:13px; color:#777; font-weight:600; margin-bottom:6px; }
.wave-summary-col ul { margin:0; padding-left:18px; font-size:13px; color:#777; }
.wave-summary-col li { margin-bottom:4px; }
.focus-tags { display:flex; flex-wrap:wrap; gap:6px; margin-top:6px; }
.wave-summary-cheer {
  margin-top:12px;
  padding:10px 14px;
  background:#0a1a0a;
  border-radius:8px;
  font-size:14px;
  color:#4a4;
  font-weight:500;
}

/* ========== 图片导入增强 ========== */
.extract-mode-bar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
}
.image-slots-section { margin-bottom: 10px; }
.image-slots-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 8px;
}
.image-slot-wrap { position: relative; }
.image-slot {
  width: 100%;
  aspect-ratio: 4/3;
  border: 2px dashed #2a2a2a;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.2s;
  overflow: hidden;
  background: #080808;
}
.image-slot:focus { outline: none; border-color: #ff5f00; }
.image-slot.has-image { border-style: solid; border-color: #555; }
.slot-preview { width: 100%; height: 100%; object-fit: contain; }
.slot-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  color: #555;
  font-size: 13px;
}
.slot-hint { font-size: 11px; color: #3a3a3a; }
.slot-remove { position: absolute; top: -6px; right: -6px; padding: 2px 6px; min-width: auto; }
.batch-paste-section { margin-top: 12px; }
.batch-paste-zone {
  border: 2px dashed #ff5f00;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  color: #ff5f00;
  font-size: 13px;
  cursor: pointer;
  background: #0d0600;
  transition: all 0.2s;
}
.batch-paste-zone:focus { border-color: #ff5f00; background: #1a0a00; }
.single-fallback { margin-top: 8px; }

/* 逐张批量模式 */
.batch-import-layout { display:flex; gap:16px; }
.batch-import-left { flex: 1; min-width: 0; }
.batch-import-left .batch-paste-zone {
  min-height: 80px;
  margin-bottom: 10px;
}
.batch-image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
}
.batch-image-item {
  position: relative;
  width: 100px;
  height: 70px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #2a2a2a;
  background: #0d0d0d;
}
.batch-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.batch-idx {
  position: absolute;
  top: 2px;
  left: 4px;
  background: rgba(0,0,0,0.7);
  color: #ff5f00;
  font-size: 11px;
  font-weight: 600;
  padding: 0 4px;
  border-radius: 2px;
}
.batch-remove {
  position: absolute;
  top: -2px;
  right: -2px;
  padding: 0 4px;
  min-width: auto;
  font-size: 12px;
}

/* ========== 水波纠错 ========== */
.mw-block { margin-bottom: 14px; }
.mw-label { font-size: 13px; color: #777; font-weight: 600; margin-bottom: 4px; }
.mw-pattern { font-size: 16px; color: #ff5f00; font-weight: 500; margin: 2px 0; line-height: 1.7; letter-spacing: 0.02em; }
.mw-text { font-size: 13px; color: #c1c1c1; line-height: 1.7; margin: 0; }
.mw-ex-row { display: flex; align-items: baseline; gap: 6px; padding: 2px 0; font-size: 13px; }
.mw-ex-en { color: #c1c1c1; }
.mw-ex-arrow { color: #555; flex-shrink: 0; }
.mw-ex-zh { color: #777; }

/* 错误结构卡片 */
.mw-card {
  border: 1px solid #2d2d3f; border-radius: 10px; padding: 12px; margin-bottom: 10px;
  background: #0d0d0d;
}
.mw-card-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.mw-sentence-tag { font-size: 11px; background: #409eff; color: #fff; padding: 1px 8px; border-radius: 10px; }
.mw-error-tag { font-size: 11px; background: #e6a23c; color: #fff; padding: 1px 8px; border-radius: 10px; }

/* 翻译错误对照表 */
.te-table { border-radius: 10px; overflow: hidden; border: 1px solid #2d2d3f; margin-bottom: 12px; }
.te-row { display: flex; border-bottom: 1px solid #1e1e30; }
.te-row:last-child { border-bottom: none; }
.te-cell { padding: 8px 10px; font-size: 12px; line-height: 1.5; }
.te-orig { flex: 0 0 140px; color: #ff5f00; font-family: monospace; border-right: 1px solid #1e1e30; background: #0d0d0d; }
.te-correct { flex: 0 0 120px; color: #22C55E; border-right: 1px solid #1e1e30; }
.te-wrong { flex: 0 0 130px; color: #ef4444; border-right: 1px solid #1e1e30; text-decoration: line-through; text-decoration-color: #ef444466; }
.te-note { flex: 1; color: #888; font-size: 11px; }

/* ========== 生词短语池 ========== */
.vocab-pool-card {
  margin-top: 12px;
  padding: 14px;
  border-radius: 10px;
  border: 1px solid #1e1e1e;
  background: #0d0d0d;
}
.vocab-list { max-height: 400px; overflow-y: auto; }
.vocab-item {
  padding: 8px 10px;
  border-bottom: 1px solid #141414;
}
.vocab-item:last-child { border-bottom: none; }
.vocab-word {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}
.vocab-text { font-weight: 600; font-size: 14px; color: #f8fafc; }
.vocab-meaning { font-size: 13px; color: #c1c1c1; margin-bottom: 2px; }
.vocab-meta { font-size: 11px; color: #555; }

/* 生词池全量弹窗 */
.vocab-pool-full-list { max-height: 60vh; overflow-y: auto; }

/* Error highlight mark */
mark { background:#3a1000; color:#ff5f00; padding:0 2px; border-radius:2px; }

/* ========== 反转训练 ========== */
.reverse-ref-text {
  max-height: 260px;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.8;
  color: #c1c1c1;
  padding: 8px 12px;
  background: #0a0a0a;
  border-radius: 6px;
  border: 1px solid #1a1a1a;
}
.ett-body:not(.dark) .reverse-ref-text {
  color: #333;
  background: #f8f9fa;
  border-color: #e4e7ed;
}

/* Word analysis light mode */
.ett-body:not(.dark) .wa-word { color:#e6a23c; }
.ett-body:not(.dark) .wa-part { background:#fff; border-color:#e4e7ed; }
.ett-body:not(.dark) .wa-part-misleading { background:#fef0f0; border-color:#f56c6c; }
.ett-body:not(.dark) .wa-part-text { color:#303133; }
.ett-body:not(.dark) .wa-part-meaning { color:#606266; }
.ett-body:not(.dark) .wa-test-title { color:#606266; }
.ett-body:not(.dark) .wa-warning { background:#fef0f0; color:#f56c6c; }
.ett-body:not(.dark) .wa-answer-meaning { color:#67c23a; }
.ett-body:not(.dark) .wa-answer-reasoning { background:#f8f9fa; color:#303133; }
.ett-body:not(.dark) .wa-answer-yours { background:#fdf6ec; color:#e6a23c; }
.ett-body:not(.dark) .wa-raw-notice { background:#fdf6ec; color:#e6a23c; }
.ett-body:not(.dark) .wa-raw-content { background:#fff; color:#303133; border-color:#e4e7ed; }

/* ========== Drag-drop ========== */
.essay-item.dragging { opacity: 0.5; }
.essay-item.drag-over { border-color: #ff5f00; background:#1a1008; }

/* ========== 词根词缀分析弹窗 ========== */
.wa-loading { text-align:center; padding:40px 0; color:#777; display:flex; align-items:center; justify-content:center; gap:8px; font-size:14px; }
.wa-init { padding: 12px 0; }
.wa-word { font-size:28px; font-weight:800; text-align:center; color:#ff5f00; margin-bottom:16px; letter-spacing:0.04em; font-family:Georgia,serif; }
.wa-breakdown { display:flex; flex-direction:column; gap:10px; margin-bottom:12px; }
.wa-part { display:flex; align-items:center; gap:10px; padding:10px 14px; border-radius:8px; background:#0d0d0d; border:1px solid #1e1e1e; }
.wa-part-misleading { border-color:#ff5f00; background:#1a1008; }
.wa-part-text { font-size:18px; font-weight:700; color:#f8fafc; font-family:monospace; min-width:60px; }
.wa-part-meaning { font-size:14px; color:#c1c1c1; flex:1; }
.wa-badge { font-size:12px; padding:2px 8px; border-radius:4px; flex-shrink:0; font-weight:600; }
.wa-badge-good { color:#22c55e; background:#0a1a0a; }
.wa-badge-warn { color:#f59e0b; background:#1a1408; }
.wa-badge-bad { color:#ef4444; background:#1a0a0a; }
.wa-warning { margin-top:10px; padding:10px 14px; background:#1a0a0a; border:1px solid #ff5f00; border-radius:8px; font-size:13px; color:#ff5f00; line-height:1.6; }
.wa-test { margin-top:4px; }
.wa-test-title { font-size:14px; color:#c1c1c1; margin-bottom:8px; font-weight:600; }
.wa-answer { margin-top:4px; }
.wa-answer-label { font-size:13px; color:#777; font-weight:600; margin-bottom:6px; }
.wa-answer-meaning { font-size:22px; font-weight:700; color:#22c55e; margin-bottom:4px; }
.wa-answer-reasoning { font-size:14px; color:#c1c1c1; line-height:1.8; padding:10px 14px; background:#0d0d0d; border-radius:8px; border-left:3px solid #ff5f00; }
.wa-answer-yours { font-size:14px; color:#f59e0b; line-height:1.8; padding:10px 14px; background:#1a1408; border-radius:8px; border-left:3px solid #f59e0b; }
.wa-raw-notice { font-size:13px; color:#f59e0b; margin-bottom:12px; padding:8px 12px; background:#1a1408; border-radius:6px; border-left:3px solid #f59e0b; }
.wa-raw-content { font-size:14px; color:#c1c1c1; line-height:1.8; white-space:pre-wrap; padding:14px; background:#0d0d0d; border-radius:8px; border:1px solid #1e1e1e; max-height:400px; overflow-y:auto; font-family:monospace; }

/* Hover hint on clickable English text */
.seg-en { cursor:pointer; transition:background .15s; padding:2px 4px; border-radius:3px; }
.seg-en:hover { background:rgba(255,95,0,0.1); color:#ff5f00; }
.wave-seg-text { cursor:pointer; }
.wave-seg-text:hover { color:#ff5f00; }

</style>

<!-- 全局暗色弹窗 + 抽屉（非scoped，teleport到body后脱离组件树） -->
<style>
html.ett-dark .el-dialog { --el-dialog-bg-color: #0d0d0d; background: #0d0d0d; border: 1px solid #1e1e1e; }
html.ett-dark .el-dialog__header { border-bottom: 1px solid #1e1e1e; }
html.ett-dark .el-dialog__title { color: #f8fafc; }
html.ett-dark .el-dialog__body { color: #c1c1c1; }
html.ett-dark .el-drawer { background: #0d0d0d; }
html.ett-dark .el-drawer__title { color: #f8fafc; }
html.ett-dark .el-drawer__header { border-bottom: 1px solid #1e1e1e; }
html.ett-dark .el-drawer__body { color: #c1c1c1; }

/* ===== 手机端弹窗适配（全局，因Element Plus teleport弹窗到body） ===== */
@media (max-width: 768px) {
  /* 需键盘 → 全屏（默认） */
  .el-dialog { width: 100% !important; max-width: 100% !important; height: 100dvh !important; max-height: 100dvh !important; margin: 0 !important; border-radius: 0 !important; }
  .el-dialog__body { max-height: calc(100dvh - 110px) !important; overflow-y: auto !important; }
  /* 方案D：底部抽屉 85% 高屏（生词短语池） */
  @keyframes mob-slide-up { from { transform: translateY(100%); } to { transform: translateY(0); } }
  .el-dialog.mob-sheet-d { width: 100% !important; max-width: 100% !important; height: 85dvh !important; max-height: 85dvh !important; position: fixed !important; bottom: 0 !important; left: 0 !important; right: 0 !important; top: auto !important; margin: 0 !important; border-radius: 16px 16px 0 0 !important; overflow: hidden !important; transform: none !important; animation: mob-slide-up 0.28s ease-out; }
  .el-dialog.mob-sheet-d .el-dialog__header { padding-top: 4px !important; }
  .el-dialog.mob-sheet-d .el-dialog__header::before { content: ""; display: block; width: 32px; height: 4px; border-radius: 2px; background: #555; margin: 4px auto 8px; }
  .el-dialog.mob-sheet-d .el-dialog__body { max-height: calc(85dvh - 90px) !important; overflow-y: auto !important; }
  /* 不需键盘 → 底部抽屉 */
  .el-overlay:has(.mob-bottom-sheet) { align-items: flex-end !important; }
  .el-dialog.mob-bottom-sheet { width: 100% !important; max-width: 100% !important; height: auto !important; max-height: 75dvh !important; margin: 0 !important; border-radius: 16px 16px 0 0 !important; overflow: hidden !important; }
  .el-dialog.mob-bottom-sheet .el-dialog__header { padding-top: 4px !important; }
  .el-dialog.mob-bottom-sheet .el-dialog__header::before { content: ""; display: block; width: 32px; height: 4px; border-radius: 2px; background: #555; margin: 4px auto 8px; }
  .el-dialog.mob-bottom-sheet .el-dialog__body { max-height: calc(75dvh - 90px) !important; overflow-y: auto !important; }
  /* el-drawer 保持侧边滑入（方案G：列表类→侧边） */
  .el-drawer { width: 85% !important; }
  .el-drawer__body { overflow-y: auto !important; }
}

/* 方案H 全屏弹窗（需键盘：短语默写/提示词/图片导入） */
.mob-fullscreen-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: #0d0d0d; z-index: 3000; display: flex; flex-direction: column; }
.mob-fullscreen-hdr { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; border-bottom: 1px solid #1e1e1e; flex-shrink: 0; color: #888; font-size: 14px; cursor: pointer; }
.mob-fullscreen-title { font-size: 15px; font-weight: 700; color: #f8fafc; cursor: default; }
.mob-fullscreen-body { flex: 1; overflow-y: auto; padding: 14px; }
.mob-phrase-set-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.mob-phrase-set-bar .el-select { max-width: 65%; }
.mob-phrase-progress { display: flex; align-items: center; margin-bottom: 14px; }
.mob-phrase-card { background: #1a1a1a; border-radius: 14px; padding: 18px; margin-bottom: 12px; }
.mob-phrase-zh { font-size: 15px; color: #e0e0e0; text-align: center; margin-bottom: 14px; font-weight: 600; }
.mob-phrase-input-area { margin-bottom: 10px; }
.mob-phrase-label { font-size: 11px; color: #888; margin-bottom: 4px; }
.mob-phrase-answer { margin-top: 10px; }
.mob-phrase-original { font-size: 14px; color: #22C55E; background: #0a1a0a; padding: 10px; border-radius: 8px; line-height: 1.5; }
.mob-phrase-btns { display: flex; gap: 8px; margin-top: 14px; }
.mob-phrase-nav { display: flex; gap: 10px; justify-content: center; margin-top: 14px; }

:root { --ett-fs: 1; }
/* 弹窗/抽屉字体跟随全局缩放 */
.el-dialog__body { font-size: calc(14px * var(--ett-fs, 1)) !important; }
.el-dialog__title { font-size: calc(16px * var(--ett-fs, 1)) !important; }
.el-drawer__body { font-size: calc(14px * var(--ett-fs, 1)) !important; }
.el-drawer__title { font-size: calc(16px * var(--ett-fs, 1)) !important; }
.el-dialog__body .el-input__inner { font-size: calc(13px * var(--ett-fs, 1)) !important; }
.el-dialog__body .el-textarea__inner { font-size: calc(13px * var(--ett-fs, 1)) !important; }
.el-dialog__body .el-tag { font-size: calc(11px * var(--ett-fs, 1)) !important; }

html.ett-dark .el-divider--horizontal { border-top-color: #1e1e1e; }
html.ett-dark .el-input__wrapper { background: #141414; box-shadow: 0 0 0 1px #1e1e1e; }
html.ett-dark .el-input__inner { color: #f8fafc; }
html.ett-dark .el-textarea__inner { background: #141414; border-color: #1e1e1e; color: #f8fafc; }
html.ett-dark .el-select-dropdown { background: #141414; border: 1px solid #1e1e1e; }
html.ett-dark .el-select-dropdown__item { color: #c1c1c1; }
html.ett-dark .el-select-dropdown__item.hover,
html.ett-dark .el-select-dropdown__item:hover { background: #1a1008; }
html.ett-dark .el-select-dropdown__item.selected { color: #ff5f00; }
html.ett-dark .el-popper__arrow::before { background: #141414; border: 1px solid #1e1e1e; }

/* 暗色标签 — 降饱和度，不刺眼 */
html.ett-dark .el-tag { --el-tag-bg-color: #1a1a1a; --el-tag-border-color: #2a2a2a; --el-tag-text-color: #999; }
html.ett-dark .el-tag--success { --el-tag-bg-color: #0a1a0a; --el-tag-border-color: #1a3a1a; --el-tag-text-color: #4a8; }
html.ett-dark .el-tag--warning { --el-tag-bg-color: #1a1408; --el-tag-border-color: #2a2008; --el-tag-text-color: #b8860b; }
html.ett-dark .el-tag--danger { --el-tag-bg-color: #1a0a0a; --el-tag-border-color: #2a0a0a; --el-tag-text-color: #c66; }
html.ett-dark .el-tag--info { --el-tag-bg-color: #141414; --el-tag-border-color: #1e1e1e; --el-tag-text-color: #888; }
html.ett-dark .el-tag--primary { --el-tag-bg-color: #0a0a1a; --el-tag-border-color: #1a1a2a; --el-tag-text-color: #68a; }

/* 词根分析弹窗 */
.wa-dialog { --el-dialog-bg-color: #0d0d0d; }
.wa-dialog .el-dialog { background: #0d0d0d; border: 1px solid #1e1e1e; }
.wa-dialog .el-dialog__header { border-bottom: 1px solid #1e1e1e; }
.wa-dialog .el-dialog__title { color: #f8fafc; }
.wa-dialog .el-dialog__body { color: #c1c1c1; }

</style>
