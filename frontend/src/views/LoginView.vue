<script setup lang="ts">
import {
  BellIcon,
  ChartBarIcon,
  LockClosedIcon,
  RocketLaunchIcon,
} from '@heroicons/vue/24/solid'
import {
  EnvelopeIcon,
  EyeSlashIcon,
  EyeIcon,
  LockClosedIcon as LockClosedOutlineIcon,
} from '@heroicons/vue/24/outline'
import { siGithub } from 'simple-icons'
import { computed, ref } from 'vue'
import deploymentHeroImage from '../assets/images/autodeployhub-hero-bg.png'
import logoIconImage from '../assets/images/autodeployhub-logo-icon.png'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const githubIconPath = siGithub.path

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const keepLoggedIn = ref(false)
const showPassword = ref(false)
const isSubmitting = ref(false)
const errorMessage = ref('')

const redirectPath = computed(() => {
  const redirect = route.query.redirect

  return typeof redirect === 'string' ? redirect : '/projects'
})

async function handleLogin() {
  errorMessage.value = ''
  isSubmitting.value = true

  try {
    await authStore.login({
      email: email.value,
      password: password.value,
    })

    await router.push(redirectPath.value)
  } catch {
    errorMessage.value = '이메일 또는 비밀번호를 확인해주세요.'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-hero" aria-label="AutoDeployHub 소개">
      <div class="login-logo">
        <span class="brand-cloud" aria-hidden="true">
          <img class="brand-cloud-icon" :src="logoIconImage" alt="" />
        </span>
        <strong>AutoDeploy<span>Hub</span></strong>
      </div>

      <div class="login-copy">
        <h1>배포 자동화 플랫폼</h1>
      </div>

      <div class="hero-stage">
        <div class="hero-caption">
          <p>
            <span>더 빠르고, 더 안전한 배포.</span>
            <span>자동화로 운영을 간편하게.</span>
          </p>
        </div>

        <div class="launch-visual" aria-hidden="true">
          <img :src="deploymentHeroImage" alt="" />
        </div>
      </div>

      <div class="feature-grid">
        <div class="feature-card">
          <span class="feature-icon" aria-hidden="true">
            <RocketLaunchIcon class="feature-svg" />
          </span>
          <strong>자동 배포</strong>
          <small>Blue/Green</small>
        </div>
        <div class="feature-card">
          <span class="feature-icon" aria-hidden="true">
            <ChartBarIcon class="feature-svg" />
          </span>
          <strong>실시간 모니터링</strong>
          <small>SSE 로그</small>
        </div>
        <div class="feature-card">
          <span class="feature-icon" aria-hidden="true">
            <BellIcon class="feature-svg" />
          </span>
          <strong>알림 지원</strong>
          <small>Slack, Discord, Email</small>
        </div>
        <div class="feature-card">
          <span class="feature-icon" aria-hidden="true">
            <LockClosedIcon class="feature-svg" />
          </span>
          <strong>보안 및 권한 관리</strong>
          <small>역할 기반 접근 제어</small>
        </div>
      </div>
    </section>

    <section class="login-panel" aria-label="로그인">
      <form class="login-card" @submit.prevent="handleLogin">
        <div class="form-heading">
          <h2>로그인</h2>
          <p>계정 정보를 입력하고 로그인하세요.</p>
        </div>

        <label class="field">
          <span class="field-label">이메일</span>
          <span class="input-shell">
            <EnvelopeIcon class="input-icon" aria-hidden="true" />
            <input v-model="email" autocomplete="email" required type="email" placeholder="이메일을 입력하세요" />
          </span>
        </label>

        <label class="field">
          <span class="field-label">비밀번호</span>
          <span class="input-shell">
            <LockClosedOutlineIcon class="input-icon" aria-hidden="true" />
            <input
              v-model="password"
              autocomplete="current-password"
              required
              :type="showPassword ? 'text' : 'password'"
              placeholder="비밀번호를 입력하세요"
            />
            <button
              class="ghost-icon-button"
              type="button"
              :aria-label="showPassword ? '비밀번호 숨기기' : '비밀번호 보기'"
              :aria-pressed="showPassword"
              @click="showPassword = !showPassword"
            >
              <EyeSlashIcon v-if="showPassword" class="eye-icon" aria-hidden="true" />
              <EyeIcon v-else class="eye-icon" aria-hidden="true" />
            </button>
          </span>
        </label>

        <div class="form-row">
          <label class="checkbox">
            <input v-model="keepLoggedIn" type="checkbox" />
            로그인 상태 유지
          </label>
          <a href="/">비밀번호를 잊으셨나요?</a>
        </div>

        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

        <button class="primary-button wide" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? '로그인 중...' : '로그인' }}
        </button>

        <div class="divider"><span>또는</span></div>

        <button class="secondary-button wide github-button" type="button">
          <svg class="github-mark" viewBox="0 0 24 24" aria-hidden="true">
            <path :d="githubIconPath" />
          </svg>
          GitHub 계정으로 로그인
        </button>

        <p class="signup">계정이 없으신가요? <a href="/">회원가입</a></p>
      </form>
    </section>
  </main>
</template>
