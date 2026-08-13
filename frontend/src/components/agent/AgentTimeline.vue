<template>
  <section class="ink-panel ink-timeline-panel" aria-labelledby="timelineTitle">
    <div class="ink-panel-head"><div><span class="ink-eyebrow">审查进度</span><h2 id="timelineTitle">六步守门流程</h2></div><span class="ink-muted">{{ steps.length }} 个步骤</span></div>
    <ol v-if="steps.length" class="ink-timeline">
      <li v-for="step in steps" :key="step.id || step.sequenceNo" class="ink-timeline-step" :class="`ink-timeline-${String(step.status || '').toLowerCase()}`">
        <span class="ink-timeline-dot" aria-hidden="true">{{ marker(step.status) }}</span>
        <div><strong>{{ statusLabel(step.status) }}</strong><code>{{ step.stepType || 'STEP' }}</code><small>{{ step.outputSummary || '暂无输出摘要' }}</small></div>
      </li>
    </ol>
    <div v-else class="ink-inline-empty">暂无步骤记录</div>
  </section>
</template>

<script setup>
defineProps({ steps: { type: Array, default: () => [] } })

function statusLabel(value) {
  return { SUCCEEDED: '已完成', COMPLETED: '已完成', SUCCESS: '成功', RUNNING: '进行中', WAITING_EXTERNAL: '等待外部', FAILED: '失败', CANCELED: '已取消' }[value] || value || '待执行'
}

function marker(value) {
  if (['SUCCEEDED', 'COMPLETED', 'SUCCESS'].includes(value)) return '✓'
  if (value === 'RUNNING') return '析'
  if (value === 'FAILED') return '!'
  return '核'
}
</script>
