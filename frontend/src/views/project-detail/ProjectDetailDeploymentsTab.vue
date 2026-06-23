<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  CalendarDaysIcon,
  CheckCircleIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  FunnelIcon,
  MagnifyingGlassIcon,
  ArrowPathIcon,
  XCircleIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'
import {
  getMockProjectDeployments,
  getProjectDeployments,
  type DeploymentStatus,
  type DeploymentSummary,
} from '../../api/projects'

type DeploymentFilterStatus = 'ALL' | DeploymentStatus

const DEPLOYMENT_PAGE_SIZE = 5
const route = useRoute()
const today = new Date()
const oneWeekAgo = new Date()
oneWeekAgo.setDate(today.getDate() - 7)

const deployments = ref<DeploymentSummary[]>([])
const isLoading = ref(false)
const errorMessage = ref('')
const statusFilter = ref<DeploymentFilterStatus>('ALL')
const dateFrom = ref(formatDateInput(oneWeekAgo))
const dateTo = ref(formatDateInput(today))
const searchKeyword = ref('')
const currentPage = ref(0)

const projectId = computed(() => Number(route.params.projectId))

const filteredDeployments = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const fromTime = dateFrom.value ? new Date(`${dateFrom.value}T00:00:00`).getTime() : null
  const toTime = dateTo.value ? new Date(`${dateTo.value}T23:59:59`).getTime() : null

  return deployments.value.filter((deployment) => {
    const startedAt = deployment.startedAt ? new Date(deployment.startedAt).getTime() : null
    const matchesStatus =
      statusFilter.value === 'ALL' || deployment.status === statusFilter.value
    const matchesDate =
      startedAt === null ||
      ((fromTime === null || startedAt >= fromTime) &&
        (toTime === null || startedAt <= toTime))
    const matchesKeyword =
      !keyword ||
      (deployment.commitHash ?? '').toLowerCase().includes(keyword) ||
      (deployment.commitMessage ?? '').toLowerCase().includes(keyword) ||
      getTriggerTypeLabel(deployment.triggerType).toLowerCase().includes(keyword) ||
      `#${deployment.deploymentId}`.includes(keyword)

    return matchesStatus && matchesDate && matchesKeyword
  })
})

const totalPages = computed(() => {
  return Math.max(Math.ceil(filteredDeployments.value.length / DEPLOYMENT_PAGE_SIZE), 1)
})

const pagedDeployments = computed(() => {
  const startIndex = currentPage.value * DEPLOYMENT_PAGE_SIZE

  return filteredDeployments.value.slice(startIndex, startIndex + DEPLOYMENT_PAGE_SIZE)
})

const visiblePages = computed(() => {
  const lastPage = totalPages.value - 1
  const startPage = Math.max(0, Math.min(currentPage.value - 2, lastPage - 4))
  const endPage = Math.min(lastPage, startPage + 4)

  return Array.from({ length: endPage - startPage + 1 }, (_, index) => startPage + index)
})

async function loadDeployments() {
  if (!Number.isFinite(projectId.value)) {
    return
  }

  isLoading.value = true
  errorMessage.value = ''

  try {
    const response = await getProjectDeployments(projectId.value, 0, 100)
    deployments.value = response.data
  } catch {
    if (import.meta.env.DEV) {
      deployments.value = getMockProjectDeployments(projectId.value)
      return
    }

    errorMessage.value = '배포 이력을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

function formatStartedAt(value: string | null) {
  if (!value) {
    return '-'
  }

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function formatFinishedAt(value: string | null) {
  if (!value) {
    return '-'
  }

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function formatDateInput(value: Date) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

function formatDuration(startedAt: string | null, finishedAt: string | null) {
  if (!startedAt || !finishedAt) {
    return '-'
  }

  const seconds = Math.max(
    0,
    Math.floor((new Date(finishedAt).getTime() - new Date(startedAt).getTime()) / 1000),
  )

  if (seconds <= 0) {
    return '-'
  }

  const minutes = Math.floor(seconds / 60)
  const remainSeconds = seconds % 60

  if (minutes === 0) {
    return `${remainSeconds}초`
  }

  return `${minutes}분 ${remainSeconds.toString().padStart(2, '0')}초`
}

function getStatusLabel(status: DeploymentStatus) {
  switch (status) {
    case 'SUCCEEDED':
      return '성공'
    case 'FAILED':
    case 'ROLLBACK_FAILED':
      return '실패'
    case 'QUEUED':
    case 'PENDING':
    case 'CLONING':
    case 'CHECKING_DOCKERFILE':
    case 'BUILDING':
    case 'PUSHING_IMAGE':
    case 'DEPLOYING':
    case 'HEALTH_CHECKING':
    case 'SWITCHING_TRAFFIC':
    case 'ROLLING_BACK':
      return '진행 중'
    case 'CANCELED':
      return '취소'
    case 'ROLLED_BACK':
      return '롤백 완료'
    default:
      return status
  }
}

function getStatusClass(status: DeploymentStatus) {
  switch (status) {
    case 'SUCCEEDED':
    case 'ROLLED_BACK':
      return 'success'
    case 'FAILED':
    case 'ROLLBACK_FAILED':
      return 'failed'
    case 'CANCELED':
      return 'inactive'
    default:
      return 'pending'
  }
}

function getTriggerTypeLabel(triggerType: string) {
  switch (triggerType) {
    case 'MANUAL':
      return '수동'
    case 'WEBHOOK':
      return 'Webhook'
    case 'ROLLBACK':
      return 'Rollback'
    default:
      return triggerType
  }
}

function formatCommitHash(commitHash: string | null) {
  if (!commitHash) {
    return '-'
  }

  return commitHash.length > 8 ? commitHash.slice(0, 8) : commitHash
}

function resetFilters() {
  statusFilter.value = 'ALL'
  dateFrom.value = formatDateInput(oneWeekAgo)
  dateTo.value = formatDateInput(today)
  searchKeyword.value = ''
}

onMounted(() => {
  void loadDeployments()
})

watch([statusFilter, dateFrom, dateTo, searchKeyword], () => {
  currentPage.value = 0
})

watch(totalPages, (nextTotalPages) => {
  if (currentPage.value > nextTotalPages - 1) {
    currentPage.value = Math.max(nextTotalPages - 1, 0)
  }
})

watch(projectId, () => {
  currentPage.value = 0
  void loadDeployments()
})
</script>

<template>
  <section class="panel deployment-history-panel">
    <div class="deployment-history-toolbar">
      <div class="deployment-history-filters">
        <label class="deployment-filter-field">
          <span class="deployment-filter-icon" aria-hidden="true">
            <FunnelIcon />
          </span>
          <select v-model="statusFilter">
            <option value="ALL">전체 상태</option>
            <option value="SUCCEEDED">성공</option>
            <option value="FAILED">실패</option>
            <option value="QUEUED">대기</option>
            <option value="PENDING">요청됨</option>
            <option value="DEPLOYING">배포 중</option>
            <option value="CANCELED">취소</option>
          </select>
        </label>

        <div class="deployment-filter-field deployment-date-range">
          <span class="deployment-filter-icon" aria-hidden="true">
            <CalendarDaysIcon />
          </span>
          <input v-model="dateFrom" type="date" />
          <span class="deployment-range-divider">~</span>
          <input v-model="dateTo" type="date" />
        </div>
      </div>

      <div class="deployment-history-search">
        <label class="search-field deployment-search-field">
          <MagnifyingGlassIcon class="search-icon" aria-hidden="true" />
          <input v-model="searchKeyword" placeholder="커밋, 트리거 검색" />
        </label>
        <button class="secondary-button icon-button" type="button" aria-label="필터 초기화" @click="resetFilters">
          <ArrowPathIcon aria-hidden="true" />
        </button>
      </div>
    </div>

    <table class="data-table deployment-history-table">
      <thead>
        <tr>
          <th>배포 ID</th>
          <th>상태</th>
          <th>시작 시간</th>
          <th>종료 시간</th>
          <th>소요 시간</th>
          <th>트리거</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="isLoading">
          <td colspan="6" class="table-message">배포 이력을 불러오는 중입니다.</td>
        </tr>
        <tr v-else-if="errorMessage">
          <td colspan="6" class="table-message error">{{ errorMessage }}</td>
        </tr>
        <tr v-else-if="pagedDeployments.length === 0">
          <td colspan="6" class="table-message">표시할 배포 이력이 없습니다.</td>
        </tr>
        <tr
          v-for="deployment in pagedDeployments"
          :key="deployment.deploymentId"
          :class="{ 'failed-deployment-row': getStatusClass(deployment.status) === 'failed' }"
        >
          <td>
            <strong>#{{ deployment.deploymentId }}</strong>
            <span>{{ formatCommitHash(deployment.commitHash) }}</span>
          </td>
          <td>
            <span class="deployment-history-status" :class="getStatusClass(deployment.status)">
              <CheckCircleIcon v-if="getStatusClass(deployment.status) === 'success'" aria-hidden="true" />
              <XCircleIcon v-else-if="getStatusClass(deployment.status) === 'failed'" aria-hidden="true" />
              <ClockIcon v-else aria-hidden="true" />
              {{ getStatusLabel(deployment.status) }}
            </span>
          </td>
          <td>{{ formatStartedAt(deployment.startedAt) }}</td>
          <td>{{ formatFinishedAt(deployment.finishedAt) }}</td>
          <td>{{ formatDuration(deployment.startedAt, deployment.finishedAt) }}</td>
          <td>{{ getTriggerTypeLabel(deployment.triggerType) }}</td>
        </tr>
      </tbody>
    </table>

    <div class="deployment-history-footer">
      <nav class="pagination" aria-label="배포 이력 페이지 이동">
        <button
          type="button"
          :disabled="currentPage === 0"
          aria-label="이전 페이지"
          @click="currentPage -= 1"
        >
          <ChevronLeftIcon aria-hidden="true" />
        </button>
        <button
          v-for="page in visiblePages"
          :key="page"
          type="button"
          :class="{ active: page === currentPage }"
          @click="currentPage = page"
        >
          {{ page + 1 }}
        </button>
        <button
          type="button"
          :disabled="currentPage + 1 >= totalPages"
          aria-label="다음 페이지"
          @click="currentPage += 1"
        >
          <ChevronRightIcon aria-hidden="true" />
        </button>
      </nav>
    </div>
  </section>
</template>
