<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const keepLoggedIn = ref(false)
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
        <span class="brand-mark">AD</span>
        <strong>AutoDeploy<span>Hub</span></strong>
      </div>

      <div class="login-copy">
        <h1>배포 자동화 플랫폼</h1>
        <p>더 빠르고, 더 안전한 배포. 자동화로 운영을 간편하게.</p>
      </div>

      <div class="launch-visual">
        <div class="server"></div>
        <div class="rocket">▲</div>
        <div class="server"></div>
      </div>

      <div class="feature-grid">
        <div>
          <span>01</span>
          <strong>자동 배포</strong>
          <small>Blue/Green</small>
        </div>
        <div>
          <span>02</span>
          <strong>실시간 모니터링</strong>
          <small>SSE 로그</small>
        </div>
        <div>
          <span>03</span>
          <strong>알림 지원</strong>
          <small>Slack, Discord, Email</small>
        </div>
        <div>
          <span>04</span>
          <strong>권한 관리</strong>
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
          <span>이메일</span>
          <input v-model="email" autocomplete="email" required type="email" placeholder="이메일을 입력하세요" />
        </label>

        <label class="field">
          <span>비밀번호</span>
          <input
            v-model="password"
            autocomplete="current-password"
            required
            type="password"
            placeholder="비밀번호를 입력하세요"
          />
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

        <button class="secondary-button wide" type="button">GitHub 계정으로 로그인</button>

        <p class="signup">계정이 없으신가요? <a href="/">회원가입</a></p>
      </form>
    </section>
  </main>
</template>
