<template>
  <div class="ink-agent-layout">
  <div class="ink-agent-page">
    <header class="ink-page-head">
      <div><span class="ink-eyebrow">Agent Run #{{ agentRunId || '—' }}</span><h1>权限提升风险复核</h1><p>检查角色校验、越权路径与候选 Patch；仅持久化证据可用于阻断。</p></div>
      <div class="ink-head-actions"><button class="ink-button" type="button" :disabled="busy.agentRuns" @click="run(loadAgentRuns)">刷新案卷</button><button class="ink-button ink-button-primary" type="button" :disabled="!agentRunId || busy.agent" @click="run(loadAgentWorkspace)">{{ busy.agent ? '加载中…' : '加载审查' }}</button></div>
    </header>

    <section v-if="agentRunDetail" class="ink-state-row" :class="`ink-state-${String(agentRunDetail.status || '').toLowerCase()}`" role="status">
      <div><strong>{{ statusLabel(agentRunDetail.status) }}</strong><span>{{ agentPolling ? '正在自动刷新持久化状态' : (agentRunDetail.terminal ? '运行已结束' : '自动刷新已暂停') }}</span></div>
      <div class="ink-state-actions"><button v-if="!agentRunDetail.terminal" class="ink-button ink-button-quiet" type="button" :disabled="busy.agentControl" @click="askCancelAgentRun">取消运行</button><button v-if="['FAILED', 'TIMED_OUT'].includes(agentRunDetail.status)" class="ink-button" type="button" :disabled="busy.agentControl" @click="askRetryAgentRun">重试失败步骤</button></div>
    </section>

    <section class="ink-panel ink-agent-selector" aria-labelledby="agentSelectorTitle">
      <div class="ink-panel-head"><div><span class="ink-eyebrow">案卷索引</span><h2 id="agentSelectorTitle">选择 Agent Run</h2></div><span class="ink-muted">{{ filteredAgentRuns.length }} 个匹配案卷</span></div>
      <div class="ink-agent-fields">
        <label class="ink-field"><span>Run 状态筛选</span><select v-model="agentRunFilter"><option value="ALL">全部（{{ agentRuns.length }}）</option><option value="ACTIVE">运行中（{{ agentRunCounts.active }}）</option><option value="WAITING">等待审批（{{ agentRunCounts.waiting }}）</option><option value="FAILED">失败（{{ agentRunCounts.failed }}）</option><option value="DONE">已完成（{{ agentRunCounts.done }}）</option></select></label>
        <label class="ink-field"><span>最近 Agent Run</span><select v-model="agentRunId" @change="selectAgentRun"><option :value="null">请选择</option><option v-for="runItem in filteredAgentRuns" :key="runItem.id" :value="runItem.id">#{{ runItem.id }} · {{ statusLabel(runItem.status) }} · {{ shortCommit(runItem.headSha) }}</option></select></label>
        <label class="ink-field"><span>当前 Head SHA</span><input v-model="agentHeadSha" autocomplete="off" spellcheck="false" /></label>
      </div>
      <p v-if="!filteredAgentRuns.length" class="ink-field-hint">当前筛选没有匹配项；<button class="ink-link-button" type="button" @click="agentRunFilter = 'ALL'">显示全部</button></p>
    </section>

    <AgentReviewWorkspace v-if="agentRunId && agentPatch" :project-id="activeProject?.projectId" :agent-run-id="agentRunId" :current-head-sha="agentHeadSha" :timeline="agentTimeline" :findings="agentFindings" :patch="agentPatch" @decided="onPatchDecided" @error="onPatchError" />
    <section v-else class="ink-empty-state"><span class="ink-seal" aria-hidden="true">巡</span><h2>等待进入审查工作面</h2><p>输入 Agent Run 与当前 Head SHA 后加载候选 Patch。若当前项目还没有案卷，请先从项目或 PR 工作流进入。</p></section>
  </div>
  <AnnotationRail :findings="agentFindings" :run-id="agentRunId" :head-sha="agentHeadSha" />
  </div>
</template>

<script setup>
import AgentReviewWorkspace from '../components/agent/AgentReviewWorkspace.vue'
import AnnotationRail from '../components/AnnotationRail.vue'
import { shortCommit } from '../utils/format.js'
import { statusLabel } from '../utils/labels.js'
import { useBusy } from '../composables/useBusy.js'
import { useSession } from '../composables/useSession.js'
import { useAgentWorkspace } from '../composables/useAgentWorkspace.js'

const { busy, run } = useBusy()
const { activeProject } = useSession()
const { agentRuns, agentRunId, agentRunFilter, agentHeadSha, agentTimeline, agentFindings, agentPatch, agentRunDetail, agentPolling, filteredAgentRuns, agentRunCounts, loadAgentWorkspace, loadAgentRuns, selectAgentRun, onPatchDecided, onPatchError, askCancelAgentRun, askRetryAgentRun } = useAgentWorkspace()
</script>
