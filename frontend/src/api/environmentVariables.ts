import { apiClient } from './client'

export interface EnvironmentVariableItem {
  envId: number
  key: string
  value: string
  isSecret: boolean
  updatedAt: string
}

export interface CreateEnvironmentVariableRequest {
  key: string
  value: string
  isSecret: boolean
}

export interface UpdateEnvironmentVariableRequest {
  value: string
}

export interface EnvironmentVariableResponse {
  envId: number
  projectId: number
  key: string
  isSecret: boolean
  createdAt: string
}

interface ApiResponse<T> {
  data: T
}

export async function getEnvironmentVariables(projectId: number) {
  const response = await apiClient.get<ApiResponse<EnvironmentVariableItem[]>>(
    `/projects/${projectId}/env`,
  )

  return response.data.data
}

export async function createEnvironmentVariable(
  projectId: number,
  payload: CreateEnvironmentVariableRequest,
) {
  const response = await apiClient.post<ApiResponse<EnvironmentVariableResponse>>(
    `/projects/${projectId}/env`,
    payload,
  )

  return response.data.data
}

export async function updateEnvironmentVariable(
  projectId: number,
  envId: number,
  payload: UpdateEnvironmentVariableRequest,
) {
  const response = await apiClient.patch<ApiResponse<EnvironmentVariableResponse>>(
    `/projects/${projectId}/env/${envId}`,
    payload,
  )

  return response.data.data
}

export async function deleteEnvironmentVariable(projectId: number, envId: number) {
  await apiClient.delete(`/projects/${projectId}/env/${envId}`)
}
