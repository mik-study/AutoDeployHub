<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  CheckCircleIcon,
  ClockIcon,
  ExclamationCircleIcon,
} from '@heroicons/vue/24/outline'
import {
  getDeploymentDetail,
  getProjectDeployments,
  type DeploymentDetail,
  type DeploymentStatus,
  type DeploymentSummary,
} from '../../../api/projects'
import { useProjectDetailContext } from '../projectDetailContext'

const { project, deploymentRefreshKey, formatDate } = useProjectDetailContext()
const latestDeployment = ref<DeploymentSummary | null>(null)
const latestDeploymentDetail = ref<DeploymentDetail | null>(null)
const currentDeployment = ref<DeploymentDetail | null>(null)
const previousDeployment = ref<DeploymentDetail | null>(null)
const isLoadingDeployments = ref(false)
const deploymentError = ref('')

const deploymentStatusLabel = computed(() => {
  if (!latestDeployment.value) {
    return '배포 전'
  }

  return getStatusLabel(latestDeployment.value.status)
})

const deploymentStatusClass = computed(() => {
  if (!latestDeployment.value) {
    return 'inactive'
  }

  return getStatusClass(latestDeployment.value.status)
})

const healthCheckState = computed(() => {
  const status = latestDeployment.value?.status

  if (!status) {
    return { label: '확인 전', className: 'inactive', icon: ClockIcon }
  }

  if (status === 'HEALTH_CHECKING') {
    return { label: '확인 중', className: 'pending', icon: ClockIcon }
  }

  if (status === 'SUCCEEDED') {
    return { label: '정상', className: 'success', icon: CheckCircleIcon }
  }

  if (status === 'FAILED' || status === 'ROLLBACK_FAILED') {
    return { label: '확인 필요', className: 'failed', icon: ExclamationCircleIcon }
  }

  return { label: '대기 중', className: 'pending', icon: ClockIcon }
})

async function loadDeploymentSummary() {
  const projectId = project.value?.projectId

  if (!projectId) {
    return
  }

  isLoadingDeployments.value = true
  deploymentError.value = ''

  try {
    const response = await getProjectDeployments(projectId, 0, 20)
    latestDeployment.value = response.data[0] ?? null

    const successfulDeployments = response.data
      .filter((deployment) => deployment.status === 'SUCCEEDED')
      .slice(0, 2)
    const detailIds = [
      latestDeployment.value?.deploymentId,
      ...successfulDeployments.map((deployment) => deployment.deploymentId),
    ].filter((deploymentId): deploymentId is number => deploymentId !== undefined)
    const uniqueDetailIds = [...new Set(detailIds)]
    const deploymentDetails = await Promise.all(
      uniqueDetailIds.map((deploymentId) => getDeploymentDetail(deploymentId)),
    )
    const detailsById = new Map(
      deploymentDetails.map((deployment) => [deployment.deploymentId, deployment]),
    )

    latestDeploymentDetail.value = latestDeployment.value
      ? detailsById.get(latestDeployment.value.deploymentId) ?? null
      : null
    currentDeployment.value = successfulDeployments[0]
      ? detailsById.get(successfulDeployments[0].deploymentId) ?? null
      : null
    previousDeployment.value = successfulDeployments[1]
      ? detailsById.get(successfulDeployments[1].deploymentId) ?? null
      : null
  } catch {
    latestDeployment.value = null
    latestDeploymentDetail.value = null
    currentDeployment.value = null
    previousDeployment.value = null
    deploymentError.value = '배포 정보를 불러오지 못했습니다.'
  } finally {
    isLoadingDeployments.value = false
  }
}

function getDeploymentVersion(deployment: DeploymentDetail | null) {
  if (!deployment) {
    return '-'
  }

  if (deployment.imageTag) {
    return deployment.imageTag
  }

  if (deployment.commitHash) {
    return deployment.commitHash.slice(0, 8)
  }

  return `#${deployment.deploymentId}`
}

function getLatestDeploymentDate() {
  const value = latestDeployment.value?.finishedAt
    ?? latestDeployment.value?.startedAt
    ?? latestDeploymentDetail.value?.createdAt
  return value ? formatDate(value) : '-'
}

function getStatusLabel(status: DeploymentStatus) {
  switch (status) {
    case 'SUCCEEDED':
      return '성공'
    case 'FAILED':
    case 'ROLLBACK_FAILED':
      return '실패'
    case 'CANCELED':
      return '취소'
    case 'ROLLED_BACK':
      return '롤백 완료'
    case 'PENDING':
      return '요청됨'
    case 'QUEUED':
      return '대기 중'
    case 'CLONING':
      return '소스 복제 중'
    case 'CHECKING_DOCKERFILE':
      return 'Dockerfile 확인 중'
    case 'BUILDING':
      return '빌드 중'
    case 'PUSHING_IMAGE':
      return '이미지 처리 중'
    case 'DEPLOYING':
      return '배포 중'
    case 'HEALTH_CHECKING':
      return 'Health Check 중'
    case 'SWITCHING_TRAFFIC':
      return '트래픽 전환 중'
    case 'ROLLING_BACK':
      return '롤백 중'
    default:
      return status
  }
}

function getStatusClass(status: DeploymentStatus) {
  if (status === 'SUCCEEDED' || status === 'ROLLED_BACK') {
    return 'success'
  }

  if (status === 'FAILED' || status === 'ROLLBACK_FAILED') {
    return 'failed'
  }

  if (status === 'CANCELED') {
    return 'inactive'
  }

  return 'pending'
}

watch(
  [() => project.value?.projectId, deploymentRefreshKey],
  () => {
    void loadDeploymentSummary()
  },
  { immediate: true },
)
</script>

<template>
  <div v-if="project" class="overview-grid">
    <section class="panel detail-card summary-card overview-summary-card">
      <h2>배포 요약</h2>
      <p v-if="deploymentError" class="overview-api-message error">{{ deploymentError }}</p>
      <p v-else-if="isLoadingDeployments" class="overview-api-message">배포 정보를 불러오는 중입니다.</p>
      <dl v-else class="detail-list compact">
        <div>
          <dt>현재 버전</dt>
          <dd>{{ getDeploymentVersion(currentDeployment) }}</dd>
        </div>
        <div>
          <dt>이전 성공 버전</dt>
          <dd>{{ getDeploymentVersion(previousDeployment) }}</dd>
        </div>
        <div>
          <dt>최근 배포</dt>
          <dd>{{ getLatestDeploymentDate() }}</dd>
        </div>
        <div>
          <dt>배포 상태</dt>
          <dd>
            <span class="status-badge" :class="deploymentStatusClass">
              {{ deploymentStatusLabel }}
            </span>
          </dd>
        </div>
        <div>
          <dt>배포 브랜치</dt>
          <dd>{{ latestDeployment?.branch || '-' }}</dd>
        </div>
        <div>
          <dt>Health Check</dt>
          <dd class="health-state" :class="healthCheckState.className">
            <component :is="healthCheckState.icon" aria-hidden="true" />
            {{ healthCheckState.label }}
          </dd>
        </div>
      </dl>
    </section>

    <section class="panel detail-card">
      <h2>서비스 정보</h2>
      <dl class="detail-list compact">
        <div>
          <dt>애플리케이션</dt>
          <dd>{{ project.name }}</dd>
        </div>
        <div>
          <dt>포트</dt>
          <dd>{{ project.healthCheckPort }}</dd>
        </div>
        <div>
          <dt>기본 브랜치</dt>
          <dd>{{ project.defaultBranch }}</dd>
        </div>
        <div>
          <dt>빌드 타입</dt>
          <dd>{{ project.buildType }}</dd>
        </div>
        <div>
          <dt>루트 디렉터리</dt>
          <dd>{{ project.rootDirectory || '/' }}</dd>
        </div>
        <div>
          <dt>생성일</dt>
          <dd>{{ formatDate(project.createdAt) }}</dd>
        </div>
      </dl>
    </section>

    <section class="panel detail-card resource-card">
      <h2>리소스 사용 현황</h2>
      <div class="resource-empty-state">
        <ClockIcon aria-hidden="true" />
        <strong>리소스 데이터 없음</strong>
        <span>현재 수집된 리소스 사용량이 없습니다.</span>
      </div>
    </section>
  </div>
</template>
