<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const name = ref('')
const email = ref('')
const password = ref('')
const passwordConfirm = ref('')
const isSubmitting = ref(false)
const errorMessage = ref('')
const PASSWORD_MIN_LENGTH = 8
const PASSWORD_MAX_LENGTH = 64

const canSubmit = computed(() => {
  return name.value.trim() && email.value.trim() && password.value && passwordConfirm.value
})

async function handleSignup() {
  errorMessage.value = ''

  if (password.value !== passwordConfirm.value) {
    errorMessage.value = '비밀번호가 일치하지 않습니다.'
    return
  }

  if (password.value.length < PASSWORD_MIN_LENGTH || password.value.length > PASSWORD_MAX_LENGTH) {
    errorMessage.value = `비밀번호는 ${PASSWORD_MIN_LENGTH}자 이상 ${PASSWORD_MAX_LENGTH}자 이하로 입력해주세요.`
    return
  }

  isSubmitting.value = true

  try {
    await authStore.signup({
      name: name.value.trim(),
      email: email.value.trim(),
      password: password.value,
    })

    await router.push({
      name: 'login',
      query: {
        registered: '1',
        email: email.value.trim(),
      },
    })
  } catch {
    errorMessage.value = '회원가입에 실패했습니다. 입력 정보를 확인해주세요.'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <form class="login-card signup-card" @submit.prevent="handleSignup">
      <div class="form-heading">
        <h2>회원가입</h2>
        <p>AutoDeployHub 계정을 생성하세요.</p>
      </div>

      <label class="field">
        <span class="field-label">이름</span>
        <input v-model="name" name="input-name" autocomplete="name" required type="text" placeholder="이름을 입력하세요" />
      </label>

      <label class="field">
        <span class="field-label">이메일</span>
        <input v-model="email" name="input-email" autocomplete="email" required type="email" placeholder="이메일을 입력하세요" />
      </label>

      <label class="field">
        <span class="field-label">비밀번호</span>
        <input
          v-model="password"
          name="input-password"
          autocomplete="new-password"
          required
          :minlength="PASSWORD_MIN_LENGTH"
          :maxlength="PASSWORD_MAX_LENGTH"
          type="password"
          placeholder="비밀번호를 입력하세요"
        />
      </label>

      <label class="field">
        <span class="field-label">비밀번호 확인</span>
        <input
          v-model="passwordConfirm"
          name="input-password-confirm"
          autocomplete="new-password"
          required
          :minlength="PASSWORD_MIN_LENGTH"
          :maxlength="PASSWORD_MAX_LENGTH"
          type="password"
          placeholder="비밀번호를 다시 입력하세요"
        />
      </label>

      <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

      <button class="primary-button wide submit-button" type="submit" :disabled="isSubmitting || !canSubmit">
        {{ isSubmitting ? '가입 중...' : '회원가입' }}
      </button>

      <p class="signup">이미 계정이 있으신가요? <RouterLink to="/login">로그인</RouterLink></p>
    </form>
  </main>
</template>
