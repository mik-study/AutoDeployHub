import axios, { type InternalAxiosRequestConfig } from 'axios'
import { getAccessToken, getRefreshToken, removeAuthTokens, setAuthTokens } from '../utils/authToken'

const apiBaseConfig = {
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  headers: {
    'Content-Type': 'application/json',
  },
}

type RetryableRequestConfig = InternalAxiosRequestConfig & {
  _retry?: boolean
}

export const publicApiClient = axios.create(apiBaseConfig)
export const apiClient = axios.create(apiBaseConfig)

apiClient.interceptors.request.use((config) => {
  const accessToken = getAccessToken()

  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }

  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalConfig = error.config as RetryableRequestConfig | undefined
    const refreshToken = getRefreshToken()
    const requestUrl = originalConfig?.url ?? ''

    if (
      error.response?.status === 401 &&
      originalConfig &&
      !originalConfig._retry &&
      refreshToken &&
      !requestUrl.includes('/auth/login') &&
      !requestUrl.includes('/auth/refresh')
    ) {
      originalConfig._retry = true

      try {
        const response = await publicApiClient.post<{
          data: {
            accessToken: string
            refreshToken: string
            accessTokenExpiresIn: number
          }
        }>('/auth/refresh', { refreshToken })
        const tokens = response.data.data

        setAuthTokens(tokens.accessToken, tokens.refreshToken)
        originalConfig.headers.Authorization = `Bearer ${tokens.accessToken}`

        return apiClient(originalConfig)
      } catch {
        removeAuthTokens()
      }
    } else if (error.response?.status === 401) {
      removeAuthTokens()
    }

    return Promise.reject(error)
  },
)
