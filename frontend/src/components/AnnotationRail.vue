<template>
  <button v-if="!desktopLayout && open" class="ink-drawer-scrim ink-rail-scrim" type="button" aria-label="关闭朱批栏" @click="close"></button>
  <aside id="annotation-rail" ref="railRef" class="ink-annotation-rail" :class="{ open }" aria-label="朱批与案卷信息" :aria-hidden="!desktopLayout && !open ? 'true' : undefined" :inert="!desktopLayout && !open">
    <div class="ink-rail-head"><div><span class="ink-eyebrow">朱批</span><h2>审查批注</h2></div><button v-if="!desktopLayout" class="ink-icon-button" type="button" aria-label="关闭朱批栏" @click="close">×</button></div>
    <ol v-if="visibleFindings.length" class="ink-annotations">
      <li v-for="finding in visibleFindings" :key="finding.id" :class="{ 'ink-annotation-critical': finding.severity === 'CRITICAL' }">
        <time>{{ finding.severity || 'INFO' }}</time><strong>{{ finding.title }}</strong><p>{{ finding.description }}</p><a v-if="primaryEvidence(finding)" :href="evidenceLink(finding)" @click="close(false)">定位证据</a>
      </li>
    </ol>
    <div v-else class="ink-inline-empty">当前案卷暂无朱批</div>
    <section class="ink-case-facts"><h3>案卷简目</h3><dl><div><dt>Run</dt><dd>#{{ runId || '—' }}</dd></div><div><dt>Head</dt><dd>{{ shortHead }}</dd></div><div><dt>Finding</dt><dd>{{ findings.length }}</dd></div><div><dt>可阻断</dt><dd>{{ blockingCount }}</dd></div></dl></section>
    <blockquote>“所有阻断结论，必须能回到持久化证据。”<cite>— RepoSage 守门规范</cite></blockquote>
  </aside>
  <button v-if="!desktopLayout" ref="toggleRef" class="ink-rail-fab" type="button" :aria-expanded="String(open)" aria-controls="annotation-rail" @click="toggle">批 <i>{{ findings.length }}</i></button>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps({ findings: { type: Array, default: () => [] }, runId: Number, headSha: { type: String, default: '' } })
const open = ref(false)
const desktopLayout = ref(false)
const railRef = ref(null)
const toggleRef = ref(null)
let query = null
const visibleFindings = computed(() => props.findings.filter((finding) => finding.status !== 'rejected').slice(0, 4))
const blockingCount = computed(() => props.findings.filter((finding) => finding.blocking && finding.status !== 'rejected').length)
const shortHead = computed(() => props.headSha ? props.headSha.slice(0, 12) : '—')

function primaryEvidence(finding) { return (finding.evidence || [])[0] || null }
function evidenceLink(finding) {
  const evidence = primaryEvidence(finding)
  const path = evidence?.filePath || evidence?.path || ''
  const line = evidence?.lineStart || evidence?.line || 1
  return `#/agent?evidence=${encodeURIComponent(path)}:${line}`
}
function sync(event) { desktopLayout.value = event.matches; if (event.matches) close(false) }
function toggle() { open.value ? close() : (open.value = true) }
function close(returnFocus = true) { open.value = false; if (returnFocus) nextTick(() => toggleRef.value?.focus()) }
function focusables() { return [...railRef.value?.querySelectorAll('button:not(:disabled), a[href], [tabindex]:not([tabindex="-1"])') || []].filter((item) => !item.hidden) }
function onKeydown(event) {
  if (!open.value || desktopLayout.value) return
  if (event.key === 'Escape') return close()
  if (event.key !== 'Tab') return
  const items = focusables()
  if (!items.length) return
  const first = items[0]
  const last = items[items.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
watch(open, (value) => {
  document.body.style.overflow = value ? 'hidden' : ''
  if (value) nextTick(() => focusables()[0]?.focus())
})
onMounted(() => {
  query = window.matchMedia('(min-width: 1280px)')
  desktopLayout.value = query.matches
  query.addEventListener?.('change', sync)
  window.addEventListener('keydown', onKeydown)
})
onUnmounted(() => {
  query?.removeEventListener?.('change', sync)
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>
