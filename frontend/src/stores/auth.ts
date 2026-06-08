import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as requestLogin, type LoginRequest } from '../api/auth'
import { getAccessToken, removeAuthTokens, setAuthTokens } from '../utils/authToken'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(getAccessToken())
  const isAuthenticated = computed(() => Boolean(accessToken.value))

  async function login(payload: LoginRequest) {
    const tokens = await requestLogin(payload)

    setAuthTokens(tokens.accessToken, tokens.refreshToken)
    accessToken.value = tokens.accessToken
  }

  function logout() {
    removeAuthTokens()
    accessToken.value = null
  }

  return {
    accessToken,
    isAuthenticated,
    login,
    logout,
  }
})
