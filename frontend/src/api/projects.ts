import { apiClient } from './client'

export type ProjectStatus = 'ACTIVE' | 'ARCHIVED'
export type DeploymentStatus =
  | 'PENDING'
  | 'QUEUED'
  | 'CLONING'
  | 'CHECKING_DOCKERFILE'
  | 'BUILDING'
  | 'PUSHING_IMAGE'
  | 'DEPLOYING'
  | 'HEALTH_CHECKING'
  | 'SWITCHING_TRAFFIC'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELED'
  | 'ROLLING_BACK'
  | 'ROLLED_BACK'
  | 'ROLLBACK_FAILED'
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

export interface CreateProjectRequest {
  name: string
  description?: string
  repositoryUrl: string
  defaultBranch?: string
  rootDirectory?: string
  healthCheckPath?: string
  healthCheckPort?: number
  healthCheckTimeoutSeconds?: number
  healthCheckIntervalSeconds?: number
  subdomain?: string
}

export interface UpdateProjectRequest {
  name?: string
  description?: string
  defaultBranch?: string
  rootDirectory?: string
  healthCheckPath?: string
  healthCheckPort?: number
  healthCheckTimeoutSeconds?: number
  healthCheckIntervalSeconds?: number
}

export interface CreateProjectResponse {
  projectId: number
  name: string
  subdomain: string
  repositoryUrl: string
  defaultBranch: string
  status: ProjectStatus
  webhookUrl: string
  webhookSecret: string
  createdAt: string
}

export interface ProjectDetail {
  projectId: number
  name: string
  description: string | null
  repositoryUrl: string
  defaultBranch: string
  rootDirectory: string | null
  buildType: string
  healthCheckPath: string
  healthCheckPort: number
  healthCheckTimeoutSeconds: number
  healthCheckIntervalSeconds: number
  subdomain: string
  webhookSecret: string
  status: ProjectStatus
  createdAt: string
}

export interface DeploymentRequestResponse {
  deploymentId: number
  status: DeploymentStatus
  queuedAt?: string
}

export interface DeploymentSummary {
  deploymentId: number
  branch: string | null
  commitHash: string | null
  commitMessage: string | null
  status: DeploymentStatus
  triggerType: 'MANUAL' | 'WEBHOOK' | 'ROLLBACK' | string
  startedAt: string | null
  finishedAt: string | null
}

export interface DeploymentDetail extends DeploymentSummary {
  projectId?: number
  previousDeploymentId?: number | null
  imageRepository?: string | null
  imageTag?: string | null
  failureReason?: string | null
  createdAt?: string | null
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

interface DeploymentListResponse {
  data: DeploymentSummary[]
  page: PageInfo
}

export async function getProjects(page = 0, size = 20) {
  const response = await apiClient.get<ProjectListResponse>('/projects', {
    params: { page, size },
  })

  return response.data
}

export async function getProject(projectId: number) {
  const response = await apiClient.get<{ data: ProjectDetail }>(`/projects/${projectId}`)
  return response.data.data
}

export async function createProject(payload: CreateProjectRequest) {
  const response = await apiClient.post<{ data: CreateProjectResponse }>('/projects', payload)
  return response.data.data
}

export async function updateProject(projectId: number, payload: UpdateProjectRequest) {
  const response = await apiClient.patch<{ data: ProjectDetail }>(`/projects/${projectId}`, payload)
  return response.data.data
}

export async function deleteProject(projectId: number) {
  await apiClient.delete(`/projects/${projectId}`)
}

export async function requestDeployment(projectId: number) {
  const response = await apiClient.post<{ data: DeploymentRequestResponse }>(
    `/projects/${projectId}/deployments`,
  )

  return response.data.data
}

export async function getProjectDeployments(projectId: number, page = 0, size = 100) {
  const response = await apiClient.get<DeploymentListResponse>(`/projects/${projectId}/deployments`, {
    params: { page, size },
  })

  return response.data
}

export async function cancelDeployment(deploymentId: number) {
  const response = await apiClient.post<{ data: DeploymentDetail }>(
    `/deployments/${deploymentId}/cancel`,
  )

  return response.data.data
}

export async function getDeploymentDetail(deploymentId: number) {
  const response = await apiClient.get<{ data: DeploymentDetail }>(`/deployments/${deploymentId}`)
  return response.data.data
}
