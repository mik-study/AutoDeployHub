<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChevronLeftIcon,
  ChevronRightIcon,
  MagnifyingGlassIcon,
  PlusIcon,
} from '@heroicons/vue/24/outline'
import {
  getMockProjectsPage,
  getProjects,
  type DeploymentStatus,
  type ProjectItem,
} from '../../api/projects'

type ProjectDisplayStatus = 'RUNNING' | 'PENDING' | 'FAILED' | 'INACTIVE'

const PROJECTS_PAGE_SIZE = 5
const route = useRoute()
const router = useRouter()

const projects = ref<ProjectItem[]>([])
const searchKeyword = ref('')
const isLoading = ref(false)
const errorMessage = ref('')
const currentPage = ref(0)
const totalPages = ref(1)
const successMessage = computed(() => {
  return route.query.created === '1' && typeof route.query.name === 'string'
    ? `"${route.query.name}" 프로젝트가 생성되었습니다.`
    : ''
})

const filteredProjects = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()

  if (!keyword) {
    return projects.value
  }

  return projects.value.filter((project) => {
    return (
      project.name.toLowerCase().includes(keyword) ||
      project.subdomain.toLowerCase().includes(keyword)
    )
  })
})

const visiblePages = computed(() => {
  const maxVisiblePages = 5
  const halfRange = Math.floor(maxVisiblePages / 2)
  const lastPage = totalPages.value - 1
  const startPage = Math.max(0, Math.min(currentPage.value - halfRange, lastPage - maxVisiblePages + 1))
  const endPage = Math.min(lastPage, startPage + maxVisiblePages - 1)

  return Array.from({ length: endPage - startPage + 1 }, (_, index) => startPage + index)
})

async function loadProjects(page = 0) {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const response = await getProjects(page, PROJECTS_PAGE_SIZE)

    projects.value = response.data
    currentPage.value = response.page.page
    totalPages.value = Math.max(response.page.totalPages, 1)
  } catch {
    if (import.meta.env.DEV) {
      const response = getMockProjectsPage(page, PROJECTS_PAGE_SIZE)

      projects.value = response.data
      currentPage.value = response.page.page
      totalPages.value = response.page.totalPages
      return
    }

    errorMessage.value = '프로젝트 목록을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

function formatLastDeployDate(project: ProjectItem) {
  if (!project.lastDeployment) {
    return '-'
  }

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(project.lastDeployment.finishedAt))
}

function formatRelativeDeploy(project: ProjectItem) {
  if (!project.lastDeployment) {
    return ''
  }

  const deployedAt = new Date(project.lastDeployment.finishedAt).getTime()
  const diffMinutes = Math.max(1, Math.floor((Date.now() - deployedAt) / 60000))

  if (diffMinutes < 60) {
    return `(${diffMinutes}분 전)`
  }

  const diffHours = Math.floor(diffMinutes / 60)

  if (diffHours < 24) {
    return `(${diffHours}시간 전)`
  }

  return `(${Math.floor(diffHours / 24)}일 전)`
}

function isPendingDeploymentStatus(status: DeploymentStatus) {
  return [
    'PENDING',
    'QUEUED',
    'CLONING',
    'CHECKING_DOCKERFILE',
    'BUILDING',
    'PUSHING_IMAGE',
    'DEPLOYING',
    'HEALTH_CHECKING',
    'SWITCHING_TRAFFIC',
    'ROLLING_BACK',
  ].includes(status)
}

function getProjectDisplayStatus(project: ProjectItem): ProjectDisplayStatus {
  if (project.status !== 'ACTIVE') {
    return 'INACTIVE'
  }

  if (!project.lastDeployment) {
    return 'RUNNING'
  }

  if (
    project.lastDeployment.status === 'FAILED' ||
    project.lastDeployment.status === 'ROLLBACK_FAILED'
  ) {
    return 'FAILED'
  }

  if (isPendingDeploymentStatus(project.lastDeployment.status)) {
    return 'PENDING'
  }

  if (
    project.lastDeployment.status === 'SUCCEEDED' ||
    project.lastDeployment.status === 'ROLLED_BACK' ||
    project.lastDeployment.status === 'CANCELED'
  ) {
    return 'RUNNING'
  }

  return 'INACTIVE'
}

function getStatusClass(project: ProjectItem) {
  return getProjectDisplayStatus(project).toLowerCase()
}

function getActiveVersionLabel(project: ProjectItem) {
  if (project.activeVersion) {
    return project.activeVersion.version
  }

  return project.lastDeployment ? `#${project.lastDeployment.deploymentId}` : '-'
}

async function goToCreateProject() {
  await router.push({ name: 'project-create' })
}

async function goToProjectDetail(project: ProjectItem) {
  await router.push({
    name: 'project-detail-overview',
    params: {
      projectId: project.projectId,
    },
  })
}

onMounted(() => {
  void loadProjects()
})
</script>

<template>
  <section class="screen">
    <div class="screen-header">
      <div>
        <h1>프로젝트</h1>
      </div>
    </div>

    <div class="toolbar">
      <label class="search-field">
        <MagnifyingGlassIcon class="search-icon" aria-hidden="true" />
        <input v-model="searchKeyword" placeholder="프로젝트 검색..." />
      </label>
      <button class="primary-button create-project-button" type="button" @click="goToCreateProject">
        <PlusIcon class="button-icon" aria-hidden="true" />
        새 프로젝트
      </button>
    </div>

    <p v-if="successMessage" class="form-success project-feedback">{{ successMessage }}</p>

    <section class="panel">
      <table class="data-table project-table">
        <thead>
          <tr>
            <th>프로젝트명</th>
            <th>상태</th>
            <th>활성 버전</th>
            <th>최근 배포</th>
            <th class="action-column" aria-label="상세"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="isLoading">
            <td colspan="5">프로젝트 목록을 불러오는 중입니다.</td>
          </tr>
          <tr v-else-if="errorMessage">
            <td colspan="5" class="table-message error">{{ errorMessage }}</td>
          </tr>
          <tr v-else-if="filteredProjects.length === 0">
            <td colspan="5" class="table-message">표시할 프로젝트가 없습니다.</td>
          </tr>
          <template v-else>
            <tr v-for="project in filteredProjects" :key="project.projectId">
              <td>
                <strong>{{ project.name }}</strong>
                <span>{{ project.subdomain }}</span>
              </td>
              <td>
                <span class="status-badge" :class="getStatusClass(project)">
                  {{ getProjectDisplayStatus(project) }}
                </span>
              </td>
              <td>
                <strong>{{ getActiveVersionLabel(project) }}</strong>
                <span v-if="project.activeVersion" class="version-color" :class="project.activeVersion.color.toLowerCase()">
                  ({{ project.activeVersion.color }})
                </span>
                <span v-else>활성 버전 없음</span>
              </td>
              <td>
                <strong>{{ formatLastDeployDate(project) }}</strong>
                <span v-if="formatRelativeDeploy(project)">{{ formatRelativeDeploy(project) }}</span>
              </td>
              <td class="row-action-cell">
                <button
                  class="row-action-button"
                  type="button"
                  :aria-label="`${project.name} 상세 보기`"
                  @click="goToProjectDetail(project)"
                >
                  <ChevronRightIcon aria-hidden="true" />
                </button>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </section>

    <nav class="pagination" aria-label="페이지 이동">
      <button type="button" :disabled="currentPage === 0" aria-label="이전 페이지" @click="loadProjects(currentPage - 1)">
        <ChevronLeftIcon aria-hidden="true" />
      </button>
      <button
        v-for="page in visiblePages"
        :key="page"
        type="button"
        :class="{ active: page === currentPage }"
        @click="loadProjects(page)"
      >
        {{ page + 1 }}
      </button>
      <button
        type="button"
        :disabled="currentPage + 1 >= totalPages"
        aria-label="다음 페이지"
        @click="loadProjects(currentPage + 1)"
      >
        <ChevronRightIcon aria-hidden="true" />
      </button>
    </nav>
  </section>
</template>
