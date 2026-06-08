<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getProjects, type ProjectItem } from '../api/projects'

const statusLabel = {
  ACTIVE: 'RUNNING',
  INACTIVE: 'PENDING',
  FAILED: 'FAILED',
  PENDING: 'PENDING',
  RUNNING: 'RUNNING',
}

const projects = ref<ProjectItem[]>([])
const searchKeyword = ref('')
const isLoading = ref(false)
const errorMessage = ref('')
const currentPage = ref(0)
const totalPages = ref(1)

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

async function loadProjects(page = 0) {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const response = await getProjects(page, 20)

    projects.value = response.data
    currentPage.value = response.page.page
    totalPages.value = Math.max(response.page.totalPages, 1)
  } catch {
    errorMessage.value = '프로젝트 목록을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

function formatLastDeploy(project: ProjectItem) {
  if (!project.lastDeployment) {
    return '-'
  }

  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(project.lastDeployment.finishedAt))
}

function getActiveVersion(project: ProjectItem) {
  return project.lastDeployment ? `#${project.lastDeployment.deploymentId}` : '-'
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
      <button class="primary-button" type="button">+ 새 프로젝트</button>
    </div>

    <div class="toolbar">
      <label class="search-field">
        <span class="search-icon" aria-hidden="true"></span>
        <input v-model="searchKeyword" placeholder="프로젝트 검색..." />
      </label>
    </div>

    <section class="panel">
      <table class="data-table project-table">
        <thead>
          <tr>
            <th>프로젝트명</th>
            <th>상태</th>
            <th>활성 버전</th>
            <th>최근 배포</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="isLoading">
            <td colspan="4">프로젝트 목록을 불러오는 중입니다.</td>
          </tr>
          <tr v-else-if="errorMessage">
            <td colspan="4" class="table-message error">{{ errorMessage }}</td>
          </tr>
          <tr v-else-if="filteredProjects.length === 0">
            <td colspan="4" class="table-message">표시할 프로젝트가 없습니다.</td>
          </tr>
          <template v-else>
            <tr v-for="project in filteredProjects" :key="project.projectId">
              <td>
                <strong>{{ project.name }}</strong>
                <span>{{ project.subdomain }}</span>
              </td>
              <td>
                <span class="status-badge" :class="project.status.toLowerCase()">
                  {{ statusLabel[project.status] }}
                </span>
              </td>
              <td>
                <strong>{{ getActiveVersion(project) }}</strong>
                <span>{{ project.lastDeployment?.status ?? '배포 이력 없음' }}</span>
              </td>
              <td>
                <strong>{{ formatLastDeploy(project) }}</strong>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </section>

    <nav class="pagination" aria-label="페이지 이동">
      <button type="button" :disabled="currentPage === 0" @click="loadProjects(currentPage - 1)">‹</button>
      <button class="active" type="button">{{ currentPage + 1 }}</button>
      <button type="button" :disabled="currentPage + 1 >= totalPages" @click="loadProjects(currentPage + 1)">
        ›
      </button>
    </nav>
  </section>
</template>
