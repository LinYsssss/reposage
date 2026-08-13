<template>
  <div class="ink-ambient" aria-hidden="true">
    <canvas ref="canvas" class="ink-ambient-canvas"></canvas>
    <div class="ink-grain"></div>
    <div class="ink-cloud ink-cloud-one"></div>
    <div class="ink-cloud ink-cloud-two"></div>
    <div class="ink-taiji"><span></span></div>
    <svg class="ink-mountain ink-mountain-far" viewBox="0 0 1440 900" preserveAspectRatio="none">
      <path d="M0 710 C150 650 225 690 325 590 C430 480 510 650 620 565 C760 455 840 650 960 555 C1085 455 1190 600 1440 470 L1440 900 L0 900Z" />
    </svg>
    <svg class="ink-mountain ink-mountain-near" viewBox="0 0 1440 900" preserveAspectRatio="none">
      <path d="M0 790 C170 650 280 790 410 675 C535 565 640 770 790 655 C940 545 1105 705 1440 560 L1440 900 L0 900Z" />
    </svg>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps({ staticMode: { type: Boolean, default: false } })
const canvas = ref(null)
const reduced = typeof window === 'undefined' ? { matches: true } : window.matchMedia('(prefers-reduced-motion: reduce)')
const coarse = typeof window === 'undefined' ? { matches: true } : window.matchMedia('(pointer: coarse)')
let context = null
let frame = 0
let pointerFrame = 0
let width = 0
let height = 0
let pointerX = 0
let pointerY = 0
let particles = []

function allowed() {
  return !props.staticMode && !reduced.matches && !coarse.matches && !document.hidden && document.hasFocus()
}

function resize() {
  if (!canvas.value || !context) return
  const ratio = Math.min(window.devicePixelRatio || 1, 1.5)
  width = window.innerWidth
  height = window.innerHeight
  canvas.value.width = Math.round(width * ratio)
  canvas.value.height = Math.round(height * ratio)
  canvas.value.style.width = `${width}px`
  canvas.value.style.height = `${height}px`
  context.setTransform(ratio, 0, 0, ratio, 0, 0)
  const count = Math.max(30, Math.min(64, Math.round(width / 25)))
  particles = Array.from({ length: count }, (_, index) => ({
    x: Math.random() * width,
    y: Math.random() * height,
    r: 0.8 + Math.random() * 2.4,
    vx: -0.035 + Math.random() * 0.07,
    vy: -0.055 - Math.random() * 0.08,
    alpha: 0.035 + Math.random() * 0.095,
    warm: index % 15 === 0,
  }))
  draw(false)
}

function draw(advance = true) {
  if (!context) return
  context.clearRect(0, 0, width, height)
  context.globalCompositeOperation = 'multiply'
  particles.forEach((particle) => {
    if (advance) {
      particle.x += particle.vx + pointerX * 0.015
      particle.y += particle.vy + pointerY * 0.01
      if (particle.y < -8) { particle.y = height + 8; particle.x = Math.random() * width }
      if (particle.x < -8) particle.x = width + 8
      if (particle.x > width + 8) particle.x = -8
    }
    context.beginPath()
    context.fillStyle = particle.warm
      ? `rgb(158 56 43 / ${particle.alpha * 0.65})`
      : `rgb(47 68 60 / ${particle.alpha})`
    context.arc(particle.x, particle.y, particle.r, 0, Math.PI * 2)
    context.fill()
  })
}

function animate() {
  frame = 0
  if (!allowed()) return draw(false)
  draw(true)
  frame = requestAnimationFrame(animate)
}

function sync() {
  if (frame) cancelAnimationFrame(frame)
  frame = 0
  if (allowed()) frame = requestAnimationFrame(animate)
  else draw(false)
}

function resetPointer() {
  pointerX = 0
  pointerY = 0
  document.documentElement.style.setProperty('--ink-pointer-x', '0')
  document.documentElement.style.setProperty('--ink-pointer-y', '0')
}

function onPointerMove(event) {
  if (!allowed()) return
  pointerX = (event.clientX / window.innerWidth - 0.5) * 2
  pointerY = (event.clientY / window.innerHeight - 0.5) * 2
  if (pointerFrame) return
  pointerFrame = requestAnimationFrame(() => {
    pointerFrame = 0
    if (!allowed()) return resetPointer()
    document.documentElement.style.setProperty('--ink-pointer-x', pointerX.toFixed(3))
    document.documentElement.style.setProperty('--ink-pointer-y', pointerY.toFixed(3))
  })
}

function onVisibilityChange() {
  resetPointer()
  sync()
}

onMounted(() => {
  context = canvas.value?.getContext('2d', { alpha: true })
  window.addEventListener('resize', resize, { passive: true })
  window.addEventListener('pointermove', onPointerMove, { passive: true })
  window.addEventListener('blur', onVisibilityChange)
  document.addEventListener('visibilitychange', onVisibilityChange)
  reduced.addEventListener?.('change', onVisibilityChange)
  coarse.addEventListener?.('change', onVisibilityChange)
  resize()
  sync()
})

onUnmounted(() => {
  if (frame) cancelAnimationFrame(frame)
  if (pointerFrame) cancelAnimationFrame(pointerFrame)
  window.removeEventListener('resize', resize)
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('blur', onVisibilityChange)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  reduced.removeEventListener?.('change', onVisibilityChange)
  coarse.removeEventListener?.('change', onVisibilityChange)
  resetPointer()
})

watch(() => props.staticMode, sync)
</script>
