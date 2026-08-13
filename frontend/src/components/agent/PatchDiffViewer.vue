<template>
  <section class="ink-panel ink-diff-panel" aria-labelledby="diffTitle">
    <div class="ink-panel-head"><div><span class="ink-eyebrow">证据与 Diff</span><h2 id="diffTitle">候选 Patch</h2></div><a v-if="downloadUrl" class="ink-link-button" :href="downloadUrl" download>下载 Patch</a></div>
    <div v-if="lines.length" class="ink-diff-scroll" role="region" aria-label="Patch unified diff" tabindex="0">
      <div v-for="(line, index) in lines" :key="index" class="ink-diff-row" :class="`ink-diff-${line.cls || 'context'}`" :data-diff-file="line.file || null" :data-diff-line="line.newNo || null"><span class="ink-diff-number" aria-hidden="true">{{ line.newNo }}</span><code>{{ line.text || ' ' }}</code></div>
    </div>
    <div v-else class="ink-inline-empty">暂无 Patch</div>
    <details class="ink-validation-log"><summary>验证日志</summary><pre>{{ validationLog || '暂无验证日志' }}</pre></details>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { diffLines } from '../../utils/labels.js'

const props = defineProps({ patchContent: { type: String, default: '' }, validationLog: { type: String, default: '' }, downloadUrl: { type: String, default: '' } })
const lines = computed(() => {
  let file = ''
  return diffLines(props.patchContent || '').map((line) => {
    if (line.text.startsWith('+++ b/')) file = line.text.slice(6).trim()
    else if (line.text.startsWith('+++ ')) file = line.text.slice(4).trim()
    return { ...line, file: line.cls === 'meta' || line.cls === 'hunk' ? '' : file }
  })
})
</script>
