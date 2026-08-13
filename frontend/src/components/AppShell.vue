<template>
  <div class="ink-app-shell" :class="{ 'ink-static-mode': staticMode }">
    <InkAmbientScene :static-mode="staticMode" />
    <button v-if="navOpen" class="ink-drawer-scrim" type="button" aria-label="关闭主导航" @click="closeNav"></button>

    <aside id="main-navigation" class="ink-sidebar" :class="{ open: navOpen }" aria-label="案卷与主导航" :aria-hidden="mobileLayout && !navOpen ? 'true' : undefined" :inert="mobileLayout && !navOpen">
      <div class="ink-brand-row">
        <a class="ink-brand" href="#/dashboard" aria-label="RepoSage 墨境审查院">
          <span class="ink-brand-mark" aria-hidden="true">睿</span>
          <span><strong>RepoSage</strong><small>墨境审查院</small></span>
        </a>
        <button v-if="mobileLayout" class="ink-icon-button" type="button" aria-label="关闭主导航" @click="closeNav">×</button>
      </div>

      <div class="ink-eyebrow">案卷索引</div>
      <label class="ink-search"><span class="sr-only">搜索案卷</span><span aria-hidden="true">⌕</span><input type="search" placeholder="搜索案卷、仓库…" /></label>

      <nav class="ink-nav" aria-label="主导航">
        <button v-for="item in navItems" :key="item.name" class="ink-nav-item" :class="{ active: current === item.name }" type="button" :disabled="item.requiresProject && !activeProject" :aria-current="current === item.name ? 'page' : undefined" @click="navigate(item.name)">
          <span class="ink-nav-glyph" aria-hidden="true">{{ item.glyph }}</span>
          <span>{{ item.label }}</span>
          <i v-if="item.badge">{{ item.badge }}</i>
        </button>
      </nav>

      <section v-if="activeProject" class="ink-active-case" aria-labelledby="activeProjectTitle">
        <div class="ink-section-label"><span id="activeProjectTitle">当前案卷</span><b>已同步</b></div>
        <h2>{{ activeProject.name }}</h2>
        <dl><div><dt>分支</dt><dd>{{ activeProject.defaultBranch || 'main' }}</dd></div><div><dt>项目</dt><dd>{{ activeProject.projectId }}</dd></div></dl>
      </section>

      <div class="ink-sidebar-foot">
        <div class="ink-user-chip">
          <span class="ink-avatar" aria-hidden="true">{{ userInitial }}</span>
          <span><strong>{{ me.nickname || me.username || '审查员' }}</strong><small>{{ me.role || 'Review Member' }}</small></span>
        </div>
        <button class="ink-button ink-button-quiet" type="button" @click="emit('logout')">退出登录</button>
        <small class="ink-rule-note">书院守门规则 · v1.0</small>
      </div>
    </aside>

    <main class="ink-main">
      <header class="ink-topbar">
        <div class="ink-topbar-left">
          <button ref="navToggleRef" class="ink-icon-button ink-mobile-only" type="button" aria-label="打开案卷导航" :aria-expanded="String(navOpen)" aria-controls="main-navigation" @click="openNav">☰</button>
          <div class="ink-context"><span>当前案卷</span><strong>{{ tabTitle }}</strong><small v-if="activeProject">{{ activeProject.name }} · <code>{{ activeProject.defaultBranch || 'main' }}</code></small></div>
        </div>
        <div class="ink-top-actions">
          <span class="ink-connection"><i></i>实时同步</span>
          <button class="ink-text-button ink-motion-toggle" type="button" :aria-pressed="String(staticMode)" :aria-label="staticMode ? '启用水墨动效' : '切换到静态墨境'" @click="toggleStatic"><span class="ink-motion-label-full">{{ staticMode ? '启用水墨' : '静态墨境' }}</span><span class="ink-motion-label-short">动效</span></button>
          <button class="ink-icon-button ink-notification-button" type="button" aria-label="通知，2 条未读">♧<i class="ink-notification-count">2</i></button>
          <span class="ink-avatar ink-avatar-dark" aria-label="当前用户">{{ userInitial }}</span>
          <button class="ink-button ink-button-primary ink-refresh" type="button" :disabled="busy.refresh" @click="emit('refresh')">{{ busy.refresh ? '刷新中…' : '刷新案卷' }}</button>
        </div>
      </header>

      <div class="ink-page-frame"><slot /></div>
    </main>

    <div v-if="confirmModal" class="ink-modal-backdrop" role="presentation" @click.self="dismiss">
      <section ref="modalRef" class="ink-modal" role="dialog" aria-modal="true" aria-labelledby="inkModalTitle" aria-describedby="inkModalBody">
        <span class="ink-seal ink-seal-cinnabar" aria-hidden="true">准</span>
        <h2 id="inkModalTitle">{{ modalView.title }}</h2>
        <p id="inkModalBody">{{ modalView.body }}</p>
        <div class="ink-modal-actions"><button class="ink-button" type="button" @click="dismiss">返回复核</button><button class="ink-button ink-button-primary" type="button" :disabled="busy.confirm" @click="run(confirmAction)">{{ busy.confirm ? '处理中…' : (modalView.confirmLabel || '确认落印') }}</button></div>
      </section>
    </div>

    <div v-if="toast.text" class="ink-toast" :class="`ink-toast-${toast.type || 'info'}`" role="status" aria-live="polite">{{ toast.text }}</div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import InkAmbientScene from './InkAmbientScene.vue'
import { useBusy } from '../composables/useBusy.js'
import { useConfirm } from '../composables/useConfirm.js'
import { useSession } from '../composables/useSession.js'
import { useToast } from '../composables/useToast.js'

const emit = defineEmits(['navigate', 'refresh', 'logout'])
const route = useRoute()
const { busy } = useBusy()
const { confirmModal, dismiss, confirmAction } = useConfirm()
const { me, activeProject } = useSession()
const { toast, toastMsg } = useToast()
const navOpen = ref(false)
const staticMode = ref(false)
const modalRef = ref(null)
const navToggleRef = ref(null)
const mobileLayout = ref(false)
let mobileQuery = null
const current = computed(() => (typeof route.name === 'string' ? route.name : 'dashboard'))
const userInitial = computed(() => (me.nickname || me.username || 'U').slice(0, 1).toUpperCase())
const tabTitles = { dashboard: '总览', projects: '项目', repository: '仓库', pullRequests: 'PR 工作流', knowledge: '知识库', reviews: '审查记录', agent: 'Agent 审查', aiLogs: 'AI 日志' }
const tabTitle = computed(() => tabTitles[current.value] || 'RepoSage')
const navItems = [
  { name: 'dashboard', label: '总览', glyph: '概' },
  { name: 'projects', label: '项目', glyph: '项' },
  { name: 'repository', label: '仓库', glyph: '仓' , requiresProject: true },
  { name: 'pullRequests', label: 'PR 工作流', glyph: '审', requiresProject: true },
  { name: 'knowledge', label: '知识库', glyph: '知', requiresProject: true },
  { name: 'reviews', label: '审查记录', glyph: '录', requiresProject: true, badge: '8' },
  { name: 'agent', label: 'Agent 审查', glyph: '巡', requiresProject: true, badge: '3' },
  { name: 'aiLogs', label: 'AI 日志', glyph: '志', requiresProject: true },
]
const modalView = ref({})
let modalReturnFocus = null

watch(confirmModal, (value) => {
  if (value) {
    modalReturnFocus = document.activeElement
    modalView.value = value
    nextTick(() => modalRef.value?.querySelector('button:last-child')?.focus())
  } else if (modalReturnFocus) {
    nextTick(() => modalReturnFocus?.focus())
  }
})
watch(navOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
  if (open) nextTick(() => document.querySelector('#main-navigation .ink-icon-button')?.focus())
})
watch(() => route.name, () => { navOpen.value = false })

function navigate(name) { emit('navigate', name); navOpen.value = false }
function openNav() { navOpen.value = true }
function closeNav(returnFocus = true) { navOpen.value = false; if (returnFocus) nextTick(() => navToggleRef.value?.focus()) }
function toggleStatic() {
  staticMode.value = !staticMode.value
  toastMsg(staticMode.value ? '已切换静态墨境' : '已启用太极水墨与墨粒动效', 'success')
}
function focusables(container) {
  return [...container?.querySelectorAll('button:not(:disabled), input:not(:disabled), a[href], [tabindex]:not([tabindex=\"-1\"])') || []].filter((item) => !item.hidden)
}

function onKeydown(event) {
  if (event.key === 'Escape' && confirmModal.value) return dismiss()
  if (event.key === 'Escape' && navOpen.value) return closeNav()
  if (event.key !== 'Tab') return
  const container = confirmModal.value ? modalRef.value : navOpen.value ? document.querySelector('#main-navigation') : null
  const items = focusables(container)
  if (!items.length) return
  const first = items[0]
  const last = items[items.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}

function syncMobileQuery(event) { mobileLayout.value = event.matches; if (!event.matches) closeNav(false) }

onMounted(() => {
  mobileQuery = window.matchMedia('(max-width: 767px)')
  mobileLayout.value = mobileQuery.matches
  mobileQuery.addEventListener?.('change', syncMobileQuery)
  window.addEventListener('keydown', onKeydown)
})
onUnmounted(() => {
  mobileQuery?.removeEventListener?.('change', syncMobileQuery)
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>
