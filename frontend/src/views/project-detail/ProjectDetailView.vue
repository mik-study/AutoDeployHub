<script setup lang="ts">
import { computed, provide, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import {
  ArrowLeftIcon,
  BoltIcon,
  CubeIcon,
  ExclamationTriangleIcon,
  PencilSquareIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'
import {
  deleteMockProject,
  deleteProject,
  getMockProject,
  getProject,
  requestDeployment,
  updateMockProject,
  updateProject,
  type ProjectDetail,
  type UpdateProjectRequest,
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
const isSaving = ref(false)
const isDeleting = ref(false)
const isEditModalOpen = ref(false)
const errorMessage = ref('')
const deployMessage = ref('')
const editForm = ref<UpdateProjectRequest>({
  name: '',
  description: '',
  defaultBranch: '',
  rootDirectory: '',
  healthCheckPath: '',
  healthCheckPort: 8080,
  healthCheckTimeoutSeconds: 30,
  healthCheckIntervalSeconds: 10,
})

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

function openEditModal() {
  if (!project.value) {
    return
  }

  editForm.value = {
    name: project.value.name,
    description: project.value.description ?? '',
    defaultBranch: project.value.defaultBranch,
    rootDirectory: project.value.rootDirectory ?? '/',
    healthCheckPath: project.value.healthCheckPath,
    healthCheckPort: project.value.healthCheckPort,
    healthCheckTimeoutSeconds: project.value.healthCheckTimeoutSeconds,
    healthCheckIntervalSeconds: project.value.healthCheckIntervalSeconds,
  }
  errorMessage.value = ''
  deployMessage.value = ''
  isEditModalOpen.value = true
}

function closeEditModal() {
  if (isSaving.value) {
    return
  }

  isEditModalOpen.value = false
}

async function handleUpdateProject() {
  if (!project.value || !editForm.value.name?.trim()) {
    return
  }

  isSaving.value = true
  errorMessage.value = ''
  deployMessage.value = ''

  const payload: UpdateProjectRequest = {
    name: editForm.value.name.trim(),
    description: editForm.value.description?.trim() || '',
    defaultBranch: editForm.value.defaultBranch?.trim() || 'main',
    rootDirectory: editForm.value.rootDirectory?.trim() || '/',
    healthCheckPath: editForm.value.healthCheckPath?.trim() || '/health',
    healthCheckPort: Number(editForm.value.healthCheckPort),
    healthCheckTimeoutSeconds: Number(editForm.value.healthCheckTimeoutSeconds),
    healthCheckIntervalSeconds: Number(editForm.value.healthCheckIntervalSeconds),
  }

  try {
    project.value = await updateProject(project.value.projectId, payload)
    deployMessage.value = '프로젝트 정보가 수정되었습니다.'
    isEditModalOpen.value = false
  } catch {
    if (import.meta.env.DEV) {
      project.value = updateMockProject(project.value, payload)
      deployMessage.value = '개발 모드: 프로젝트 정보가 수정되었습니다.'
      isEditModalOpen.value = false
      return
    }

    errorMessage.value = '프로젝트 수정에 실패했습니다.'
  } finally {
    isSaving.value = false
  }
}

async function handleDeleteProject() {
  if (!project.value || isDeleting.value) {
    return
  }

  if (!window.confirm('프로젝트를 삭제하시겠습니까?')) {
    return
  }

  isDeleting.value = true
  errorMessage.value = ''
  deployMessage.value = ''

  try {
    await deleteProject(project.value.projectId)
  } catch {
    if (import.meta.env.DEV) {
      deleteMockProject(project.value.projectId)
    } else {
      errorMessage.value = '프로젝트 삭제에 실패했습니다.'
      isDeleting.value = false
      return
    }
  }

  await router.push({ name: 'projects' })
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
          <button class="secondary-button detail-action-button" type="button" @click="openEditModal">
            <PencilSquareIcon class="button-icon" aria-hidden="true" />
            수정
          </button>
          <button class="danger-button detail-action-button" type="button" :disabled="isDeleting" @click="handleDeleteProject">
            <TrashIcon class="button-icon" aria-hidden="true" />
            {{ isDeleting ? '삭제 중...' : '삭제' }}
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

      <div v-if="isEditModalOpen" class="modal-backdrop" role="presentation" @click.self="closeEditModal">
        <form class="panel modal-panel project-edit-modal" @submit.prevent="handleUpdateProject">
          <header class="modal-header">
            <div>
              <h2>프로젝트 수정</h2>
              <p>{{ project.name }} 설정을 변경합니다.</p>
            </div>
            <button class="modal-close" type="button" aria-label="닫기" @click="closeEditModal">×</button>
          </header>

          <div class="project-form-grid">
            <label class="field">
              <span class="field-label">프로젝트명</span>
              <input v-model="editForm.name" required type="text" />
            </label>

            <label class="field">
              <span class="field-label">기본 브랜치</span>
              <input v-model="editForm.defaultBranch" type="text" />
            </label>

            <label class="field project-form-wide">
              <span class="field-label">설명</span>
              <textarea v-model="editForm.description" rows="3"></textarea>
            </label>

            <label class="field">
              <span class="field-label">루트 디렉터리</span>
              <input v-model="editForm.rootDirectory" type="text" />
            </label>

            <label class="field">
              <span class="field-label">헬스 체크 경로</span>
              <input v-model="editForm.healthCheckPath" type="text" />
            </label>

            <label class="field">
              <span class="field-label">헬스 체크 포트</span>
              <input v-model.number="editForm.healthCheckPort" min="1" type="number" />
            </label>

            <label class="field">
              <span class="field-label">헬스 체크 타임아웃(초)</span>
              <input v-model.number="editForm.healthCheckTimeoutSeconds" min="1" type="number" />
            </label>

            <label class="field">
              <span class="field-label">헬스 체크 주기(초)</span>
              <input v-model.number="editForm.healthCheckIntervalSeconds" min="1" type="number" />
            </label>
          </div>

          <div class="form-actions">
            <button class="secondary-button" type="button" @click="closeEditModal">취소</button>
            <button class="primary-button" :disabled="isSaving || !editForm.name?.trim()" type="submit">
              {{ isSaving ? '저장 중...' : '저장' }}
            </button>
          </div>
        </form>
      </div>
    </template>
  </section>
</template>
