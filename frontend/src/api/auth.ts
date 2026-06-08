import { apiClient } from './client'

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  accessTokenExpiresIn: number
}

interface ApiResponse<T> {
  data: T
}

export async function login(payload: LoginRequest) {
  const response = await apiClient.post<ApiResponse<LoginResponse>>('/auth/login', payload)

  return response.data.data
}
