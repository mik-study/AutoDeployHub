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
const MOCK_PROJECTS_STORAGE_KEY = 'autodeploy.mockProjects'

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

interface MockProjectDetailOverrides {
  description?: string | null
  defaultBranch?: string
  rootDirectory?: string | null
  healthCheckPath?: string
  healthCheckPort?: number
  healthCheckTimeoutSeconds?: number
  healthCheckIntervalSeconds?: number
}

export interface DeploymentRequestResponse {
  deploymentId: number
  status: DeploymentStatus
  queuedAt?: string
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

const defaultMockProjects: ProjectItem[] = [
  {
    projectId: 1,
    name: '배포 플랫폼',
    subdomain: 'deploy-platform',
    status: 'ACTIVE',
    activeVersion: {
      version: 'v1.2.3',
      color: 'GREEN',
    },
    lastDeployment: {
      deploymentId: 101,
      status: 'SUCCEEDED',
      finishedAt: createMinutesAgo(10),
    },
  },
  {
    projectId: 2,
    name: '사용자 서비스',
    subdomain: 'user-service',
    status: 'ACTIVE',
    activeVersion: {
      version: 'v2.1.0',
      color: 'BLUE',
    },
    lastDeployment: {
      deploymentId: 102,
      status: 'PENDING',
      finishedAt: createMinutesAgo(60),
    },
  },
  {
    projectId: 3,
    name: '주문 서비스',
    subdomain: 'order-service',
    status: 'ACTIVE',
    activeVersion: {
      version: 'v1.0.5',
      color: 'BLUE',
    },
    lastDeployment: {
      deploymentId: 103,
      status: 'FAILED',
      finishedAt: createMinutesAgo(19 * 60),
    },
  },
  {
    projectId: 4,
    name: '결제 서비스',
    subdomain: 'payment-service',
    status: 'ACTIVE',
    activeVersion: null,
    lastDeployment: {
      deploymentId: 104,
      status: 'PENDING',
      finishedAt: createMinutesAgo(24 * 60),
    },
  },
  {
    projectId: 5,
    name: '알림 서비스',
    subdomain: 'notification-service',
    status: 'ACTIVE',
    activeVersion: {
      version: 'v1.3.0',
      color: 'GREEN',
    },
    lastDeployment: {
      deploymentId: 105,
      status: 'SUCCEEDED',
      finishedAt: createMinutesAgo(2 * 60),
    },
  },
  {
    projectId: 6,
    name: 'API 게이트웨이',
    subdomain: 'gateway-service',
    status: 'ACTIVE',
    activeVersion: {
      version: 'v1.4.2',
      color: 'BLUE',
    },
    lastDeployment: {
      deploymentId: 106,
      status: 'SUCCEEDED',
      finishedAt: createMinutesAgo(35),
    },
  },
]

const defaultMockProjectDetails: Record<number, MockProjectDetailOverrides> = {
  1: {
    description: '배포 플랫폼의 배포 설정과 최근 상태를 확인합니다.',
  },
  2: {
    description: '사용자 서비스의 배포 설정과 최근 상태를 확인합니다.',
  },
  3: {
    description: '주문 서비스의 배포 설정과 최근 상태를 확인합니다.',
  },
  4: {
    description: '결제 서비스의 배포 설정과 최근 상태를 확인합니다.',
  },
  5: {
    description: '알림 서비스의 배포 설정과 최근 상태를 확인합니다.',
  },
  6: {
    description: 'API 게이트웨이의 배포 설정과 최근 상태를 확인합니다.',
  },
}

const MOCK_PROJECT_DETAILS_STORAGE_KEY = 'autodeploy.mockProjectDetails'

function createMinutesAgo(minutes: number) {
  return new Date(Date.now() - minutes * 60 * 1000).toISOString()
}

function normalizeSubdomain(name: string) {
  return name
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 63)
}

function getStoredMockProjects() {
  const value = localStorage.getItem(MOCK_PROJECTS_STORAGE_KEY)

  if (!value) {
    localStorage.setItem(MOCK_PROJECTS_STORAGE_KEY, JSON.stringify(defaultMockProjects))
    return [...defaultMockProjects]
  }

  try {
    return JSON.parse(value) as ProjectItem[]
  } catch {
    localStorage.setItem(MOCK_PROJECTS_STORAGE_KEY, JSON.stringify(defaultMockProjects))
    return [...defaultMockProjects]
  }
}

function setStoredMockProjects(projects: ProjectItem[]) {
  localStorage.setItem(MOCK_PROJECTS_STORAGE_KEY, JSON.stringify(projects))
}

function getStoredMockProjectDetails() {
  const value = localStorage.getItem(MOCK_PROJECT_DETAILS_STORAGE_KEY)

  if (!value) {
    localStorage.setItem(
      MOCK_PROJECT_DETAILS_STORAGE_KEY,
      JSON.stringify(defaultMockProjectDetails),
    )
    return { ...defaultMockProjectDetails }
  }

  try {
    return JSON.parse(value) as Record<number, MockProjectDetailOverrides>
  } catch {
    localStorage.setItem(
      MOCK_PROJECT_DETAILS_STORAGE_KEY,
      JSON.stringify(defaultMockProjectDetails),
    )
    return { ...defaultMockProjectDetails }
  }
}

function setStoredMockProjectDetails(details: Record<number, MockProjectDetailOverrides>) {
  localStorage.setItem(MOCK_PROJECT_DETAILS_STORAGE_KEY, JSON.stringify(details))
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

export function getMockProjectsPage(page = 0, size = 20): ProjectListResponse {
  const projects = getStoredMockProjects()
  const startIndex = page * size

  return {
    data: projects.slice(startIndex, startIndex + size),
    page: {
      page,
      size,
      totalElements: projects.length,
      totalPages: Math.max(Math.ceil(projects.length / size), 1),
    },
  }
}

export function getMockProject(projectId: number): ProjectDetail | null {
  const project = getStoredMockProjects().find((item) => item.projectId === projectId)
  const detailOverrides = getStoredMockProjectDetails()[projectId]

  if (!project) {
    return null
  }

  return {
    projectId: project.projectId,
    name: project.name,
    description:
      detailOverrides?.description ?? `${project.name}의 배포 설정과 최근 상태를 확인합니다.`,
    repositoryUrl: `https://github.com/example-team/${project.subdomain}.git`,
    defaultBranch: detailOverrides?.defaultBranch ?? 'main',
    rootDirectory: detailOverrides?.rootDirectory ?? '/',
    buildType: 'DOCKERFILE',
    healthCheckPath: detailOverrides?.healthCheckPath ?? '/health',
    healthCheckPort: detailOverrides?.healthCheckPort ?? 8080,
    healthCheckTimeoutSeconds: detailOverrides?.healthCheckTimeoutSeconds ?? 30,
    healthCheckIntervalSeconds: detailOverrides?.healthCheckIntervalSeconds ?? 10,
    subdomain: project.subdomain,
    webhookSecret: 'whs_****',
    status: project.status,
    createdAt: createMinutesAgo(24 * 60),
  }
}

export function createMockProject(payload: CreateProjectRequest): CreateProjectResponse {
  const projects = getStoredMockProjects()
  const projectId = projects.reduce((maxId, project) => Math.max(maxId, project.projectId), 0) + 1
  const subdomain = payload.subdomain?.trim() || normalizeSubdomain(payload.name) || `project-${projectId}`
  const createdAt = new Date().toISOString()
  const createdProject: ProjectItem = {
    projectId,
    name: payload.name.trim(),
    subdomain,
    status: 'ACTIVE',
    activeVersion: null,
    lastDeployment: null,
  }

  setStoredMockProjects([createdProject, ...projects])

  return {
    projectId,
    name: createdProject.name,
    subdomain,
    repositoryUrl: payload.repositoryUrl.trim(),
    defaultBranch: payload.defaultBranch?.trim() || 'main',
    status: 'ACTIVE',
    webhookUrl: 'http://api.autodeploy.test/api/webhooks/github',
    webhookSecret: `whs_mock_${projectId}`,
    createdAt,
  }
}

export function updateMockProject(
  project: ProjectDetail,
  payload: UpdateProjectRequest,
): ProjectDetail {
  const projects = getStoredMockProjects()
  const detailOverrides = getStoredMockProjectDetails()

  const nextProject = {
    ...project,
    name: payload.name?.trim() || project.name,
    description: payload.description ?? project.description,
    defaultBranch: payload.defaultBranch?.trim() || project.defaultBranch,
    rootDirectory: payload.rootDirectory?.trim() || null,
    healthCheckPath: payload.healthCheckPath?.trim() || project.healthCheckPath,
    healthCheckPort: payload.healthCheckPort ?? project.healthCheckPort,
    healthCheckTimeoutSeconds:
      payload.healthCheckTimeoutSeconds ?? project.healthCheckTimeoutSeconds,
    healthCheckIntervalSeconds:
      payload.healthCheckIntervalSeconds ?? project.healthCheckIntervalSeconds,
  }

  setStoredMockProjects(projects.map((item) => {
    if (item.projectId !== project.projectId) {
      return item
    }

    return {
      ...item,
      name: payload.name?.trim() || item.name,
    }
  }))

  setStoredMockProjectDetails({
    ...detailOverrides,
    [project.projectId]: {
      description: nextProject.description,
      defaultBranch: nextProject.defaultBranch,
      rootDirectory: nextProject.rootDirectory,
      healthCheckPath: nextProject.healthCheckPath,
      healthCheckPort: nextProject.healthCheckPort,
      healthCheckTimeoutSeconds: nextProject.healthCheckTimeoutSeconds,
      healthCheckIntervalSeconds: nextProject.healthCheckIntervalSeconds,
    },
  })

  return nextProject
}

export function deleteMockProject(projectId: number) {
  setStoredMockProjects(getStoredMockProjects().filter((project) => project.projectId !== projectId))
  const detailOverrides = getStoredMockProjectDetails()
  delete detailOverrides[projectId]
  setStoredMockProjectDetails(detailOverrides)
}
