<template>
  <section class="ink-panel ink-findings-panel" aria-labelledby="findingsTitle">
    <div class="ink-panel-head"><div><span class="ink-eyebrow">朱批清单</span><h2 id="findingsTitle">发现 {{ findings.length }} 项 · {{ blockingCount }} 项需立即处理</h2></div><span class="ink-muted">仅持久化证据可用于阻断</span></div>
    <div v-if="findings.length" class="ink-finding-list">
      <article v-for="finding in findings" :key="finding.id" class="ink-finding" :class="{ 'ink-finding-rejected': isRejected(finding) }">
        <div class="ink-finding-head">
          <div class="ink-finding-title"><span class="ink-severity" :class="`ink-severity-${String(finding.severity || 'NONE').toLowerCase()}`">{{ severityLabel(finding.severity) }}</span><strong>{{ finding.title }}</strong><span v-if="isRejected(finding)" class="ink-status-note">已否决</span></div>
          <span class="ink-confidence">置信度 {{ pct(finding.confidence) }}</span>
        </div>
        <p>{{ finding.description }}</p>
        <p v-if="isRejected(finding)" class="ink-alert ink-alert-warning">验证器已否决：{{ finding.rejectionReason || '未给出原因' }}——不参与阻断。</p>
        <div class="ink-finding-meta"><span>{{ (finding.evidence || []).length }} 条证据</span><span :class="finding.blocking ? 'ink-meta-blocking' : ''">{{ finding.blocking ? '可阻断' : '不阻断' }}</span><span v-if="finding.decisionReason">{{ finding.decisionReason }}</span></div>
        <details v-if="(finding.evidence || []).length" class="ink-evidence-drawer" @keydown.esc="closeDrawer">
          <summary>展开证据与 citation</summary>
          <ul>
            <li v-for="e in finding.evidence || []" :key="e.contentHash || e.reference" :data-evidence-path="e.filePath || e.path || ''"><code>{{ e.reference || e.filePath }}</code><span>{{ e.excerpt || e.sourceVersion || '已记录 citation' }}</span><a v-if="e.filePath || e.path" :href="fileLink(e)">定位到 Diff</a></li>
          </ul>
        </details>
        <p v-if="!(finding.evidence || []).length && !isRejected(finding)" class="ink-alert ink-alert-warning">缺少证据：该 Finding 不应阻断合并。</p>
      </article>
    </div>
    <div v-else class="ink-inline-empty">当前 head 没有 Finding</div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ findings: { type: Array, default: () => [] } })
const blockingCount = computed(() => props.findings.filter((finding) => finding.blocking && !isRejected(finding)).length)

function pct(value) { return value == null ? '-' : `${Math.round(value * 100)}%` }
function isRejected(finding) { return finding.status === 'rejected' }
function severityLabel(value) { return { CRITICAL: '危', HIGH: '高', MEDIUM: '中', LOW: '低', INFO: '讯' }[value] || value || '—' }
function fileLink(evidence) {
  const path = evidence.filePath || evidence.path || ''
  const line = evidence.lineStart || evidence.line || 1
  return `#/agent?evidence=${encodeURIComponent(path)}:${line}`
}
function closeDrawer(event) { event.target.closest('details')?.removeAttribute('open') }
</script>
