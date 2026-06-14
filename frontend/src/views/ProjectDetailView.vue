<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeftIcon,
  BoltIcon,
  CheckCircleIcon,
  Cog6ToothIcon,
  CubeIcon,
  ExclamationTriangleIcon,
} from '@heroicons/vue/24/outline'
import {
  getMockProject,
  getProject,
  requestDeployment,
  type ProjectDetail,
} from '../api/projects'

const props = defineProps<{
  projectId: number
}>()

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

const tabs = ['개요', '배포 이력', '환경 변수', 'Webhook', '모니터링', '로그']

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

onMounted(() => {
  void loadProject()
})
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
        <button
          v-for="tab in tabs"
          :key="tab"
          type="button"
          :class="{ active: tab === '개요' }"
        >
          {{ tab }}
        </button>
      </nav>

      <p v-if="deployMessage" class="form-success project-feedback">{{ deployMessage }}</p>
      <p v-if="errorMessage" class="form-error project-feedback">{{ errorMessage }}</p>

      <div class="overview-grid">
        <section class="panel detail-card summary-card overview-summary-card">
          <h2>배포 요약</h2>
          <dl class="detail-list compact">
            <div>
              <dt>현재 버전</dt>
              <dd>v1.2.2 <span class="version-color blue">(BLUE)</span></dd>
            </div>
            <div>
              <dt>이전 배포 버전</dt>
              <dd>v1.2.1 <span class="version-color green">(GREEN)</span></dd>
            </div>
            <div>
              <dt>최근 배포</dt>
              <dd>2025-05-16 14:30:22</dd>
            </div>
            <div>
              <dt>배포 상태</dt>
              <dd><span class="status-badge running">{{ statusLabel }}</span></dd>
            </div>
            <div>
              <dt>업타임</dt>
              <dd>2일 14시간 32분</dd>
            </div>
            <div>
              <dt>Health Check</dt>
              <dd class="health-ok"><CheckCircleIcon aria-hidden="true" /> 정상</dd>
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
              <dt>컨테이너 이미지</dt>
              <dd>registry.example.com/{{ project.subdomain }}:1.2.2</dd>
            </div>
            <div>
              <dt>환경</dt>
              <dd>production</dd>
            </div>
            <div>
              <dt>인스턴스</dt>
              <dd>2 / 2</dd>
            </div>
            <div>
              <dt>로드밸런서</dt>
              <dd>Traefik</dd>
            </div>
          </dl>
        </section>

        <section class="panel detail-card resource-card">
          <h2>리소스 사용 현황</h2>
          <div class="resource-grid">
            <article class="resource-item blue">
              <div class="resource-ring"><span>23%</span></div>
              <strong>CPU</strong>
              <small>0.46 / 2 CPU</small>
            </article>
            <article class="resource-item green">
              <div class="resource-ring"><span>45%</span></div>
              <strong>메모리</strong>
              <small>920MB / 2GB</small>
            </article>
            <article class="resource-item dark">
              <div class="resource-ring"><span>31%</span></div>
              <strong>디스크</strong>
              <small>15GB / 50GB</small>
            </article>
            <article class="resource-item teal">
              <div class="resource-ring"><span>12%</span></div>
              <strong>네트워크</strong>
              <small>120Mbps</small>
            </article>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>
