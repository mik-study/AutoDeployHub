import { apiClient } from './client'

export type ProjectStatus = 'ACTIVE' | 'ARCHIVED'
export type DeploymentStatus = 'SUCCEEDED' | 'FAILED' | 'RUNNING' | 'PENDING'
export type RuntimeColor = 'BLUE' | 'GREEN'

export interface ProjectItem {
  projectId: number
  name: string
  subdomain: string
  status: ProjectStatus
  activeVersion?: {
    version: string
    color: RuntimeColor
  } | null
  lastDeployment: {
    deploymentId: number
    status: DeploymentStatus
    finishedAt: string
  } | null
}

export interface PageInfo {
  page: number
  size: number
  totalElements: number
  totalPages: number
}

interface ProjectListResponse {
  data: ProjectItem[]
  page: PageInfo
}

export async function getProjects(page = 0, size = 20) {
  const response = await apiClient.get<ProjectListResponse>('/projects', {
    params: {
      page,
      size,
    },
  })

  return response.data
}
