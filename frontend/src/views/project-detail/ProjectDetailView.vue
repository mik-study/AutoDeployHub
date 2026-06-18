<script setup lang="ts">
import { computed, provide, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import {
  ArrowLeftIcon,
  BoltIcon,
  Cog6ToothIcon,
  CubeIcon,
  ExclamationTriangleIcon,
} from '@heroicons/vue/24/outline'
import {
  getMockProject,
  getProject,
  requestDeployment,
  type ProjectDetail,
} from '../../api/projects'
import { projectDetailContextKey } from './projectDetailContext'

const props = defineProps<{
  projectId: number
}>()

const route = useRoute()
const router = useRouter()
const project = ref<ProjectDetail | null>(null)
const isLoading = ref(false)
const isDeploying = ref(false)
const errorMessage = ref('')
const deployMessage = ref('')

const statusLabel = computed(() => {
  if (!project.value) {
    return 'UNKNOWN'
  }

  return project.value.status === 'ACTIVE' ? 'RUNNING' : project.value.status
})

const tabs = [
  { label: '개요', routeName: 'project-detail-overview' },
  { label: '배포 이력', routeName: 'project-detail-deployments' },
  { label: '환경 변수', routeName: 'project-detail-environment' },
  { label: 'Webhook', routeName: 'project-detail-webhook' },
  { label: '모니터링', routeName: 'project-detail-monitoring' },
  { label: '로그', routeName: 'project-detail-logs' },
]

provide(projectDetailContextKey, {
  project,
  statusLabel,
  formatDate,
})

async function loadProject() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    project.value = await getProject(props.projectId)
  } catch {
    if (import.meta.env.DEV) {
      const mockProject = getMockProject(props.projectId)

      if (mockProject) {
        project.value = mockProject
        return
      }
    }

    errorMessage.value = '프로젝트 정보를 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

async function handleDeploy() {
  if (!project.value) {
    return
  }

  isDeploying.value = true
  deployMessage.value = ''
  errorMessage.value = ''

  try {
    const deployment = await requestDeployment(project.value.projectId)
    deployMessage.value = `배포 요청이 등록되었습니다. 상태: ${deployment.status}`
  } catch {
    if (import.meta.env.DEV) {
      deployMessage.value = '개발 모드: 배포 API가 준비되면 실제 배포 요청으로 연결됩니다.'
      return
    }

    errorMessage.value = '배포 요청에 실패했습니다.'
  } finally {
    isDeploying.value = false
  }
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

// projectId가 바뀔 때도 자동으로 다시 조회하도록 watch 사용
watch(() => props.projectId, () => {
  void loadProject()
}, { immediate: true })
</script>

<template>
  <section class="screen project-detail-screen">
    <button class="back-button" type="button" @click="router.push({ name: 'projects' })">
      <ArrowLeftIcon aria-hidden="true" />
      프로젝트 목록
    </button>

    <section v-if="isLoading" class="panel placeholder-panel">
      <h2>프로젝트 정보를 불러오는 중입니다.</h2>
    </section>

    <section v-else-if="errorMessage && !project" class="panel placeholder-panel">
      <ExclamationTriangleIcon class="placeholder-icon" aria-hidden="true" />
      <h2>{{ errorMessage }}</h2>
    </section>

    <template v-else-if="project">
      <header class="project-detail-header">
        <div>
          <div class="project-title-row">
            <span class="project-title-icon" aria-hidden="true">
              <CubeIcon />
            </span>
            <div>
              <h1>{{ project.name }}</h1>
            </div>
            <span class="status-badge running">{{ statusLabel }}</span>
          </div>
        </div>

        <div class="detail-actions">
          <button class="secondary-button detail-action-button" type="button">
            <Cog6ToothIcon class="button-icon" aria-hidden="true" />
            설정
          </button>
          <button class="primary-button detail-action-button" type="button" :disabled="isDeploying" @click="handleDeploy">
            <BoltIcon class="button-icon" aria-hidden="true" />
            {{ isDeploying ? '배포 요청 중...' : '배포 실행' }}
          </button>
        </div>
      </header>

      <dl class="project-meta-row">
        <div>
          <dt>프로젝트 키</dt>
          <dd>{{ project.subdomain }}</dd>
        </div>
        <div>
          <dt>Git Repository</dt>
          <dd>{{ project.repositoryUrl }}</dd>
        </div>
        <div>
          <dt>생성일</dt>
          <dd>{{ formatDate(project.createdAt) }}</dd>
        </div>
      </dl>

      <nav class="project-tabs" aria-label="프로젝트 상세 탭">
        <RouterLink
          v-for="tab in tabs"
          :key="tab.routeName"
          :to="{ name: tab.routeName, params: { projectId: props.projectId } }"
          :class="{ active: route.name === tab.routeName }"
        >
          {{ tab.label }}
        </RouterLink>
      </nav>

      <p v-if="deployMessage" class="form-success project-feedback">{{ deployMessage }}</p>
      <p v-if="errorMessage" class="form-error project-feedback">{{ errorMessage }}</p>
      <RouterView />
    </template>
  </section>
</template>
