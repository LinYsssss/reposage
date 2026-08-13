<template>
  <section class="ink-panel ink-approval-panel" aria-labelledby="approvalTitle">
    <div class="ink-panel-head"><div><span class="ink-eyebrow">人工落款</span><h2 id="approvalTitle">Patch 审批</h2></div><span v-if="approvable" class="ink-gate-pass">四道校验已通过</span></div>
    <div class="ink-gate-grid"><span>Apply <b>{{ gate(patch?.applyStatus) }}</b></span><span>Build <b>{{ gate(patch?.buildStatus) }}</b></span><span>Test <b>{{ gate(patch?.testStatus) }}</b></span><span>Scan <b>{{ gate(patch?.scanStatus) }}</b></span></div>
    <p v-if="!approvable" class="ink-alert ink-alert-warning">Patch 未通过 apply/目标消失校验或已过期，不能批准。</p>
    <label class="ink-field"><span>审批意见</span><textarea v-model="comment" rows="3" placeholder="记录批准或退回依据…"></textarea></label>
    <div class="ink-approval-actions"><button class="ink-button ink-button-quiet" type="button" :disabled="busy" @click="decide('REJECTED')">退回修改</button><button class="ink-button ink-button-primary" type="button" :disabled="!approvable || busy" @click="openApproval">批准并落印</button></div>
  </section>

  <div v-if="confirmOpen" class="ink-modal-backdrop" role="presentation" @click.self="closeApproval">
    <section ref="dialogRef" class="ink-modal" role="dialog" aria-modal="true" aria-labelledby="patchApprovalDialogTitle" aria-describedby="patchApprovalDialogBody" @keydown="onDialogKeydown">
      <span class="ink-seal ink-seal-cinnabar" aria-hidden="true">准</span>
      <h2 id="patchApprovalDialogTitle">确认批准候选 Patch？</h2>
      <p id="patchApprovalDialogBody">批准后将记录当前 Head SHA 与审批意见，并写入持久化审计记录。</p>
      <div class="ink-modal-actions"><button class="ink-button" type="button" @click="closeApproval">返回复核</button><button class="ink-button ink-button-primary" type="button" :disabled="busy" @click="confirmApproval">{{ busy ? '正在落印…' : '确认落印' }}</button></div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { submitPatchApproval } from '../../api/patchApproval.js'
import { canApprovePatch } from './patchApprovalPolicy.js'

const props = defineProps({ projectId: Number, agentRunId: Number, patch: Object, currentHeadSha: String })
const emit = defineEmits(['decided', 'error'])
const comment = ref('')
const busy = ref(false)
const confirmOpen = ref(false)
const dialogRef = ref(null)
let returnFocus = null
const approvable = computed(() => canApprovePatch(props.patch))
function gate(value) { return value === 'SUCCEEDED' ? '通过' : value || '待执行' }
function openApproval(event) {
  returnFocus = event.currentTarget
  confirmOpen.value = true
  nextTick(() => dialogRef.value?.querySelector('button:last-child')?.focus())
}
function closeApproval() {
  confirmOpen.value = false
  nextTick(() => returnFocus?.focus())
}
function onDialogKeydown(event) {
  if (event.key === 'Escape') return closeApproval()
  if (event.key !== 'Tab') return
  const items = [...dialogRef.value?.querySelectorAll('button:not(:disabled)') || []]
  const first = items[0]
  const last = items[items.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
async function confirmApproval() {
  if (await decide('APPROVED')) closeApproval()
}
async function decide(decision) {
  busy.value = true
  try {
    const value = await submitPatchApproval({ projectId: props.projectId, agentRunId: props.agentRunId, patchId: props.patch.id, currentHeadSha: props.currentHeadSha, decision, comment: comment.value })
    emit('decided', value)
    return true
  } catch (error) {
    emit('error', error)
    return false
  } finally {
    busy.value = false
  }
}
</script>
