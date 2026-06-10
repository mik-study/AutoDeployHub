import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  login as requestLogin,
  logout as requestLogout,
  signup as requestSignup,
  type LoginRequest,
  type SignupRequest,
} from '../api/auth'
import { getAccessToken, removeAuthTokens, setAuthTokens } from '../utils/authToken'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(getAccessToken())
  const isAuthenticated = computed(() => Boolean(accessToken.value))

  async function login(payload: LoginRequest) {
    const tokens = await requestLogin(payload)

    setAuthTokens(tokens.accessToken, tokens.refreshToken)
    accessToken.value = tokens.accessToken
  }

  async function signup(payload: SignupRequest) {
    return requestSignup(payload)
  }

  async function logout() {
    try {
      await requestLogout()
    } finally {
      removeAuthTokens()
      accessToken.value = null
    }
  }

  function clearSession() {
    removeAuthTokens()
    accessToken.value = null
  }

  return {
    accessToken,
    isAuthenticated,
    login,
    signup,
    logout,
    clearSession,
  }
})
