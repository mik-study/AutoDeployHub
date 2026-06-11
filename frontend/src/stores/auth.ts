import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  login as requestLogin,
  logout as requestLogout,
  signup as requestSignup,
  type LoginResponse,
  type LoginRequest,
  type SignupRequest,
} from '../api/auth'
import { getAccessToken, removeAuthTokens, setAuthTokens } from '../utils/authToken'

const DEV_LOGIN_EMAIL = 'admin@test.com'
const DEV_LOGIN_PASSWORD = 'test1234'
const DEV_LOGIN_ROLE = 'Administrator'
const AUTH_USER_KEY = 'autodeploy.user'

export interface AuthUser {
  email: string
  name: string
  role: string
}

function getStoredUser() {
  const value = localStorage.getItem(AUTH_USER_KEY)

  if (!value) {
    return null
  }

  try {
    return JSON.parse(value) as AuthUser
  } catch {
    localStorage.removeItem(AUTH_USER_KEY)
    return null
  }
}

function setStoredUser(user: AuthUser) {
  localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user))
}

function removeStoredUser() {
  localStorage.removeItem(AUTH_USER_KEY)
}

function createUserFromLoginResponse(response: LoginResponse, fallbackEmail: string): AuthUser {
  const email = response.user.email || fallbackEmail

  return {
    email,
    name: response.user.name || email.split('@')[0] || 'user',
    role: response.user.role || 'User',
  }
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(getAccessToken())
  const user = ref<AuthUser | null>(getStoredUser())
  const isAuthenticated = computed(() => Boolean(accessToken.value))

  async function login(payload: LoginRequest) {
    try {
      const tokens = await requestLogin(payload)
      const loginUser = createUserFromLoginResponse(tokens, payload.email)

      setAuthTokens(tokens.accessToken, tokens.refreshToken)
      setStoredUser(loginUser)
      accessToken.value = tokens.accessToken
      user.value = loginUser
    } catch (error) {
      if (
        import.meta.env.DEV &&
        payload.email === DEV_LOGIN_EMAIL &&
        payload.password === DEV_LOGIN_PASSWORD
      ) {
        const devAccessToken = 'dev-access-token'
        const devRefreshToken = 'dev-refresh-token'
        const devUser = {
          email: DEV_LOGIN_EMAIL,
          name: DEV_LOGIN_EMAIL.split('@')[0],
          role: DEV_LOGIN_ROLE,
        }

        setAuthTokens(devAccessToken, devRefreshToken)
        setStoredUser(devUser)
        accessToken.value = devAccessToken
        user.value = devUser
        return
      }

      throw error
    }
  }

  async function signup(payload: SignupRequest) {
    return requestSignup(payload)
  }

  async function logout() {
    try {
      await requestLogout()
    } catch {
      // Stateless JWT logout is primarily a client-side token discard.
    } finally {
      removeAuthTokens()
      removeStoredUser()
      accessToken.value = null
      user.value = null
    }
  }

  function clearSession() {
    removeAuthTokens()
    removeStoredUser()
    accessToken.value = null
    user.value = null
  }

  return {
    accessToken,
    user,
    isAuthenticated,
    login,
    signup,
    logout,
    clearSession,
  }
})
