<template>
  <div class="ink-login-page">
    <InkAmbientScene :static-mode="staticMode" />
    <div class="ink-login-shell">
      <section class="ink-login-story" aria-labelledby="loginStoryTitle">
        <a class="ink-brand" href="#/dashboard" aria-label="RepoSage 墨境书院"><span class="ink-brand-mark" aria-hidden="true">睿</span><span><strong>RepoSage</strong><small>墨境审查院</small></span></a>
        <div class="ink-login-copy">
          <span class="ink-eyebrow">AI CODE REVIEW GATEKEEPER</span>
          <div class="ink-login-taiji" aria-hidden="true"><span></span></div>
          <h1 id="loginStoryTitle">入境观心<br />落墨有据</h1>
          <p>让每一条风险结论回到可验证证据。太极水墨随环境缓慢流动，真正的审查信息始终清晰、稳定、可追溯。</p>
        </div>
        <p class="ink-login-note">“静处见风险，动处循证据。”<br />RepoSage 将 Agent 过程、Finding 与 Patch 审批收进同一案卷。</p>
      </section>

      <section class="ink-login-panel-wrap">
        <main class="ink-login-panel" aria-labelledby="loginTitle">
          <span class="ink-eyebrow">书院门禁</span>
          <h2 id="loginTitle">登录审查工作台</h2>
          <p class="ink-login-sub">使用组织账户进入。凭据仅通过 HttpOnly Cookie 维持。</p>
          <p v-if="errorMessage" class="ink-alert ink-alert-error" role="alert" aria-live="assertive">{{ errorMessage }}</p>
          <form class="ink-form" @submit.prevent="login" novalidate>
            <label class="ink-field"><span>组织账号</span><input v-model.trim="auth.username" name="username" type="text" autocomplete="username" placeholder="name@company.com" required :aria-invalid="String(Boolean(errorMessage && !auth.username))" /></label>
            <label class="ink-field"><span>密码</span><input v-model="auth.password" name="password" type="password" autocomplete="current-password" placeholder="请输入密码" required :aria-invalid="String(Boolean(errorMessage && !auth.password))" /></label>
            <div class="ink-login-options"><label><input v-model="remember" type="checkbox" /> 保持登录</label><button class="ink-link-button" type="button" @click="toastMsg('请联系组织管理员恢复账号。', 'info')">账号恢复</button></div>
            <button class="ink-button ink-button-primary ink-submit" type="submit" :disabled="busy.auth" :aria-busy="String(busy.auth)">{{ busy.auth ? '正在验明身份…' : '入境审查' }}</button>
          </form>
          <div class="ink-login-foot"><button class="ink-text-button" type="button" :aria-pressed="String(staticMode)" @click="toggleStatic">{{ staticMode ? '启用太极水墨' : '静态墨境' }}</button><br />受组织 SSO 与最小权限策略保护</div>
        </main>
      </section>
    </div>
    <div v-if="toast.text" class="ink-toast" :class="`ink-toast-${toast.type || 'info'}`" role="status" aria-live="polite">{{ toast.text }}</div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { api, initCsrf } from '../api/client.js'
import InkAmbientScene from '../components/InkAmbientScene.vue'
import { useBusy } from '../composables/useBusy.js'
import { useSession } from '../composables/useSession.js'
import { useToast } from '../composables/useToast.js'

const emit = defineEmits(['authenticated'])
const { busy } = useBusy()
const { authenticated } = useSession()
const { toast, toastMsg } = useToast()
const auth = reactive({ username: '', password: '' })
const remember = ref(false)
const staticMode = ref(false)
const errorMessage = ref('')

async function login() {
  if (!auth.username || !auth.password) {
    errorMessage.value = '账号或密码不能为空，请补全后重试。'
    return
  }
  errorMessage.value = ''
  busy.auth = true
  try {
    await api('/auth/login', { method: 'POST', body: JSON.stringify(auth) })
    await initCsrf()
    authenticated.value = true
    emit('authenticated')
  } catch (error) {
    errorMessage.value = error?.message || '登录失败，请检查组织账号后重试。'
    toastMsg(errorMessage.value, 'error')
  } finally {
    busy.auth = false
  }
}

function toggleStatic() {
  staticMode.value = !staticMode.value
  toastMsg(staticMode.value ? '已切换静态墨境' : '已启用太极水墨与墨粒动效', 'success')
}
</script>
