<template>
  <div class="ink-agent-workspace" aria-live="polite">
    <section class="ink-risk-strip" aria-label="风险摘要">
      <article class="ink-risk-score"><span>风险评分</span><strong>{{ riskScore }}</strong><small>{{ patchStateLabel }}</small></article>
      <article><span class="ink-severity ink-severity-critical">危</span><div><strong>{{ criticalFindings }}</strong><small>Critical</small></div></article>
      <article><span class="ink-severity ink-severity-high">高</span><div><strong>{{ highFindings }}</strong><small>High</small></div></article>
      <article><span class="ink-severity ink-severity-medium">中</span><div><strong>{{ mediumFindings }}</strong><small>Medium</small></div></article>
      <article><span class="ink-severity ink-severity-low">低</span><div><strong>{{ lowFindings }}</strong><small>Low</small></div></article>
      <article class="ink-run-health"><span>当前状态</span><strong><i></i>{{ patchStateLabel }}</strong><small>证据已同步</small></article>
    </section>
    <section class="ink-run-summary" aria-label="Agent Run 摘要"><div><span>Agent Run</span><strong>#{{ agentRunId }}</strong></div><div><span>Head SHA</span><code>{{ shortSha }}</code></div><div><span>完成步骤</span><strong>{{ completedSteps }}/{{ timeline.length }}</strong></div><div><span>有效 Findings</span><strong>{{ activeFindings }}</strong></div></section>
    <AgentTimeline :steps="timeline" />
    <AgentFindings :findings="findings" />
    <PatchDiffViewer :patch-content="patch?.patchContent" :validation-log="patch?.validationLog" :download-url="patch?.downloadUrl" />
    <PatchApprovalPanel :project-id="projectId" :agent-run-id="agentRunId" :patch="patch" :current-head-sha="currentHeadSha" @decided="$emit('decided', $event)" @error="$emit('error', $event)" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { statusLabel } from '../../utils/labels.js'
import AgentTimeline from './AgentTimeline.vue'
import AgentFindings from './AgentFindings.vue'
import PatchDiffViewer from './PatchDiffViewer.vue'
import PatchApprovalPanel from './PatchApprovalPanel.vue'

const props = defineProps({ projectId: Number, agentRunId: Number, currentHeadSha: String, timeline: { type: Array, default: () => [] }, findings: { type: Array, default: () => [] }, patch: Object })
defineEmits(['decided', 'error'])
const completedSteps = computed(() => props.timeline.filter((step) => ['SUCCEEDED', 'COMPLETED', 'SUCCESS'].includes(step.status)).length)
const activeFindings = computed(() => props.findings.filter((finding) => finding.status !== 'rejected').length)
const shortSha = computed(() => props.currentHeadSha ? props.currentHeadSha.slice(0, 12) : '-')
const patchState = computed(() => props.patch?.status || 'RUNNING')
const patchStateLabel = computed(() => statusLabel(patchState.value))
const riskScore = computed(() => Math.min(99, 58 + props.findings.filter((finding) => finding.blocking && finding.status !== 'rejected').length * 6))
const countBySeverity = (severity) => computed(() => props.findings.filter((finding) => finding.severity === severity && finding.status !== 'rejected').length)
const criticalFindings = countBySeverity('CRITICAL')
const highFindings = countBySeverity('HIGH')
const mediumFindings = countBySeverity('MEDIUM')
const lowFindings = countBySeverity('LOW')
</script>
