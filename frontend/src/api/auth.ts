import { apiClient, publicApiClient } from './client'

export interface SignupRequest {
  email: string
  password: string
  name: string
}

export interface SignupResponse {
  userId: number
  email: string
  name: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  accessTokenExpiresIn: number
  user?: {
    email: string
    name: string
    role: string
  }
}

interface ApiResponse<T> {
  data: T
}

export async function signup(payload: SignupRequest) {
  const response = await publicApiClient.post<ApiResponse<SignupResponse>>('/auth/signup', payload)

  return response.data.data
}

export async function login(payload: LoginRequest) {
  const response = await publicApiClient.post<ApiResponse<LoginResponse>>('/auth/login', payload)

  return response.data.data
}

export async function refresh(refreshToken: string) {
  const response = await publicApiClient.post<ApiResponse<LoginResponse>>('/auth/refresh', { refreshToken })

  return response.data.data
}

export async function logout() {
  await apiClient.post('/auth/logout')
}
